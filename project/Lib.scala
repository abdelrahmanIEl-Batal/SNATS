import sbt.*

object Lib {

  object Version {

    val catsEffect: String =  "3.5.3"

    val fs2Core: String = "3.9.4"

    val fs2IO: String = "3.9.4"
  }

  val catsEffect: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-effect" % Version.catsEffect
  )

  val fs2Core: Seq[ModuleID] = Seq(
    "co.fs2" %% "fs2-core" % Version.fs2Core
  )

  val fs2IO: Seq[ModuleID] = Seq(
    "co.fs2" %% "fs2-io" % Version.fs2IO
  )
}
