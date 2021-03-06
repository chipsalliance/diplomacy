package diplomacy.bundlebridge
import chisel3.Data
import diplomacy.nodes.EphemeralNode

case class BundleBridgeEphemeralNode[T <: Data]()(implicit valName: sourcecode.Name)
    extends EphemeralNode(new BundleBridgeImp[T])()
