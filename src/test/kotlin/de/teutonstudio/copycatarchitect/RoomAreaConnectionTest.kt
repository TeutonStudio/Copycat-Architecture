package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*

class RoomAreaConnectionTest {
    private fun draft(area:BuildingNode):BuildingGraph {
        val base=GraphCards.starter("ROOM")
        val room=BuildingAssembly.newNode(BuildingKind.ROOM,"room").copy(height=5,
            walls=RoomGeometry.edges(listOf(Rect(width=3,length=3))).associate { it.id to "wall" },floor="floor",ceiling="ceiling")
        return base.copy(nodes=base.nodes.map { if(it.resultType!=null) it.copy(inputs=listOf(room.id)) else it }+listOf(area,room,
            BuildingNode("wall",BuildingKind.SEGMENT,assembly=true,module="",inputs=listOf("source"),dynamicAxes=setOf(RotationAxis.X,RotationAxis.Y)),
            BuildingNode("floor",BuildingKind.FLOOR,inputs=listOf("source")),
            BuildingNode("ceiling",BuildingKind.CEILING,inputs=listOf("source")))).withCurrentSchema()
    }
    private fun wire(graph:BuildingGraph,source:String,diagram:Boolean):BuildingGraph {
        if(!diagram) return graph.connect("room","geometry",source)
        val connections=graph.nodes.flatMap { n->BuildingPorts.of(n,graph,true).filter { it.source.isNotEmpty() && !(n.id=="room" && it.key=="geometry") }.map { DiagramConnection(it.source,n.id,it.key) } }
        return DiagramEdits.building(graph,graph.nodes.map { DiagramPosition(it.id,it.x,it.y) },connections+listOfNotNull(source.takeIf(String::isNotEmpty)?.let { DiagramConnection(it,"room","geometry") }))
    }
    private fun preview(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,emptyMap(),emptyMap()).preview("room")

    @Test fun `first room area connection promotes the schema before validating in both editor commands`() {
        val areas=listOf(
            BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.SQUARE,length=4),
            BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.RECTANGLE,length=2,height=4),
            BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.DRAW,coordinates=listOf(P(),P(1,0,0),P(0,0,1))))
        for(area in areas) {
            val before=draft(area);assertEquals(8,before.schema);BuildingGraphCompiler.structure(before)
            val expected=wire(before,area.id,false)
            for(diagram in listOf(false,true)) {
                val connected=wire(before,area.id,diagram)
                assertEquals(11,connected.schema)
                assertEquals(area.id,connected.nodes.first { it.id=="room" }.geometry)
                assertEquals(before.areaOf(area)!!.points.toSet(),RoomGeometry.cells(connected.resolvedNode("room").rectangles))
                assertEquals(5,connected.resolvedNode("room").height)
                assertEquals(preview(expected),preview(connected))
                assertTrue(preview(connected).placements.any { it.component=="wall" })
                val restored=Gson().fromJson(Gson().toJson(connected),BuildingGraph::class.java)
                assertEquals(preview(connected),preview(restored))
            }
            assertEquals("",before.nodes.first { it.id=="room" }.geometry)
            assertEquals(8,before.schema)
        }
    }
    @Test fun `empty drawn areas can connect as incomplete drafts and invalid value types remain rejected`() {
        val before=draft(BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.DRAW))
        for(diagram in listOf(false,true)) {
            val connected=wire(before,"area",diagram)
            assertEquals(11,connected.schema)
            assertContains(assertFails { preview(connected) }.message.orEmpty(),"occupied coordinates")
            assertEquals("",wire(connected,"",diagram).nodes.first { it.id=="room" }.geometry)
            assertFails { wire(before,"source",diagram) }
        }
    }
    @Test fun `moving legacy room geometry graphs does not promote their schema or alter geometry`() {
        val before=draft(BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.SQUARE)).let { graph->
            graph.copy(nodes=graph.nodes.map { if(it.id=="room") it.copy(geometry="legacy") else it }+
                BuildingAssembly.newNode(BuildingKind.ROOM_GEOMETRY,"legacy")).withCurrentSchema()
        }
        assertEquals(8,before.schema)
        val moved=DiagramEdits.building(before,before.nodes.map { DiagramPosition(it.id,it.x+16,it.y) },before.nodes.flatMap { n->
            BuildingPorts.of(n,before).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,n.id,it.key) }
        })
        assertEquals(before.schema,moved.schema)
        assertEquals(preview(before),preview(moved))
    }
}
