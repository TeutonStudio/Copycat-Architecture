package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import java.util.UUID

/** Ordinary property transactions participate in the shared graph history. */
object SpecialRoomEditors {
    private val segments=(setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds)
    private fun StructurePropertyScreen.source(label:String,value:String,graph:BuildingGraph,kinds:Set<BuildingKind>,change:(String)->Unit) {
        choice(ui(label),value,listOf("")+graph.nodes.filter { it.kind in kinds }.map { it.id },{ id ->
            graph.nodes.firstOrNull { it.id==id }?.name?.ifBlank { id } ?: ui("bg_no_connection")
        },change)
    }
    fun definition(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var bay=node.bayWindow ?: BayWindowConfig();var dormer=node.dormer ?: DormerConfig()
        StructurePropertyScreen.open(tr("special_definition_settings"),{
            if(node.kind==BuildingKind.BAY_WINDOW) {
                int(ui("special_inner_width"),bay.width,1..32) { bay=bay.copy(width=it) }
                int(ui("special_inner_depth"),bay.depth,1..32) { bay=bay.copy(depth=it) }
                source("special_wall_source",bay.wall,graph,segments) { bay=bay.copy(wall=it) }
                source("special_front_source",bay.front,graph,segments) { bay=bay.copy(front=it) }
                source("bg_floor",bay.floor,graph,segments+BuildingKind.FLOOR) { bay=bay.copy(floor=it) }
                source("bg_ceiling",bay.ceiling,graph,segments+BuildingKind.CEILING) { bay=bay.copy(ceiling=it) }
                hint(ui("bay_definition_hint"))
            } else {
                int(ui("special_inner_width"),dormer.width,1..32) { dormer=dormer.copy(width=it) }
                int(ui("special_inner_depth"),dormer.depth,1..32) { dormer=dormer.copy(depth=it) }
                int(ui("dormer_wall_height"),dormer.wallHeight,1..16) { dormer=dormer.copy(wallHeight=it) }
                choice(ui("roof_slope"),dormer.slope,RoofSlope.presets,{ BuildingStructureEditors.slopeLabel(it) }) { dormer=dormer.copy(slope=it) }
                source("special_wall_source",dormer.wall,graph,segments) { dormer=dormer.copy(wall=it) }
                source("special_front_source",dormer.front,graph,segments) { dormer=dormer.copy(front=it) }
                source("special_roof_source",dormer.roof,graph,CopycatBooleans.sourceKinds) { dormer=dormer.copy(roof=it) }
                hint(ui("dormer_definition_hint"))
            }
        }) { commit(if(node.kind==BuildingKind.BAY_WINDOW) node.copy(bayWindow=bay) else node.copy(dormer=dormer)) }
    }
    fun instances(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var bays=node.bayWindowInstances;var dormers=node.dormerInstances;var selected=0
        val bay=node.kind==BuildingKind.ROOM
        val definitions=graph.nodes.filter { it.kind==if(bay) BuildingKind.BAY_WINDOW else BuildingKind.DORMER }
        fun updateBay(f:(BayWindowInstance)->BayWindowInstance) { bays=bays.mapIndexed { i,v -> if(i==selected) f(v) else v } }
        fun updateDormer(f:(DormerInstance)->DormerInstance) { dormers=dormers.mapIndexed { i,v -> if(i==selected) f(v) else v } }
        val anchors=if(bay) runCatching { SpecialRooms.anchors(graph,node) }.getOrDefault(emptyList()) else emptyList()
        StructurePropertyScreen.open(tr(if(bay) "bay_instances" else "dormer_instances"),{
            action(ui("special_add_instance")) {
                val id=UUID.randomUUID().toString();val source=definitions.firstOrNull()?.id.orEmpty()
                if(bay) { bays=bays+BayWindowInstance(id,source,anchors.firstOrNull()?.edge.orEmpty());selected=bays.lastIndex }
                else { dormers=dormers+DormerInstance(id,source);selected=dormers.lastIndex }
            }
            val size=if(bay) bays.size else dormers.size
            if(size==0) { hint(ui("special_instance_hint"));return@open }
            selected=selected.coerceIn(0,size-1)
            choice(ui("special_instance"),selected,(0 until size).toList(),{ "${it+1}" }) { selected=it }
            val current=if(bay) bays[selected].source else dormers[selected].source
            source("special_definition",current,graph,setOf(if(bay) BuildingKind.BAY_WINDOW else BuildingKind.DORMER)) { value -> if(bay) updateBay { it.copy(source=value) } else updateDormer { it.copy(source=value) } }
            if(bay) {
                val item=bays[selected]
                choice(ui("bay_target_wall"),item.edge,(listOf(item.edge)+anchors.map { it.edge }).distinct(),{ it.ifEmpty { ui("bg_no_connection") } }) { value -> updateBay { it.copy(edge=value) } }
                int(ui("bay_wall_offset"),item.offset,0..128) { value -> updateBay { it.copy(offset=value) } }
                int(ui("semantic_opening_width"),item.openingWidth,0..32) { value -> updateBay { it.copy(openingWidth=value) } }
                int(ui("semantic_opening_height"),item.openingHeight,0..128) { value -> updateBay { it.copy(openingHeight=value) } }
                hint(ui("bay_opening_auto_hint"))
            } else {
                val item=dormers[selected]
                int("X",item.position.x,-128..128) { value -> updateDormer { it.copy(position=it.position.copy(x=value)) } }
                int("Z",item.position.z,-128..128) { value -> updateDormer { it.copy(position=it.position.copy(z=value)) } }
                choice(ui("dormer_front_direction"),item.direction,(0..3).toList(),{ ui(listOf("north","east","south","west")[it]) }) { value -> updateDormer { it.copy(direction=value) } }
                toggle(ui("dormer_open_ceiling"),item.openCeiling) { value -> updateDormer { it.copy(openCeiling=value) } }
                hint(ui("dormer_definition_hint"))
            }
            action(ui("special_remove_instance")) {
                if(bay) bays=bays.filterIndexed { i,_ -> i!=selected } else dormers=dormers.filterIndexed { i,_ -> i!=selected }
                selected=0
            }
        }) { commit(node.copy(bayWindows=bays,dormers=dormers)) }
    }
}
