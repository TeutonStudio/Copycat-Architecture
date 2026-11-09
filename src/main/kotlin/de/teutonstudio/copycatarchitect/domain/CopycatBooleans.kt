package de.teutonstudio.copycatarchitect.domain

/** Building-graph Copycat operations retain a single block as their output type. */
object CopycatBooleans {
    val kinds=setOf(BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,
        BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE)
    val sourceKinds=setOf(BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE)+kinds

    fun evaluate(node:BuildingNode,inputs:List<SegmentCell>,forms:MutableMap<String,Form>,palette:Map<String,String>):SegmentCell {
        require(node.kind in kinds)
        val operation=node.copy(kind=BuildingKind.valueOf(node.kind.name.replaceFirst("COPYCAT_","SEGMENT_")))
        // Use the same exact geometry, material and source-receipt rules as segment operations.
        val result=SegmentBooleans.evaluate(operation,inputs.map { FixedSegment(1,1,listOf(it)) },forms,palette)
        check(result.cells.size<=1) { "A Copycat operation must produce at most one block" }
        return result.cells.singleOrNull() ?: SegmentCell(Cell(block="minecraft:air",empty=true))
    }
}
