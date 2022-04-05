package diplomacy.unittest

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.internal.sourceinfo.SourceLine
import diplomacy.bundlebridge.{BundleBridgeIdentityNode, BundleBridgeSink, BundleBridgeSource}
import diplomacy.lazymodule.{
  InModuleBody,
  LazyModule,
  LazyModuleImp,
  LazyModuleImpLike,
  LazyRawModuleImp,
  LazyScope,
  SimpleLazyModule
}
import diplomacy.lazymodule.ModuleValue
import utest._

object LazyModuleSpec extends TestSuite {
  def tests: Tests = Tests {

    test("test A example: LazyModule can be defined") {
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

    test("test var parent and method parents and getParent: LazyModule can have sub-LazyModules") {
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
      // var parent
      utest.assert(outerLM.parent.isEmpty)
      utest.assert(outerLM.innerLM.parent == Some(outerLM))
      utest.assert(outerLM.innerLM.inInnerLM.parent == Some[LazyModule](outerLM.innerLM))
      // method parents
      utest.assert(outerLM.parents.isEmpty)
      utest.assert(outerLM.innerLM.parents == Seq(outerLM))
      // method parents can get grandparent
      utest.assert(outerLM.innerLM.inInnerLM.parents == Seq(outerLM.innerLM, outerLM))
      // getParent is just an alias to parent.We should get rid of `getParent` API
      utest.assert(outerLM.innerLM.inInnerLM.getParent == Some[LazyModule](outerLM.innerLM))
    }

    test("test var children and method getChildren: LazyModule can get children-LazyModules") {
      implicit val p = Parameters.empty
      class OuterLM(implicit p: Parameters) extends LazyModule {
        class InnerLM(implicit p: Parameters) extends LazyModule {
          class InInnerLM(implicit p: Parameters) extends LazyModule {
            lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          }
          val inInnerLM = LazyModule(new InInnerLM)
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }
        class InnerOtherLM(implicit p: Parameters) extends LazyModule {
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        }
        val innerLM = LazyModule(new InnerLM)
        val InnerOtherLM = LazyModule(new InnerOtherLM)
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
      }
      val outerLM = LazyModule(new OuterLM)
      // elaborate the lazyModule.module,elaborate InModuleBody logic circuit, then can to emit firrtl and verilog
      chisel3.stage.ChiselStage.elaborate(outerLM.module)
      // test getChildren
      utest.assert(outerLM.innerLM.inInnerLM.getChildren.isEmpty)
      utest.assert(outerLM.innerLM.getChildren == List[LazyModule](outerLM.innerLM.inInnerLM))

      /**
        * test getChildren function can only get child lazyModule, not grandchild
        */
      utest.assert(outerLM.getChildren == List(outerLM.InnerOtherLM, outerLM.innerLM))
      utest.assert(outerLM.children == List(outerLM.InnerOtherLM, outerLM.innerLM))
    }

    test("test method childrenIterator and NodesIterator") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class InSourceLazyModule extends LazyModule {
        var flag = 0
        val source = new DemoSource
        source.makeSink()
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {
          source.bundle := 1.U
        }
      }
      class SourceLazyModule extends LazyModule {
        val insource = LazyModule(new InSourceLazyModule)
        val insourcesecond = LazyModule(new InSourceLazyModule)
        val source = new DemoSource
        var flag = 1
        source.makeSink()
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
        }
      }
      var sourceNodeFlag = 0
      var sinkNodeFlag = 0
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      // test childrenIterator: childrenIterator return itself LazyModule name and Child LazyModule name
      // Todo:why function childrenIterator return an empty type and always println in console
      sourceModule.childrenIterator {
        case lm: InSourceLazyModule =>
          lm.flag += 1
        case lm: SourceLazyModule =>
          lm.flag += 1
        case _ =>
      }
      utest.assert(sourceModule.insource.flag == 1)
      utest.assert(sourceModule.insourcesecond.flag == 1)
      utest.assert(sourceModule.flag == 2)
      // test nodeIterator
      // Todo:why node's name is <LazyModuleSpec$$$Lambda$1576> .

      // FIXME:apply function nodeIterator will return this.nodes.foreach(iterfunc: BaseNode => Unit) twice
      sourceModule.nodeIterator {
        case basenode: BundleBridgeSource[UInt] => sourceNodeFlag += 1
        case basenode: BundleBridgeSink[UInt]   => sinkNodeFlag += 1
        case _ =>
      }
      utest.assert(sourceNodeFlag == 4)
      utest.assert(sinkNodeFlag == 4)
    }

    test("test method line: Return source line that defines this instance") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
      }
      implicit val p = Parameters.empty
      // instance source line
      val demoLM = LazyModule(new DemoLazyModule)
      // test function <line> indicate the line of instantiating if lazyModule
      // function line return a string,which line to define the LazyModule
      utest.assert(demoLM.line.contains("158:30"))
    }

    test("test var nodes and def getNodes: test how to get nodes in a lazyModule instance") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        source.makeSink()
        lazy val module = new LazyModuleImp(this) {}
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      var sourceFlag: Int = 1
      var sinkFlag:   Int = 0
      val getNodesList = sourceModule.getNodes
      for (node <- (getNodesList)) {
        node match {
          case sourceNode: BundleBridgeSink[UInt]   => sourceFlag += 1
          case sinkNode:   BundleBridgeSource[UInt] => sinkFlag += 1
          case _ =>
        }
      }
      utest.assert(sourceFlag == 2 && sinkFlag == 1)
    }

    test("test var moduleName and pathName and instanceName: test how to get nodes in a lazyModule instance") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule extends LazyModule {
        lazy val module = new LazyModuleImp(this) {}
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.elaborate(sourceModule.module)
      // Fixme:Why all these name is LazyModule,need fix
      utest.assert(sourceModule.moduleName.contains("LazyModule"))
      utest.assert(sourceModule.pathName.contains("LazyModule"))
      utest.assert(sourceModule.instanceName.contains("LazyModule"))
    }

    test("test var info and def getInfo: SourceInfo=info") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)
      // test var info and getInfo:return the line to wrapper a new LazyModule class
      utest.assert(lm.getInfo.makeMessage(c => c.toString).contains("208:26"))

      val sourceLineInfo = lm.info
      sourceLineInfo match {
        case n: SourceLine => {
          utest.assert(n.line == 208)
          utest.assert(n.col == 26)
          utest.assert(n.filename == "LazyModuleSpec.scala")
          utest.assert(n.makeMessage(c => c) == "@[LazyModuleSpec.scala 208:26]")
        }
        case _ => throw new Exception("Error: LazyModule var info is not a SourceLine class!")
      }

      val sourceLineGetInfo = lm.getInfo
      sourceLineGetInfo match {
        case n: SourceLine => {
          utest.assert(n.line == 208)
          utest.assert(n.col == 26)
          utest.assert(n.filename == "LazyModuleSpec.scala")
          utest.assert(n.makeMessage(c => c) == "@[LazyModuleSpec.scala 208:26]")
        }
        case _ => throw new Exception("Error: LazyModule method getInfo is not a SourceLine class!")
      }
    }

    test("test LazyScope") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      // add a lazyScope same as sourceModule
      val myScope = LazyScope.apply[LazyModule]("lazyScope", "SimpleLazyModule", None)(sourceModule)(p)
      chisel3.stage.ChiselStage.emitSystemVerilog(sourceModule.module)
      utest.assert(myScope == sourceModule)
    }

    test("test def wrapper") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
          chisel3.assert(sink.bundle === 4.U)
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.emitSystemVerilog(sourceModule.module)
      //test def wrapper
      var wrapperTestFlag = 0
      val wrapperOfModule = sourceModule.module.wrapper
      wrapperOfModule match {
        case args: SourceLazyModule => wrapperTestFlag += 1
        case _ => wrapperTestFlag += 0
      }
      utest.assert(wrapperTestFlag == 1)
      utest.assert(wrapperOfModule == sourceModule)
    }

    test("test var inModuleBody: List[() => Unit]=List[() => Unit]()") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        val sourceIO = InModuleBody { source.makeIO() }
        val flag:  Boolean = false
        val value: ModuleValue[UInt] = InModuleBody { if (flag) 4.U else 2.U }
        lazy val module = new LazyModuleImp(this) {
          source.bundle := value
          chisel3.assert(sink.bundle === 2.U)
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      chisel3.stage.ChiselStage.emitSystemVerilog(sourceModule.module)

      // test var inModuleBody:List[() => Unit] = List[() => Unit]()
      // there are two InModuleBody in LazyModule(SourceLazyModule)
      utest.assert(sourceModule.inModuleBody.length == 2)

      // test getWrappedValue
      var getWrappedValueFlag = 0
      val WrappedValue = sourceModule.sourceIO.getWrappedValue.getWidth
      WrappedValue match {
        case 32 => getWrappedValueFlag += 1
        case _  =>
      }
      utest.assert(getWrappedValueFlag == 1)
      utest.assert(sourceModule.value.getWrappedValue.getClass.toString == "class chisel3.UInt")
    }

    test("test var scope and def getScope: Option[LazyModule]=scope") {
      implicit val p = Parameters.empty
      class DemoLazyModule(implicit p: Parameters) extends LazyModule with LazyScope {
        class InDemoLazyModule(implicit p: Parameters) extends LazyModule {
          lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
          val inDemoSourceNode = BundleBridgeIdentityNode[Bool]
        }
        val DemoSourceNode = BundleBridgeSource(() => UInt(32.W))
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
        val InLM = LazyModule(new InDemoLazyModule)
      }
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)
      // protected[diplomacy] val parent:Option[LazyModule]=LazyModule.scope
      // default scope is None,when LazyModule is on the top of hierarchy
      utest.assert(LazyModule.getScope == None)
      utest.assert(LazyModule.scope == None)
      utest.assert(lm.toString.contains("LazyScope named lm"))
      // test method shouldBeInlined
      utest.assert(lm.shouldBeInlined == false)
      utest.assert(lm.InLM.shouldBeInlined == true)
    }

    test("test def lazyModule.module.desiredName: Suggests instance name for [LazyModuleImpLike] module") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        lazy val module = new LazyModuleImp(this) {
          val sourceBundile = source.bundle
          sourceBundile := 4.U(32.W)
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {}
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink :*= sourceModule.source
        lazy val module = new LazyModuleImp(this) {}
      }
      val TopModule = LazyModule(new TopLazyModule)
      chisel3.stage.ChiselStage.elaborate(TopModule.module)
      // ToDo:Why module desireName is LazyModule
      utest.assert(TopModule.module.desiredName.contains("LazyModule"))
    }

    test("test def lazyModule.module.dangles/auto") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        InModuleBody { source.makeIO() }
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U(32.W)
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {}
      }
      class TopLazyModule extends LazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink := sourceModule.source
        lazy val module = new LazyModuleImp(this) {}
      }
      val TopModule = LazyModule(new TopLazyModule)
      chisel3.stage.ChiselStage.emitSystemVerilog(TopModule.module)
      utest.assert(TopModule.module.dangles.isEmpty)
      utest.assert(TopModule.module.auto.elements.isEmpty)
    }

    test("test LazyModule can be defined with LazyRawModuleImp") {
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

    test("test SimpleLazyModule") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)

      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))
      class DemoSink(implicit valName: sourcecode.Name) extends BundleBridgeSink[UInt](Some(genOption))

      class SourceLazyModule extends LazyModule {
        val source = new DemoSource
        InModuleBody { source.makeIO() }
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U(32.W)
        }
      }
      class SinkLazyModule extends LazyModule {
        val sink = new DemoSink
        lazy val module = new LazyModuleImp(this) {}
      }
      class TopLazyModule extends SimpleLazyModule {
        val sourceModule = LazyModule(new SourceLazyModule)
        val sinkModule = LazyModule(new SinkLazyModule)
        sinkModule.sink := sourceModule.source
        // there is no LazyModuleImp module
      }
      val TopModule = LazyModule(new TopLazyModule)
      chisel3.stage.ChiselStage.emitSystemVerilog(TopModule.module)
      utest.assert(TopModule.module.dangles.isEmpty)
      utest.assert(TopModule.module.auto.elements.isEmpty)
    }
  }
}
