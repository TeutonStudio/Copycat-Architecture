package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.*

class BuildingNodePreviewTest {
    private val palette=Building().palette
    private val full=Form(id="full",parts=listOf(Part("copycats:copycat_block")))
    private val panel=Form(id="panel",groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",1)),parts=listOf(
        Part("create:copycat_panel",tilt=3),Part("copycats:copycat_byte_panel",tilt=3,group="accent")))
    private val forms=mapOf("full@1" to full,"panel@1" to panel)
    private fun node(graph:BuildingGraph,id:String)=graph.nodes.single { it.id==id }
    private fun evaluation(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,palette,forms,emptyMap())

    @Test fun `local segment previews preserve sparse geometry rotation clear cells and material ownership`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val source=BuildingAssembly.newNode(BuildingKind.COPYCAT,"panel_source").copy(form="panel@1",role="frame")
        val layout=node(initial,"wall_layout").copy(layoutCells=listOf(SegmentLayoutCell(0,0,0),SegmentLayoutCell(2,1,1,1)))
        val segment=node(initial,"wall").copy(inputs=listOf("wall_copycat",source.id))
        val brokenResult=node(initial,"building").copy(levels=emptyMap(),roof="roof")
        val graph=initial.copy(nodes=initial.nodes+source).replace(layout).replace(segment).replace(brokenResult)
        val json=Gson().toJson(graph)
        val evaluated=evaluation(graph)
        assertFails { evaluated.building() }
        val preview=evaluated.preview(segment.id)
        assertEquals(setOf(P(),P(2,1,0)),preview.placements.map { it.pos }.toSet())
        assertEquals(10,preview.clear.size)
        assertTrue(preview.clear.all { it.z==0 && it.x in 0..2 && it.y in 0..3 })
        assertTrue(preview.placements.all { it.component=="wall" })
        val actual=preview.placements.single { it.pos==P(2,1,0) }
        val expected=Geometry.rotate(Geometry.validate(panel).parts,1)
        assertEquals(expected,actual.parts)
        assertEquals(panel.groups,actual.groups)
        assertEquals(palette.getValue("frame"),actual.material)
        assertEquals(mapOf("body" to palette.getValue("frame"),"accent" to palette.getValue("glass")),actual.surfaces)
        assertEquals(Gson().toJson(graph),json)
        val grouped=evaluation(graph.replace(segment.copy(materialMode=MaterialMode.GROUPED,role="roof"))).preview(segment.id)
        assertTrue(grouped.placements.all { it.material==palette.getValue("roof") && it.surfaces.values.all { material -> material==palette.getValue("roof") } })
        assertEquals(preview.placements.map { it.pos },grouped.placements.map { it.pos })
        assertEquals(preview.clear,grouped.clear)
    }

    @Test fun `pattern previews flatten only their own segments and retain explicit empty cells`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val pattern=BuildingAssembly.newNode(BuildingKind.PATTERN,"preview_pattern").copy(inputs=listOf("wall","empty_wall","wall"))
        val graph=initial.copy(nodes=initial.nodes+pattern)
            .replace(node(initial,"building").copy(levels=emptyMap()))
        val preview=evaluation(graph).preview(pattern.id)
        assertEquals(24,preview.placements.size)
        assertEquals((0..3).map { P(3,it,0) }.toSet(),preview.clear.toSet())
        assertEquals(setOf(0,1,2,4,5,6),preview.placements.map { it.pos.x }.toSet())
        assertTrue(preview.placements.all { it.pos.z==0 && it.component=="wall" })
        val empty=evaluation(graph).preview("empty_wall")
        assertTrue(empty.placements.isEmpty())
        assertEquals((0..3).map { P(0,it,0) }.toSet(),empty.clear.toSet())
        assertEquals(0,empty.raw)
    }

    @Test fun `room previews are local room plans even with repeated storeys and a broken result`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val storey=node(initial,"storey").copy(inputs=listOf("room","room"),offsets=listOf(P(),P(7,0,0)))
        val result=node(initial,"building").copy(levels=mapOf(-1 to "storey",0 to "storey",1 to "storey"),roof="roof",materialMode=MaterialMode.GROUPED,role="glass")
        val graph=initial.replace(storey).replace(result)
        val evaluated=evaluation(graph)
        assertContains(assertFails { evaluated.building() }.message.orEmpty(),"pending definition")
        val room=evaluated.preview("room")
        assertEquals(evaluated.room("room").plan,room)
        assertEquals(114,room.placements.size)
        assertEquals(-1,room.placements.minOf { it.pos.y })
        assertEquals(4,room.placements.maxOf { it.pos.y })
        assertTrue(room.placements.all { it.pos.x in -1..3 && it.pos.z in -1..3 })
        assertEquals(setOf("wall","floor","ceiling"),room.placements.map { it.component }.toSet())
        assertEquals(evaluated.storey("storey").plan,evaluated.preview("storey"))
        assertEquals(228,evaluated.preview("storey").placements.size)
        val source=evaluated.preview("wall_copycat")
        assertEquals(listOf(P()),source.placements.map { it.pos })
        assertEquals(Geometry.validate(full).parts,source.placements.single().parts)
        assertFails { evaluated.preview("building") }
        assertFails { evaluated.preview("room_geometry") }
    }

    @Test fun `legacy roof preview uses the connected top storey outline without compiling its wall and floor recipes`() {
        val initial=BuildingGraph.starter()
        val room=node(initial,"room").copy(id="top_room",rectangles=listOf(Rect(width=2,length=4)),walls=emptyMap())
        val storey=node(initial,"storey").copy(id="top_storey",inputs=listOf(room.id),offsets=listOf(P(-4,0,7)))
        val roof=node(initial,"roof").copy(roofType="flat",overhang=1,thickness=2)
        val result=node(initial,"building").copy(levels=mapOf(0 to "storey",1 to storey.id))
        val graph=initial.copy(nodes=initial.nodes+listOf(room,storey)).replace(roof).replace(result)
        val evaluated=evaluation(graph)
        val preview=evaluated.preview(roof.id)
        assertFalse(evaluated.roofPreviewUsesSample(roof.id))
        assertEquals(96,preview.placements.size)
        assertEquals((-6..-1).toSet(),preview.placements.map { it.pos.x }.toSet())
        assertEquals((5..12).toSet(),preview.placements.map { it.pos.z }.toSet())
        assertEquals(setOf(0,1),preview.placements.map { it.pos.y }.toSet())
        assertTrue(preview.placements.all { it.component=="roof" && it.material==palette.getValue("roof") })
        val compiled=evaluated.building()
        val roofBase=compiled.storeys.last().maximum
        assertEquals(preview.placements,compiled.plan.placements.filter { it.component=="roof" }.map { it.copy(pos=it.pos+P(0,-roofBase,0)) })
        val broken=graph.replace(room.copy(floor="",ceiling=""))
        val brokenEvaluation=evaluation(broken)
        assertFails { brokenEvaluation.building() }
        assertFalse(brokenEvaluation.roofPreviewUsesSample(roof.id))
        assertEquals(preview,brokenEvaluation.preview(roof.id))
    }

    @Test fun `unattached legacy roofs expose a labelled sample while explicit roofs remain pending`() {
        val initial=BuildingGraph.assembledStarter()
        val detached=initial.replace(node(initial,"building").copy(levels=emptyMap(),roof=""))
        val evaluated=evaluation(detached)
        assertFails { evaluated.building() }
        assertTrue(evaluated.roofPreviewUsesSample("roof"))
        val preview=evaluated.preview("roof")
        assertEquals(33,preview.placements.size)
        assertTrue(preview.placements.all { it.component in setOf("roof","gable") && it.pos.y>=0 })
        assertEquals((-1..3).toSet(),preview.placements.map { it.pos.x }.toSet())
        assertEquals((-1..3).toSet(),preview.placements.map { it.pos.z }.toSet())
        val connected=evaluation(initial)
        assertFalse(connected.roofPreviewUsesSample("roof"))
        assertEquals(preview,connected.preview("roof"))
        val incomplete=detached.replace(node(detached,"building").copy(levels=mapOf(0 to "storey"),roof="roof"))
            .replace(node(detached,"storey").copy(inputs=listOf("")))
        assertTrue(evaluation(incomplete).roofPreviewUsesSample("roof"))
        assertEquals(preview,evaluation(incomplete).preview("roof"))
        val explicit=BuildingGraph.explicitStarter(joinWallEnds=false)
        val explicitEvaluation=evaluation(explicit)
        assertFalse(explicitEvaluation.roofPreviewUsesSample("roof"))
        assertContains(assertFails { explicitEvaluation.preview("roof") }.message.orEmpty(),"pending definition")
        assertContains(assertFails { evaluation(explicit.remove("roof_layout")).preview("roof") }.message.orEmpty(),"arrangement source")
    }

    @Test fun `legacy flat and gable compilation retain exact independently constructed placements and costs`() {
        for(type in listOf("flat","gable")) {
            val initial=BuildingGraph.starter()
            val graph=initial.replace(node(initial,"roof").copy(roofType=type))
            val evaluated=evaluation(graph)
            val expected=Resolver()
            val storey=evaluated.storey("storey")
            storey.plan.placements.forEach { expected.add(it.copy(pos=it.pos+P(0,1,0)),true) }
            storey.plan.clear.forEach { expected.clear(it+P(0,1,0)) }
            val roofOnly=Resolver()
            for(z in -1..3) for(x in -1..3) {
                val height=if(type=="flat") 0 else 2-abs(x-1)
                val turn=if(x==1) -1 else if(x<1) 3 else 1
                val parts=if(type=="flat") Geometry.validate(full).parts else listOf(Part(if(turn<0) "copycats:copycat_slab" else "copycats:copycat_stairs",turn.coerceAtLeast(0)))
                roofOnly.add(Placement(P(x,height,z),parts,palette.getValue("roof"),component="roof",surfaces=if(type=="flat") mapOf("body" to palette.getValue("roof")) else emptyMap()))
                if(type=="gable" && z in setOf(-1,3)) for(y in 0 until height)
                    roofOnly.add(Placement(P(x,y,z),Geometry.validate(full).parts,palette.getValue("gable"),component="gable",surfaces=mapOf("body" to palette.getValue("gable"))))
            }
            val local=roofOnly.plan(emptyList())
            local.placements.forEach { expected.add(it.copy(pos=it.pos+P(0,6,0)),true) }
            assertEquals(local,evaluated.preview("roof"))
            assertEquals(CompiledBuilding(expected.plan(emptyList()),listOf(StoreyPosition(0,1,0,6))),evaluated.building())
        }
    }
}
