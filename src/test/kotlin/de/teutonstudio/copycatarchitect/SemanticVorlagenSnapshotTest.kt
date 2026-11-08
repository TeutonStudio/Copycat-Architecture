package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import kotlin.test.*

/** Optional, read-only regression against the real exported Vorlagen library. */
@EnabledIfEnvironmentVariable(named="ARCHITECT_SEMANTIC_SNAPSHOT",matches=".+")
class SemanticVorlagenSnapshotTest {
    @Test fun `saved depot and deliberate conversion preserve exact world geometry`() {
        val library=Json.decode<Library>(File(System.getenv("ARCHITECT_SEMANTIC_SNAPSHOT")).readText())
        val building=library.buildings.values.filter { it.name=="Lagerhalle" && it.graph!=null }.maxBy { it.version }
        val graph=building.graph!!;val text=Json.encode(graph)
        val before=Generator.generate(building,library.forms,library.modules)
        var draft=graph
        val selected=graph.nodes.filter { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" }
        assertTrue(selected.isNotEmpty())
        selected.forEach { draft=SemanticConversion.convert(draft,it.id) }
        BuildingGraphCompiler.structure(draft)
        val after=Generator.generate(building.copy(graph=draft),library.forms,library.modules)
        assertEquals(text,Json.encode(graph),"Original authoring graph stays unchanged")
        assertEquals(3,draft.schema)
        assertEquals(before.clear.toSet(),after.clear.toSet())
        val old=before.placements.associateBy { it.pos };val new=after.placements.associateBy { it.pos }
        assertEquals(old.keys,new.keys)
        old.forEach { (p,value)->assertTrue(value.sameVisual(new.getValue(p)),"Conversion changes depot geometry at $p") }
        assertEquals(before.materials,after.materials)
        assertTrue(Json.encode(building.copy(graph=draft)).length<180000)
        println("REAL VORLAGEN DEPOT CONVERSION VERIFIED: ${old.size} placements, ${before.clear.size} air cells, ${graph.nodes.size} original nodes, ${draft.nodes.size} draft nodes")
    }
}
