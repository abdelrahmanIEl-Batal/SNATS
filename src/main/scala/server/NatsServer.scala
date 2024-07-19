package server

import cats.effect.std.Console
import cats.effect.{Concurrent, ExitCode, IO, IOApp, Ref}
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFlatMapOps, toFoldableOps}
import com.comcast.ip4s.Port
import fs2.io.net.{Network, Socket}
import fs2.text
import fs2.Stream
import model.{ClientId, Topic}

object NatsServer extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      clientsRef <- Ref.of[IO, Map[ClientId, Socket[IO]]](Map.empty)
      topicRef   <- Ref.of[IO, Map[Topic, Vector[ClientId]]](Map.empty)
      _          <- server[IO](clientsRef, topicRef)
    } yield ExitCode.Success

  private final val port: Int = 4222

  private def server[F[_]: Concurrent: Network: Console](
    clientsRef: Ref[F, Map[ClientId, Socket[F]]],
    topicRef: Ref[F, Map[Topic, Vector[ClientId]]]
  ): F[Unit] =
    Network[F].server(port = Port.fromInt(port)).map { client =>
      println(topicRef)
      Stream.eval(client.remoteAddress).flatMap { address =>
        val currAddress: ClientId = ClientId.fromString(s"${address.host.toUriString}:${address.port.value}")
        Stream.eval(
          clientsRef.update(clients => clients + (currAddress -> client))
        ) ++
          handleClient(client, currAddress, clientsRef)
            .handleErrorWith(handleError(currAddress, clientsRef))
            .onFinalize(removeClient(currAddress, clientsRef))
      }
    }.parJoin(100).compile.drain

  private def handleClient[F[_]: Concurrent: Console](
    client: Socket[F],
    address: ClientId,
    clientsRef: Ref[F, Map[ClientId, Socket[F]]]
  ): Stream[F, String] =
    client.reads
      .through(text.utf8.decode)
      .through(text.lines)
      .interleave(Stream.constant("\n"))
      .evalTap(Console[F].println)
      .evalTap(message => broadcastMessage(message, address, clientsRef))

  private def handleError[F[_]: Concurrent: Console](
    address: ClientId,
    clientsRef: Ref[F, Map[ClientId, Socket[F]]]
  ): Throwable => Stream[F, Unit] =
    error =>
      Stream.eval(
        Console[F].println(s"Error for client $address: ${error.getMessage}") >>
          removeClient(address, clientsRef)
      )

  private def removeClient[F[_]: Concurrent: Console](
    address: ClientId,
    clientsRef: Ref[F, Map[ClientId, Socket[F]]]
  ): F[Unit] =
    clientsRef.update(_ - address) >>
      Console[F].println(s"Client $address disconnected")

  private def broadcastMessage[F[_]: Concurrent](
    message: String,
    senderAddress: ClientId,
    clientsRef: Ref[F, Map[ClientId, Socket[F]]]
  ): F[Unit] =
    clientsRef.get.flatMap { clients =>
      clients.toVector.traverse_ { case (address, client) =>
        if (address != senderAddress) {
          Stream.emit(message)
            .through(text.utf8.encode)
            .through(client.writes)
            .compile.drain
            .handleError(_ => ())
        } else {
          Concurrent[F].unit // Do nothing for the sender
        }
      }
    }
}
