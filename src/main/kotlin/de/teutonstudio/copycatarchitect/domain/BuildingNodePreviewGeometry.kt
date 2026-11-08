package de.teutonstudio.copycatarchitect.domain

/** Exact local volumes for definition nodes that describe geometry rather than material recipes. */
object BuildingNodePreviewGeometry {
    fun boxes(graph:BuildingGraph,node:BuildingNode):List<Box> {
        val n=graph.resolvedNode(node)
        return when(n.kind) {
            BuildingKind.AREA -> columns(n.coordinates,1)
            BuildingKind.ROOM_GEOMETRY -> columns(if(n.geometry.isNotEmpty()) n.coordinates else n.coordinates.ifEmpty { RoomGeometry.cells(n.rectangles).toList() },n.height)
            BuildingKind.SEGMENT_LAYOUT -> {
                val area=graph.areaOf(n)
                val occupied=area?.segmentCells()?.map { P(it.x,0,it.y) }?.toSet()
                val cells=when {
                    n.layoutCells.isNotEmpty() -> n.layoutCells.map { P(it.x,0,it.y) }
                    occupied!=null -> occupied.toList()
                    else -> (0 until n.height).flatMap { y -> (0 until n.length).map { x -> P(x,0,y) } }
                }.filter { p -> occupied==null || p in occupied }
                // A segment's raster lives in X/Y; its extrusion points along Z.
                columns(cells,n.depth).map { Box(it.x,it.z,it.y,it.maxX,it.maxZ,it.maxY) }
            }
            else -> emptyList()
        }
    }

    /** Merge complete rectangles without imposing the stricter room-footprint coordinate limits. */
    private fun columns(points:List<P>,height:Int):List<Box> {
        val remaining=points.toMutableSet();val result=mutableListOf<Box>()
        while(remaining.isNotEmpty()) {
            val first=remaining.minWith(compareBy({it.z},{it.x}))
            var width=1
            while(first+P(width,0,0) in remaining) width++
            var length=1
            while((0 until width).all { first+P(it,0,length) in remaining }) length++
            result+=Box(first.x,0,first.z,first.x+width,height,first.z+length)
            for(z in 0 until length) for(x in 0 until width) remaining-=first+P(x,0,z)
        }
        return result
    }
}
