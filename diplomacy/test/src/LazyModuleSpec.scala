package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import diplomacy.lazymodule.{LazyModule, LazyModuleImp, LazyModuleImpLike}
import utest._

object LazyModuleSpec extends TestSuite {
  def tests: Tests = Tests {
    test("LazyModule can be defined with") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          printf(p"hello world")
        }
      }
      test("new DemoLazyModule doesn't do any thing") {
        implicit val p = Parameters.empty
        val demo = LazyModule(new DemoLazyModule)
        test("demo can generate a simple verilog with invoking module") {
          utest.assert(
            chisel3.stage.ChiselStage
              .emitSystemVerilog(demo.module)
              .contains("""$fwrite(32'h80000002,"hello world");""")
          )
          utest.assert(demo.name == "demo")
        }
      }
    }

    test("method parents and getParent : LazyModule can have sub-LazyModules") {
      class OuterLM(implicit p: Parameters) extends LazyModule {
        class InnerLM(implicit p: Parameters) extends LazyModule {
          class InInnerLM(implicit p: Parameters) extends LazyModule {
            lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          }
          val inInnerLM = LazyModule(new InInnerLM)
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }
        val innerLM = LazyModule(new InnerLM)
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
      }
      implicit val p = Parameters.empty
      val outerLM = LazyModule(new OuterLM)
      utest.assert(outerLM.parents.isEmpty)
      utest.assert(outerLM.innerLM.parents.contains(outerLM))
      utest.assert(outerLM.innerLM.inInnerLM.parents.contains(outerLM))
      utest.assert(outerLM.innerLM.inInnerLM.parents.contains(outerLM.innerLM))
      utest.assert(outerLM.innerLM.inInnerLM.getParent.contains(outerLM.innerLM))
    }

    test("method getChildren : LazyModule can get children-LazyModules"){
      class OuterLM(implicit p: Parameters) extends LazyModule {
        class InnerLM(implicit p: Parameters) extends LazyModule {
          class InInnerLM(implicit p: Parameters) extends LazyModule {
            lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          }
          val inInnerLM = LazyModule(new InInnerLM)
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }
        val innerLM = LazyModule(new InnerLM)
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
      }
      implicit val p = Parameters.empty
      val outerLM = LazyModule(new OuterLM)
      utest.assert(outerLM.innerLM.inInnerLM.getChildren.isEmpty)
      utest.assert(outerLM.innerLM.getChildren.contains(outerLM.innerLM.inInnerLM))
      utest.assert(outerLM.getChildren.contains(outerLM.innerLM))
    }

    test("method line : Return source line that defines this instance "){
      class DemoLazyModule(implicit p:Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
        }
      }
      implicit val p = Parameters.empty
      // instance source line
      val demoLM = LazyModule(new DemoLazyModule)
      println(demoLM.line)
      utest.assert(demoLM.line.contains("LazyModuleSpec.scala:77:30"))
      // function line return a string
      utest.assert(demoLM.line.contains("L"))
    }

    test("def suggestName : Suggests instance name for [[LazyModuleImpLike]] module."){
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        //node
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //logic and connect and init value
        }
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      // all lazy val will be triggered after elaborating entire LazyModule
      chisel3.stage.ChiselStage.elaborate(lm.module)
      println(lm.module.wrapper == lm)
    }
  }
}
