package org.chipsalliance.diplomacy.bundlebridge

import chisel3.Data
import org.chipsalliance.diplomacy.nodes.IdentityNode

case class BundleBridgeIdentityNode[T <: Data](
)(
  implicit valName: sourcecode.Name)
    extends IdentityNode(new BundleBridgeImp[T])()
