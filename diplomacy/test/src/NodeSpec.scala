package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.{Data, _}
import diplomacy.bundlebridge.BundleBridgeNexus.{fillN, orReduction}
import diplomacy.bundlebridge.{BundleBridgeNexus, BundleBridgeNexusNode, BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import diplomacy.nodes.{NexusNode, RenderedEdge, SimpleNodeImp, SinkNode, SourceNode}
import utest._
import chisel3.util.random.FibonacciLFSR

object NodeSpec extends TestSuite {
  def tests: Tests = Tests {
    test("iBindings and oBindings.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :*= NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // source node's outwardnode have accPo that pass to oBindings
          // nothing
          printf(p"${sourceModule.source.iBindings}")
          // return  (0, BundleBridgeSink , query ,chipsalliance.rocketchip.config$EmptyParameters , SourceLine(NodeSpec.scala,47,25))
          printf(p"${sourceModule.source.oBindings}")

          // sink node's inwardnode have accPi that pass to iBindings
          // return  (0, BundleBridgeSource , star ,chipsalliance.rocketchip.config$EmptyParameters , SourceLine(NodeSpec.scala,47,25))
          printf(p"${sinkModule.sink.iBindings}")
          // nothing
          printf(p"${sinkModule.sink.oBindings}")

          // sink node's inwardnode have accPi that pass to iBindings
          // return  (0, BundleBridgeSource , star ,chipsalliance.rocketchip.config$EmptyParameters , SourceLine(NodeSpec.scala,47,25))
          printf(p"${OthersinkModule.sink.iBindings}")
          // nothing
          printf(p"${OthersinkModule.sink.oBindings}")

          // return  List((0,1))
          printf(p"${NexusLM.broadcastnode.iBindings}")
          // nothing
          printf(p"${NexusLM.broadcastnode.oBindings}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("iPortMapping and oPortMapping.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // (oPortMapping,iPortMapping,oStar,iStar) = (oSum.init.zip(oSum.tail), iSum.init.zip(iSum.tail), oStar, iStar)
          //iPortMapping = iSum.init.zip(iSum.tail)
          //oPortMapping = oSum.init.zip(oSum.tail)
          // nothing
          printf(p"${sourceModule.source.iPortMapping}")
          // return  List((0,1))
          printf(p"${sourceModule.source.oPortMapping}")

          // return  List((0,1))
          printf(p"${sinkModule.sink.iPortMapping}")
          // nothing
          printf(p"${sinkModule.sink.oPortMapping}")

          // return  List((0,1))
          printf(p"${OthersinkModule.sink.iPortMapping}")
          // nothing
          printf(p"${OthersinkModule.sink.oPortMapping}")

          // return  List((0,1))
          printf(p"${NexusLM.broadcastnode.iPortMapping}")
          // return List((0,1), (1,2))
          printf(p"${NexusLM.broadcastnode.oPortMapping}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("ResolveStar : iStar and oStar.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink := NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // val (iStar, oStar) = resolveStar(iKnown, oKnown, iStars, oStars)
          //def resolveStar can be override in nodeImp
          //return  0
          printf(p"${sourceModule.source.iStar}")
          // return  0
          printf(p"${sourceModule.source.oStar}")

          // return  0
          printf(p"${sinkModule.sink.iStar}")
          //return  0
          printf(p"${sinkModule.sink.oStar}")

          // return  0
          printf(p"${OthersinkModule.sink.iStar}")
          //return  0
          printf(p"${OthersinkModule.sink.oStar}")

          // return  1
          printf(p"${NexusLM.broadcastnode.iStar}")
          //return  1
          printf(p"${NexusLM.broadcastnode.oStar}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("iDirectPorts and oDirectPorts.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // iDirectPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)]
          // oDirectPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)]
          // return the node  directly linked to this node
          // nothing
          printf(p"${sourceModule.source.iDirectPorts}")
          // return BundleBridgeNexusNode(Some(diplomacy.unittest.NodeSpec$$$Lambda$1873/0x000000080066b598@cfd1075),true)
          printf(p"${sourceModule.source.oDirectPorts}")

          // return BundleBridgeNexusNode(Some(diplomacy.unittest.NodeSpec$$$Lambda$1873/0x000000080066b598@cfd1075),true)
          printf(p"${sinkModule.sink.iDirectPorts}")
          // nothing
          printf(p"${sinkModule.sink.oDirectPorts}")

          // return  List(1,BundleBridgeNexusNode(Some(diplomacy.unittest.NodeSpec$$$Lambda$1873/0x000000080066b598@cfd1075),true), xx ,xx )
          printf(p"${OthersinkModule.sink.iDirectPorts}")
          // nothing
          printf(p"${OthersinkModule.sink.oDirectPorts}")

          // return  BundleBridgeSource(Some(diplomacy.unittest.NodeSpec$$$Lambda$1873/0x000000080066b598@cfd1075))
          printf(p"${NexusLM.broadcastnode.iDirectPorts}")
          // return  BundleBridgeSink(Some(diplomacy.unittest.NodeSpec$$$Lambda$1873/0x000000080066b598@cfd1075))
          printf(p"${NexusLM.broadcastnode.oDirectPorts}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("iPorts and oPorts.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // iPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)] = iDirectPorts.map(iTrace)
          // oPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)] = oDirectPorts.map(oTrace)
          // return the node  directly linked to this node
          //return List()
          printf(p"${sourceModule.source.iPorts}")
          // return BundleBridgeNexusNode(Some(diplomacy.unittest.NodeSpec$$$Lambda$1866/0x000000080066b598@5a7df831),true)
          printf(p"${sourceModule.source.oPorts}")

          // return BundleBridgeNexusNode(Some(diplomacy.unittest.NodeSpec$$$Lambda$1866/0x000000080066b598@5a7df831),true)
          printf(p"${sinkModule.sink.iPorts}")
          // nothing
          printf(p"${sinkModule.sink.oPorts}")

          // return  List((1,BundleBridgeNexusNode(Some(diplomacy.unittest.NodeSpec$$$Lambda$1866/0x000000080066b598@5a7df831),true), xx ,xx )
          printf(p"${OthersinkModule.sink.iPorts}")
          // nothing
          printf(p"${OthersinkModule.sink.oPorts}")

          // return BundleBridgeSource(Some(diplomacy.unittest.NodeSpec$$$Lambda$1866/0x000000080066b598@5a7df831))
          printf(p"${NexusLM.broadcastnode.iPorts}")
          // return  BundleBridgeSink(Some(diplomacy.unittest.NodeSpec$$$Lambda$1866/0x000000080066b598@5a7df831))
          printf(p"${NexusLM.broadcastnode.oPorts}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("diParams and doParams and uiParams and uoParams") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // protected[diplomacy] lazy val diParams: Seq[DI] = iPorts.map { case (i, n, _, _) => n.doParams(i) }
          // protected[diplomacy] lazy val doParams: Seq[DO] = {

          //  protected[diplomacy] lazy val uoParams: Seq[UO] = oPorts.map { case (o, n, _, _) => n.uiParams(o) }
          //  protected[diplomacy] lazy val uiParams: Seq[UI] = {

          // nothing
          printf(p"${sourceModule.source.diParams}")
          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sourceModule.source.doParams}")
          // nothing
          printf(p"${sourceModule.source.uiParams}")
          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sourceModule.source.uoParams}")

          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sinkModule.sink.diParams}")
          // nothing
          printf(p"${sinkModule.sink.doParams}")
          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sinkModule.sink.uiParams}")
          // nothing
          printf(p"${sinkModule.sink.uoParams}")

          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${OthersinkModule.sink.diParams}")
          // nothing
          printf(p"${OthersinkModule.sink.doParams}")
          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${OthersinkModule.sink.uiParams}")
          // nothing
          printf(p"${OthersinkModule.sink.uoParams}")

          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))")
          printf(p"${NexusLM.broadcastnode.diParams}")
          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)), BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${NexusLM.broadcastnode.doParams}")
          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))")
          printf(p"${NexusLM.broadcastnode.uiParams}")
          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)), BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${NexusLM.broadcastnode.uoParams}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("edgesOut and edgesIn.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          // protected[diplomacy] lazy val edgesOut: Seq[EO] =
          //        (oPorts.zip(doParams)).map { case ((i, n, p, s), o) => outer.edgeO(o, n.uiParams(i), p, s) }
          // protected[diplomacy] lazy val edgesIn: Seq[EI] =
          //    (iPorts.zip(uiParams)).map { case ((o, n, p, s), i) => inner.edgeI(n.doParams(o), i, p, s) }

          // return the node  directly linked to this node
          // nothing
          printf(p"${sourceModule.source.edgesIn}")
          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sourceModule.source.edgesOut}")

          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sinkModule.sink.edgesIn}")
          // nothing
          printf(p"${sinkModule.sink.edgesOut}")

          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${OthersinkModule.sink.edgesIn}")
          // nothing
          printf(p"${OthersinkModule.sink.edgesOut}")

          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))")
          printf(p"${NexusLM.broadcastnode.edgesIn}")
          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)), BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${NexusLM.broadcastnode.edgesOut}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("bundleIn and bundleOut.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

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
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = true,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        // use object undleBridgeNexus to return a BundleBridgeNexusNode
        val broadcast_other: BundleBridgeNexusNode[T] = BundleBridgeNexus[T](
          inputFn = BundleBridgeNexus.orReduction[T](registered),
          outputFn = BundleBridgeNexus.fillN[T](registered),
          default = default,
          inputRequiresOutput = false,
          shouldBeInlined = canshouldBeInlined
        )(p)


        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= NexusLM.broadcastnode
        OthersinkModule.sink :=* NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          /** Create actual Wires corresponding to the Bundles parameterized by the outward edges of this node. */
          /**protected[diplomacy] lazy val bundleOut: Seq[BO] = edgesOut.map(e => chisel3.Wire(outer.bundleO(e)))

          /** Create actual Wires corresponding to the Bundles parameterized by the inward edges of this node. */
          //protected[diplomacy] lazy val bundleIn: Seq[BI] = edgesIn.map(e => chisel3.Wire(inner.bundleI(e))) */

          // return the node  directly linked to this node
          // nothing
          printf(p"${sourceModule.source.bundleIn}")
          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sourceModule.source.bundleOut}")

          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${sinkModule.sink.bundleIn}")
          // nothing
          printf(p"${sinkModule.sink.bundleOut}")

          // return List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${OthersinkModule.sink.bundleIn}")
          // nothing
          printf(p"${OthersinkModule.sink.bundleOut}")

          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))")
          printf(p"${NexusLM.broadcastnode.bundleIn}")
          // return  List(BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)), BundleBridgeParams(Some(diplomacy.unittest.NodeSpec$$$Lambda$1880/0x00000008006757f0@122635ef)))
          printf(p"${NexusLM.broadcastnode.bundleOut}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

  }
}