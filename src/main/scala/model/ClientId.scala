package model

sealed trait ClientId extends Any {

  def value: String
}

object ClientId {

  private case class DefaultClientId(value: String) extends AnyVal with ClientId

  def fromString(value: String): ClientId = DefaultClientId(value)
}
