package de.teutonstudio.copycatarchitect.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Gardens occupy raster cells; paths interpolate an ordered list of height-aware waypoints. */
object LandscapeGeometry {
    val kinds=setOf(BuildingKind.GARDEN,BuildingKind.PATH)
    val surfaceBlocks=listOf("grass_block","dirt","coarse_dirt","rooted_dirt","gravel","sand","stone","cobblestone","stone_bricks","moss_block","oak_planks","spruce_planks").map { "minecraft:$it" }
    val groundBlocks=listOf("dirt","coarse_dirt","stone","cobblestone","gravel","sand").map { "minecraft:$it" }
    val fenceBlocks=listOf("oak_fence","spruce_fence","birch_fence","jungle_fence","acacia_fence","dark_oak_fence","mangrove_fence","cherry_fence","bamboo_fence","crimson_fence","warped_fence","nether_brick_fence","cobblestone_wall","stone_brick_wall").map { "minecraft:$it" }
    val nativeBlocks=(surfaceBlocks+groundBlocks+fenceBlocks).toSet()
    fun validate(n:BuildingNode) {
        require(n.kind in kinds)
        require(n.landscapeOffset.y==0 && n.landscapeOffset.x in -128..128 && n.landscapeOffset.z in -128..128) { "Landscape position exceeds horizontal bounds" }
        require(n.groundDepth in 1..16 && n.clearance in 0..16 && n.elevation in -64..64 && n.pathWidth in 1..16 && n.fenceHeight in 1..4) { "Invalid landscape dimensions" }
        require(n.block in surfaceBlocks && n.groundBlock in groundBlocks && n.fenceBlock in fenceBlocks) { "Unsupported landscape block" }
        if(n.kind==BuildingKind.GARDEN) {
            if(n.coordinates.isNotEmpty()) Area(n.coordinates)
        } else {
            require(n.coordinates.size in 2..64) { "A path needs 2–64 ordered waypoints" }
            require(n.coordinates.all { it.x in -128..127 && it.z in -128..127 && it.y in -64..64 }) { "Path waypoints exceed landscape bounds" }
            require(n.coordinates.zipWithNext().all { (a,b) ->
                val distance=maxOf(abs(a.x-b.x),abs(a.z-b.z))
                distance>0 && abs(a.y-b.y)<=distance
            }) { "Path segments need distinct horizontal endpoints and at most one block of rise per step" }
        }
    }

    fun gardenCells(graph:BuildingGraph,n:BuildingNode):Set<P> {
        val cells=if(n.geometry.isNotEmpty()) graph.areaOf(n)?.points.orEmpty() else n.coordinates
        require(cells.isNotEmpty()) { "Define a garden area" }
        return Area(cells).points.toSet()
    }

    /** Square raster joins close diagonal gaps. Crossing portions must agree on their elevation. */
    fun pathCells(n:BuildingNode):Set<P> {
        validate(n)
        val centers=linkedMapOf<Pair<Int,Int>,Int>()
        val cells=linkedMapOf<Pair<Int,Int>,Pair<Int,Int>>()
        val low=-(n.pathWidth-1)/2;val high=n.pathWidth/2
        for((a,b) in n.coordinates.zipWithNext()) {
            val steps=maxOf(abs(b.x-a.x),abs(b.z-a.z))
            for(i in 0..steps) {
                val t=i.toDouble()/steps
                val x=(a.x+(b.x-a.x)*t).roundToInt();val z=(a.z+(b.z-a.z)*t).roundToInt()
                val y=(a.y+(b.y-a.y)*t).roundToInt()+n.elevation
                val previous=centers.putIfAbsent(x to z,y)
                require(previous==null || previous==y) { "Crossing path sections have incompatible heights" }
            }
        }
        for((center,y) in centers) for(dx in low..high) for(dz in low..high) {
            val key=center.first+dx to center.second+dz;val distance=dx*dx+dz*dz
            val previous=cells[key]
            if(previous==null || distance<previous.first || distance==previous.first && y>previous.second) cells[key]=distance to y
        }
        require(cells.size<=16384) { "Path exceeds 16384 surface cells" }
        return cells.map { (xz,sample) -> P(xz.first,sample.second,xz.second) }.toSet()
    }

    /** Source coordinates remain local, even when a shared area supplies several positioned gardens. */
    fun localFootprint(graph:BuildingGraph,n:BuildingNode):Set<P> = when(n.kind) {
        BuildingKind.GARDEN -> gardenCells(graph,n)
        BuildingKind.PATH -> pathCells(n).map { it.copy(y=0) }.toSet()
        else -> error("Select a garden or path")
    }
    fun placedFootprint(graph:BuildingGraph,n:BuildingNode)=localFootprint(graph,n).map { it+n.landscapeOffset }.toSet()

    /** A reference outline remains available while materials or the exterior placement are incomplete. */
    fun buildingFootprint(graph:BuildingGraph,segment:((String)->FixedSegment)?=null):Set<P> {
        val result=graph.nodes.firstOrNull { it.kind==BuildingKind.BUILDING } ?: return emptySet()
        val storey=graph.nodes.firstOrNull { it.id==result.levels[0] && it.kind==BuildingKind.STOREY } ?: return emptySet()
        if(storey.storeyMode=="walls") return runCatching { RoomGeometry.shell(storey.rectangles) }.getOrDefault(emptySet())
        return storey.inputs.flatMapIndexed { index,id ->
            val room=graph.nodes.firstOrNull { it.id==id && it.kind==BuildingKind.ROOM }
            if(room==null) emptyList() else runCatching {
                val local=if(room.roomConfig?.mode==RoomMode.SEGMENTS) RoomTopology.outline(graph,room,segment).shell else RoomWallJoints.shell(graph.resolvedNode(room))
                local.map { it+(storey.offsets.getOrNull(index) ?: P()) }
            }.getOrDefault(emptyList())
        }.toSet()
    }

    /** The fence occupies the inner perimeter, including concave edges and holes, once per cell. */
    fun fenceCells(area:Set<P>):Set<P> = area.filter { p -> RoomGeometry.directions.any { p+it !in area } }.toSet()
}

/** A shared garden area keeps all room consumers and their wall assignments in sync. */
fun BuildingGraph.editLandscapeCoordinates(id:String,coordinates:List<P>):BuildingGraph {
    val node=nodes.first { it.id==id }
    require(node.kind in LandscapeGeometry.kinds)
    if(node.kind==BuildingKind.GARDEN && node.geometry.isNotEmpty()) return editRoomGeometry(node.geometry,coordinates)
    val changed=replace(node.copy(coordinates=coordinates))
    BuildingGraphCompiler.structure(changed)
    return changed
}

/** Move only this outdoor node; its shape, shared sources and diagram coordinates stay unchanged. */
fun BuildingGraph.moveLandscape(id:String,offset:P):BuildingGraph {
    val node=nodes.first { it.id==id }
    require(node.kind in LandscapeGeometry.kinds) { "Select a garden or path to move" }
    if(node.landscapeOffset==offset) return this
    val changed=replace(node.copy(landscapeOffset=offset))
    BuildingGraphCompiler.structure(changed)
    return changed
}
