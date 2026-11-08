package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CreativeCatalogPolicyTest {
    @Test fun `all graph supported base sources belong to geometry`() {
        for(shape in BaseShapes.all) {
            val (namespace,path)=shape.source.split(':')
            assertEquals(CreativeCatalogSection.BASIC,CreativeCatalogPolicy.section(namespace,path),shape.id)
        }
    }
    @Test fun `functional Copycats belong to the special section`() {
        for(path in listOf(
            "copycat_fluid_pipe","copycat_glass_fluid_pipe","copycat_cogwheel","copycat_large_cogwheel","copycat_shaft",
            "copycat_door","copycat_iron_door","copycat_sliding_door","copycat_folding_door",
            "copycat_trapdoor","copycat_iron_trapdoor","copycat_fence_gate",
            "copycat_stone_button","copycat_wooden_button","copycat_stone_pressure_plate","copycat_wooden_pressure_plate",
            "copycat_light_weighted_pressure_plate","copycat_heavy_weighted_pressure_plate","copycat_ladder","copycat_ghost_block"))
            assertEquals(CreativeCatalogSection.SPECIAL,CreativeCatalogPolicy.section("copycats",path),path)
    }
    @Test fun `third party Copycat control desks and stands belong to the special section`() {
        for(path in listOf("copycat_control_desk","copycat_control_stand"))
            assertEquals(CreativeCatalogSection.SPECIAL,CreativeCatalogPolicy.section("aeroworks",path),path)
        for(path in listOf("computer_copycat_control_desk","advanced_computer_copycat_control_desk",
            "computer_copycat_control_stand","advanced_computer_copycat_control_stand")) {
            assertTrue(CreativeCatalogPolicy.isCopycat("cc_aeroworks",path),path)
            assertEquals(CreativeCatalogSection.SPECIAL,CreativeCatalogPolicy.section("cc_aeroworks",path),path)
        }
    }
    @Test fun `unrelated items stay outside the Copycat catalog`() {
        assertNull(CreativeCatalogPolicy.section("create","fluid_pipe"))
        assertNull(CreativeCatalogPolicy.section("minecraft","stone"))
        assertNull(CreativeCatalogPolicy.section("cc_aeroworks","computer_control_desk"))
        assertNull(CreativeCatalogPolicy.section("cc_aeroworks","advanced_computer_control_stand"))
        assertNull(CreativeCatalogPolicy.section("cc_aeroworks","guide_book"))
    }
    @Test fun `decorative Copycats remain available without implying a graph adapter`() {
        for(path in listOf("copycat_slope","copycat_board","copycat_fence","copycat_wall","copycat_catwalk","copycat_box"))
            assertEquals(CreativeCatalogSection.FREQUENT,CreativeCatalogPolicy.section("copycats",path),path)
        assertTrue(CreativeCatalogPolicy.isCopycat("create","copycat_panel"))
        assertFalse(CreativeCatalogPolicy.isCopycat("copycat_architect","composite"))
    }
}
