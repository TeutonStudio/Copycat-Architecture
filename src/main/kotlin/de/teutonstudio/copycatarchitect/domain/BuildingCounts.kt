package de.teutonstudio.copycatarchitect.domain

object BuildingCounts {
    val kinds=BuildingKind.entries.filter { it.name in GraphValues.integerKinds }.toSet()
    fun validate(n:BuildingNode) {
        require(n.countValue==null || n.kind==BuildingKind.INT) { "Anzahl values belong to INT nodes" }
        require(n.kind!=BuildingKind.INT || n.inputs.isEmpty()) { "Anzahl sources have no inputs" }
        val allowed=when { n.kind==BuildingKind.ROTATION_SEGMENT -> setOf("length");n.kind==BuildingKind.AREA && n.areaMode==AreaMode.SQUARE -> setOf("length");n.kind==BuildingKind.AREA && n.areaMode==AreaMode.RECTANGLE -> setOf("length","height");else -> emptySet() }
        require(n.dimensions.orEmpty().keys.all { it in allowed }) { "Anzahl dimensions do not match this node and area mode" }
    }
    fun resolve(graph:BuildingGraph,n:BuildingNode,strict:Boolean=false):BuildingNode {
        validate(n)
        var resolved=n
        for((key,id) in n.dimensions.orEmpty()) {
            if(id.isEmpty()) continue
            val value=try { GraphValues.building(graph.nodes.associateBy { it.id }).integer(id) }
            catch(e:GraphValues.IncompleteValue) { if(strict) throw e;continue }
            require(value in 1..128) { "Dimensions must be between 1 and 128" }
            resolved=if(key=="length") resolved.copy(length=value) else resolved.copy(height=value)
        }
        return resolved
    }
}
