package scala.fp.exercises.cats.effect

import cats.effect.{Concurrent, Fiber, IO}
import cats.effect.concurrent.MVar
import cats.implicits._
import cats.effect.implicits._

import scala.fp.exercises.cats.effect.WorkerPool.Worker

trait WorkerPool[A, B, F[_]] {
  def exec(a: A): F[B]
  def removeAllWorkers: F[Unit]
  def addWorker(worker: Worker[A, B, F]): F[Unit]
}

object WorkerPool {
  type Worker[A, B, F[_]] = A => F[B]

  // Implement this constructor, and, correspondingly, the interface above.
  // You are free to use named or anonymous classes
  def of[A, B, F[_]: Concurrent](fs: List[Worker[A, B, F]]): F[WorkerPool[A, B, F]] = {

    MVar.of[F, List[Worker[A, B, F]]](fs).map { workers =>

      new WorkerPool[A, B, F] {
        override def exec(a: A): F[B] = {
          def loop: F[B] = {
            for {
              freeWorkers <- workers.take
              res <- if(freeWorkers.nonEmpty) {
                val worker :: tail = freeWorkers
                workers.put(tail).bracket(_ => worker(a))(_ =>
                  workers.take.flatMap(freeW => workers.put(worker :: freeW)).start.void)
              } else workers.put(List.empty) *> loop
            } yield res
          }
          loop
        }

        //TODO cancel all on-flight jobs so old workers not going to come back
        override def removeAllWorkers: F[Unit] =
          workers.take *> workers.put(List.empty)

        override def addWorker(newWorker: Worker[A, B, F]): F[Unit] = for {
          freeWorkers <- workers.take
          _ <- workers.put(newWorker :: freeWorkers)
        } yield ()
      }
    }
  }

}

