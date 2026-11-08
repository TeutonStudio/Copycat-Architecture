package de.teutonstudio.copycatarchitect.domain

/** Instantiated semantic requests collected before room/storey caches are released. */
internal class BuildingAttachments(private val graph:BuildingGraph,private val segment:(String)->FixedSegment,private val palette:Map<String,String> = emptyMap(),
                                   private val copycat:(String)->Placement={ error("Missing opening mask Copycat $it") }) {
    val columns=mutableListOf<PlacedColumn>()
    val plates=mutableListOf<PlacedPlateAttachment>()
    val walls=mutableListOf<WallAttachmentSurface>()
    private val nodes=graph.nodes.associateBy { it.id }
    private data class OpeningMask(val sourcePath:String,val retain:Boolean,val volume:ExactVolume,val minimum:P,val maximum:P)
    private val masks=mutableListOf<OpeningMask>()
    private var facadeDepth=1
    private fun forced(node:BuildingNode?)=node?.takeIf { it.materialMode==MaterialMode.GROUPED }?.let { palette[it.role] ?: error("Missing material role ${it.role}") }

    fun collect(storey:BuildingNode,story:CompiledStorey,offset:P,instance:String,room:(String)->SemanticRoom) {
        facadeDepth=storey.facade?.source?.takeIf(String::isNotEmpty)?.let { segment(it).depth } ?: 1
        val material=forced(graph.nodes.singleOrNull { it.kind==BuildingKind.BUILDING }) ?: forced(storey)
        val storyWalls=story.plan.placements.filter { it.component in setOf("wall","trim") }
        recordWall(SurfaceRef(storey.id,instance),storyWalls.map { it.copy(pos=it.pos+offset) })
        if(storey.storeyMode=="walls") {
            legacyColumns(graph.resolvedNode(storey),offset,instance,storyWalls,material)
            nodes[storey.ceiling]?.attachments.orEmpty().forEach { spec -> plates+=PlacedPlateAttachment(storey.ceiling,instance,spec,story.plan.placements.filter { it.component=="ceiling" }.map { it.copy(pos=it.pos+offset) }) }
            return
        }
        storey.inputs.forEachIndexed { index,id ->
            val definition=nodes.getValue(id);val value=room(id)
            val roomMaterial=material ?: forced(definition)
            val localOffset=storey.offsets.getOrNull(index) ?: P()
            val global=offset+localOffset;val path="$instance/room:$index"
            if(definition.roomConfig?.mode==RoomMode.SEGMENTS) {
                value.topology.instances.forEach { item ->
                    if(item.definition.policy==WallPolicy.OPEN) return@forEach
                    val original=segment(item.source)
                    val frame=SemanticTransform.rotation(RotationAxis.Y,item.definition.quarterTurns,P(original.length,original.height,original.depth)).moved(global+item.position)
                    val depth=if(item.definition.policy==WallPolicy.AUTO) maxOf(original.depth,facadeDepth) else original.depth
                    addColumns(item.value.metadata.moved(global+item.position,"$path/${item.definition.id}"),roomMaterial,original.depth,worldBox(frame,P(original.length,original.height,depth)))
                    val occupied=item.value.cells.map { it.cell.pos+item.position+localOffset }.toSet()
                    val actual=storyWalls.filter { it.pos in occupied }.map { it.copy(pos=it.pos+offset) }
                    recordWall(SurfaceRef(item.source,"$path/${item.definition.id}"),actual)
                }
            } else legacyColumns(graph.resolvedNode(definition),global,path,storyWalls.map { it.copy(pos=it.pos-localOffset) },roomMaterial)
            val shell=value.topology.shell
            val actual=storyWalls.filter { (it.pos-localOffset).copy(y=0) in shell }
            walls+=WallAttachmentSurface(SurfaceRef(id,path),actual.map { it.copy(pos=it.pos+offset) })
            val faceNames=listOf("north","east","south","west")
            for(direction in 0..3) {
                val spans=value.topology.spans.filter { it.direction==direction }
                val normal=RoomGeometry.directions[direction]
                val surface=actual.filter { placement ->
                    val p=(placement.pos-localOffset).copy(y=0)
                    spans.any { span ->
                        val base=RoomTopology.interiorColumn(span.start,direction)
                        val delta=p-base
                        if(normal.x==0) delta.x==0 && delta.z*normal.z<=0 else delta.z==0 && delta.x*normal.x<=0
                    }
                }.map { it.copy(pos=it.pos+offset) }
                walls+=WallAttachmentSurface(SurfaceRef(id,path,faceNames[direction]),surface)
            }
            nodes[definition.ceiling]?.let { ceiling ->
                val placements=value.compiled.plan.placements.filter { it.component=="ceiling" }.map { p ->
                    val moved=p.copy(pos=p.pos+global)
                    if(roomMaterial==null || moved.block.isNotEmpty()) moved else moved.copy(material=roomMaterial,surfaces=moved.groups.associate { it.id to roomMaterial })
                }
                ceiling.attachments.orEmpty().forEach { plates+=PlacedPlateAttachment(ceiling.id,path,it,placements) }
            }
        }
    }

    private fun addColumns(metadata:SegmentMetadata,materialOverride:String?,wallDepth:Int,wallBand:ExactVolume) {
        metadata.instances.forEach { feature ->
            feature.features.extensions.forEach { columns+=PlacedColumn(feature,it,materialOverride) }
            for(spec in feature.features.openings) {
                val depth=maxOf(wallDepth,facadeDepth)
                val opening=ResolvedRoomOpening(spec.id,feature.path,feature.transform,spec,feature.dimensions)
                val volume=SemanticStoreys.openingVolume(opening,copycat,-(depth+2)*16,(depth+feature.dimensions.z+2)*16).intersect(wallBand)
                val vertices=volume.cells.flatMap { it.vertices() }
                if(vertices.isEmpty()) continue
                val low=P(kotlin.math.floor(vertices.minOf { it.x.toDouble() }/16).toInt(),kotlin.math.floor(vertices.minOf { it.y.toDouble() }/16).toInt(),kotlin.math.floor(vertices.minOf { it.z.toDouble() }/16).toInt())
                val high=P(kotlin.math.ceil(vertices.maxOf { it.x.toDouble() }/16).toInt(),kotlin.math.ceil(vertices.maxOf { it.y.toDouble() }/16).toInt(),kotlin.math.ceil(vertices.maxOf { it.z.toDouble() }/16).toInt())
                masks+=OpeningMask(feature.path,spec.retainSource,volume,low,high)
            }
        }
    }

    private fun worldBox(frame:SemanticTransform,size:P):ExactVolume {
        val corners=buildList { for(x in listOf(0,size.x)) for(y in listOf(0,size.y)) for(z in listOf(0,size.z)) add(frame.point(P(x,y,z))) }
        return ExactVolume.box(corners.minOf { it.x }*16,corners.minOf { it.y }*16,corners.minOf { it.z }*16,corners.maxOf { it.x }*16,corners.maxOf { it.y }*16,corners.maxOf { it.z }*16)
    }

    /** Masks remain authoritative after roof-bound columns and wall-bound plate extensions. */
    fun openingMask(sourcePath:String,block:P):ExactVolume=masks.asSequence().filter { mask ->
        !(mask.retain && sourcePath==mask.sourcePath) && block.x in mask.minimum.x until mask.maximum.x && block.y in mask.minimum.y until mask.maximum.y && block.z in mask.minimum.z until mask.maximum.z
    }.fold(ExactVolume()) { all,mask -> all.union(mask.volume.translated(P(-block.x*16,-block.y*16,-block.z*16))) }.intersect(ExactVolume.box(0,0,0,16,16,16))

    private fun recordWall(reference:SurfaceRef,placements:List<Placement>) {
        walls+=WallAttachmentSurface(reference,placements)
        for((direction,normal) in RoomGeometry.directions.withIndex()) {
            val nearest=placements.groupBy { (if(normal.x==0) it.pos.x else it.pos.z) to it.pos.y }.values.flatMap { row ->
                val edge=row.maxOf { it.pos.x*normal.x+it.pos.z*normal.z }
                row.filter { it.pos.x*normal.x+it.pos.z*normal.z==edge }
            }
            walls+=WallAttachmentSurface(reference.copy(face=listOf("north","east","south","west")[direction]),nearest)
        }
    }

    private fun legacyColumns(room:BuildingNode,offset:P,path:String,localWalls:List<Placement>,materialOverride:String?) {
        RoomWallJoints.runs(room).forEach { run ->
            val id=room.walls[run.id].orEmpty()
            if(id.isEmpty()) return@forEach
            val value=segment(id);val normal=RoomGeometry.directions[run.direction]
            val x=P(-normal.z,0,normal.x);val y=P(0,1,0);val z=P(-normal.x,0,-normal.z)
            val first=run.cells.first()+if(room.usesJoinedWallEnds) P() else P(normal.x*(value.depth-1),0,normal.z*(value.depth-1))
            val correction=P(minOf(0,x.x)+minOf(0,y.x)+minOf(0,z.x),minOf(0,x.y)+minOf(0,y.y)+minOf(0,z.y),minOf(0,x.z)+minOf(0,y.z)+minOf(0,z.z))
            val transform=SemanticTransform(first-correction,x,y,z)
            addColumns(value.metadata.transformed(transform).moved(offset,"$path/wall:${run.id}"),materialOverride,value.depth,worldBox(transform.moved(offset),P(value.length,value.height,value.depth)))
            val occupied=buildSet { for(i in 0 until value.length) for(j in 0 until value.height) for(k in 0 until value.depth) add(transform.cell(P(i,j,k))) }
            val physical=localWalls.filter { it.pos in occupied }.map { it.copy(pos=it.pos+offset) }
            recordWall(SurfaceRef(id,"$path/wall:${run.id}"),physical)
            walls+=WallAttachmentSurface(SurfaceRef(room.id,path,run.id),physical)
        }
        for(edge in RoomVerticalEdges.of(room.rectangles)) {
            val id=room.verticalEdges.orEmpty()[edge.id]?.takeIf(String::isNotEmpty) ?: continue
            val value=segment(id)
            val origin=if(room.usesJoinedWallEnds) edge.pos-edge.normalX-edge.normalZ else edge.pos+P(edge.normalX.x*(value.length-1)+edge.normalZ.x*(value.depth-1),0,edge.normalX.z*(value.length-1)+edge.normalZ.z*(value.depth-1))
            val x=P(1,0,0).turn(edge.turn);val y=P(0,1,0);val z=P(0,0,1).turn(edge.turn)
            val correction=P(minOf(0,x.x)+minOf(0,z.x),0,minOf(0,x.z)+minOf(0,z.z))
            val transform=SemanticTransform(origin-correction,x,y,z)
            addColumns(value.metadata.transformed(transform).moved(offset,"$path/edge:${edge.id}"),materialOverride,value.depth,worldBox(transform.moved(offset),P(value.length,value.height,value.depth)))
            val occupied=value.cells.map { transform.cell(it.cell.pos) }.toSet()
            recordWall(SurfaceRef(id,"$path/edge:${edge.id}"),localWalls.filter { it.pos in occupied }.map { it.copy(pos=it.pos+offset) })
        }
    }
}
