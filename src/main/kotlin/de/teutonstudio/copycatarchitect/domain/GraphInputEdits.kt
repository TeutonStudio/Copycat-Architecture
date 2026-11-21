package de.teutonstudio.copycatarchitect.domain

/** Input permutations preserve local cables, per-input metadata and named group terminals. */
object GraphInputEdits {
    private val binary=setOf("DIFFERENCE","SYMMETRIC_DIFFERENCE","COPYCAT_DIFFERENCE","COPYCAT_SYMMETRIC_DIFFERENCE","SEGMENT_DIFFERENCE","INT_SUBTRACT","INT_DIVIDE","INT_REMAINDER","POSITION_SUBTRACT","POSITION_DIVIDE","INT_COMPARE")
    private fun permutation(kind:String,order:List<Int>,size:Int)=kind in binary && order.size==size && order.sorted()==(0 until size).toList()

    private fun validate(order:List<Int>,size:Int,min:Int,max:Int,enabled:Boolean) {
        require(enabled) { "Dynamic inputs require a commutative and associative node" }
        require(order.size in min..max && order.all { it in -1 until size }) { "Invalid input count or index" }
        val retained=order.filter { it>=0 }
        require(retained.distinct().size==retained.size) { "An input may occur only once" }
        require(when(order.size-size) {
            0->retained.size==size
            1->retained.size==size && order.count { it==-1 }==1
            -1->retained.size==size-1
            else->false
        }) { "Change one input at a time" }
    }
    private fun affected(groups:List<GraphGroup>,id:String):Set<String> {
        val source=groups.firstOrNull { id in it.nodes } ?: return setOf(id)
        val instances=groups.filter { it.effectiveDefinitionId==source.effectiveDefinitionId }
        require(instances.none { it.libraryReference!=null }) { "Saved group references are read-only" }
        val member=source.nodes.indexOf(id)
        return instances.map { it.nodes[member] }.toSet()
    }
    private fun groups(groups:List<GraphGroup>,ids:Set<String>,order:List<Int>,building:Boolean):List<GraphGroup> = groups.map { group ->
        val face=group.graphInterface ?: return@map group
        val inputs=face.inputs.mapNotNull { input ->
            val targets=input.targets.mapNotNull { target ->
                if(group.nodes.getOrNull(target.member) !in ids) target else {
                    val index=if(building) target.port.takeIf { it.startsWith("in:") }?.substringAfter(':')?.toIntOrNull() else target.port.toIntOrNull()
                    if(index==null) target else order.indexOf(index).takeIf { it>=0 }?.let { target.copy(port=if(building) "in:$it" else it.toString()) }
                }
            }
            if(targets.isEmpty()) null else input.copy(targets=targets)
        }
        group.copy(graphInterface=face.copy(inputs=inputs),inputConnections=group.inputConnections.filterKeys { key -> inputs.any { it.id==key } })
    }
    private fun <T> reorder(values:List<T>,order:List<Int>,fallback:T):List<T> =
        if(values.isEmpty()) values else order.map { values.getOrElse(it) { fallback } }
    fun shape(graph:NodeGraph,id:String,order:List<Int>):NodeGraph {
        val node=graph.nodes.single { it.id==id };val spec=GraphModels.shape(node.kind);val limits=requireNotNull(spec.inputs)
        validate(order,node.inputs.size,limits.min,limits.max,spec.commutativeAssociative || permutation(node.kind.name,order,node.inputs.size))
        val ids=affected(graph.nodeGroups,id)
        return graph.copy(nodes=graph.nodes.map { n -> if(n.id !in ids) n else n.copy(
            inputs=order.map { n.inputs.getOrElse(it) { "" } },inputGroups=reorder(n.inputGroups,order,"")) },
            nodeGroups=groups(graph.nodeGroups,ids,order,false)).withTransformSchema()
    }
    fun building(graph:BuildingGraph,id:String,order:List<Int>):BuildingGraph {
        val node=graph.nodes.single { it.id==id };val spec=GraphModels.building.nodes.getValue(node.kind.name)
        val limits=GraphModels.buildingPort(node,"in:0")
        validate(order,node.inputs.size,limits.min ?: 2,limits.max ?: GraphModels.building.maxInputs,spec.commutativeAssociative || permutation(node.kind.name,order,node.inputs.size))
        val ids=affected(graph.nodeGroups,id)
        return graph.copy(nodes=graph.nodes.map { n -> if(n.id !in ids) n else n.copy(
            inputs=order.map { n.inputs.getOrElse(it) { "" } },offsets=reorder(n.offsets,order,P()),operandOffsets=n.operandOffsets?.let { values -> order.map { values.getOrElse(it) { P() } } },transforms=reorder(n.transforms,order,SegmentTransform())) },
            nodeGroups=groups(graph.nodeGroups,ids,order,true)).withCurrentSchema()
    }
}
