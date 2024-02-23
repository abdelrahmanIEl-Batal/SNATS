package socket

import cats.effect.Concurrent
import cats.effect.std.Queue
import fs2.{Stream, text}
import fs2.io.net.Socket
import cats.implicits._

// TODO extend input output to be generic?
sealed trait ClientSocket[F[_]] {
  def read: Stream[F, String]

  def write(output: String): F[Unit]
}

object ClientSocket {

  private case class DefaultClientSocket[F[_]: Concurrent](socket: Socket[F], queue: Queue[F, String]) extends ClientSocket[F] {

    override def read: Stream[F, String] = {

      val readInput: Stream[F, String] = socket.reads.through(text.utf8.decode).through(text.lines).interleave(Stream.constant("\n"))

      val writeOutput = Stream.fromQueueUnterminated(queue).through(text.utf8.encode).through(socket.writes)

      readInput.concurrently(writeOutput)
    }

    override def write(output: String): F[Unit] =
      queue.offer(output)
  }

  def apply[F[_]: Concurrent](socket: Socket[F]): F[ClientSocket[F]] =
    for {
      queue <- Queue.unbounded[F, String]
    } yield DefaultClientSocket[F](socket = socket, queue = queue)
}
