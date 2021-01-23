import mill._
import scalalib._
import scalafmt._
import publish._

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.1`
import de.tobiasroeser.mill.vcs.version.VcsVersion

object ivys {
  val chisel3 = ivy"edu.berkeley.cs::chisel3:3.4.3"
  val chisel3Plugin = ivy"edu.berkeley.cs:::chisel3-plugin:3.4.3"
  val sourcecode = ivy"com.lihaoyi::sourcecode:0.2.7"
}

object diplomacy extends diplomacy

class diplomacy extends ScalaModule with ScalafmtModule with PublishModule { m =>
  def scalaVersion = "2.12.13"

  def chisel3Module: Option[PublishModule] = None

  override def moduleDeps = Seq() ++ chisel3Module

  override def scalacPluginIvyDeps = if (chisel3Module.isEmpty) Agg(ivys.chisel3Plugin) else Agg.empty[Dep]

  override def ivyDeps = Agg(ivys.sourcecode) ++ (if (chisel3Module.isEmpty) Some(ivys.chisel3) else None)

  object tests extends Tests with TestModule.Utest {
    def ivyDeps = Agg(ivy"com.lihaoyi::utest:0.7.10")
  }

  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "me.sequncer",
    url = "https://www.github.com/sequencer/diplomacy",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("sequencer", "diplomacy"),
    developers = Seq(
      Developer("terpstra", "Wesley W. Terpstra", "https://github.com/terpstra"),
      Developer("hcook", "Henry Cook", "https://github.com/hcook"),
      Developer("sequencer", "Jiuyang Liu", "https://jiuyang.me"),
    )
  )
}
