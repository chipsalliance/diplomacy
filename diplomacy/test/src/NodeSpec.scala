package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.{Data, _}
import diplomacy.bundlebridge.BundleBridgeNexus.{fillN, orReduction}
import diplomacy.bundlebridge.{BundleBridgeNexus, BundleBridgeNexusNode, BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import diplomacy.nodes.{BIND_ONCE, BIND_QUERY, BIND_STAR, NexusNode, RenderedEdge, SimpleNodeImp, SinkNode, SourceNode}
import utest._
import chisel3.util.random.FibonacciLFSR

object NodeSpec extends TestSuite {
  def tests: Tests = Tests {

    test("iBindings and oBindings ") {
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

        val aname: Option[String] = Some("NexusNode")
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
        OthersinkModule.sink := NexusLM.broadcastnode

        lazy val module = new LazyModuleImp(this) {
          /** test oBindings / iBindings :
            * protected[diplomacy] lazy val oBindings: Seq[(Int, InwardNode[DO, UO, BO], NodeBinding, Parameters, SourceInfo)] = {
            *  oRealized = true; accPO.result()}
            *
            * source node's outwardnode have accPo that pass to oBindings
            * sink node's inwardnode have accPi that pass to iBindings
            *
            *  Hint : why oBindings(0).index == oBindings(1).index ,
            *  because index : numeric index of this binding in the other end of [[InwardNode]].
            *  in this case , index show the bundleBridge nexus node*/
          // sourceModule.source iBindings/oBindings
          utest.assert(sourceModule.source.iBindings.isEmpty)
          utest.assert(sourceModule.source.oBindings.head._1 == 0)
          utest.assert(sourceModule.source.oBindings.head._2.name == "broadcast.node")
          utest.assert(sourceModule.source.oBindings.head._3 == BIND_QUERY)
          // sinkModule.sink iBindings/oBindings
          utest.assert(sinkModule.sink.iBindings.head._1 == 0)
          utest.assert(sinkModule.sink.iBindings.head._2.name == "broadcast.node")
          utest.assert(sinkModule.sink.iBindings.head._3 == BIND_STAR)
          utest.assert(sinkModule.sink.oBindings.isEmpty)
          // OthersinkModule.sink iBindings/oBindings
          //TODO : why two sink nodes these bound to a same  broadcast node have different index 0 and 1
          utest.assert(OthersinkModule.sink.iBindings.head._1 == 1)
          utest.assert(OthersinkModule.sink.iBindings.head._2.name == "broadcast.node")
          utest.assert(OthersinkModule.sink.iBindings.head._3 == BIND_ONCE)
          utest.assert(OthersinkModule.sink.oBindings.isEmpty)
          // NexusLM.broadcastnode iBindings/oBindings
          //TODO : why a same  broadcast node that bound to two different sink nodes has same index 0
          utest.assert(NexusLM.broadcastnode.oBindings.size == 2)
          //printf(p"${NexusLM.broadcastnode.oBindings(0) )
          utest.assert(NexusLM.broadcastnode.oBindings(0)._1 == 0 )
          utest.assert(NexusLM.broadcastnode.oBindings(0)._2.name == "sinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oBindings(0)._3 == BIND_QUERY)
          //printf(p"${NexusLM.broadcastnode.oBindings(1)}")
          utest.assert(NexusLM.broadcastnode.oBindings(1)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oBindings(1)._2.name == "OthersinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oBindings(1)._3 == BIND_ONCE)

          utest.assert(NexusLM.broadcastnode.iBindings.size == 1)
          utest.assert(NexusLM.broadcastnode.iBindings(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iBindings(0)._2.name == "sourceModule.source")
          utest.assert(NexusLM.broadcastnode.iBindings(0)._3 == BIND_STAR)
        }
      }
      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("iPortMapping and oPortMapping ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test iDirectPorts / oDirectPorts :
            *  iDirectPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)]
            *  oDirectPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)]
            */
          // NexusLM.broadcastnode.iPortMapping/oPortMapping
          utest.assert(NexusLM.broadcastnode.iPortMapping(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iPortMapping(0)._2 == 1)
          utest.assert(NexusLM.broadcastnode.iPortMapping.length  == 1)
          utest.assert(NexusLM.broadcastnode.oPortMapping(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oPortMapping(0)._2 == 1)
          utest.assert(NexusLM.broadcastnode.oPortMapping(1)._1 == 1)
          utest.assert(NexusLM.broadcastnode.oPortMapping(1)._2 == 2)
          utest.assert(NexusLM.broadcastnode.oPortMapping.length == 2)
          // sourceModule.source.iPortMapping/oPortMapping
          utest.assert(sourceModule.source.iPortMapping.isEmpty)
          utest.assert(sourceModule.source.oDirectPorts.size == 1)
          utest.assert(sourceModule.source.oPortMapping(0)._1 == 0)
          utest.assert(sourceModule.source.oPortMapping(0)._2 == 1)
          // sinkModule.sink.iPortMapping/oPortMapping
          utest.assert(sinkModule.sink.iPortMapping(0)._1 == 0)
          utest.assert(sinkModule.sink.iPortMapping(0)._2 == 1)
          utest.assert(sinkModule.sink.iPortMapping.length  == 1)
          utest.assert(sinkModule.sink.oPortMapping.isEmpty)
          // OthersinkModule.sink.iPortMapping/oPortMapping
          utest.assert(OthersinkModule.sink.iPortMapping(0)._1 == 0)
          utest.assert(OthersinkModule.sink.iPortMapping(0)._2 == 1)
          utest.assert(OthersinkModule.sink.iPortMapping.length  == 1)
          utest.assert(OthersinkModule.sink.oPortMapping.isEmpty)
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("iDirectPorts and oDirectPorts ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test iDirectPorts / oDirectPorts :
            *  iDirectPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)]
            *  oDirectPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)]
            */
          // NexusLM.broadcastnode.iDirectPorts/oDirectPorts
          utest.assert(NexusLM.broadcastnode.oDirectPorts.size == 2)
          utest.assert(NexusLM.broadcastnode.oDirectPorts(0)._1 == 0 )
          utest.assert(NexusLM.broadcastnode.oDirectPorts(0)._2.name == "sinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oDirectPorts(1)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oDirectPorts(1)._2.name == "OthersinkModule.sink")

          utest.assert(NexusLM.broadcastnode.iDirectPorts.size == 1)
          utest.assert(NexusLM.broadcastnode.iDirectPorts(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iDirectPorts(0)._2.name == "sourceModule.source")
          // sourceModule.source.iDirectPorts/oDirectPorts
          utest.assert(sourceModule.source.iDirectPorts.isEmpty)
          utest.assert(sourceModule.source.oDirectPorts.size == 1)
          utest.assert(sourceModule.source.oDirectPorts(0)._1 == 0)
          utest.assert(sourceModule.source.oDirectPorts(0)._2.name == "broadcast.node")
          // sinkModule.sink.iDirectPorts/oDirectPorts
          utest.assert(sinkModule.sink.iDirectPorts.size == 1)
          utest.assert(sinkModule.sink.iDirectPorts(0)._1 == 0)
          utest.assert(sinkModule.sink.iDirectPorts(0)._2.name == "broadcast.node")
          utest.assert(sinkModule.sink.oDirectPorts.isEmpty)
          // OthersinkModule.sink.iDirectPorts/oDirectPorts
          utest.assert(OthersinkModule.sink.iDirectPorts.size == 1)
          utest.assert(OthersinkModule.sink.iDirectPorts(0)._1 == 1)
          utest.assert(OthersinkModule.sink.iDirectPorts(0)._2.name == "broadcast.node")
          utest.assert(OthersinkModule.sink.oDirectPorts.isEmpty)
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("iPorts and oPorts ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test iPorts / oPorts :
            *  iPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)] = iDirectPorts.map(iTrace)
            *  oPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)] = oDirectPorts.map(oTrace)   */
          // NexusLM.broadcastnode.iPorts/oPorts
          utest.assert(NexusLM.broadcastnode.oPorts.size == 2)
          utest.assert(NexusLM.broadcastnode.oPorts(0)._1 == 0 )
          utest.assert(NexusLM.broadcastnode.oPorts(0)._2.name == "sinkModule.sink")
          utest.assert(NexusLM.broadcastnode.oPorts(1)._1 == 0)
          utest.assert(NexusLM.broadcastnode.oPorts(1)._2.name == "OthersinkModule.sink")

          utest.assert(NexusLM.broadcastnode.iPorts.size == 1)
          utest.assert(NexusLM.broadcastnode.iPorts(0)._1 == 0)
          utest.assert(NexusLM.broadcastnode.iPorts(0)._2.name == "sourceModule.source")
          // sourceModule.source.iPorts/oPorts
          utest.assert(sourceModule.source.iPorts.isEmpty)
          utest.assert(sourceModule.source.oPorts.size == 1)
          utest.assert(sourceModule.source.oPorts(0)._1 == 0)
          utest.assert(sourceModule.source.oPorts(0)._2.name == "broadcast.node")
          // sinkModule.sink.iPorts/oPorts
          utest.assert(sinkModule.sink.iPorts.size == 1)
          utest.assert(sinkModule.sink.iPorts(0)._1 == 0)
          utest.assert(sinkModule.sink.iPorts(0)._2.name == "broadcast.node")
          utest.assert(sinkModule.sink.oPorts.isEmpty)
          // OthersinkModule.sink.iPorts/oPorts
          utest.assert(OthersinkModule.sink.iPorts.size == 1)
          utest.assert(OthersinkModule.sink.iPorts(0)._1 == 1)
          utest.assert(OthersinkModule.sink.iPorts(0)._2.name == "broadcast.node")
          utest.assert(OthersinkModule.sink.oPorts.isEmpty)
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test("ResolveStar : iStar and oStar ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test oStar / iStar :
            * In the NexusNode.scala ,
            * resolveStar teturn  (iStar, oStar) = if (iKnown == 0 && oKnown == 0) (0, 0) else (1, 1)  */
          // NexusLM.broadcastnode.iStar/oStar
          utest.assert(NexusLM.broadcastnode.oStar == 1)
          utest.assert(NexusLM.broadcastnode.iStar == 1)
          // sourceModule.source.iStar/oStar
          utest.assert(sourceModule.source.oStar == 0)
          utest.assert(sourceModule.source.iStar == 0)
          // sinkModule.sink.iStar/oStar
          utest.assert(sinkModule.sink.oStar == 0)
          utest.assert(sinkModule.sink.iStar == 1)
          // OthersinkModule.sink.iStar/oStar
          utest.assert(OthersinkModule.sink.oStar == 0)
          utest.assert(OthersinkModule.sink.iStar == 0)
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test(" diParams and doParams ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test diParams / doParams :
            *  val diParams: Seq[DI] = iPorts.map { case (i, n, _, _) => n.doParams(i) }
            *  in this test , it  means that  :
            *  NexusLM.broadcastnode.diParams  =  sourceModule.source.doParams
            *  NexusLM.broadcastnode.diParams  =  sinkModule.sink.diParams++OthersinkModule.sink.diParams */
          // NexusLM.broadcastnode.diParams / doParams
          utest.assert(NexusLM.broadcastnode.diParams.size == 1)
          utest.assert(NexusLM.broadcastnode.diParams == sourceModule.source.doParams)
          utest.assert(NexusLM.broadcastnode.doParams.size == 2)
          utest.assert(NexusLM.broadcastnode.doParams == (sinkModule.sink.diParams++OthersinkModule.sink.diParams))
          // sourceModule.source.diParams / doParams
          utest.assert(sourceModule.source.diParams.isEmpty)
          utest.assert(sourceModule.source.doParams.size ==1)
          utest.assert(sourceModule.source.doParams == NexusLM.broadcastnode.diParams)
          // sinkModule.sink.diParams / doParams
          utest.assert(sinkModule.sink.diParams.size ==1)
          utest.assert(sinkModule.sink.diParams(0) == NexusLM.broadcastnode.doParams(0))
          utest.assert(sinkModule.sink.doParams.isEmpty)
          // OthersinkModule.sink.diParams / doParams
          utest.assert(OthersinkModule.sink.diParams.size ==1)
          utest.assert(OthersinkModule.sink.diParams(0) == NexusLM.broadcastnode.doParams(0))
          utest.assert(OthersinkModule.sink.doParams.isEmpty )
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test(" uoParams and uiParams ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test uoParams / uiParams :
            * uoParams: Seq[UO] = oPorts.map { case (o, n, _, _) => n.uiParams(o) }
            *  in this test , it  means that  :
            *  NexusLM.broadcastnode.uoParams  =  sinkModule.sink.uiParams++OthersinkModule.sink.uiParams
            *  NexusLM.broadcastnode.uiParams  =  sourceModule.source.uoParams */
          // NexusLM.broadcastnode.uiParams / uoParams
          utest.assert(NexusLM.broadcastnode.uiParams.size == 1)
          utest.assert(NexusLM.broadcastnode.uiParams == sourceModule.source.uoParams)
          utest.assert(NexusLM.broadcastnode.uoParams.size == 2)
          utest.assert(NexusLM.broadcastnode.uoParams == (sinkModule.sink.uiParams++OthersinkModule.sink.uiParams))
          // sourceModule.source.uiParams / uoParams
          utest.assert(sourceModule.source.uiParams.isEmpty)
          utest.assert(sourceModule.source.uoParams.size ==1)
          utest.assert(sourceModule.source.uoParams == NexusLM.broadcastnode.uiParams)
          // sinkModule.sink.uiParams / uoParams
          utest.assert(sinkModule.sink.uiParams.size ==1)
          utest.assert(sinkModule.sink.uiParams(0) == NexusLM.broadcastnode.uoParams(0))
          utest.assert(sinkModule.sink.uoParams.isEmpty)
          // OthersinkModule.sink.uiParams / uoParams
          utest.assert(OthersinkModule.sink.uiParams.size ==1)
          utest.assert(OthersinkModule.sink.uiParams(0) == NexusLM.broadcastnode.uoParams(0))
          utest.assert(OthersinkModule.sink.uoParams.isEmpty )
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

    test(" edgesIn and edgesOut ") {
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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
          /** test edgesIn / edgesOut :
            *  edges: Edges[EI, EO] = Edges(edgesIn, edgesOut)
            *  edgesIn: Seq[EI]  =(iPorts.zip(uiParams)).map { case ((o, n, p, s), i) => inner.edgeI(n.doParams(o), i, p, s) }
            *  edgesOut: Seq[EO] =(oPorts.zip(doParams)).map { case ((i, n, p, s), o) => outer.edgeO(o, n.uiParams(i), p, s) }  */
          // NexusLM.broadcastnode.edgesIn / edgesOut
          utest.assert(NexusLM.broadcastnode.iPorts.zip(NexusLM.broadcastnode.uiParams).head._1 == NexusLM.broadcastnode.iPorts.head)
          utest.assert(NexusLM.broadcastnode.iPorts.zip(NexusLM.broadcastnode.uiParams).head._2 == NexusLM.broadcastnode.uiParams.head)
          utest.assert(NexusLM.broadcastnode.edgesIn.contains(NexusLM.broadcastnode.inner.edgeI(sourceModule.source.doParams(0),
            NexusLM.broadcastnode.uiParams.head,
            NexusLM.broadcastnode.iPorts.head._3,
            NexusLM.broadcastnode.iPorts.head._4)))
          utest.assert(NexusLM.broadcastnode.edgesOut.contains(NexusLM.broadcastnode.outer.edgeO(
            NexusLM.broadcastnode.doParams.head,
            sinkModule.sink.uiParams(0),
            NexusLM.broadcastnode.oPorts(0)._3,
            NexusLM.broadcastnode.oPorts(0)._4)))
          utest.assert(NexusLM.broadcastnode.edgesOut.contains(NexusLM.broadcastnode.outer.edgeO(
            NexusLM.broadcastnode.doParams.head,
            sinkModule.sink.uiParams(0),
            NexusLM.broadcastnode.oPorts(1)._3,
            NexusLM.broadcastnode.oPorts(1)._4)))
          utest.assert(NexusLM.broadcastnode.edges.out ==  NexusLM.broadcastnode.edgesOut)
          utest.assert(NexusLM.broadcastnode.edges.in ==  NexusLM.broadcastnode.edgesIn)
          // sourceModule.source.edgesIn / edgesOut
          utest.assert(sourceModule.source.oPorts.zip(NexusLM.broadcastnode.doParams).head._1 == sourceModule.source.oPorts.head)
          utest.assert(sourceModule.source.oPorts.zip(NexusLM.broadcastnode.doParams).head._2 == sourceModule.source.doParams.head)
          utest.assert(sourceModule.source.edgesIn.isEmpty)
          utest.assert(sourceModule.source.edgesOut.contains(sourceModule.source.outer.edgeO(
            sourceModule.source.doParams.head,
            NexusLM.broadcastnode.uiParams(0),
            sourceModule.source.oPorts(0)._3,
            sourceModule.source.oPorts(0)._4)))
          // sinkModule.sink.edgesIn / edgesOut
          utest.assert(sinkModule.sink.iPorts.zip(NexusLM.broadcastnode.uiParams).head._1 == sinkModule.sink.iPorts.head)
          utest.assert(sinkModule.sink.iPorts.zip(NexusLM.broadcastnode.uiParams).head._2 == sinkModule.sink.uiParams.head)
          utest.assert(sinkModule.sink.edgesIn.contains(sinkModule.sink.inner.edgeI(NexusLM.broadcastnode.doParams(0),
            sinkModule.sink.uiParams.head,
            sinkModule.sink.iPorts.head._3,
            sinkModule.sink.iPorts.head._4)))
          utest.assert(sinkModule.sink.edgesOut.isEmpty)
          // OthersinkModule.sink.edgesIn / edgesOut
          utest.assert(OthersinkModule.sink.iPorts.zip(NexusLM.broadcastnode.uiParams).head._1 == OthersinkModule.sink.iPorts.head)
          utest.assert(OthersinkModule.sink.iPorts.zip(NexusLM.broadcastnode.uiParams).head._2 == OthersinkModule.sink.uiParams.head)
          utest.assert(OthersinkModule.sink.edgesIn.contains(OthersinkModule.sink.inner.edgeI(NexusLM.broadcastnode.doParams(0),
            OthersinkModule.sink.uiParams.head,
            OthersinkModule.sink.iPorts.head._3,
            OthersinkModule.sink.iPorts.head._4)))
          utest.assert(OthersinkModule.sink.edgesOut.isEmpty)
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

        val registered: Boolean = true
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = true

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )
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

          /** test bundleIn / bundleOut :
            *  edges: Edges[EI, EO] = Edges(edgesIn, edgesOut)
            *  bundleIn: Seq[BI] = edgesIn.map(e => chisel3.Wire(inner.bundleI(e)))
            *  bundleOut: Seq[BO] = edgesOut.map(e => chisel3.Wire(outer.bundleO(e)))  */
          // NexusLM.broadcastnode.bundleIn / bundleOut
          utest.assert(NexusLM.broadcast.node.bundleIn.head.getWidth == 32)
          utest.assert(NexusLM.broadcast.node.bundleIn.length == 1)
          utest.assert(NexusLM.broadcast.node.bundleOut.head.getWidth == 32)
          utest.assert(NexusLM.broadcast.node.bundleOut.tail.head.getWidth == 32)
          utest.assert(NexusLM.broadcast.node.bundleOut.length == 2)
          utest.assert(NexusLM.broadcast.node.bundleIn.head.getWidth == 32)
          // sourceModule.source.bundleIn / bundleOut
          utest.assert(sourceModule.source.bundleIn.isEmpty)
          utest.assert(sourceModule.source.bundleIn.length == 0)
          utest.assert(sourceModule.source.bundleOut.head.getWidth == 32)
          utest.assert(sourceModule.source.bundleOut.length == 1)
          // sinkModule.sink.bundleIn / bundleOut
          utest.assert(sinkModule.sink.bundleIn.head.getWidth == 32)
          utest.assert(sinkModule.sink.bundleIn.length == 1)
          utest.assert(sinkModule.sink.bundleOut.isEmpty)
          utest.assert(sinkModule.sink.bundleOut.length == 0)
          // OthersinkModule.sink.bundleIn / bundleOut
          utest.assert(OthersinkModule.sink.bundleIn.head.getWidth == 32)
          utest.assert(OthersinkModule.sink.bundleIn.length == 1)
          utest.assert(OthersinkModule.sink.bundleOut.isEmpty)
          utest.assert(OthersinkModule.sink.bundleOut.length == 0)
        }
      }
      val TopLM = LazyModule(new TopLazyModule())
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
    }

  }
}