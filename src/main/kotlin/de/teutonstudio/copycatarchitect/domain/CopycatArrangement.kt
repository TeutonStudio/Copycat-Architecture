package de.teutonstudio.copycatarchitect.domain

/** Explicit per-input positions; legacy repeating walls keep their original behavior. */
object CopycatArrangement {
    fun initial(n:BuildingNode):List<P> = n.inputs.indices.map { i ->
        if(n.copycatArrangement==true) n.offsets.getOrNull(i) ?: P() else P(i % n.length,i / n.length,0)
    }
    fun compose(sources:List<FixedSegment>,n:BuildingNode):FixedSegment {
        require(n.length in 1..128 && n.height in 1..128 && n.depth in 1..128 && n.length.toLong()*n.height*n.depth<=262144) { "Invalid segment dimensions" }
        require(n.regionCopies.isEmpty()) { "Copycat arrangement cannot override region assignments" }
        require(sources.isNotEmpty() && sources.size==n.inputs.size) { "Connect every Copycat input" }
        val offsets=initial(n)
        val minimum=P(minOf(0,offsets.minOf { it.x }),minOf(0,offsets.minOf { it.y }),minOf(0,offsets.minOf { it.z }))
        require(offsets.all { p -> p.x-minimum.x in 0 until n.length && p.y-minimum.y in 0 until n.height && p.z-minimum.z in 0 until n.depth }) { "Copycat positions exceed the segment dimensions" }
        val united=SegmentGeometry.union(sources.zip(offsets.map { SegmentTransform(it) }))
        // Union normalizes to the minimum input position. Restore the declared segment origin.
        val origin=P(offsets.minOf { it.x }-minimum.x,offsets.minOf { it.y }-minimum.y,offsets.minOf { it.z }-minimum.z)
        return united.copy(air=false,length=n.length,height=n.height,depth=n.depth,cells=united.cells.map { it.copy(cell=it.cell.copy(pos=it.cell.pos+origin)) })
    }
}
