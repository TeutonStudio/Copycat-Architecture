package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.*
import de.teutonstudio.copycatarchitect.domain.GraphGroup
import org.junit.jupiter.api.Test
import kotlin.test.*

class KilaGroupProjectionTest {
    @Test fun `inspector selection distinguishes group headers members and multiple selection`() {
        for(expanded in listOf(emptySet(),setOf("module"))) {
            val projection=KilaGroupProjection(fixture(),expanded)
            val header=projection.groupViewId("module")
            assertEquals(header,projection.inspectorSelection(setOf(header)))
            assertEquals(group.nodes.toSet(),projection.actualSelection(setOf(header)))
            assertEquals("angle",projection.inspectorSelection(setOf("angle")))
            assertEquals("",projection.inspectorSelection(setOf("angle","cap")))
            assertEquals("",projection.inspectorSelection(setOf(header,"external")))
            assertEquals("",projection.inspectorSelection(setOf("missing")))
            assertEquals("",projection.inspectorSelection(emptySet()))
        }
    }
    private val group=GraphGroup("module","Module",listOf("transform","angle","cap"))
    private fun fixture()=KilaGraphData(listOf(
        KilaNode("external","External",-300,20,emptyList()),
        KilaNode("alternative","Alternative",-300,220,emptyList()),
        KilaNode("transform","Transform",100,120,listOf("Shape","Angle"),inputTypes=listOf("shape","rotation")),
        KilaNode("angle","Angle",220,240,emptyList(),outputType="rotation"),
        KilaNode("cap","Cap",300,160,emptyList()),
        KilaNode("operation","Operation",600,20,listOf("Shape","Angle","Cap"),inputTypes=listOf("shape","rotation","shape")),
        KilaNode("spare","Spare",600,220,listOf("Shape"))),listOf(
        KilaLink("external","transform",0),KilaLink("angle","transform",1),
        KilaLink("transform","operation",0),KilaLink("angle","operation",1),KilaLink("cap","operation",2)),listOf(group))

    @Test fun `collapsed module exposes typed boundary sockets and preserves the exact round trip`() {
        val original=fixture()
        val projection=KilaGroupProjection(original,emptySet())
        val proxy=projection.display.nodes.single { it.id==projection.groupViewId(group.id) }
        assertEquals(setOf("external","alternative","operation","spare",proxy.id),projection.display.nodes.map { it.id }.toSet())
        assertEquals(100 to 120,proxy.x to proxy.y)
        assertFalse(proxy.output)
        assertEquals(listOf("Transform · Shape"),proxy.inputs)
        assertEquals(listOf("shape"),proxy.inputTypes)
        assertEquals(listOf(KilaOutput("Transform","shape"),KilaOutput("Angle","rotation"),KilaOutput("Cap","shape")),proxy.outputs)
        assertEquals(listOf(
            KilaLink("external",proxy.id,0),KilaLink(proxy.id,"operation",0,0),
            KilaLink(proxy.id,"operation",1,1),KilaLink(proxy.id,"operation",2,2)),projection.display.links)
        assertEquals("transform" to 0,projection.inputRef(proxy.id,0))
        assertEquals("angle",projection.outputRef(proxy.id,1))
        assertNull(projection.inputRef(proxy.id,1))
        assertNull(projection.outputRef(proxy.id,3))
        assertEquals(original,projection.restore(projection.display))
    }

    @Test fun `rewiring exposed sockets resolves each proxy output without losing internal wires`() {
        val original=fixture()
        val projection=KilaGroupProjection(original,emptySet())
        val id=projection.groupViewId(group.id)
        val changed=projection.display.copy(links=projection.display.links.filterNot {
            it.target==id || it.target=="operation" && it.slot==2
        }+listOf(KilaLink("alternative",id,0),KilaLink(id,"spare",0,2)))
        val restored=projection.restore(changed)
        assertContains(restored.links,KilaLink("angle","transform",1))
        assertContains(restored.links,KilaLink("alternative","transform",0))
        assertContains(restored.links,KilaLink("angle","operation",1))
        assertContains(restored.links,KilaLink("cap","spare",0))
        assertFalse(KilaLink("external","transform",0) in restored.links)
        assertFalse(KilaLink("cap","operation",2) in restored.links)
        assertEquals(original.nodes,restored.nodes)
        assertEquals(original.nodeGroups,restored.nodeGroups)
    }

    @Test fun `deleting all visible wires preserves only the folded internal graph`() {
        val projection=KilaGroupProjection(fixture(),emptySet())
        val restored=projection.restore(projection.display.copy(links=emptyList()))
        assertEquals(listOf(KilaLink("angle","transform",1)),restored.links)
        assertEquals(fixture().nodes,restored.nodes)
    }

    @Test fun `moving a folded group clamps a common translation at both coordinate bounds`() {
        val original=fixture()
        val projection=KilaGroupProjection(original,emptySet())
        val id=projection.groupViewId(group.id)
        val changed=projection.display.copy(nodes=projection.display.nodes.map { if(it.id==id) it.copy(x=20000,y=-20000) else it })
        val restored=projection.restore(changed)
        val moved=restored.nodes.filter { it.id in group.nodes }
        assertEquals(8192,moved.maxOf { it.x })
        assertEquals(-8192,moved.minOf { it.y })
        val offsets=moved.map { node ->
            val old=original.nodes.single { it.id==node.id }
            node.x-old.x to node.y-old.y
        }
        assertEquals(1,offsets.distinct().size)
        assertEquals(original.nodes.filter { it.id !in group.nodes },restored.nodes.filter { it.id !in group.nodes })
        assertEquals(original.links,restored.links)
    }

    @Test fun `groups at the complete saved bounds retain their positions and cannot move beyond them`() {
        val original=fixture().let { graph -> graph.copy(nodes=graph.nodes.map {
            when(it.id) {
                "transform" -> it.copy(x=-8192,y=-8192)
                "angle" -> it.copy(x=8192,y=8192)
                else -> it
            }
        }) }
        listOf(emptySet(),setOf(group.id)).forEach { expanded ->
            val projection=KilaGroupProjection(original,expanded)
            assertEquals(original,projection.restore(projection.display))
            val id=projection.groupViewId(group.id)
            val moved=projection.display.copy(nodes=projection.display.nodes.map { if(it.id==id) it.copy(x=it.x+500,y=it.y-500) else it })
            assertEquals(original,projection.restore(moved))
        }
    }

    @Test fun `moving expanded headers and selected members together applies the translation once`() {
        val original=fixture()
        val projection=KilaGroupProjection(original,setOf(group.id))
        val id=projection.groupViewId(group.id)
        listOf(group.nodes.toSet(),setOf("angle")).forEach { movedMembers ->
            val moved=projection.display.copy(nodes=projection.display.nodes.map {
                if(it.id==id || it.id in movedMembers) it.copy(x=it.x+50,y=it.y-30) else it
            })
            val restored=projection.restore(moved)
            original.nodes.forEach { old ->
                assertEquals(if(old.id in group.nodes) old.copy(x=old.x+50,y=old.y-30) else old,
                    restored.nodes.single { it.id==old.id })
            }
        }
    }

    @Test fun `expanded headers translate all members and convert selections to document nodes`() {
        val original=fixture()
        val projection=KilaGroupProjection(original,setOf(group.id))
        val id=projection.groupViewId(group.id)
        val header=projection.display.nodes.single { it.id==id }
        assertEquals(100 to 48,header.x to header.y)
        assertTrue(header.inputs.isEmpty() && header.outputs.isEmpty() && !header.output)
        assertEquals(original.links,projection.display.links)
        assertEquals(group,projection.groupForView(id))
        assertEquals(group.nodes.toSet()+"external",projection.actualSelection(setOf(id,"external","missing")))
        assertEquals("angle",projection.viewIdForNode("angle"))
        assertNull(projection.inputRef(id,0))
        assertNull(projection.outputRef(id,0))
        val changed=projection.display.copy(nodes=projection.display.nodes.map { if(it.id==id) it.copy(x=it.x+40,y=it.y+20) else it })
        val restored=projection.restore(changed)
        original.nodes.forEach { old ->
            val node=restored.nodes.single { it.id==old.id }
            assertEquals(if(old.id in group.nodes) old.copy(x=old.x+40,y=old.y+20) else old,node)
        }
        assertEquals(original,projection.restore(projection.display))
        val folded=KilaGroupProjection(original,emptySet())
        assertEquals(id,folded.viewIdForNode("angle"))
        assertEquals(group.nodes.toSet(),folded.actualSelection(setOf(id)))
    }

    @Test fun `deleting the group proxy deletes its complete instance and dependent wires`() {
        listOf(emptySet(),setOf(group.id)).forEach { expanded ->
            val projection=KilaGroupProjection(fixture(),expanded)
            val id=projection.groupViewId(group.id)
            val restored=projection.restore(projection.display.copy(nodes=projection.display.nodes.filterNot { it.id==id }))
            assertEquals(fixture().nodes.filter { it.id !in group.nodes },restored.nodes)
            assertTrue(restored.links.isEmpty())
            assertTrue(restored.nodeGroups.isEmpty())
        }
    }

    @Test fun `ungrouped sockets and explicit source slots survive the view projection`() {
        val original=KilaGraphData(listOf(
            KilaNode("multi","Multi",0,0,emptyList(),output=false,outputs=listOf(KilaOutput("Shape","shape"),KilaOutput("Angle","rotation"))),
            KilaNode("target","Target",240,0,listOf("Angle"),output=false,inputTypes=listOf("rotation"))),listOf(KilaLink("multi","target",0,1)))
        val projection=KilaGroupProjection(original,emptySet())
        assertEquals(original,projection.display)
        assertEquals(original,projection.restore(projection.display))
        assertEquals("multi",projection.outputRef("multi",1))
        assertNull(projection.outputRef("target",0))
        assertEquals("target" to 0,projection.inputRef("target",0))
        assertNull(projection.inputRef("missing",0))
    }
}
