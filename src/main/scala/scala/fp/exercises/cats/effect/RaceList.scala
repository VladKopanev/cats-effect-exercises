package scala.fp.exercises.cats.effect

import cats.data.NonEmptyList
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{ContextShift, ExitCase, ExitCode, Fiber, IO, IOApp, Timer}
import cats.implicits._

import scala.concurrent.duration._
import scala.fp.exercises.cats.effect.RaceList.CompositeException
import scala.util.Random

object RaceList extends IOApp {
  case class Data(source: String, body: String)

  def provider(name: String)(implicit timer: Timer[IO]): IO[Data] = {
    val proc = for {
      dur <- IO { Random.nextInt(500) }
      _   <- IO.sleep { (100 + dur).millis }
      _   <- IO { if (Random.nextBoolean()) throw new Exception(s"Error in $name") }
      txt <- IO { Random.alphanumeric.take(16).mkString }
    } yield Data(name, txt)

    proc.guaranteeCase {
      case ExitCase.Completed => IO { println(s"$name request finished") }
      case ExitCase.Canceled  => IO { println(s"$name request canceled") }
      case ExitCase.Error(ex) => IO { println(s"$name errored") }
    }
  }

  // Use this class for reporting all failures.
  case class CompositeException(ex: NonEmptyList[Throwable]) extends Exception("All race candidates have failed")


  override def run(args: List[String]): IO[ExitCode] = for {
    res <- Race.raceToSuccess(methods)
    _ <- IO(println(res))
  } yield ExitCode.Success

  val methods: NonEmptyList[IO[Data]] = NonEmptyList.of(
    "memcached",
    "redis",
    "postgres",
    "mongodb",
    "hdd",
    "aws"
  ).map(provider)
}

object Race {
  def raceToSuccess[A](ios: NonEmptyList[IO[A]])(implicit cs: ContextShift[IO]): IO[A] = {
    type EResult =  Either[CompositeException, A]
    (Ref.of[IO, List[Throwable]](List.empty), Deferred[IO, EResult])
      .tupled.flatMap { case (failedList, promise) =>

      def startTask(io: IO[A]): IO[Fiber[IO, Unit]] =
        (io.map(Right(_)).handleErrorWith(failTask) >>= promise.complete).start

      def failTask(e: Throwable): IO[EResult] = for {
        _ <- failedList.update(e :: _)
        failed <- failedList.get
        res <- if (failed.size >= ios.size)
          IO.pure(Left(CompositeException(NonEmptyList.fromListUnsafe(failed))))
        else IO.raiseError(e)
      } yield res

      for {
        rFiber <- promise.get.start
        fibers <- ios.traverse(startTask)
        eitherResult <- rFiber.join
        _ <- fibers.traverse(_.cancel.asInstanceOf[IO[Unit]])
        result <- eitherResult.fold(IO.raiseError, IO.pure)
      } yield result
    }
  }

}
