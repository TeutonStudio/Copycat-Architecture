package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingAttachmentsTest {
    @Test fun `legacy mounting preserves every source cell in joined and unjoined wall transforms`() {
        for(joined in listOf(false,true)) {
            val base=BuildingGraph.explicitStarter(joined)
            val room=base.resolvedNode(base.nodes.first { it.id=="room" })
            val spec=ColumnExtensionSpec("column",0,1,roof=SurfaceRef("roof_plane","storey_0"))
            val source=BuildingSemantics.annotate(FixedSegment(3,4,listOf(SegmentCell(Cell(P(1,2,1)))),depth=2),"column",SegmentFeatures(extensions=listOf(spec)))
            val changed=room.copy(walls=RoomWallJoints.runs(room).associate { it.id to "column" })
            val storey=BuildingNode("direct",BuildingKind.STOREY,storeyMode="walls",rectangles=changed.rectangles,walls=changed.walls)
            val helper=BuildingAttachments(BuildingGraph(nodes=base.nodes+storey),{ source })
            val offset=P(11,5,-7)
            val placeholder=CompiledStorey(Plan(emptyList(),emptyList(),emptyList(),0,emptyMap()),emptySet(),0,4,0,0)
            // Direct-wall stockworks use the legacy unjoined mounting convention.
            helper.collect(storey,placeholder,offset,"storey_0") { error("No room callback expected") }
            assertEquals(RoomGeometry.edges(changed.rectangles).size,helper.columns.size)
            for((run,column) in RoomGeometry.edges(changed.rectangles).zip(helper.columns)) {
                val normal=RoomGeometry.directions[run.direction]
                val expected=run.cells[1]+P(normal.x*(source.depth-1-1),2,normal.z*(source.depth-1-1))+offset
                assertEquals(expected,column.instance.transform.cell(P(1,2,1)))
                assertEquals("storey_0/wall:${run.id}/column",column.instance.path)
            }
        }
    }

    @Test fun `direct wall ceiling attachments and legacy run surface references are collected`() {
        val footprint=listOf(Rect(width=3,length=3));val runs=RoomGeometry.edges(footprint)
        val ceiling=BuildingNode("ceiling",BuildingKind.CEILING,attachments=listOf(AttachmentSpec("awning",1,SurfaceRef("hall"))))
        val storey=BuildingNode("direct",BuildingKind.STOREY,storeyMode="walls",rectangles=footprint,walls=runs.associate { it.id to "wall" },ceiling=ceiling.id)
        val source=FixedSegment(3,2,listOf(SegmentCell(Cell())))
        val wall=Placement(runs.first().cells.first(),listOf(Part("copycats:copycat_block")),component="wall")
        val roof=Placement(P(0,2,0),listOf(Part("copycats:copycat_block")),component="ceiling")
        val plan=Resolver().apply { add(wall);add(roof) }.plan(emptyList())
        val helper=BuildingAttachments(BuildingGraph(nodes=listOf(storey,ceiling)),{ source })
        val offset=P(5,7,9)
        helper.collect(storey,CompiledStorey(plan,RoomGeometry.cells(footprint),0,3,0,1),offset,"storey_0") { error("No room callback expected") }
        assertEquals(roof.pos+offset,helper.plates.single().placements.single().pos)
        assertTrue(helper.walls.any { it.reference==SurfaceRef("direct","storey_0",runs.first().id) && it.placements.single().pos==wall.pos+offset })
        assertTrue(helper.walls.any { it.reference==SurfaceRef("wall","storey_0/wall:${runs.first().id}") })
        assertTrue(helper.walls.any { it.reference==SurfaceRef("direct","storey_0","north") })
    }

    @Test fun `building material override precedes stockwork material for attached columns`() {
        val footprint=listOf(Rect(width=3,length=3));val runs=RoomGeometry.edges(footprint)
        val storey=BuildingNode("direct",BuildingKind.STOREY,storeyMode="walls",rectangles=footprint,walls=runs.associate { it.id to "column" },materialMode=MaterialMode.GROUPED,role="frame")
        val result=BuildingNode("building",BuildingKind.BUILDING,materialMode=MaterialMode.GROUPED,role="roof")
        val source=BuildingSemantics.annotate(FixedSegment(3,4,listOf(SegmentCell(Cell()))),"column",SegmentFeatures(extensions=listOf(ColumnExtensionSpec("column"))))
        val palette=mapOf("frame" to "minecraft:oak_planks","roof" to "minecraft:deepslate_tiles")
        val helper=BuildingAttachments(BuildingGraph(nodes=listOf(storey,result)),{ source },palette)
        helper.collect(storey,CompiledStorey(Plan(emptyList(),emptyList(),emptyList(),0,emptyMap()),emptySet(),0,4,0,0),P(),"storey_0") { error("No room callback expected") }
        assertTrue(helper.columns.isNotEmpty())
        assertTrue(helper.columns.all { it.materialOverride==palette.getValue("roof") })
    }

    @Test fun `opening mask ownership uses the placed source path and follows wall transforms`() {
        val footprint=listOf(Rect(width=3,length=3));val runs=RoomGeometry.edges(footprint)
        val storey=BuildingNode("direct",BuildingKind.STOREY,storeyMode="walls",rectangles=footprint,walls=runs.associate { it.id to "column" })
        val features=SegmentFeatures(openings=listOf(OpeningSpec("window",P(1,1,0),P(1,1,1))),extensions=listOf(ColumnExtensionSpec("column")))
        val source=BuildingSemantics.annotate(FixedSegment(3,3,listOf(SegmentCell(Cell()))),"column",features)
        val helper=BuildingAttachments(BuildingGraph(nodes=listOf(storey)),{ source })
        val offset=P(10,8,-5)
        helper.collect(storey,CompiledStorey(Plan(emptyList(),emptyList(),emptyList(),0,emptyMap()),emptySet(),0,3,0,0),offset,"storey_0") { error("No room callback expected") }
        val column=helper.columns.first();val position=column.instance.transform.cell(P(1,1,0))
        assertFalse(helper.openingMask("another/source",position).isEmpty())
        assertTrue(helper.openingMask(column.instance.path,position).isEmpty())
        assertTrue(helper.openingMask("another/source",position+P(0,4,0)).isEmpty())
        assertTrue(helper.openingMask("interior/column",position+P(0,0,2)).isEmpty(),"Window masks must not cut an interior column behind the physical wall")
    }
}
