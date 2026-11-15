package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*

class DynamicAreaSegmentTest {
    private val forms=mapOf("full@1" to Form(parts=listOf(Part("copycats:copycat_block"))))
    private fun graph(vararg extra:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+extra).withCurrentSchema() }
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun source(id:String="dynamic",axes:Set<RotationAxis> = setOf(RotationAxis.X,RotationAxis.Y))=BuildingNode(id,BuildingKind.SEGMENT,assembly=true,module="",inputs=listOf("copy"),dynamicAxes=axes,length=2,height=2)
    private val copy=BuildingNode("copy",BuildingKind.COPYCAT,surfaces=mapOf("body" to "minecraft:gold_block"))

    @Test fun `square and rectangle resolve Anzahl ports while drawn areas retain exact coordinates`() {
        val square=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.SQUARE,length=3,height=7,dimensions=mapOf("length" to "count"))
        val count=BuildingNode("count",BuildingKind.INT,countValue=4)
        val g=graph(square,count)
        assertEquals(16,g.areaOf(square)!!.points.size)
        assertEquals(listOf("length"),BuildingPorts.of(square,g).map { it.key })
        val rectangle=square.copy(areaMode=AreaMode.RECTANGLE,dimensions=mapOf("length" to "count","height" to "count2"))
        val r=g.copy(nodes=g.nodes+BuildingNode("count2",BuildingKind.INT,countValue=2)).replace(rectangle)
        assertEquals(8,r.areaOf(rectangle)!!.points.size)
        assertEquals(listOf("length","height"),BuildingPorts.of(rectangle,r).map { it.key })
        val points=listOf(P(-2,0,1),P(1,0,3))
        val drawn=r.editRoomGeometry("area",points)
        assertEquals(AreaMode.DRAW,drawn.nodes.first { it.id=="area" }.areaMode)
        assertEquals(points,drawn.areaOf(drawn.nodes.first { it.id=="area" })!!.points)
        assertTrue(BuildingPorts.of(drawn.nodes.first { it.id=="area" },drawn).isEmpty())
        assertNull(drawn.editRoomGeometry("area",emptyList()).areaOf(drawn.nodes.first { it.id=="area" }.copy(coordinates=emptyList())))
    }
    @Test fun `dynamic recipe sizes independently at each consumer without stretching copycats or materials`() {
        val src=source();val g=graph(copy,src);val e=eval(g)
        val a=e.segment(src.id,P(3,5,1));val b=e.segment(src.id,P(7,2,1))
        assertEquals(P(3,5,1),P(a.length,a.height,a.depth));assertEquals(15,a.cells.size)
        assertEquals(P(7,2,1),P(b.length,b.height,b.depth));assertEquals(14,b.cells.size)
        assertEquals(4,e.segment(src.id).cells.size)
        assertTrue(a.cells.all { it.cell.surfaces?.get("body")=="minecraft:gold_block" })
        assertEquals(src,g.nodes.first { it.id==src.id })
        assertEquals(listOf("in:0"),BuildingPorts.of(src,g,true).map { it.key })
        assertFails { e.segment(src.id,P(129,2,1)) }
    }
    @Test fun `union offsets rotation mirror and difference propagate dynamic dimensions before geometry`() {
        val a=source("a",setOf(RotationAxis.X));val b=source("b",setOf(RotationAxis.X))
        val union=BuildingNode("union",BuildingKind.SEGMENT_UNION,inputs=listOf("a","b"),offsets=listOf(P(),P(1,1,0)))
        val rotate=BuildingNode("rotate",BuildingKind.SEGMENT_ROTATE,inputs=listOf("union"),quarterTurns=1,rotationAxis=RotationAxis.Y)
        val mirror=BuildingNode("mirror",BuildingKind.SEGMENT_MIRROR,inputs=listOf("rotate"),rotationAxis=RotationAxis.X)
        val g=graph(copy,a,b,union,rotate,mirror);val e=eval(g)
        assertEquals(setOf(RotationAxis.Z),DynamicSegments.axes(g,"mirror"))
        val result=e.segment("mirror",P(1,3,8))
        assertEquals(P(1,3,8),P(result.length,result.height,result.depth))
        assertEquals(8,e.segment("union",P(8,3,1)).length)
        assertEquals(2,e.segment("a").length)
        val diff=BuildingNode("difference",BuildingKind.SEGMENT_DIFFERENCE,inputs=listOf("a","b"))
        val d=eval(g.copy(nodes=g.nodes+diff)).segment(diff.id,P(6,2,1))
        assertEquals(6,d.length);assertTrue(d.cells.isEmpty())
    }
    @Test fun `stacking allocates a requested dimension between fixed and dynamic operands`() {
        val a=source("a",setOf(RotationAxis.X));val fixed=source("fixed",emptySet()).copy(assembly=false,length=2)
        val pattern=BuildingNode("pattern",BuildingKind.PATTERN,assembly=true,inputs=listOf("a","fixed"))
        val g=graph(copy,a,fixed,pattern);val result=eval(g).segment("pattern",P(9,2,1))
        assertEquals(9,result.length);assertEquals(18,result.cells.size)
        assertFails { eval(g).segment("pattern",P(2,2,1)) }
    }
    @Test fun `room walls consume one dynamic recipe at different lengths`() {
        val base=BuildingGraph.starter();val rect=listOf(Rect(width=3,length=5))
        val room=base.nodes.first { it.id=="room" }.copy(rectangles=rect,walls=RoomGeometry.edges(rect).associate { it.id to "dynamic" })
        val g=base.copy(nodes=base.nodes.map { if(it.id==room.id) room else it }+copy+source()).withCurrentSchema()
        BuildingGraphCompiler.structure(g)
        val plan=eval(g).room("room").plan
        assertTrue(plan.placements.any { it.component=="wall" })
        assertEquals(2,g.nodes.first { it.id=="dynamic" }.length)
    }
    @Test fun `adaptive roof detects variants and sizes segment periods including the wall independently`() {
        val base=BuildingGraph.explicitStarter();val src=source(axes=setOf(RotationAxis.X,RotationAxis.Y))
        val roof=base.nodes.first { it.id=="roof" }.copy(adaptiveRoof=true,inputs=List(5) { "dynamic" })
        val level=BuildingNode("level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=RoofForm.GABLE,overhang16=0))
        val g=base.copy(nodes=base.nodes.map { when(it.id) { "roof"->roof;"roof_layout"->it.copy(inputs=listOf("level"));"building"->it.copy(roof="roof");else->it } }+copy+src+level).withCurrentSchema()
        BuildingGraphCompiler.structure(g)
        val plan=BuildingGraphCompiler.compile(Building(schema=2,graph=g),forms,emptyMap()).plan
        assertTrue(plan.placements.any { it.component=="roof" })
        assertTrue(plan.placements.any { "minecraft:gold_block" in it.surfaces.values })
        assertEquals(6,BuildingPorts.of(roof,g,true).size)
        assertEquals(src,g.nodes.first { it.id==src.id })
    }
    @Test fun `new features require schema seven and round trip while old schemas stay unchanged`() {
        val old=BuildingGraph.starter();assertEquals(old,old.withCurrentSchema())
        val g=graph(copy,source());assertEquals(7,g.schema)
        assertFails { BuildingGraphCompiler.structure(g.copy(schema=6)) }
        assertEquals(g,Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java))
    }
}
