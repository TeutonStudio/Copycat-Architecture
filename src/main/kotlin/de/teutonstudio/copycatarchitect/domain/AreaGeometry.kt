package de.teutonstudio.copycatarchitect.domain

/** A two-dimensional raster domain, shared by room and segment geometry. */
data class Area(val points:List<P>) {
    init {
        require(points.size in 1..16384) { "An area needs 1–16384 raster points" }
        require(points.distinct().size==points.size) { "Area points must be unique" }
        require(points.all { it.y==0 && it.x in -128..127 && it.z in -128..127 }) { "Area points need x,z coordinates between -128 and 127" }
    }
    val minX get()=points.minOf { it.x }
    val minZ get()=points.minOf { it.z }
    val maxZ get()=points.maxOf { it.z }
    val width get()=points.maxOf { it.x }-minX+1
    val length get()=maxZ-minZ+1
    /** Raster rows run down the screen; segment height runs up in the world. */
    fun segmentPoint(x:Int,y:Int)=P(x+minX,0,maxZ-y)
    fun segmentCells():List<P> {
        val left=minX;val top=maxZ
        return points.map { P(it.x-left,top-it.z,0) }
    }
}
data class RoomVolume(val area:Area,val height:Int) {
    init {
        require(height in 1..16) { "Room height must be 1–16" }
        RoomFootprintEditor.validate(area.points.toSet())
        RoomGeometry.fromCells(area.points.toSet())
    }
}
data class SegmentVolume(val area:Area,val depth:Int) {
    init {
        require(depth in 1..128) { "Segment depth must be 1–128" }
        require(area.width in 1..128 && area.length in 1..128) { "Segment surface dimensions must be 1–128" }
        require(area.width.toLong()*area.length*depth<=262144) { "Segment exceeds 262144 cells" }
    }
}

fun BuildingGraph.areaOf(node:BuildingNode):Area? {
    val source=if(node.kind==BuildingKind.AREA) node else nodes.firstOrNull { it.id==node.geometry && it.kind==BuildingKind.AREA }
    return source?.coordinates?.takeIf { it.isNotEmpty() }?.let(::Area)
}

/** Expand a collapsed choice to the logical boundaries it represents, retaining it after growth. */
private fun expandedRegionKeys(key:String):List<String> {
    if(!key.matches(Regex("region:x[01ib]_y[01ib]_z[01ib]"))) return emptyList()
    val pieces=key.removePrefix("region:").split('_')
    val states=pieces.map { piece -> if(piece[1]=='b') listOf("0","i","1") else listOf(piece.substring(1)) }
    return buildList { for(x in states[0]) for(y in states[1]) for(z in states[2]) add("region:x${x}_y${y}_z${z}") }
}

private fun BuildingNode.expandedRegionCopies():Map<String,String> = linkedMapOf<String,String>().also { result ->
    regionCopies.entries.sortedWith(compareByDescending<Map.Entry<String,String>> { it.key.count { c -> c=='b' } }.thenBy { it.key }).forEach { (key,source) ->
        if(source.isNotEmpty()) expandedRegionKeys(key).forEach { result.putIfAbsent(it,source) }
    }
}

/** Collapse coincident sockets; explicit collapsed choices win, then the low end. */
fun BuildingNode.effectiveRegionCopies():Map<String,String> {
    val valid=SegmentRegions.of(length,height,depth).map { it.key }.toSet()
    val dims=listOf(length,height,depth)
    return linkedMapOf<String,String>().also { result ->
        expandedRegionCopies().toSortedMap().forEach { (key,source) ->
            val target="region:"+key.removePrefix("region:").split('_').mapIndexed { index,piece -> if(dims[index]==1) piece.take(1)+"b" else piece }.joinToString("_")
            if(target in valid) result.putIfAbsent(target,source)
        }
    }
}

/** Edit every alias of one visible socket without retaining hidden duplicate connections. */
fun BuildingNode.withRegionCopy(key:String,source:String):BuildingNode {
    require(SegmentRegions.of(length,height,depth).any { it.key==key }) { "Unknown segment region" }
    val changed=expandedRegionCopies().toMutableMap()
    for(alias in expandedRegionKeys(key)) if(source.isEmpty()) changed.remove(alias) else changed[alias]=source
    return copy(regionCopies=changed)
}
