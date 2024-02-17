package server

import cats.effect.std.Console
import cats.effect.{Concurrent, ExitCode, IO, IOApp}
import com.comcast.ip4s.Port
import fs2.io.net.Network
import fs2.text
import fs2.Stream

object NatsServer extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = server[IO].as(ExitCode.Success)

  private final val port: Int = 4222

  private def server[F[_]: Concurrent: Network: Console]: F[Unit] =
    Network[F].server(port = Port.fromInt(port)).map { client =>
      client.reads
        .through(text.utf8.decode)
        .through(text.lines)
        .evalTap(Console[F].println)
        .through(text.utf8.encode)
        .through(client.writes)
        .handleErrorWith(_ => Stream.empty)
    }.parJoin(100).compile.drain
}
