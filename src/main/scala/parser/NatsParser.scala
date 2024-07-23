package parser

import cats.implicits.catsSyntaxEitherId
import message.Command.CommandType
import message.{Command, NatsMessage, Payload, Subject}

object NatsParser {

  private final val regex: String = " "

  def parseMessage(message: String): Either[Throwable, NatsMessage] =
    extractMessage(message.split(regex).toVector)

  private def extractMessage(components: Vector[String], position: Int = 0): Either[Throwable, NatsMessage] =
    if (components.nonEmpty)
      extractCommand(components(position)) match {
        case Left(error) => error.asLeft
        case Right(command) => command match {
            case Command.Pub     => extractPubSubject(components = components, position = position + 1)
            case Command.Sub     => extractSubSubject(components = components, position = position + 1)
            case Command.Ping    => NatsMessage.PingMessage.asRight
          }
      }
    else new IllegalArgumentException("invalid message").asLeft

  private def extractCommand(command: String): Either[Throwable, Command] =
    command match {
      case CommandType.SUB     => Command.Sub.asRight
      case CommandType.PUB     => Command.Pub.asRight
      case CommandType.PING    => Command.Ping.asRight
      case otherwise           => new IllegalArgumentException(s"invalid command encountered, value: $otherwise").asLeft
    }

  private def extractSubSubject(position: Int, components: Vector[String]): Either[Throwable, NatsMessage] =
    if (position < components.length) NatsMessage.SubMessage(Subject(components(position))).asRight
    else new IllegalArgumentException("expected subject, found nothing").asLeft

  private def extractPubSubject(position: Int, components: Vector[String]): Either[Throwable, NatsMessage] =
    if (position < components.length) {
      val subject: Subject = Subject(components(position))
      extractPayLoad(position = position + 1, components = components) match {
        case Left(error)    => error.asLeft
        case Right(payload) => NatsMessage.PubMessage(subject = subject, payload = payload).asRight
      }
    } else new IllegalArgumentException("expected subject, found nothing").asLeft

  private def extractPayLoad(position: Int, components: Vector[String]): Either[Throwable, Payload] =
    if (position < components.length) Payload(components(position)).asRight
    else new IllegalArgumentException("expected payload, found nothing").asLeft
}
