package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*

/** Local defaults complement the connected S/R/P values and commit as one edit. */
object TransformationValueEditor {
    fun open(node:GraphNode,apply:(GraphNode)->Unit) = open(node.transformationOrder,node.rotationAxis,node.quarterTurns,node.rotationPivot,node.translation,node.mirror) { order,axis,turn,pivot,position,mirror ->
        apply(node.copy(transformationOrder=order,rotationAxis=axis,quarterTurns=turn,rotationPivot=pivot,translation=position,mirror=mirror))
    }
    fun open(node:BuildingNode,apply:(BuildingNode)->Unit) = open(node.transformationOrder,node.rotationAxis,node.quarterTurns,node.rotationPivot,node.translation,node.mirror) { order,axis,turn,pivot,position,mirror ->
        apply(node.copy(transformationOrder=order,rotationAxis=axis,quarterTurns=turn,rotationPivot=pivot,translation=position,mirror=mirror))
    }
    private fun open(initialOrder:TransformationOrder?,initialAxis:RotationAxis,initialTurn:Int,initialPivot:P?,initialPosition:P?,initialMirror:CopycatMirror?,apply:(TransformationOrder,RotationAxis,Int,P,P,CopycatMirror?)->Unit) {
        var order=initialOrder ?: TransformationOrder.SRP
        var axis=initialAxis.takeUnless { it==RotationAxis.NORMAL } ?: RotationAxis.Y
        var turn=initialTurn;var pivot=initialPivot ?: P(8,8,8);var position=initialPosition ?: P()
        var mirror=initialMirror
        StructurePropertyScreen.open(tr("copycat_transforms"),{
            choice(ui("transform_order"),order,TransformationOrder.entries,{it.name}) {order=it}
            toggle(ui("transform_mirror"),mirror!=null) {mirror=if(it) CopycatMirror() else null}
            if(mirror!=null) choice(ui("transform_plane"),mirror!!.plane,CopycatMirrorPlane.entries,{it.name}) {mirror=CopycatMirror(it)}
            choice(ui("transform_axis"),axis,listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z),{it.name}) {axis=it}
            choice(ui("transform_angle"),turn,(0..3).toList(),{"${it*90}°"}) {turn=it}
            point(ui("rotation_pivot"),pivot,0..16) {pivot=it}
            point(ui("position_hb"),position,-2048..2048) {position=it}
        },{apply(order,axis,turn,pivot,position,mirror)})
    }
}
