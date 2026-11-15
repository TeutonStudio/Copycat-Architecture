package de.teutonstudio.copycatarchitect.domain

/** Integer and vector values use the same arithmetic on the JVM and in Kotlin/JS. */
object GraphValues {
    val operations=listOf("ADD","SUBTRACT","MULTIPLY","DIVIDE","MIN","MAX")
    val integerKinds=setOf("INT","POSITION_X","POSITION_Y","POSITION_Z")+operations.map { "INT_$it" }
    val vectorKinds=setOf("POSITION")+operations.map { "POSITION_$it" }
    val kinds=integerKinds+vectorKinds
    class IncompleteValue(message:String):IllegalArgumentException(message)
    data class ValueNode(val id:String,val kind:String,val inputs:List<String>,val count:Int?,val point:P?)
    fun shape(nodes:Map<String,GraphNode>)=Evaluation(nodes.mapValues { (_,n)->ValueNode(n.id,n.kind.name,n.inputs,n.countValue,n.translation) })
    fun building(nodes:Map<String,BuildingNode>)=Evaluation(nodes.mapValues { (_,n)->ValueNode(n.id,n.kind.name,n.inputs,n.countValue ?: if(n.kind==BuildingKind.INT) 4 else null,n.translation) })
    class Evaluation(private val nodes:Map<String,ValueNode>) {
        private val integers=mutableMapOf<String,Int>();private val vectors=mutableMapOf<String,P>();private val active=mutableSetOf<String>()
        private fun node(id:String)=nodes[id] ?: error("Missing parameter source $id")
        private fun input(n:ValueNode,index:Int)=n.inputs.getOrNull(index)?.takeIf(String::isNotEmpty) ?: throw IncompleteValue("Connect both inputs of ${n.kind}")
        private fun calculate(operation:String,a:Int,b:Int):Int {
            val x=a.toLong();val y=b.toLong()
            val result=when(operation) {
                "ADD"->x+y;"SUBTRACT"->x-y;"MULTIPLY"->x*y
                "DIVIDE"->{require(y!=0L) { "Integer division by zero" };x/y}
                "MIN"->minOf(x,y);"MAX"->maxOf(x,y)
                else->error("Unknown integer operation $operation")
            }
            require(result in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Integer overflow" }
            return result.toInt()
        }
        fun integer(id:String):Int {
            integers[id]?.let { return it };require(active.add(id)) { "Cycle in parameter graph at $id" }
            try {
                val n=node(id);require(n.kind in integerKinds) { "Anzahl input requires an integer value" }
                val result=when(n.kind) {
                    "INT"->n.count ?: 0
                    "POSITION_X"->vector(input(n,0)).x;"POSITION_Y"->vector(input(n,0)).y;"POSITION_Z"->vector(input(n,0)).z
                    else->calculate(n.kind.removePrefix("INT_"),integer(input(n,0)),integer(input(n,1)))
                }
                integers[id]=result;return result
            } finally { active.remove(id) }
        }
        fun vector(id:String):P {
            vectors[id]?.let { return it };require(active.add(id)) { "Cycle in parameter graph at $id" }
            try {
                val n=node(id);require(n.kind in vectorKinds) { "Position input requires a vector value" }
                val result=if(n.kind=="POSITION") {
                    val fallback=n.point ?: P()
                    fun component(index:Int,value:Int)=n.inputs.getOrNull(index)?.takeIf(String::isNotEmpty)?.let(::integer) ?: value
                    P(component(0,fallback.x),component(1,fallback.y),component(2,fallback.z))
                } else {
                    val a=vector(input(n,0));val b=vector(input(n,1));val operation=n.kind.removePrefix("POSITION_")
                    P(calculate(operation,a.x,b.x),calculate(operation,a.y,b.y),calculate(operation,a.z,b.z))
                }
                require(n.kind=="POSITION" && n.inputs.isEmpty() || listOf(result.x,result.y,result.z).all { it>=0 }) { "hB coordinates must be non-negative integers" }
                vectors[id]=result;return result
            } finally { active.remove(id) }
        }
    }
}

/** Older documents omit optional sockets; editing reserves their typed inputs. */
fun GraphNode.withValueSockets():GraphNode {
    val reserved=when(kind) { NodeKind.MIRROR->2;NodeKind.POSITION->3;else->inputs.size }
    return if(inputs.size>=reserved) this else copy(inputs=inputs+List(reserved-inputs.size) { "" })
}
