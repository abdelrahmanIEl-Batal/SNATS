package model

sealed trait Topic extends Any {

  def name: String
}

object Topic {

  private case class DefaultTopic(name: String) extends AnyVal with Topic

  def fromString(name: String): Topic = DefaultTopic(name)
}
