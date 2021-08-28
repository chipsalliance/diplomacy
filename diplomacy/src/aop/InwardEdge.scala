package diplomacy.aop

import chipsalliance.rocketchip.config.Parameters
import chisel3.Data
import diplomacy.OutwardNode

/** Contains information about an inward edge of a node */
case class InwardEdge[Bundle <: Data, EdgeInParams](
  params: Parameters,
  bundle: Bundle,
  edge:   EdgeInParams,
  node:   OutwardNode[_, _, Bundle])
