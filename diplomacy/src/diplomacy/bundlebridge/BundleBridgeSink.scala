package diplomacy.bundlebridge

import chisel3._
import chisel3.experimental.DataMirror.internal.chiselTypeClone
import chisel3.experimental.{DataMirror, IO}
import diplomacy.ValName
import diplomacy.nodes._

case class BundleBridgeSink[T <: Data](genOpt: Option[() => T] = None)(implicit valName: sourcecode.Name)
    extends SinkNode(new BundleBridgeImp[T])(Seq(BundleBridgeParams(genOpt))) {
  def bundle: T = in(0)._1

  private def inferOutput = getElements(bundle).forall { elt =>
    DataMirror.directionOf(elt) == ActualDirection.Unspecified
  }

  def makeIO()(implicit valName: sourcecode.Name): T = {
    val io: T = IO(if (inferOutput) Output(chiselTypeOf(bundle)) else chiselTypeClone(bundle))
    io.suggestName(valName.value)
    io <> bundle
    io
  }
  def makeIO(name: String): T = makeIO()(ValName(name))
}

object BundleBridgeSink {
  def apply[T <: Data]()(implicit valName: sourcecode.Name): BundleBridgeSink[T] = {
    BundleBridgeSink(None)
  }
}
