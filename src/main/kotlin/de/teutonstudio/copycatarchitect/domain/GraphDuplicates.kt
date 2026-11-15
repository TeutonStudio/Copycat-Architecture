package de.teutonstudio.copycatarchitect.domain

/** Copies only edges inside the selection; caller-provided IDs make native/browser commands testable. */
object GraphDuplicates {
    private fun mapping(ids:List<String>,copies:List<String>,known:Set<String>):Map<String,String> {
        require(ids.isNotEmpty() && ids.size==ids.distinct().size && ids.size==copies.size && ids.all { it in known }) { "Select existing nodes to duplicate" }
        require(copies.size==copies.distinct().size && copies.none { it in known }) { "Duplicate IDs must be new and unique" }
        return ids.zip(copies).toMap()
    }
    private fun groups(groups:List<GraphGroup>,mapping:Map<String,String>)=groups.filter { it.nodes.all(mapping::containsKey) }.map { group ->
        group.copy(id=java.util.UUID.randomUUID().toString(),definitionId=group.effectiveDefinitionId,
            nodes=group.nodes.map(mapping::getValue),inputConnections=group.inputConnections.mapNotNull { (port,ref) -> mapping[ref]?.let { port to it } }.toMap())
    }
    fun shape(graph:NodeGraph,materials:List<MaterialGroup>,ids:List<String>,copies:List<String>):NodeGraph {
        val map=mapping(ids,copies,graph.nodes.map { it.id }.toSet())
        val nodes=graph.nodes.filter { it.id in map }
        require(nodes.none { it.kind==NodeKind.OUTPUT }) { "The result node cannot be duplicated" }
        val dx=if(nodes.any { it.x>8160 }) -32 else 32;val dy=if(nodes.any { it.y>8160 }) -32 else 32
        return graph.copy(nodes=graph.nodes+nodes.map { it.copy(id=map.getValue(it.id),x=it.x+dx,y=it.y+dy,inputs=it.inputs.map { ref -> map[ref].orEmpty() }) },
            nodeGroups=graph.nodeGroups+groups(graph.nodeGroups,map)).withTransformSchema().also { GraphCompiler.structure(it,materials) }
    }
    fun building(graph:BuildingGraph,ids:List<String>,copies:List<String>):BuildingGraph {
        val map=mapping(ids,copies,graph.nodes.map { it.id }.toSet())
        val nodes=graph.nodes.filter { it.id in map }
        require(nodes.none { it.id=="empty_wall" || it.id==graph.resultNode().id }) { "The result and empty wall cannot be duplicated" }
        val dx=if(nodes.any { it.x>8160 }) -32 else 32;val dy=if(nodes.any { it.y>8160 }) -32 else 32
        return graph.copy(nodes=graph.nodes+nodes.map { node -> GraphGroups.remap(node) { _,ref -> map[ref].orEmpty() }.copy(id=map.getValue(node.id),x=node.x+dx,y=node.y+dy) },
            nodeGroups=graph.nodeGroups+groups(graph.nodeGroups,map)).withCurrentSchema().also { BuildingGraphCompiler.structure(it) }
    }
}
