package de.teutonstudio.copycatarchitect.domain

/** Input identities remain S/R/P; only their presentation and evaluation order changes. */
enum class TransformationOrder {
    SRP, SPR, RSP, RPS, PRS, PSR;
    val slots:List<Int> get()=name.map { "SRP".indexOf(it) }
    fun apply(steps:List<CopycatTransform>):List<CopycatTransform> = slots.flatMap { slot -> steps.filter { it.kind==listOf(CopycatTransformKind.MIRROR,CopycatTransformKind.ROTATE,CopycatTransformKind.POSITION)[slot] } }
}

/** A structured value, not a component-wise vector. SRP is the default. */
object TransformationValues {
    fun shape(n:GraphNode,nodes:Map<String,GraphNode>):List<CopycatTransform> {
        require(n.kind==NodeKind.TRANSFORMATION) { "Connect a transformation value" }
        fun source(slot:Int)=n.inputs.getOrNull(slot)?.takeIf(String::isNotEmpty)?.let(nodes::getValue)
        val mirror=source(0)?.let { it.mirror ?: CopycatMirror() } ?: n.mirror
        val rotation=source(1) ?: n
        val point=source(2)?.let { GraphValues.shape(nodes).vector(it.id) } ?: n.translation ?: P()
        require(listOf(point.x,point.y,point.z).all { it in -2048..2048 }) { "Transformation position must be between −2048 and 2048 hB" }
        return buildList {
            mirror?.let { add(CopycatTransform(CopycatTransformKind.MIRROR,plane=it.plane)) }
            if(rotation.quarterTurns!=0) add(CopycatTransform(axis=rotation.rotationAxis.takeUnless { it==RotationAxis.NORMAL } ?: RotationAxis.Y,quarterTurns=rotation.quarterTurns,pivot=rotation.rotationPivot ?: P(8,8,8)))
            if(point!=P()) add(CopycatTransform(CopycatTransformKind.POSITION,offset=point))
        }.let { (n.transformationOrder ?: TransformationOrder.SRP).apply(it) }
    }
    fun building(n:BuildingNode,nodes:Map<String,BuildingNode>):List<CopycatTransform> {
        require(n.kind==BuildingKind.TRANSFORMATION) { "Connect a transformation value" }
        val mirror=n.mirroring?.takeIf(String::isNotEmpty)?.let(nodes::getValue)
        val rotation=n.rotation?.takeIf(String::isNotEmpty)?.let(nodes::getValue)
        val point=n.positionValue?.takeIf(String::isNotEmpty)?.let { GraphValues.building(nodes).vector(it) } ?: n.translation ?: P()
        require(listOf(point.x,point.y,point.z).all { it in -2048..2048 }) { "Transformation position must be between −2048 and 2048 hB" }
        return buildList {
            if(mirror!=null) addAll(mirror.mirrors.orEmpty().map { CopycatTransform(CopycatTransformKind.MIRROR,plane=it.plane) })
            else n.mirror?.let { add(CopycatTransform(CopycatTransformKind.MIRROR,plane=it.plane)) }
            if(rotation!=null) addAll(rotation.rotations.orEmpty().map { CopycatTransform(axis=it.axis,quarterTurns=it.quarterTurns,pivot=rotation.rotationPivot ?: P(8,8,8)) })
            else if(n.quarterTurns!=0) add(CopycatTransform(axis=n.rotationAxis,quarterTurns=n.quarterTurns,pivot=n.rotationPivot ?: P(8,8,8)))
            if(point!=P()) add(CopycatTransform(CopycatTransformKind.POSITION,offset=point))
        }.let { (n.transformationOrder ?: TransformationOrder.SRP).apply(it) }
    }
    fun describe(steps:List<CopycatTransform>,order:TransformationOrder?=null)=if(steps.isEmpty()) "Identität · ${order ?: TransformationOrder.SRP}" else steps.joinToString(" → ") { when(it.kind) {
        CopycatTransformKind.MIRROR->"Spiegeln ${it.plane}"
        CopycatTransformKind.ROTATE->"Rotieren ${it.axis} ${it.quarterTurns*90}°"
        CopycatTransformKind.POSITION->"Position ${it.offset} hB"
    } }
    fun segment(n:BuildingNode,graph:BuildingGraph,value:FixedSegment,forms:MutableMap<String,Form>):FixedSegment {
        val nodes=graph.nodes.associateBy { it.id }
        val steps=n.transformationValue?.takeIf(String::isNotEmpty)?.let { building(nodes.getValue(it),nodes) }.orEmpty()
        return steps.fold(value) { segment,step -> when(step.kind) {
            CopycatTransformKind.MIRROR->{
                require(step.plane.name in listOf("X","Y","Z")) { "Segment mirroring requires an X/Y/Z plane" }
                SegmentGeometry.mirror(segment,RotationAxis.valueOf(step.plane.name))
            }
            CopycatTransformKind.ROTATE->SegmentGeometry.rotated(segment,step.axis,step.quarterTurns)
            CopycatTransformKind.POSITION->SegmentPosition.translate(segment,step.offset,forms,n.id)
        } }.let { result -> if(n.materialMode==MaterialMode.GROUPED) result.copy(cells=result.cells.map { it.copy(overrideRole=n.role) }) else result }
    }
}

/** Retains signed block origins. Sub-block shifts split exact Copycat geometry at cell boundaries. */
object SegmentPosition {
    fun translate(value:FixedSegment,offset:P,forms:MutableMap<String,Form>,id:String):FixedSegment {
        val blocks=P(Math.floorDiv(offset.x,16),Math.floorDiv(offset.y,16),Math.floorDiv(offset.z,16))
        val remainder=P(Math.floorMod(offset.x,16),Math.floorMod(offset.y,16),Math.floorMod(offset.z,16))
        val origin=value.origin+blocks
        if(remainder==P()) return value.copy(origin=origin,cells=value.cells.map { it.copy(cell=it.cell.copy(pos=it.cell.pos+blocks)) },metadata=value.metadata.moved(blocks,"position:$id"))
        require(!value.air && value.cells.all { !it.cell.empty && it.cell.block.isEmpty() }) { "Sub-block segment positions require Copycats; normal blocks and air move in whole blocks (16 hB)" }
        require(value.metadata.instances.isEmpty()) { "Segments with semantic openings move in whole blocks (16 hB)" }
        val size=P(value.length+if(remainder.x>0) 1 else 0,value.height+if(remainder.y>0) 1 else 0,value.depth+if(remainder.z>0) 1 else 0)
        require(listOf(size.x,size.y,size.z).all { it in 1..128 } && size.x.toLong()*size.y*size.z<=262144) { "Translated segment exceeds its limits" }
        val cache=mutableMapOf<SegmentCell,List<SegmentCell>>()
        val cells=value.cells.flatMap { entry ->
            val local=entry.copy(cell=entry.cell.copy(pos=P()))
            cache.getOrPut(local) {
                val form=Geometry.validate(forms[local.cell.form] ?: error("Missing form ${local.cell.form}"))
                val parts=SegmentGeometry.parts(local.cell,form.parts)
                val shifted=parts.map { it to ExactGeometry.part(it).translated(remainder) }
                buildList {
                    for(x in 0..if(remainder.x>0) 1 else 0) for(y in 0..if(remainder.y>0) 1 else 0) for(z in 0..if(remainder.z>0) 1 else 0) {
                        val pixel=P(16*x,16*y,16*z)
                        val box=ExactVolume.box(pixel.x,pixel.y,pixel.z,pixel.x+16,pixel.y+16,pixel.z+16)
                        val fragments=shifted.mapNotNull { (part,volume) -> val clipped=volume.intersect(box).translated(P(-pixel.x,-pixel.y,-pixel.z))
                            if(clipped.isEmpty()) null else part.copy(exact=clipped,exactClip=null,cutMask="",offset=P(),turn=0,spin=0,tilt=0,top=false) }
                        if(fragments.isNotEmpty()) {
                            val key="position_${id}_${forms.size}@1"
                            forms[key]=Form(name="Position",groups=form.groups,parts=fragments)
                            add(local.copy(cell=local.cell.copy(pos=P(x,y,z),form=key,turn=0,rotations=emptyList())))
                        }
                    }
                }
            }.map { it.copy(cell=it.cell.copy(pos=it.cell.pos+entry.cell.pos+blocks)) }
        }
        require(cells.size<=262144) { "Translated segment exceeds its limits" }
        return value.copy(length=size.x,height=size.y,depth=size.z,cells=cells,origin=origin)
    }
}
