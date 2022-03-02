package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.{Data, _}
import diplomacy.bundlebridge.BundleBridgeNexus.{fillN, orReduction}
import diplomacy.bundlebridge.{BundleBridgeEphemeralNode, BundleBridgeIdentityNode, BundleBridgeNexus, BundleBridgeNexusNode, BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import diplomacy.nodes.{BIND_QUERY, BIND_STAR, NexusNode, NodeBinding, RenderedEdge, SimpleNodeImp, SinkNode, SourceNode}
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
          //val source_io = source.makeIO("source_io")
          source_bundile := 4.U(32.W)
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          val sink_io = sink.makeIO("sink_io")
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink :*= sourceModule.source
        lazy val module = new LazyModuleImp(this) {
          //test oPorts
          // lazy val oPorts: Seq[(Int, InwardNode[DO, UO, BO], Parameters, SourceInfo)] = oDirectPorts.map(oTrace)
          //  := source    only have oPorts show the binding in the [[InwardNode]] on the other end.
          //printf(p"${sourceModule.source.oPorts}")
          utest.assert(sourceModule.source.oPorts.size == 1)
          utest.assert(sourceModule.source.oPorts.head._1 == 0)
          utest.assert(sourceModule.source.oPorts.head._2.name == "sinkModule.sink")
          utest.assert(sourceModule.source.oPorts.head._2.index == 0)
          utest.assert(sourceModule.source.oPorts.head._2.lazyModule == sinkModule)
          // sinknode :*=
          utest.assert(sinkModule.sink.oPorts.size == 0)
          utest.assert(sinkModule.sink.oPorts.isEmpty)
          //test iPorts
          //lazy val iPorts: Seq[(Int, OutwardNode[DI, UI, BI], Parameters, SourceInfo)] = iDirectPorts.map(iTrace)
          // := source do not have iPorts (  [OutwardNode]] on the other end of this binding.  )
          //printf(p"${sourceModule.source.iPorts}")
          utest.assert(sourceModule.source.iPorts.isEmpty)
          utest.assert(sourceModule.source.iPorts.size == 0)
          utest.assert(sinkModule.sink.iPorts.size == 1)
          utest.assert(sinkModule.sink.iPorts.head._1 == 0)
          utest.assert(sinkModule.sink.iPorts.head._2.name == "sourceModule.source")
          utest.assert(sinkModule.sink.iPorts.head._2.index == 0)
          utest.assert(sinkModule.sink.iPorts.head._2.lazyModule == sourceModule)
          // test iBindings
          // source node's outwardnode have accPo that pass to oBindings
          //iBindings : immutable.Seq[(Int, OutwardNode[DI, UI, BI], NodeBinding, Parameters, SourceInfo)]
          utest.assert(sourceModule.source.iBindings.isEmpty)
          utest.assert(sinkModule.sink.iBindings.head._1 == 0)
          utest.assert(sinkModule.sink.iBindings.head._2.name == "sourceModule.source")
          utest.assert(sinkModule.sink.iBindings.head._3 == BIND_STAR)
          // test oBindings
          //printf(p"${sourceModule.source.oBindings}")
          utest.assert(sourceModule.source.oBindings.head._1 == 0)
          utest.assert(sourceModule.source.oBindings.head._2.name == "sinkModule.sink")
          utest.assert(sourceModule.source.oBindings.head._3 == BIND_QUERY)
          utest.assert(sinkModule.sink.oBindings.isEmpty)
          //test diParams
          utest.assert(sourceModule.source.diParams.isEmpty)
          utest.assert(sinkModule.sink.diParams.size == 1)
          //test doParams
          utest.assert(sourceModule.source.doParams.size == 1)
          utest.assert(sinkModule.sink.doParams.isEmpty)
        }
      }
      val TopModule = LazyModule(new TopLazyModule)
      println(chisel3.stage.ChiselStage.emitSystemVerilog(TopModule.module))
      //test bundlein/bundleout
      //bundleOut: Seq[BO] = edgesOut.map(e => chisel3.Wire(outer.bundleO(e)))
      //println(TopModule.sourceModule.source.bundleOut(0))
      utest.assert(TopModule.sourceModule.source.bundleIn.isEmpty)
      utest.assert(TopModule.sourceModule.source.bundleOut(0).getWidth == 32)
      utest.assert(TopModule.sinkModule.sink.bundleIn(0).getWidth == 32)
      utest.assert(TopModule.sinkModule.sink.bundleOut.isEmpty)
      //test danglesIn/bundleout
      //@param serial the global [[BaseNode.serial]] number of the [[BaseNode]] that this [[HalfEdge]] connects to.
      //@param index  the `index` in the [[BaseNode]]'s input or output port list that this [[HalfEdge]] belongs to.
      //FixMe : MixedNode.scala line 32 maybe have some mistake
      utest.assert(TopModule.sourceModule.source.danglesIn.isEmpty)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).source.serial == 0)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).source.index  == 0)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).sink.serial == 1)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).sink.index == 0)
      utest.assert(TopModule.sourceModule.source.danglesOut(0).name == "source_out")
      utest.assert(TopModule.sourceModule.source.danglesOut(0).flipped == false)
      println(TopModule.sourceModule.source.danglesOut(0).data.getClass )
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).source.serial == 0)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).source.index  == 0)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).sink.serial  == 1)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).sink.index  == 0)
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).name == "sink_in")
      utest.assert(TopModule.sinkModule.sink.danglesIn(0).flipped  ==  true)
      println(TopModule.sinkModule.sink.danglesIn(0).data.getClass)
      utest.assert(TopModule.sinkModule.sink.danglesOut.isEmpty)
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
        //val nodeEphemeral = BundleBridgeEphemeralNode[T]()(valName)

        lazy val module = new LazyModuleImp(this) {
        }
      }

      class TopLazyModule extends LazyModule {
        //val sourceModule = LazyModule(new SourceLazyModule)
        //val EphemeralsourceModule = LazyModule(new SourceLazyModule)
        //val sinkModule = LazyModule(new SinkLazyModule)
        //val EphemeralsinkModule = LazyModule(new SinkLazyModule)
        val oherNexusLM = LazyModule(new DemoNexus)
        val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption))("nodeX"))

        val IdentitysourceModule = LazyModule(new SourceLazyModule)
        //val IdentitysinkModule = LazyModule(new SinkLazyModule)


        //oherNexusLM.node :*= sourceModule.source
        //sinkModule.sink := oherNexusLM.node
        //OthersinkModule.sink := oherNexusLM.node

        //NexusLM.nodeEphemeral := EphemeralsourceModule.source
        //EphemeralsinkModule.sink := NexusLM.nodeEphemeral

        //IdentitysinkModule.sink := IdentitysourceModule.source
        NexusLM.nodeIdentity :*= oherNexusLM.node := IdentitysourceModule.source
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