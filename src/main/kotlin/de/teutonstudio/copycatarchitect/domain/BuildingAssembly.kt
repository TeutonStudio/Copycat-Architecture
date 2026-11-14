package de.teutonstudio.copycatarchitect.domain

/** Factories for the explicit assembly vocabulary. Legacy node constructors keep their old semantics. */
object BuildingAssembly {
    fun newNode(kind:BuildingKind,id:String,name:String="",x:Int=0,y:Int=0):BuildingNode =
        GraphModels.newBuilding(kind,true).copy(id=id,name=name,x=x,y=y)

    fun starter(joinWallEnds:Boolean=true):BuildingGraph {
        val rectangles=listOf(Rect(width=3,length=3))
        return BuildingGraph(schema=GraphModels.building.defaultSchema,nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",20,20,block="minecraft:air",length=1),
            newNode(BuildingKind.COPYCAT,"wall_copycat","Wall Copycat",20,170),
            newNode(BuildingKind.COPYCAT,"plate_copycat","Plate Copycat",20,390),
            newNode(BuildingKind.SEGMENT_LAYOUT,"wall_layout","Wall arrangement",20,600),
            newNode(BuildingKind.SEGMENT_LAYOUT,"plate_layout","Plate arrangement",20,800).copy(length=if(joinWallEnds) 3 else 5,height=if(joinWallEnds) 3 else 5),
            newNode(BuildingKind.SEGMENT,"wall","Wall segment",310,30).copy(layout="wall_layout",inputs=listOf("wall_copycat")),
            newNode(BuildingKind.SEGMENT,"floor","Floor segment",310,250).copy(layout="plate_layout",inputs=listOf("plate_copycat")),
            newNode(BuildingKind.SEGMENT,"ceiling","Ceiling segment",310,470).copy(layout="plate_layout",inputs=listOf("plate_copycat")),
            newNode(BuildingKind.ROOM_GEOMETRY,"room_geometry","Room geometry",310,690),
            newNode(BuildingKind.ROOM,"room","Room",620,30).copy(joinWallEnds=joinWallEnds,geometry="room_geometry",walls=RoomGeometry.edges(rectangles).associate { it.id to "wall" },floor="floor",ceiling="ceiling"),
            newNode(BuildingKind.STOREY,"storey","Storey",930,30).copy(inputs=listOf("room"),offsets=listOf(P())),
            newNode(BuildingKind.ROOF_LAYOUT,"roof_layout","Roof arrangement",620,630),
            newNode(BuildingKind.ROOF,"roof","Roof",930,470).copy(layout="roof_layout",inputs=emptyList()),
            newNode(BuildingKind.BUILDING,"building","Building",1240,100).copy(levels=mapOf(0 to "storey"))
        ))
    }
}

private fun Collection<P>.orderedCoordinates()=sortedWith(compareBy({ it.z },{ it.x }))

/** Resolve connected sources for display. Incomplete drafts retain their local preview until connected. */
fun BuildingGraph.resolvedNode(node:BuildingNode):BuildingNode {
    var resolved=BuildingCounts.resolve(this,node)
    if(node.kind==BuildingKind.SEGMENT && node.layout.isNotEmpty()) {
        nodes.firstOrNull { it.id==node.layout && it.kind==BuildingKind.SEGMENT_LAYOUT }?.let {
            val geometry=resolvedNode(it)
            resolved=resolved.copy(length=geometry.length,height=geometry.height,depth=geometry.depth)
        }
    }
    if(node.kind in setOf(BuildingKind.SEGMENT_LAYOUT,BuildingKind.ROOM_GEOMETRY)) {
        if(node.kind==BuildingKind.ROOM_GEOMETRY && node.geometry.isNotEmpty() && nodes.any { it.id==node.geometry && it.kind==BuildingKind.AREA && it.coordinates.isEmpty() })
            resolved=resolved.copy(coordinates=emptyList())
        areaOf(node)?.let { area ->
            resolved=if(node.kind==BuildingKind.SEGMENT_LAYOUT) {
                SegmentVolume(area,resolved.depth)
                if(runCatching { BuildingCounts.resolve(this,node,strict=true) }.exceptionOrNull() !is BuildingCounts.IncompleteCount)
                    BuildingCounts.checkDerived(node,resolved,area.width,area.length)
                resolved.copy(length=area.width,height=area.length)
            } else {
                RoomVolume(area,node.height)
                resolved.copy(coordinates=area.points,rectangles=RoomGeometry.fromCells(area.points.toSet()))
            }
        }
    }
    if(node.kind==BuildingKind.ROOM && node.geometry.isNotEmpty()) {
        nodes.firstOrNull { it.id==node.geometry && it.kind==BuildingKind.ROOM_GEOMETRY && (it.coordinates.isNotEmpty() || it.geometry.isNotEmpty()) }?.let {
            val geometry=resolvedNode(it)
            if(geometry.coordinates.isNotEmpty()) resolved=resolved.copy(rectangles=RoomGeometry.fromCells(geometry.coordinates.toSet()),height=geometry.height)
        }
    }
    return resolved
}

fun BuildingGraph.resolvedNode(id:String)=resolvedNode(nodes.first { it.id==id })

/** Rewiring a shared area changes every room consumer while retaining its wall assignments. */
fun BuildingGraph.remapGeometryDependents(beforeGraph:BuildingGraph):BuildingGraph {
    val affected=mutableSetOf<String>()
    val changed=copy(nodes=nodes.map { room ->
        if(room.kind!=BuildingKind.ROOM || room.roomConfig?.mode==RoomMode.SEGMENTS || !room.hasRoomGeometry(this)) return@map room
        val previous=beforeGraph.nodes.firstOrNull { it.id==room.id && it.kind==BuildingKind.ROOM } ?: return@map room
        // Direct room socket edits already reshape their own walls. Shared-source edits keep this reference.
        if(room.geometry!=previous.geometry) return@map room
        val before=beforeGraph.resolvedNode(previous);val after=resolvedNode(room)
        if(RoomGeometry.cells(before.rectangles)==RoomGeometry.cells(after.rectangles)) room
        else {
            affected+=room.id
            val reshaped=RoomGeometry.reshape(before.copy(walls=room.walls,verticalEdges=room.verticalEdges),after.rectangles)
            room.copy(walls=reshaped.walls,verticalEdges=reshaped.verticalEdges)
        }
    })
    BuildingGraphCompiler.structure(changed)
    changed.validateRoomPlacements(affected)
    return changed
}

private fun BuildingNode.hasRoomGeometry(graph:BuildingGraph):Boolean {
    if(roomConfig?.mode==RoomMode.SEGMENTS) return graph.nodes.any { it.id==layout && it.kind==BuildingKind.ROOM_LAYOUT }
    if(!assembly) return true
    val source=graph.nodes.firstOrNull { it.id==geometry && it.kind==BuildingKind.ROOM_GEOMETRY } ?: return false
    return if(source.geometry.isNotEmpty()) graph.areaOf(source)!=null else source.coordinates.isNotEmpty()
}

/** Edit one shared source and preserve directional assignments in every dependent room atomically. */
fun BuildingGraph.editRoomGeometry(id:String,coordinates:List<P>):BuildingGraph {
    val selected=nodes.first { it.id==id }
    val geometrySource=if(selected.kind in setOf(BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY)) selected else {
        require(selected.kind==BuildingKind.ROOM && selected.assembly && selected.geometry.isNotEmpty()) { "Connect a room geometry source before editing its cells" }
        nodes.firstOrNull { it.id==selected.geometry && it.kind==BuildingKind.ROOM_GEOMETRY }
            ?: error("Connect a room geometry source before editing its cells")
    }
    val source=if(geometrySource.geometry.isNotEmpty()) nodes.first { it.id==geometrySource.geometry && it.kind==BuildingKind.AREA } else geometrySource
    val area=Area(coordinates)
    val roomGeometries=nodes.filter { it.kind==BuildingKind.ROOM_GEOMETRY && (it.id==source.id || it.geometry==source.id) }
    roomGeometries.forEach { RoomVolume(area,it.height) }
    val changed=GraphGroups.synchronize(this,copy(nodes=nodes.map { node ->
        if(node.id==source.id) node.copy(coordinates=coordinates.orderedCoordinates()) else node
    }))
    // Definition edits also change the geometry sources in every linked instance. Rebuild and
    // validate all their room consumers before accepting the edit, including external consumers.
    return changed.remapGeometryDependents(this)
}

internal fun BuildingGraph.validateRoomPlacements(affectedRooms:Set<String>) {
    for(storey in nodes.filter { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" && it.inputs.any(affectedRooms::contains) }) {
        val occupied=mutableSetOf<P>()
        storey.inputs.forEachIndexed { index,id ->
            val room=nodes.firstOrNull { it.id==id && it.kind==BuildingKind.ROOM } ?: return@forEachIndexed
            if(!room.hasRoomGeometry(this)) return@forEachIndexed
            val offset=storey.offsets.getOrNull(index) ?: P()
            val local=if(room.roomConfig?.mode==RoomMode.SEGMENTS) runCatching { RoomTopology.outline(this,room).interior }.getOrDefault(emptySet()) else RoomGeometry.cells(resolvedNode(room).rectangles)
            val cells=local.map { it+offset }
            require(cells.none { it in occupied }) { "Room interiors overlap; move them apart before changing their geometry" }
            occupied+=cells
        }
    }
}

fun BuildingGraph.reshapeRoom(id:String,rectangles:List<Rect>):BuildingGraph {
    val node=nodes.first { it.id==id }
    require(node.roomConfig?.mode!=RoomMode.SEGMENTS) { "Edit this room in its segment arrangement instead of changing a legacy rectangle" }
    if(node.kind in setOf(BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY) || node.kind==BuildingKind.ROOM && node.assembly)
        return editRoomGeometry(id,RoomGeometry.cells(rectangles).orderedCoordinates())
    require(node.kind==BuildingKind.ROOM || node.kind==BuildingKind.STOREY && node.storeyMode=="walls") { "Select a room to edit its block footprint" }
    val changed=replace(RoomGeometry.reshape(node,rectangles))
    BuildingGraphCompiler.structure(changed)
    return changed
}
