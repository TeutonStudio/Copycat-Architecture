package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildingNodePreviewGeometryTest {
    private fun occupied(boxes:List<Box>)=buildSet {
        for(b in boxes) for(x in b.x until b.maxX) for(y in b.y until b.maxY) for(z in b.z until b.maxZ) add(P(x,y,z))
    }

    @Test fun `area preview retains negative coordinates and gaps outside room bounds`() {
        val cells=listOf(P(-128,0,120),P(-127,0,120),P(-128,0,121),P(127,0,-128))
        val area=BuildingNode(id="area",kind=BuildingKind.AREA,coordinates=cells)
        val boxes=BuildingNodePreviewGeometry.boxes(BuildingGraph(nodes=listOf(area)),area)
        assertEquals(cells.toSet(),occupied(boxes))
        assertEquals(3,boxes.size)
    }

    @Test fun `shared room geometry extrudes the source footprint through the selected height`() {
        val cells=listOf(P(-2,0,4),P(-1,0,4),P(-2,0,5))
        val area=BuildingNode(id="area",kind=BuildingKind.AREA,coordinates=cells)
        val room=BuildingNode(id="room",kind=BuildingKind.ROOM_GEOMETRY,geometry=area.id,height=5)
        val graph=BuildingGraph(nodes=listOf(area,room))
        assertEquals(cells.flatMap { p -> (0..4).map { p+P(0,it,0) } }.toSet(),occupied(BuildingNodePreviewGeometry.boxes(graph,room)))
        assertTrue(BuildingNodePreviewGeometry.boxes(graph.replace(area.copy(coordinates=emptyList())),room).isEmpty())
    }

    @Test fun `segment arrangement maps the shared raster onto XY and clips assignments to its outline`() {
        val area=BuildingNode(id="area",kind=BuildingKind.AREA,coordinates=listOf(P(-3,0,4),P(-2,0,4),P(-3,0,5)))
        val layout=BuildingNode(id="layout",kind=BuildingKind.SEGMENT_LAYOUT,geometry=area.id,depth=2,
            layoutCells=listOf(SegmentLayoutCell(0,0),SegmentLayoutCell(1,1)))
        val graph=BuildingGraph(nodes=listOf(area,layout))
        assertEquals(setOf(P(),P(0,0,1),P(1,1,0),P(1,1,1)),occupied(BuildingNodePreviewGeometry.boxes(graph,layout)))
        val full=layout.copy(layoutCells=emptyList())
        assertEquals(setOf(P(),P(0,1,0),P(1,1,0),P(0,0,1),P(0,1,1),P(1,1,1)),occupied(BuildingNodePreviewGeometry.boxes(graph,full)))
    }

    @Test fun `rectangular arrangements merge into a single volume`() {
        val layout=BuildingNode(id="layout",kind=BuildingKind.SEGMENT_LAYOUT,length=5,height=4,depth=3)
        assertEquals(listOf(Box(0,0,0,5,4,3)),BuildingNodePreviewGeometry.boxes(BuildingGraph(),layout))
    }
}
