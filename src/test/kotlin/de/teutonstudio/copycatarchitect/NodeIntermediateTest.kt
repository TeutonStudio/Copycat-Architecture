package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class NodeIntermediateTest {
    private val full=GraphNode("full",part=Part("copycats:copycat_block"))
    private val slab=GraphNode("slab",part=Part("copycats:copycat_slab"))
    private fun graph(kind:NodeKind)=NodeGraph(nodes=listOf(full,slab,GraphNode("op",kind,listOf("full","slab")),GraphNode("result",NodeKind.OUTPUT,listOf("op"))))
    private fun solid(g:NodeGraph,id:String)=Geometry.solid(GraphCompiler.intermediate(g,id))
    @Test fun `sources retain their full geometry before downstream cutters`() {
        val g=graph(NodeKind.DIFFERENCE)
        assertEquals(4096,solid(g,"full").count());assertEquals(2048,solid(g,"slab").count())
        assertEquals(2048,solid(g,"op").count())
        assertFalse(solid(g,"op").has(1,1,1));assertTrue(solid(g,"slab").has(1,1,1))
        assertEquals(GraphCompiler.compile(g),GraphCompiler.intermediate(g,"result"))
    }
    @Test fun `every boolean operation previews its own result`() {
        for((kind,count) in mapOf(NodeKind.UNION to 4096,NodeKind.INTERSECTION to 2048,NodeKind.DIFFERENCE to 2048,NodeKind.SYMMETRIC_DIFFERENCE to 2048)) {
            val g=graph(kind)
            assertEquals(count,solid(g,"op").count(),kind.name)
            assertEquals(4096,solid(g,"full").count())
        }
    }
    @Test fun `unfinished output and unrelated drafts do not hide connected upstream previews`() {
        val base=graph(NodeKind.UNION)
        val g=base.copy(nodes=base.nodes.dropLast(1)+GraphNode("draft",NodeKind.DIFFERENCE,listOf("full",""))+GraphNode("result",NodeKind.OUTPUT,listOf("")))
        assertEquals(4096,solid(g,"full").count());assertEquals(4096,solid(g,"op").count())
        assertFails { GraphCompiler.intermediate(g,"draft") };assertFails { GraphCompiler.compile(g) }
    }
    @Test fun `empty intermediate geometry is distinct from a missing input and final output remains invalid`() {
        val g=graph(NodeKind.DIFFERENCE).replace(GraphNode("op",NodeKind.DIFFERENCE,listOf("full","full")))
        assertTrue(GraphCompiler.intermediate(g,"op").isEmpty())
        assertTrue(GraphCompiler.intermediate(g,"result").isEmpty())
        assertFails { GraphCompiler.compile(g) }
        assertFails { GraphCompiler.intermediate(g.replace(GraphNode("op",NodeKind.DIFFERENCE,listOf("full",""))),"op") }
    }
    @Test fun `rotation preview uses the connected rotation value without changing its source`() {
        val g=NodeGraph(nodes=listOf(slab,GraphNode("angle",NodeKind.ROTATION,rotationAxis=RotationAxis.Z,quarterTurns=1),
            GraphNode("rotate",NodeKind.ROTATE,listOf("slab","angle")),GraphNode("result",NodeKind.OUTPUT,listOf("rotate"))))
        assertEquals(Solid.part(slab.part).key(),solid(g,"slab").key())
        assertEquals(Solid.part(slab.part).rotate(RotationAxis.Z,1).key(),solid(g,"rotate").key())
        assertFails { GraphCompiler.intermediate(g,"angle") }
    }
    @Test fun `material overrides and cache entries belong to each node and graph revision`() {
        val groups=DEFAULT_GROUPS+MaterialGroup("cap","Cap","trim",1)
        val g=graph(NodeKind.UNION).replace(GraphNode("op",NodeKind.UNION,listOf("full","slab"),materialMode=MaterialMode.GROUPED,materialGroup="cap"))
        assertEquals(setOf("body"),GraphCompiler.intermediate(g,"full",groups).map { it.group }.toSet())
        assertEquals(setOf("cap"),GraphCompiler.intermediate(g,"op",groups).map { it.group }.toSet())
        val updated=g.replace(full.copy(part=slab.part))
        assertEquals(2048,Geometry.solid(GraphCompiler.intermediate(updated,"full",groups)).count())
        assertEquals(4096,Geometry.solid(GraphCompiler.intermediate(g,"full",groups)).count())
        assertEquals(full,g.nodes.first())
    }
}
