package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GraphSelectionEditsTest {
    private val graph=NodeGraph(nodes=listOf(
        GraphNode("source",part=Part("copycats:copycat_block"),x=10,y=30),
        GraphNode("external",part=Part("copycats:copycat_slab"),x=10,y=210),
        GraphNode("union",NodeKind.UNION,listOf("source","external"),x=260,y=100),
        GraphNode("result",NodeKind.OUTPUT,listOf("union"),x=520,y=30)))
    @Test fun `duplicate preserves internal and external wires and never copies the result`() {
        val (changed,selection)=GraphSelectionEdits.duplicate(graph,setOf("source","union","result"))
        GraphCompiler.structure(changed,DEFAULT_GROUPS)
        assertEquals(2,selection.size)
        val copies=changed.nodes.filter { it.id in selection }
        val source=copies.single { it.kind==NodeKind.INPUT }
        val union=copies.single { it.kind==NodeKind.UNION }
        assertEquals(listOf(source.id,"external"),union.inputs)
        assertEquals(42,source.x);assertEquals(62,source.y)
        assertEquals(graph.nodes,changed.nodes.take(graph.nodes.size))
        assertEquals(GraphCompiler.compile(graph),GraphCompiler.compile(changed))
    }
    @Test fun `delete protects result and disconnects all surviving references`() {
        val changed=GraphSelectionEdits.remove(graph,setOf("source","union","result"))
        GraphCompiler.structure(changed,DEFAULT_GROUPS)
        assertEquals(setOf("external","result"),changed.nodes.map { it.id }.toSet())
        assertEquals(listOf(""),changed.nodes.single { it.id=="result" }.inputs)
        assertEquals(graph,GraphSelectionEdits.remove(graph,setOf("result","missing")))
    }
    @Test fun `alignment changes only selected positions and preserves compiled geometry`() {
        for(horizontal in listOf(false,true)) {
            val changed=GraphSelectionEdits.align(graph,setOf("source","union"),horizontal)
            GraphCompiler.structure(changed,DEFAULT_GROUPS)
            val selected=changed.nodes.filter { it.id in setOf("source","union") }
            assertEquals(1,selected.map { if(horizontal) it.y else it.x }.distinct().size)
            assertEquals(graph.nodes.last(),changed.nodes.last())
            assertEquals(GraphCompiler.compile(graph),GraphCompiler.compile(changed))
        }
    }
    @Test fun `group assignment preserves connections and changes only supported selected nodes`() {
        val groups=DEFAULT_GROUPS+MaterialGroup("cap","Cap","trim",1)
        val changed=GraphSelectionEdits.assignGroup(graph,setOf("source","union","result"),"cap")
        GraphCompiler.structure(changed,groups)
        assertEquals("cap",changed.nodes.first().part.group)
        assertEquals(MaterialMode.GROUPED,changed.nodes[2].materialMode)
        assertEquals("cap",changed.nodes[2].materialGroup)
        assertEquals(graph.nodes[2].inputs,changed.nodes[2].inputs)
        assertEquals(graph.nodes[1],changed.nodes[1]);assertEquals(graph.nodes[3],changed.nodes[3])
    }
}
