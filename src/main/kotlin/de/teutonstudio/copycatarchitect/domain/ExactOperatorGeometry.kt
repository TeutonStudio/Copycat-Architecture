package de.teutonstudio.copycatarchitect.domain

/** Geometry and material ownership are resolved per block, never by occupancy alone. */
internal object ExactOperatorGeometry {
    fun apply(operation:String,inputs:List<Placement>,inverse:Placement?=null):Placement? {
        fun volume(p:Placement)=Geometry.exact(p.parts)
        val volumes=inputs.map(::volume)
        val visible=when(operation) {
            "UNION"->volumes.fold(ExactVolume(),ExactVolume::union)
            "INTERSECTION"->volumes.drop(1).fold(volumes.first(),ExactVolume::intersect)
            "DIFFERENCE"->volumes[0].minus(volumes[1])
            "SYMMETRIC_DIFFERENCE"->volumes.fold(ExactVolume()) { a,b -> a.minus(b).union(b.minus(a)) }
            "INVERT"->volume(requireNotNull(inverse)).minus(volumes.single())
            else->error("Not a set operation: $operation")
        }
        if(visible.isEmpty()) return null
        val owners=when(operation) { "INVERT"->listOf(requireNotNull(inverse));"INTERSECTION","DIFFERENCE"->inputs.take(1);else->inputs }
        val clipped=owners.mapNotNull { source ->
            val parts=source.parts.mapNotNull { part ->
                val clip=ExactGeometry.part(part).intersect(visible)
                if(clip.isEmpty()) null else part.copy(exactClip=clip)
            }
            if(parts.isEmpty()) null else source.copy(parts=parts)
        }
        return clipped.takeIf { it.isNotEmpty() }?.let(ExactPlacementUnion::merge)
    }

    /** Missing operands are empty sets. All entries are already in the same coordinate frame. */
    fun segments(node:BuildingNode,inputs:List<FixedSegment>,placement:(SegmentCell)->Placement,store:(String,Placement)->Cell):FixedSegment {
        val native=linkedMapOf<P,SegmentCell>()
        inputs.forEach { segment -> segment.cells.filter { !it.cell.empty && it.cell.block.isNotEmpty() && it.cell.block!="minecraft:air" }.forEach { entry ->
            val old=native.putIfAbsent(entry.cell.pos,entry)
            require(old==null || old.cell.block==entry.cell.block) { "Conflicting native blocks at ${entry.cell.pos}" }
        } }
        val geometry=inputs.map { it.cells.filter { c -> !c.cell.empty && c.cell.block.isEmpty() }.groupBy { c -> c.cell.pos } }
        val points=geometry.flatMap { it.keys }.toSet()
        val cells=mutableListOf<SegmentCell>()
        for(point in points.sortedWith(compareBy({it.y},{it.z},{it.x}))) {
            val args=geometry.map { entries -> entries[point]?.map(placement)?.let(ExactPlacementUnion::merge) ?: Placement(parts=emptyList()) }
            val result=apply(node.operation ?: "UNION",args) ?: continue
            if(point !in native) cells+=SegmentCell(store("${node.id}_${cells.size}",result).copy(pos=point),if(node.materialMode==MaterialMode.GROUPED) node.role else "")
        }
        cells+=native.values
        return bounded(cells,inputs.flatMap { it.metadata.instances }.let(::SegmentMetadata))
    }
    fun transform(node:BuildingNode,input:FixedSegment,offset:P,axis:RotationAxis,turns:Int):FixedSegment {
        val op=node.operation
        val native=input.cells.filter { it.cell.block.isNotEmpty() && it.cell.block!="minecraft:air" && !it.cell.empty }
        val nativePositions=native.map { it.cell.pos }.toSet()
        val changed=input.cells.filter { it.cell.block.isEmpty() && !it.cell.empty }.map { entry ->
            val cell=entry.cell
            var p=cell.pos
            val rotations=cell.rotations.toMutableList()
            when(op) {
                "TRANSLATE"->p=P(Math.addExact(p.x,offset.x),Math.addExact(p.y,offset.y),Math.addExact(p.z,offset.z))
                "ROTATE"->{
                    repeat(turns) { p=when(axis) { RotationAxis.X->P(p.x,-p.z-1,p.y);RotationAxis.Z->P(-p.y-1,p.x,p.z);else->P(-p.z-1,p.y,p.x) } }
                    rotations+=SegmentRotation(axis,turns)
                }
                "MIRROR"->{
                    p=when(axis) { RotationAxis.X->p.copy(x=-p.x-1);RotationAxis.Y->p.copy(y=-p.y-1);RotationAxis.Z->p.copy(z=-p.z-1);else->error("Mirror needs X/Y/Z") }
                    rotations+=SegmentRotation(axis,reflected=true)
                }
            }
            entry.copy(cell=cell.copy(pos=p,rotations=rotations),overrideRole=if(node.materialMode==MaterialMode.GROUPED) node.role else entry.overrideRole)
        }.filter { it.cell.pos !in nativePositions }
        // Native blocks and their positions are intentionally outside every operation.
        return bounded(changed+native,input.metadata)
    }
    private fun bounded(cells:List<SegmentCell>,metadata:SegmentMetadata):FixedSegment {
        require(cells.size<=262144) { "Segment exceeds 262144 cells" }
        val points=cells.map { it.cell.pos }
        val min=P(points.minOfOrNull { it.x } ?: 0,points.minOfOrNull { it.y } ?: 0,points.minOfOrNull { it.z } ?: 0)
        val max=P(points.maxOfOrNull { it.x } ?: 0,points.maxOfOrNull { it.y } ?: 0,points.maxOfOrNull { it.z } ?: 0)
        val dimensions=listOf(max.x.toLong()-min.x+1,max.y.toLong()-min.y+1,max.z.toLong()-min.z+1)
        require(dimensions.all { it in 1..128 } && dimensions.reduce(Long::times)<=262144) { "Segment dimensions exceed bounds" }
        return FixedSegment(dimensions[0].toInt(),dimensions[1].toInt(),cells,depth=dimensions[2].toInt(),metadata=metadata,origin=min)
    }
}
