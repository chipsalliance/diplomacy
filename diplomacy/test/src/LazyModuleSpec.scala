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
import scala.util.matching.Regex
import firrtl.analyses.{CircuitGraph, InstanceKeyGraph}
import firrtl.analyses.InstanceKeyGraph.InstanceKey
import utest._
import firrtl.annotations.CircuitTarget
import firrtl.options.Dependency
import firrtl.passes.ExpandWhensAndCheck
import firrtl.{CircuitState, UnknownForm}

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
      // getParent is just an alias to parent. We should get rid of `getParent` API
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
      // elaborate the lazyModule.module, elaborate InModuleBody logic circuit, then can to emit firrtl and verilog
      chisel3.stage.ChiselStage.elaborate(outerLM.module)
      // test getChildren
      utest.assert(outerLM.innerLM.inInnerLM.getChildren.isEmpty)
      utest.assert(outerLM.innerLM.getChildren == List[LazyModule](outerLM.innerLM.inInnerLM))

      // test getChildren function can only get child lazyModule, not grandchild
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
      // Todo: why function childrenIterator return an empty type and always println in console
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
      // Todo: why node's name is <LazyModuleSpec$$$Lambda$1576>.

      // FIXME: apply function nodeIterator will return this.nodes.foreach(iterfunc: BaseNode => Unit) twice
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
      // function line return a string, which line to define the LazyModule

      // TODO: line is mutable in the test, so note it temporarily
      // utest.assert(demoLM.line.contains("156:30"))
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
      val topModule = LazyModule(new OuterLM).suggestName("myTopModule")
      chisel3.stage.ChiselStage.elaborate(topModule.module)
      // Fixme: Why all these name is LazyModule, need fix.The top module's instanceName and pathName have some bugs
      // moduleName return the wrapper's class name
      // from inner to outer, wrapper's class name is numbered with className, className_1, className_2, className_3.....
      utest.assert(topModule.innerLM.inInnerLM.moduleName == "LazyModule")
      utest.assert(topModule.innerLM.moduleName == "LazyModule_1")
      utest.assert(topModule.moduleName == "LazyModule_2")
      // pathName return the Path like: topInstanceName.childInstanceName.grandChildInstanceName
      utest.assert(topModule.innerLM.inInnerLM.pathName == "LazyModule_2.innerLM.inInnerLM")
      utest.assert(topModule.innerLM.pathName == "LazyModule_2.innerLM")
      utest.assert(topModule.pathName == "LazyModule_2")
      // instanceName only return instanceName of itself
      utest.assert(topModule.innerLM.inInnerLM.instanceName == "inInnerLM")
      utest.assert(topModule.innerLM.instanceName == "innerLM")
      utest.assert(topModule.instanceName == "LazyModule_2")
    }

    test("test var info and def getInfo: SourceInfo=info") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        lazy val module: LazyModuleImpLike = new LazyModuleImp(this) {}
      }
      implicit val p = Parameters.empty
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)
      // test var info and getInfo: return the line to wrapper a new LazyModule class
      val infoKeyValPattern: Regex = "[@][\\[]([0-9a-zA-Z-. ]+) ([0-9]+):([0-9]+)[\\]]".r
      val infoString = lm.getInfo.makeMessage(c => c.toString)
      val sourceLineInfo = lm.getInfo

      for (patternMatch <- infoKeyValPattern.findAllMatchIn(infoString)) {
        sourceLineInfo match {
          case n: SourceLine => {
            utest.assert(n.filename == patternMatch.group(1))
            utest.assert(n.line == patternMatch.group(2).toInt)
            utest.assert(n.col == patternMatch.group(3).toInt)
          }
          case _ => throw new Exception("Error: LazyModule var info is not a SourceLine class!")
        }
      }
    }

    test("test LazyScope") {
      implicit val p = Parameters.empty
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class LazyScopeModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        lazy val module = new LazyModuleImp(this) {
          val io = IO(new Bundle() {
            val a = Input(UInt(32.W))
            val b = Input(UInt(32.W))
            val c = Output(UInt(32.W))
          })
          source.bundle := io.a + io.b
          io.c := sink.bundle
        }
      }

      class SourceLazyModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        val lazyModuleInScope =
          LazyScope.apply[LazyModule]("myLazyScope", "moduleDesiredName")(LazyModule(new LazyScopeModule))(p)
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
        }
      }
      val sourceModule = LazyModule(new SourceLazyModule)
      val firrtlCircuit = firrtl.Parser.parse(chisel3.stage.ChiselStage.emitFirrtl(sourceModule.module))
      val graph = InstanceKeyGraph.apply(firrtlCircuit).graph
      // test the topGraph like following code
      //
      //                           LazyModule_1
      //                                 *
      //                               *   *
      //                             *       *
      //                           *           *
      //                         *               *
      //                 myLazyScope               None
      //                       *
      //                     *   *
      //                   *       *
      //      lazyModuleInScope     None
      //              *
      //              *
      //            None
      val topGraph = graph.getVertices.map(v => v -> graph.getEdges(v))
      //test graph -> (InstanceKey("parent.name","parent.module.name"),Set(InstanceKey("children","children.module.name")))
      utest.assert(
        topGraph.head == (InstanceKey("LazyModule_1", "LazyModule_1"), Set(
          InstanceKey("myLazyScope", "moduleDesiredName")
        ))
      )
      utest.assert(
        topGraph.tail.head == (InstanceKey("myLazyScope", "moduleDesiredName"), Set(
          InstanceKey("lazyModuleInScope", "LazyModule")
        ))
      )
      utest.assert(topGraph.tail.tail.head == (InstanceKey("lazyModuleInScope", "LazyModule"), Set()))

      utest.assert(sourceModule.children.head.className == "SimpleLazyModule")
      utest.assert(sourceModule.children.head.name == "myLazyScope")
      utest.assert(sourceModule.children.head.children.head.className == "LazyModule")
      utest.assert(sourceModule.children.head.children.head.name == "lazyModuleInScope")
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
      val genOption = () => UInt(32.W)
      class DemoSource(implicit valName: sourcecode.Name) extends BundleBridgeSource[UInt](Some(genOption))

      class LazyScopeModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        lazy val module = new LazyModuleImp(this) {}
        utest.assert(LazyModule.getScope.get.toString.contains("LazyScopeModule"))
      }

      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        val source = new DemoSource
        val sink = source.makeSink()
        val lazyModuleInScope =
          LazyScope.apply[LazyModule]("myLazyScope", "moduleDesiredName")(LazyModule(new LazyScopeModule))(p)
        lazy val module = new LazyModuleImp(this) {
          source.bundle := 4.U
        }
        utest.assert(LazyModule.getScope.get.toString.contains("DemoLazyModule"))
      }
      val lm = LazyModule(new DemoLazyModule)
      chisel3.stage.ChiselStage.elaborate(lm.module)
      // protected[diplomacy] val parent: Option[LazyModule]=LazyModule.scope
      // default scope is None, when LazyModule is on the top of hierarchy
      utest.assert(LazyModule.getScope == None)
    }

    test("test def shouldBeInlined") {
      implicit val p = Parameters.empty
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
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
      // test method shouldBeInlined: The default heuristic is to inline any parents whose children have been inlined
      // and whose nodes all produce identity circuits
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
        lazy val module = new LazyModuleImp(this) {}.suggestName("myModule")
      }
      val topModule = LazyModule(new TopLazyModule).suggestName("myTopModule")
      utest.assert(topModule.name == "myTopModule")
      chisel3.stage.ChiselStage.elaborate(topModule.module)
      // desiredName return the Scala class name of this module's wrapper
      utest.assert(topModule.module.desiredName == "LazyModule")
      // module.name = if(this.isInstanceOf[ModuleAspect]) desiredName else Builder.globalNamespace.name(desiredName)
      utest.assert(topModule.module.name == "LazyModule_2")
    }

    //TODO: have no idea to complete this test
    test("test LazyModule can be defined with LazyRawModuleImp") {
      class DemoLazyModule(implicit p: Parameters) extends LazyModule {
        override lazy val module = new LazyRawModuleImp(this) {
          childClock := false.B.asClock
          childReset := chisel3.DontCare
        }
      }
      implicit val p = Parameters.empty
      val demoModule = LazyModule(new DemoLazyModule).suggestName("demoModule")
      val testCircuit = new firrtl.stage.transforms.Compiler(Seq(Dependency[ExpandWhensAndCheck]))
        .runTransform(
          CircuitState(firrtl.Parser.parse(chisel3.stage.ChiselStage.emitFirrtl(demoModule.module)), UnknownForm)
        )
        .circuit
      val circuitGraph = CircuitGraph.apply(testCircuit)
      val C = CircuitTarget("LazyModule")
      val moduleTarget = C.module("LazyModule")
      utest.assert(
        circuitGraph.fanOutSignals(moduleTarget.ref("_childClock_T_1")).contains(moduleTarget.ref("childClock"))
      )
      utest.assert(circuitGraph.fanOutSignals(moduleTarget.ref("_childClock_T")).isEmpty)
      utest.assert(circuitGraph.fanOutSignals(moduleTarget.ref("childClock")).isEmpty)
      utest.assert(
        circuitGraph.fanInSignals(moduleTarget.ref("_childClock_T_1")).contains(moduleTarget.ref("@asClock#1"))
      )
      utest.assert(
        circuitGraph.fanInSignals(moduleTarget.ref("_childClock_T")).contains(moduleTarget.ref("@asClock#0"))
      )
      utest.assert(
        circuitGraph.fanInSignals(moduleTarget.ref("childClock")).contains(moduleTarget.ref("_childClock_T_1"))
      )
      /*val firrtlCircuit = (chisel3.stage.ChiselStage.emitFirrtl(demoModule.module))
      println(firrtlCircuit)*/
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
