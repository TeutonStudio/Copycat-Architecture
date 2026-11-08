package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Command
import de.teutonstudio.copycatarchitect.server.CommandTransport
import de.teutonstudio.copycatarchitect.server.Json

/** Integer lists are drafts until the complete list and its dependent geometry are valid. */
object BuildingSourceEditors {
    const val AIR_SOURCE="minecraft:air"
    fun copycatSource(node:BuildingNode)=node.block.ifEmpty { node.form }
    fun copycatSource(node:BuildingNode,source:String,forms:Map<String,Form>):BuildingNode {
        require(node.kind==BuildingKind.COPYCAT) { "Only a Copycat has a selectable form source" }
        require(source==AIR_SOURCE || source in forms) { "Unknown Copycat source $source" }
        return if(source==AIR_SOURCE) node.copy(block=AIR_SOURCE) else node.copy(form=source,block="")
    }
    /** A source selects a role; the building palette owns the shared material assignment. */
    fun material(building:Building,role:String,block:String):Building {
        require(role in building.palette) { "Missing material role $role" }
        require(block.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) && block!=AIR_SOURCE) { "Invalid surface material $block" }
        return building.copy(palette=building.palette+(role to block))
    }
    /** Use the compiler's role contract, including aliases shared by several Copycat groups. */
    fun copycatMaterialRoles(node:BuildingNode,groups:List<MaterialGroup>):Map<String,String> {
        require(node.kind==BuildingKind.COPYCAT) { "Only Copycat sources have material groups" }
        return groups.associate { it.id to if(it.role=="surface") node.role else it.role }
    }
    fun copycatMaterials(building:Building,node:BuildingNode,groups:List<MaterialGroup>,surfaces:Map<String,String>):Building {
        val roles=copycatMaterialRoles(node,groups)
        require(surfaces.keys==roles.keys) { "Assign every Copycat material group" }
        require(surfaces.values.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) && it!=AIR_SOURCE }) { "Invalid surface material" }
        val graph=requireNotNull(building.graph)
        val current=graph.nodes.single { it.id==node.id && it.kind==BuildingKind.COPYCAT }
        return building.copy(graph=graph.replace(current.copy(surfaces=surfaces.toMap())))
    }
    fun coordinateText(cells:List<P>)=cells.joinToString("; ") { "${it.x},${it.z}" }
    fun pathText(points:List<P>)=points.joinToString("; ") { "${it.x},${it.y},${it.z}" }
    fun parsePath(text:String):List<P> = entries(text).map { entry ->
        val values=integers(entry);require(values.size==3) { "Use x,y,z for each waypoint" }
        P(values[0],values[1],values[2])
    }.also { LandscapeGeometry.validate(BuildingAssembly.newNode(BuildingKind.PATH,"path").copy(coordinates=it)) }
    fun parseCoordinates(text:String):List<P> {
        val cells=coordinateEntries(text)
        require(cells.size==cells.distinct().size) { "Room cells must be unique" }
        RoomFootprintEditor.validate(cells.toSet())
        return cells
    }
    fun parseAreaCoordinates(text:String):List<P> = coordinateEntries(text).also { Area(it) }
    private fun coordinateEntries(text:String)=entries(text).map { entry ->
        val values=integers(entry);require(values.size==2) { "Use x,z for each raster point" }
        P(values[0],0,values[1])
    }
    fun layoutText(cells:List<SegmentLayoutCell>)=cells.joinToString("; ") { "${it.x},${it.y},${it.sourceIndex+1},${it.turn*90}" }
    fun parseLayout(text:String):List<SegmentLayoutCell> {
        if(text.isBlank()) return emptyList()
        val cells=entries(text).map { entry ->
            val values=integers(entry);require(values.size in 3..4) { "Use x,y,Copycat number,rotation" }
            val turn=values.getOrNull(3) ?: 0
            require(values[0] in 0..127 && values[1] in 0..127 && values[2] in 1..16 && turn in setOf(0,90,180,270)) { "Invalid segment cell" }
            SegmentLayoutCell(values[0],values[1],values[2]-1,turn/90)
        }
        require(cells.size<=16384 && cells.map { it.x to it.y }.distinct().size==cells.size) { "Segment cells must be unique" }
        return cells
    }
    private fun entries(text:String):List<String> {
        val entries=text.trim().split(Regex("[;\\n]"))
        require(entries.isNotEmpty() && entries.none { it.isBlank() }) { "Enter a complete coordinate list" }
        return entries
    }
    private fun integers(entry:String):List<Int> {
        val text=entry.trim().removeSurrounding("(",")")
        return text.split(',').map { it.trim().toIntOrNull() ?: error("Coordinates must be integers") }
    }
    /** Reserve the largest build-command envelope so Save and Create job share a checked limit. */
    fun validateBuilding(building:Building) {
        val payload=Json.encode(building)
        val far=P(Int.MIN_VALUE,Int.MIN_VALUE,Int.MIN_VALUE)
        CommandTransport.encode(Json.encode(Command("createJob",payload,turn=3,origin=far,dock=far,computer=1_000_000)))
    }
    fun coordinates(node:BuildingNode,graph:()->BuildingGraph,commit:(BuildingGraph)->Unit,validate:(BuildingGraph)->Unit={}) {
        val landscape=node.kind in LandscapeGeometry.kinds
        fun updated(value:String):BuildingGraph {
            val cells=when(node.kind) { BuildingKind.PATH->parsePath(value);BuildingKind.GARDEN,BuildingKind.AREA->parseAreaCoordinates(value);else->parseCoordinates(value) }
            return (if(landscape) graph().editLandscapeCoordinates(node.id,cells) else graph().editRoomGeometry(node.id,cells)).also(validate)
        }
        val cells=if(node.kind==BuildingKind.GARDEN) runCatching { LandscapeGeometry.gardenCells(graph(),node).toList() }.getOrDefault(node.coordinates) else graph().resolvedNode(node).coordinates
        EditorValueInput.text(tr(if(node.kind==BuildingKind.PATH) "bg_path_coordinates" else "bg_coordinate_list"),if(node.kind==BuildingKind.PATH) pathText(cells) else coordinateText(cells),if(node.kind==BuildingKind.AREA || node.kind==BuildingKind.GARDEN) CommandTransport.MAX_JSON_CHARS else 64000,{ value ->
            runCatching { updated(value) }.isSuccess
        }) { value -> commit(updated(value)) }
    }
    fun layout(node:BuildingNode,graph:()->BuildingGraph,commit:(BuildingGraph)->Unit,validate:(BuildingGraph)->Unit={}) {
        fun updated(value:String):BuildingGraph {
            val cells=parseLayout(value)
            require(cells.all { it.x<graph().resolvedNode(node).length && it.y<graph().resolvedNode(node).height }) { "A segment cell lies outside the arrangement" }
            return graph().replace(node.copy(layoutCells=cells)).also(BuildingGraphCompiler::structure).also(validate)
        }
        EditorValueInput.text(tr("bg_layout_cell_list"),layoutText(node.layoutCells),64000,{ runCatching { updated(it) }.isSuccess }) { commit(updated(it)) }
    }
}
