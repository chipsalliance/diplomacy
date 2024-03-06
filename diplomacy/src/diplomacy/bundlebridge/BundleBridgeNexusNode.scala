package org.chipsalliance.diplomacy.bundlebridge

import chisel3._
import org.chipsalliance.diplomacy.nodes.NexusNode

case class BundleBridgeNexusNode[T <: Data](
  default:             Option[() => T] = None,
  inputRequiresOutput: Boolean = false) // when false, connecting a source does not mandate connecting a sink
(implicit valName:     sourcecode.Name)
    extends NexusNode(new BundleBridgeImp[T])(
      dFn = seq => seq.headOption.getOrElse(BundleBridgeParams(default)),
      uFn = seq => seq.headOption.getOrElse(BundleBridgeParams(None)),
      inputRequiresOutput = inputRequiresOutput,
      outputRequiresInput = !default.isDefined
    )
