package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class VersionedGraphsTest {
    private val gson=Gson()
    private fun original()=NodeGraph(nodes=listOf(
        GraphNode("full",part=Part("copycats:copycat_block")),
        GraphNode("slab",part=Part("copycats:copycat_slab")),
        GraphNode("difference",NodeKind.DIFFERENCE,listOf("full","slab")),
        GraphNode("union",NodeKind.UNION,listOf("full","difference")),
        GraphNode("result",NodeKind.OUTPUT,listOf("union"))))
    private fun extraction()=VersionedGraphs.extract(VersionedGraphs.fromShape("root","Root",original()),setOf("full","difference"),"piece","Piece","instance")

    @Test fun `legacy edges have a default source port and conversion preserves evaluation`() {
        val old=gson.fromJson("""{"source":"a","target":"b","port":"0"}""",DiagramConnection::class.java)
        assertEquals("out",old.sourcePort)
        val root=VersionedGraphs.fromShape("root","Root",original())
        assertEquals(GraphCompiler.compile(original()),VersionedGraphs.compileShape(root,emptyMap()))
        assertEquals(root,gson.fromJson(gson.toJson(root),GraphDocument::class.java))
        assertFails { DiagramEdits.shape(original(),DEFAULT_GROUPS,original().nodes.map { DiagramPosition(it.id,it.x,it.y) },root.connections.map { it.copy(sourcePort="lost") }) }
    }
    @Test fun `extraction retains distinct outputs boundary inputs and material results`() {
        val extracted=extraction();val d=extracted.definition;val parent=extracted.parent
        assertEquals(2,d.ports.count { it.direction==GraphPortDirection.OUTPUT })
        assertEquals(1,d.ports.count { it.direction==GraphPortDirection.INPUT })
        assertEquals(setOf("slab","union","result"),parent.shape!!.nodes.map { it.id }.toSet())
        assertEquals(setOf("full","difference"),d.shape!!.nodes.map { it.id }.toSet())
        assertEquals(GraphCompiler.compile(original()),VersionedGraphs.compileShape(parent,mapOf(d.key to d)))
        assertEquals(setOf("out1","out2"),parent.connections.filter { it.source=="instance" }.map { it.sourcePort }.toSet())
    }
    @Test fun `parallel versions remain pinned and port update keeps obsolete wires`() {
        val e=extraction();val v1=e.definition
        val v2=v1.copy(version=2,shape=v1.shape!!.copy(nodes=v1.shape.nodes.map { if(it.id=="full") it.copy(part=Part("copycats:copycat_slab")) else it }))
        val versions=mapOf(v1.key to v1,v2.key to v2)
        assertEquals(GraphCompiler.compile(original()),VersionedGraphs.compileShape(e.parent,versions))
        val changed=v2.copy(ports=v2.ports.filter { it.id!="out1" },outputBindings=v2.outputBindings.filter { it.port!="out1" })
        val diff=VersionedGraphs.compare(v1.ports,changed.ports)
        assertTrue(diff.single().incompatible)
        val switched=VersionedGraphs.switchVersion(e.parent,"instance",changed)
        assertEquals(e.parent.connections,switched.connections)
        assertFails { VersionedGraphs.compileShape(switched,mapOf(changed.key to changed)) }
        assertEquals(e.parent,VersionedGraphs.switchVersion(switched,"instance",v1))
    }
    @Test fun `nested references preserve identities and compile without saved member copies`() {
        val first=extraction();val second=VersionedGraphs.extract(first.parent,setOf("instance","union"),"nested","Nested","nestedInstance")
        val versions=mapOf(first.definition.key to first.definition,second.definition.key to second.definition)
        assertEquals(listOf("slab","result"),second.parent.shape!!.nodes.map { it.id })
        assertEquals(1,second.parent.references.size)
        assertEquals(GraphCompiler.compile(original()),VersionedGraphs.compileShape(second.parent,versions))
        val bundle=GraphPackages.export(second.parent,versions,emptyMap(),emptyMap())
        assertEquals(versions.keys,bundle.graphs.keys)
        val restored=gson.fromJson(gson.toJson(bundle),GraphPackage::class.java)
        GraphPackages.validate(restored)
        assertEquals(GraphCompiler.compile(original()),VersionedGraphs.compileShape(restored.root,restored.graphs))
        assertFails { GraphPackages.validate(restored.copy(graphs=restored.graphs-first.definition.key)) }
    }
    @Test fun `missing versions incompatible schemas type mismatch and instance cycles fail safely`() {
        val e=extraction();val d=e.definition
        assertFails { VersionedGraphs.resolve(e.parent,emptyMap()) }
        assertFails { VersionedGraphs.resolve(e.parent,mapOf(d.key to d.copy(schema=999))) }
        assertFails { VersionedGraphs.validate(e.parent.copy(connections=e.parent.connections.map { it.copy(sourcePort="missing") })) }
        val wrong=d.copy(ports=d.ports.map { it.copy(type="rotation") })
        assertFails { VersionedGraphs.validate(wrong) }
        val self=GraphReferenceNode("self",GraphVersion(d.graphId,1),d.ports)
        val recursive=d.copy(references=d.references+self)
        assertFails { VersionedGraphs.dependencies(recursive,mapOf(recursive.key to recursive)) }
        val cycle=e.parent.copy(connections=e.parent.connections.map { if(it.target=="instance") it.copy(source="instance",sourcePort="out1") else it })
        assertFails { VersionedGraphs.resolve(cycle,mapOf(d.key to d)) }
    }
    @Test fun `package import rejects same version different contents before any mutation`() {
        val e=extraction();val bundle=GraphPackages.export(e.parent,mapOf(e.definition.key to e.definition),emptyMap(),emptyMap())
        val library=linkedMapOf(e.definition.key to e.definition.copy(name="Different"))
        val before=library.toMap()
        assertFails { GraphPackages.checkConflicts(bundle,library,emptyMap(),emptyMap()) }
        assertEquals(before,library)
        GraphPackages.checkConflicts(bundle,mapOf(e.definition.key to e.definition),emptyMap(),emptyMap())
    }
    @Test fun `building graph reference retains count sources room topology and placement`() {
        val original=BuildingGraph.starter()
        val root=VersionedGraphs.fromBuilding("buildingRoot","Building",original)
        val e=VersionedGraphs.extract(root,setOf("wall","floor","ceiling","room"),"roomPart","Room","roomInstance")
        val graph=VersionedGraphs.resolve(e.parent,mapOf(e.definition.key to e.definition)).building!!
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        assertEquals(BuildingGraphCompiler.compile(Building(schema=2,graph=original),forms,emptyMap()).plan,
            BuildingGraphCompiler.compile(Building(schema=2,graph=graph),forms,emptyMap()).plan)
    }
    @Test fun `repeated identical instances share evaluation without expanding the stored parent`() {
        val e=VersionedGraphs.extract(VersionedGraphs.fromShape("root","Root",NodeGraph.fromParts(listOf(Part("copycats:copycat_block")))),setOf("source0"),"piece","Piece","first")
        val second=e.reference.copy(id="second")
        val root=e.parent.copy(references=e.parent.references+second,shape=e.parent.shape!!.copy(nodes=listOf(
            GraphNode("union",NodeKind.UNION,listOf("","")),GraphNode("result",NodeKind.OUTPUT,listOf("")))),
            connections=listOf(DiagramConnection("first","union","0","out1"),DiagramConnection("second","union","1","out1"),DiagramConnection("union","result","0")))
        val library=mapOf(e.definition.key to e.definition)
        val resolved=VersionedGraphs.resolve(root,library)
        assertEquals(3,resolved.shape!!.nodes.size)
        assertEquals(resolved.nodeOutputs["first"],resolved.nodeOutputs["second"])
        assertEquals(2,root.shape!!.nodes.size)
        assertEquals(VersionedGraphs.compileShape(e.parent,library),VersionedGraphs.compileShape(root,library))
        assertEquals(listOf(Part("copycats:copycat_block")),VersionedGraphs.evaluateShapeOutput(e.definition,emptyMap(),"out1"))
    }
    @Test fun `dangling version ports remain displayable and persist in repairable draft packages`() {
        val e=extraction();val next=e.definition.copy(version=2,ports=e.definition.ports.filter { it.id!="out1" },outputBindings=e.definition.outputBindings.filter { it.port!="out1" })
        val broken=VersionedGraphs.switchVersion(e.parent,"instance",next)
        assertTrue(VersionedGraphs.displayOutputs(broken,"instance").any { it.id=="out1" && it.name.startsWith("?") })
        val bundle=GraphPackages.export(broken,mapOf(next.key to next),emptyMap(),emptyMap())
        val restored=gson.fromJson(gson.toJson(bundle),GraphPackage::class.java)
        assertEquals(broken.connections,restored.root.connections)
        GraphPackages.validate(restored)
        assertFails { VersionedGraphs.compileShape(restored.root,restored.graphs) }
        assertFails { GraphPackages.validate(restored.copy(root=restored.root.copy(version=1))) }
    }
    @Test fun `deep reference chains are bounded even with no native members in intermediate definitions`() {
        val leaf=GraphDocument(graphId="leaf",version=1,shape=NodeGraph(nodes=listOf(GraphNode("source",part=Part("copycats:copycat_block")))),
            ports=listOf(GraphInterfacePort("out","Shape","shape",GraphPortDirection.OUTPUT)),outputBindings=listOf(GraphOutputBinding("out",GraphOutput("source"))))
        val versions=linkedMapOf(leaf.key to leaf);var child=leaf
        repeat(VersionedGraphs.MAX_DEPTH+1) { index ->
            val ref=GraphReferenceNode("instance",GraphVersion(child.graphId,child.version),child.ports)
            child=GraphDocument(graphId="level$index",version=1,shape=NodeGraph(nodes=emptyList()),references=listOf(ref),ports=child.ports,
                outputBindings=listOf(GraphOutputBinding("out",GraphOutput("instance","out"))))
            versions[child.key]=child
        }
        assertFails { VersionedGraphs.resolve(child,versions) }
    }

}
