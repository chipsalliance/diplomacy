package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.stage.ChiselStage
import chisel3.util.random.FibonacciLFSR
import chisel3.{Data, _}
import diplomacy.bundlebridge.{BundleBridgeNexus, BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import diplomacy.nodes._
import utest._

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
            OthersinkModule.sink.uiParams(0),
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

    test(" SourceNode && SinkNode resolveStar"){
      implicit val p = Parameters.empty

      case class CustomSourceNodeParams(width: Int)

      case class CustomSinkNodeParams(width: Int)

      case class CustomNodeEdgeParams(width: Int)

      class CustomNodeImp extends SimpleNodeImp[CustomSourceNodeParams, CustomSinkNodeParams, CustomNodeEdgeParams, UInt] {
        def edge(pd: CustomSourceNodeParams, pu: CustomSinkNodeParams, p: Parameters, sourceInfo: SourceInfo) = {
          if (pd.width < pu.width) CustomNodeEdgeParams(pd.width) else CustomNodeEdgeParams(pu.width)
        }
        def bundle(e: CustomNodeEdgeParams) = UInt(e.width.W)
        def render(e: CustomNodeEdgeParams) = RenderedEdge("blue", s"width = ${e.width}")
        override def mixO(pd: CustomSourceNodeParams, node: OutwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSourceNodeParams =
          pd
        override def mixI(pu: CustomSinkNodeParams, node: InwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSinkNodeParams =
          pu
      }

      /** node for [[AdderDriver]] (source) */
      class AdderDriverNode(widths: Seq[CustomSourceNodeParams])(implicit valName: sourcecode.Name)
        extends SourceNode(new CustomNodeImp)(widths)

      /** node for [[AdderMonitor]] (sink) */
      class AdderMonitorNode(width: Seq[CustomSinkNodeParams])(implicit valName: sourcecode.Name)
        extends SinkNode(new CustomNodeImp)(width)

      /** node for [[AdderReg]] (Adapter) */
      class AdderAdapterNode(dFn: CustomSourceNodeParams => CustomSourceNodeParams,
                             uFn: CustomSinkNodeParams => CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends AdapterNode(new CustomNodeImp)(dFn, uFn)

      /** driver (source)
        * drives one random number on multiple outputs */
      class AdderDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
        val node = new AdderDriverNode(Seq.fill(numOutputs)(CustomSourceNodeParams(width)))

        lazy val module = new LazyModuleImp(this) {
          // check that node parameters converge after negotiation
          val negotiatedWidths = node.edges.out.map(_.width)
          require(negotiatedWidths.forall(_ == negotiatedWidths.head), "outputs must all have agreed on same width")
          val finalWidth = negotiatedWidths.head

          // generate random addend (notice the use of the negotiated width)
          val randomAddend = FibonacciLFSR.maxPeriod(finalWidth)

          // drive signals
          node.out.foreach { case (addend, _) => addend := randomAddend }
        }
        override lazy val desiredName = "AdderDriver"
      }

      /** monitor (sink) */
      class AdderMonitor(width: Int, numOperands: Int)(implicit p: Parameters) extends LazyModule {
        val nodeSeq = Seq.fill(numOperands) { new AdderMonitorNode(Seq(CustomSinkNodeParams(width))) }
        lazy val module = new LazyModuleImp(this) {
        }
        override lazy val desiredName = "AdderMonitor"
      }

      /** top-level connector */
      class AdderTestHarness()(implicit p: Parameters) extends LazyModule {
        val numOperands = 1
        /**
        case 0 : sink node := source node
         */
        val drivers_0 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_0 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        drivers_0.zip(monitor_0.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        /**
        case 1 : sink node :=* source node
          */
        val drivers_1 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_1 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        drivers_1.zip(monitor_1.nodeSeq).foreach { case (driver, monitorNode) => monitorNode :=* driver.node }

        /**
        case 2 : sink node :*= source node
          */
        val drivers_2 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_2 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        drivers_2.zip(monitor_2.nodeSeq).foreach { case (driver, monitorNode) => monitorNode :*= driver.node }

        /**
        case 3 : sink node :*=* source node
          */
        val drivers_3 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_3 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        drivers_3.zip(monitor_3.nodeSeq).foreach { case (driver, monitorNode) => monitorNode :*=* driver.node }

        lazy val module = new LazyModuleImp(this) {
        }

        override lazy val desiredName = "AdderTestHarness"
      }
      val TopLM=LazyModule(new AdderTestHarness()(Parameters.empty))
      val verilog = chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
      //println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
      //TODO: why the verilog code generated by this top module have no detail , just clock and reset signals
      println(s"```verilog\n$verilog```")
      /**
      test source&sink  node resolveStar

        Source : resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int)
        = (iStar , oStar) = (0, po.size - oKnown)
        there are two cases :
        1. oStars == 0 , require (po.size == oKnown) , so (iStar , oStar) = (0, 0)
        2. oStars > 0  , (iStar , oStar) = (0, po.size - oKnown)

        Sink : resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int)
        = (iStar , oStar) = (pi.size - iKnown, 0)
        there are two cases :
        1. iStars == 0 , require (pi.size == iKnown) , so (iStar , oStar) = (0, 0)
        2. iStars > 0  , (iStar , oStar) = (pi.size - iKnown, 0)
        */
      //utest.assert(TopLM.drivers.head.node.iStar == 0)
      //utest.assert(TopLM.drivers.head.node.oStar == 0)
      //utest.assert(TopLM.drivers.last.node.iStar == 0)
      //utest.assert(TopLM.drivers.last.node.oStar == 0)
      /**
        * test case 0 : sink node := source node
        */
      utest.assert(TopLM.drivers_0.head.node.iStar == 0)
      utest.assert(TopLM.drivers_0.head.node.oStar == 0)

      utest.assert(TopLM.monitor_0.nodeSeq.head.iStar == 0)
      utest.assert(TopLM.monitor_0.nodeSeq.head.oStar == 0)
      /**
        * test case 1: sink node :=* source node
        * sourceNode.oStars can only be 0 or 1,so according oStar can only be 0 or 1
        */
      utest.assert(TopLM.drivers_1.head.node.iStar == 0)
      utest.assert(TopLM.drivers_1.head.node.oStar == 1)

      utest.assert(TopLM.monitor_1.nodeSeq.head.iStar == 0)
      utest.assert(TopLM.monitor_1.nodeSeq.head.oStar == 0)
      /**
        * test case 2: sink node :*= source node
        * sinkNode.iStars can only be 0 or 1,so according iStar can only be 0 or 1
        */
      utest.assert(TopLM.drivers_2.head.node.iStar == 0)
      utest.assert(TopLM.drivers_2.head.node.oStar == 0)

      utest.assert(TopLM.monitor_2.nodeSeq.head.iStar == 1)
      utest.assert(TopLM.monitor_2.nodeSeq.head.oStar == 0)
      /**
        * test case 3: sink node :*=* source node
        * bind flex
        */
      utest.assert(TopLM.drivers_3.head.node.iStar == 0)
      utest.assert(TopLM.drivers_3.head.node.oStar == 0)

      utest.assert(TopLM.monitor_3.nodeSeq.head.iStar == 0)
      utest.assert(TopLM.monitor_3.nodeSeq.head.oStar == 0)
    }

    test(" AdapterNode resolveStar"){
      implicit val p = Parameters.empty

      case class CustomSourceNodeParams(width: Int)

      case class CustomSinkNodeParams(width: Int)

      case class CustomNodeEdgeParams(width: Int)

      class CustomNodeImp extends SimpleNodeImp[CustomSourceNodeParams, CustomSinkNodeParams, CustomNodeEdgeParams, UInt] {
        def edge(pd: CustomSourceNodeParams, pu: CustomSinkNodeParams, p: Parameters, sourceInfo: SourceInfo) = {
          if (pd.width < pu.width) CustomNodeEdgeParams(pd.width) else CustomNodeEdgeParams(pu.width)
        }
        def bundle(e: CustomNodeEdgeParams) = UInt(e.width.W)
        def render(e: CustomNodeEdgeParams) = RenderedEdge("blue", s"width = ${e.width}")
        override def mixO(pd: CustomSourceNodeParams, node: OutwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSourceNodeParams =
          pd
        override def mixI(pu: CustomSinkNodeParams, node: InwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSinkNodeParams =
          pu
      }

      /** node for [[AdderDriver]] (source) */
      class AdderDriverNode(widths: Seq[CustomSourceNodeParams])(implicit valName: sourcecode.Name)
        extends SourceNode(new CustomNodeImp)(widths)

      /** node for [[AdderMonitor]] (sink) */
      class AdderMonitorNode(width: Seq[CustomSinkNodeParams])(implicit valName: sourcecode.Name)
        extends SinkNode(new CustomNodeImp)(width)

      /** node for [[AdderReg]] (Adapter) */
      class AdderAdapterNode(dFn: CustomSourceNodeParams => CustomSourceNodeParams,
                             uFn: CustomSinkNodeParams => CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends AdapterNode(new CustomNodeImp)(dFn, uFn)

      /** driver (source)
        * drives one random number on multiple outputs */
      class AdderDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
        val node = new AdderDriverNode(Seq.fill(numOutputs)(CustomSourceNodeParams(width)))

        lazy val module = new LazyModuleImp(this) {
          // check that node parameters converge after negotiation
          val negotiatedWidths = node.edges.out.map(_.width)
          require(negotiatedWidths.forall(_ == negotiatedWidths.head), "outputs must all have agreed on same width")
          val finalWidth = negotiatedWidths.head

          // generate random addend (notice the use of the negotiated width)
          val randomAddend = FibonacciLFSR.maxPeriod(finalWidth)

          // drive signals
          node.out.foreach { case (addend, _) => addend := randomAddend }
        }
        override lazy val desiredName = "AdderDriver"
      }

      /** monitor (sink) */
      class AdderMonitor(width: Int, numOperands: Int)(implicit p: Parameters) extends LazyModule {
        val nodeSeq = Seq.fill(numOperands) { new AdderMonitorNode(Seq(CustomSinkNodeParams(width))) }
        lazy val module = new LazyModuleImp(this) {
        }
        override lazy val desiredName = "AdderMonitor"
      }

      class AdderReg(implicit p: Parameters) extends LazyModule {
        val nodeSumAdapter = new AdderAdapterNode(
          {case dps:CustomSourceNodeParams => dps},
          {case ups:CustomSinkNodeParams => ups}
        )
        lazy val module = new LazyModuleImp(this) {
          (nodeSumAdapter.in zip nodeSumAdapter.out) foreach { case ((in, _), (out, _)) =>
            out := RegNext(in) }
        }

        override lazy val desiredName = "AdderMonitor"
      }

      /** top-level connector */
      class AdderTestHarness()(implicit p: Parameters) extends LazyModule {
        val numOperands = 1
        /**
        case 0 : sink node := adapter node := source node
          */
        val drivers_0 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_0 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        val Register_0 = LazyModule(new AdderReg)
        drivers_0.zip(monitor_0.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := Register_0.nodeSumAdapter := driver.node }
        /**
        case 1 : sink node := adapter node :*=  source node
          */
        val drivers_1 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_1 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        val Register_1 = LazyModule(new AdderReg)
        drivers_1.zip(monitor_1.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := Register_1.nodeSumAdapter :*= driver.node }

        /**
        case 2 : sink node :=* adapter node :=  source node
          */
        val drivers_2 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 1)) }
        // 8 will be the upward-traveling width from our monitor
        val monitor_2 = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))
        val Register_2 = LazyModule(new AdderReg)
        drivers_2.zip(monitor_2.nodeSeq).foreach { case (driver, monitorNode) => monitorNode :=* Register_2.nodeSumAdapter := driver.node }
        /**
        case 3 : sink node :=* adapter node :*=  source node
        ERROR : require(oStars + iStars <= 1)
          */

        lazy val module = new LazyModuleImp(this) {
        }

        override lazy val desiredName = "AdderTestHarness"
      }
      val TopLM=LazyModule(new AdderTestHarness()(Parameters.empty))
      val verilog = chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
      //println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
      //TODO: why the verilog code generated by this top module have no detail , just clock and reset signals
      println(s"```verilog\n$verilog```")
      /**
      test adapter  node resolveStar
        Adapter : resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = (iStar , oStar)
        there are three cases :
        0. iStars == 1 && oStars == 1,  (0, iKnown - oKnown)
        1. iStars == 1 && oStars == 0,  (oKnown - iKnown, 0)
        2. iStars == 0 && oStars == 0 , (0, 0)
        */
      /**
        * test case 0 : sink node := adapter node :=  source node
        */
      utest.assert(TopLM.Register_0.nodeSumAdapter.iStar == 0)
      utest.assert(TopLM.Register_0.nodeSumAdapter.oStar == 0)
      /**
        * test case 1: sink node := adapter node :*=  source node
        */
      utest.assert(TopLM.Register_1.nodeSumAdapter.iStar == 1)
      utest.assert(TopLM.Register_1.nodeSumAdapter.oStar == 0)
      /**
        * test case 2: sink node :=* adapter node :=  source node
        */
      utest.assert(TopLM.Register_2.nodeSumAdapter.iStar == 0)
      utest.assert(TopLM.Register_2.nodeSumAdapter.oStar == 1)
    }

    test(" JunctionNode resolveStar"){
      implicit val p = Parameters.empty

      case class CustomSourceNodeParams(width: Int)

      case class CustomSinkNodeParams(width: Int)

      case class CustomNodeEdgeParams(width: Int)

      class CustomNodeImp extends SimpleNodeImp[CustomSourceNodeParams, CustomSinkNodeParams, CustomNodeEdgeParams, UInt] {
        def edge(pd: CustomSourceNodeParams, pu: CustomSinkNodeParams, p: Parameters, sourceInfo: SourceInfo) = {
          if (pd.width < pu.width) CustomNodeEdgeParams(pd.width) else CustomNodeEdgeParams(pu.width)
        }
        def bundle(e: CustomNodeEdgeParams) = UInt(e.width.W)
        def render(e: CustomNodeEdgeParams) = RenderedEdge("blue", s"width = ${e.width}")
        override def mixO(pd: CustomSourceNodeParams, node: OutwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSourceNodeParams =
          pd
        override def mixI(pu: CustomSinkNodeParams, node: InwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSinkNodeParams =
          pu
      }

      /** node for [[AdderDriver]] (source) */
      class AdderDriverNode(widths: Seq[CustomSourceNodeParams])(implicit valName: sourcecode.Name)
        extends SourceNode(new CustomNodeImp)(widths)

      /** node for [[AdderMonitor]] (sink) */
      class AdderMonitorNode(width: Seq[CustomSinkNodeParams])(implicit valName: sourcecode.Name)
        extends SinkNode(new CustomNodeImp)(width)

      /** node for [[AdderJunction]] (Junction) */
      class AdderJunctionNode(dFn: Seq[CustomSourceNodeParams] => Seq[CustomSourceNodeParams],
                      uFn: Seq[CustomSinkNodeParams] => Seq[CustomSinkNodeParams])(implicit valName: sourcecode.Name)
        extends JunctionNode(new CustomNodeImp)(dFn, uFn)

      /** driver (source)
        * drives one random number on multiple outputs */
      class AdderDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
        val node = new AdderDriverNode(Seq.fill(numOutputs)(CustomSourceNodeParams(width)))

        lazy val module = new LazyModuleImp(this) {
          // check that node parameters converge after negotiation
          val negotiatedWidths = node.edges.out.map(_.width)
          require(negotiatedWidths.forall(_ == negotiatedWidths.head), "outputs must all have agreed on same width")
          val finalWidth = negotiatedWidths.head

          // generate random addend (notice the use of the negotiated width)
          val randomAddend = FibonacciLFSR.maxPeriod(finalWidth)

          // drive signals
          node.out.foreach { case (addend, _) => addend := randomAddend }
        }
        override lazy val desiredName = "AdderDriver"
      }

      /** monitor (sink) */
      class AdderMonitor(width: Int, numOperands: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
        val nodeSeq = Seq.fill(numOperands) { new AdderMonitorNode(Seq.fill(numOutputs)(CustomSinkNodeParams(width))) }
        lazy val module = new LazyModuleImp(this) {
        }
        override lazy val desiredName = "AdderMonitor"
      }

      /** junction  */
      class AdderJunction(implicit p: Parameters) extends LazyModule {
        val junctionNode = new AdderJunctionNode (
          { case dps: Seq[CustomSourceNodeParams] =>
            require(dps.forall(dp => dp.width == dps.head.width), "inward, downward adder widths must be equivalent")
            dps
          },
          { case ups: Seq[CustomSinkNodeParams] =>
            require(ups.forall(up => up.width == ups.head.width), "outward, upward adder widths must be equivalent")
            ups
          }
        )
        lazy val module = new LazyModuleImp(this) {
        }
        override lazy val desiredName = "AdderJunction"
      }


      /** top-level connector */
      class AdderTestHarness()(implicit p: Parameters) extends LazyModule {
        val numOperands = 1
        /**
        {{{   case 0 :
          *   val jbar = LazyModule(new JBar)
          *   slave1.node := jbar.node
          *   slave2.node := jbar.node
          *   extras.node :=* jbar.node
          *   jbar.node :*= masters1.node
          *   jbar.node :*= masters2.node
          * }}}
          */
          //TODO: ERROR[module AdderJunction]  Reference bundleOut_2 is not fully initialized. : bundleOut_2 <= VOID
          // why the edge negotiated can generate according bundle
        val masters_0_1 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 2)) }
        val masters_0_2 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 2, numOutputs = 2)) }
        // 8 will be the upward-traveling width from our monitor
        val slave_0_1 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands, numOutputs = 1))
        val slave_0_2 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands, numOutputs = 1))
        val extras_0 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands, numOutputs = 2))

        val jbar_0 = LazyModule(new AdderJunction)
        slave_0_1.nodeSeq.head := jbar_0.junctionNode
        slave_0_2.nodeSeq.head := jbar_0.junctionNode
        extras_0.nodeSeq.head :=* jbar_0.junctionNode
        jbar_0.junctionNode :*= masters_0_1.head.node
        jbar_0.junctionNode :*= masters_0_2.head.node

        lazy val module = new LazyModuleImp(this) {
        }

        override lazy val desiredName = "AdderTestHarness"
      }
      val TopLM=LazyModule(new AdderTestHarness()(Parameters.empty))
      val verilog = chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
      //println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
      //TODO: why the verilog code generated by this top module have no detail , just clock and reset signals
      println(s"```verilog\n$verilog```")
      /**
        * test case 0
        */
      println(TopLM.jbar_0.junctionNode.iStar)
      println(TopLM.jbar_0.junctionNode.oStar)
      println(TopLM.jbar_0.junctionNode.uRatio)
      println(TopLM.jbar_0.junctionNode.dRatio)
      println(TopLM.jbar_0.junctionNode.multiplicity)
    }

    test("NexusNode resolveStar"){
      implicit val p = Parameters.empty

      case class CustomSourceNodeParams(width: Int)

      case class CustomSinkNodeParams(width: Int)

      case class CustomNodeEdgeParams(width: Int)

      class CustomNodeImp extends SimpleNodeImp[CustomSourceNodeParams, CustomSinkNodeParams, CustomNodeEdgeParams, UInt] {
        def edge(pd: CustomSourceNodeParams, pu: CustomSinkNodeParams, p: Parameters, sourceInfo: SourceInfo) = {
          if (pd.width < pu.width) CustomNodeEdgeParams(pd.width) else CustomNodeEdgeParams(pu.width)
        }
        def bundle(e: CustomNodeEdgeParams) = UInt(e.width.W)
        def render(e: CustomNodeEdgeParams) = RenderedEdge("blue", s"width = ${e.width}")
        override def mixO(pd: CustomSourceNodeParams, node: OutwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSourceNodeParams =
          pd
        override def mixI(pu: CustomSinkNodeParams, node: InwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSinkNodeParams =
          pu
      }

      /** node for [[AdderDriver]] (source) */
      class AdderDriverNode(widths: Seq[CustomSourceNodeParams])(implicit valName: sourcecode.Name)
        extends SourceNode(new CustomNodeImp)(widths)

      /** node for [[AdderMonitor]] (sink) */
      class AdderMonitorNode(width: CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends SinkNode(new CustomNodeImp)(Seq(width))

      /** node for [[Adder]] (nexus) */
      class AdderNode(dFn: Seq[CustomSourceNodeParams] => CustomSourceNodeParams,
                      uFn: Seq[CustomSinkNodeParams] => CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends NexusNode(new CustomNodeImp)(dFn, uFn)

      /** adder DUT (nexus) */
      class Adder(implicit p: Parameters) extends LazyModule {
        val node = new AdderNode (
          { case dps: Seq[CustomSourceNodeParams] =>
            require(dps.forall(dp => dp.width == dps.head.width), "inward, downward adder widths must be equivalent")
            dps.head
          },
          { case ups: Seq[CustomSinkNodeParams] =>
            require(ups.forall(up => up.width == ups.head.width), "outward, upward adder widths must be equivalent")
            ups.head
          }
        )

        lazy val module = new LazyModuleImp(this) {
          require(node.in.size >= 2)
          node.out.head._1 := node.in.unzip._1.reduce(_ + _)
        }
        override lazy val desiredName = "Adder"
      }

      /** driver (source)
        * drives one random number on multiple outputs */
      class AdderDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
        val node = new AdderDriverNode(Seq.fill(numOutputs)(CustomSourceNodeParams(width)))

        lazy val module = new LazyModuleImp(this) {
          // check that node parameters converge after negotiation
          val negotiatedWidths = node.edges.out.map(_.width)
          require(negotiatedWidths.forall(_ == negotiatedWidths.head), "outputs must all have agreed on same width")
          val finalWidth = negotiatedWidths.head

          // generate random addend (notice the use of the negotiated width)
          val randomAddend = FibonacciLFSR.maxPeriod(finalWidth)

          // drive signals
          node.out.foreach { case (addend, _) => addend := randomAddend }
        }

        override lazy val desiredName = "AdderDriver"
      }

      /** monitor (sink) */
      class AdderMonitor(width: Int, numOperands: Int)(implicit p: Parameters) extends LazyModule {
        val nodeSeq = Seq.fill(numOperands) { new AdderMonitorNode(CustomSinkNodeParams(width)) }
        val nodeSum = new AdderMonitorNode(CustomSinkNodeParams(width))

        lazy val module = new LazyModuleImp(this) {
          val io = IO(new Bundle {
            val error = Output(Bool())
          })
          // print operation
          printf(nodeSeq.map(node => p"${node.in.head._1}").reduce(_ + p" + " + _) + p" = ${nodeSum.in.head._1}")
          // basic correctness checking
          io.error := nodeSum.in.head._1 =/= nodeSeq.map(_.in.head._1).reduce(_ + _)
        }

        override lazy val desiredName = "AdderMonitor"
      }

      /** top-level connector */
      class AdderTestHarness()(implicit p: Parameters) extends LazyModule {
        val numOperands = 2

        /**
        case 0 : sink node := nexus node :=  source node
          */
        val adder_0 = LazyModule(new Adder)
        val drivers_0 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 4, numOutputs = 2)) }
        val monitor_0 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands))
        // create edges via binding operators between nodes in order to define a complete graph
        drivers_0.foreach{ driver => adder_0.node := driver.node }
        drivers_0.zip(monitor_0.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        monitor_0.nodeSum := adder_0.node
        /**
        case 1 : sink node := nexus node :*=  source node
          */
        val adder_1 = LazyModule(new Adder)
        val drivers_1 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 4, numOutputs = 2)) }
        val monitor_1 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands))
        // create edges via binding operators between nodes in order to define a complete graph
        drivers_1.foreach{ driver => adder_1.node :*= driver.node }
        drivers_1.zip(monitor_1.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        monitor_1.nodeSum := adder_1.node
        /**
        case 2 : sink node :=* nexus node :=  source node
          */
        val adder_2 = LazyModule(new Adder)
        val drivers_2 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 4, numOutputs = 2)) }
        val monitor_2 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands))
        // create edges via binding operators between nodes in order to define a complete graph
        drivers_2.foreach{ driver => adder_2.node := driver.node }
        drivers_2.zip(monitor_2.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        monitor_2.nodeSum :=* adder_2.node
        /**
        case 3 : sink node :=* nexus node :*=  source node
          */
          //TODO: there may be a question , in case 3 ,
          // nexusNode.iKnow == 0 && nexusNode.oKnow == 0 => nexusNode.iStar==0 && nexusNode.oStar==0
          // so like that  nexusNode is nothing in a bind like that
        //val adder_3 = LazyModule(new Adder)
        //val drivers_3 = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 4, numOutputs = 2)) }
        //val monitor_3 = LazyModule(new AdderMonitor(width = 2, numOperands = numOperands))
        // create edges via binding operators between nodes in order to define a complete graph
        //drivers_3.foreach{ driver => adder_3.node :*= driver.node }
        //drivers_3.zip(monitor_3.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        //monitor_3.nodeSum :=* adder_3.node

        //TODO: why there is an ERROR :value io is not a member of diplomacy.lazymodule.LazyModuleImp
        lazy val module = new LazyModuleImp(this) {
          //when(monitor.module.io.error) {
          //  printf("something went wrong")
          //}
        }

        override lazy val desiredName = "AdderTestHarness"
      }
      val TopLM=LazyModule(new AdderTestHarness()(Parameters.empty))

      val verilog = chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
      //println(TopLM.monitor.module.io.error)
      println(s"```verilog\n$verilog```")
      /**
      test nexus  node resolveStar
        Adapter : resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = (iStar , oStar)
        if (iKnown == 0 && oKnown == 0) (0, 0) else (1, 1)
        there are four cases :
        */
      /**
        * test case 0 : sink node := nexus node :=  source node
        */
      utest.assert(TopLM.adder_0.node.iStar == 1)
      utest.assert(TopLM.adder_0.node.oStar == 1)
      /**
        * test case 1: sink node := nexus node :*=  source node
        */
      utest.assert(TopLM.adder_1.node.iStar == 1)
      utest.assert(TopLM.adder_1.node.oStar == 1)
      /**
        * test case 2: sink node :=* nexus node :=  source node
        */
      utest.assert(TopLM.adder_2.node.iStar == 1)
      utest.assert(TopLM.adder_2.node.oStar == 1)
      /**
        * test case 3: sink node :=* nexus node :*=  source node
        */
      //utest.assert(TopLM.adder_3.node.iStar == 1)
      //utest.assert(TopLM.adder_3.node.oStar == 1)
    }

    test("example add"){
      implicit val p = Parameters.empty

      case class CustomSourceNodeParams(width: Int)

      case class CustomSinkNodeParams(width: Int)

      case class CustomNodeEdgeParams(width: Int)

      class CustomNodeImp extends SimpleNodeImp[CustomSourceNodeParams, CustomSinkNodeParams, CustomNodeEdgeParams, UInt] {
        def edge(pd: CustomSourceNodeParams, pu: CustomSinkNodeParams, p: Parameters, sourceInfo: SourceInfo) = {
          if (pd.width < pu.width) CustomNodeEdgeParams(pd.width) else CustomNodeEdgeParams(pu.width)
        }
        def bundle(e: CustomNodeEdgeParams) = UInt(e.width.W)
        def render(e: CustomNodeEdgeParams) = RenderedEdge("blue", s"width = ${e.width}")
        override def mixO(pd: CustomSourceNodeParams, node: OutwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSourceNodeParams =
          pd
        override def mixI(pu: CustomSinkNodeParams, node: InwardNode[CustomSourceNodeParams, CustomSinkNodeParams, UInt]): CustomSinkNodeParams =
          pu
      }

      /** node for [[AdderDriver]] (source) */
      class AdderDriverNode(widths: Seq[CustomSourceNodeParams])(implicit valName: sourcecode.Name)
        extends SourceNode(new CustomNodeImp)(widths)

      /** node for [[AdderMonitor]] (sink) */
      class AdderMonitorNode(width: CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends SinkNode(new CustomNodeImp)(Seq(width))

      /** node for [[Adder]] (nexus) */
      class AdderNode(dFn: Seq[CustomSourceNodeParams] => CustomSourceNodeParams,
                      uFn: Seq[CustomSinkNodeParams] => CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends NexusNode(new CustomNodeImp)(dFn, uFn)

      /** node for [[AdderReg]] (Adapter) */
      class AdderAdapterNode(dFn: CustomSourceNodeParams => CustomSourceNodeParams,
                      uFn: CustomSinkNodeParams => CustomSinkNodeParams)(implicit valName: sourcecode.Name)
        extends AdapterNode(new CustomNodeImp)(dFn, uFn)

      /** adder DUT (nexus) */
      class Adder(implicit p: Parameters) extends LazyModule {
        val node = new AdderNode (
          { case dps: Seq[CustomSourceNodeParams] =>
            require(dps.forall(dp => dp.width == dps.head.width), "inward, downward adder widths must be equivalent")
            dps.head
          },
          { case ups: Seq[CustomSinkNodeParams] =>
            require(ups.forall(up => up.width == ups.head.width), "outward, upward adder widths must be equivalent")
            ups.head
          }
        )

        lazy val module = new LazyModuleImp(this) {
          require(node.in.size >= 2)
          node.out.head._1 := node.in.unzip._1.reduce(_ + _)
        }
        override lazy val desiredName = "Adder"
      }

      /** driver (source)
        * drives one random number on multiple outputs */
      class AdderDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
        val node = new AdderDriverNode(Seq.fill(numOutputs)(CustomSourceNodeParams(width)))

        lazy val module = new LazyModuleImp(this) {
          // check that node parameters converge after negotiation
          val negotiatedWidths = node.edges.out.map(_.width)
          require(negotiatedWidths.forall(_ == negotiatedWidths.head), "outputs must all have agreed on same width")
          val finalWidth = negotiatedWidths.head

          // generate random addend (notice the use of the negotiated width)
          val randomAddend = FibonacciLFSR.maxPeriod(finalWidth)

          // drive signals
          node.out.foreach { case (addend, _) => addend := randomAddend }
        }

        override lazy val desiredName = "AdderDriver"
      }

      /** monitor (sink) */
      class AdderMonitor(width: Int, numOperands: Int)(implicit p: Parameters) extends LazyModule {
        val nodeSeq = Seq.fill(numOperands) { new AdderMonitorNode(CustomSinkNodeParams(width)) }
        val nodeSum = new AdderMonitorNode(CustomSinkNodeParams(width))

        lazy val module = new LazyModuleImp(this) {
          val io = IO(new Bundle {
            val error = Output(Bool())
          })
          // print operation
          printf(nodeSeq.map(node => p"${node.in.head._1}").reduce(_ + p" + " + _) + p" = ${nodeSum.in.head._1}")
          // basic correctness checking
          io.error := nodeSum.in.head._1 =/= nodeSeq.map(_.in.head._1).reduce(_ + _)
        }

        override lazy val desiredName = "AdderMonitor"
      }

      class AdderReg(implicit p: Parameters) extends LazyModule
      {
        val nodeSumAdapter = new AdderAdapterNode(
          {case dps:CustomSourceNodeParams => dps},
          {case ups:CustomSinkNodeParams => ups}
        )
        lazy val module = new LazyModuleImp(this) {
          (nodeSumAdapter.in zip nodeSumAdapter.out) foreach { case ((in, _), (out, _)) =>
          out := RegNext(in) }
        }

        override lazy val desiredName = "AdderMonitor"
      }

      /** top-level connector */
      class AdderTestHarness()(implicit p: Parameters) extends LazyModule {
        val numOperands = 2
        val adder = LazyModule(new Adder)
        // 8 will be the downward-traveling widths from our drivers
        val drivers = Seq.fill(numOperands) { LazyModule(new AdderDriver(width = 16, numOutputs = 2)) }
        // 4 will be the upward-traveling width from our monitor
        val monitor = LazyModule(new AdderMonitor(width = 8, numOperands = numOperands))
        val Register = LazyModule(new AdderReg)

        /**Hint:  bind like
          * a:=* b(nexus node)
          * b:*= c(source node)
          * there is a ERROR with c node:  po.size =/= oKnown
          * because c.oKnown+=b.iStar   and b.iStar = 0 in this case.
          */
        // create edges via binding operators between nodes in order to define a complete graph
        drivers.foreach{ driver => adder.node := driver.node }

        drivers.zip(monitor.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        //monitor.nodeSum := adder.node

        Register.nodeSumAdapter := adder.node
        monitor.nodeSum :=* Register.nodeSumAdapter

        //TODO: why there is an ERROR :value io is not a member of diplomacy.lazymodule.LazyModuleImp
        lazy val module = new LazyModuleImp(this) {
          //when(monitor.module.io.error) {
          //  printf("something went wrong")
          //}
        }

        override lazy val desiredName = "AdderTestHarness"
      }
      val TopLM=LazyModule(new AdderTestHarness()(Parameters.empty))

      //val verilog = (new ChiselStage).emitVerilog(
      //   TopLM.monitor.module
      //)
      val verilog = chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module)
      //println(TopLM.monitor.module.io.error)
      println(s"```verilog\n$verilog```")
      /**
      test source node resolveStar

      resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int)
      = (iStar , oStar) = (0, po.size - oKnown)
        there are two cases :
        1. oStars == 0 , require (po.size == oKnown) , so (iStar , oStar) = (0, 0)
        2. oStars > 0  , (iStar , oStar) = (0, po.size - oKnown)
       */
      println(TopLM.adder.node.iStar)
      println(TopLM.adder.node.oStar)

      println(TopLM.Register.nodeSumAdapter.iStar)
      println(TopLM.Register.nodeSumAdapter.oStar)
    }

  }
}