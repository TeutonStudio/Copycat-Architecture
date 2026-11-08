package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SharedRoomWallTest {
    private val palette=Building().palette
    private val full=Form(id="full",parts=listOf(Part("copycats:copycat_block")))
    private val panel=Form(id="panel",parts=listOf(Part("create:copycat_panel",tilt=1)))
    private val forms=listOf(full,panel).associateBy { "${it.id}@${it.version}" }
    private fun graph(offset:Int=3,firstForm:String="panel@1",secondForm:String=firstForm):BuildingGraph {
        val initial=BuildingGraph.starter()
        val wall=initial.nodes.first { it.id=="wall" }.copy(module="",form=firstForm,length=2,height=2)
        val second=wall.copy(id="second_wall",form=secondForm,role="frame")
        val rectangles=listOf(Rect(width=2,length=2))
        val room=initial.nodes.first { it.id=="room" }.copy(rectangles=rectangles,height=2,
            walls=RoomGeometry.edges(rectangles).associate { it.id to wall.id })
        val neighbor=room.copy(id="neighbor",walls=room.walls.mapValues { second.id })
        val storey=initial.nodes.first { it.id=="storey" }.copy(inputs=listOf(room.id,neighbor.id),offsets=listOf(P(),P(offset,0,0)))
        val assembled=initial.replace(wall).replace(room).replace(storey)
        return assembled.copy(nodes=assembled.nodes+second+neighbor)
    }
    private fun evaluate(graph:BuildingGraph,library:Map<String,Form> = forms)=BuildingGraphCompiler.Evaluation(graph,palette,library,emptyMap())
    private fun Plan.at(pos:P)=placements.single { it.pos==pos }
    private fun materials(p:Placement)=Geometry.resolve(p.parts,p.groups).materialSolids(p.material,p.surfaces)

    @Test fun `neighboring room panels share one shell block and retain both finishes`() {
        val g=graph();val evaluation=evaluate(g);val storey=evaluation.storey("storey").plan
        val first=evaluation.room("room").plan.at(P(2,0,0));val second=evaluation.room("neighbor").plan.at(P(-1,0,0))
        val merged=storey.at(P(2,0,0))
        assertEquals(2,merged.groups.size)
        assertEquals(setOf(palette.getValue("wall"),palette.getValue("frame")),merged.surfaceItems().keys)
        assertEquals(Geometry.solid(first.parts).union(Geometry.solid(second.parts)).key(),Geometry.solid(merged.parts).key())
        assertEquals(1536,Geometry.solid(merged.parts).count())
        assertEquals(96,Extrusion.cost(merged.parts,merged.groups))
        assertEquals(merged.groups.map { it.id }.toSet(),merged.parts.map { it.group }.toSet())
        assertEquals(storey.placements.size,storey.placements.map { it.pos }.distinct().size)
        for(offset in listOf(P(),P(3,0,0))) for(p in RoomGeometry.cells(listOf(Rect(width=2,length=2)))) for(y in 0..1)
            assertTrue(p+offset+P(0,y,0) in storey.clear)
    }

    @Test fun `separate shell coordinates preserve two block wall thickness`() {
        val storey=evaluate(graph(offset=4)).storey("storey").plan
        for(x in 2..3) {
            val wall=storey.at(P(x,0,0))
            assertEquals(1,wall.groups.size)
            assertEquals(768,Geometry.solid(wall.parts).count())
            assertEquals(48,Extrusion.cost(wall.parts,wall.groups))
        }
        assertEquals(palette.getValue("wall"),storey.at(P(2,0,0)).surfaceItems().keys.single())
        assertEquals(palette.getValue("frame"),storey.at(P(3,0,0)).surfaceItems().keys.single())
    }

    @Test fun `union preserves resolved material regions inside each contributing form`() {
        val decorated=Form(id="decorated",groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",5)),parts=listOf(
            Part("create:copycat_panel",tilt=1),Part("copycats:copycat_half_panel",tilt=1,group="accent")))
        val evaluation=evaluate(graph(firstForm="decorated@1"),forms+("decorated@1" to decorated))
        val first=evaluation.room("room").plan.at(P(2,0,0));val second=evaluation.room("neighbor").plan.at(P(-1,0,0))
        val shared=evaluation.storey("storey").plan.at(P(2,0,0));val actual=materials(shared)
        val expected=linkedMapOf<String,Solid>()
        for(source in listOf(first,second)) for((material,solid) in materials(source)) expected[material]=(expected[material] ?: Solid()).union(solid)
        assertEquals(expected.keys,actual.keys)
        expected.forEach { (material,solid) -> assertEquals(solid.key(),actual.getValue(material).key(),material) }
        assertEquals(3,shared.groups.size)
        assertEquals(setOf("surface","glass"),shared.groups.map { it.role }.toSet())
        assertEquals(96,Extrusion.cost(shared.parts,shared.groups))
    }

    @Test fun `independent clipped sources keep separate receipts while split groups share one receipt`() {
        // Both sources mount in the same direction at the shared block. Their clipped halves
        // survive together, and each half was split into two material regions from one panel.
        fun splitPanel(id:String,turn:Int,upper:Boolean):Form {
            val groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",1))
            val nodes=listOf(
                GraphNode("panel_source",part=Part("create:copycat_panel",turn=turn,tilt=1)),
                GraphNode("height_cut",part=Part("copycats:copycat_slab")),
                GraphNode("width_cut",part=Part("copycats:copycat_slab",turn=1,tilt=1)),
                GraphNode("half",if(upper) NodeKind.DIFFERENCE else NodeKind.INTERSECTION,listOf("panel_source","height_cut")),
                GraphNode("a",NodeKind.INTERSECTION,listOf("half","width_cut")),
                GraphNode("b",NodeKind.DIFFERENCE,listOf("half","width_cut")),
                GraphNode("union",NodeKind.UNION,listOf("a","b"),inputGroups=listOf("body","accent")),
                GraphNode("result",NodeKind.OUTPUT,listOf("union")))
            return Form(id=id,groups=groups,graph=NodeGraph(nodes=nodes))
        }
        val lower=splitPanel("lower",0,false);val upper=splitPanel("upper",2,true)
        val evaluation=evaluate(graph(firstForm="lower@1",secondForm="upper@1"),forms+mapOf("lower@1" to lower,"upper@1" to upper))
        val shared=evaluation.storey("storey").plan.at(P(2,0,0))
        assertEquals(768,Geometry.solid(shared.parts).count())
        assertEquals(4,shared.parts.size)
        assertEquals(1,shared.parts.map { it.turn }.distinct().size)
        assertEquals(listOf(2,2),shared.parts.groupingBy { it.recipeSource }.eachCount().values.sorted())
        assertTrue(shared.parts.all { it.recipeSource.isNotEmpty() })
        assertEquals(48,Extrusion.cost(shared.parts,shared.groups))
    }

    @Test fun `automatic corners union differently finished perpendicular wall faces`() {
        val g=graph();val room=g.nodes.first { it.id=="room" }
        val east=RoomGeometry.edges(room.rectangles).single { it.direction==1 }
        val evaluation=evaluate(g.replace(room.copy(walls=room.walls+(east.id to "second_wall"))))
        val plan=evaluation.room("room").plan;val joint=plan.at(P(2,0,-1))
        val north=plan.at(P(1,0,-1));val side=plan.at(P(2,0,0))
        assertEquals(Geometry.solid(north.parts).union(Geometry.solid(side.parts)).key(),Geometry.solid(joint.parts).key())
        assertEquals(1392,Geometry.solid(joint.parts).count())
        val finishes=materials(joint)
        assertEquals(768,finishes.getValue(palette.getValue("wall")).count())
        assertEquals(624,finishes.getValue(palette.getValue("frame")).count())
    }

    @Test fun `earlier room owns shared voxels after each source resolves its own group priorities`() {
        val earlier=panel.copy(id="earlier",groups=listOf(MaterialGroup(priority=-32)))
        // The pre-rotation makes this room's west panel coincide with the first room's east panel.
        val later=panel.copy(id="later",parts=panel.parts.map { it.copy(turn=2) },groups=listOf(MaterialGroup(priority=32)))
        val evaluation=evaluate(graph(firstForm="earlier@1",secondForm="later@1"),forms+mapOf("earlier@1" to earlier,"later@1" to later))
        val shared=evaluation.storey("storey").plan.at(P(2,0,0))
        assertEquals(setOf(palette.getValue("wall")),shared.surfaceItems().keys)
        assertEquals(768,Geometry.solid(shared.parts).count())
        assertEquals(48,Extrusion.cost(shared.parts,shared.groups))
    }

    @Test fun `three nested corner unions clip room ownership before merging matching finishes`() {
        val initial=graph();val room=initial.nodes.first { it.id=="room" };val north=room.copy(id="north")
        val storey=initial.nodes.first { it.id=="storey" }.copy(inputs=listOf("room","neighbor","north"),
            offsets=listOf(P(),P(3,0,0),P(0,0,-3)))
        val evaluation=evaluate(initial.replace(storey).copy(nodes=initial.replace(storey).nodes+north))
        val first=evaluation.room("room").plan.at(P(2,0,-1))
        val second=evaluation.room("neighbor").plan.at(P(-1,0,-1))
        val third=evaluation.room("north").plan.at(P(2,0,2))
        val shared=evaluation.storey("storey").plan.at(P(2,0,-1));val actual=materials(shared)
        val firstSolid=Geometry.solid(first.parts);val secondSolid=Geometry.solid(second.parts);val thirdSolid=Geometry.solid(third.parts)
        assertEquals(firstSolid.union(secondSolid).union(thirdSolid).key(),Geometry.solid(shared.parts).key())
        assertEquals(2496,Geometry.solid(shared.parts).count())
        assertEquals(secondSolid.minus(firstSolid).key(),actual.getValue(palette.getValue("frame")).key())
        assertEquals(firstSolid.union(thirdSolid.minus(firstSolid.union(secondSolid))).key(),actual.getValue(palette.getValue("wall")).key())
        assertTrue(actual.getValue(palette.getValue("frame")).has(1,8,14),"The later stone corner must not overwrite the earlier wooden corner")
    }

    @Test fun `shared wall union still rejects occupied interiors and incompatible native blocks`() {
        assertFails { evaluate(graph(offset=2)).storey("storey") }
        assertFails { evaluate(graph(offset=1)).storey("storey") }
        val g=graph();val second=g.nodes.first { it.id=="second_wall" }.copy(block="minecraft:oak_planks")
        assertFails { evaluate(g.replace(second)).storey("storey") }
        val first=g.nodes.first { it.id=="wall" }.copy(block="minecraft:stone_bricks")
        assertFails { evaluate(g.replace(first).replace(second)).storey("storey") }
    }
}
