package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import java.util.UUID

/** Transactional authoring of the information which survives segment composition. */
object SemanticPropertyEditors {
    private val segmentKinds=(setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds)
    private val copycatKinds=CopycatBooleans.sourceKinds
    private fun name(graph:BuildingGraph,id:String)=graph.nodes.firstOrNull { it.id==id }?.let { it.name.ifBlank { ui("bg_"+it.kind.name.lowercase()) } } ?: ui("bg_no_connection")
    private fun StructurePropertyScreen.source(label:String,value:String,graph:BuildingGraph,kinds:Set<BuildingKind>,change:(String)->Unit) {
        choice(ui(label),value,listOf("")+graph.nodes.filter { it.kind in kinds }.map { it.id },{ name(graph,it) },change)
    }
    fun room(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var c=node.roomConfig ?: RoomConfig()
        var layout=node.layout
        StructurePropertyScreen.open(tr("semantic_room_settings"),{
            choice(ui("semantic_room_mode"),c.mode,RoomMode.entries,{ ui("semantic_room_mode_"+it.name.lowercase()) }) { c=c.copy(mode=it) }
            if(c.mode==RoomMode.SEGMENTS) source("bg_room_layout",layout,graph,setOf(BuildingKind.ROOM_LAYOUT)) { layout=it }
            toggle(ui("semantic_height_override"),c.heightOverride!=null) { c=c.copy(heightOverride=if(it) c.heightOverride ?: node.height else null) }
            c.heightOverride?.let { value -> int(ui("room_height"),value,1..128) { c=c.copy(heightOverride=it) } }
            source("semantic_interior_source",c.interiorSource,graph,segmentKinds) { c=c.copy(interiorSource=it) }
            source("semantic_partition_source",c.defaultPartition,graph,segmentKinds) { c=c.copy(defaultPartition=it) }
            hint(ui("semantic_room_help"))
        }) { commit(node.copy(roomConfig=c,layout=layout)) }
    }
    fun facade(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var enabled=node.facade!=null;var c=node.facade ?: FacadeConfig()
        StructurePropertyScreen.open(tr("semantic_facade_settings"),{
            toggle(ui("semantic_facade_enabled"),enabled) { enabled=it }
            if(enabled) {
                source("semantic_facade_source",c.source,graph,segmentKinds) { c=c.copy(source=it) }
                source("semantic_filler_source",c.filler,graph,segmentKinds) { c=c.copy(filler=it) }
                hint(ui("semantic_facade_help"))
            }
        }) { commit(node.copy(facade=if(enabled) c else null)) }
    }
    fun features(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var c=node.features ?: SegmentFeatures();var opening=0;var extension=0
        fun changeOpening(change:(OpeningSpec)->OpeningSpec) { c=c.copy(openings=c.openings.mapIndexed { i,v -> if(i==opening) change(v) else v }) }
        fun changeExtension(change:(ColumnExtensionSpec)->ColumnExtensionSpec) { c=c.copy(extensions=c.extensions.mapIndexed { i,v -> if(i==extension) change(v) else v }) }
        StructurePropertyScreen.open(tr("semantic_segment_settings"),{
            hint(ui("semantic_openings"))
            action(ui("semantic_add_opening")) { c=c.copy(openings=c.openings+OpeningSpec(id=UUID.randomUUID().toString(),size=P(1,2,1)));opening=c.openings.lastIndex }
            if(c.openings.isNotEmpty()) {
                opening=opening.coerceIn(0,c.openings.lastIndex)
                choice(ui("semantic_opening"),opening,c.openings.indices.toList(),{ "${it+1}" }) { opening=it }
                val o=c.openings[opening]
                for(axis in 0..2) int(ui("semantic_opening_position")+" "+listOf("X","Y","Z")[axis],listOf(o.position.x,o.position.y,o.position.z)[axis],-128..128) { value -> changeOpening { it.copy(position=coordinate(it.position,axis,value)) } }
                for(axis in 0..1) int(ui(if(axis==0) "semantic_opening_width" else "semantic_opening_height"),if(axis==0) o.size.x else o.size.y,1..128) { value -> changeOpening { it.copy(size=coordinate(it.size,axis,value)) } }
                source("semantic_opening_mask",o.maskSource,graph,copycatKinds) { value -> changeOpening { it.copy(maskSource=value) } }
                source("semantic_opening_inset",o.insetSource,graph,copycatKinds) { value -> changeOpening { it.copy(insetSource=value) } }
                int(ui("semantic_opening_inset_depth"),o.size.z,1..128) { value -> changeOpening { it.copy(size=it.size.copy(z=value)) } }
                toggle(ui("semantic_retain_source"),o.retainSource) { value -> changeOpening { it.copy(retainSource=value) } }
                hint(ui("semantic_opening_help"))
                action(ui("semantic_remove_opening")) { c=c.copy(openings=c.openings.filterIndexed { i,_ -> i!=opening });opening=0 }
            }
            hint(ui("semantic_columns"))
            action(ui("semantic_add_column")) { c=c.copy(extensions=c.extensions+ColumnExtensionSpec(id=UUID.randomUUID().toString(),shaftMaxY=maxOf(1,node.height)));extension=c.extensions.lastIndex }
            if(c.extensions.isNotEmpty()) {
                extension=extension.coerceIn(0,c.extensions.lastIndex)
                choice(ui("semantic_column"),extension,c.extensions.indices.toList(),{ "${it+1}" }) { extension=it }
                val e=c.extensions[extension]
                int(ui("semantic_shaft_min"),e.shaftMinY,0..127) { value -> changeExtension { it.copy(shaftMinY=value) } }
                int(ui("semantic_shaft_max"),e.shaftMaxY,1..128) { value -> changeExtension { it.copy(shaftMaxY=value) } }
                source("semantic_column_profile",e.profileSource,graph,segmentKinds) { value -> changeExtension { it.copy(profileSource=value) } }
                surface(e.roof,graph,true) { value -> changeExtension { it.copy(roof=value) } }
                blocks(ui("semantic_gap"),e.gap,-128..128) { value -> changeExtension { it.copy(gap=value) } }
                hint(ui("semantic_column_help"))
                action(ui("semantic_remove_column")) { c=c.copy(extensions=c.extensions.filterIndexed { i,_ -> i!=extension });extension=0 }
            }
        }) { commit(node.copy(features=c.takeIf { it.openings.isNotEmpty() || it.extensions.isNotEmpty() })) }
    }
    fun attachments(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var draft=node.attachments.orEmpty();var selected=0
        fun update(change:(AttachmentSpec)->AttachmentSpec) { draft=draft.mapIndexed { i,v -> if(i==selected) change(v) else v } }
        StructurePropertyScreen.open(tr("semantic_attachment_settings"),{
            action(ui("semantic_add_attachment")) { draft=draft+AttachmentSpec(id=UUID.randomUUID().toString());selected=draft.lastIndex }
            if(draft.isEmpty()) { hint(ui("semantic_attachment_help"));return@open }
            selected=selected.coerceIn(0,draft.lastIndex)
            choice(ui("semantic_attachment"),selected,draft.indices.toList(),{ "${it+1}" }) { selected=it }
            val a=draft[selected]
            choice(ui("semantic_attached_edge"),a.edge,(0..3).toList(),{ ui(listOf("north","east","south","west")[it]) }) { value -> update { it.copy(edge=value) } }
            surface(a.target,graph,false) { value -> update { it.copy(target=value) } }
            blocks(ui("semantic_gap"),a.gap,-128..128) { value -> update { it.copy(gap=value) } }
            hint(ui("semantic_attachment_help"))
            action(ui("semantic_remove_attachment")) { draft=draft.filterIndexed { i,_ -> i!=selected };selected=0 }
        }) { commit(node.copy(attachments=draft.takeIf { it.isNotEmpty() })) }
    }
    private fun StructurePropertyScreen.surface(ref:SurfaceRef,graph:BuildingGraph,roof:Boolean,change:(SurfaceRef)->Unit) {
        val kinds=if(roof) setOf(BuildingKind.ROOF_LEVEL) else setOf(BuildingKind.ROOM)
        source(if(roof) "semantic_target_roof" else "semantic_target_wall",ref.node,graph,kinds) { change(ref.copy(node=it,instance="",face=if(roof) "underside" else "")) }
        val instances=if(roof) graph.storeyInstances().map { it.id } else graph.storeyInstances().flatMap { storey ->
            graph.nodes.firstOrNull { it.id==storey.source && it.kind==BuildingKind.STOREY }?.inputs.orEmpty().mapIndexedNotNull { index,source ->
                if(source==ref.node) "${storey.id}/room:$index" else null
            }
        }
        choice(ui("semantic_target_instance"),ref.instance,listOf("")+instances,{ path ->
            if(path.isEmpty()) ui("semantic_target_automatic") else {
                val storey=path.substringBefore("/room:")
                BuildingStructureEditors.storeyLabel(graph,storey)+if("/room:" in path) " · ${ui("bg_room_index",path.substringAfter("/room:").toIntOrNull()?.plus(1) ?: 1)}" else ""
            }
        }) { change(ref.copy(instance=it)) }
        text(ui("semantic_target_path"),ref.instance,256) { change(ref.copy(instance=it)) }
        if(!roof) {
            val room=graph.nodes.firstOrNull { it.id==ref.node && it.kind==BuildingKind.ROOM }
            val legacyFaces=room?.takeIf { it.roomConfig?.mode!=RoomMode.SEGMENTS }?.let { runCatching { RoomWallJoints.runs(graph.resolvedNode(it)).map { r -> r.id } }.getOrDefault(emptyList()) }.orEmpty()
            val directions=listOf("north","east","south","west")
            choice(ui("semantic_target_face"),ref.face,listOf("")+directions+legacyFaces,{ if(it.isEmpty()) ui("semantic_target_automatic") else if(it in directions) ui(it) else it }) { change(ref.copy(face=it)) }
        }
    }
    fun portLabel(key:String):String {
        val ref=key.removePrefix("semantic:")
        val label=when {
            ref.startsWith("bay:") && ref.endsWith(":source") -> "bg_bay_window"
            ref.startsWith("dormer:") && ref.endsWith(":source") -> "bg_dormer"
            ref=="bay:wall" || ref=="dormer:wall" -> "special_wall_source"
            ref=="bay:front" || ref=="dormer:front" -> "special_front_source"
            ref=="bay:floor" -> "bg_floor"
            ref=="bay:ceiling" -> "bg_ceiling"
            ref=="dormer:roof" -> "special_roof_source"
            ref=="room:filler" || ref=="facade:filler" -> "semantic_filler_source"
            ref=="room:interior" -> "semantic_interior_source"
            ref=="room:partition" -> "semantic_partition_source"
            ref=="facade:source" -> "semantic_facade_source"
            ref.startsWith("opening:") && ref.endsWith(":mask") -> "semantic_opening_mask"
            ref.startsWith("opening:") -> "semantic_opening_inset"
            ref.startsWith("column:") -> "semantic_column_profile"
            else -> "semantic_target_wall"
        }
        val number=ref.substringAfter(':',"").substringBefore(':').toIntOrNull()
        return ui(label)+(number?.let { " ${it+1}" } ?: "")
    }
    private fun coordinate(p:P,axis:Int,value:Int)=when(axis) { 0->p.copy(x=value);1->p.copy(y=value);else->p.copy(z=value) }
}
