package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GraphLibraryTest {
    private fun root()=VersionedGraphs.fromShape("root","Root",NodeGraph.fromParts(listOf(Part("copycats:copycat_block"))))
    private fun extracted()=VersionedGraphs.extract(root(),setOf("source0"),"piece","Piece","instance")
    @Test fun `publication assigns immutable versions and detaches caller owned collections`() {
        val e=extracted();val nodes=e.definition.shape!!.nodes.toMutableList()
        val draft=e.definition.copy(shape=e.definition.shape.copy(nodes=nodes))
        val lib=Library()
        val first=GraphLibrary.publish(lib,draft)
        nodes.clear()
        val second=GraphLibrary.publish(lib,e.definition.copy(name="Changed"))
        assertEquals(1,first.version);assertEquals(2,second.version)
        assertEquals(1,lib.graphs.getValue(first.key).shape!!.nodes.size)
        assertEquals("Piece",lib.graphs.getValue(first.key).name)
        assertEquals("Changed",lib.graphs.getValue(second.key).name)
        assertEquals(GraphCompiler.compile(NodeGraph.fromParts(listOf(Part("copycats:copycat_block")))),VersionedGraphs.compileShape(e.parent,lib.graphs))
    }
    @Test fun `package imports commit once and conflict or storage failure leaves library unchanged`() {
        val e=extracted();val bundle=GraphPackages.export(e.parent,mapOf(e.definition.key to e.definition),emptyMap(),emptyMap())
        val lib=Library()
        GraphLibrary.importPackage(lib,bundle)
        assertEquals(bundle.graphs.keys,lib.graphs.keys)
        val before=Json.encode(lib)
        assertFails { GraphLibrary.importPackage(lib,bundle.copy(graphs=mapOf(e.definition.key to e.definition.copy(name="Conflict")))) }
        assertEquals(before,Json.encode(lib))
        lib.buildings["large@1"]=Building(name="x".repeat(Library.MAX_JSON_CHARS))
        val full=Json.encode(lib)
        assertFails { GraphLibrary.publish(lib,e.definition.copy(name="Too much")) }
        assertEquals(full,Json.encode(lib))
    }
    @Test fun `old libraries initialize the new version map and definition packages are valid`() {
        assertTrue(Json.decode<Library>("""{"forms":{},"modules":{}}""").graphs.isEmpty())
        val d=extracted().definition
        GraphPackages.validate(GraphPackages.export(d,emptyMap(),emptyMap(),emptyMap()))
    }
    @Test fun `uploads resume out of order are owner scoped and reject incomplete or conflicting chunks`() {
        val transfers=GraphPackageTransfers();val payload="ä".repeat(48005)
        val manifest=GraphTransferManifest("upload",payload.length,3,GraphPackageTransfers.digest(payload))
        transfers.begin("alice",manifest,0)
        val chunks=payload.chunked(GraphPackageTransfers.CHUNK_CHARACTERS)
        transfers.append("alice","upload",2,chunks[2],1)
        transfers.begin("alice",manifest,2)
        assertEquals(listOf(0,1),transfers.missing("alice","upload",3))
        assertFails { transfers.content("alice","upload",3) }
        assertFails { transfers.append("bob","upload",0,chunks[0],3) }
        transfers.append("alice","upload",0,chunks[0],3)
        assertFails { transfers.append("alice","upload",0,"a".repeat(chunks[0].length),4) }
        transfers.append("alice","upload",1,chunks[1],5)
        assertEquals(payload,transfers.content("alice","upload",6))
        assertEquals(payload,transfers.content("alice","upload",7))
        transfers.complete("alice","upload")
        assertFails { transfers.content("alice","upload",8) }
    }
    @Test fun `upload manifests enforce limits expiry and final integrity`() {
        val transfers=GraphPackageTransfers();val manifest=GraphTransferManifest("one",3,1,GraphPackageTransfers.digest("abc"))
        assertFails { transfers.begin("alice",manifest.copy(characters=GraphPackageTransfers.MAX_CHARACTERS+1),0) }
        transfers.begin("alice",manifest,0)
        transfers.append("alice","one",0,"bad",1)
        assertFails { transfers.content("alice","one",2) }
        assertFails { transfers.content("alice","one",GraphPackageTransfers.TIMEOUT_MS+2) }
        transfers.begin("alice",manifest,GraphPackageTransfers.TIMEOUT_MS+3)
        assertEquals(listOf(0),transfers.missing("alice","one",GraphPackageTransfers.TIMEOUT_MS+4))
    }
    @Test fun `unicode transfers retain surrogate pairs and known SHA256 integrity`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",GraphPackageTransfers.digest("abc"))
        val payload="x".repeat(GraphPackageTransfers.CHUNK_CHARACTERS-1)+"\uD83D\uDE80"+"y".repeat(GraphPackageTransfers.CHUNK_CHARACTERS)
        val chunks=GraphPackageTransfers.split(payload)
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        val transfer=GraphPackageTransfers();transfer.begin("owner",GraphTransferManifest("unicode",payload.length,chunks.size,GraphPackageTransfers.digest(payload)),0)
        chunks.forEachIndexed { i,value -> transfer.append("owner","unicode",i,value,i+1L) }
        assertEquals(payload,transfer.content("owner","unicode",9))
    }
    @Test fun `explicit draft saves replace editable content while imports protect existing drafts`() {
        val e=extracted();val lib=Library()
        val package1=GraphPackages.export(e.parent,mapOf(e.definition.key to e.definition),emptyMap(),emptyMap())
        GraphLibrary.importPackage(lib,package1)
        val changed=package1.copy(root=package1.root.copy(name="Edited"))
        assertFails { GraphLibrary.importPackage(lib,changed) }
        GraphLibrary.importPackage(lib,changed,replaceDraft=true)
        assertEquals("Edited",lib.graphDrafts.getValue(e.parent.graphId).name)
        assertEquals(e.definition,lib.graphs.getValue(e.definition.key))
    }

}
