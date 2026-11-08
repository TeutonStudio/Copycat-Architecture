package de.teutonstudio.copycatarchitect.domain

/** Explicit, reversible opt-in: existing complete wall recipes remain authoritative prefabs. */
object SemanticConversion {
    fun convert(graph:BuildingGraph,storeyId:String):BuildingGraph {
        val storey=graph.nodes.firstOrNull { it.id==storeyId && it.kind==BuildingKind.STOREY }
            ?: error("Select a stockwork to convert")
        require(storey.storeyMode=="rooms") { "Only a stockwork assembled from rooms can be converted without guessing its rooms" }
        require(storey.inputs.isNotEmpty() && storey.inputs.all(String::isNotEmpty)) { "Connect the stockwork's rooms before converting it" }
        val byId=graph.nodes.associateBy { it.id }
        val originals=storey.inputs.distinct().map { byId[it]?.takeIf { n -> n.kind==BuildingKind.ROOM } ?: error("A stockwork input is not a room") }
        val legacy=originals.filter { it.roomConfig==null }
        if(legacy.isEmpty() && storey.facade!=null) return if(graph.schema<3) graph.copy(schema=3) else graph
        require(graph.nodes.size+legacy.size<=96) { "Conversion needs a separate draft for each legacy room and would exceed 96 nodes" }
        val used=graph.nodes.map { it.id }.toMutableSet()
        val mapping=legacy.associate { room ->
            var suffix=1;var label="_semantic";var id=room.id.take(36-label.length)+label
            while(id in used) { label="_semantic_${suffix++}";id=room.id.take(36-label.length)+label }
            used+=id;room.id to id
        }
        val copies=legacy.map { room ->
            // The legacy adapter keeps all explicit walls, openings and the original footprint.
            // No window masks or column identities are inferred from already-united geometry.
            room.copy(id=mapping.getValue(room.id),x=(room.x+32).coerceIn(-8192,8192),y=(room.y+32).coerceIn(-8192,8192),
                roomConfig=RoomConfig(mode=RoomMode.FOOTPRINT))
        }
        val changedStorey=storey.copy(inputs=storey.inputs.map { mapping[it] ?: it },facade=storey.facade ?: FacadeConfig())
        // A converted stockwork is now a local definition. Detach its linked group family rather
        // than silently propagating semantic settings into unrelated stockworks.
        val detached=graph.nodeGroups.filter { storeyId in it.nodes }.map { it.effectiveDefinitionId }.toSet()
        return graph.copy(schema=3,nodes=graph.nodes.map { if(it.id==storeyId) changedStorey else it }+copies,
            nodeGroups=graph.nodeGroups.filter { it.effectiveDefinitionId !in detached })
    }
}
