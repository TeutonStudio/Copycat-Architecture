package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingRotationTest {
    private val part=Part("copycats:copycat_byte",offset=P(1,2,3))
    private val forms=mapOf("byte@1" to Form(id="byte",parts=listOf(part)))
    private val source=BuildingNode("source",BuildingKind.COPYCAT,form="byte@1",role="frame",surfaces=mapOf("body" to "minecraft:glass"))
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+nodes) }
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun rotate(kind:BuildingKind,id:String,input:String,axis:RotationAxis=RotationAxis.Y,q:Int=1)=
        BuildingAssembly.newNode(kind,id).copy(inputs=listOf(input),rotationAxis=axis,quarterTurns=q)

    @Test fun `rotation nodes have one fixed typed input and reject bad connections and cycles`() {
        for((kind,input,wrong) in listOf(Triple(BuildingKind.COPYCAT_ROTATE,source.id,"wall"),Triple(BuildingKind.SEGMENT_ROTATE,"wall",source.id))) {
            val draft=BuildingAssembly.newNode(kind,"rotation")
            assertEquals(listOf(""),draft.inputs)
            assertEquals(1,draft.quarterTurns)
            assertEquals(1,BuildingPorts.of(draft,true).size)
            val graph=graph(source,draft)
            BuildingGraphCompiler.structure(graph)
            assertContains(assertFails { eval(graph).preview(draft.id) }.message.orEmpty(),"Connect the")
            assertFails { graph.connect(draft.id,"in:0",wrong) }
            assertFails { graph.connect(draft.id,"in:1",input) }
            assertFails { graph.connect(draft.id,"in:0",draft.id) }
            for(inputs in listOf(emptyList(),listOf(input,input),listOf("missing")))
                assertFails { BuildingGraphCompiler.structure(graph.replace(draft.copy(inputs=inputs))) }
            assertFails { BuildingGraphCompiler.structure(graph.replace(draft.copy(rotationAxis=RotationAxis.NORMAL))) }
            assertFails { BuildingGraphCompiler.structure(graph.replace(draft.copy(quarterTurns=4))) }
            val connected=graph.connect(draft.id,"in:0",input)
            assertEquals(listOf(""),connected.connect(draft.id,"in:0","").nodes.single { it.id==draft.id }.inputs)
        }
    }

    @Test fun `Copycat rotation follows source orientation and reflection while preserving finishes and cost`() {
        val oriented=source.copy(rotationAxis=RotationAxis.X,quarterTurns=1)
        val mirror=BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,"mirror").copy(inputs=listOf(source.id))
        for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) for(q in 0..3) {
            val rotation=rotate(BuildingKind.COPYCAT_ROTATE,"rotation",mirror.id,axis,q)
            val segment=BuildingNode("segment",BuildingKind.SEGMENT,inputs=listOf(rotation.id),module="",length=1,height=1)
            val graph=graph(oriented,mirror,rotation,segment)
            val evaluation=eval(graph)
            val before=evaluation.preview(mirror.id);val after=evaluation.preview(rotation.id)
            val expected=before.placements.single().parts.map { PartRotations.rotate(it,axis,q)!! }
            assertTrue(Geometry.exact(expected).equivalent(Geometry.exact(after.placements.single().parts)),"$axis/$q")
            assertEquals(before.raw,after.raw)
            assertEquals(before.materials,after.materials)
            assertEquals(before.placements.single().surfaces,after.placements.single().surfaces)
            assertEquals(after,evaluation.preview(segment.id))
            val grouped=graph.replace(rotation.copy(materialMode=MaterialMode.GROUPED,role="roof"))
            assertEquals(setOf(Building().palette.getValue("roof")),eval(grouped).preview(segment.id).materials.keys)
            val again=rotate(BuildingKind.COPYCAT_ROTATE,"again",rotation.id,axis,Math.floorMod(-q,4))
            assertTrue(Geometry.exact(before.placements.single().parts).equivalent(Geometry.exact(eval(graph.copy(nodes=graph.nodes+again)).preview(again.id).placements.single().parts)))
        }
    }

    @Test fun `segment rotation moves an asymmetric box and its Copycats together and composes with mirrors`() {
        val segment=BuildingNode("segment",BuildingKind.SEGMENT,inputs=listOf(source.id),module="",length=2,height=3,depth=4)
        for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) for(q in 0..3) {
            val rotation=rotate(BuildingKind.SEGMENT_ROTATE,"rotation",segment.id,axis,q)
            val graph=graph(source,segment,rotation)
            val evaluation=eval(graph)
            val actual=evaluation.segment(rotation.id)
            assertEquals(SegmentGeometry.rotated(evaluation.segment(segment.id),axis,q),actual)
            val dimensions=if(q%2==0) P(2,3,4) else when(axis) {
                RotationAxis.X->P(2,4,3);RotationAxis.Y->P(4,3,2);else->P(3,2,4)
            }
            assertEquals(dimensions,P(actual.length,actual.height,actual.depth))
            val before=evaluation.preview(segment.id);val after=evaluation.preview(rotation.id)
            assertEquals(before.raw,after.raw)
            assertEquals(before.materials,after.materials)
            assertEquals(24,after.placements.size)
            val mirrored=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"mirror").copy(inputs=listOf(rotation.id))
            val chained=eval(graph.copy(nodes=graph.nodes+mirrored))
            assertEquals(SegmentGeometry.mirror(actual,RotationAxis.X),chained.segment(mirrored.id))
            val inverse=rotate(BuildingKind.SEGMENT_ROTATE,"inverse",rotation.id,axis,Math.floorMod(-q,4))
            val restored=eval(graph.copy(nodes=graph.nodes+inverse)).preview(inverse.id)
            assertEquals(before.materials,restored.materials)
            assertEquals(before.placements.map { it.pos }.toSet(),restored.placements.map { it.pos }.toSet())
            for(placement in before.placements) assertTrue(Geometry.exact(placement.parts).equivalent(
                Geometry.exact(restored.placements.single { it.pos==placement.pos }.parts)),"Inverse $axis/$q at ${placement.pos}")
        }
    }

    @Test fun `rotation graphs round trip and linked group instances synchronize settings`() {
        val rotation=rotate(BuildingKind.COPYCAT_ROTATE,"rotation",source.id)
        val segment=BuildingNode("segment",BuildingKind.SEGMENT,inputs=listOf(rotation.id),module="",length=1,height=1)
        val turn=rotate(BuildingKind.SEGMENT_ROTATE,"turn",segment.id)
        val original=graph(source,rotation,segment,turn)
        val grouped=GraphGroups.create(original,setOf(source.id,rotation.id,segment.id,turn.id),"Rotated")
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val changed=instances.replace(rotation.copy(rotationAxis=RotationAxis.Z,quarterTurns=3))
        BuildingGraphCompiler.structure(changed)
        val copy=changed.nodeGroups.last().nodes.map { id -> changed.nodes.single { it.id==id } }
        val copiedRotation=copy.single { it.kind==BuildingKind.COPYCAT_ROTATE }
        assertEquals(RotationAxis.Z,copiedRotation.rotationAxis)
        assertEquals(3,copiedRotation.quarterTurns)
        assertEquals(eval(changed).preview(turn.id),eval(changed).preview(copy.single { it.kind==BuildingKind.SEGMENT_ROTATE }.id))
        val restored=Gson().fromJson(Gson().toJson(changed),BuildingGraph::class.java)
        assertEquals(changed,restored)
        assertEquals(eval(changed).preview(turn.id),eval(restored).preview(turn.id))
    }
}
