package de.teutonstudio.copycatarchitect.domain

import java.math.BigInteger

/** Integer and vector values use the same arithmetic on the JVM and in Kotlin/JS. */
object GraphValues {
    val operations=listOf("ADD","SUBTRACT","MULTIPLY","DIVIDE","MIN","MAX")
    val variadicKinds=setOf("INT_ADD","INT_MULTIPLY","INT_MIN","INT_MAX","POSITION_ADD","POSITION_MULTIPLY","POSITION_MIN","POSITION_MAX")
    val integerKinds=setOf("NUMBER_OPERATOR","COUNT","INT_ABS","INT_TO_COUNT","INT_REMAINDER","INT","POSITION_X","POSITION_Y","POSITION_Z")+operations.map { "INT_$it" }
    val vectorKinds=setOf("POSITION_OPERATOR","POSITION")+operations.map { "POSITION_$it" }
    val kinds=integerKinds+vectorKinds+GraphTypes.logicKinds
    class IncompleteValue(message:String):IllegalArgumentException(message)
    data class ValueNode(val id:String,val kind:String,val inputs:List<String>,val count:Int?,val point:P?,val boolean:Boolean?=null,val operation:String?=null)
    fun shape(nodes:Map<String,GraphNode>,signed:Boolean=GraphNumbers.modern(nodes))=Evaluation(nodes.mapValues { (_,n)->ValueNode(n.id,n.kind.name,n.inputs,n.countValue,n.translation,n.booleanValue,n.operation) },signed,{ id->UnifiedOperators.value(UnifiedOperators.valueNodes(nodes.values.toList()),id) })
    fun building(nodes:Map<String,BuildingNode>,signed:Boolean=GraphNumbers.modern(nodes))=Evaluation(nodes.mapValues { (_,raw)->val n=GraphPrograms.producer(raw);ValueNode(n.id,n.kind.name,GraphPrograms.source(n)?.let { listOf(it) } ?: n.inputs,n.countValue ?: if(n.kind==BuildingKind.INT) 4 else null,n.translation,n.booleanValue,n.operation) },signed,{ id->UnifiedOperators.value(UnifiedOperators.buildingValueNodes(nodes.values.toList()),id) })
    class Evaluation(private val nodes:Map<String,ValueNode>,private val signed:Boolean=false,private val unified:(String)->Any) {
        private val integers=mutableMapOf<String,Int>();private val vectors=mutableMapOf<String,P>();private val active=mutableSetOf<String>()
        private fun node(id:String):ValueNode {
            val n=nodes[id] ?: error("Missing parameter source $id")
            if(n.kind in setOf("RESULT","GROUP_INPUT","GRAPH_REFERENCE")) {
                require(active.add("alias:$id")) { "Cycle in graph arguments" };try { val source=n.inputs.firstOrNull().orEmpty();if(source.isEmpty()) throw IncompleteValue("Gruppeneingang $id benötigt einen Wert oder Standardwert");return node(source) } finally { active.remove("alias:$id") }
            }
            if(n.kind!="CONDITIONAL") return n
            require(active.add("alias:$id")) { "Cycle in conditional values" }
            try {
                require(n.inputs.size==3 && n.inputs.all(String::isNotEmpty)) { "Wert A, Boolean und Wert B verbinden" }
                return node(n.inputs[if(boolean(n.inputs[1])) 0 else 2])
            } finally { active.remove("alias:$id") }
        }
        fun boolean(id:String):Boolean {
            require(active.add("boolean:$id")) { "Cycle in Boolean graph" }
            try {
                val n=node(id)
                return when(n.kind) {
                    "RELATION_OPERATOR","STATEMENT_OPERATOR"->unified(id) as Boolean
                    "BOOLEAN"->n.boolean ?: false
                    "INT_COMPARE"->{ val a=integer(input(n,0));val b=integer(input(n,1))
                        when(n.operation ?: "EQUAL") { "EQUAL"->a==b;"NOT_EQUAL"->a!=b;"LESS"->a<b;"LESS_EQUAL"->a<=b;"GREATER"->a>b;"GREATER_EQUAL"->a>=b;"DIVISIBLE"->{require(b!=0) { "Teilbarkeit: Divisor 0 ist ungültig" };a.toLong()%b.toLong()==0L};else->error("Unbekannter Zahlenvergleich") }
                    }
                    "BOOLEAN_LOGIC"->{ val a=boolean(input(n,0));val op=n.operation ?: "AND"
                        if(op=="NOT") !a else { val b=boolean(input(n,1));when(op) { "AND"->a && b;"OR"->a || b;"XOR"->a xor b;else->error("Unbekannte Booleanoperation") } }
                    }
                    else->error("Boolean-Eingang benötigt einen Booleanwert")
                }
            } finally { active.remove("boolean:$id") }
        }
        private fun input(n:ValueNode,index:Int)=n.inputs.getOrNull(index)?.takeIf(String::isNotEmpty) ?: throw IncompleteValue("Connect every input of ${n.kind}")
        private fun calculate(operation:String,a:Int,b:Int):Int {
            val x=a.toLong();val y=b.toLong()
            val result=when(operation) {
                "ADD"->x+y;"SUBTRACT"->x-y;"MULTIPLY"->x*y
                "DIVIDE"->{require(y!=0L) { "Integer division by zero" };x/y}
                "REMAINDER"->{require(y!=0L) { "Integer remainder by zero" };require(!(x==Int.MIN_VALUE.toLong() && y == -1L)) { "Integer overflow" };x-(x/y)*y}
                "MIN"->minOf(x,y);"MAX"->maxOf(x,y)
                else->error("Unknown integer operation $operation")
            }
            require(result in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Integer overflow" }
            return result.toInt()
        }
        private fun calculateAll(operation:String,values:List<Int>):Int {
            if(operation !in setOf("ADD","MULTIPLY")) return values.drop(1).fold(values.first()) { a,b -> calculate(operation,a,b) }
            // Validate the final value so a permutation cannot introduce an intermediate overflow.
            val exact=values.map { BigInteger.valueOf(it.toLong()) }.reduce { a,b -> if(operation=="ADD") a+b else a*b }
            require(exact>=BigInteger.valueOf(Int.MIN_VALUE.toLong()) && exact<=BigInteger.valueOf(Int.MAX_VALUE.toLong())) { "Integer overflow" }
            return exact.toString().toInt()
        }
        fun integer(id:String):Int {
            integers[id]?.let { return it };require(active.add(id)) { "Cycle in parameter graph at $id" }
            try {
                val n=node(id);require(n.kind in integerKinds) { "Anzahl input requires an integer value" }
                val result=when(n.kind) {
                    "NUMBER_OPERATOR"->unified(id) as Int
                    "INT"->n.count ?: 0
                    "COUNT"->(n.count ?: 0).also { require(it>=0) { "Anzahl muss mindestens 0 sein" } }
                    "INT_ABS"->integer(input(n,0)).let { require(it!=Int.MIN_VALUE) { "Integer overflow" };kotlin.math.abs(it) }
                    "INT_TO_COUNT"->integer(input(n,0)).also { require(it>=0) { "Als Anzahl: negative Ganzzahl" } }
                    "POSITION_X"->vector(input(n,0)).x;"POSITION_Y"->vector(input(n,0)).y;"POSITION_Z"->vector(input(n,0)).z
                    else->calculateAll(n.kind.removePrefix("INT_"),n.inputs.indices.map { integer(input(n,it)) })
                }
                integers[id]=result;return result
            } finally { active.remove(id) }
        }
        fun vector(id:String):P {
            vectors[id]?.let { return it };require(active.add(id)) { "Cycle in parameter graph at $id" }
            try {
                val n=node(id);require(n.kind in vectorKinds) { "Position input requires a vector value" }
                val result=if(n.kind=="POSITION_OPERATOR") unified(id) as P else if(n.kind=="POSITION") {
                    val fallback=n.point ?: P()
                    fun component(index:Int,value:Int)=n.inputs.getOrNull(index)?.takeIf(String::isNotEmpty)?.let(::integer) ?: value
                    P(component(0,fallback.x),component(1,fallback.y),component(2,fallback.z))
                } else {
                    val operation=n.kind.removePrefix("POSITION_")
                    val values=n.inputs.indices.map { vector(input(n,it)) }
                    P(calculateAll(operation,values.map { it.x }),calculateAll(operation,values.map { it.y }),calculateAll(operation,values.map { it.z }))
                }
                require(signed || n.kind=="POSITION_OPERATOR" || n.kind=="POSITION" && n.inputs.isEmpty() || listOf(result.x,result.y,result.z).all { it>=0 }) { "hB coordinates must be non-negative integers" }
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
