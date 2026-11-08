package de.teutonstudio.copycatarchitect.domain

enum class CreativeCatalogSection(val key:String) { PROCEDURAL("procedural"), BASIC("basic"), FREQUENT("frequent"), SPECIAL("special"), SAVED("saved") }

/** Registry IDs determine discoverability only; registrations and crafting recipes stay intact. */
object CreativeCatalogPolicy {
    private val geometry=BaseShapes.all.map { it.source }.toSet()
    // These blocks need native behavior that a printed geometric composite cannot preserve.
    private val special=setOf(
        "copycat_fluid_pipe","copycat_glass_fluid_pipe","copycat_shaft","copycat_cogwheel","copycat_large_cogwheel",
        "copycat_control_desk","copycat_control_stand",
        "copycat_door","copycat_iron_door","copycat_sliding_door","copycat_folding_door",
        "copycat_trapdoor","copycat_iron_trapdoor","copycat_fence_gate",
        "copycat_stone_button","copycat_wooden_button",
        "copycat_stone_pressure_plate","copycat_wooden_pressure_plate",
        "copycat_light_weighted_pressure_plate","copycat_heavy_weighted_pressure_plate",
        "copycat_ladder","copycat_ghost_block")
    private val computerControls=setOf(
        "cc_aeroworks:computer_copycat_control_desk","cc_aeroworks:advanced_computer_copycat_control_desk",
        "cc_aeroworks:computer_copycat_control_stand","cc_aeroworks:advanced_computer_copycat_control_stand")
    fun isCopycat(namespace:String,path:String)=namespace=="copycats" || path.startsWith("copycat_") || path=="copycat" || path=="wrapped_copycat" || "$namespace:$path" in computerControls
    fun section(namespace:String,path:String):CreativeCatalogSection? {
        if(!isCopycat(namespace,path)) return null
        return when {
            "$namespace:$path" in geometry -> CreativeCatalogSection.BASIC
            path in special || "$namespace:$path" in computerControls -> CreativeCatalogSection.SPECIAL
            else -> CreativeCatalogSection.FREQUENT
        }
    }
}
