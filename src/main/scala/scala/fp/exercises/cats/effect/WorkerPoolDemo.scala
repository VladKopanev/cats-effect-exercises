package scala.fp.exercises.cats.effect

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp, Timer}
import cats.implicits._
import cats.effect.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.fp.exercises.cats.effect.WorkerPool.Worker
import scala.util.Random

object WorkerPoolDemo extends IOApp {
  def mkWorker(id: Int)(implicit timer: Timer[IO]): IO[Worker[Int, Int, IO]] =
    Ref[IO].of(0).map { counter =>
      def simulateWork: IO[Unit] =
        IO(50 + Random.nextInt(450)).map(_.millis).flatMap(IO.sleep)

      def report: IO[Unit] =
        counter.get.flatMap(i => IO(println(s"Total processed by $id: $i")))

      x =>
        simulateWork >>
          counter.update(_ + 1) >>
          report >>
          IO.pure(x + 1)
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val testPool: IO[WorkerPool[Int, Int, IO]] =
      List.range(0, 10)
        .traverse(mkWorker)
        .flatMap(WorkerPool.of[Int, Int, IO])
    val test1 = for {
      pool <- testPool
      _ <- List.range(0, 1000).map(i => pool.exec(i)).parSequence.void
    } yield ExitCode.Success

    //should not terminate
    val test2 = for {
      pool <- WorkerPool.of[Unit, Unit, IO]((a: Unit) => IO.sleep(2.seconds))
      _    <- pool.exec(()).start
      _    <- pool.removeAllWorkers
      _    <- pool.exec(())
    } yield ExitCode.Error

    test2

  }
}
