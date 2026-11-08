package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomTopologyTest {
    private val full=Part("copycats:copycat_block")
    private fun wall(length:Int=5,height:Int=4,air:Boolean=false)=FixedSegment(length,height,
        if(air) emptyList() else (0 until length).flatMap { x->(0 until height).map { y->SegmentCell(Cell(P(x,y,0))) } },air)
    private fun layout(instances:List<RoomSegmentInstance> = rectangle(),boundary:List<P> = emptyList())=BuildingNode("layout",BuildingKind.ROOM_LAYOUT,
        inputs=listOf("wall","window"),roomLayout=RoomLayoutConfig(instances,boundary))
    private fun rectangle()=listOf(RoomSegmentInstance("north",0,P(),0),RoomSegmentInstance("east",0,P(4,0,0),1),
        RoomSegmentInstance("south",0,P(0,0,4),2),RoomSegmentInstance("west",0,P(),3))
    private fun placed(p:P,cells:List<SegmentCell>,turn:Int,type:String)=Placement(p,SegmentGeometry.parts(cells.first().cell,listOf(full),turn),component=type)
    private fun compiled(layout:BuildingNode=layout(),source:(String)->FixedSegment={wall(air=true)})=RoomTopology.compile(
        BuildingNode("room",BuildingKind.ROOM,roomConfig=RoomConfig(RoomMode.SEGMENTS)),layout,source,::placed)

    @Test fun `logical boundaries and air segments do not reserve phantom wall area`() {
        val outline=listOf(P(),P(5,0,0),P(5,0,5),P(0,0,5))
        val logical=RoomTopology.resolve(layout(emptyList(),outline)) {wall()}
        assertEquals(25,logical.interior.size);assertTrue(logical.wallColumns.isEmpty())
        val emptySegments=RoomTopology.resolve(layout()) {wall(air=true)}
        assertEquals(25,emptySegments.interior.size);assertTrue(emptySegments.wallColumns.isEmpty())
    }
    @Test fun `an open canopy may meet an enclosed hall without deleting or adding a wall`() {
        val canopy=compiled(layout(rectangle().map {it.copy(policy=WallPolicy.OPEN)}))
        for(policy in listOf(WallPolicy.AUTO,WallPolicy.EXPLICIT)) {
            val hall=compiled(layout(rectangle().map {it.copy(policy=policy)})) {wall(air=policy==WallPolicy.AUTO)}
            val story=SemanticStoreys.compile(BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body")),
                listOf(SemanticRoomInstance(hall,id="hall"),SemanticRoomInstance(canopy,P(5,0,0),"canopy")),{wall(1,1)},::placed,{Placement(parts=listOf(full))})
            assertTrue(story.plan.placements.any {it.pos==P(4,2,2)})
            assertFalse(story.plan.placements.any {it.pos==P(5,2,2) || it.pos==P(9,2,2)})
        }
    }
    @Test fun `occupied plates stop at a facade body created after room compilation`() {
        val room=RoomTopology.compile(BuildingNode("room",BuildingKind.ROOM),layout(),{wall(air=true)},::placed,
            plates={ topology,height ->
                val floor=topology.interior.map {Placement(it,parts=listOf(full),material="floor",component="floor")}
                RoomPlateResult(Resolver().apply {floor.forEach {add(it)}}.plan(emptyList()),maximum=height,usableHeight=height-1)
            })
        val story=SemanticStoreys.compile(BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body")),
            listOf(SemanticRoomInstance(room,id="room")),{wall(1,1)},::placed,{Placement(parts=listOf(full))})
        assertEquals("minecraft:stone",story.plan.placements.single {it.pos==P(2,0,0)}.material)
        assertEquals("floor",story.plan.placements.single {it.pos==P(2,0,2)}.material)
    }
    @Test fun `site and body maps use segment geometry rather than stale room rectangles`() {
        val source=BuildingNode("wall",BuildingKind.SEGMENT,module="",length=5,height=4)
        val raster=layout().copy(inputs=listOf("wall"))
        val room=BuildingNode("room",BuildingKind.ROOM,assembly=true,layout="layout",roomConfig=RoomConfig(RoomMode.SEGMENTS),rectangles=listOf(Rect(-30,-30,20,20)))
        val storey=BuildingNode("storey",BuildingKind.STOREY,inputs=listOf("room"),offsets=listOf(P(8,0,-6)))
        val result=BuildingNode("building",BuildingKind.BUILDING,levels=mapOf(0 to "storey"),buildingOffset=P(3,0,12))
        val graph=BuildingGraph(schema=3,nodes=listOf(source,raster,room,storey,result))
        val expected=(0..4).flatMap { x->(0..4).map { z->P(x+8,0,z-6) } }.toSet()
        assertEquals(expected,LandscapeGeometry.buildingFootprint(graph))
        assertEquals(expected.map {it+result.buildingOffset}.toSet(),BuildingSite.footprint(graph,BuildingSite.part(graph,"building",BuildingSite.BODY)))
        assertFailsWith<IllegalArgumentException> {graph.reshapeRoom("room",listOf(Rect(width=2,length=2)))}
    }
    @Test fun `geometry only outline evaluates nested patterns without material recipes`() {
        val a=BuildingNode("a",BuildingKind.SEGMENT,module="",length=2,height=4)
        val b=BuildingNode("b",BuildingKind.SEGMENT,module="",length=3,height=4)
        val pattern=BuildingNode("wall",BuildingKind.PATTERN,inputs=listOf("a","b"))
        val raster=layout().copy(inputs=listOf("wall"))
        val graph=BuildingGraph(nodes=listOf(a,b,pattern,raster))
        val outline=RoomTopology.outline(graph,raster)
        assertTrue(outline.closed);assertEquals(25,outline.footprint.size)
        assertEquals(4,outline.height)
    }
    @Test fun `opening modules close the logical boundary while normal walls determine height`() {
        val raster=layout(rectangle().map {if(it.id=="north") it.copy(sourceIndex=1,contribution=RoomContribution.OPENING) else it})
        val outline=RoomTopology.resolve(raster) {if(it=="window") wall(5,12) else wall()}
        assertTrue(outline.closed);assertEquals(25,outline.footprint.size);assertEquals(4,outline.height)
    }
    @Test fun `oriented segment ends infer a room and preserve placement identity`() {
        val result=RoomTopology.resolve(layout()) {wall()}
        assertTrue(result.closed)
        assertEquals(25,result.footprint.size)
        assertEquals(9,result.interior.size)
        assertEquals(5,result.width);assertEquals(5,result.depth);assertEquals(4,result.height)
        assertEquals(setOf("north","east","south","west"),result.instances.map { it.definition.id }.toSet())
        assertEquals(P(5,0,0),result.instances[1].start)
    }
    @Test fun `open arrangement has extents but never invents plates or floor area`() {
        var plateCalls=0
        val room=RoomTopology.compile(BuildingNode("room",BuildingKind.ROOM),layout(rectangle().dropLast(1)),{wall()},::placed,
            plates={ _,_->plateCalls++;RoomPlateResult() })
        assertFalse(room.topology.closed);assertTrue(room.compiled.footprint.isEmpty());assertEquals(0,plateCalls)
        assertTrue(room.topology.width>0);assertTrue(room.topology.warnings.isNotEmpty())
        val suggestions=RoomTopology.closureSuggestions(layout(rectangle().dropLast(1))) {wall()}
        assertEquals(1,suggestions.size)
        assertFalse(RoomTopology.resolve(layout(rectangle().dropLast(1))) {wall()}.closed)
    }
    @Test fun `explicit logical outline closes an open canopy without adding walls`() {
        val outline=listOf(P(),P(5,0,0),P(5,0,5),P(0,0,5))
        val room=compiled(layout(emptyList(),outline))
        assertTrue(room.topology.closed);assertEquals(25,room.topology.footprint.size)
        assertTrue(room.compiled.plan.placements.isEmpty())
    }
    @Test fun `accepted open closure remains open when the stockwork adds a facade`() {
        val outline=listOf(P(),P(5,0,0),P(5,0,5),P(0,0,5))
        val source=layout(rectangle().dropLast(1),outline)
        val open=source.copy(roomLayout=source.roomLayout!!.copy(boundaryRules=listOf(RoomBoundaryRule(P(0,0,5),P(),WallPolicy.OPEN))))
        val room=compiled(open)
        assertTrue(room.topology.closed)
        assertTrue(room.topology.spans.filter {it.start.x==0 && it.end.x==0}.all {it.policy==WallPolicy.OPEN})
        val story=SemanticStoreys.compile(BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body")),
            listOf(SemanticRoomInstance(room,id="room")),{wall(1,1)},::placed,{Placement(parts=listOf(full))})
        assertFalse(story.plan.placements.any {it.pos==P(0,2,2)})
    }
    @Test fun `opening and structure heights do not increase the room height`() {
        val items=rectangle()+RoomSegmentInstance("tower",1,P(2,0,2),contribution=RoomContribution.STRUCTURE)
        val value=RoomTopology.resolve(layout(items)) {if(it=="window") wall(1,12) else wall()}
        assertEquals(4,value.height)
        val room=RoomTopology.compile(BuildingNode("room",BuildingKind.ROOM,roomConfig=RoomConfig(RoomMode.SEGMENTS,7)),layout(items),
            {if(it=="window") wall(1,12) else wall()},::placed)
        assertEquals(7,room.compiled.usableHeight)
    }
    @Test fun `concave outline keeps its missing quadrant and rejects self crossing`() {
        val l=listOf(P(),P(6,0,0),P(6,0,3),P(3,0,3),P(3,0,6),P(0,0,6))
        val resolved=RoomTopology.resolve(layout(emptyList(),l)) {wall()}
        assertEquals(27,resolved.footprint.size);assertFalse(P(4,0,4) in resolved.footprint)
        val crossing=listOf(P(),P(4,0,0),P(4,0,4),P(2,0,4),P(2,0,-2),P(0,0,-2))
        assertFailsWith<IllegalArgumentException> {RoomTopology.resolve(layout(emptyList(),crossing)) {wall()}}
    }
    @Test fun `policies conflict visibly rather than depending on source order`() {
        val items=rectangle()+rectangle().first().copy(id="conflict",policy=WallPolicy.OPEN)
        assertFailsWith<IllegalArgumentException> {RoomTopology.resolve(layout(items,listOf(P(),P(5,0,0),P(5,0,5),P(0,0,5)))) {wall()}}
    }
    @Test fun `stockwork splits shared wall once and keeps windows out of exterior defaults`() {
        val room=compiled()
        val story=SemanticStoreys.compile(BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body")),
            listOf(SemanticRoomInstance(room,P(),"left"),SemanticRoomInstance(room,P(5,0,0),"right")),{wall(1,1)},::placed,{Placement(parts=listOf(full))})
        assertEquals(story.plan.placements.size,story.plan.placements.map {it.pos}.distinct().size)
        assertTrue(story.plan.placements.any {it.pos==P(4,2,2)})
        assertFalse(story.plan.placements.any {it.pos==P(5,2,2)})
    }
    @Test fun `two room finishes share one partition body and keep their own materials`() {
        fun colored(name:String)=wall().copy(cells=wall().cells.map { it.copy(cell=it.cell.copy(role=name)) })
        fun painter(p:P,cells:List<SegmentCell>,turn:Int,type:String)=placed(p,cells,turn,type).copy(material=cells.first().cell.role)
        fun room(name:String)=RoomTopology.compile(BuildingNode(name,BuildingKind.ROOM),layout(),{colored(name)},::painter)
        val left=room("left");val right=room("right")
        val body=wall(1,1).copy(cells=listOf(SegmentCell(Cell(role="body"))))
        val story=SemanticStoreys.compile(BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body")),
            listOf(SemanticRoomInstance(left,id="left"),SemanticRoomInstance(right,P(5,0,0),"right")),{body},::painter,{Placement(parts=listOf(full))})
        val partition=story.plan.placements.single {it.pos==P(4,2,2)}
        val materials=Geometry.resolve(partition.parts,partition.groups).materialSolids(partition.material,partition.surfaces)
        assertEquals(setOf("left","right","body"),materials.keys)
        assertTrue(materials.getValue("left").has(0,8,8))
        assertTrue(materials.getValue("right").has(15,8,8))
        assertTrue(materials.getValue("body").has(8,8,8))
        assertFalse(story.plan.placements.any { it.pos==P(5,2,2) })
    }
    @Test fun `full depth opening removes facade while preserving same cell frame`() {
        val opening=OpeningSpec("door",P(1,0,0),P(2,3,1),retainSource=false)
        val annotated=BuildingSemantics.annotate(wall(air=true),"wall",SegmentFeatures(listOf(opening)))
        val northOnly=rectangle().map {if(it.id=="north") it.copy(sourceIndex=1) else it}
        val room=compiled(layout(northOnly)) {if(it=="window") annotated else wall(air=true)}
        val story=SemanticStoreys.compile(BuildingNode("storey",BuildingKind.STOREY,facade=FacadeConfig("body")),
            listOf(SemanticRoomInstance(room,id="room")),{wall(1,1)},::placed,{Placement(parts=listOf(full))})
        assertFalse(story.plan.placements.any {it.pos==P(1,1,0)})
        assertTrue(story.plan.placements.any {it.pos==P(1,3,0)})
        assertTrue(P(1,1,0) in story.plan.clear)
    }
}
