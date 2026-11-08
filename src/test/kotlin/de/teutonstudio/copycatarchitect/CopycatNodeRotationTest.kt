package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CopycatNodeRotationTest {
    private val byte=Form(id="byte",parts=listOf(Part("copycats:copycat_byte")))
    private val full=Form(id="full",parts=listOf(Part("copycats:copycat_block")))
    private val forms=mapOf("byte@1" to byte,"full@1" to full)
    private val source=BuildingNode("rotated_copycat",BuildingKind.COPYCAT,form="byte@1",role="frame",rotationAxis=RotationAxis.X,quarterTurns=1)
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun rotated(axis:RotationAxis=source.rotationAxis,turns:Int=source.quarterTurns)=Geometry.validate(byte).parts.map { PartRotations.rotate(it,axis,turns)!! }
    private fun assertGeometry(expected:List<Part>,actual:Placement) {
        assertEquals(Geometry.solid(expected).key(),Geometry.solid(actual.parts).key())
        assertTrue(actual.parts.all { it.source==byte.parts.single().source })
        assertEquals(Building().palette.getValue("frame"),actual.material)
    }

    @Test fun `source orientation compiles in every shape input without modifying its original part`() {
        val part=Part("copycats:copycat_byte",group="cap")
        val groups=listOf(MaterialGroup("cap","Cap","frame"))
        for(axis in RotationAxis.entries) for(q in 0..3) {
            val input=GraphNode("source",part=part,rotationAxis=axis,quarterTurns=q)
            val graph=NodeGraph(nodes=listOf(input,GraphNode("result",NodeKind.OUTPUT,listOf(input.id))))
            val json=Gson().toJson(graph)
            val result=GraphCompiler.compile(graph,groups)
            val expected=PartRotations.rotate(part,axis,q)!!
            assertEquals(Solid.part(expected).key(),Geometry.solid(result).key())
            assertTrue(result.all { it.source==part.source && it.group=="cap" })
            assertEquals(json,Gson().toJson(graph))
            assertEquals(result,GraphCompiler.compile(Gson().fromJson(json,NodeGraph::class.java),groups))
        }
    }

    @Test fun `building Copycat local rotation precedes layout rotation and wall mounting`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val layout=initial.nodes.single { it.id=="wall_layout" }.copy(layoutCells=listOf(SegmentLayoutCell(0,0,0,1)))
        val wall=initial.nodes.single { it.id=="wall" }.copy(inputs=listOf(source.id))
        val graph=initial.copy(nodes=initial.nodes+source).replace(layout).replace(wall)
        val evaluation=eval(graph)
        assertGeometry(rotated(),evaluation.preview(source.id).placements.single())
        assertGeometry(Geometry.rotate(rotated(),1),evaluation.preview(wall.id).placements.single())
        val room=evaluation.room("room")
        val eastWall=room.plan.placements.single { it.component=="wall" && it.pos==P(3,0,0) }
        assertGeometry(Geometry.rotate(rotated(),2),eastWall)
    }

    @Test fun `legacy Copycat consumers use orientation in floors ceilings roof and explicit corners`() {
        val initial=BuildingGraph.starter()
        val wall=initial.nodes.single { it.id=="wall" }.copy(module="",inputs=listOf(source.id))
        val floor=initial.nodes.single { it.id=="floor" }.copy(inputs=listOf(source.id))
        val ceiling=initial.nodes.single { it.id=="ceiling" }.copy(inputs=listOf(source.id))
        val room=initial.nodes.single { it.id=="room" }.copy(corner=source.id)
        val roof=initial.nodes.single { it.id=="roof" }.copy(inputs=listOf(source.id),roofType="flat")
        val graph=initial.copy(nodes=initial.nodes+source).replace(wall).replace(floor).replace(ceiling).replace(room).replace(roof)
        val evaluated=eval(graph)
        val plan=evaluated.room(room.id).plan
        for(component in listOf("floor","ceiling")) assertGeometry(rotated(),plan.placements.first { it.component==component })
        assertGeometry(rotated(),evaluated.preview(roof.id).placements.first())
        assertGeometry(rotated(),plan.placements.single { it.component=="trim" && it.pos==P(-1,0,-1) })
        val gable=evaluated.graph.replace(roof.copy(roofType="gable"))
        val sample=eval(gable).preview(roof.id).placements.single { it.component=="roof" && it.pos==P(-1,0,-1) }
        assertGeometry(Geometry.rotate(rotated(),3),sample)
    }

    @Test fun `carpet support validation examines rotated Copycat top face`() {
        val panel=Form(id="panel",parts=listOf(Part("create:copycat_panel",top=true)))
        val initial=BuildingGraph.starter()
        val support=source.copy(form="panel@1",quarterTurns=0)
        val floor=initial.nodes.single { it.id=="floor" }.copy(inputs=listOf(source.id),thickness=2,finish="minecraft:white_carpet")
        val room=initial.nodes.single { it.id=="room" }.copy(walls=emptyMap())
        val graph=initial.copy(nodes=initial.nodes+support).replace(floor).replace(room)
        fun evaluator(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms+("panel@1" to panel),emptyMap())
        assertTrue(evaluator(graph).room(room.id).plan.placements.any { it.block==floor.finish })
        assertContains(assertFails { evaluator(graph.replace(support.copy(quarterTurns=1))).room(room.id) }.message.orEmpty(),"complete top face")
    }

    @Test fun `isolated plate previews match compiled layers with source order air rotations and grouped finishes`() {
        val initial=BuildingGraph.starter()
        val air=source.copy(id="air_copycat",block="minecraft:air")
        val floor=initial.nodes.single { it.id=="floor" }.copy(inputs=listOf(source.id,air.id),thickness=2,materialMode=MaterialMode.GROUPED,role="frame")
        val ceiling=initial.nodes.single { it.id=="ceiling" }.copy(inputs=listOf(air.id,source.id),thickness=2,role="frame")
        val room=initial.nodes.single { it.id=="room" }.copy(rectangles=listOf(Rect(width=1,length=1)),walls=emptyMap())
        val graph=initial.copy(nodes=initial.nodes+source+air).replace(floor).replace(ceiling).replace(room)
        val evaluated=eval(graph)
        val enclosed=evaluated.room(room.id).plan
        for(layer in listOf(floor,ceiling)) {
            val sample=evaluated.preview(layer.id)
            val component=if(layer.kind==BuildingKind.FLOOR) "floor" else "ceiling"
            val shift=if(layer.kind==BuildingKind.FLOOR) P(1,2,1) else P(1,-4,1)
            val expected=enclosed.placements.filter { it.component==component }.map { it.copy(pos=it.pos+shift) }.associateBy { it.pos }
            assertEquals(expected,sample.placements.associateBy { it.pos })
            assertEquals((0..2).flatMap { x -> (0..1).flatMap { y -> (0..2).map { z -> P(x,y,z) } } }.toSet(),(sample.clear+sample.placements.map { it.pos }).toSet())
            assertEquals(9,sample.placements.size);assertEquals(9,sample.clear.size)
            assertEquals(9,sample.materials.getValue(Building().palette.getValue("frame")))
        }
        val carpet=floor.copy(inputs=emptyList(),form="full@1",finish="minecraft:white_carpet")
        val carpetPreview=eval(graph.replace(carpet)).preview(carpet.id)
        assertEquals(9,carpetPreview.materials.getValue("minecraft:white_carpet"))
        assertEquals(9,carpetPreview.placements.count { it.block=="minecraft:white_carpet" && it.pos.y==1 })
        assertFails { eval(graph.replace(carpet.copy(inputs=listOf(source.id)))).preview(carpet.id) }
    }

    @Test fun `old node JSON retains identity orientation and invalid rotations are rejected`() {
        val gson=Gson()
        val old=gson.fromJson("""{"id":"old","kind":"COPYCAT","form":"byte@1"}""",BuildingNode::class.java)
        assertEquals(RotationAxis.Y,old.rotationAxis);assertEquals(0,old.quarterTurns)
        val initial=BuildingGraph.starter()
        for(invalid in listOf(source.copy(quarterTurns=4),source.copy(rotationAxis=RotationAxis.NORMAL))) {
            assertFailsWith<IllegalArgumentException> { eval(initial.copy(nodes=initial.nodes+invalid)) }
        }
        assertEquals(source,gson.fromJson(gson.toJson(source),BuildingNode::class.java))
    }
}
