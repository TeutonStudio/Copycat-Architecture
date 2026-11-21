package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class WallLayerThicknessTest {
    private val full=Part("copycats:copycat_block",recipeSource="wall_source")
    private fun wall(length:Int=5,depth:Int=1,material:String="inside")=FixedSegment(length,1,
        (0 until length).flatMap { x -> (0 until depth).map { z -> SegmentCell(Cell(P(x,0,z),role=material)) } },depth=depth)
    private fun placed(p:P,cells:List<SegmentCell>,turn:Int,type:String)=Placement(p,parts=listOf(full),material=cells.first().cell.role,component=type)
    private fun layout()=BuildingNode("layout",BuildingKind.ROOM_LAYOUT,inputs=listOf("wall"),roomLayout=RoomLayoutConfig(listOf(
        RoomSegmentInstance("n",position=P()),RoomSegmentInstance("e",position=P(4,0,0),quarterTurns=1),
        RoomSegmentInstance("s",position=P(0,0,4),quarterTurns=2),RoomSegmentInstance("w",position=P(),quarterTurns=3))))
    private fun room(thickness:Map<String,Int>?=null)=RoomTopology.compile(
        BuildingNode("room",BuildingKind.ROOM,roomConfig=RoomConfig(RoomMode.SEGMENTS,wallThickness=thickness)),layout(),{wall()},::placed)
    private fun storey(thickness:Map<String,Int>?=null,facade:Int?=null,rooms:List<SemanticRoomInstance>?=null)=SemanticStoreys.compile(
        BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body",thickness=facade)),
        rooms ?: listOf(SemanticRoomInstance(room(thickness),id="room")),{wall(1,material="outside")},::placed,{Placement(parts=listOf(full))}).plan
    private fun Plan.at(p:P)=placements.single { it.pos==p }
    private fun materials(p:Placement)=Geometry.resolve(p.parts,p.groups).materialSolids(p.material,p.surfaces)

    @Test fun `each compass side is clipped from its own interior face`() {
        val plan=room(mapOf("north" to 4,"east" to 8,"south" to 12,"west" to 16)).compiled.plan
        val expected=listOf(P(2,0,0) to 4,P(4,0,2) to 8,P(2,0,4) to 12,P(0,0,2) to 16)
        for((p,depth) in expected) {
            val value=plan.at(p)
            assertEquals(depth*256,Geometry.solid(value.parts).count(),p.toString())
            assertEquals("wall_source",value.parts.single().recipeSource)
        }
        assertTrue(Geometry.solid(plan.at(P(2,0,0)).parts).has(8,8,15))
        assertFalse(Geometry.solid(plan.at(P(2,0,0)).parts).has(8,8,0))
        assertTrue(Geometry.solid(plan.at(P(4,0,2)).parts).has(0,8,8))
    }
    @Test fun `individual wall overrides compass setting and zero removes the wall`() {
        val plan=room(mapOf("north" to 12,"wall:n" to 4,"west" to 0)).compiled.plan
        assertEquals(4*256,Geometry.solid(plan.at(P(2,0,0)).parts).count())
        assertTrue(plan.placements.none { it.pos==P(0,0,2) })
        assertContains(plan.clear,P(0,0,2))
    }
    @Test fun `inside and outside layers meet exactly without filling intentional gaps`() {
        for((inside,outside) in listOf(4 to 12,8 to 8,12 to 4,4 to 4,12 to 12)) {
            val value=storey(mapOf("north" to inside),outside).at(P(2,0,0))
            val regions=materials(value)
            assertEquals(inside*256,regions.getValue("inside").count())
            assertEquals(minOf(outside,16-inside)*256,regions.getValue("outside").count())
            assertEquals(minOf(16,inside+outside)*256,Geometry.solid(value.parts).count())
        }
    }
    @Test fun `facade thickness runs inward from each exterior face`() {
        val value=storey(mapOf("north" to 0,"east" to 0,"south" to 0,"west" to 0),4)
        assertTrue(Geometry.solid(value.at(P(2,0,0)).parts).has(8,8,0))
        assertFalse(Geometry.solid(value.at(P(2,0,0)).parts).has(8,8,15))
        assertTrue(Geometry.solid(value.at(P(4,0,2)).parts).has(15,8,8))
        assertTrue(Geometry.solid(value.at(P(2,0,4)).parts).has(8,8,15))
        assertTrue(Geometry.solid(value.at(P(0,0,2)).parts).has(0,8,8))
    }
    @Test fun `no exterior facade is emitted between adjoining rooms`() {
        val left=room(mapOf("east" to 4));val right=room(mapOf("west" to 4))
        val plan=storey(facade=8,rooms=listOf(SemanticRoomInstance(left,id="left"),SemanticRoomInstance(right,P(5,0,0),"right")))
        assertEquals(setOf("inside"),materials(plan.at(P(4,0,2))).keys)
        assertEquals(setOf("inside"),materials(plan.at(P(5,0,2))).keys)
    }
    @Test fun `omitted thickness retains old one pixel veneer and full facade`() {
        val value=storey().at(P(2,0,0));val regions=materials(value)
        assertEquals(256,regions.getValue("inside").count())
        assertEquals(15*256,regions.getValue("outside").count())
    }
    @Test fun `depth masks span multiple blocks and preserve fractional final layers`() {
        val source=wall(length=1,depth=3)
        val raster=BuildingNode("layout",BuildingKind.ROOM_LAYOUT,inputs=listOf("wall"),roomLayout=RoomLayoutConfig(listOf(RoomSegmentInstance("n"))))
        val room=BuildingNode("room",BuildingKind.ROOM,roomConfig=RoomConfig(RoomMode.SEGMENTS,wallThickness=mapOf("north" to 24)))
        val plan=RoomTopology.compile(room,raster,{source},::placed).compiled.plan
        assertEquals(setOf(P(0,0,1),P(0,0,2)),plan.placements.map { it.pos }.toSet())
        assertEquals(8*256,Geometry.solid(plan.at(P(0,0,1)).parts).count())
        assertEquals(4096,Geometry.solid(plan.at(P(0,0,2)).parts).count())
        assertContains(assertFails { RoomTopology.compile(room.copy(roomConfig=room.roomConfig!!.copy(wallThickness=mapOf("north" to 49))),raster,{source},::placed) }.message.orEmpty(),"deeper segment")
    }
    @Test fun `new settings survive JSON and old JSON keeps source defaults`() {
        val node=BuildingNode("room",BuildingKind.ROOM,roomConfig=RoomConfig(wallThickness=mapOf("north" to 8,"wall:a" to 24)))
        val gson=Gson()
        assertEquals(node,gson.fromJson(gson.toJson(node),BuildingNode::class.java))
        val facade=FacadeConfig("body",thickness=12)
        assertEquals(facade,gson.fromJson(gson.toJson(facade),FacadeConfig::class.java))
        assertNull(gson.fromJson("{\"mode\":\"SEGMENTS\",\"interiorSource\":\"\",\"defaultPartition\":\"\"}",RoomConfig::class.java).wallThickness)
        assertNull(gson.fromJson("{\"source\":\"body\",\"filler\":\"\"}",FacadeConfig::class.java).thickness)
    }
    @Test fun `openings remove both configured layers across their complete depth`() {
        val raster=layout()
        val source=BuildingSemantics.annotate(wall(),"wall",SegmentFeatures(openings=listOf(OpeningSpec("hole",P(2,0,0),P(1,1,1),retainSource=false))))
        val room=RoomTopology.compile(BuildingNode("room",BuildingKind.ROOM,roomConfig=RoomConfig(RoomMode.SEGMENTS,wallThickness=mapOf("north" to 12))),
            raster,{source},::placed)
        val plan=storey(facade=12,rooms=listOf(SemanticRoomInstance(room,id="room")))
        assertTrue(plan.placements.none { it.pos==P(2,0,0) })
        assertContains(plan.clear,P(2,0,0))
    }
    @Test fun `footprint rooms retain independently configured wall depths`() {
        for(joined in listOf(false,true)) {
            val initial=BuildingGraph.explicitStarter(joinWallEnds=joined)
            val n=initial.nodes.single { it.id=="room" }
            val graph=initial.replace(n.copy(roomConfig=RoomConfig(wallThickness=mapOf("north" to 8))))
            val ev=BuildingGraphCompiler.Evaluation(graph,Building().palette,mapOf("full@1" to Form(id="full",parts=listOf(full.copy(recipeSource="")))),emptyMap())
            val plan=ev.room("room").plan
            val north=plan.at(P(1,0,if(joined) 0 else -1))
            assertEquals(8*256,Geometry.solid(north.parts).count())
            assertEquals(4096,Geometry.solid(plan.at(P(if(joined) 2 else 3,0,1)).parts).count())
        }
    }
    @Test fun `native blocks support whole layers but reject partial thickness`() {
        val block=Placement(block="minecraft:stone")
        assertEquals(block,WallLayers.inside(block,0,16,16,1))
        assertNull(WallLayers.inside(block,0,16,0,1))
        assertContains(assertFails { WallLayers.inside(block,0,16,8,1) }.message.orEmpty(),"Copycat")
    }
}
