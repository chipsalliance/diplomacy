package diplomacy.bundlebridge

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.experimental.DataMirror.internal.chiselTypeClone
import chisel3.experimental.{DataMirror, IO}
import diplomacy.ValName
import diplomacy.nodes._

case class BundleBridgeSource[T <: Data](genOpt: Option[() => T] = None)(implicit valName: sourcecode.Name)
    extends SourceNode(new BundleBridgeImp[T])(Seq(BundleBridgeParams(genOpt))) {
  def bundle: T = out(0)._1

  private def inferInput = getElements(bundle).forall { elt =>
    DataMirror.directionOf(elt) == ActualDirection.Unspecified
  }

  def makeIO()(implicit valName: sourcecode.Name): T = {
    val io: T = IO(if (inferInput) Input(chiselTypeOf(bundle)) else Flipped(chiselTypeClone(bundle)))
    io.suggestName(valName.value)
    bundle <> io
    io
  }
  def makeIO(name: String): T = makeIO()(ValName(name))

  private var doneSink = false
  def makeSink()(implicit p: Parameters) = {
    require(!doneSink, "Can only call makeSink() once")
    doneSink = true
    val sink = BundleBridgeSink[T]()
    sink := this
    sink
  }
}

object BundleBridgeSource {
  def apply[T <: Data]()(implicit valName: sourcecode.Name): BundleBridgeSource[T] = {
    BundleBridgeSource(None)
  }
  def apply[T <: Data](gen: () => T)(implicit valName: sourcecode.Name): BundleBridgeSource[T] = {
    BundleBridgeSource(Some(gen))
  }
}
