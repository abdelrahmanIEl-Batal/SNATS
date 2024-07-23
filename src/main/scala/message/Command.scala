package message

sealed trait Command {

  def commandType: String
}

object Command {

  object CommandType {

    final val PUB: String = "PUB"

    final val SUB: String = "SUB"

    final val PING: String = "PING"
  }

  case object Pub extends Command {

    override val commandType: String = CommandType.PUB
  }

  case object Sub extends Command {

    override val commandType: String = CommandType.SUB
  }

  case object Ping extends Command {

    override val commandType: String = CommandType.PING
  }
}
