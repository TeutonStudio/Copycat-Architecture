package de.teutonstudio.copycatarchitect.domain

/** Values, rather than implementation node variants, define graph interfaces. */
fun buildingGraphType(kind:BuildingKind):String=when(kind) {
    BuildingKind.COUNT,BuildingKind.INT_ABS,BuildingKind.INT_TO_COUNT -> "COUNT"
    BuildingKind.INT_REMAINDER -> "INT"
    BuildingKind.BOOLEAN,BuildingKind.INT_COMPARE,BuildingKind.BOOLEAN_LOGIC -> "BOOLEAN"
    BuildingKind.CONDITIONAL -> "VALUE"
    BuildingKind.SERVICE_SHAFT_SEGMENT,BuildingKind.ELEVATOR_SHAFT_SEGMENT -> "WALL"
    BuildingKind.COPYCAT_OPERATOR->"COPYCAT"
    BuildingKind.SEGMENT_OPERATOR->"WALL"
    BuildingKind.POSITION_OPERATOR->"POSITION"
    BuildingKind.NUMBER_OPERATOR->"INT"
    BuildingKind.RELATION_OPERATOR,BuildingKind.STATEMENT_OPERATOR->"BOOLEAN"
    BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_TRANSFORM,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE -> "WALL"
    BuildingKind.COPYCAT_POSITION,BuildingKind.COPYCAT_TRANSFORM,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE -> "COPYCAT"
    BuildingKind.INT,BuildingKind.INT_ADD,BuildingKind.INT_SUBTRACT,BuildingKind.INT_MULTIPLY,BuildingKind.INT_DIVIDE,BuildingKind.INT_MIN,BuildingKind.INT_MAX,BuildingKind.POSITION_X,BuildingKind.POSITION_Y,BuildingKind.POSITION_Z -> "INT"
    BuildingKind.POSITION_ADD,BuildingKind.POSITION_SUBTRACT,BuildingKind.POSITION_MULTIPLY,BuildingKind.POSITION_DIVIDE,BuildingKind.POSITION_MIN,BuildingKind.POSITION_MAX -> "POSITION"
    else -> kind.name
}
