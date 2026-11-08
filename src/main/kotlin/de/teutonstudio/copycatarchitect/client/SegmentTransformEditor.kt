package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*

object SegmentTransformEditor {
    fun format(transforms:List<SegmentTransform>,count:Int)=List(count) { transforms.getOrNull(it) ?: SegmentTransform() }
        .joinToString("; ") { "${it.offset.x},${it.offset.y},${it.offset.z},${it.axis},${it.quarterTurns*90}" }
    fun parse(text:String,count:Int):List<SegmentTransform> {
        val values=text.split(';','\n').map { row ->
            val columns=row.trim().split(',').map(String::trim)
            require(columns.size==5) { "Use x,y,z,axis,degrees for each segment" }
            val offset=columns.take(3).map { it.toIntOrNull() ?: error("Offsets must be integers") }
            require(offset.all { it in -128..128 }) { "Segment offsets must be between -128 and 128" }
            val axis=RotationAxis.valueOf(columns[3].uppercase())
            val degrees=columns[4].toIntOrNull() ?: error("Rotation must be 0, 90, 180 or 270")
            require(axis!=RotationAxis.NORMAL && degrees in listOf(0,90,180,270)) { "Use axis X, Y or Z and a quarter turn" }
            SegmentTransform(P(offset[0],offset[1],offset[2]),axis,degrees/90)
        }
        require(values.size==count) { "Enter one transformation for every segment input" }
        return values
    }
    fun open(node:BuildingNode,commit:(BuildingNode)->Unit) {
        EditorValueInput.text(tr("bg_segment_transforms"),format(node.transforms,node.inputs.size),2048,
            { runCatching { parse(it,node.inputs.size) }.isSuccess }) { commit(node.copy(transforms=parse(it,node.inputs.size),patternAxis="transform")) }
    }
}

fun segmentRegionLabel(key:String):String {
    val pieces=key.removePrefix("region:").split('_')
    val labels=pieces.mapNotNull { value -> when(value) {
        "x0"->ui("bg_region_left");"x1"->ui("bg_region_right");"xb"->ui("bg_region_left_right")
        "y0"->ui("bottom");"y1"->ui("top");"yb"->ui("bg_region_top_bottom")
        "z0"->ui("bg_region_front");"z1"->ui("bg_region_back");"zb"->ui("bg_region_front_back")
        else->null
    } }
    val kind=when(labels.size) { 3->"corner";2->"edge";1->"face";else->return ui("bg_region_inside") }
    return ui("bg_region_$kind",labels.joinToString(" / "))
}
