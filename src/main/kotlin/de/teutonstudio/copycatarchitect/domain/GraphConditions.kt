package de.teutonstudio.copycatarchitect.domain

/** Types belong to values. Conditional nodes never introduce an untyped Any socket. */
object GraphTypes {
    val logicKinds=setOf("BOOLEAN","INT_COMPARE","BOOLEAN_LOGIC")
    val newKinds=logicKinds+setOf("CONDITIONAL","ROOF_TEMPLATE","SERVICE_SHAFT_SEGMENT","ELEVATOR_SHAFT_SEGMENT")
    private fun infer(id:String,kind:(String)->String,inputs:(String)->List<String>,output:(String)->String?,modern:Boolean,active:MutableSet<String>):String? {
        if(id.isEmpty()) return null
        require(active.add(id)) { "Cycle in value types at $id" }
        try {
            val k=kind(id);val refs=inputs(id)
            fun type(source:String)=infer(source,kind,inputs,output,modern,active)
            if(k=="CONDITIONAL") {
                val types=listOf(0,2).mapNotNull { refs.getOrNull(it)?.takeIf(String::isNotEmpty)?.let(::type) }.distinct()
                if(modern && types.isNotEmpty() && types.all { it in setOf("INT","COUNT") }) return if("INT" in types) "INT" else "COUNT"
                require(types.size<=1) { "Bedingte Abbildung benötigt paarweise gleiche Werttypen" }
                return types.singleOrNull()
            }
            if(modern && k in GraphNumbers.derived) {
                val types=refs.map { if(it.isEmpty()) null else type(it) }
                if("INT" in types) return "INT"
                return if(types.size>=2 && types.all { it=="COUNT" }) "COUNT" else null
            }
            return output(id)
        } finally { active.remove(id) }
    }
    fun shape(nodes:Map<String,GraphNode>,id:String):String?=infer(id,{nodes.getValue(it).kind.name},{nodes.getValue(it).inputs},{GraphModels.shape(nodes.getValue(it).kind).output},GraphNumbers.modern(nodes),mutableSetOf())
    fun building(nodes:Map<String,BuildingNode>,id:String):String?=infer(id,{nodes.getValue(it).kind.name},{nodes.getValue(it).inputs},{buildingGraphType(nodes.getValue(it).kind)},GraphNumbers.modern(nodes),mutableSetOf())
    fun building(graph:BuildingGraph,id:String)=building(GraphNumbers.buildingContext(graph),id)
    fun expectedShape(nodes:Map<String,GraphNode>,n:GraphNode,slot:Int):String? = if(n.kind==NodeKind.CONDITIONAL) {
        if(slot==1) "BOOLEAN" else shape(nodes,n.id)
    } else GraphModels.shape(n.kind).inputs?.type(slot)
    fun accepts(graph:BuildingGraph,port:BuildingPort,source:BuildingNode):Boolean {
        val actual=building(graph,source.id) ?: return true
        if(port.valueType!=null) return GraphNumbers.compatible(actual,port.valueType)
        return port.accepts.filter { it!=BuildingKind.CONDITIONAL }.any { GraphNumbers.compatible(actual,buildingGraphType(it)) || actual=="ROOF" && buildingGraphType(it)=="WALL" }
    }

}


/** Lazy node lookup preserves the original graph and all its structural/group validation. */
object GraphConditions {
    private fun selected(inputs:List<String>,boolean:(String)->Boolean):String {
        require(inputs.size==3 && inputs.all(String::isNotEmpty)) { "Wert A, Boolean und Wert B verbinden" }
        return inputs[if(boolean(inputs[1])) 0 else 2]
    }
    fun shapeNodes(raw:Map<String,GraphNode>):Map<String,GraphNode> {
        val values=GraphValues.shape(raw);val active=mutableSetOf<String>()
        fun read(id:String):GraphNode? {
            val n=raw[id] ?: return null
            if(n.kind!=NodeKind.CONDITIONAL) return n
            require(active.add(id)) { "Cycle in conditional values" }
            try { return read(selected(n.inputs,values::boolean)) } finally { active.remove(id) }
        }
        return object:Map<String,GraphNode> by raw,NumericContext { override val modernNumbers=GraphNumbers.modern(raw);override fun get(key:String)=read(key) }
    }
    fun buildingNodes(raw:Map<String,BuildingNode>):Map<String,BuildingNode> {
        val values=GraphValues.building(raw);val active=mutableSetOf<String>()
        fun read(id:String):BuildingNode? {
            val n=raw[id] ?: return null
            if(n.kind!=BuildingKind.CONDITIONAL) return n
            require(active.add(id)) { "Cycle in conditional values" }
            try { return read(selected(n.inputs,values::boolean)) } finally { active.remove(id) }
        }
        return object:Map<String,BuildingNode> by raw,NumericContext { override val modernNumbers=GraphNumbers.modern(raw);override fun get(key:String)=read(key) }
    }
    fun buildingNode(graph:BuildingGraph,id:String)=buildingNodes(GraphNumbers.buildingContext(graph)).getValue(id)
}
