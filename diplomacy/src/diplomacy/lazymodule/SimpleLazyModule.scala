package diplomacy.lazymodule

import chipsalliance.rocketchip.config.Parameters

/** Used for a [[LazyModule]] which does not need to define any [[LazyModuleImp]] implementation.
  *
  * It can be used as wrapper that only instantiates and connects [[LazyModule]]s.
  */
class SimpleLazyModule(implicit p: Parameters) extends LazyModule {
  lazy val module = new LazyModuleImp(this)
}
