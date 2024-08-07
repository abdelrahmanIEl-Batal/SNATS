package message

sealed trait NatsMessage {

  def command: Command
}

object NatsMessage {

  case class PubMessage(subject: Subject, payload: Payload) extends NatsMessage {

    override val command: Command = Command.Pub
  }

  case class SubMessage(subject: Subject) extends NatsMessage {

    override val command: Command = Command.Sub
  }

  case class UnsubscribeMessage(subject: Subject) extends NatsMessage {

    override val command: Command = Command.Unsubscribe
  }

  case object PingMessage extends NatsMessage {

    override val command: Command = Command.Ping
  }
}
