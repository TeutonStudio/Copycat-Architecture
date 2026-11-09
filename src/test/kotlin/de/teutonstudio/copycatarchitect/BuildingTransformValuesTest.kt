package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingTransformValuesTest {
    private val forms=mapOf("byte@1" to Form(parts=listOf(Part("copycats:copycat_byte",offset=P(1,2,3)))))
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+nodes) }
    private fun eval(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap())

    @Test fun `ordered values match chained operators and an empty list preserves geometry`() {
        val source=BuildingNode("source",BuildingKind.COPYCAT,form="byte@1")
        val segment=BuildingNode("segment",BuildingKind.SEGMENT,inputs=listOf(source.id),module="",length=2,height=3,depth=4)
        val steps=BuildingNode("steps",BuildingKind.ROTATION,rotations=listOf(SegmentRotation(RotationAxis.X,1),SegmentRotation(RotationAxis.Y,1)))
        for((kind,input) in listOf(BuildingKind.COPYCAT_ROTATE to source.id,BuildingKind.SEGMENT_ROTATE to segment.id)) {
            val actual=BuildingAssembly.newNode(kind,"actual").copy(inputs=listOf(input),rotation=steps.id)
            val first=BuildingAssembly.newNode(kind,"first").copy(inputs=listOf(input),rotationAxis=RotationAxis.X,quarterTurns=1)
            val expected=BuildingAssembly.newNode(kind,"expected").copy(inputs=listOf(first.id),rotationAxis=RotationAxis.Y,quarterTurns=1)
            val g=graph(source,segment,steps,actual,first,expected)
            assertEquals(eval(g).preview(expected.id),eval(g).preview(actual.id))
            val restored=Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java)
            assertEquals(eval(g).preview(actual.id),eval(restored).preview(actual.id))
            assertEquals(eval(g).preview(input),eval(g.replace(steps.copy(rotations=emptyList()))).preview(actual.id))
            assertFails { g.connect(actual.id,"rotation",source.id) }
        }
    }

    @Test fun `group instances remap both typed value references and removal disconnects them`() {
        val rotation=BuildingNode("r",BuildingKind.ROTATION,rotations=emptyList())
        val mirror=BuildingNode("m",BuildingKind.MIRRORING,mirrors=listOf(CopycatMirror()))
        val rotate=BuildingAssembly.newNode(BuildingKind.SEGMENT_ROTATE,"rotate").copy(inputs=listOf("wall"),rotation="r")
        val reflect=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"reflect").copy(inputs=listOf("rotate"),mirroring="m")
        val g=graph(rotation,mirror,rotate,reflect).copy(nodeGroups=listOf(GraphGroup("group","Transformations",listOf("r","m","rotate","reflect"))))
        val instance=GraphGroups.instantiate(g,"group")
        BuildingGraphCompiler.structure(instance)
        val copied=instance.nodeGroups.last().nodes.map { id -> instance.nodes.single { it.id==id } }
        assertEquals(copied[0].id,copied[2].rotation)
        assertEquals(copied[1].id,copied[3].mirroring)
        assertEquals(copied[2].id,copied[3].inputs.single())
        assertNull(g.remove("r").nodes.single { it.id=="rotate" }.rotation)
        assertNull(g.remove("m").nodes.single { it.id=="reflect" }.mirroring)
        assertFails { BuildingGraphCompiler.structure(g.replace(mirror.copy(mirrors=List(65) { CopycatMirror() }))) }
    }

    @Test fun `union offsets move real cells and preserve relative displacement through rotation`() {
        val a=BuildingNode("a",BuildingKind.SEGMENT,module="",block="minecraft:stone",length=1,height=1)
        val b=a.copy(id="b")
        val union=BuildingNode("union",BuildingKind.SEGMENT_UNION,inputs=listOf("a","b"),offsets=listOf(P(),P(2,1,-1)))
        val rotate=BuildingAssembly.newNode(BuildingKind.SEGMENT_ROTATE,"rotate").copy(inputs=listOf("union"),quarterTurns=1)
        val g=graph(a,b,union,rotate)
        val value=eval(g).segment("union")
        assertEquals(P(3,2,2),P(value.length,value.height,value.depth))
        assertEquals(setOf(P(0,0,1),P(2,1,0)),value.cells.map { it.cell.pos }.toSet())
        assertEquals(2,eval(g).preview("rotate").placements.size)
        assertEquals(2,eval(g).preview("union").placements.size)
    }

    @Test fun `explicit empty Copycat exports and contributes no blocks or material`() {
        val empty=NodeGraph(nodes=listOf(GraphNode("empty",NodeKind.SAVED,savedCopycat=SavedCopycat(parts=emptyList())),GraphNode("out",NodeKind.OUTPUT,listOf("empty"))))
        val form=Geometry.validate(Form(graph=empty))
        assertTrue(form.parts.isEmpty())
        val g=graph(BuildingNode("empty_copy",BuildingKind.COPYCAT,form="empty@1"))
        val plan=BuildingGraphCompiler.Evaluation(g,Building().palette,mapOf("empty@1" to form),emptyMap()).preview("empty_copy")
        assertTrue(plan.placements.isEmpty())
        assertEquals(0,plan.raw)
        assertTrue(plan.materials.isEmpty())
        assertFails { GraphCompiler.compile(NodeGraph()) }
    }
}
