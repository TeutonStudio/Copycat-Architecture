package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.client.*
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GroupGraphInterfaceTest {
    private fun data():KilaGraphData {
        val face=GraphGroupInterface(listOf(GraphGroupInput("form","Form","shape",listOf(GraphGroupTarget(0,"0")),y=20)),
            listOf(GraphGroupOutput("result","Form","shape",0)))
        return KilaGraphData(listOf(
            KilaNode("source","Source",0,0,emptyList()),
            KilaNode("union","Union",250,0,listOf("A","B"),inputTypes=listOf("shape","shape")),
            KilaNode("external","Result",600,0,listOf("Form"),output=false,inputTypes=listOf("shape"))),
            listOf(KilaLink("source","union",0),KilaLink("union","external",0)),
            listOf(GraphGroup("module","Module",listOf("union"),graphInterface=face,inputConnections=mapOf("form" to "source"))))
    }

    @Test fun `opening a group shows only its nodes and named typed terminals`() {
        val original=data();val projection=KilaGroupGraphProjection(original,"module")
        assertEquals(setOf("union","@input:form","@output:result"),projection.display.nodes.map { it.id }.toSet())
        assertEquals("shape",projection.display.nodes.single { it.id=="@input:form" }.outputType)
        assertEquals(listOf(KilaLink("@input:form","union",0),KilaLink("union","@output:result",0)),projection.display.links)
        assertEquals(original,projection.restore(projection.display))
    }

    @Test fun `wiring a named input to multiple members fans out an instance connection`() {
        val original=data();val projection=KilaGroupGraphProjection(original,"module")
        val restored=projection.restore(projection.display.copy(links=projection.display.links+KilaLink("@input:form","union",1)))
        assertTrue(KilaLink("source","union",0) in restored.links)
        assertTrue(KilaLink("source","union",1) in restored.links)
        assertEquals(listOf(GraphGroupTarget(0,"0"),GraphGroupTarget(0,"1")),restored.nodeGroups.single().graphInterface!!.inputs.single().targets)
        val parent=KilaGroupProjection(restored,emptySet())
        assertEquals(1,parent.display.nodes.single { it.id=="@group:module" }.inputs.size)
        assertEquals(restored,parent.restore(parent.display))
    }

    @Test fun `unused named input still retains its independent instance connection`() {
        val original=data();val group=original.nodeGroups.single()
        val changed=GroupGraphInterfaces.withInterface(original,group.copy(graphInterface=group.graphInterface!!.copy(inputs=listOf(group.graphInterface.inputs.single().copy(targets=emptyList())))))
        val projection=KilaGroupProjection(changed,emptySet())
        assertTrue(KilaLink("source","@group:module",0) in projection.display.links)
        assertEquals(changed,projection.restore(projection.display))
    }

    @Test fun `wrong input value types cannot enter a group definition`() {
        val original=data();val group=original.nodeGroups.single()
        val changed=original.copy(nodeGroups=listOf(group.copy(graphInterface=group.graphInterface!!.copy(inputs=listOf(group.graphInterface.inputs.single().copy(type="rotation"))))))
        val projection=KilaGroupGraphProjection(changed,"module")
        assertFails { projection.restore(projection.display) }
    }

    @Test fun `changing a connected terminal type releases old instance bindings`() {
        val original=data();val group=original.nodeGroups.single()
        val face=group.graphInterface!!
        val changed=GroupGraphInterfaces.withInterface(original,group.copy(graphInterface=face.copy(inputs=face.inputs.map { it.copy(type="rotation",targets=emptyList()) })))
        assertTrue(changed.nodeGroups.single().inputConnections.isEmpty())
        assertFalse(changed.links.any { it.target=="union" && it.slot==0 })
        assertEquals("rotation",KilaGroupProjection(changed,emptySet()).display.nodes.single { it.id=="@group:module" }.inputTypes.single())
    }

    @Test fun `interfaces survive JSON and instantiate matching typed ports`() {
        val graph=NodeGraph(nodes=listOf(GraphNode("source",part=Part("copycats:copycat_block")),GraphNode("union",NodeKind.UNION,listOf("source","")),GraphNode("result",NodeKind.OUTPUT,listOf("union"))),
            nodeGroups=data().nodeGroups)
        val restored=Gson().fromJson(Gson().toJson(graph),NodeGraph::class.java)
        assertEquals(graph,restored)
        val instanced=GraphGroups.instantiate(restored,"module")
        GraphCompiler.structure(instanced,DEFAULT_GROUPS)
        assertEquals(instanced.nodeGroups[0].graphInterface,instanced.nodeGroups[1].graphInterface)
        val added=GraphNode("second",part=Part("copycats:copycat_slab"))
        val grown=GraphGroups.addMembers(instanced.copy(nodes=instanced.nodes+added),"module",listOf(added.id))
        GraphCompiler.structure(grown,DEFAULT_GROUPS)
        assertTrue(grown.nodeGroups.all { it.nodes.size==2 })
    }

    @Test fun `group graph building result can feed the parent building result`() {
        val starter=BuildingGraph.starter()
        val child=starter.resultNode().copy(id="child")
        val face=GraphGroupInterface(listOf(GraphGroupInput("storey","Geschoss","STOREY",listOf(GraphGroupTarget(0,"level:0")))),
            listOf(GraphGroupOutput("house","Haus","BUILDING",0)))
        val group=GraphGroup("house","Haus",listOf("child"),graphInterface=face,inputConnections=mapOf("storey" to "storey"))
        val graph=starter.copy(nodes=starter.nodes.map { if(it.kind==BuildingKind.BUILDING) it.copy(levels=emptyMap(),roof="",inputs=listOf("child")) else it }+child,nodeGroups=listOf(group))
        BuildingGraphCompiler.structure(graph)
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        val actual=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap()).building().plan
        val expected=BuildingGraphCompiler.Evaluation(starter,Building().palette,forms,emptyMap()).building().plan
        assertEquals(expected,actual)
        val instanced=GraphGroups.instantiate(graph,"house")
        BuildingGraphCompiler.structure(instanced)
        assertEquals(3,instanced.nodes.count { it.kind==BuildingKind.BUILDING })
    }

    @Test fun `every building value kind can be exported by a group`() {
        for(kind in BuildingKind.entries.filter { it!=BuildingKind.BUILDING }) {
            val node=BuildingAssembly.newNode(kind,"value")
            val base=BuildingGraph.starter()
            val group=GraphGroup("module","Module",listOf(node.id),graphInterface=GraphGroupInterface(outputs=listOf(GraphGroupOutput("value","Value",buildingGraphType(kind),0))))
            BuildingGraphCompiler.structure(base.copy(nodes=base.nodes+node,nodeGroups=listOf(group)))
        }
    }
}
