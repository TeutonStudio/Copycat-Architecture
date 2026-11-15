package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*

/** A private draft; accepting the sheet sends exactly one editor command. */
object CopycatTransformEditor {
    fun open(node:GraphNode,apply:(GraphNode)->Unit) {
        var draft=node
        open(node.kind.name,node.rotationAxis,node.quarterTurns,node.rotationPivot ?: P(8,8,8),node.translation ?: P(),node.mirror,node.copycatTransforms.orEmpty()) { axis,turn,pivot,position,mirror,steps ->
            draft=draft.copy(rotationAxis=axis,quarterTurns=turn,rotationPivot=pivot,translation=position,mirror=mirror,copycatTransforms=steps.takeIf { it.isNotEmpty() });apply(draft)
        }
    }
    fun open(node:BuildingNode,apply:(BuildingNode)->Unit) = open(node.kind.name,node.rotationAxis,node.quarterTurns,node.rotationPivot ?: P(8,8,8),node.translation ?: P(),node.mirror,node.copycatTransforms.orEmpty()) { axis,turn,pivot,position,mirror,steps ->
        apply(node.copy(rotationAxis=axis,quarterTurns=turn,rotationPivot=pivot,translation=position,mirror=mirror,rotations=if(node.kind==BuildingKind.ROTATION && (axis!=node.rotationAxis || turn!=node.quarterTurns)) listOf(SegmentRotation(axis,turn)) else node.rotations,mirrors=if(node.kind==BuildingKind.MIRRORING && mirror!=node.mirror) listOfNotNull(mirror) else node.mirrors,copycatTransforms=steps.takeIf { it.isNotEmpty() }))
    }
    private fun open(kind:String,initialAxis:RotationAxis,initialTurn:Int,initialPivot:P,initialPosition:P,initialMirror:CopycatMirror?,initial:List<CopycatTransform>,apply:(RotationAxis,Int,P,P,CopycatMirror?,List<CopycatTransform>)->Unit) {
        var axis=initialAxis;var turn=initialTurn;var pivot=initialPivot;var position=initialPosition;var mirror=initialMirror
        var steps=initial.toList()
        StructurePropertyScreen.open(tr("copycat_transforms"),{
            if(kind in setOf("ROTATE","ROTATION","COPYCAT_ROTATE")) {
                choice(ui("transform_axis"),axis,listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z),{it.name}) { axis=it }
                choice(ui("transform_angle"),turn,(0..3).toList(),{"${it*90}°"}) {turn=it}
                point(ui("rotation_pivot"),pivot,0..16) {pivot=it}
            }
            if(kind in setOf("MIRROR","MIRRORING","COPYCAT_MIRROR")) choice(ui("transform_plane"),mirror?.plane ?: CopycatMirrorPlane.X,CopycatMirrorPlane.entries,{it.name}) {mirror=CopycatMirror(it)}
            if(kind in setOf("POSITION","TRANSLATE","COPYCAT_POSITION")) point(ui("position_hb"),position,-16..16) {position=it}
            if(kind !in setOf("ROTATION","MIRRORING","POSITION")) {
                for(type in CopycatTransformKind.entries) {
                    hint(ui("transform_"+type.name.lowercase()))
                    steps.forEachIndexed { index,step -> if(step.kind==type) {
                        val prefix="${index+1}. "
                        fun change(next:CopycatTransform) {steps=steps.mapIndexed {i,old->if(i==index) next else old}}
                        toggle(prefix+ui("transform_enabled"),step.enabled) {change(step.copy(enabled=it))}
                        when(type) {
                            CopycatTransformKind.ROTATE->{
                                choice(prefix+ui("transform_axis"),step.axis,listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z),{it.name}) {change(step.copy(axis=it))}
                                choice(prefix+ui("transform_angle"),step.quarterTurns,(0..3).toList(),{"${it*90}°"}) {change(step.copy(quarterTurns=it))}
                                point(prefix+ui("rotation_pivot"),step.pivot,0..16) {change(step.copy(pivot=it))}
                            }
                            CopycatTransformKind.MIRROR->choice(prefix+ui("transform_plane"),step.plane,CopycatMirrorPlane.entries,{it.name}) {change(step.copy(plane=it))}
                            CopycatTransformKind.POSITION->point(prefix+ui("position_hb"),step.offset,-16..16) {change(step.copy(offset=it))}
                        }
                        action(prefix+ui("remove")) {steps=steps.filterIndexed {i,_->i!=index}}
                    } }
                    action("+ "+ui("transform_"+type.name.lowercase())) {if(steps.size<64) steps=steps+CopycatTransform(kind=type)}
                }
                hint(ui("transform_order"))
                steps.forEachIndexed {index,step->
                    hint("${index+1}. ${ui("transform_"+step.kind.name.lowercase())}")
                    if(index>0) action("↑ ${index+1}") {steps=steps.toMutableList().apply {add(index-1,removeAt(index))}}
                    if(index<steps.lastIndex) action("↓ ${index+1}") {steps=steps.toMutableList().apply {add(index+1,removeAt(index))}}
                }
            }
        },{CopycatTransforms.validate(steps);apply(axis,turn,pivot,position,mirror,steps)})
    }
}
