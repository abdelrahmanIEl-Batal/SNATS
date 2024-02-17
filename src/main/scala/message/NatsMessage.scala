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

  case object ConnectMessage extends NatsMessage {

    override val command: Command = Command.Connect
  }

  case object PingMessage extends NatsMessage {

    override val command: Command = Command.Ping
  }
}
