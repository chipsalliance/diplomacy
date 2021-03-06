package diplomacy.nodes

import chisel3.Data

/** [[EphemeralNode]]s are used as temporary connectivity placeholders, but disappear from the final node graph.
  * An ephemeral node provides a mechanism to directly connect two nodes to each other where neither node knows about the other,
  * but both know about an ephemeral node they can use to facilitate the connection.
  */
class EphemeralNode[D, U, EO, EI, B <: Data](imp: NodeImp[D, U, EO, EI, B])()(implicit valName: sourcecode.Name)
    extends AdapterNode(imp)({ s => s }, { s => s }) {
  override def description = "ephemeral"
  override final def circuitIdentity = true
  override def omitGraphML = true
  override def oForward(x: Int): Option[(Int, OutwardNode[D, U, B])] = Some(iDirectPorts(x) match {
    case (i, n, _, _) => (i, n)
  })
  override def iForward(x: Int): Option[(Int, InwardNode[D, U, B])] = Some(oDirectPorts(x) match {
    case (i, n, _, _) => (i, n)
  })
  override protected[diplomacy] def instantiate(): Seq[Dangle] = {
    instantiated = true
    Nil
  }
}
