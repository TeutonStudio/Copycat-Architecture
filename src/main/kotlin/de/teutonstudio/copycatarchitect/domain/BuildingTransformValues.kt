package de.teutonstudio.copycatarchitect.domain

/** Typed, reusable ordered transformations. Missing links retain legacy inline settings. */
object BuildingTransformValues {
    fun validate(n:BuildingNode) {
        require(n.rotations==null || n.kind==BuildingKind.ROTATION && n.rotations.size<=64 && n.rotations.all { it.axis!=RotationAxis.NORMAL && it.quarterTurns in 0..3 && !it.reflected && it.mirror==null }) { "Invalid rotation value list" }
        require(n.mirrors==null || n.kind==BuildingKind.MIRRORING && n.mirrors.size<=64 && n.mirrors.all { it.plane.name in listOf("X","Y","Z") }) { "Invalid mirror value list" }
        require(n.rotation.isNullOrEmpty() || n.kind in setOf(BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_ROTATE)) { "Only rotation operators accept rotation values" }
        require(n.mirroring.isNullOrEmpty() || n.kind in setOf(BuildingKind.COPYCAT_MIRROR,BuildingKind.SEGMENT_MIRROR)) { "Only mirror operators accept mirroring values" }
    }
    fun rotations(n:BuildingNode,graph:BuildingGraph):List<SegmentRotation> = if(n.rotation.isNullOrEmpty()) listOf(SegmentRotation(n.rotationAxis,n.quarterTurns)) else
        graph.nodes.single { it.id==n.rotation && it.kind==BuildingKind.ROTATION }.rotations.orEmpty()
    fun mirrors(n:BuildingNode,graph:BuildingGraph):List<CopycatMirror> = if(n.mirroring.isNullOrEmpty()) listOf(if(n.kind==BuildingKind.SEGMENT_MIRROR) CopycatMirror(CopycatMirrorPlane.valueOf(n.rotationAxis.name)) else n.mirror ?: CopycatMirror()) else
        graph.nodes.single { it.id==n.mirroring && it.kind==BuildingKind.MIRRORING }.mirrors.orEmpty()
}
