// import Mill dependency
import mill._
import scalalib._
import scalafmt._
import publish._

val defaultVersions = Map(
  "chisel3" -> "3.4.1",
  "chisel3-plugin" -> "3.4.1",
  "scala" -> "2.12.12",
)

def getVersion(dep: String, org: String = "edu.berkeley.cs", cross: Boolean = false) = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}

trait CommonModule extends ScalaModule with SbtModule with ScalafmtModule with PublishModule {
  def scalaVersion = defaultVersions("scala")

  def publishVersion = "0.1"

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "edu.berkeley.cs",
    url = "https://www.chisel-lang.org",
    licenses = Seq(License.`BSD-3-Clause`),
    versionControl = VersionControl.github("freechipsproject", "chisel3"),
    developers = Seq(
      Developer("jackbackrack", "Jonathan Bachrach", "https://eecs.berkeley.edu/~jrb/")
    )
  )

}

object diplomacy extends diplomacy

class diplomacy extends CommonModule { m =>
  // ValName macros, give name to Nodes.
  def chisel3Module: Option[PublishModule] = None

  object macros extends CommonModule {
    override def ivyDeps = Agg(
      ivy"${scalaOrganization()}:scala-reflect:${scalaVersion()}"
    )
  }

  def chisel3IvyDeps = if (chisel3Module.isEmpty) Agg(
    getVersion("chisel3")
  ) else Agg.empty[Dep]

  override def moduleDeps = super.moduleDeps ++ Seq(macros) ++ chisel3Module

  private val chisel3Plugin = getVersion("chisel3-plugin", cross = true)

  override def scalacPluginIvyDeps = if (chisel3Module.isEmpty) Agg(chisel3Plugin) else Agg.empty[Dep]

  // add some scala ivy module you like here.
  override def ivyDeps = super.ivyDeps() ++ chisel3IvyDeps

  // use scalatest as your test framework
  object tests extends Tests {
    override def ivyDeps = Agg(ivy"org.scalatest::scalatest:latest.integration")

    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
