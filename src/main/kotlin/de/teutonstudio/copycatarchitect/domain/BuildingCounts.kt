package de.teutonstudio.copycatarchitect.domain

/** Integer source contract shared with the upcoming Anzahl arithmetic nodes. */
object BuildingCounts {
    fun validate(n:BuildingNode) {
        require(n.countValue==null || n.kind==BuildingKind.INT) { "Anzahl values belong to INT nodes" }
        require(n.kind!=BuildingKind.INT || n.inputs.isEmpty()) { "Anzahl sources have no inputs" }
        require(n.dimensions.orEmpty().isEmpty() || n.kind==BuildingKind.ROTATION_SEGMENT && n.dimensions.orEmpty().keys.all { it=="length" }) { "Only rotation segments accept an Anzahl length" }
    }
    fun resolve(graph:BuildingGraph,n:BuildingNode):BuildingNode {
        validate(n)
        val id=n.dimensions.orEmpty()["length"].orEmpty()
        if(id.isEmpty()) return n
        val source=graph.nodes.firstOrNull { it.id==id } ?: error("Missing Anzahl source $id")
        require(source.kind==BuildingKind.INT) { "Length requires an Anzahl source" }
        val value=source.countValue ?: 0
        require(value in 1..128) { "Length must be between 1 and 128" }
        return n.copy(length=value)
    }
}
