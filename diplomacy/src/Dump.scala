package cde

import scala.collection.mutable

object ParameterDump {
  val dump = mutable.Set[Tuple2[Any,Any]]()
  val knobList = mutable.ListBuffer[Any]()
  def apply[T](key:Any,value:T):T = {addToDump(key,value); value}
  def apply[T](knob:Knob[T]):Knob[T] = {knobList += knob.name; knob}
  def addToDump(key:Any,value:Any) = dump += ((key,value))
  def getDump:String = dump.map(_.toString).reduce(_+"\n"+_) + "\n"
}
