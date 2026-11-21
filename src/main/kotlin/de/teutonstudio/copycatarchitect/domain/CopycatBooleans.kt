package de.teutonstudio.copycatarchitect.domain

/** Building-graph Copycat operations retain a single block as their output type. */
object CopycatBooleans {
    val kinds=setOf(BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,
        BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE)
    val sourceKinds=setOf(BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_POSITION,BuildingKind.COPYCAT_TRANSFORM)+kinds

    fun evaluate(node:BuildingNode,inputs:List<SegmentCell>,forms:MutableMap<String,Form>,palette:Map<String,String>):SegmentCell {
        require(node.kind in kinds)
        if(node.kind in CopycatSetInputs.buildingKinds && inputs.isEmpty()) {
            if(node.kind==BuildingKind.COPYCAT_UNION) return SegmentCell(Cell(block="minecraft:air",empty=true))
            val id="identity_${node.id}@1"
            forms[id]=Form(id="identity_${node.id}",name="Vollblock",parts=listOf(Part("copycats:copycat_block")))
            return SegmentCell(Cell(form=id,role=node.role))
        }
        if(node.kind in CopycatSetInputs.buildingKinds && inputs.size==1)
            return inputs.single().let { if(node.materialMode==MaterialMode.GROUPED) it.copy(overrideRole=node.role) else it }
        val operation=node.copy(kind=BuildingKind.valueOf(node.kind.name.replaceFirst("COPYCAT_","SEGMENT_")))
        // Use the same exact geometry, material and source-receipt rules as segment operations.
        val result=SegmentBooleans.evaluate(operation,inputs.map { FixedSegment(1,1,listOf(it)) },forms,palette)
        check(result.cells.size<=1) { "A Copycat operation must produce at most one block" }
        return result.cells.singleOrNull() ?: SegmentCell(Cell(block="minecraft:air",empty=true))
    }
}


/** Missing connections are absent operands, distinct from connected empty geometry. */
object CopycatSetInputs {
    val shapeKinds=setOf(NodeKind.UNION,NodeKind.INTERSECTION)
    val buildingKinds=setOf(BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION)
    fun connected(node:GraphNode):GraphNode {
        if(node.kind !in shapeKinds || node.inputs.all(String::isNotEmpty)) return node
        val slots=node.inputs.indices.filter { node.inputs[it].isNotEmpty() }
        return node.copy(inputs=slots.map { node.inputs[it] },inputGroups=if(node.inputGroups.isEmpty()) emptyList() else slots.map { node.inputGroups.getOrNull(it).orEmpty() })
    }
    fun identity(node:GraphNode,groups:List<MaterialGroup>):List<Part> =
        if(node.kind==NodeKind.INTERSECTION) listOf(Part("copycats:copycat_block",group=node.materialGroup.ifEmpty { groups.first().id })) else emptyList()
}
