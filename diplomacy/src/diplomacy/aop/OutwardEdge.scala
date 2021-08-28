package diplomacy.aop

import chipsalliance.rocketchip.config.Parameters
import chisel3.Data
import diplomacy.nodes.InwardNode

/** Contains information about an outward edge of a node */
case class OutwardEdge[Bundle <: Data, EdgeOutParams](
  params: Parameters,
  bundle: Bundle,
  edge:   EdgeOutParams,
  node:   InwardNode[_, _, Bundle])
