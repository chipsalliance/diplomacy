package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import diplomacy.bundlebridge.{BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{InModuleBody, LazyModule, LazyModuleImp, LazyModuleImpLike}
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

    test("var parent and method parents and getParent : LazyModule can have sub-LazyModules") {
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
      chisel3.stage.ChiselStage.elaborate(outerLM.module)
      //var parent
      utest.assert(outerLM.parent.isEmpty)
      utest.assert(outerLM.innerLM.parent.contains(outerLM))
      //method parents
      utest.assert(outerLM.parents.isEmpty)
      utest.assert(outerLM.innerLM.parents.contains(outerLM))
      // method parents can get grandparent
      utest.assert(outerLM.innerLM.inInnerLM.parents.contains(outerLM))
      utest.assert(outerLM.innerLM.inInnerLM.parents.contains(outerLM.innerLM))
      //method getParent can not get grandparent , can only get parent
      utest.assert(outerLM.innerLM.inInnerLM.getParent.contains(outerLM.innerLM))
      //following utest can not pass
      //utest.assert(outerLM.innerLM.inInnerLM.getParent.contains(outerLM))
    }

    test("var child and method getChildren: LazyModule can get children-LazyModules"){
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class OuterLM(implicit p: Parameters) extends LazyModule {
        // val a node in the lazymodule
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
      //test getChildren
      utest.assert(outerLM.innerLM.inInnerLM.getChildren.isEmpty)
      utest.assert(outerLM.innerLM.getChildren.contains(outerLM.innerLM.inInnerLM))
      //test getchild function can only get child lazymodule ,not grandchild
      println(outerLM.getChildren)
      println(outerLM.children)
      utest.assert(outerLM.getChildren.contains(outerLM.innerLM))
      utest.assert(outerLM.getChildren.contains(outerLM.Inner_otherLM))
    }

    test("method childrenIterator and NodesIterator "){
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        class InSourceLazyModule extends  LazyModule{
          lazy val module:LazyModuleImpLike = new LazyModuleImp(this){}
        }
        val insource = LazyModule(new InSourceLazyModule)
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this){}
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      //test childrenIterator
      println(sourceModule.childrenIterator(c => println(c)))
      //test nodeIterator
      println(sourceModule.nodeIterator(c => println(c)))
    }

    test("method line : Return source line that defines this instance "){
      class DemoLazyModule(implicit p:Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
        }
      }
      implicit val p = Parameters.empty
      // instance source line
      val demoLM = LazyModule(new DemoLazyModule)
      //test function <line> indicate the line of instantiating if lazymoduel
      //utest.assert(demoLM.line.contains("LazyModuleSpec.scala:100:30"))
      // function line return a string
      utest.assert(demoLM.line.contains("L"))
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
    }

    test("var moduleName and pathName and  instanceName : test how to get nodes in a lazymodule instantiste ") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this){}
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      //Fixme : why all these name is Lazymodule
      println(sourceModule.moduleName)
      utest.assert(sourceModule.moduleName.contains("LazyModule"))
      println(sourceModule.pathName)
      utest.assert(sourceModule.pathName.contains("LazyModule"))
      println(sourceModule.instanceName)
      utest.assert(sourceModule.instanceName.contains("LazyModule"))
      //utest.assert(sourceModule.nodes.contains("Lambda"))
    }

    test("var inModuleBody: List[() => Unit] = List[() => Unit]() ") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        // can have InModuleBody in the lazymodule
        val source = new DemoSource
        val iosource = InModuleBody{ source.makeIO()}
        lazy val module = new LazyModuleImp(this){}
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      val sink = sourceModule.source.makeSink()
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      println(sourceModule.module.wrapper.inModuleBody)
    }

    test("var info and def getInfo: SourceInfo = info"){
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        //node
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //logic and connect and init value
        }
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)
      //var info and getInfo return the line to wrapper a new Lazymodue class
      println(lm.getInfo)
      println(lm.info)
    }

    // TODO: how to  test function:getScope in object Lazymodule
    test("var scope and def getScope :Option[LazyModule] = scope"){
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        class InDemoLazyModule(implicit p: Parameters) extends LazyModule {
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }
        //node
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //logic and connect and init value
        }
        val InLM = LazyModule(new InDemoLazyModule)
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      //val demolm = LazyModule()
      chisel3.stage.ChiselStage.elaborate(lm.module)
      //var info and getInfo return the line to wrapper a new Lazymodue class
      println(LazyModule.getScope)
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
      utest.assert(lm.module.wrapper == lm)
    }

    test("def lazymodule.module.dangles/auto/desiredName : Suggests instance name for [[LazyModuleImpLike]] module."){
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        // can have InModuleBody in the lazymodule
        val source = new DemoSource
        val iosource = InModuleBody{ source.makeIO()}
        lazy val module = new LazyModuleImp(this){
          val a = Output(UInt(1.W))
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      val sink = sourceModule.source.makeSink()
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      println(sourceModule.module.dangles)
      println(sourceModule.module.auto)
      println(sourceModule.module.desiredName)
    }

  }
}