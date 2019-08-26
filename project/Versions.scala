import scala.sys.process._
import scala.util.Try

object Versions {
  lazy val appVersion: String = Try(
    "git describe --tags --dirty --always".!!.stripPrefix("v").trim.replace("-dirty", "-SNAPSHOT")
  ).toOption.getOrElse("alpha")
}
