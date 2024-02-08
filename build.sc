import mill._
import mill.scalalib._
import mill.scalalib.publish._

import scalafmt._

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.11:0.4.0`
import de.tobiasroeser.mill.vcs.version.VcsVersion

import $file.dependencies.cde.common
import $file.common

object ivys {}

object v {
  val scala = "2.13.12"

  val chiselCrossVersions = Map(
    "5.1.0" -> (ivy"org.chipsalliance::chisel:5.1.0", ivy"org.chipsalliance:::chisel-plugin:5.1.0"),
    "6.0.0" -> (ivy"org.chipsalliance::chisel:6.0.0", ivy"org.chipsalliance:::chisel-plugin:6.0.0")
  )

  val sourcecode = ivy"com.lihaoyi::sourcecode:0.3.1"
  val utest = ivy"com.lihaoyi::utest:0.8.2"
}

object cde extends CDE

trait CDE extends millbuild.dependencies.cde.common.CDEModule with DiplomacyPublishModule with ScalaModule {
  def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
}

object diplomacy extends Cross[Diplomacy](v.chiselCrossVersions.keys.toSeq)

trait Diplomacy
    extends millbuild.common.DiplomacyModule
    with DiplomacyPublishModule
    with ScalafmtModule
    with Cross.Module[String] {

  override def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = os.pwd / "diplomacy"

  // dont use chisel from source
  def chiselModule = None
  def chiselPluginJar = None

  // use chisel from ivy
  def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)
  def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)

  // use CDE from source untill published to sonatype
  def cdeModule = Some(cde)

  // no cde ivy currently published
  def cdeIvy = None

  def sourcecodeIvy = v.sourcecode
}

trait DiplomacyPublishModule extends PublishModule {
  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "org.chipsalliance",
    url = "https://www.github.com/chipsalliance/diplomacy",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("chipsalliance", "diplomacy"),
    developers = Seq(
      Developer("terpstra", "Wesley W. Terpstra", "https://github.com/terpstra"),
      Developer("hcook", "Henry Cook", "https://github.com/hcook"),
      Developer("sequencer", "Jiuyang Liu", "https://github.com/sequencer")
    )
  )

  override def sonatypeUri:         String = "https://s01.oss.sonatype.org/service/local"
  override def sonatypeSnapshotUri: String = "https://s01.oss.sonatype.org/content/repositories/snapshots"

  def githubPublish = T {
    os.proc("gpg", "--import", "--no-tty", "--batch", "--yes")
      .call(stdin = java.util.Base64.getDecoder.decode(sys.env("PGP_SECRET").replace("\n", "")))
    val PublishModule.PublishData(artifactInfo, artifacts) = publishArtifacts()
    new SonatypePublisher(
      sonatypeUri,
      sonatypeSnapshotUri,
      s"${sys.env("SONATYPE_USERNAME")}:${sys.env("SONATYPE_PASSWORD")}",
      true,
      Seq(
        s"--passphrase=${sys.env("PGP_PASSPHRASE")}",
        "--no-tty",
        "--pinentry-mode=loopback",
        "--batch",
        "--yes",
        "-a",
        "-b"
      ).flatMap(_.split("[,]")),
      60000,
      5000,
      T.log,
      os.pwd / "out",
      Map[String, String](),
      120000,
      true
    ).publish(artifacts.map { case (a, b) => (a.path, b) }, artifactInfo, true)
  }
}
