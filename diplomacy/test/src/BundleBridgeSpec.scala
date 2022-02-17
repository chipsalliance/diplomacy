package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.{Data, _}
import diplomacy.bundlebridge.BundleBridgeNexus.{fillN, orReduction}
import diplomacy.bundlebridge.{BundleBridgeNexus, BundleBridgeNexusNode, BundleBridgeSink, BundleBridgeSource,BundleBridgeEphemeralNode,BundleBridgeIdentityNode}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import diplomacy.nodes.{NexusNode, RenderedEdge, SimpleNodeImp, SinkNode, SourceNode}
import utest._
import chisel3.util.random.FibonacciLFSR

object BundleBridgeSpec extends TestSuite {
  def tests: Tests = Tests {
    test("raw BundleBridge Source and Sink prototype.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val source_bundile = source.bundle
          //TODO : why source_io can't be connected to a Unit value
          val source_io = source.makeIO("source_io")
          //source_io := 4.U(32.W)
          //printf(p"${source_io}")

          source_bundile := 4.U(32.W)
          //printf(p"${source_bundile}")
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          //printf(p"${sink.bundle}")
          val sink_io = sink.makeIO("sink_io")
          //printf(p"${sink_io}")
          //chisel3.assert(sink.bundle === 4.U)
        }
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink :*= sourceModule.source
        lazy val module = new LazyModuleImp(this) {
          // := source    only have oPorts show the binding in the [[InwardNode]] on the other end.
          printf(p"${sourceModule.source.oPorts}")
          printf(p"${sourceModule.source.oPorts.size}")
          printf(p"${sourceModule.source.iPorts}")
          printf(p"${sourceModule.source.iPorts.size}")

          // source node's outwardnode have accPo that pass to oBindings
          // nothing
          printf(p"${sourceModule.source.iBindings}")
          // return  (0, BundleBridgeSink , query ,chipsalliance.rocketchip.config$EmptyParameters , SourceLine(NodeSpec.scala,47,25))
          printf(p"${sourceModule.source.oBindings}")


          // sink :=     only have iPorts show the binding in the [[OutwardNode]] on the other end.
          printf(p"${sinkModule.sink.oPorts}")
          printf(p"${sinkModule.sink.oPorts.size}")
          printf(p"${sinkModule.sink.iPorts}")
          printf(p"${sinkModule.sink.iPorts.size}")

          // sink node's inwardnode have accPi that pass to iBindings
          //
          // return  (0, BundleBridgeSource , query ,chipsalliance.rocketchip.config$EmptyParameters , SourceLine(NodeSpec.scala,47,25))
          printf(p"${sourceModule.source.iBindings}")
          // nothing
          printf(p"${sourceModule.source.oBindings}")

          printf(p"${sourceModule.source.doParams}")
          printf(p"${sourceModule.source.doParams.size}")
          printf(p"${sourceModule.source.diParams}")
          printf(p"${sourceModule.source.diParams.size}")
          //sinkModule.sink := sourceModule.source

          printf(p"${sourceModule.source.bundleIn}")
          printf(p"${sourceModule.source.bundleOut}")

        }
      }
      println(chisel3.stage.ChiselStage.emitSystemVerilog(LazyModule(new TopLazyModule).module))
    }

    test("BundleBridge Source and Sink normal usage") {
      implicit val p = Parameters.empty
      class BottomLazyModule extends LazyModule {
        val source = BundleBridgeSource(() => UInt(32.W))
        val anothersource = BundleBridgeSource()
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
        }
      }
      class TopLazyModule extends LazyModule {
        val bottom = LazyModule(new BottomLazyModule)
        // make sink node and connect.
        val sink = bottom.source.makeSink()
        //require(!doneSink, "Can only call makeSink() once")
        //val sinkx = bottom.source.makeSink()
        lazy val module = new LazyModuleImp(this) {
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      println(chisel3.stage.ChiselStage.emitSystemVerilog(LazyModule(new TopLazyModule).module))
    }

    test("BundleBridge Nexus prototype and normal usage") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))
      class DemoNexus(implicit valName: sourcecode.Name) extends BundleBridgeNexus[UInt](
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
          printf(p"${source_bundle}")
        }
      }

      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          printf(p"${sink.bundle}")
          chisel3.assert(sink.bundle === 4.U)
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T]) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = false
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = true // when false, connecting a source does not mandate connecting a sink
        val canshouldBeInlined: Boolean = false

        val broadcast: BundleBridgeNexus[T] = LazyModule(
          new BundleBridgeNexus[T](
            inputFn = BundleBridgeNexus.requireOne[T](registered),
            outputFn = BundleBridgeNexus.fillN[T](registered),
            default = default,
            inputRequiresOutput = inputRequiresOutput,
            shouldBeInlined = canshouldBeInlined
          )(p)
        )

        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
          printf(p"${broadcast.node.default.isDefined}")
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val OthersinkModule = LazyModule(new SinkLazyModule)
        val oherNexusLM = LazyModule(new DemoNexus)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))

        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink := NexusLM.broadcastnode
        OthersinkModule.sink := NexusLM.broadcastnode

        //oherNexusLM.node :*= sourceModule.source
        //sinkModule.sink := oherNexusLM.node
        //OthersinkModule.sink := oherNexusLM.node

        lazy val module = new LazyModuleImp(this) {
          printf(p"${NexusLM.broadcastnode.oStar}")
          printf(p"${NexusLM.broadcastnode.iStar}")
          printf(p"${NexusLM.broadcastnode.oBindings}")
          printf(p"${NexusLM.broadcastnode.iBindings}")
          printf(p"${NexusLM.broadcastnode.iPortMapping}")
          printf(p"${NexusLM.broadcastnode.oPortMapping}")
          printf(p"${NexusLM.broadcastnode.in}")
          printf(p"${NexusLM.broadcastnode.out}")
          printf(p"${NexusLM.broadcastnode.inputRequiresOutput}")
          printf(p"${NexusLM.broadcast.node.bundleIn}")
          printf(p"${NexusLM.broadcast.node.bundleOut}")
          printf(p"${NexusLM.broadcastnode.diParams}")
          printf(p"${NexusLM.broadcastnode.doParams}")
          printf(p"${NexusLM.broadcastnode.edges}")
          printf(p"${NexusLM.broadcastnode.edgesIn}")
          printf(p"${NexusLM.broadcastnode.edgesOut}")
          printf(p"${NexusLM.broadcastnode.iDirectPorts}")
          printf(p"${NexusLM.broadcastnode.index}")
          printf(p"${NexusLM.broadcastnode.instantiated}")
          printf(p"${NexusLM.broadcastnode.flexes}")
          printf(p"${NexusLM.broadcastnode.flexOffset}")
          printf(p"${NexusLM.broadcastnode.inner}")
          printf(p"${NexusLM.broadcastnode.inward}")
          printf(p"${NexusLM.broadcastnode.iPorts}")
          printf(p"${NexusLM.broadcastnode.oDirectPorts}")
          printf(p"${NexusLM.broadcastnode.outer}")
          printf(p"${NexusLM.broadcastnode.outward}")
          printf(p"${NexusLM.broadcastnode.oPorts}")

          printf(p"${NexusLM.broadcast.module}")
          //printf(p"${NexusLM.broadcast.module.defaultWireOpt}")
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      //chisel3.stage.ChiselStage.elaborate(ExampleLM.broadcast.module)
      //chisel3.stage.ChiselStage.elaborate(TopLM.module)
      //println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.NexusLM.module))
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
      //printf(p"${TopLM.NexusLM.broadcast.module.defaultWireOpt}")
    }

    test("BundleBridge Identity prototype and normal usage") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))
      class DemoNexus(implicit valName: sourcecode.Name) extends BundleBridgeNexus[UInt](
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
          printf(p"${source_bundle}")
        }
      }

      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          printf(p"${sink.bundle}")
          chisel3.assert(sink.bundle === 4.U)
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T])(implicit valName: sourcecode.Name) extends LazyModule {

        val nodeIdentity = BundleBridgeIdentityNode[T]()(valName)
        val nodeEphemeral = BundleBridgeEphemeralNode[T]()(valName)

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val EphemeralsourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        val EphemeralsinkModule = LazyModule(new SinkLazyModule)
        val oherNexusLM = LazyModule(new DemoNexus)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption))("nodeX"))

        //val IdentitysourceModule = LazyModule(new SourceLazyModule)
        //val IdentitysinkModule = LazyModule(new SinkLazyModule)


        oherNexusLM.node :*= sourceModule.source
        sinkModule.sink := oherNexusLM.node
        //OthersinkModule.sink := oherNexusLM.node

        NexusLM.nodeEphemeral :*= EphemeralsourceModule.source
        EphemeralsinkModule.sink := NexusLM.nodeEphemeral

        //NexusLM.nodeIdentity := IdentitysourceModule.source
        //IdentitysinkModule.sink := NexusLM.nodeIdentity




        lazy val module = new LazyModuleImp(this) {
        }
      }

      val TopLM = LazyModule(new TopLazyModule())
      //chisel3.stage.ChiselStage.elaborate(ExampleLM.broadcast.module)
      //chisel3.stage.ChiselStage.elaborate(TopLM.module)
      //println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.NexusLM.module))
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopLM.module))
      //printf(p"${TopLM.NexusLM.broadcast.module.defaultWireOpt}")
    }

    test("parameterd adder to test .") {
      case class UpwardParam(width: Int)
      case class DownwardParam(width: Int)
      case class EdgeParam(width: Int)

      // PARAMETER TYPES:      D       U     E    B
      object AdderNodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, UInt] {
        def edge(pd: DownwardParam, pu: UpwardParam, p: Parameters, sourceInfo: SourceInfo) = {
          if (pd.width < pu.width) EdgeParam(pd.width) else EdgeParam(pu.width)
        }

        def bundle(e: EdgeParam) = UInt(e.width.W)

        def render(e: EdgeParam) = RenderedEdge("blue", s"width = ${e.width}")
      }

      /** node for [[AdderDriver]] (source) */
      class AdderDriverNode(widths: Seq[DownwardParam])(implicit valName: sourcecode.Name)
        extends SourceNode(AdderNodeImp)(widths)

      /** node for [[AdderMonitor]] (sink) */
      class AdderMonitorNode(width: UpwardParam)(implicit valName: sourcecode.Name)
        extends SinkNode(AdderNodeImp)(Seq(width))

      /** node for [[Adder]] (nexus) */
      class AdderNode(dFn: Seq[DownwardParam] => DownwardParam,
                      uFn: Seq[UpwardParam] => UpwardParam)(implicit valName: sourcecode.Name)
        extends NexusNode(AdderNodeImp)(dFn, uFn)

      /** adder DUT (nexus) */
      class Adder(implicit p: Parameters) extends LazyModule {
        val node = new AdderNode(
          { case dps: Seq[DownwardParam] =>
            require(dps.forall(dp => dp.width == dps.head.width), "inward, downward adder widths must be equivalent")
            dps.head
          },
          { case ups: Seq[UpwardParam] =>
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
        val node = new AdderDriverNode(Seq.fill(numOutputs)(DownwardParam(width)))

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
        val nodeSeq = Seq.fill(numOperands) {
          new AdderMonitorNode(UpwardParam(width))
        }
        val nodeSum = new AdderMonitorNode(UpwardParam(width))

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
        val adder = LazyModule(new Adder)
        // 8 will be the downward-traveling widths from our drivers
        val drivers = Seq.fill(numOperands) {
          LazyModule(new AdderDriver(width = 8, numOutputs = 2))
        }
        // 4 will be the upward-traveling width from our monitor
        val monitor = LazyModule(new AdderMonitor(width = 4, numOperands = numOperands))

        // create edges via binding operators between nodes in order to define a complete graph
        drivers.foreach { driver => adder.node := driver.node }

        drivers.zip(monitor.nodeSeq).foreach { case (driver, monitorNode) => monitorNode := driver.node }
        monitor.nodeSum := adder.node

        lazy val module = new LazyModuleImp(this) {
          //when(monitor.module.io.error) {
          //  printf("something went wrong")
          // }
        }

        override lazy val desiredName = "AdderTestHarness"
      }

      val verilog = chisel3.stage.ChiselStage.emitVerilog(
        LazyModule(new AdderTestHarness()(Parameters.empty)).module
      )

      println(s"```verilog\n$verilog```")

    }

  }
}