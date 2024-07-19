package model

import fs2.io.net.Socket

case class ClientIdentifier[F[_]](socket: Socket[F])
