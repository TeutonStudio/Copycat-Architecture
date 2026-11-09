package de.teutonstudio.copycatarchitect.domain

/** Authoring intent is retained alongside geometry until the building context is known. */
enum class RoomMode { FOOTPRINT, SEGMENTS }
enum class WallPolicy { AUTO, EXPLICIT, OPEN }
enum class RoomContribution { BOUNDARY, OPENING, STRUCTURE, DECORATION }

data class RoomSegmentInstance(
    val id:String="", val sourceIndex:Int=0, val position:P=P(), val quarterTurns:Int=0,
    val policy:WallPolicy=WallPolicy.AUTO, val contribution:RoomContribution=RoomContribution.BOUNDARY
)
/** Boundary vertices are block-grid corners. The final vertex connects to the first. */
data class RoomBoundaryRule(val start:P=P(),val end:P=P(1,0,0),val policy:WallPolicy=WallPolicy.OPEN)
data class RoomLayoutConfig(val instances:List<RoomSegmentInstance> = emptyList(),val boundary:List<P> = emptyList(),val filler:String="",val boundaryRules:List<RoomBoundaryRule> = emptyList())
data class RoomConfig(val mode:RoomMode=RoomMode.FOOTPRINT,val heightOverride:Int?=null,val interiorSource:String="",val defaultPartition:String="")
data class FacadeConfig(val source:String="",val filler:String="")

/** A node plus a concrete placement path and stable surface key, never a material role. */
data class SurfaceRef(val node:String="",val instance:String="",val face:String="")

/** Rectangle dimensions/position use blocks; maskSource uses the connected Copycat's exact geometry. */
data class OpeningSpec(
    val id:String="",val position:P=P(),val size:P=P(1,2,1),val maskSource:String="",val insetSource:String="",
    val retainSource:Boolean=true
)
/** [shaftMaxY] is exclusive. The shaft interval is repeated without stretching the base. Gap is in sixteenths. */
data class ColumnExtensionSpec(
    val id:String="",val shaftMinY:Int=0,val shaftMaxY:Int=1,val profileSource:String="",val roof:SurfaceRef=SurfaceRef(),val gap:Int=0
)
data class SegmentFeatures(val openings:List<OpeningSpec> = emptyList(),val extensions:List<ColumnExtensionSpec> = emptyList()) {
    val isEmpty get()=openings.isEmpty() && extensions.isEmpty()
}
/** The chosen horizontal edge is extended to target; gap is in sixteenths of a block. */
data class AttachmentSpec(val id:String="",val edge:Int=0,val target:SurfaceRef=SurfaceRef(),val gap:Int=0)

/** Exact orthogonal affine transform in block-boundary coordinates (not cell indices). */
data class SemanticTransform(
    val origin:P=P(),val xAxis:P=P(1,0,0),val yAxis:P=P(0,1,0),val zAxis:P=P(0,0,1),
    val shapeXAxis:P?=null,val shapeYAxis:P?=null,val shapeZAxis:P?=null
) {
    fun vector(p:P)=P(xAxis.x*p.x+yAxis.x*p.y+zAxis.x*p.z,xAxis.y*p.x+yAxis.y*p.y+zAxis.y*p.z,xAxis.z*p.x+yAxis.z*p.y+zAxis.z*p.z)
    fun point(p:P)=origin+vector(p)
    fun microPoint(p:P)=P(origin.x*16,origin.y*16,origin.z*16)+vector(p)
    /** A reflected cell's minimum lies one unit before its reflected boundary. */
    fun cell(p:P):P=point(p)+P(minOf(0,xAxis.x)+minOf(0,yAxis.x)+minOf(0,zAxis.x),minOf(0,xAxis.y)+minOf(0,yAxis.y)+minOf(0,zAxis.y),minOf(0,xAxis.z)+minOf(0,yAxis.z)+minOf(0,zAxis.z))
    private fun shapeVector(p:P):P {
        val x=shapeXAxis ?: xAxis;val y=shapeYAxis ?: yAxis;val z=shapeZAxis ?: zAxis
        return P(x.x*p.x+y.x*p.y+z.x*p.z,x.y*p.x+y.y*p.y+z.y*p.z,x.z*p.x+y.z*p.y+z.z*p.z)
    }
    /** Includes position-only mirrors, which intentionally leave within-cell geometry unreflected. */
    fun microCellPoint(cell:P,local:P):P {
        val p=this.cell(cell);val x=shapeXAxis ?: xAxis;val y=shapeYAxis ?: yAxis;val z=shapeZAxis ?: zAxis
        return P(p.x*16,p.y*16,p.z*16)+shapeVector(local)+P(-16*(minOf(0,x.x)+minOf(0,y.x)+minOf(0,z.x)),-16*(minOf(0,x.y)+minOf(0,y.y)+minOf(0,z.y)),-16*(minOf(0,x.z)+minOf(0,y.z)+minOf(0,z.z)))
    }
    fun then(parent:SemanticTransform):SemanticTransform {
        val x=parent.vector(xAxis);val y=parent.vector(yAxis);val z=parent.vector(zAxis)
        val sx=parent.shapeVector(shapeXAxis ?: xAxis);val sy=parent.shapeVector(shapeYAxis ?: yAxis);val sz=parent.shapeVector(shapeZAxis ?: zAxis)
        return SemanticTransform(parent.point(origin),x,y,z,sx.takeUnless { it==x },sy.takeUnless { it==y },sz.takeUnless { it==z })
    }
    fun moved(offset:P)=copy(origin=origin+offset)
    fun validate() {
        for(axes in listOf(listOf(xAxis,yAxis,zAxis),listOf(shapeXAxis ?: xAxis,shapeYAxis ?: yAxis,shapeZAxis ?: zAxis)))
            require(axes.all { kotlin.math.abs(it.x)+kotlin.math.abs(it.y)+kotlin.math.abs(it.z)==1 } && axes.map { listOf(kotlin.math.abs(it.x),kotlin.math.abs(it.y),kotlin.math.abs(it.z)) }.distinct().size==3) { "Semantic transform must use three orthogonal unit axes" }
    }
    companion object {
        fun rotation(axis:RotationAxis,quarterTurns:Int,dimensions:P):SemanticTransform {
            require(axis!=RotationAxis.NORMAL) { "A semantic rotation needs a world axis" }
            var result=SemanticTransform();var d=dimensions
            repeat(Math.floorMod(quarterTurns,4)) {
                val step=when(axis) {
                    RotationAxis.X->SemanticTransform(P(0,d.z,0),P(1,0,0),P(0,0,1),P(0,-1,0))
                    RotationAxis.Y->SemanticTransform(P(d.z,0,0),P(0,0,1),P(0,1,0),P(-1,0,0))
                    RotationAxis.Z->SemanticTransform(P(d.y,0,0),P(0,1,0),P(-1,0,0),P(0,0,1))
                    RotationAxis.NORMAL->error("A semantic rotation needs a world axis")
                }
                result=result.then(step)
                d=when(axis) { RotationAxis.X->P(d.x,d.z,d.y);RotationAxis.Y->P(d.z,d.y,d.x);RotationAxis.Z->P(d.y,d.x,d.z);else->d }
            }
            return result
        }
        fun reflection(axis:RotationAxis,dimensions:P,positionsOnly:Boolean=false):SemanticTransform=(when(axis) {
            RotationAxis.X->SemanticTransform(P(dimensions.x,0,0),P(-1,0,0))
            RotationAxis.Y->SemanticTransform(P(0,dimensions.y,0),yAxis=P(0,-1,0))
            RotationAxis.Z->SemanticTransform(P(0,0,dimensions.z),zAxis=P(0,0,-1))
            RotationAxis.NORMAL->error("A semantic reflection needs a world axis")
        }).let { if(positionsOnly) it.copy(shapeXAxis=P(1,0,0),shapeYAxis=P(0,1,0),shapeZAxis=P(0,0,1)) else it }
    }
}

/** Original local features plus their placement; unions never erase the identity of a column or opening. */
data class SegmentFeatureInstance(
    val path:String="",val source:String="",val dimensions:P=P(1,1,1),val features:SegmentFeatures=SegmentFeatures(),
    val transform:SemanticTransform=SemanticTransform(),val materialRole:String=""
)
data class SegmentMetadata(val instances:List<SegmentFeatureInstance> = emptyList()) {
    val isEmpty get()=instances.isEmpty()
    fun transformed(transform:SemanticTransform)=copy(instances=instances.map { it.copy(transform=it.transform.then(transform)) })
    fun moved(offset:P,pathPrefix:String="")=copy(instances=instances.map { it.copy(path=listOf(pathPrefix,it.path).filter(String::isNotEmpty).joinToString("/"),transform=it.transform.moved(offset)) })
    operator fun plus(other:SegmentMetadata)=SegmentMetadata(instances+other.instances)
    companion object {
        fun of(source:String,dimensions:P,features:SegmentFeatures?):SegmentMetadata = if(features==null || features.isEmpty) SegmentMetadata()
            else SegmentMetadata(listOf(SegmentFeatureInstance(source,source,dimensions,features)))
    }
}

/** One reference registry shared by deletion, graph copies, group instances and diagram edits. */
object BuildingSemantics {
    fun annotate(segment:FixedSegment,source:String,features:SegmentFeatures?,overrideRole:String=""):FixedSegment {
        var metadata=segment.metadata+SegmentMetadata.of(source,P(segment.length,segment.height,segment.depth),features)
        if(overrideRole.isNotEmpty()) metadata=metadata.copy(instances=metadata.instances.map { it.copy(materialRole=overrideRole) })
        return if(metadata==segment.metadata) segment else segment.copy(metadata=metadata)
    }
    fun references(node:BuildingNode):Map<String,String> = buildMap { remapReferences(node) { key,ref -> if(ref.isNotEmpty()) put(key,ref);ref } }
    /** Targets are late-bound surfaces, so they must not introduce room → roof → room evaluation cycles. */
    fun structuralReferences(node:BuildingNode)=references(node).filterKeys { !it.startsWith("attachment:") && !(it.startsWith("column:") && it.endsWith(":roof")) }
    fun remapReferences(node:BuildingNode,reference:(String,String)->String):BuildingNode=node.copy(
        roomLayout=node.roomLayout?.let { it.copy(filler=reference("room:filler",it.filler)) },
        roomConfig=node.roomConfig?.let { it.copy(interiorSource=reference("room:interior",it.interiorSource),defaultPartition=reference("room:partition",it.defaultPartition)) },
        facade=node.facade?.let { it.copy(source=reference("facade:source",it.source),filler=reference("facade:filler",it.filler)) },
        features=node.features?.let { features -> features.copy(
            openings=features.openings.mapIndexed { i,it -> it.copy(maskSource=reference("opening:$i:mask",it.maskSource),insetSource=reference("opening:$i:inset",it.insetSource)) },
            extensions=features.extensions.mapIndexed { i,it -> it.copy(profileSource=reference("column:$i:profile",it.profileSource),roof=it.roof.copy(node=reference("column:$i:roof",it.roof.node))) }
        ) },
        attachments=node.attachments?.mapIndexed { i,it -> it.copy(target=it.target.copy(node=reference("attachment:$i:target",it.target.node))) }
    )
    fun validate(node:BuildingNode,nodes:Map<String,BuildingNode>) {
        require(references(node).values.all { it in nodes }) { "Unknown semantic source or attachment target" }
        val segmentKinds=(setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR)+SegmentBooleans.kinds)
        val copycatKinds=CopycatBooleans.sourceKinds
        fun source(id:String,kinds:Set<BuildingKind>,message:String) { if(id.isNotEmpty()) require(nodes.getValue(id).kind in kinds) { message } }
        node.roomLayout?.let { source(it.filler,segmentKinds,"Room filler requires a segment source") }
        node.roomConfig?.let { source(it.interiorSource,segmentKinds,"Room interior requires a segment source");source(it.defaultPartition,segmentKinds,"Room partition requires a segment source") }
        node.facade?.let { source(it.source,segmentKinds,"Facade requires a segment source");source(it.filler,segmentKinds,"Facade filler requires a segment source") }
        node.roomConfig?.heightOverride?.let { require(it in 1..128) { "Room height must be between 1 and 128 blocks" } }
        node.roomLayout?.let { layout ->
            require(layout.instances.size<=256) { "A room layout accepts at most 256 segment instances" }
            require(layout.instances.map { it.id }.distinct().size==layout.instances.size && layout.instances.all { validId(it.id) }) { "Room segment instance IDs must be unique and visible" }
            require(layout.instances.all { it.sourceIndex in node.inputs.indices && kotlin.math.abs(it.position.x.toLong())<=128 && kotlin.math.abs(it.position.z.toLong())<=128 && it.position.y in -64..128 }) { "Room segment placement lies outside the block grid or has no source" }
            require(layout.boundary.size<=256 && layout.boundary.all { it.y==0 && kotlin.math.abs(it.x.toLong())<=128 && kotlin.math.abs(it.z.toLong())<=128 }) { "Room boundary exceeds the block grid" }
            if(layout.boundary.isNotEmpty()) {
                require(layout.boundary.size>=4 && layout.boundary.distinct().size==layout.boundary.size) { "A room boundary needs at least four distinct corners" }
                require(layout.boundary.indices.all { i -> val a=layout.boundary[i];val b=layout.boundary[(i+1)%layout.boundary.size];(a.x==b.x) xor (a.z==b.z) }) { "Room boundary edges must follow the block grid" }
            }
            require(layout.boundaryRules.size<=256 && layout.boundaryRules.all { rule ->
                rule.start.y==0 && rule.end.y==0 && ((rule.start.x==rule.end.x) xor (rule.start.z==rule.end.z)) &&
                    listOf(rule.start.x,rule.start.z,rule.end.x,rule.end.z).all { it in -128..128 }
            }) { "Room boundary rules need horizontal orthogonal grid edges" }
        }
        node.features?.let { features ->
            require(features.openings.size<=128 && features.extensions.size<=128) { "A segment accepts at most 128 openings and 128 column extensions" }
            require(features.openings.map { it.id }.distinct().size==features.openings.size && features.openings.all { validId(it.id) }) { "Opening IDs must be unique and visible" }
            require(features.extensions.map { it.id }.distinct().size==features.extensions.size && features.extensions.all { validId(it.id) }) { "Column IDs must be unique and visible" }
            features.openings.forEach { opening ->
                require(listOf(opening.size.x,opening.size.y,opening.size.z).all { it in 1..128 } && listOf(opening.position.x,opening.position.y,opening.position.z).all { it in -128..128 }) { "Opening dimensions exceed 128 blocks" }
                source(opening.maskSource,copycatKinds,"An opening mask requires a Copycat source")
                source(opening.insetSource,copycatKinds,"An opening insert requires a Copycat source")
            }
            features.extensions.forEach {
                require(it.shaftMinY in 0..127 && it.shaftMaxY in (it.shaftMinY+1)..128 && it.gap in -256..256) { "Invalid repeated column shaft or roof gap" }
                source(it.profileSource,segmentKinds,"Column shaft requires a segment source")
                source(it.roof.node,setOf(BuildingKind.ROOF_LEVEL,BuildingKind.ROOF),"A column must target a roof level")
            }
        }
        val attachments=node.attachments.orEmpty()
        require(attachments.size<=64 && attachments.map { it.id }.distinct().size==attachments.size && attachments.all { validId(it.id) && it.edge in 0..3 && it.gap in -256..256 }) { "Invalid wall attachment" }
        attachments.forEach { source(it.target.node,segmentKinds+setOf(BuildingKind.ROOM,BuildingKind.STOREY),"A plate attachment must target a wall or room surface") }
        (node.features?.extensions.orEmpty().map { it.roof }+attachments.map { it.target }).forEach {
            require(it.node.length<=64 && it.instance.length<=256 && it.face.length<=128 && listOf(it.node,it.instance,it.face).none { s -> s.any(Char::isISOControl) }) { "Invalid surface reference" }
        }
    }
    private fun validId(id:String)=id.isNotBlank() && id.length<=64 && id.none(Char::isISOControl)
}
