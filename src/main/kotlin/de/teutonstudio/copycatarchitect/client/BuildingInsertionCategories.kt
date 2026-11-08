package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.BuildingKind

/** Every building node appears once in the native Add node submenu. */
object BuildingInsertionCategories {
    fun key(kind:BuildingKind):String=when(kind) {
        BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOM_LAYOUT -> "node_category_geometry"
        BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE -> "node_category_copycats"
        BuildingKind.SEGMENT_UNION, BuildingKind.SEGMENT_INTERSECTION, BuildingKind.SEGMENT_DIFFERENCE, BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,BuildingKind.SEGMENT_LAYOUT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.FLOOR,BuildingKind.CEILING -> "node_category_segments"
        BuildingKind.ROOM,BuildingKind.STOREY,BuildingKind.BUILDING -> "node_category_assembly"
        BuildingKind.ROOF_LEVEL,BuildingKind.ROOF_LAYOUT,BuildingKind.ROOF -> "node_category_roofs"
        BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT -> "node_category_access"
        BuildingKind.GARDEN,BuildingKind.PATH -> "node_category_landscape"
    }
}
