package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3.{Data, _}
import diplomacy.bundlebridge.BundleBridgeNexus.{fillN, orReduction}
import diplomacy.bundlebridge.{BundleBridgeNexus, BundleBridgeNexusNode, BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import utest._

object NodeSpec extends TestSuite {
  def tests: Tests = Tests {
    test("raw BundleBridge Source and Sink prototype.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this){
          source.bundle := 4.U
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this){
          printf(p"${sink.bundle}")
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink := sourceModule.source
        lazy val module = new LazyModuleImp(this){}
      }
      println(chisel3.stage.ChiselStage.emitSystemVerilog(LazyModule(new TopLazyModule).module))
      //chisel3.stage.ChiselStage.emitSystemVerilog(LazyModule(new TopLazyModule).module)
    }

    test("BundleBridge Source and Sink normal usage"){
      implicit val p = Parameters.empty
      class BottomLazyModule extends LazyModule {
        val source = BundleBridgeSource(() => UInt(32.W))
        lazy val module = new LazyModuleImp(this){
          source.bundle := 4.U
        }
      }
      class TopLazyModule extends LazyModule {
        val bottom = LazyModule(new BottomLazyModule)
        // make sink node and connect.
        val sink = bottom.source.makeSink()
        lazy val module = new LazyModuleImp(this){
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

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this){
          source.bundle := 4.U
        }
      }

      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this){
          printf(p"${sink.bundle}")
          chisel3.assert(sink.bundle === 4.U)
        }
      }

      class NexusLazymodule[T <: Data](genOpt: Option[() => T] = None) extends LazyModule {

        val aname: Option[String] = Some("X")
        val registered: Boolean = false
        val default: Option[() => T] = genOpt
        val inputRequiresOutput: Boolean = false // when false, connecting a source does not mandate connecting a sink
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
        val broadcastname = aname.foreach(broadcast.suggestName)
        //def return a node
        val broadcastnode = broadcast.node

        lazy val module = new LazyModuleImp(this) {
          printf(p"${broadcast.node.out}")
          printf(p"${broadcast.node.bundleIn}")
          printf(p"${broadcast.node.bundleOut}")
          //chisel3.assert(sink.bundle === 4.U)
        }
      }

      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        //val OthersinkModule = LazyModule(new SinkLazyModule)
        val NexusLM =LazyModule(new NexusLazymodule[UInt](Some(genOption)))
        NexusLM.broadcastnode :*= sourceModule.source
        sinkModule.sink :*= sourceModule.source
        //OthersinkModule.sink :=* NexusLM.broadcastnode
        lazy val module = new LazyModuleImp(this){
        }
      }

      val TopLM =  LazyModule(new TopLazyModule())
      val NexusLM = LazyModule(new NexusLazymodule[UInt](Some(genOption)))
      //chisel3.stage.ChiselStage.elaborate(ExampleLM.broadcast.module)
      chisel3.stage.ChiselStage.elaborate(TopLM.module)
      println(chisel3.stage.ChiselStage.emitSystemVerilog(LazyModule(new TopLazyModule).module))
      println(TopLM.NexusLM.broadcastnode)
      println(TopLM.NexusLM.broadcastname)
      println(NexusLM.broadcastnode)
      println(NexusLM.broadcastname)
    }

  }
}