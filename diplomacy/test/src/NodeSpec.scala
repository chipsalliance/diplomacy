package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import diplomacy.bundlebridge.{BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import utest._

object NodeSpec extends TestSuite {
  def tests: Tests = Tests {
    test("raw BundleBridge prototype.") {
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
    }
    test("BundleBridge normal usage"){
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
  }
}
