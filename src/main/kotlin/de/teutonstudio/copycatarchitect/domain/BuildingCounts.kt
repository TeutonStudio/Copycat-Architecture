package de.teutonstudio.copycatarchitect.domain

object BuildingCounts {
    val kinds=BuildingKind.entries.filter { it.name in GraphValues.integerKinds }.toSet()
    fun validate(n:BuildingNode) {
        require(n.countValue==null || n.kind==BuildingKind.INT) { "Anzahl values belong to INT nodes" }
        require(n.kind!=BuildingKind.INT || n.inputs.isEmpty()) { "Anzahl sources have no inputs" }
        require(n.dimensions.orEmpty().isEmpty() || n.kind==BuildingKind.ROTATION_SEGMENT && n.dimensions.orEmpty().keys.all { it=="length" }) { "Only rotation segments accept an Anzahl length" }
    }
    fun resolve(graph:BuildingGraph,n:BuildingNode,strict:Boolean=false):BuildingNode {
        validate(n)
        val id=n.dimensions.orEmpty()["length"].orEmpty()
        if(id.isEmpty()) return n
        val value=try { GraphValues.building(graph.nodes.associateBy { it.id }).integer(id) }
        catch(e:GraphValues.IncompleteValue) { if(strict) throw e;return n }
        require(value in 1..128) { "Length must be between 1 and 128" }
        return n.copy(length=value)
    }
}
