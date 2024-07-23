package server

import cats.effect.std.Console
import cats.effect.{Concurrent, ExitCode, IO, IOApp, Ref}
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxEitherId, catsSyntaxFlatMapOps, toFlatMapOps, toFoldableOps}
import com.comcast.ip4s.Port
import fs2.io.net.{Network, Socket}
import fs2.text
import fs2.Stream
import message.{NatsMessage, Payload, Subject}
import model.ClientId
import parser.NatsParser

object NatsServer extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      clientsRef <- Ref.of[IO, Map[ClientId, Socket[IO]]](Map.empty)
      topicRef   <- Ref.of[IO, Map[Subject, Vector[ClientId]]](Map.empty)
      _          <- server[IO](clientsRef, topicRef)
    } yield ExitCode.Success

  private final val port: Int = 4222

  private def server[F[_]: Concurrent: Network: Console](
    clientsRef: Ref[F, Map[ClientId, Socket[F]]],
    topicRef: Ref[F, Map[Subject, Vector[ClientId]]]
  ): F[Unit] =
    Network[F].server(port = Port.fromInt(port)).map { client =>
      println(topicRef)
      Stream.eval(client.remoteAddress).flatMap { address =>
        val currAddress: ClientId = ClientId.fromString(s"${address.host.toUriString}:${address.port.value}")
        Stream.eval(
          clientsRef.update(clients => clients + (currAddress -> client))
        ) ++
          handleClient(client, currAddress, clientsRef, topicRef)
            .handleErrorWith(handleError(currAddress, clientsRef))
            .onFinalize(removeClient(currAddress, clientsRef))
      }
    }.parJoin(100).compile.drain

  private def handleClient[F[_]: Concurrent: Console](
    client: Socket[F],
    address: ClientId,
    clientsRef: Ref[F, Map[ClientId, Socket[F]]],
    topicRef: Ref[F, Map[Subject, Vector[ClientId]]],
  ): Stream[F, String] =
    client.reads
      .through(text.utf8.decode)
      .through(text.lines)
      .evalTap(Console[F].println)
      .evalTap { message =>
        parseMessage(message.trim) match {
          case Left(exception) =>
            responseStream(exception.getMessage).through(text.utf8.encode).through(client.writes).compile.drain
          case Right(command) =>
            command match {
              case NatsMessage.PubMessage(subject, payload) => broadcastMessage(address, clientsRef, subject, payload, topicRef)
              case NatsMessage.SubMessage(subject) => handleSubscribe(subject = subject, clientId = address, topicRef = topicRef, client)
              case NatsMessage.PingMessage =>
                responseStream("PONG").through(text.utf8.encode).through(client.writes).compile.drain
            }
        }
      }

  private def responseStream[F[_]](message: String): Stream[F, String] =
    Stream.emit(message).interleave(Stream.constant("\n"))

  private def handleSubscribe[F[_]: Concurrent](
    subject: Subject,
    clientId: ClientId,
    topicRef: Ref[F, Map[Subject, Vector[ClientId]]],
    socket: Socket[F]
  ): F[Unit] =
    topicRef.update { mp =>
      mp.find(_._1.value == subject.value) match {
        case Some(subjectMap) => mp.updated(subject, subjectMap._2 :+ clientId)
        case None             => mp + (subject -> Vector(clientId))
      }
    } >> responseStream(s"subscribed to topic: ${subject.value}").through(text.utf8.encode).through(socket.writes).compile.drain

  private def parseMessage(message: String): Either[Throwable, NatsMessage] =
    NatsParser.parseMessage(message) match {
      case Left(exception) => exception.asLeft
      case Right(command)  => command.asRight
    }

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
    senderAddress: ClientId,
    clientsRef: Ref[F, Map[ClientId, Socket[F]]],
    subject: Subject,
    payload: Payload,
    topicRef: Ref[F, Map[Subject, Vector[ClientId]]]
  ): F[Unit] =
    clientsRef.get.flatMap { clients =>
      topicRef.get.flatMap { topics =>
        topics.get(subject) match {
          case Some(subjectClients) =>
            clients.toVector.traverse_ { case (address, client) =>
              if (address != senderAddress && subjectClients.contains(address)) {
                responseStream(s"topic: `${subject.value}`, received: ${payload.value}")
                  .through(text.utf8.encode)
                  .through(client.writes)
                  .compile.drain
                  .handleError(_ => ())
              } else {
                Concurrent[F].unit // Do nothing for the sender
              }
            }
          case None => Concurrent[F].unit // Do nothing for the sender
        }
      }
    }
}
