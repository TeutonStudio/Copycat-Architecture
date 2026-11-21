package de.teutonstudio.copycatarchitect.domain

enum class CopycatTransformKind { ROTATE, MIRROR, POSITION }
data class CopycatTransform(val kind:CopycatTransformKind=CopycatTransformKind.ROTATE,val enabled:Boolean=true,
    val axis:RotationAxis=RotationAxis.Y,val quarterTurns:Int=1,val pivot:P=P(8,8,8),
    val plane:CopycatMirrorPlane=CopycatMirrorPlane.X,val offset:P=P())

/** One ordered contract for inline operations and typed operator nodes, in sixteenths of a block. */
object CopycatTransforms {
    val valueKinds=NodeKind.entries.filter { it.name in GraphValues.kinds || it in setOf(NodeKind.ROTATION,NodeKind.MIRRORING,NodeKind.TRANSFORMATION) }.toSet()
    val operatorKinds=setOf(NodeKind.ROTATE,NodeKind.MIRROR,NodeKind.TRANSLATE,NodeKind.COPYCAT_TRANSFORM)
    fun validate(steps:List<CopycatTransform>) {
        require(steps.size<=64) { "At most 64 Copycat transformations" }
        steps.forEach { step ->
            require(step.quarterTurns in 0..3 && step.axis!=RotationAxis.NORMAL) { "Rotation needs X/Y/Z and 90 degree steps" }
            require(listOf(step.pivot.x,step.pivot.y,step.pivot.z).all { it in 0..16 }) { "Rotation pivot must be in 0–16 hB" }
            require(listOf(step.offset.x,step.offset.y,step.offset.z).all { it in -16..16 }) { "Position must be in −16–16 hB" }
        }
    }
    fun apply(parts:List<Part>,steps:List<CopycatTransform>):List<Part> {
        validate(steps)
        return steps.filter { it.enabled }.fold(parts) { value,step -> value.map { part ->
            val transformed=when(step.kind) {
                CopycatTransformKind.MIRROR->PartRotations.mirror(part,step.plane)
                CopycatTransformKind.POSITION->translate(part,step.offset)
                CopycatTransformKind.ROTATE->{
                    val rotated=PartRotations.rotate(part,step.axis,step.quarterTurns) ?: error("Copycat cannot rotate around ${step.axis}")
                    val relative=step.pivot+P(-8,-8,-8)
                    val turned=rotatePoint(relative,step.axis,step.quarterTurns,true)
                    translate(rotated,relative+P(-turned.x,-turned.y,-turned.z))
                }
            }
            require(ExactGeometry.part(transformed).withinCopycatBlock()) { "Copycat transformation leaves block bounds (0–16 hB)" }
            transformed
        } }
    }
    private fun translate(part:Part,offset:P):Part {
        if(offset==P()) return part
        val visible=ExactGeometry.part(part).translated(offset)
        require(visible.withinCopycatBlock()) { "Copycat position leaves block bounds (0–16 hB)" }
        val candidate=part.copy(offset=part.offset+offset,
            cutMask=if(part.cutMask.isEmpty()) "" else visible.solid().mask(),exactClip=part.exactClip?.translated(offset))
        if(runCatching { ExactGeometry.part(candidate).equivalent(visible) }.getOrDefault(false)) return candidate
        // A clipped source may extend outside the cube although its visible result fits.
        // Store the actual exact volume, keeping its original item and receipt identity.
        return part.copy(exact=visible,exactClip=null,cutMask="",offset=P(),turn=0,spin=0,tilt=0,top=false)
    }
    fun operator(node:GraphNode,nodes:Map<String,GraphNode>):List<CopycatTransform> {
        if(node.kind==NodeKind.COPYCAT_TRANSFORM) return node.inputs.getOrNull(1)?.takeIf(String::isNotEmpty)?.let { TransformationValues.shape(nodes.getValue(it),nodes) }.orEmpty()
        val value=node.inputs.getOrNull(1)?.takeIf(String::isNotEmpty)?.let(nodes::getValue) ?: node
        return listOf(when(node.kind) {
            NodeKind.ROTATE->CopycatTransform(axis=if(value.rotationAxis==RotationAxis.NORMAL) RotationAxis.Y else value.rotationAxis,
                quarterTurns=value.quarterTurns,pivot=value.rotationPivot ?: P(8,8,8))
            NodeKind.MIRROR->CopycatTransform(CopycatTransformKind.MIRROR,plane=(value.mirror ?: CopycatMirror()).plane)
            NodeKind.TRANSLATE->CopycatTransform(CopycatTransformKind.POSITION,offset=if(value.kind.name in GraphValues.vectorKinds) GraphValues.shape(nodes).vector(value.id) else value.translation ?: P())
            else->error("Not a Copycat operator")
        })
    }
}

fun NodeGraph.withTransformSchema():NodeGraph {
    if(schema>=9 || nodes.any { it.kind.name in GraphNumbers.newKinds } || nodeGroups.any { g->g.graphInterface?.let { it.inputs.any {p->p.type=="COUNT"} || it.outputs.any {p->p.type=="COUNT"} }==true }) return copy(schema=maxOf(schema,9))
    if(nodes.any { it.kind.name in GraphTypes.newKinds }) return copy(schema=maxOf(schema,8))
    if(nodes.any { it.kind in setOf(NodeKind.TRANSFORMATION,NodeKind.COPYCAT_TRANSFORM) } || nodeGroups.any { g->g.graphInterface?.let { it.inputs.any { p->p.type=="transformation" } || it.outputs.any { p->p.type=="transformation" } }==true }) return copy(schema=maxOf(schema,7))
    val modern=nodeGroups.any { group -> group.graphInterface?.let { face -> face.inputs.any { it.type in setOf("position","mirroring") } || face.outputs.any { it.type in setOf("position","mirroring") } }==true } || nodes.any { !it.copycatTransforms.isNullOrEmpty() || it.kind in setOf(NodeKind.MIRRORING,NodeKind.POSITION,NodeKind.TRANSLATE) || it.rotationPivot!=null && it.rotationPivot!=P(8,8,8) || it.kind==NodeKind.MIRROR && it.inputs.size>1 }
    val arithmetic=nodes.any { it.kind.name in GraphValues.kinds && (it.kind!=NodeKind.POSITION || it.inputs.isNotEmpty()) }
    val variadic=nodes.any { it.kind.name in GraphValues.variadicKinds && it.inputs.size>2 || it.kind in CopycatSetInputs.shapeKinds && it.inputs.size<2 }
    val next=maxOf(schema,if(variadic) 6 else if(arithmetic) 5 else if(modern) 4 else schema)
    return copy(schema=next)
}
