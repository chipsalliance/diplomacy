import mill._
import mill.scalalib._

trait HasChisel extends ScalaModule {
  // Define these for building chisel from source
  def chiselModule: Option[ScalaModule]
  override def moduleDeps = super.moduleDeps ++ chiselModule

  def chiselPluginJar: T[Option[PathRef]]
  override def scalacOptions = T(
    (super.scalacOptions() ++ chiselPluginJar().map(path => s"-Xplugin:${path.path}")) ++ Seq("-deprecation", "-feature")
  )
  override def scalacPluginClasspath: T[Agg[PathRef]] = T(super.scalacPluginClasspath() ++ chiselPluginJar())

  // Define these for using chisel from ivy
  def chiselIvy: Option[Dep]
  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)

  def chiselPluginIvy: Option[Dep]
  override def scalacPluginIvyDeps: T[Agg[Dep]] = T(
    super.scalacPluginIvyDeps() ++ chiselPluginIvy.map(Agg(_)).getOrElse(Agg.empty[Dep])
  )
}

trait DiplomacyModule extends HasChisel {

  // cde from module till published to sonatype
  def cdeModule: Option[ScalaModule]

  // prep for cde use from ivy
  def cdeIvy: Option[Dep]

  override def moduleDeps = super.moduleDeps ++ cdeModule

  def sourcecodeIvy: Dep

  override def ivyDeps = T(super.ivyDeps() ++ Some(sourcecodeIvy) ++ cdeIvy)

  override def scalacOptions = T(
    super.scalacOptions() ++ Seq("-Wunused")
  )

}
