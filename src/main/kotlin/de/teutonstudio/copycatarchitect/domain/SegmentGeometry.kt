package de.teutonstudio.copycatarchitect.domain

/** A disjoint part of a segment: 0/1 are ends, i is interior, b is both ends. */
data class SegmentRegion(val key:String,val cells:Set<P>)

/** Partitions the complete box, collapsing coincident faces, edges and corners. */
object SegmentRegions {
    private data class Range(val key:String,val indices:IntRange)
    private class RegionCells(private val xs:IntRange,private val ys:IntRange,private val zs:IntRange):AbstractSet<P>() {
        override val size:Int = Math.multiplyExact(Math.multiplyExact(xs.count(),ys.count()),zs.count())
        override fun contains(element:P)=element.x in xs && element.y in ys && element.z in zs
        override fun iterator():Iterator<P> = sequence { for(x in xs) for(y in ys) for(z in zs) yield(P(x,y,z)) }.iterator()
    }
    private fun axis(size:Int):List<Range> {
        require(size>0) { "Segment dimensions must be positive" }
        if(size==1) return listOf(Range("b",0..0))
        return buildList {
            add(Range("0",0..0))
            if(size>2) add(Range("i",1 until size-1))
            add(Range("1",size-1 until size))
        }
    }

    fun of(length:Int,height:Int,depth:Int):List<SegmentRegion> {
        val xs=axis(length);val ys=axis(height);val zs=axis(depth)
        return buildList {
            for(x in xs) for(y in ys) for(z in zs) add(SegmentRegion("region:x${x.key}_y${y.key}_z${z.key}",RegionCells(x.indices,y.indices,z.indices)))
        }
    }
}

data class SegmentRotation(val axis:RotationAxis=RotationAxis.Y,val quarterTurns:Int=0,val reflected:Boolean=false,val mirror:CopycatMirror?=null)
data class SegmentTransform(val offset:P=P(),val axis:RotationAxis=RotationAxis.Y,val quarterTurns:Int=0)

/** Integer box transformations retain cell geometry and explicit air requirements. */
object SegmentGeometry {
    private data class Box(val offset:P,val dimensions:P,val cells:List<SegmentCell>,val metadata:SegmentMetadata)

    /** The same local box used by composition, without applying its world offset. */
    fun rotated(segment:FixedSegment,axis:RotationAxis,quarterTurns:Int):FixedSegment {
        val box=transform(segment,SegmentTransform(axis=axis,quarterTurns=quarterTurns))
        return FixedSegment(box.dimensions.x,box.dimensions.y,box.cells,segment.air,box.dimensions.z,box.metadata)
    }

    /** The selected axis is normal to the mirror plane through this segment's center. */
    fun mirror(segment:FixedSegment,axis:RotationAxis,positionsOnly:Boolean=false):FixedSegment {
        require(axis!=RotationAxis.NORMAL) { "A segment reflection needs a world axis" }
        require(segment.length in 1..128 && segment.height in 1..128 && segment.depth in 1..128) { "Segment dimensions must be between 1 and 128" }
        require(segment.length.toLong()*segment.height*segment.depth<=262144) { "Segment exceeds 262144 cells" }
        val cells=segment.cells.map { entry ->
            val cell=entry.cell;val p=cell.pos
            require(p.x in 0 until segment.length && p.y in 0 until segment.height && p.z in 0 until segment.depth) { "Segment cell lies outside its dimensions" }
            val position=when(axis) {
                RotationAxis.X->p.copy(x=segment.length-1-p.x)
                RotationAxis.Y->p.copy(y=segment.height-1-p.y)
                RotationAxis.Z->p.copy(z=segment.depth-1-p.z)
                RotationAxis.NORMAL->error("A segment reflection needs a world axis")
            }
            entry.copy(cell=if(positionsOnly) cell.copy(pos=position) else cell.copy(pos=position,rotations=cell.rotations+SegmentRotation(axis,0,true)))
        }
        return segment.copy(cells=cells,metadata=segment.metadata.transformed(SemanticTransform.reflection(axis,P(segment.length,segment.height,segment.depth),positionsOnly)))
    }

    /** Offsets refer to the minimum corner of each rotated box, before final normalization. */
    fun union(items:List<Pair<FixedSegment,SegmentTransform>>):FixedSegment {
        require(items.isNotEmpty()) { "Connect at least one segment to unite" }
        val boxes=items.map { (segment,transform) -> transform(segment,transform) }
        val minimum=P(boxes.minOf { it.offset.x },boxes.minOf { it.offset.y },boxes.minOf { it.offset.z })
        val length=boxes.maxOf { it.offset.x.toLong()+it.dimensions.x }-minimum.x
        val height=boxes.maxOf { it.offset.y.toLong()+it.dimensions.y }-minimum.y
        val depth=boxes.maxOf { it.offset.z.toLong()+it.dimensions.z }-minimum.z
        require(length in 1..128 && height in 1..128 && depth in 1..128) { "Transformed segment dimensions exceed 128 blocks" }
        require(length*height*depth<=262144) { "Transformed segment exceeds 262144 cells" }
        val dimensions=P(length.toInt(),height.toInt(),depth.toInt())
        val cells=linkedMapOf<P,MutableList<SegmentCell>>()
        var contributions=0
        for(box in boxes) for(entry in box.cells) {
            val position=P((entry.cell.pos.x.toLong()+box.offset.x-minimum.x).toInt(),
                (entry.cell.pos.y.toLong()+box.offset.y-minimum.y).toInt(),(entry.cell.pos.z.toLong()+box.offset.z-minimum.z).toInt())
            val moved=entry.copy(cell=entry.cell.copy(pos=position))
            var previous=cells[position]
            if(previous?.contains(moved)==true) continue
            val clear=moved.cell.empty || moved.cell.block=="minecraft:air"
            if(clear && previous?.all { it.cell.empty || it.cell.block=="minecraft:air" }==true) continue
            // Empty cells in another segment must not obstruct the Copycat union.
            if(clear && previous?.all { !it.cell.empty && it.cell.block.isEmpty() }==true) continue
            if(!clear && moved.cell.block.isEmpty() && previous?.all { it.cell.empty || it.cell.block=="minecraft:air" }==true) {
                contributions-=previous.size
                cells.remove(position)
                previous=null
            }
            require(previous==null || !clear && moved.cell.block.isEmpty() && previous.all { !it.cell.empty && it.cell.block.isEmpty() }) { "Transformed segments conflict at $position" }
            require(++contributions<=262144) { "Transformed segment exceeds 262144 source cells" }
            cells.getOrPut(position) { mutableListOf() }+=moved
        }
        val ordered=cells.values.flatten().sortedWith(compareBy({ it.cell.pos.y },{ it.cell.pos.z },{ it.cell.pos.x }))
        val air=ordered.size.toLong()==dimensions.x.toLong()*dimensions.y*dimensions.z && ordered.all { it.cell.empty || it.cell.block=="minecraft:air" }
        val metadata=SegmentMetadata(boxes.flatMapIndexed { i,box ->
            box.metadata.moved(P(box.offset.x-minimum.x,box.offset.y-minimum.y,box.offset.z-minimum.z),"union:$i").instances
        })
        require(metadata.instances.size<=262144) { "Transformed segment exceeds 262144 semantic instances" }
        return FixedSegment(dimensions.x,dimensions.y,ordered,air,dimensions.z,metadata)
    }

    private fun transform(segment:FixedSegment,transform:SegmentTransform):Box {
        require(segment.length in 1..128 && segment.height in 1..128 && segment.depth in 1..128) { "Segment dimensions must be between 1 and 128" }
        require(segment.length.toLong()*segment.height*segment.depth<=262144) { "Segment exceeds 262144 cells" }
        require(transform.axis!=RotationAxis.NORMAL) { "A segment rotation needs a world axis" }
        val q=Math.floorMod(transform.quarterTurns,4)
        var dimensions=P(segment.length,segment.height,segment.depth)
        val source=if(segment.air) buildList {
            for(x in 0 until segment.length) for(y in 0 until segment.height) for(z in 0 until segment.depth)
                add(SegmentCell(Cell(P(x,y,z),block="minecraft:air",empty=true)))
        } else segment.cells
        require(source.all { it.cell.pos.let { p -> p.x in 0 until dimensions.x && p.y in 0 until dimensions.y && p.z in 0 until dimensions.z } }) { "Segment cell lies outside its dimensions" }
        var cells=source
        repeat(q) {
            cells=cells.map { entry ->
                val p=entry.cell.pos
                val position=when(transform.axis) {
                    RotationAxis.X->P(p.x,dimensions.z-1-p.z,p.y)
                    RotationAxis.Y->P(dimensions.z-1-p.z,p.y,p.x)
                    RotationAxis.Z->P(dimensions.y-1-p.y,p.x,p.z)
                    RotationAxis.NORMAL->error("A segment rotation needs a world axis")
                }
                entry.copy(cell=entry.cell.copy(pos=position))
            }
            dimensions=when(transform.axis) {
                RotationAxis.X->P(dimensions.x,dimensions.z,dimensions.y)
                RotationAxis.Y->P(dimensions.z,dimensions.y,dimensions.x)
                RotationAxis.Z->P(dimensions.y,dimensions.x,dimensions.z)
                RotationAxis.NORMAL->error("A segment rotation needs a world axis")
            }
        }
        if(q!=0) cells=cells.map { entry ->
            val cell=entry.cell
            val rotated=if(transform.axis==RotationAxis.Y && cell.rotations.isEmpty()) cell.copy(turn=Math.floorMod(cell.turn+q,4))
                else cell.copy(rotations=cell.rotations+SegmentRotation(transform.axis,q))
            entry.copy(cell=rotated)
        }
        val metadata=segment.metadata.transformed(SemanticTransform.rotation(transform.axis,q,P(segment.length,segment.height,segment.depth)))
        return Box(transform.offset,dimensions,cells,metadata)
    }

    /** Source Y rotation, ordered nested segment transformations, then mounting on a room wall. */
    fun parts(cell:Cell,parts:List<Part>,worldTurn:Int=0):List<Part> {
        var result=Geometry.rotate(parts,cell.turn)
        for(rotation in cell.rotations) {
            require(rotation.axis!=RotationAxis.NORMAL) { "A segment rotation needs a world axis" }
            result=if(rotation.mirror!=null) {
                require(rotation.quarterTurns==0 && !rotation.reflected) { "A Copycat mirror cannot also turn or reflect" }
                result.map { PartRotations.mirror(it,rotation.mirror) }
            } else if(rotation.reflected) {
                require(rotation.quarterTurns==0) { "A segment reflection cannot also turn" }
                result.map { PartRotations.mirror(it,rotation.axis) }
            } else result.map { part -> PartRotations.rotate(part,rotation.axis,rotation.quarterTurns)
                ?: error("Copycat source ${part.source} cannot rotate around ${rotation.axis}") }
        }
        return Geometry.rotate(result,worldTurn)
    }
}
