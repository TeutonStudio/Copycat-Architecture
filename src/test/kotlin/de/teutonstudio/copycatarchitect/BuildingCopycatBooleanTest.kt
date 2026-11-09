package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.client.BuildingInsertionCategories
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingCopycatBooleanTest {
    private val full=Part("copycats:copycat_block")
    private val slab=Part("copycats:copycat_slab")
    private val forms=mapOf("full@1" to Form(parts=listOf(full)),"slab@1" to Form(parts=listOf(slab)))
    private val a=BuildingNode("a",BuildingKind.COPYCAT,form="full@1")
    private val b=BuildingNode("b",BuildingKind.COPYCAT,form="slab@1")
    private val kinds=listOf(BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,
        BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE)
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+listOf(a,b)+nodes) }
    private fun operation(kind:BuildingKind,inputs:List<String> = listOf("a","b"))=BuildingAssembly.newNode(kind,"op").copy(inputs=inputs)
    private fun eval(graph:BuildingGraph,definitions:Map<String,Form> = forms)=BuildingGraphCompiler.Evaluation(graph,Building().palette,definitions,emptyMap())
    private fun volume(plan:Plan)=plan.placements.sumOf { Geometry.solid(it.parts).count() }

    @Test fun `four building Copycat operations match shape operations and retain their type after JSON export`() {
        for(kind in kinds) {
            val op=operation(kind);val graph=graph(op)
            val shapeOperation=NodeKind.valueOf(kind.name.removePrefix("COPYCAT_"))
            val shape=NodeGraph(nodes=listOf(GraphNode("a",part=full),GraphNode("b",part=slab),
                GraphNode("op",shapeOperation,listOf("a","b")),GraphNode("result",NodeKind.OUTPUT,listOf("op"))))
            val expected=Geometry.exact(GraphCompiler.compile(shape))
            val actual=eval(graph).preview(op.id)
            assertTrue(expected.equivalent(Geometry.exact(actual.placements.single().parts)),kind.name)
            assertEquals(if(kind==BuildingKind.COPYCAT_UNION) 4096 else 2048,volume(actual),kind.name)
            val restored=Gson().fromJson(Gson().toJson(graph),BuildingGraph::class.java)
            assertEquals(kind,restored.nodes.last().kind)
            assertEquals(actual,eval(restored).preview(op.id))
        }
        assertEquals(0,volume(eval(graph(operation(BuildingKind.COPYCAT_DIFFERENCE,listOf("b","a")))).preview("op")))
    }

    @Test fun `new operators have two typed sockets in explicit and legacy graphs and reject bad wiring`() {
        for(kind in kinds+listOf(BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,
            BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE)) {
            for(explicit in listOf(false,true)) {
                val node=GraphModels.newBuilding(kind,explicit).copy(id="op")
                assertEquals(listOf("",""),node.inputs,"$kind/$explicit")
                BuildingGraphCompiler.structure(graph(node))
            }
        }
        for(kind in kinds) {
            assertEquals("node_category_copycats",BuildingInsertionCategories.key(kind))
            val op=operation(kind);val graph=graph(op)
            val ports=BuildingPorts.of(op,true)
            assertEquals(2,ports.size)
            assertTrue(ports.all { port -> kinds.all { it in port.accepts } })
            assertFails { graph.connect(op.id,"in:0","wall") }
            assertFails { graph.connect(op.id,"in:0",op.id) }
            assertFails { BuildingGraphCompiler.structure(graph.replace(op.copy(inputs=listOf("a")))) }
            assertFails { eval(graph.replace(op.copy(inputs=listOf("a","")))).preview(op.id) }
        }
        assertFails { BuildingGraphCompiler.structure(graph(operation(BuildingKind.COPYCAT_DIFFERENCE,listOf("a","b","a")))) }
        val g=graph(operation(BuildingKind.COPYCAT_UNION))
        val wall=g.nodes.single { it.id=="wall" }.copy(module="",inputs=listOf("op"))
        BuildingGraphCompiler.structure(g.replace(wall))
        for(kind in BuildingKind.entries) {
            val node=BuildingAssembly.newNode(kind,"consumer")
            for(port in BuildingPorts.of(node,true)) if(BuildingKind.COPYCAT in port.accepts)
                assertTrue(kinds.all { it in port.accepts },"Missing Copycat result kind at $kind/${port.key}")
        }
    }

    @Test fun `Copycat booleans compose with rotations mirrors and repeated segment sources`() {
        val op=operation(BuildingKind.COPYCAT_DIFFERENCE)
        val rotation=BuildingAssembly.newNode(BuildingKind.COPYCAT_ROTATE,"rotation").copy(inputs=listOf(op.id),rotationAxis=RotationAxis.Z)
        val mirror=BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,"mirror").copy(inputs=listOf(rotation.id),mirror=CopycatMirror(CopycatMirrorPlane.YZ))
        val segment=BuildingNode("segment",BuildingKind.SEGMENT,module="",length=2,height=1,inputs=listOf(mirror.id))
        val evaluation=eval(graph(op,rotation,mirror,segment))
        val expected=Geometry.exact(evaluation.preview(op.id).placements.single().parts
            .map { PartRotations.rotate(it,RotationAxis.Z,1)!! }.map { PartRotations.mirror(it,CopycatMirrorPlane.YZ) })
        assertTrue(expected.equivalent(Geometry.exact(evaluation.preview(mirror.id).placements.single().parts)))
        val placed=evaluation.preview(segment.id).placements
        assertEquals(setOf(P(),P(1,0,0)),placed.map { it.pos }.toSet())
        assertTrue(placed.all { expected.equivalent(Geometry.exact(it.parts)) })
        assertEquals(forms.size+1,evaluation.forms.size,"Repeated placements reuse the derived Copycat")
        assertEquals(1,evaluation.graph.withCurrentSchema().schema)
    }

    @Test fun `empty boolean results remain valid through transforms segments and odd parity XOR`() {
        val op=operation(BuildingKind.COPYCAT_DIFFERENCE,listOf("a","a"))
        val rotation=BuildingAssembly.newNode(BuildingKind.COPYCAT_ROTATE,"rotation").copy(inputs=listOf("op"))
        val mirror=BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,"mirror").copy(inputs=listOf("rotation"))
        val segment=BuildingNode("segment",BuildingKind.SEGMENT,module="",length=1,height=1,inputs=listOf("mirror"))
        val join=operation(BuildingKind.COPYCAT_UNION,listOf("mirror","b")).copy(id="join")
        val evaluation=eval(graph(op,rotation,mirror,segment,join))
        for(id in listOf("op","rotation","mirror","segment")) {
            assertEquals(0,volume(evaluation.preview(id)),id)
            assertEquals(listOf(P()),evaluation.preview(id).clear,id)
        }
        assertEquals(2048,volume(evaluation.preview("join")))
        assertEquals(4096,volume(eval(graph(operation(BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE,listOf("a","a","a")))).preview("op")))
    }

    @Test fun `grouped transform input remains grouped when union passes it through beside air`() {
        val air=a.copy(id="air",block="minecraft:air")
        val local=b.copy(surfaces=mapOf("body" to "minecraft:glass"))
        val grouped=BuildingAssembly.newNode(BuildingKind.COPYCAT_ROTATE,"grouped").copy(inputs=listOf("b"),materialMode=MaterialMode.GROUPED,role="frame")
        val op=operation(BuildingKind.COPYCAT_UNION,listOf("air","grouped"))
        val value=eval(graph(air,grouped,op).replace(local)).preview("op")
        assertEquals(setOf(Building().palette.getValue("frame")),value.placements.single().surfaces.values.toSet())
    }

    @Test fun `operations preserve material priority when a reused finish follows a new background`() {
        val red="minecraft:red_concrete";val green="minecraft:green_concrete"
        val redGroup=MaterialGroup("red","Red",priority=1)
        val accent=Form(groups=listOf(redGroup),parts=listOf(Part("copycats:copycat_byte",group="red",offset=P(8,8,8))))
        val base=Form(groups=listOf(MaterialGroup("green","Green",priority=0),redGroup),parts=listOf(full.copy(group="green"),slab.copy(group="red")))
        val sources=mapOf("accent@1" to accent,"base@1" to base)
        val graph=graph(operation(BuildingKind.COPYCAT_UNION))
            .replace(a.copy(form="accent@1",surfaces=mapOf("red" to red)))
            .replace(b.copy(form="base@1",surfaces=mapOf("red" to red,"green" to green)))
        val result=eval(graph,sources).preview("op").placements.single()
        val owned=Geometry.resolve(result.parts,result.groups).owned
        val byMaterial=result.groups.groupBy { result.surfaces.getValue(it.id) }
            .mapValues { (_,groups) -> groups.sumOf { owned[it.id]?.count() ?: 0 } }
        assertEquals(mapOf(red to 2560,green to 1536),byMaterial)
    }

    @Test fun `rational clipping and source receipts split into materials survive building operations`() {
        val groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","glass",1))
        val split=Form(groups=groups,graph=NodeGraph(nodes=listOf(GraphNode("source",part=full),GraphNode("mask",part=slab),
            GraphNode("lower",NodeKind.INTERSECTION,listOf("source","mask"),materialMode=MaterialMode.GROUPED,materialGroup="cap"),
            GraphNode("upper",NodeKind.DIFFERENCE,listOf("source","mask")),
            GraphNode("join",NodeKind.UNION,listOf("upper","lower")),GraphNode("result",NodeKind.OUTPUT,listOf("join")))))
        val cutter=PartRotations.mirror(Part("copycats:copycat_byte",offset=P(4,4,4)),CopycatMirrorPlane.BODY_PPP)
        val exact=Form(graph=NodeGraph(nodes=listOf(GraphNode("saved",NodeKind.SAVED,savedCopycat=SavedCopycat(parts=listOf(cutter))),
            GraphNode("result",NodeKind.OUTPUT,listOf("saved")))))
        val graph=graph(operation(BuildingKind.COPYCAT_DIFFERENCE)).replace(a.copy(form="split@1")).replace(b.copy(form="exact@1"))
        val evaluation=eval(graph,mapOf("split@1" to split,"exact@1" to exact))
        val before=evaluation.preview("a");val after=evaluation.preview("op")
        val parts=after.placements.single().parts
        val expected=Geometry.exact(full).minus(Geometry.exact(cutter))
        assertTrue(expected.equivalent(Geometry.exact(parts)))
        assertTrue(parts.any(ExactGeometry::usesExact))
        assertEquals((expected.solid().count()+15)/16,after.raw)
        assertEquals(1,parts.map { it.recipeSource }.distinct().size)
        assertTrue(parts.all { it.recipeSource.isNotEmpty() && it.source==full.source })
        assertEquals(before.placements.single().surfaces.values.toSet(),after.placements.single().surfaces.values.toSet())
    }

    @Test fun `normal block differences cannot silently turn a partial native block into a Copycat`() {
        val op=operation(BuildingKind.COPYCAT_DIFFERENCE)
        val graph=graph(op).replace(a.copy(block="minecraft:stone"))
        assertFailsWith<IllegalArgumentException> { eval(graph).preview("op") }
        assertEquals(0,volume(eval(graph.replace(op.copy(inputs=listOf("a","a")))).preview("op")))
        val union=graph.replace(op.copy(kind=BuildingKind.COPYCAT_UNION,inputs=listOf("a","a")))
        val native=eval(union).preview("op").placements.single()
        assertEquals("minecraft:stone",native.block)
        assertTrue(native.parts.isEmpty())
    }
}
