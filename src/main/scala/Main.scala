import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toTraverseOps
import parser.NatsParser

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val messages: Vector[String] = Vector("PUB lol 32434", "SUB FOO 1\\r\\n", "CONNECT", "PING")

    val execute: IO[Vector[Unit]] = messages.traverse { message =>
      NatsParser.parseMessage(message) match {
        case Left(error) =>
          IO.println(error.getMessage)
        case Right(natsMessage) =>
          IO.println(natsMessage)
      }
    }

    execute.as(ExitCode.Success)
  }
}
