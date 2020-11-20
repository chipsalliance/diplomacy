// import Mill dependency
import mill._
import scalalib._
import scalafmt._
// support BSP
import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`

val defaultVersions = Map(
  "chisel3" -> "3.4.0",
  "chisel3-plugin" -> "3.4.0"
)

def getVersion(dep: String, org: String = "edu.berkeley.cs", cross: Boolean = false) = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}

object diplomacy extends mill.Cross[diplomacyCrossModule]("2.11.12", "2.12.12")

// Currently, it depends on all projects for fast development, after first step to give a standalone version, all these dependencies will be removed.
class diplomacyCrossModule(val crossScalaVersion: String) extends ScalaModule with ScalafmtModule {
  def scalaVersion = crossScalaVersion
 // ValName macros, give name to Nodes.
  object macros extends ScalaModule with ScalafmtModule {
    override def millSourcePath = os.pwd / "macros"

    override def scalaVersion = crossScalaVersion
  
    override def ivyDeps = Agg(
      ivy"${scalaOrganization()}:scala-reflect:${scalaVersion()}"
    )
  }

  def chisel3Module: Option[PublishModule] = None

  def chisel3IvyDeps = if (chisel3Module.isEmpty) Agg(
    getVersion("chisel3")
  ) else Agg.empty[Dep]

  override def moduleDeps = super.moduleDeps ++ Seq(macros) ++ chisel3Module

  private val chisel3Plugin = getVersion("chisel3-plugin", cross = true)

  override def scalacPluginIvyDeps = if(chisel3Module.isDefined) Agg[Dep]() else Agg(chisel3Plugin)

  // add some scala ivy module you like here.
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::upickle:latest.integration",
    ivy"com.lihaoyi::os-lib:latest.integration",
    ivy"com.lihaoyi::pprint:latest.integration",
    ivy"org.scala-lang.modules::scala-xml:latest.integration"
  ) ++ chisel3IvyDeps

  // use scalatest as your test framework
  object tests extends Tests {
    override def ivyDeps = Agg(ivy"org.scalatest::scalatest:latest.integration")

    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
