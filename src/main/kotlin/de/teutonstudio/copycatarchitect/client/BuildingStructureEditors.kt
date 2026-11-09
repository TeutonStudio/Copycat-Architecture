package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import java.util.Locale
import java.util.UUID
import kotlin.math.atan

object BuildingStructureEditors {
    val coreKinds get()=setOf(BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT)
    fun slopeLabel(s:RoofSlope)=ui("roof_slope_label",s.p,s.q,String.format(Locale.ROOT,"%.3f",Math.toDegrees(atan(s.p.toDouble()/s.q))))
    fun storeyLabel(graph:BuildingGraph,id:String,automatic:String="roof_top_storey"):String {
        val entry=graph.storeyInstances().firstOrNull { it.id==id }
            ?: return if(id.isEmpty()) ui(automatic) else "${ui("disconnected")} · $id"
        val level=if(entry.level==0) ui("bg_ground") else ui(if(entry.level>0) "bg_upper" else "bg_lower",kotlin.math.abs(entry.level))
        return "$level · ${graph.nodes.first { it.id==entry.source }.name}"
    }
    fun roofLevel(node:BuildingNode,graph:()->BuildingGraph,commit:(BuildingNode)->Unit) {
        var c=node.roofLevel ?: RoofLevelConfig()
        StructurePropertyScreen.open(tr("roof_level_settings"),{
            choice(ui("roof_target_storey"),c.storey,listOf("")+graph().storeyInstances().map { it.id },{ storeyLabel(graph(),it) }) { c=c.copy(storey=it) }
            choice(ui("roof_form"),c.form,RoofForm.entries.filter { it!=RoofForm.CROSS },{ ui("roof_form_"+it.name.lowercase()) }) { c=if(it==RoofForm.MANSARD && c.form!=RoofForm.MANSARD) c.copy(form=it,slope=RoofSlope(2,1)) else c.copy(form=it) }
            choice(ui("roof_axis"),c.axis,(0..3).toList(),{ listOf("Z / +X","X / +Z","Z / −X","X / −Z")[it] }) { c=c.copy(axis=it) }
            choice(ui("roof_slope"),c.slope,RoofSlope.presets,{ slopeLabel(it) }) { c=c.copy(slope=it) }
            hint(ui("roof_repeat",c.slope.q,c.slope.p))
            choice(ui("roof_mode"),c.mode,RoofMode.entries,{ ui("roof_mode_"+it.name.lowercase()) }) { c=c.copy(mode=it) }
            if(c.mode==RoofMode.HEIGHT) blocks(ui("roof_ridge_height"),c.height16,1..2048) { c=c.copy(height16=it) }
            else blocks(ui("roof_knee_height"),c.knee16,0..2048) { c=c.copy(knee16=it) }
            hint(ui(if(c.mode==RoofMode.HEIGHT) "roof_height_mode_hint" else "roof_angle_mode_hint"))
            blocks(ui("roof_overhang"),c.overhang16,0..128) { c=c.copy(overhang16=it) }
            choice(ui("roof_area"),c.area,listOf("")+graph().nodes.filter { it.kind==BuildingKind.AREA }.map { it.id },{ id -> if(id.isEmpty()) ui("roof_exposed_area") else graph().nodes.first { it.id==id }.name }) { c=c.copy(area=it) }
            val context=graph()
            val layouts=context.nodes.filter { it.kind==BuildingKind.ROOF_LAYOUT && node.id in it.inputs }.map { it.id }.toSet()
            val roofs=context.nodes.filter { it.kind==BuildingKind.ROOF && it.assembly && it.layout in layouts }
            val families=roofs.firstOrNull()?.roofConfig?.families.orEmpty().filter { family ->
                roofs.all { roof -> roof.roofConfig?.families.orEmpty().any { it==family } }
            }
            choice(ui("roof_family"),c.family,listOf("","builtin")+families.map { it.id },{ id -> if(id.isEmpty()) ui("roof_inherit_family") else if(id=="builtin") ui("structure_builtin") else families.firstOrNull { it.id==id }?.name ?: id }) { c=c.copy(family=it) }
            if(c.form==RoofForm.MANSARD) {
                choice(ui("roof_upper_slope"),c.upperSlope,RoofSlope.presets,{ slopeLabel(it) }) { c=c.copy(upperSlope=it) }
                choice(ui("roof_break"),c.breakRatio,listOf(RoofSlope(1,4),RoofSlope(1,3),RoofSlope(1,2),RoofSlope(2,3),RoofSlope(3,4)),{ "${it.p}/${it.q}" }) { c=c.copy(breakRatio=it) }
            }
        }) { commit(node.copy(roofLevel=c)) }
    }
    fun roofTemplates(node:BuildingNode,graph:()->BuildingGraph,commit:(BuildingNode)->Unit) {
        var c=node.roofConfig ?: RoofConfig()
        val sources=graph().nodes.filter { it.kind in CopycatBooleans.sourceKinds }
        val walls=graph().nodes.filter { it.kind in (setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds) }
        StructurePropertyScreen.open(tr("roof_templates"),{
            choice(ui("roof_family"),c.family,listOf("builtin")+c.families.map { it.id },{ id -> if(id=="builtin") ui("structure_builtin") else c.families.firstOrNull { it.id==id }?.name ?: id }) { c=c.copy(family=it) }
            action(ui("roof_add_family")) { family(RoofTemplateFamily(id=UUID.randomUUID().toString(),name=ui("roof_new_family"))) { value -> c=c.copy(families=c.families+value,family=value.id) } }
            c.families.firstOrNull { it.id==c.family }?.let { selected ->
                action(ui("roof_edit_family")) { family(selected) { value -> c=c.copy(families=c.families.map { if(it.id==value.id) value else it }) } }
                action(ui("roof_remove_family")) { c=c.copy(families=c.families.filterNot { it.id==selected.id },family="builtin") }
            }
            for(variant in listOf("slope","eave","edge","ridge","hip","valley")) {
                val current=c.variants[variant].orEmpty()
                choice(ui("roof_variant_$variant"),current,listOf("")+sources.map { it.id },{ id -> if(id.isEmpty()) ui("structure_builtin") else sources.firstOrNull { it.id==id }?.name ?: id }) { value -> c=c.copy(variants=if(value.isEmpty()) c.variants-variant else c.variants+(variant to value)) }
            }
            choice(ui("roof_extension_wall"),c.wall,listOf("")+walls.map { it.id },{ id -> if(id.isEmpty()) ui("structure_builtin") else walls.firstOrNull { it.id==id }?.name ?: id }) { c=c.copy(wall=it) }
            hint(ui("roof_template_period_hint"))
        }) { commit(node.copy(roofConfig=c)) }
    }
    private fun family(initial:RoofTemplateFamily,apply:(RoofTemplateFamily)->Unit) {
        var c=initial;var pitch=c.slopes.firstOrNull() ?: RoofSlope();var variant="slope";var phase=0;var slice=0
        StructurePropertyScreen.open(tr("roof_edit_family"),{
            text(ui("roof_family_name"),c.name,32,{ it.isNotBlank() }) { c=c.copy(name=it) }
            int(ui("roof_skin_thickness"),c.thickness16,1..16) { c=c.copy(thickness16=it) }
            choice(ui("roof_slope"),pitch,RoofSlope.presets,{ slopeLabel(it) }) { pitch=it;phase=0;slice=0 }
            toggle(ui("roof_family_supports"),pitch in c.slopes) { enabled -> c=c.copy(slopes=if(enabled) (c.slopes+pitch).distinct() else (c.slopes-pitch).ifEmpty { c.slopes }) }
            choice(ui("roof_source_variant"),variant,listOf("slope","eave","edge","ridge","hip","valley"),{ ui("roof_variant_$it") }) { variant=it }
            int(ui("roof_source_phase"),phase,0 until pitch.q) { phase=it }
            int(ui("roof_source_slice"),slice,-1..pitch.p) { slice=it }
            val cell=c.cells.firstOrNull { it.slope==pitch && it.variant==variant && it.phase==phase && it.slice==slice }
            val form=ClientState.snapshot.library.forms[cell?.form]
            action(ui("roof_period_cell",form?.name ?: ui("bg_no_connection"))) {
                SavedCopycatSelectionScreen.open(net.minecraft.client.Minecraft.getInstance().screen,cell?.form.orEmpty()) { reference ->
                    c=c.copy(cells=c.cells.filterNot { it.slope==pitch && it.variant==variant && it.phase==phase && it.slice==slice }+RoofTemplateCell(pitch,variant,phase,slice,reference))
                }
            }
            if(cell!=null) action(ui("roof_remove_cell")) { c=c.copy(cells=c.cells-cell) }
            hint(ui("roof_period_count",c.cells.size));hint(ui("roof_template_period_hint"))
        }) { require(c.slopes.isNotEmpty());apply(c) }
    }
    fun core(node:BuildingNode,graph:()->BuildingGraph,commit:(BuildingNode)->Unit) {
        var s=node.stairwell ?: StairwellConfig();var e=node.elevatorShaft ?: ElevatorShaftConfig();var v=node.serviceShaft ?: ServiceShaftConfig()
        fun StructurePropertyScreen.template(key:String,value:String,set:(String)->Unit) {
            val sources=graph().nodes.filter { it.kind in CopycatBooleans.sourceKinds }
            choice(ui(key),value,listOf("")+sources.map { it.id },{ id -> if(id.isEmpty()) ui("structure_builtin") else sources.firstOrNull { it.id==id }?.name ?: id },set)
        }
        StructurePropertyScreen.open(tr("bg_"+node.kind.name.lowercase()),{
            when(node.kind) {
                BuildingKind.STAIRWELL -> {
                    choice(ui("stairs_layout"),s.layout,StairLayout.entries,{ ui("stairs_layout_"+it.name.lowercase()) }) { s=s.copy(layout=it) }
                    int(ui("stairs_width"),s.width,1..8) { s=s.copy(width=it,landingDepth=maxOf(s.landingDepth,it)) }
                    int(ui("stairs_landing_depth"),s.landingDepth,s.width..16) { s=s.copy(landingDepth=it) }
                    int(ui("structure_headroom"),s.headroom,2..8) { s=s.copy(headroom=it) }
                    toggle(ui("stairs_clockwise"),s.clockwise) { s=s.copy(clockwise=it) }
                    toggle(ui("structure_enclosed"),s.enclosed) { s=s.copy(enclosed=it) }
                    template("stairs_tread",s.tread) { s=s.copy(tread=it) };template("stairs_landing",s.landing) { s=s.copy(landing=it) };template("structure_enclosure",s.enclosure) { s=s.copy(enclosure=it) }
                }
                BuildingKind.ELEVATOR_SHAFT -> {
                    int(ui("structure_width"),e.width,1..16) { e=e.copy(width=it) };int(ui("structure_depth"),e.depth,1..16) { e=e.copy(depth=it) }
                    int(ui("structure_headroom"),e.headroom,2..8) { e=e.copy(headroom=it) };toggle(ui("structure_enclosed"),e.enclosed) { e=e.copy(enclosed=it) }
                    template("structure_enclosure",e.enclosure) { e=e.copy(enclosure=it) };hint(ui("elevator_preparation_hint"))
                }
                BuildingKind.SERVICE_SHAFT -> {
                    int(ui("structure_width"),v.width,1..16) { v=v.copy(width=it) };int(ui("structure_depth"),v.depth,1..16) { v=v.copy(depth=it) }
                    toggle(ui("structure_enclosed"),v.enclosed) { v=v.copy(enclosed=it) };template("structure_enclosure",v.enclosure) { v=v.copy(enclosure=it) }
                }
                else -> Unit
            }
        }) { commit(when(node.kind) { BuildingKind.STAIRWELL->node.copy(stairwell=s);BuildingKind.ELEVATOR_SHAFT->node.copy(elevatorShaft=e);else->node.copy(serviceShaft=v) }) }
    }
    fun instances(node:BuildingNode,graph:()->BuildingGraph,commit:(BuildingNode)->Unit) {
        var draft=node.accessInstances;var selected=0
        val sources=graph().nodes.filter { it.kind in coreKinds }
        fun update(change:(VerticalAccessInstance)->VerticalAccessInstance) { draft=draft.mapIndexed { i,value -> if(i==selected) change(value) else value } }
        StructurePropertyScreen.open(tr("structure_instances"),{
            if(draft.isNotEmpty()) { selected=selected.coerceIn(0,draft.lastIndex);choice(ui("structure_instance"),selected,draft.indices.toList(),{ i -> "${i+1}. ${sources.firstOrNull { it.id==draft[i].source }?.name ?: ui("disconnected")}" }) { selected=it } }
            if(draft.size<16 && sources.isNotEmpty()) action(ui("structure_add")) { draft=draft+VerticalAccessInstance(id=UUID.randomUUID().toString(),source=sources.first().id);selected=draft.lastIndex }
            if(draft.isEmpty()) { hint(ui("structure_connect_hint"));return@open }
            val c=draft[selected]
            choice(ui("structure_source"),c.source,listOf("")+sources.map { it.id },{ id -> sources.firstOrNull { it.id==id }?.name ?: ui("disconnected") }) { value -> update { it.copy(source=value) } }
            int("X",c.position.x,-128..128) { value -> update { it.copy(position=it.position.copy(x=value)) } };int("Z",c.position.z,-128..128) { value -> update { it.copy(position=it.position.copy(z=value)) } }
            choice(ui("rotation"),c.quarterTurns,(0..3).toList(),{ "${it*90}°" }) { value -> update { it.copy(quarterTurns=value) } }
            val ids=listOf("")+graph().storeyInstances().map { it.id }
            choice(ui("structure_from"),c.fromStorey,ids,{ storeyLabel(graph(),it,"structure_lowest_storey") }) { value -> update { it.copy(fromStorey=value) } }
            choice(ui("structure_to"),c.toStorey,ids,{ storeyLabel(graph(),it,"structure_highest_storey") }) { value -> update { it.copy(toStorey=value) } }
            if(sources.firstOrNull { it.id==c.source }?.kind==BuildingKind.ELEVATOR_SHAFT) {
                action(ui("structure_all_stops")) { update { it.copy(stops=emptyList()) } }
                graph().storeyInstances().forEach { storey ->
                    val all=graph().storeyInstances().map { it.id }
                    toggle(ui("structure_stop",storeyLabel(graph(),storey.id)),c.stops.isEmpty() || storey.id in c.stops) { enabled -> update {
                        val stops=if(it.stops.isEmpty()) all else it.stops;val next=if(enabled) (stops+storey.id).distinct() else stops-storey.id
                        it.copy(stops=next.ifEmpty { stops })
                    } }
                }
            }
            action(ui("structure_remove")) { draft=draft.filterIndexed { i,_ -> i!=selected };selected=selected.coerceAtMost(draft.lastIndex).coerceAtLeast(0) }
            hint(ui("structure_position_hint"))
        }) { commit(node.copy(accessInstances=draft)) }
    }
}
