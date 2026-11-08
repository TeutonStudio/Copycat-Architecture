package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.SemanticBuildingClientSmoke
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import kotlin.test.*

class SemanticBuildingIntegrationTest {
    @Test fun `architect fixture compiles with exact attachments and compact authoring roundtrip`() {
        val library=Library().also { it.seed() }
        val building=SemanticBuildingClientSmoke.createBuilding()
        BuildingGraphCompiler.structure(building.graph!!)
        val plan=Generator.generate(building,library.forms,library.modules)
        assertTrue(plan.warnings.isEmpty(),plan.warnings.toString())
        assertTrue(plan.placements.any { it.pos.x==3 && it.pos.z==3 && it.pos.y>=5 && it.component=="trim" })
        assertTrue(plan.placements.any { it.pos.x in 6..7 && it.pos.z in 1..4 && it.component=="ceiling" })
        val text=Json.encode(building)
        assertTrue(text.length<180000)
        assertFalse(text.contains("roofContacts"))
        assertEquals(plan,Generator.generate(Json.decode<Building>(text),library.forms,library.modules))
        val graph=building.graph!!
        val ev=BuildingGraphCompiler.Evaluation(graph,building.palette,library.forms,library.modules)
        val canopy=ev.room("canopy_room")
        assertEquals(16,canopy.footprint.size)
        assertEquals(4,canopy.outerWidth)
        assertEquals(4,canopy.outerDepth)
        val placements=plan.placements.associateBy { it.pos }
        assertTrue(P(4,1,0) !in placements,"Door is cleared through facade")
    }
}
