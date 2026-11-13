package de.teutonstudio.copycatarchitect.domain

import java.util.UUID

/** One domain edit per group action, including internal cables and protected result nodes. */
object GraphSelectionEdits {
    fun remove(graph:NodeGraph,selection:Set<String>):NodeGraph {
        val removed=graph.nodes.filter { it.id in selection && it.kind!=NodeKind.OUTPUT }.map { it.id }.toSet()
        return GraphGroups.synchronize(graph,graph.copy(nodes=graph.nodes.filter { it.id !in removed }.map { n -> n.copy(inputs=n.inputs.map { if(it in removed) "" else it }) },
            nodeGroups=GraphGroups.prune(graph.nodeGroups,graph.nodes.map { it.id }.toSet()-removed)))
    }
    fun duplicate(graph:NodeGraph,selection:Set<String>):Pair<NodeGraph,Set<String>> {
        val originals=graph.nodes.filter { it.id in selection && it.kind!=NodeKind.OUTPUT }
        val ids=originals.associate { it.id to UUID.randomUUID().toString() }
        val copies=originals.map { n -> n.copy(id=ids.getValue(n.id),x=(n.x+32).coerceIn(-8000,8000),y=(n.y+32).coerceIn(-8000,8000),inputs=n.inputs.map { ids[it] ?: it }) }
        val groups=GraphGroups.captureLayouts(graph.nodeGroups,graph.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        return graph.copy(nodes=graph.nodes+copies,nodeGroups=GraphGroups.duplicate(groups,ids,(graph.nodes+copies).map { it.id }.toSet())) to ids.values.toSet()
    }
    fun align(graph:NodeGraph,selection:Set<String>,horizontal:Boolean):NodeGraph {
        val nodes=graph.nodes.filter { it.id in selection }
        if(nodes.size<2) return graph
        val coordinate=if(horizontal) nodes.minOf { it.y } else nodes.minOf { it.x }
        return graph.copy(nodeGroups=GraphGroups.captureLayouts(graph.nodeGroups,graph.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) }),
            nodes=graph.nodes.map { n -> if(n.id !in selection) n else if(horizontal) n.copy(y=coordinate) else n.copy(x=coordinate) })
    }
    fun assignGroup(graph:NodeGraph,selection:Set<String>,group:String):NodeGraph = GraphGroups.synchronize(graph,graph.copy(nodes=graph.nodes.map { n ->
        if(n.id !in selection) n else when(n.kind) {
            NodeKind.INPUT -> n.copy(part=n.part.copy(group=group))
            NodeKind.SAVED -> n.copy(savedCopycat=n.savedCopycat?.let { it.copy(parts=it.parts.map { p -> p.copy(group=group) }) })
            NodeKind.UNION,NodeKind.SYMMETRIC_DIFFERENCE -> n.copy(materialMode=MaterialMode.GROUPED,materialGroup=group)
            else -> n
        }
    }))
}
