package de.teutonstudio.copycatarchitect.domain

/** Values, rather than implementation node variants, define graph interfaces. */
fun buildingGraphType(kind:BuildingKind):String=when(kind) {
    BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE -> "WALL"
    BuildingKind.COPYCAT_POSITION,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE -> "COPYCAT"
    BuildingKind.INT,BuildingKind.INT_ADD,BuildingKind.INT_SUBTRACT,BuildingKind.INT_MULTIPLY,BuildingKind.INT_DIVIDE,BuildingKind.INT_MIN,BuildingKind.INT_MAX,BuildingKind.POSITION_X,BuildingKind.POSITION_Y,BuildingKind.POSITION_Z -> "INT"
    BuildingKind.POSITION_ADD,BuildingKind.POSITION_SUBTRACT,BuildingKind.POSITION_MULTIPLY,BuildingKind.POSITION_DIVIDE,BuildingKind.POSITION_MIN,BuildingKind.POSITION_MAX -> "POSITION"
    else -> kind.name
}
