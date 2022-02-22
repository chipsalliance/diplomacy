package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import diplomacy.bundlebridge.{BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{InModuleBody, LazyModule, LazyModuleImp, LazyModuleImpLike, LazyRawModuleImp, LazyScope, SimpleLazyModule}
import utest._

object LazyModuleSpec extends TestSuite {
  def tests: Tests = Tests {

    test("A example : LazyModule can be defined ") {
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
      utest.assert(outerLM.getChildren.contains(outerLM.innerLM))
      utest.assert(outerLM.getChildren.contains(outerLM.Inner_otherLM))
      utest.assert(outerLM.children.contains(outerLM.innerLM))
      utest.assert(outerLM.children.contains(outerLM.Inner_otherLM))
    }

    test("method childrenIterator and NodesIterator "){
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class InSourceLazyModule extends  LazyModule{
        var flag = 0
        val source = new DemoSource
        source.makeSink()
        lazy val module:LazyModuleImpLike = new LazyModuleImp(this){
          source.bundle := 1.U
        }
      }
      class SourceLazyModule extends LazyModule {
        val insource = LazyModule(new InSourceLazyModule)
        val insourcesecond = LazyModule(new InSourceLazyModule)
        val source = new DemoSource
        var flag = 0
        source.makeSink()
        lazy val module = new LazyModuleImp(this){
          source.bundle := 4.U
        }
      }
      var sourcenodeflag = 0
      var sinknodeflag = 0
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      //test childrenIterator : childrenIterator return itself Lazymodule name and Child LazyModule name
      //Todo : why function childrenIterator return an empty type and always println in console
      sourceModule.childrenIterator {
        case lm: InSourceLazyModule =>
          lm.flag = 1
        case lm: SourceLazyModule =>
          lm.flag = 1
        case _ =>
      }
      utest.assert(sourceModule.insource.flag == 1)
      utest.assert(sourceModule.insourcesecond.flag == 1)
      utest.assert(sourceModule.flag == 1)
      //test nodeIterator
      //Todo : why node's name is   <LazyModuleSpec$$$Lambda$1576>
      sourceModule.nodeIterator {
        case basenode : BundleBridgeSource[UInt] =>
          sourcenodeflag += 1
        case basenode : BundleBridgeSink[UInt] =>
          sinknodeflag += 1
        case _ =>
      }
      println(sourcenodeflag)
      println(sinknodeflag)
      utest.assert(sourcenodeflag == 4)
      utest.assert(sinknodeflag == 4)
      // FIXME: apply function nodeIterator will return this.nodes.foreach(iterfunc: BaseNode => Unit) twice
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
      //function line return a string , which line to define the LazyModule
      utest.assert(demoLM.line.contains("162:30"))
    }

    test("var nodes and def getNodes : test how to get nodes in a lazymodule instantiste ") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        source.makeSink()
        lazy val module = new LazyModuleImp(this){
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      //print : List(BundleBridgeSource(Some(diplomacy.unittest.LazyModuleSpec$$$Lambda$1601/0x000000080061ac98@3b95a6db)))
      val nodeslist = sourceModule.nodes
      val getnodeslist = sourceModule.getNodes
      println(List(nodeslist , getnodeslist))
      println(nodeslist:::getnodeslist)
      for (node <- (nodeslist ::: getnodeslist)){
        node match{
          case sourcenode : BundleBridgeSink[UInt] => println("a")
          case sinknode : BundleBridgeSource[UInt] => println("b")
          case _ => println("c")
        }
      }
      // TODO: how to contains  a BundleBridgeSource Node
      //print : List(BundleBridgeSource(Some(diplomacy.unittest.LazyModuleSpec$$$Lambda$1601/0x000000080061ac98@3b95a6db)))
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
      LazyScope.apply[LazyModule]("name", "SimpleLazyModule", None)(sourceModule)(p)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      //print : List(diplomacy.lazymodule.InModuleBody$$$Lambda$1613/0x000000080061fcc8@4eaa375c)
      println(sourceModule.module.wrapper)
      println(sourceModule.module.wrapper.inModuleBody)
      //print : UInt<32>(IO iosource in LazyModule)
      println(sourceModule.iosource)
      println(sourceModule.iosource.getWrappedValue)
      //print : diplomacy.lazymodule.InModuleBody$$anon$1@36e43829
      println(sourceModule.iosource.toString)
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
      println(lm.getInfo.makeMessage(c => c.toString))
      //utest.assert(lm.getInfo.toString.contains("SourceLine"))
      println(lm.info)
    }

    // TODO: how to  test function:getScope in object Lazymodule
    test("var scope and def getScope :Option[LazyModule] = scope"){
      class DemoLazyModule(implicit p: Parameters) extends LazyModule with LazyScope {
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
      //LazyScope.apply()
      //LazyScope.apply[LazyModule]("name", "SimpleLazyModule", None)(lm)(p)

      //val demolm = LazyModule()
      chisel3.stage.ChiselStage.elaborate(lm.module)
      //var info and getInfo return the line to wrapper a new Lazymodue class
      println(LazyModule.getScope)
      println(LazyModule.scope)
      println(lm.toString)
      println(LazyScope.inline())
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
      //print : List(Dangle(HalfEdge(5,0),HalfEdge(6,0),false,sourceModule_source_out,UInt<32>(IO auto_source_out in LazyModule)))
      println(sourceModule.module.dangles)
      //print : AutoBundle(IO auto in LazyModule)
      println(sourceModule.module.auto)
      //ToDo:Why module desireName is LazyModule
      println(sourceModule.module.desiredName)
    }

    test("LazyModule can be defined with LazyRawModuleImp") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        override lazy val module = new LazyRawModuleImp(this) {
          childClock := false.B.asClock
          childReset := chisel3.DontCare
          //printf(p"hello world")
        }
      }
      test("new DemoLazyModule doesn't do any thing") {
        implicit val p = Parameters.empty
        val demo = LazyModule(new DemoLazyModule)
        chisel3.stage.ChiselStage.emitSystemVerilog(demo.module)
        test("demo can generate a simple verilog with invoking module") {
          utest.assert(
          )
          utest.assert(demo.name == "demo")
        }
      }
    }
  }
}