package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.internal.sourceinfo.SourceLine
import diplomacy.bundlebridge.{BundleBridgeIdentityNode, BundleBridgeSink, BundleBridgeSource}
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
      utest.assert(outerLM.innerLM.inInnerLM.parent.contains(outerLM.innerLM))
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
      /**test getchild function can only get child lazymodule ,not grandchild
        */
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
        val insource  = LazyModule(new InSourceLazyModule)
        val insourcesecond = LazyModule(new InSourceLazyModule)
        val source = new DemoSource
        var flag = 0
        source.makeSink()
        lazy val module = new LazyModuleImp(this){
          source.bundle := 4.U
        }
      }
      var sourceNodeFlag = 0
      var sinkNodeFlag = 0
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
      //Todo : why node's name is   <LazyModuleSpec$$$Lambda$1576> .

      // FIXME: apply function nodeIterator will return this.nodes.foreach(iterfunc: BaseNode => Unit) twice
      sourceModule.nodeIterator {
        case basenode : BundleBridgeSource[UInt] => sourceNodeFlag += 1
        case basenode : BundleBridgeSink[UInt] => sinkNodeFlag += 1
        case _ =>
      }
      utest.assert(sourceNodeFlag == 4)
      utest.assert(sinkNodeFlag == 4)
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
      var sourceflag : Int = 0
      var sinkflag : Int = 0

      val nodeslist = sourceModule.nodes
      val getnodeslist = sourceModule.getNodes
      for (node <- (nodeslist ::: getnodeslist)){
        node match{
          case sourcenode : BundleBridgeSink[UInt] => sourceflag += 1
          case sinknode : BundleBridgeSource[UInt] => sinkflag +=  1
          case _ =>
        }
      }
      utest.assert(sourceflag == 2  && sinkflag == 2)

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
      utest.assert(sourceModule.moduleName.contains("LazyModule"))
      utest.assert(sourceModule.pathName.contains("LazyModule"))
      utest.assert(sourceModule.instanceName.contains("LazyModule"))
    }

    test("var inModuleBody: List[() => Unit] = List[() => Unit]() ") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        // can have InModuleBody in the lazymodule
        val source = new DemoSource
        val sink = source.makeSink()
        val sourceIO = InModuleBody{ source.makeIO()}
        InModuleBody {
          source.bundle := 4.U
        }
        lazy val module = new LazyModuleImp(this){
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      LazyScope.apply[LazyModule]("name", "SimpleLazyModule", None)(sourceModule)(p)
      println(chisel3.stage.ChiselStage.emitSystemVerilog(sourceModule.module))
      //chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      //test def wrapper
      var wrapperTestFlag = 0
      val wrapperOfModule = sourceModule.module.wrapper
      wrapperOfModule match {
        case args : SourceLazyModule => wrapperTestFlag = 1
        case _ => wrapperTestFlag = 0
      }
      utest.assert(wrapperTestFlag == 1)

      //test var inModuleBody: List[() => Unit] = List[() => Unit]()
      //there are two InModuleBody in LazyModule(SourceLazyModule)
      utest.assert(sourceModule.inModuleBody.length == 2)

      //test  getWrappedValue
      var getWrappedValueFlag = 0
      val WrappedValue = sourceModule.sourceIO.getWrappedValue.getWidth
      WrappedValue match {
        case 32 => getWrappedValueFlag = 1
        case _ =>
      }
      utest.assert(getWrappedValueFlag ==1)
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
      //test var info and getInfo :  return the line to wrapper a new Lazymodue class
      utest.assert(lm.getInfo.makeMessage(c => c.toString).contains("265:26"))

      val sourceLineInfo = lm.info
      sourceLineInfo match{
        case n : SourceLine => {
          utest.assert(n.line == 265)
          utest.assert(n.col  == 26)
          utest.assert(n.filename == "LazyModuleSpec.scala")
          utest.assert(n.makeMessage(c => c) == "@[LazyModuleSpec.scala 265:26]")
        }
        case _ => println("Error :  LazyModule var info is not a SourceLine class !")
      }

      val sourceLineGetInfo = lm.getInfo
      sourceLineGetInfo match{
        case n : SourceLine => {
          utest.assert(n.line == 265)
          utest.assert(n.col  == 26)
          utest.assert(n.filename == "LazyModuleSpec.scala")
          utest.assert(n.makeMessage(c => c) == "@[LazyModuleSpec.scala 265:26]")
        }
        case _ => println("Error : LazyModule method getInfo is not a SourceLine class !")
      }
    }

    test("var scope and def getScope :Option[LazyModule] = scope"){
      implicit val p = Parameters.empty
      class DemoLazyModule(implicit p: Parameters) extends LazyModule with LazyScope {
        class InDemoLazyModule(implicit p: Parameters) extends LazyModule {
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          val inDemoSourceNode = BundleBridgeIdentityNode[Bool]
        }
        val DemoSourceNode = BundleBridgeSource(() => UInt(32.W))
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          //logic and connect and init value
        }
        val InLM = LazyModule(new InDemoLazyModule)
      }
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)

      //protected[diplomacy] val parent: Option[LazyModule] = LazyModule.scope
      //default scope is None , when LazyModule is on the top of hierarchy
      utest.assert(LazyModule.getScope == None)
      utest.assert(LazyModule.scope == None)
      utest.assert(lm.toString.contains("LazyScope named lm"))
      //test  method shouldBeInlined
      utest.assert(lm.shouldBeInlined == false)
      utest.assert(lm.InLM.shouldBeInlined == true)
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

    test("def lazymodule.module.dangles/auto/desiredName : Suggests instance name for [[LazyModuleImpLike]] module.") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val source_bundile = source.bundle
          source_bundile := 4.U(32.W)
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {
          val sink_io = sink.makeIO("sink_io")
        }
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink :*= sourceModule.source
        lazy val module = new LazyModuleImp(this) {
        }
      }
      val TopModule = LazyModule(new TopLazyModule)
      chisel3.stage.ChiselStage.elaborate(TopModule.module)
      utest.assert(TopModule.module.dangles.isEmpty)
      //print : AutoBundle(IO auto in LazyModule)
      utest.assert(TopModule.module.auto.elements.isEmpty)
      //ToDo:Why module desireName is LazyModule
      utest.assert(TopModule.module.desiredName.contains("LazyModule"))
    }

    test("LazyModule can be defined with LazyRawModuleImp") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        override lazy val module = new LazyRawModuleImp(this) {
          childClock := false.B.asClock
          childReset := chisel3.DontCare
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

    test("A example : SimpleLazyModule ") {
      class DemoLazyModule(implicit p: Parameters) extends SimpleLazyModule {
        val InDemo = LazyModule(new SimpleLazyModule)
        override lazy val module = new LazyModuleImp(this) {
          printf(p"hello world")
        }
      }
        implicit val p = Parameters.empty
        val demo = LazyModule(new DemoLazyModule)
        //val demoAnother = LazyModule(new SimpleLazyModule)
          utest.assert(
            chisel3.stage.ChiselStage
              .emitSystemVerilog(demo.module)
              .contains("""$fwrite(32'h80000002,"hello world");""")
          )
          utest.assert(demo.name == "demo")
          demo.InDemo match{
            case simpleLM : SimpleLazyModule => utest.assert(simpleLM.name == "InDemo")
            case _ =>
          }
      }
    }
}