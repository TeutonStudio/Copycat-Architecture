package de.teutonstudio.copycatarchitect.domain

object BuildingCopycatTransforms {
    val sourceKinds=BuildingKind.entries.filter { it.name.startsWith("COPYCAT") }.toSet()
    val operatorKinds=setOf(BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_POSITION,BuildingKind.COPYCAT_OPERATOR)
    fun operations(node:BuildingNode,nodes:Map<String,BuildingNode>,modern:Boolean=false):List<CopycatTransform> {
        val linked=when(node.kind) {
            BuildingKind.COPYCAT_ROTATE->node.rotation
            BuildingKind.COPYCAT_MIRROR->node.mirroring
            BuildingKind.COPYCAT_POSITION->node.positionValue
            else->null
        }?.takeIf(String::isNotEmpty)?.let { nodes[it] ?: error("Missing transformation value: $it") }
        val value=linked ?: node
        val primary=when {
            node.kind==BuildingKind.COPYCAT_POSITION->{
                require(linked==null || linked.kind.name in GraphValues.vectorKinds) { "Position input requires a position value" }
                listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=if(value.kind.name in GraphValues.vectorKinds) GraphValues.building(nodes).vector(value.id) else value.translation ?: P()))
            }
            node.kind==BuildingKind.COPYCAT_ROTATE && (modern || linked!=null || node.rotationPivot!=null && node.rotationPivot!=P(8,8,8) || !node.copycatTransforms.isNullOrEmpty())->{
                require(linked==null || linked.kind==BuildingKind.ROTATION) { "Rotation input requires a rotation value" }
                if(linked?.rotations!=null) linked.rotations.map { CopycatTransform(axis=it.axis,quarterTurns=it.quarterTurns,pivot=value.rotationPivot ?: P(8,8,8)) }
                else listOf(CopycatTransform(axis=value.rotationAxis,quarterTurns=value.quarterTurns,pivot=value.rotationPivot ?: P(8,8,8)))
            }
            node.kind==BuildingKind.COPYCAT_MIRROR && (modern || linked!=null || !node.copycatTransforms.isNullOrEmpty())->{
                require(linked==null || linked.kind==BuildingKind.MIRRORING) { "Mirror input requires a mirror value" }
                if(linked?.mirrors!=null) linked.mirrors.map { CopycatTransform(CopycatTransformKind.MIRROR,plane=it.plane) }
                else listOf(CopycatTransform(CopycatTransformKind.MIRROR,plane=(value.mirror ?: CopycatMirror()).plane))
            }
            else->emptyList()
        }
        return primary+node.copycatTransforms.orEmpty()
    }
}

/** The style name is independent of the legacy socket spelling and never decides compatibility. */
object SemanticPorts {
    fun type(raw:String):String = when {
        raw in setOf("shape","COPYCAT") || raw.startsWith("COPYCAT_")->"shape"
        raw.equals("rotation",true)->"rotation"
        raw.equals("mirroring",true)->"mirroring"
        raw.equals("position",true)->"position"
        raw=="INT"->"INT"
        raw=="BOOLEAN"->"BOOLEAN"
        raw.startsWith("SEGMENT") || raw in setOf("WALL","FLOOR","CEILING","PATTERN","ROTATION_SEGMENT")->"segment"
        raw in setOf("ROOM","BAY_WINDOW","ROOM_LAYOUT","ROOM_GEOMETRY")->"room"
        raw=="STOREY"->"storey"
        raw.startsWith("ROOF") || raw=="DORMER"->"roof"
        raw=="AREA"->"AREA"
        raw.equals("material",true)->"material"
        else->"structure"
    }
}
