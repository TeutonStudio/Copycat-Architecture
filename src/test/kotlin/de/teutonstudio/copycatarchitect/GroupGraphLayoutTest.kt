package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.client.*
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GroupGraphLayoutTest {
    private fun graph()=NodeGraph(nodes=listOf(
        GraphNode("source",part=Part("copycats:copycat_block"),x=180,y=100),
        GraphNode("union",NodeKind.UNION,listOf("source",""),x=400,y=160),
        GraphNode("result",NodeKind.OUTPUT,listOf("union"),x=700,y=100)),
        nodeGroups=listOf(GraphGroup("module","Module",listOf("source","union"),graphInterface=GraphGroupInterface(
            outputs=listOf(GraphGroupOutput("result","Ergebnis","shape",1,x=650,y=100))))))
    private fun data(graph:NodeGraph)=KilaGraphData(graph.nodes.map { n ->
        KilaNode(n.id,n.id,n.x,n.y,n.inputs.indices.map(Int::toString),output=n.kind!=NodeKind.OUTPUT,
            inputTypes=n.inputs.map { "shape" })
    },graph.nodes.flatMap { n -> n.inputs.mapIndexedNotNull { i,id -> id.takeIf(String::isNotEmpty)?.let { KilaLink(it,n.id,i) } } },graph.nodeGroups)
    private fun layout(data:KilaGraphData,id:String)=KilaGroupGraphProjection(data,id).display.nodes.map { Triple(it.id.startsWith("@"),it.x,it.y) }
    private fun move(data:KilaGraphData,id:String,dx:Int,dy:Int,expanded:Boolean=false):KilaGraphData {
        val projection=KilaGroupProjection(data,if(expanded) setOf(id) else emptySet())
        return projection.restore(projection.display.copy(nodes=projection.display.nodes.map {
            if(it.id=="@group:$id") it.copy(x=it.x+dx,y=it.y+dy) else it
        }))
    }

    @Test fun `moving folded and expanded legacy groups keeps the complete inner layout`() {
        for(expanded in listOf(false,true)) {
            val original=data(graph())
            val expected=layout(original,"module")
            val moved=move(original,"module",1000,-500,expanded)
            assertEquals(expected,layout(moved,"module"))
            assertEquals(original.nodes.last(),moved.nodes.last())
            assertEquals(1180,moved.nodes.first().x)
            val opened=KilaGroupGraphProjection(moved,"module")
            assertEquals(moved,opened.restore(opened.display))
            assertEquals(expected,layout(move(moved,"module",-1700,1200,expanded),"module"))
        }
    }

    @Test fun `shape instances and duplicates share layout while retaining independent outer positions`() {
        val graph=graph()
        val instanced=GraphGroups.instantiate(graph,"module")
        GraphCompiler.structure(instanced,DEFAULT_GROUPS)
        assertNotEquals(instanced.nodes.first().x,instanced.nodes[graph.nodes.size].x)
        val expected=layout(data(graph),"module")
        assertEquals(expected,layout(data(instanced),instanced.nodeGroups.last().id))
        val (duplicated,_)=GraphSelectionEdits.duplicate(graph,setOf("source","union"))
        assertEquals(expected,layout(data(duplicated),duplicated.nodeGroups.last().id))
        assertEquals(expected,layout(data(GraphSelectionEdits.align(graph,setOf("source","union"),true)),"module"))
    }

    @Test fun `clamping outer movement at graph bounds does not clamp or shift inner terminals`() {
        val original=data(graph())
        val moved=move(original,"module",50000,-50000)
        assertEquals(8192,moved.nodes.filter { it.id!="result" }.maxOf { it.x })
        assertEquals(-8192,moved.nodes.filter { it.id!="result" }.minOf { it.y })
        assertEquals(layout(original,"module"),layout(moved,"module"))
        assertEquals(moved,move(moved,"module",50000,-50000))
    }

    @Test fun `moving only the result preserves member layout and external connections`() {
        val original=data(GraphGroups.instantiate(graph(),"module"))
        val projection=KilaGroupGraphProjection(original,original.nodeGroups.last().id)
        val changed=projection.restore(projection.display.copy(nodes=projection.display.nodes.map {
            if(it.id=="@output:result") it.copy(x=it.x+120,y=it.y-80) else it
        }))
        assertEquals(original.nodes,changed.nodes)
        assertEquals(original.links,changed.links)
        assertEquals(original.nodeGroups.first().graphInterface!!.positions,changed.nodeGroups.first().graphInterface!!.positions)
        assertEquals(GraphGroupPosition(770,20),changed.nodeGroups.first().graphInterface!!.outputs.single().let { GraphGroupPosition(it.x,it.y) })
        assertEquals(layout(changed,"module"),layout(changed,changed.nodeGroups.last().id))
    }

    @Test fun `opening a legacy instance first captures the original definition layout`() {
        val graph=GraphGroups.instantiate(graph(),"module").let { it.copy(nodeGroups=it.nodeGroups.map { group ->
            group.copy(graphInterface=group.graphInterface!!.copy(positions=null))
        }) }
        val original=data(graph)
        val initialized=GroupGraphInterfaces.initializeInstances(original,graph.nodeGroups.last().id)
        assertEquals(original.nodes,initialized.nodes)
        assertEquals(layout(original,"module"),layout(initialized,graph.nodeGroups.last().id))
        assertEquals(initialized,GroupGraphInterfaces.initializeInstances(initialized,"module"))
    }

    @Test fun `member and terminal drags persist together across instances JSON and reopening`() {
        val graph=GraphGroups.instantiate(graph(),"module")
        val original=data(graph)
        val id=graph.nodeGroups.last().id
        val projection=KilaGroupGraphProjection(original,id)
        val view=projection.display.copy(nodes=projection.display.nodes.map { it.copy(x=it.x+75,y=it.y-30) })
        val changed=projection.restore(view)
        assertEquals(original.nodes,changed.nodes)
        assertEquals(original.links,changed.links)
        assertEquals(layout(changed,"module"),layout(changed,id))
        assertEquals(view.nodes.map { it.x to it.y },KilaGroupGraphProjection(changed,id).display.nodes.map { it.x to it.y })
        val saved=graph.copy(nodeGroups=changed.nodeGroups)
        val restored=Gson().fromJson(Gson().toJson(saved),NodeGraph::class.java)
        GraphCompiler.structure(restored,DEFAULT_GROUPS)
        assertEquals(saved,restored)
        assertEquals(layout(changed,id),layout(data(restored),id))
        // Undo/redo restores the entire layout through the document snapshot.
        assertEquals(layout(original,id),layout(data(graph),id))
        assertEquals(layout(changed,id),layout(data(saved),id))
    }

    @Test fun `adding and removing members keeps layout indices and terminal bindings aligned`() {
        val graph=GraphGroups.instantiate(graph(),"module")
        val movedData=move(data(graph),graph.nodeGroups.last().id,700,300)
        val moved=graph.copy(nodes=graph.nodes.map { n -> movedData.nodes.first { it.id==n.id }.let { n.copy(x=it.x,y=it.y) } },nodeGroups=movedData.nodeGroups)
        val target=moved.nodeGroups.last()
        val anchor=moved.nodes.first { it.id==target.nodes.first() }
        val local=target.graphInterface!!.positions!!.first()
        val added=GraphNode("new",part=Part("copycats:copycat_slab"),x=anchor.x-local.x+300,y=anchor.y-local.y+250)
        val grown=GraphGroups.addMembers(moved.copy(nodes=moved.nodes+added),target.id,listOf(added.id))
        GraphCompiler.structure(grown,DEFAULT_GROUPS)
        assertTrue(grown.nodeGroups.all { it.graphInterface!!.positions!!.last()==GraphGroupPosition(300,250) })
        val originalCopy=grown.nodes.first { it.id==grown.nodeGroups.first().nodes.last() }
        assertEquals(300 to 250,originalCopy.x to originalCopy.y)
        val pruned=GraphSelectionEdits.remove(grown,setOf(target.nodes.first()))
        GraphCompiler.structure(pruned,DEFAULT_GROUPS)
        assertTrue(pruned.nodeGroups.all { it.graphInterface!!.positions!!.size==it.nodes.size })
        assertTrue(pruned.nodeGroups.all { it.graphInterface!!.outputs.single().member==0 })
        assertEquals(layout(data(pruned),"module"),layout(data(pruned),target.id))
    }

    @Test fun `new interfaces place terminals alongside members even far from the origin`() {
        val original=data(graph()).let { it.copy(nodes=it.nodes.map { n -> n.copy(y=n.y+3000) },nodeGroups=it.nodeGroups.map { g -> g.copy(graphInterface=null) }) }
        val initialized=GroupGraphInterfaces.initializeInstances(original,"module")
        val face=initialized.nodeGroups.single().graphInterface!!
        assertEquals(3100,face.outputs.single().y)
        assertEquals(listOf(GraphGroupPosition(180,3100),GraphGroupPosition(400,3160)),face.positions)
    }

    @Test fun `building instances preserve local layout and compiled geometry`() {
        val base=BuildingGraph.starter()
        val grouped=GraphGroups.create(base,setOf("wall"))
        val group=grouped.nodeGroups.single()
        val wall=base.nodes.single { it.id=="wall" }
        val face=GraphGroupInterface(outputs=listOf(GraphGroupOutput("wall","Wand","WALL",0,x=wall.x+250,y=wall.y)))
        val graph=grouped.copy(nodeGroups=listOf(group.copy(graphInterface=face)))
        val instanced=GraphGroups.instantiate(graph,group.id)
        BuildingGraphCompiler.structure(instanced)
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        assertEquals(BuildingGraphCompiler.compile(Building(schema=2,graph=graph),forms,emptyMap()),
            BuildingGraphCompiler.compile(Building(schema=2,graph=instanced),forms,emptyMap()))
        val diagram=KilaGraphData(instanced.nodes.map { KilaNode(it.id,it.id,it.x,it.y,emptyList(),outputType=buildingGraphType(it.kind)) },emptyList(),instanced.nodeGroups)
        assertEquals(layout(diagram,group.id),layout(diagram,instanced.nodeGroups.last().id))
        val moved=move(diagram,instanced.nodeGroups.last().id,-600,800)
        assertEquals(layout(diagram,group.id),layout(moved,instanced.nodeGroups.last().id))
        val added=BuildingAssembly.newNode(BuildingKind.COPYCAT,"new",x=wall.x+100,y=wall.y+200)
        val grown=GraphGroups.addMembers(instanced.copy(nodes=instanced.nodes+added),group.id,listOf(added.id))
        BuildingGraphCompiler.structure(grown)
        assertTrue(grown.nodeGroups.all { it.graphInterface!!.positions!!.last()==GraphGroupPosition(added.x,added.y) })
        val removed=grown.remove("new")
        BuildingGraphCompiler.structure(removed)
        assertTrue(removed.nodeGroups.all { it.graphInterface!!.positions!!.size==it.nodes.size })
    }

    @Test fun `invalid layout sizes and coordinates are rejected and legacy JSON remains readable`() {
        val graph=graph()
        assertEquals(graph,Gson().fromJson(Gson().toJson(graph),NodeGraph::class.java))
        for(positions in listOf(emptyList(),listOf(GraphGroupPosition(9000,0),GraphGroupPosition()))) {
            val invalid=graph.copy(nodeGroups=graph.nodeGroups.map { it.copy(graphInterface=it.graphInterface!!.copy(positions=positions)) })
            assertFails { GraphCompiler.structure(invalid,DEFAULT_GROUPS) }
        }
    }
}
