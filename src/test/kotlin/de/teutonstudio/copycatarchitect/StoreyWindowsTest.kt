package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class StoreyWindowsTest {
    private val forms=mapOf("full@1" to Form(parts=listOf(Part("copycats:copycat_block"))))
    private fun base():BuildingGraph {
        val g=BuildingGraph.starter()
        return g.copy(nodes=g.nodes.map { n->when(n.id) {
            "wall"->n.copy(module="",block="minecraft:stone_bricks",length=7,height=5)
            "room"->n.copy(height=5,rectangles=listOf(Rect(width=7,length=6)),walls=RoomGeometry.edges(listOf(Rect(width=7,length=6))).associate { it.id to if(it.length==7) "wall" else "side" })
            "building"->n.copy(roof="")
            else->n
        } }+listOf(
            BuildingNode("side",BuildingKind.SEGMENT,module="",block="minecraft:stone_bricks",length=6,height=5),
            BuildingNode("glass",BuildingKind.SEGMENT,module="",block="minecraft:glass",length=2,height=2),
            BuildingAssembly.newNode(BuildingKind.WINDOW,"window").copy(inputs=listOf("glass"),length=2,height=2)
        ))
    }
    private fun eval(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap())
    private fun attach(g:BuildingGraph,items:List<WindowInstance>):BuildingGraph=g.replace(g.nodes.first { it.id=="storey" }.copy(windows=items))
    private fun item(g:BuildingGraph,direction:Int=0,offset:Int=1,id:String="w"):WindowInstance {
        val wall=StoreyWindows.walls(g,g.nodes.first { it.id=="storey" }).first { it.direction==direction }
        return WindowInstance(id,"window",wall.roomIndex,wall.edge,offset,1)
    }
    @Test fun `window follows all four wall directions and building plan uses the same placement and costs`() {
        val original=base();val before=eval(original).storey("storey").plan
        for(direction in 0..3) {
            val g=attach(original,listOf(item(original,direction)));val p=eval(g).storey("storey").plan
            assertEquals(4,p.placements.count { it.component=="window" })
            val changed=p.placements.filter { it.component=="window" }.map { it.pos }.toSet()
            assertTrue(before.placements.filter { it.pos !in changed }.all { old->p.placements.any { it==old } })
            val compiled=BuildingGraphCompiler.compile(Building(schema=2,graph=g),forms,emptyMap()).plan
            assertEquals(p.placements.map { it.pos+P(0,-eval(g).storey("storey").minimum,0) }.toSet(),compiled.placements.map { it.pos }.toSet())
            assertEquals(p.materials,compiled.materials)
            val loaded=Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java)
            assertEquals(p,eval(loaded).storey("storey").plan)
        }
    }
    @Test fun `moving resizing removing and repeated definitions recompute openings from the original walls`() {
        val base=base();val first=item(base);val g=attach(base,listOf(first,first.copy(id="second",offset=4)))
        assertEquals(8,eval(g).storey("storey").plan.placements.count { it.component=="window" })
        val moved=attach(base,listOf(first.copy(offset=3,sill=2)))
        val before=eval(base).storey("storey").plan
        val after=eval(moved).storey("storey").plan
        assertTrue(eval(attach(base,emptyList())).storey("storey").plan==before)
        val initialPositions=eval(attach(base,listOf(first))).storey("storey").plan.placements.filter { it.component=="window" }.map { it.pos }.toSet()
        assertTrue(before.placements.filter { it.pos in initialPositions }.all { old->after.placements.any { it==old } })
        val resized=attach(base,listOf(first)).replace(base.nodes.first { it.id=="window" }.copy(length=4))
        assertEquals(8,eval(resized).storey("storey").plan.placements.count { it.component=="window" })
        for(invalid in listOf(first.copy(offset=127),first.copy(sill=5),first.copy(edge="missing"),first.copy(depthOffset=128))) assertFails { eval(attach(base,listOf(invalid))).storey("storey") }
        assertFails { eval(attach(base,listOf(first,first.copy(id="overlap")))).storey("storey") }
        assertFails { eval(attach(base,listOf(first.copy(autoOpening=false)))).storey("storey") }
        val grouped=GraphGroups.create(g,setOf("glass","window"));val copy=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        assertTrue(copy.nodes.any { it.kind==BuildingKind.WINDOW && it.id!="window" && it.inputs.single()!="glass" })
        assertEquals("",g.remove("window").nodes.first { it.id=="storey" }.windows!!.first().source)
    }


    @Test fun `complete framed module keeps geometry material groups and costs as a window definition`() {
        val gson=Gson();val json=gson.fromJson(java.io.File("examples/library.json").readText(),com.google.gson.JsonObject::class.java)
        val library=json.getAsJsonObject("forms").entrySet().associate { it.key to gson.fromJson(it.value,Form::class.java) }
        val module=gson.fromJson(java.io.File("examples/window-module.json").readText(),Module::class.java)
        val g=base().let { g->g.copy(nodes=g.nodes+BuildingNode("frame",BuildingKind.SEGMENT,module=module.key,length=module.width,height=module.height)) }
            .let { g->g.replace(g.nodes.first { it.id=="window" }.copy(inputs=listOf("frame"),length=module.width,height=module.height)) }
        val e=BuildingGraphCompiler.Evaluation(g,Building().palette,library,mapOf(module.key to module))
        assertEquals(e.preview("frame"),e.preview("window"))
    }
    @Test fun `opening never cuts the opposite wall through a narrow room interior`() {
        val g=base();val host=g.nodes.first { it.id=="storey" };val a=StoreyWindows.walls(g,host).first { it.direction==0 }
        val origin=a.cells[1];val r=Resolver()
        for(x in 0..1) for(y in 1..2) {
            r.add(Placement(origin+P(x,y,0),block="minecraft:stone_bricks"))
            r.clear(origin+P(x,y,1))
            r.add(Placement(origin+P(x,y,2),block="minecraft:stone_bricks"))
        }
        val result=StoreyWindows.attach(g,host.copy(windows=listOf(item(g))),r.plan(emptyList()),{ FixedSegment(2,2,(0..1).flatMap { x->(0..1).map { y->SegmentCell(Cell(P(x,y,0),block="minecraft:glass")) } }) },
            { p,entries,_,_->Placement(p,block=entries.first().cell.block) },{ error("unused") })
        assertEquals(4,result.placements.count { it.block=="minecraft:stone_bricks" })
        assertTrue(result.placements.filter { it.block=="minecraft:stone_bricks" }.all { it.pos.z==origin.z+2 })
    }
    @Test fun `separated wall layers are cut across air gaps and exact window frame geometry is retained`() {
        val g=base();val host=g.nodes.first { it.id=="storey" };val a=StoreyWindows.walls(g,host).first { it.direction==0 };val origin=a.cells[1]
        val cells=(0 until 7).flatMap { x->(0 until 5).flatMap { y->listOf(-8,0,1).map { z->Placement(a.cells.first()+P(x,y,z),block="minecraft:stone_bricks",component="wall") } } }
        val resolver=Resolver();cells.forEach { resolver.add(it) };val base=resolver.plan(emptyList())
        val selected=host.copy(windows=listOf(WindowInstance("cut","window",0,a.edge,1,1)))
        val partial=Part("copycats:copycat_layer",size=4)
        val result=StoreyWindows.attach(g,selected,base,{ FixedSegment(2,2,(0..1).flatMap { x->(0..1).map { y->SegmentCell(Cell(P(x,y,0),form="partial@1")) } }) },
            { p,entries,_,_->Placement(p,parts=listOf(partial),component="wall") },{ error("unused") })
        val cutPositions=(0..1).flatMap { x->(1..2).flatMap { y->listOf(-8,0,1).map { z->origin+P(x,y,z) } } }.toSet()
        assertEquals(12,cells.count { it.pos in cutPositions })
        assertTrue(result.placements.none { it.component=="wall" && it.pos in cutPositions })
        assertTrue(cells.filter { it.pos !in cutPositions }.all { it in result.placements })
        assertTrue(result.placements.filter { it.component=="window" }.all { it.parts==listOf(partial) })
    }
}
