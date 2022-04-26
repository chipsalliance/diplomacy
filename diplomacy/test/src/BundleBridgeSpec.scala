package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.{Data, _}
import diplomacy.bundlebridge.{
  BundleBridgeEphemeralNode,
  BundleBridgeIdentityNode,
  BundleBridgeNexus,
  BundleBridgeNexusNode,
  BundleBridgeSink,
  BundleBridgeSource
}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import diplomacy.nodes.{BIND_ONCE, BIND_QUERY, BIND_STAR}
import utest._

object BundleBridgeSpec extends TestSuite {
  def tests: Tests = Tests {
    test(" test connection between raw BundleBridge Source and BundleBridge Sink.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val source_bundile = source.bundle
          source_bundile := 4.U(32.W)
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink :*= sourceModule.source
        lazy val module = new LazyModuleImp(this) {

          // test oPorts:
          // lazy val oPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)] = oDirectPorts.map(oTrace) := source
          // only have oPorts show the binding in the [[InwardNode]] on the other end.
          utest.assert(sourceModule.source.oPorts.size == 1)
          utest.assert(sourceModule.source.oPorts.head._1 == 0)
          utest.assert(sourceModule.source.oPorts.head._2.name == "sinkModule.sink")
          utest.assert(sourceModule.source.oPorts.head._2.index == 0)
          utest.assert(sourceModule.source.oPorts.head._2.lazyModule == sinkModule)

          // sinknode :*=
          utest.assert(sinkModule.sink.oPorts.size == 0)
          utest.assert(sinkModule.sink.oPorts.isEmpty)

          // test iPorts:
          // lazy val iPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)] = iDirectPorts.map(iTrace)
          // := source do not have iPorts ([OutwardNode]] on the other end of this binding.)
          utest.assert(sourceModule.source.iPorts.isEmpty)
          utest.assert(sourceModule.source.iPorts.size == 0)
          utest.assert(sinkModule.sink.iPorts.size == 1)
          utest.assert(sinkModule.sink.iPorts.head._1 == 0)
          utest.assert(sinkModule.sink.iPorts.head._2.name == "sourceModule.source")
          utest.assert(sinkModule.sink.iPorts.head._2.index == 0)
          utest.assert(sinkModule.sink.iPorts.head._2.lazyModule == sourceModule)

          // test iBindings:
          // source node's outwardnode have accPo that pass to oBindings
          // iBindings: immutable.Seq[(Int, OutwardNode[DI, UI, BI], NodeBinding, Parameters, SourceInfo)]
          utest.assert(sourceModule.source.iBindings.isEmpty)
          utest.assert(sinkModule.sink.iBindings.head._1 == 0)
          utest.assert(sinkModule.sink.iBindings.head._2.name == "sourceModule.source")
          utest.assert(sinkModule.sink.iBindings.head._3 == BIND_STAR)

          // test oBindings:
          utest.assert(sourceModule.source.oBindings.head._1 == 0)
          utest.assert(sourceModule.source.oBindings.head._2.name == "sinkModule.sink")
          utest.assert(sourceModule.source.oBindings.head._3 == BIND_QUERY)
          utest.assert(sinkModule.sink.oBindings.isEmpty)

          // test diParams
          utest.assert(sourceModule.source.diParams.isEmpty)
          utest.assert(sinkModule.sink.diParams.size == 1)

          // test doParams
          utest.assert(sourceModule.source.doParams.size == 1)
          utest.assert(sinkModule.sink.doParams.isEmpty)
        }
      }
      val TopModule = LazyModule(new TopLazyModule)
      chisel3.stage.ChiselStage.emitSystemVerilog(TopModule.module)

      // test bundlein/bundleout:
      // bundleOut: Seq[BO] = edgesOut.map(e => chisel3.Wire(outer.bundleO(e)))
      utest.assert(TopModule.sourceModule.source.bundleIn.isEmpty)
      utest.assert(TopModule.sourceModule.source.bundleOut(0).getWidth == 32)
      utest.assert(TopModule.sinkModule.sink.bundleIn(0).getWidth == 32)
      utest.assert(TopModule.sinkModule.sink.bundleOut.isEmpty)

      // test danglesIn/bundleout
      // for example: sinkModule.sink :*= sourceModule.source
      // sinkModule.sink.danglesIn.source == sourceModule.source.danglesOut.source
      // sinkModule.sink.danglesIn.sink == sourceModule.source.danglesOut.sink
      //FixMe : MixedNode.scala line 32 maybe have some mistake , should be danglesOut.flipped ==false ,danglesIn.flipped ==true
      utest.assert(TopModule.sourceModule.source.danglesIn.isEmpty)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).source.index == 0)

      // because HalfEdge serial is the (global) number of the [[BaseNode]] that this [[HalfEdge]] connects to.
      // as long as there is a new node to be added,
      // the serial number of the node.danglesIn/danglesOut.source/sink.serial may be changed.
      //utest.assert(TopModule.sourceModule.source.danglesOut(0).sink.serial == 1)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).sink.index == 0)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).name == "source_out")
      utest.assert(TopModule.sourceModule.source.danglesOut(0).flipped == false)
      //utest.assert(TopModule.sinkModule.sink.danglesIn(0).source.serial == 0)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).source.index == 0)
      //utest.assert(TopModule.sinkModule.sink.danglesIn(0).sink.serial  == 1)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).sink.index == 0)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).name == "sink_in")
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).flipped == true)
      utest.assert(TopModule.sinkModule.sink.danglesOut.isEmpty)
    }

    test(" Another BundleBridge Source and Sink normal usage: use BundleBridge SourceNode.makeSink") {
      implicit val p = Parameters.empty
      class BottomLazyModule extends LazyModule {
        val source = BundleBridgeSource(() => UInt(32.W))
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
        }
      }
      class TopLazyModule extends LazyModule {
        val bottom = LazyModule(new BottomLazyModule)
        val sink = bottom.source.makeSink()

        // HINT: require(!doneSink, "Can only call makeSink() once")
        lazy val module = new LazyModuleImp(this) {
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      chisel3.stage.ChiselStage.emitSystemVerilog(LazyModule(new TopLazyModule).module)
    }

    test(" test BundleBridgeNexus prototype and normal usage") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      // Just to test how to make a new LazyModule class BundleBridgeNexus in two different ways,
      // the following line is first way
      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val source_bundle = source.bundle
          source_bundle := 4.U
        }
      }

      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          chisel3.assert(sink.bundle === 4.U)
        }
      }

      // Just to test how to make a new LazyModule class BundleBridgeNexus in two different ways,
      // the following line is second way
      class NexusLazyModule[T <: Data](genOpt: Option[() => T]) extends LazyModule {

        val aname:      Option[String] = Some("MyBroadcast")
        val registered: Boolean = false
        val default:    Option[() => T] = genOpt
        // When inputRequiresOutput is false, connecting a source does not mandate connecting a sink
        val inputRequiresOutput: Boolean = true
        val canshouldBeInlined:  Boolean = false

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // To make a name for the class NexusLazyModule
        aname.foreach(broadcast.suggestName)
        // def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
          utest.assert(broadcast.node.default.isDefined)
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazyModule[UInt](Some(genOption)))

        // now the node connection like :
        // two sink nodes := one broadcast node :*= one source node
        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink := NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          utest.assert(NexusLM.broadcast.name == "MyBroadcast")

          // test oStar / iStar:
          // In the NexusNode.scala,
          // resolveStar teturn  (iStar, oStar) = if (iKnown == 0 && oKnown == 0) (0, 0) else (1, 1)
          utest.assert(NexusLM.broadcastnode.oStar == 1)
          utest.assert(NexusLM.broadcastnode.iStar == 1)

          // test oBindings / iBindings :
          // Hint: why oBindings(0).index == oBindings(1).index,
          // because index: numeric index of this binding in the other end of [[InwardNode]].
          // in this case, index show the bundleBridge nexus node
          utest.assert(NexusLM.broadcastnode.oBindings.size == 2)
          utest.assert(NexusLM.broadcastnode.oBindings(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oBindings(0)._2.name == "sinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oBindings(0)._3 == BIND_QUERY)

          utest.assert(NexusLM.broadcastnode.oBindings(1)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oBindings(1)._2.name == "OthersinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oBindings(1)._3 == BIND_ONCE)

          utest.assert(NexusLM.broadcastnode.iBindings.size == 1)
          utest.assert(NexusLM.broadcastnode.iBindings(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iBindings(0)._2.name == "sourceModule.source")
          utest.assert(NexusLM.broadcastnode.iBindings(0)._3 == BIND_STAR)
          // test iPortMapping / oPortMapping :
          // oPortMapping = oSum.init.zip(oSum.tail)
          // iPortMapping = iSum.init.zip(iSum.tail)
          // val oSum = oBindings.map (Cumulative list of resolved outward binding range starting points)
          // val iSum = iBindings.map (Cumulative list of resolved inward binding range starting points)
          // Show that oSum = (0,1,2), iSum = (0,1)
          utest.assert(NexusLM.broadcastnode.iPortMapping(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iPortMapping(0)._2 == 1)
          utest.assert(NexusLM.broadcastnode.iPortMapping.length == 1)
          utest.assert(NexusLM.broadcastnode.oPortMapping(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oPortMapping(0)._2 == 1)
          utest.assert(NexusLM.broadcastnode.oPortMapping(1)._1 == 1)
          utest.assert(NexusLM.broadcastnode.oPortMapping(1)._2 == 2)
          utest.assert(NexusLM.broadcastnode.oPortMapping.length == 2)

          // test iDirectPorts/oDirectPorts
          utest.assert(NexusLM.broadcastnode.oDirectPorts.size == 2)
          utest.assert(NexusLM.broadcastnode.oDirectPorts(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oDirectPorts(0)._2.name == "sinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oDirectPorts(1)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oDirectPorts(1)._2.name == "OthersinkModule.sink")

          utest.assert(NexusLM.broadcastnode.iDirectPorts.size == 1)
          utest.assert(NexusLM.broadcastnode.iDirectPorts(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iDirectPorts(0)._2.name == "sourceModule.source")

          //test iPorts/oPorts
          utest.assert(NexusLM.broadcastnode.oPorts.size == 2)
          utest.assert(NexusLM.broadcastnode.oPorts(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oPorts(0)._2.name == "sinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oPorts(1)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oPorts(1)._2.name == "OthersinkModule.sink")

          utest.assert(NexusLM.broadcastnode.iPorts.size == 1)
          utest.assert(NexusLM.broadcastnode.iPorts(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iPorts(0)._2.name == "sourceModule.source")

          //test diParams/doParams
          // val diParams: Seq[DI] = iPorts.map { case (i, n, _, _) => n.doParams(i) }
          // in this test, it means that:
          // NexusLM.broadcastnode.diParams = sourceModule.source.doParams
          // NexusLM.broadcastnode.diParams = sinkModule.sink.diParams++OthersinkModule.sink.diParams
          utest.assert(NexusLM.broadcastnode.diParams == sourceModule.source.doParams)
          utest.assert(NexusLM.broadcastnode.doParams == (sinkModule.sink.diParams ++ OthersinkModule.sink.diParams))

          // test uoParams/uiParams
          utest.assert(NexusLM.broadcastnode.uoParams == (sinkModule.sink.uiParams ++ OthersinkModule.sink.uiParams))
          utest.assert(NexusLM.broadcastnode.uiParams == sourceModule.source.uoParams)

          // test edgesIn/edgesOut
          utest.assert(
            NexusLM.broadcastnode.iPorts
              .zip(NexusLM.broadcastnode.uiParams)
              .head
              ._1 == NexusLM.broadcastnode.iPorts.head
          )
          utest.assert(
            NexusLM.broadcastnode.iPorts
              .zip(NexusLM.broadcastnode.uiParams)
              .head
              ._2 == NexusLM.broadcastnode.uiParams.head
          )
          utest.assert(
            NexusLM.broadcastnode.edgesIn.contains(
              NexusLM.broadcastnode.inner.edgeI(
                sourceModule.source.doParams(0),
                NexusLM.broadcastnode.uiParams.head,
                NexusLM.broadcastnode.iPorts.head._3,
                NexusLM.broadcastnode.iPorts.head._4
              )
            )
          )
          utest.assert(
            NexusLM.broadcastnode.edgesOut.contains(
              NexusLM.broadcastnode.outer.edgeO(
                NexusLM.broadcastnode.doParams.head,
                sinkModule.sink.uiParams(0),
                NexusLM.broadcastnode.oPorts(0)._3,
                NexusLM.broadcastnode.oPorts(0)._4
              )
            )
          )
          utest.assert(
            NexusLM.broadcastnode.edgesOut.contains(
              NexusLM.broadcastnode.outer.edgeO(
                NexusLM.broadcastnode.doParams.head,
                sinkModule.sink.uiParams(0),
                NexusLM.broadcastnode.oPorts(1)._3,
                NexusLM.broadcastnode.oPorts(1)._4
              )
            )
          )
          utest.assert(NexusLM.broadcastnode.edges.out == NexusLM.broadcastnode.edgesOut)
          utest.assert(NexusLM.broadcastnode.edges.in == NexusLM.broadcastnode.edgesIn)

          // test index
          // Just one broadcastnode in this case, so index is 0
          utest.assert(NexusLM.broadcastnode.index == 0)

          // test instantiated
          // after instantiated, val instantiated switch false to true
          utest.assert(NexusLM.broadcastnode.instantiated == true)

          // test flexes
          // in our case, there is no BIND_FLEX, so flexes is empty
          utest.assert(NexusLM.broadcastnode.flexes.isEmpty)
          utest.assert(NexusLM.broadcastnode.flexOffset == 0)

          // test inward/outward
          // in our case, "this" is NexusLM.broadcastnode
          utest.assert(NexusLM.broadcastnode.inward == NexusLM.broadcastnode)
          utest.assert(NexusLM.broadcastnode.outward == NexusLM.broadcastnode)

          // test module.name
          utest.assert(NexusLM.broadcast.module.name == "BundleBridgeNexus")
        }
      }

      val TopLM = LazyModule(new TopLazyModule)
      chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
      // test bundleIn / bundleOut:
      utest.assert(TopLM.NexusLM.broadcast.node.bundleIn.head.getWidth == 32)
      utest.assert(TopLM.NexusLM.broadcast.node.bundleIn.length == 1)
      utest.assert(TopLM.NexusLM.broadcast.node.bundleOut.head.getWidth == 32)
      utest.assert(TopLM.NexusLM.broadcast.node.bundleOut.tail.head.getWidth == 32)
      utest.assert(TopLM.NexusLM.broadcast.node.bundleOut.length == 2)
    }

    test(" test BundleBridgeIdentityNode prototype and normal usage") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))
      class DemoNexus(implicit valName: sourcecode.Name)
          extends BundleBridgeNexus[UInt](
            inputFn = BundleBridgeNexus.orReduction[UInt](false),
            outputFn = BundleBridgeNexus.fillN[UInt](false),
            default = Some(genOption),
            inputRequiresOutput = false,
            shouldBeInlined = true
          )(p)

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val source_bundle = source.bundle
          source_bundle := 4.U
        }
      }

      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          chisel3.assert(sink.bundle === 4.U)
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T])(implicit valName: sourcecode.Name) extends LazyModule {

        val nodeIdentity = BundleBridgeIdentityNode[T]()(valName)
        lazy val module = new LazyModuleImp(this) {}
      }

      class TopLazyModule extends LazyModule {
        val oherNexusLM = LazyModule(new DemoNexus)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption))("nodeIdentity"))

        val IdentitysourceModule = LazyModule(new SourceLazyModule)

        NexusLM.nodeIdentity :*= oherNexusLM.node := IdentitysourceModule.source

        lazy val module = new LazyModuleImp(this) {}
      }
      val TopLM = LazyModule(new TopLazyModule())
      chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
    }

    test(" test BundleBridgeEphemeralNode prototype and normal usage") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))
      class DemoNexus(implicit valName: sourcecode.Name)
          extends BundleBridgeNexus[UInt](
            inputFn = BundleBridgeNexus.orReduction[UInt](false),
            outputFn = BundleBridgeNexus.fillN[UInt](false),
            default = Some(genOption),
            inputRequiresOutput = false,
            shouldBeInlined = true
          )(p)

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val source_bundle = source.bundle
          source_bundle := 4.U
        }
      }

      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          chisel3.assert(sink.bundle === 4.U)
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T])(implicit valName: sourcecode.Name) extends LazyModule {
        val nodeEphemeral = BundleBridgeEphemeralNode[T]()(valName)
        lazy val module = new LazyModuleImp(this) {}
      }

      class TopLazyModule extends LazyModule {
        val EphemeralsourceModule = LazyModule(new SourceLazyModule)
        val EphemeralsinkModule = LazyModule(new SinkLazyModule)
        val oherNexusLM = LazyModule(new DemoNexus)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption))("nodeEphemeral"))
        NexusLM.nodeEphemeral := oherNexusLM.node := EphemeralsourceModule.source
        EphemeralsinkModule.sink := NexusLM.nodeEphemeral
        lazy val module = new LazyModuleImp(this) {}
      }
      val TopLM = LazyModule(new TopLazyModule())
      chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
    }

  }
}
