package diplomacy.bundlebridge

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.experimental.DataMirror
import chisel3.internal.sourceinfo.SourceInfo
import diplomacy.nodes._

class BundleBridgeImp[T <: Data]()
    extends SimpleNodeImp[BundleBridgeParams[T], BundleBridgeParams[T], BundleBridgeEdgeParams[T], T] {
  def edge(pd: BundleBridgeParams[T], pu: BundleBridgeParams[T], p: Parameters, sourceInfo: SourceInfo) =
    BundleBridgeEdgeParams(pd, pu)
  def bundle(e: BundleBridgeEdgeParams[T]): T = {
    val sourceOpt = e.source.genOpt.map(_())
    val sinkOpt = e.sink.genOpt.map(_())
    (sourceOpt, sinkOpt) match {
      case (None, None) =>
        throw new Exception("BundleBridge needs source or sink to provide bundle generator function")
      case (Some(a), None) => a
      case (None, Some(b)) => b
      case (Some(a), Some(b)) => {
        require(
          DataMirror.checkTypeEquivalence(a, b),
          s"BundleBridge requires doubly-specified source and sink generators to have equivalent Chisel Data types, but got \n$a\n vs\n$b"
        )
        a
      }
    }
  }
  def render(e: BundleBridgeEdgeParams[T]) = RenderedEdge(colour = "#cccc00" /* yellow */ )
}
