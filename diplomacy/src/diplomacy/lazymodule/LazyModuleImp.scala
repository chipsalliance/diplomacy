package org.chipsalliance.diplomacy.lazymodule

import org.chipsalliance.cde.config.Parameters
import chisel3.experimental.ChiselAnnotation
import chisel3.experimental.SourceInfo
import chisel3.{RawModule, Reset, withClockAndReset, _}
import org.chipsalliance.diplomacy.nodes.Dangle
import firrtl.passes.InlineAnnotation

import scala.collection.immutable.SortedMap

/** Trait describing the actual [[Module]] implementation wrapped by a [[LazyModule]].
  *
  * This is the actual Chisel module that is lazily-evaluated in the second phase of Diplomacy.
  */
sealed trait LazyModuleImpLike extends RawModule {

  /** [[LazyModule]] that contains this instance. */
  val wrapper: LazyModule

  /** IOs that will be automatically "punched" for this instance. */
  val auto: AutoBundle

  /** The metadata that describes the [[HalfEdge]]s which generated [[auto]]. */
  val dangles: Seq[Dangle]

  // [[wrapper.module]] had better not be accessed while LazyModules are still being built!
  require(
    LazyModule.scope.isEmpty,
    s"${wrapper.name}.module was constructed before LazyModule() was run on ${LazyModule.scope.get.name}"
  )

  /** Set module name. Defaults to the containing LazyModule's desiredName. */
  override def desiredName: String = wrapper.desiredName

  suggestName(wrapper.suggestedName)

  /** [[Parameters]] for chisel [[Module]]s. */
  implicit val p: Parameters = wrapper.p

  /** instantiate this [[LazyModule]],
    * return [[AutoBundle]] and a unconnected [[Dangle]]s from this module and submodules.
    */
  protected[diplomacy] def instantiate(): (AutoBundle, List[Dangle]) = {
    // 1. It will recursively append [[wrapper.children]] into [[chisel3.internal.Builder]],
    // 2. return [[Dangle]]s from each module.
    val childDangles = wrapper.children.reverse.flatMap { c =>
      implicit val sourceInfo: SourceInfo = c.info
      val mod = Module(c.module)
      mod.dangles
    }

    // Ask each node in this [[LazyModule]] to call [[BaseNode.instantiate]].
    // This will result in a sequence of [[Dangle]] from these [[BaseNode]]s.
    val nodeDangles = wrapper.nodes.reverse.flatMap(_.instantiate())
    // Accumulate all the [[Dangle]]s from this node and any accumulated from its [[wrapper.children]]
    val allDangles = nodeDangles ++ childDangles
    // Group [[allDangles]] by their [[source]].
    val pairing = SortedMap(allDangles.groupBy(_.source).toSeq: _*)
    // For each [[source]] set of [[Dangle]]s of size 2, ensure that these
    // can be connected as a source-sink pair (have opposite flipped value).
    // Make the connection and mark them as [[done]].
    val done = Set() ++ pairing.values.filter(_.size == 2).map {
      case Seq(a, b) =>
        require(a.flipped != b.flipped)
        // @todo <> in chisel3 makes directionless connection.
        if (a.flipped) {
          a.data <> b.data
        } else {
          b.data <> a.data
        }
        a.source
    }
    // Find all [[Dangle]]s which are still not connected. These will end up as [[AutoBundle]] [[IO]] ports on the module.
    val forward = allDangles.filter(d => !done(d.source))
    // Generate [[AutoBundle]] IO from [[forward]].
    val auto = IO(new AutoBundle(forward.map { d => (d.name, d.data, d.flipped) }: _*))
    // Pass the [[Dangle]]s which remained and were used to generate the [[AutoBundle]] I/O ports up to the [[parent]] [[LazyModule]]
    val dangles = (forward.zip(auto.elements)).map {
      case (d, (_, io)) =>
        if (d.flipped) {
          d.data <> io
        } else {
          io <> d.data
        }
        d.copy(data = io, name = wrapper.suggestedName + "_" + d.name)
    }
    // Push all [[LazyModule.inModuleBody]] to [[chisel3.internal.Builder]].
    wrapper.inModuleBody.reverse.foreach {
      _()
    }

    if (wrapper.shouldBeInlined) {
      chisel3.experimental.annotate(new ChiselAnnotation {
        def toFirrtl = InlineAnnotation(toNamed)
      })
    }

    // Return [[IO]] and [[Dangle]] of this [[LazyModuleImp]].
    (auto, dangles)
  }
}

/** Actual description of a [[Module]] which can be instantiated by a call to [[LazyModule.module]].
  *
  * @param wrapper the [[LazyModule]] from which the `.module` call is being made.
  */
class LazyModuleImp(val wrapper: LazyModule) extends LazyModuleImpLike {

  /** Instantiate hardware of this `Module`. */
  val (auto, dangles) = instantiate()
}

/** Actual description of a [[RawModule]] which can be instantiated by a call to [[LazyModule.module]].
  *
  * @param wrapper the [[LazyModule]] from which the `.module` call is being made.
  */
class LazyRawModuleImp(val wrapper: LazyModule) extends RawModule with LazyModuleImpLike {
  // These wires are the default clock+reset for all LazyModule children.
  // It is recommended to drive these even if you manually drive the [[clock]] and [[reset]] of all of the
  // [[LazyRawModuleImp]] children.
  // Otherwise, anonymous children ([[Monitor]]s for example) will not have their [[clock]] and/or [[reset]] driven properly.
  /** drive clock explicitly. */
  val childClock: Clock = Wire(Clock())

  /** drive reset explicitly. */
  val childReset: Reset = Wire(Reset())
  // the default is that these are disabled
  childClock := false.B.asClock
  childReset := chisel3.DontCare
  val (auto, dangles) = withClockAndReset(childClock, childReset) {
    instantiate()
  }
}
