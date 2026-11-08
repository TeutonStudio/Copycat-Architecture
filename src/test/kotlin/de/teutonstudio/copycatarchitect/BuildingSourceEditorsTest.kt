package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.BuildingSourceEditors
import de.teutonstudio.copycatarchitect.domain.P
import de.teutonstudio.copycatarchitect.domain.SegmentLayoutCell
import de.teutonstudio.copycatarchitect.domain.Building
import de.teutonstudio.copycatarchitect.domain.BuildingGraph
import de.teutonstudio.copycatarchitect.domain.BuildingKind
import de.teutonstudio.copycatarchitect.domain.BuildingNode
import de.teutonstudio.copycatarchitect.domain.Form
import de.teutonstudio.copycatarchitect.domain.MaterialGroup
import de.teutonstudio.copycatarchitect.domain.Part
import de.teutonstudio.copycatarchitect.domain.BuildingGraphCompiler
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import kotlin.test.*

/** The editor's text inputs are pure parsers and must fail before any document is committed. */
class BuildingSourceEditorsTest {
    @Test fun `local source materials survive segment assembly and mirrors while grouped overrides still win`() {
        val original=BuildingGraph.assembledStarter()
        val source=original.nodes.single { it.id=="wall_copycat" }.copy(surfaces=mapOf("body" to "minecraft:copper_block"))
        val graph=original.replace(source).copy(nodes=original.replace(source).nodes+listOf(
            BuildingNode("copy_mirror",BuildingKind.COPYCAT_MIRROR,inputs=listOf(source.id)),
            BuildingNode("segment_mirror",BuildingKind.SEGMENT_MIRROR,inputs=listOf("wall"))))
        val form=Form(id="full",parts=listOf(Part("copycats:copycat_block")))
        fun evaluation(value:BuildingGraph)=BuildingGraphCompiler.Evaluation(value,Building().palette,mapOf("full@1" to form),emptyMap())
        val eval=evaluation(graph)
        for(id in listOf(source.id,"copy_mirror","wall","segment_mirror")) {
            val plan=eval.preview(id)
            assertTrue(plan.placements.isNotEmpty())
            assertTrue(plan.placements.all { it.surfaces["body"]=="minecraft:copper_block" },id)
            assertEquals(plan.placements.size,plan.materials["minecraft:copper_block"])
        }
        val wall=graph.nodes.single { it.id=="wall" }
        val grouped=evaluation(graph.replace(wall.copy(materialMode=de.teutonstudio.copycatarchitect.domain.MaterialMode.GROUPED,role="frame"))).preview("wall")
        assertTrue(grouped.placements.all { it.surfaces["body"]==Building().palette.getValue("frame") })
        val legacy=Json.decode<BuildingNode>("{\"id\":\"legacy\",\"kind\":\"COPYCAT\"}")
        assertTrue(legacy.surfaces.isNullOrEmpty())
        assertTrue(Json.decode<de.teutonstudio.copycatarchitect.domain.Cell>("{}").surfaces.isNullOrEmpty())
        assertEquals(graph,Json.decode<BuildingGraph>(Json.encode(graph)))
    }
    @Test fun `air is a selectable source and returning to a form preserves unrelated source settings`() {
        val form=Form(id="fixture")
        val forms=mapOf("fixture@1" to form)
        val original=BuildingNode("source",BuildingKind.COPYCAT,name="Door opening",form="fixture@1",role="glass",x=210,y=430)
        val air=BuildingSourceEditors.copycatSource(original,BuildingSourceEditors.AIR_SOURCE,forms)
        assertEquals(original.copy(block="minecraft:air"),air)
        assertEquals("minecraft:air",BuildingSourceEditors.copycatSource(air))
        assertEquals(original,BuildingSourceEditors.copycatSource(air,"fixture@1",forms))
        assertEquals("fixture@1",BuildingSourceEditors.copycatSource(original))
        assertFails { BuildingSourceEditors.copycatSource(original,"missing@1",forms) }
        assertFails { BuildingSourceEditors.copycatSource(original.copy(kind=BuildingKind.ROOM),"fixture@1",forms) }
    }

    @Test fun `source material edits update the shared role without modifying geometry or other assignments`() {
        val graph=BuildingGraph.assembledStarter()
        val original=Building(schema=2,graph=graph)
        val edited=BuildingSourceEditors.material(original,"wall","minecraft:bricks")
        assertEquals(original.copy(palette=original.palette+("wall" to "minecraft:bricks")),edited)
        assertSame(graph,edited.graph)
        assertEquals(original.palette.filterKeys { it!="wall" },edited.palette.filterKeys { it!="wall" })
        assertEquals(edited,BuildingSourceEditors.material(edited,"wall","minecraft:bricks"))
        assertFails { BuildingSourceEditors.material(original,"missing","minecraft:bricks") }
        assertFails { BuildingSourceEditors.material(original,"wall","minecraft:air") }
        assertFails { BuildingSourceEditors.material(original,"wall","not a block") }
    }

    @Test fun `copycat group selections remain local to the selected source and persist`() {
        val form=Form(id="material_fixture",groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","trim",1),MaterialGroup("alias","Other surface","surface",2)),
            parts=listOf(Part("copycats:copycat_block"),Part("copycats:copycat_slab",top=true,group="cap"),Part("copycats:copycat_byte",group="alias")))
        val source=BuildingNode("source",BuildingKind.COPYCAT,form="material_fixture@1",role="wall")
        val peer=source.copy(id="peer")
        val base=BuildingGraph.assembledStarter()
        val graph=base.copy(nodes=base.nodes+listOf(source,peer))
        val original=Building(schema=2,graph=graph)
        val selected=mapOf("body" to "minecraft:copper_block","cap" to "minecraft:glass","alias" to "minecraft:gold_block")
        assertEquals(mapOf("body" to "wall","cap" to "trim","alias" to "wall"),BuildingSourceEditors.copycatMaterialRoles(source,form.groups))
        val edited=BuildingSourceEditors.copycatMaterials(original,source,form.groups,selected)
        assertEquals(original.palette,edited.palette)
        assertEquals(selected,edited.graph!!.nodes.single { it.id==source.id }.surfaces)
        assertEquals(peer,edited.graph!!.nodes.single { it.id==peer.id })
        val evaluation=BuildingGraphCompiler.Evaluation(edited.graph!!,edited.palette,mapOf("material_fixture@1" to form),emptyMap())
        assertEquals(selected,evaluation.preview("source").placements.single().surfaces)
        assertEquals(mapOf("body" to original.palette.getValue("wall"),"cap" to original.palette.getValue("trim"),"alias" to original.palette.getValue("wall")),evaluation.preview("peer").placements.single().surfaces)
        assertEquals(edited,Json.decode<Building>(Json.encode(edited)))
        assertEquals(edited,BuildingSourceEditors.copycatMaterials(edited,source,form.groups,selected))
        val reset=edited.copy(graph=edited.graph!!.replace(edited.graph!!.nodes.single { it.id==source.id }.copy(surfaces=null)))
        assertEquals(original,reset)
    }

    @Test fun `copycat material selections allow independent groups with shared roles and reject invalid assignments`() {
        val groups=listOf(MaterialGroup(),MaterialGroup("other","Other","surface",1))
        val source=BuildingNode("source",BuildingKind.COPYCAT)
        val building=Building(schema=2,graph=BuildingGraph(nodes=listOf(source)))
        assertEquals(mapOf("body" to "minecraft:stone","other" to "minecraft:glass"),BuildingSourceEditors.copycatMaterials(building,source,groups,mapOf("body" to "minecraft:stone","other" to "minecraft:glass")).graph!!.nodes.single().surfaces)
        assertFails { BuildingSourceEditors.copycatMaterials(building,source,groups,mapOf("body" to "minecraft:stone")) }
        assertFails { BuildingSourceEditors.copycatMaterials(building,source,groups,mapOf("body" to "minecraft:air","other" to "minecraft:air")) }
        assertFails { BuildingSourceEditors.copycatMaterialRoles(source.copy(kind=BuildingKind.ROOM),groups) }
    }

    @Test fun `room coordinate lists preserve negative integer cells and x z axes`() {
        val expected=setOf(P(-2,0,1),P(-1,0,1),P(-2,0,2))
        val parsed=BuildingSourceEditors.parseCoordinates(" -2, 1 ; -1,1; -2,2 ")
        assertEquals(expected,parsed.toSet())
        assertEquals(expected.size,parsed.size)
        assertTrue(parsed.all { it.y==0 })
        assertEquals(parsed,BuildingSourceEditors.parseCoordinates(BuildingSourceEditors.coordinateText(parsed)))
    }

    @Test fun `room coordinate lists reject incomplete fractional and overflowing integers`() {
        for(text in listOf("0","0,0,0","0,","0.5,0","1e0,0","NaN,0","2147483648,0","0,0;")) {
            assertFails("Malformed coordinate list must be rejected: $text") { BuildingSourceEditors.parseCoordinates(text) }
        }
    }

    @Test fun `room coordinate lists reject duplicate disconnected and enclosed cells`() {
        for(text in listOf("","  ","0,0;0,0","0,0; 0, 0","0,0;2,0","0,0;1,1",
            "0,0;1,0;2,0;0,1;2,1;0,2;1,2;2,2")) {
            assertFails("Room source must remain nonempty connected and without holes: $text") { BuildingSourceEditors.parseCoordinates(text) }
        }
    }

    @Test fun `room coordinate lists retain supported grid edges and reject coordinates beyond them`() {
        assertEquals(setOf(P(-32,0,-32),P(-31,0,-32)),BuildingSourceEditors.parseCoordinates("-32,-32;-31,-32").toSet())
        assertEquals(setOf(P(62,0,63),P(63,0,63)),BuildingSourceEditors.parseCoordinates("62,63;63,63").toSet())
        for(text in listOf("-33,0","64,0","0,-33","0,64")) {
            assertFails("Out-of-grid coordinate must be rejected: $text") { BuildingSourceEditors.parseCoordinates(text) }
        }
    }

    @Test fun `segment arrangements translate one based sources and degree angles once`() {
        val parsed=BuildingSourceEditors.parseLayout(" 0,0,1 ; 1,0,16,90 ; 2,1,2,180 ; 127,127,3,270 ")
        assertEquals(listOf(SegmentLayoutCell(0,0,0,0),SegmentLayoutCell(1,0,15,1),
            SegmentLayoutCell(2,1,1,2),SegmentLayoutCell(127,127,2,3)),parsed)
        assertEquals(parsed,BuildingSourceEditors.parseLayout(BuildingSourceEditors.layoutText(parsed)))
    }

    @Test fun `empty segment arrangements retain automatic placement`() {
        assertEquals(emptyList(),BuildingSourceEditors.parseLayout(""))
        assertEquals(emptyList(),BuildingSourceEditors.parseLayout(" \n\t "))
        assertEquals("",BuildingSourceEditors.layoutText(emptyList()))
    }

    @Test fun `segment arrangements reject invalid source numbers angles axes and syntax`() {
        for(text in listOf("0,0","0,0,1,0,0","0,0,0","0,0,17","0,0,1,-90","0,0,1,45","0,0,1,360",
            "-1,0,1","0,-1,1","128,0,1","0,128,1","0.5,0,1","0,0,1.0","0,0,1,90.0","2147483648,0,1","0,0,1;")) {
            assertFails("Invalid segment arrangement cell must be rejected: $text") { BuildingSourceEditors.parseLayout(text) }
        }
    }

    @Test fun `segment arrangements reject duplicate positions regardless of source or turn`() {
        assertFails { BuildingSourceEditors.parseLayout("2,3,1;2,3,1") }
        assertFails { BuildingSourceEditors.parseLayout("2,3,1,90;2,3,16,270") }
        assertEquals(listOf(SegmentLayoutCell(2,3,0),SegmentLayoutCell(3,2,0)),BuildingSourceEditors.parseLayout("2,3,1;3,2,1"))
    }
}
