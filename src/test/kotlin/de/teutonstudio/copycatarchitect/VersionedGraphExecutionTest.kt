package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class VersionedGraphExecutionTest {
    @Test fun `print definitions retain references and recompute untrusted cached parts`() {
        val graph=NodeGraph(nodes=listOf(GraphNode("full",part=Part("copycats:copycat_block")),GraphNode("slab",part=Part("copycats:copycat_slab")),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf("full","slab")),GraphNode("result",NodeKind.OUTPUT,listOf("cut"))))
        val e=VersionedGraphs.extract(VersionedGraphs.fromShape("root","Printable",graph),setOf("full","slab","cut"),"piece","Piece","instance")
        val bundle=GraphPackages.export(e.parent,mapOf(e.definition.key to e.definition),emptyMap(),emptyMap())
        val cached=Form(id="printed",name="Printable",parts=listOf(Part("copycats:copycat_byte")),versionedGraph=bundle)
        val actual=Geometry.validate(cached)
        assertEquals(GraphCompiler.compile(graph),actual.parts)
        assertNull(actual.graph)
        assertEquals(1,actual.versionedGraph!!.root.shape!!.nodes.size)
        assertEquals(GraphVersion("piece",1),actual.versionedGraph.root.references.single().reference)
        assertEquals(actual,Geometry.validate(Json.decode<Form>(Json.encode(actual))))
        val broken=bundle.copy(graphs=emptyMap())
        assertFails { Geometry.validate(cached.copy(versionedGraph=broken)) }
    }
    @Test fun `building execution resolves pinned modules and forms from its offline package`() {
        val graph=BuildingGraph.starter()
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        // The starter's wall/floor paths use full@1 implicitly through their module-free defaults.
        val native=Building(schema=2,graph=graph)
        val root=VersionedGraphs.fromBuilding("root","Executable",graph,native.palette)
        val e=VersionedGraphs.extract(root,setOf("wall","floor","ceiling","room"),"roomPart","Room","instance")
        val bundle=GraphPackages.export(e.parent,mapOf(e.definition.key to e.definition),forms,emptyMap())
        val executable=native.copy(graph=null,versionedGraph=bundle)
        val restored=Json.decode<Building>(Json.encode(executable))
        assertNull(restored.graph)
        assertEquals(1,restored.versionedGraph!!.root.references.size)
        assertEquals(setOf("full@1"),bundle.forms.keys)
        assertEquals(Generator.generate(native,forms,emptyMap()),Generator.generate(restored,emptyMap(),emptyMap()))
        assertEquals(executable,restored)
    }
    @Test fun `printing never silently selects between multiple exposed shape outputs`() {
        val definition=GraphDocument(graphId="multiple",version=1,shape=NodeGraph(nodes=listOf(
            GraphNode("a",part=Part("copycats:copycat_block")),GraphNode("b",part=Part("copycats:copycat_slab")))),
            ports=listOf(GraphInterfacePort("a","First","shape",GraphPortDirection.OUTPUT),GraphInterfacePort("b","Second","shape",GraphPortDirection.OUTPUT)),
            outputBindings=listOf(GraphOutputBinding("a",GraphOutput("a")),GraphOutputBinding("b",GraphOutput("b"))))
        val bundle=GraphPackages.export(definition,emptyMap(),emptyMap(),emptyMap())
        assertFails { Geometry.validate(Form(versionedGraph=bundle)) }
        assertEquals(listOf(Part("copycats:copycat_slab")),VersionedGraphs.evaluateShapeOutput(definition,emptyMap(),"b"))
    }

    @Test fun `reopening a locally recolored building keeps the override and leaves its publication frozen`() {
        val root=VersionedGraphs.nextVersion(VersionedGraphs.fromBuilding("colored","Color",BuildingGraph.starter()),emptyMap())
        val override=root.palette.mapValues { "minecraft:oak_planks" }
        val draft=VersionedGraphs.withPalette(root,override)
        assertEquals(0,draft.version)
        assertEquals(override,draft.palette)
        assertEquals(1,root.version)
        assertNotEquals(override,root.palette)
        assertSame(root,VersionedGraphs.withPalette(root,root.palette))
    }

}
