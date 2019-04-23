package scala.fp.exercises.cats.effect

import java.util.UUID

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.implicits._
import cats.effect.{Concurrent, Sync}
import cats.implicits._

import scala.fp.exercises.cats.effect.WorkerPool.Worker

trait WorkerPool[A, B, F[_]] {
  def exec(a: A): F[B]

  def removeAllWorkers: F[Unit]

  def addWorker(worker: Worker[A, B, F]): F[Unit]
}

object WorkerPool {
  type Worker[A, B, F[_]] = A => F[B]

  def of[A, B, F[_] : Concurrent](fs: Worker[A, B, F]*): F[WorkerPool[A, B, F]] = of(fs.toList)

  def of[A, B, F[_] : Concurrent](fs: List[Worker[A, B, F]]): F[WorkerPool[A, B, F]] = {

    type Canceled = Boolean
    type TaskID = UUID

    for {
      workers <- MVar.of[F, List[Worker[A, B, F]]](fs)
      inFlight <- Ref.of[F, Map[TaskID, Canceled]](Map.empty)
    } yield {
      new WorkerPool[A, B, F] {

        override def exec(a: A): F[B] = {
          def loop: F[B] = for {
            freeWorkers <- workers.take
            res <- if (freeWorkers.nonEmpty) {
              run(freeWorkers.head, freeWorkers.tail)
            } else workers.put(List.empty) *> loop
          } yield res

          def run(worker: Worker[A, B, F], freeWorkers: List[Worker[A, B, F]]) = for {
            taskId <- Sync[F].pure(UUID.randomUUID())
            _ <- inFlight.update(_ + (taskId -> false))
            r <- workers.put(freeWorkers).bracket(_ => worker(a))(_ => putBack(worker, taskId))
          } yield r

          def putBack(worker: Worker[A, B, F], taskId: TaskID) = for {
            freeW <- workers.take
            tasks <- inFlight.get
            workersList = if (tasks.get(taskId).exists(identity _)) freeW else worker :: freeW
            _ <- inFlight.update(_ - taskId)
            _ <- workers.put(workersList).start
          } yield ()

          loop
        }

        override def removeAllWorkers: F[Unit] = for {
          _ <- workers.take
          _ <- inFlight.update(_.map { case (taskId, _) => (taskId, true) })
          _ <- workers.put(List.empty)
        } yield ()


        override def addWorker(newWorker: Worker[A, B, F]): F[Unit] = for {
          freeWorkers <- workers.take
          _ <- workers.put(newWorker :: freeWorkers)
        } yield ()
      }
    }
  }

}

