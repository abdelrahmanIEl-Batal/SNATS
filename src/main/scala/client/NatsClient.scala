package client

import cats.effect.kernel.Async
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp, Temporal}
import com.comcast.ip4s.{Host, IpLiteralSyntax, SocketAddress}
import fs2.io.net.{Network, Socket}
import fs2.io.stdin
import fs2.{Stream, text}

import scala.concurrent.duration.DurationInt

object NatsClient extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = client[IO].compile.drain.as(ExitCode.Success)

  private final val connectionRetryTimeout: Int = 5

  private def connect[F[_]: Temporal: Network](address: SocketAddress[Host]): Stream[F, Socket[F]] =
    Stream.resource(Network[F].client(address))
      .handleErrorWith(_ => connect(address).delayBy(connectionRetryTimeout.seconds))

  private def client[F[_]: Console: Network: Async]: Stream[F, Unit] =
    connect(SocketAddress(host"localhost", port"4222")).flatMap { socket =>
      stdin[F](100)
        .through(socket.writes) ++
        socket.reads
          .through(text.utf8.decode)
          .through(text.lines)
          .head
          .evalTap(Console[F].println)
          .flatMap(_ => Stream.unit)
    }
}
