package de.teutonstudio.copycatarchitect.domain

/** Numeric types are independent of the current values and shared by both editors. */
interface NumericContext { val modernNumbers:Boolean }
object GraphNumbers {
    val newKinds=setOf("COUNT","INT_ABS","INT_TO_COUNT","INT_REMAINDER")
    val numericKinds=setOf("INT","COUNT","INT_ABS","INT_TO_COUNT","INT_REMAINDER","INT_ADD","INT_SUBTRACT","INT_MULTIPLY","INT_DIVIDE","INT_MIN","INT_MAX","POSITION_X","POSITION_Y","POSITION_Z")
    val derived=setOf("INT_ADD","INT_MULTIPLY","INT_DIVIDE","INT_REMAINDER","INT_MIN","INT_MAX")
    fun modern(nodes:Map<String,*>):Boolean=(nodes as? NumericContext)?.modernNumbers ?: nodes.values.any { when(it) { is GraphNode->it.kind.name in newKinds;is BuildingNode->it.kind.name in newKinds;else->false } }
    fun <T> context(raw:Map<String,T>,modern:Boolean):Map<String,T> = object:Map<String,T> by raw,NumericContext { override val modernNumbers=modern }
    fun shapeContext(graph:NodeGraph)=context(graph.nodes.associateBy { it.id },graph.schema>=9)
    fun buildingContext(graph:BuildingGraph)=context(graph.nodes.associateBy { it.id },graph.schema>=13)
    fun compatible(actual:String?,expected:String?)=actual==null || expected==null || actual==expected || actual=="COUNT" && expected=="INT"
    fun producer(kind:String,type:String)=kind in numericKinds && type in setOf("INT","COUNT")
    fun countKeys(n:BuildingNode):Set<String> = when {
        n.kind==BuildingKind.ROTATION_SEGMENT->setOf("length")
        n.kind==BuildingKind.AREA && n.areaMode==AreaMode.SQUARE->setOf("length")
        n.kind==BuildingKind.AREA && n.areaMode==AreaMode.RECTANGLE->setOf("length","height")
        else->emptySet()
    }
    fun expected(graph:BuildingGraph,n:BuildingNode,port:BuildingPort):String? {
        if(graph.schema<13) return null
        if(port.key in countKeys(n)) return "COUNT"
        if(n.kind.name in numericKinds && port.key.startsWith("in:") && n.kind.name !in setOf("POSITION_X","POSITION_Y","POSITION_Z") || n.kind in setOf(BuildingKind.POSITION,BuildingKind.INT_COMPARE) && port.key.startsWith("in:")) return "INT"
        return null
    }
    fun portTypes(port:BuildingPort)=port.valueType?.let { listOf(it) } ?: port.accepts.filter { it!=BuildingKind.CONDITIONAL }.map(::buildingGraphType).filter { it!="VALUE" }.distinct()

    /** Promote only on use. Existing signed dimension links receive explicit, non-clamping adapters. */
    fun upgrade(graph:BuildingGraph,previousSchema:Int):BuildingGraph {
        if(previousSchema>=13) return graph
        var nodes=graph.nodes;var groups=graph.nodeGroups
        for(target in graph.nodes) for(key in countKeys(target)) {
            val source=target.dimensions.orEmpty()[key].orEmpty();if(source.isEmpty()) continue
            val candidate=graph.copy(nodes=nodes,nodeGroups=groups)
            if(GraphTypes.building(candidate,source)=="COUNT") continue
            val id=generateSequence(nodes.size) { it+1 }.map { "as_count_$it" }.first { id->nodes.none { it.id==id } }
            val adapter=BuildingNode(id,BuildingKind.INT_TO_COUNT,inputs=listOf(source),x=maxOf(-8192,target.x-210),y=target.y)
            nodes=nodes.map { if(it.id==target.id) it.copy(dimensions=it.dimensions.orEmpty()+(key to id)) else it }+adapter
            groups=groups.map { group ->
                val member=group.nodes.indexOf(target.id)
                if(member<0) group else group.copy(nodes=group.nodes+id,graphInterface=group.graphInterface?.let { face->face.copy(
                    inputs=face.inputs.map { input->input.copy(targets=input.targets.map { t->if(t.member==member && t.port==key) GraphGroupTarget(group.nodes.size,"in:0") else t }) },
                    positions=face.positions?.plus(GraphGroupPosition(adapter.x,adapter.y))) })
            }
        }
        return graph.copy(nodes=nodes,nodeGroups=groups)
    }

    private fun groups(groups:List<GraphGroup>,type:(String)->String?,changed:Set<String>):List<GraphGroup> = groups.map { group->group.copy(
        graphInterface=group.graphInterface?.let { face->face.copy(outputs=face.outputs.map { out->
            val id=group.nodes.getOrNull(out.member);val next=id?.let(type)
            if(id in changed && next!=null) out.copy(type=next) else out
        }) },inputConnections=group.inputConnections.mapValues { (_,source)->source }) }

    fun edit(before:NodeGraph,after:NodeGraph):NodeGraph {
        if(after.schema<9) return after
        val a=shapeContext(before);val b=shapeContext(after)
        val changed=after.nodes.filter { it.id in a && runCatching { GraphTypes.shape(a,it.id) }.getOrNull()!=runCatching { GraphTypes.shape(b,it.id) }.getOrNull() }.map { it.id }.toSet()
        var nodes=after.nodes.map { n->n.copy(inputs=n.inputs.mapIndexed { i,source->
            if(source in changed && !GraphNumbers.compatible(GraphTypes.shape(b,source),GraphTypes.expectedShape(b,n,i))) "" else source
        }) }
        val faces=groups(after.nodeGroups,{GraphTypes.shape(b,it)},changed).map { g->
            val invalid=g.graphInterface?.inputs.orEmpty().filter { input->g.inputConnections[input.id]?.let { it in changed && !compatible(GraphTypes.shape(b,it),input.type) }==true }
            for(input in invalid) for(t in input.targets) nodes=nodes.map { n->if(n.id==g.nodes[t.member]) n.copy(inputs=n.inputs.mapIndexed { i,v->if(i.toString()==t.port) "" else v }) else n }
            g.copy(inputConnections=g.inputConnections.filterKeys { key->invalid.none {it.id==key} })
        }
        return after.copy(nodes=nodes,nodeGroups=faces)
    }
    fun edit(before:BuildingGraph,after:BuildingGraph):BuildingGraph {
        if(after.schema<13) return after
        val a=buildingContext(before);val b=buildingContext(after)
        val changed=after.nodes.filter { it.id in a && runCatching { GraphTypes.building(a,it.id) }.getOrNull()!=runCatching { GraphTypes.building(b,it.id) }.getOrNull() }.map { it.id }.toSet()
        var nodes=after.nodes.map { n->
            val ports=BuildingPorts.of(n,after,true,true).associateBy { it.key }
            GraphGroups.remap(n) { key,source->if(source in changed && ports[key]?.let { !GraphTypes.accepts(after,it,b.getValue(source)) }==true) "" else source }
        }
        val faces=groups(after.nodeGroups,{GraphTypes.building(b,it)},changed).map { g->
            val invalid=g.graphInterface?.inputs.orEmpty().filter { input->g.inputConnections[input.id]?.let { it in changed && !compatible(GraphTypes.building(b,it),input.type) }==true }
            for(input in invalid) for(t in input.targets) nodes=nodes.map { n->if(n.id==g.nodes[t.member]) GraphGroups.remap(n) { key,ref->if(key==t.port) "" else ref } else n }
            g.copy(inputConnections=g.inputConnections.filterKeys { key->invalid.none {it.id==key} })
        }
        return after.copy(nodes=nodes,nodeGroups=faces)
    }
}
