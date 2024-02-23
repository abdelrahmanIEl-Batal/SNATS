package client

import cats.effect.kernel.Async
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp, Temporal}
import com.comcast.ip4s.{Host, IpLiteralSyntax, SocketAddress}
import fs2.io.net.{Network, Socket}
import fs2.Stream
import socket.ClientSocket

import scala.concurrent.duration.DurationInt

object NatsClient extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = client[IO].compile.drain.as(ExitCode.Success)

  private final val connectionRetryTimeout: Int = 5

  private def connect[F[_]: Temporal: Network: Console](address: SocketAddress[Host]): Stream[F, Socket[F]] =
    Stream.exec(Console[F].println("connecting to broker ...")) ++
      Stream.resource(Network[F].client(address))
        .handleErrorWith(_ => connect(address).delayBy(connectionRetryTimeout.seconds))

  private def client[F[_]: Console: Network: Async]: Stream[F, Unit] =
    connect(SocketAddress(host"localhost", port"4222")).flatMap { socket =>
      Stream.exec(Console[F].println("connected!")) ++
        Stream.eval(ClientSocket[F](socket = socket)).flatMap { clientSocket =>
          readMessage(clientSocket).concurrently(sendMessage(clientSocket))
        }
    }

  private def readMessage[F[_]: Console](clientSocket: ClientSocket[F]): Stream[F, Unit] =
    clientSocket.read.evalMap(Console[F].println)

  private def sendMessage[F[_]: Console](clientSocket: ClientSocket[F]): Stream[F, Unit] =
    Stream.repeatEval(Console[F].readLine)
      .flatMap(Stream.emit)
      .evalMap(clientSocket.write)
}
