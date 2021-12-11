package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import diplomacy.bundlebridge.{BundleBridgeSink, BundleBridgeSource}
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
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class OuterLM(implicit p: Parameters) extends LazyModule {
        // val a node in the lazymodule
        val source = new DemoSource
        class InnerLM(implicit p: Parameters) extends LazyModule {
          class InInnerLM(implicit p: Parameters) extends LazyModule {
            lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          }
          val inInnerLM = LazyModule(new InInnerLM)
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }

        class Inner_otherLM(implicit p: Parameters) extends LazyModule {
          class InInner_otherLM(implicit p: Parameters) extends LazyModule {
            lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          }
          val inInner_otherLM = LazyModule(new InInner_otherLM)
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }

        val innerLM = LazyModule(new InnerLM)
        val Inner_otherLM = LazyModule(new Inner_otherLM)
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //inModuleBody content : can add some logic circuit
        }
      }
      val outerLM = LazyModule(new OuterLM)
      //elaborate the lazymodule.module , elaborate InModuleBody logic circuit, then can to emit firrtl and verilog
      chisel3.stage.ChiselStage.elaborate(outerLM.module)
      utest.assert(outerLM.innerLM.inInnerLM.getChildren.isEmpty)
      utest.assert(outerLM.innerLM.getChildren.contains(outerLM.innerLM.inInnerLM))
      //test getchild function can only get child lazymodule ,not grandchild
      utest.assert(outerLM.getChildren.contains(outerLM.innerLM))
      utest.assert(outerLM.getChildren.contains(outerLM.Inner_otherLM))
    }

    test("method line : Return source line that defines this instance "){
      class DemoLazyModule(implicit p:Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
        }
      }
      implicit val p = Parameters.empty
      // instance source line
      val demoLM = LazyModule(new DemoLazyModule)
      //println(demoLM.line)
      //test function <line> indicate the line of instantiating if lazymoduel
      utest.assert(demoLM.line.contains("LazyModuleSpec.scala:100:30"))
      // function line return a string
      utest.assert(demoLM.line.contains("L"))
    }

    test("def lazymodule.module.wrapper : Suggests instance name for [[LazyModuleImpLike]] module."){
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
      //println(lm.children)
      //println(lm.module.wrapper == lm)
    }

    test("var nodes and def getNodes : test how to get nodes in a lazymodule instantiste ") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this){
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      println(sourceModule.nodes)
      println(sourceModule.getNodes)
      //Fixme : why all these name is Lazymodule
      println(sourceModule.moduleName)
      println(sourceModule.pathName)
      println(sourceModule.instanceName)
      //utest.assert(sourceModule.nodes.contains("Lambda"))
    }

    test("protected[diplomacy] var inModuleBody: List[() => Unit] = List[() => Unit]() ") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this){
          printf(p"hello world")
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      println(sourceModule.inModuleBody)
    }


    test("def childrenIterator : Call function on all of this [[LazyModule]]'s [[children]]."){
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        //node
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //logic and connect and init value
        }
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)
      //how to use this function
      println(lm.childrenIterator(lm.getChildren => Unit)
    }

    test("def getInfo: SourceInfo = info"){
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        //node
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //logic and connect and init value
        }
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      //utest.assert(lm.getInfo)
    }

  }
}