package de.teutonstudio.copycatarchitect.domain

/** View normal; elevations keep +Y upwards and the plan keeps +Z downwards. */
enum class SegmentView(val horizontal:String,val vertical:String) {
    X("Z","Y"),Y("X","Z"),Z("X","Y");

    fun project(p:P):P=when(this) {
        X->P(p.z,0,-p.y-1)
        Y->P(p.x,0,p.z)
        Z->P(p.x,0,-p.y-1)
    }
    fun displacement(u:Int,v:Int):P=when(this) {
        X->P(0,-v,u)
        Y->P(u,0,v)
        Z->P(u,-v,0)
    }
}

/** Transactional arrangement draft. Input order, rotations and the hidden coordinate stay intact. */
class SegmentArrangement(val sources:List<FixedSegment>,private val node:BuildingNode) {
    val copycats=node.kind==BuildingKind.SEGMENT
    val offsetRange=-128..128
    var transforms:List<SegmentTransform> = initial(node,sources)
        private set
    private val rotations=mutableMapOf<Triple<Int,RotationAxis,Int>,FixedSegment>()

    fun oriented(index:Int):FixedSegment {
        val t=transforms[index]
        return rotations.getOrPut(Triple(index,t.axis,t.quarterTurns)) { SegmentGeometry.rotated(sources[index],t.axis,t.quarterTurns) }
    }
    fun projected(index:Int,view:SegmentView):Set<P> {
        val source=oriented(index);val offset=transforms[index].offset
        return source.cells.map { view.project(it.cell.pos+offset) }.toSet()
    }
    fun update(index:Int,value:SegmentTransform) {
        validate(value)
        transforms=transforms.mapIndexed { i,t -> if(i==index) value else t }
    }
    private fun validate(value:SegmentTransform) {
        require(!copycats || value.axis==RotationAxis.Y && value.quarterTurns==0) { "Rotate Copycat sources before arranging them" }
        require(value.axis!=RotationAxis.NORMAL && value.quarterTurns in 0..3) { "Use axis X, Y or Z and a quarter turn" }
        require(listOf(value.offset.x,value.offset.y,value.offset.z).all { it in offsetRange }) { "Placement offsets must be between ${offsetRange.first} and ${offsetRange.last}" }
    }
    fun move(index:Int,original:SegmentTransform,view:SegmentView,u:Int,v:Int)=update(index,original.copy(offset=original.offset+view.displacement(u,v)))
    fun result():FixedSegment {
        transforms.forEach(::validate)
        return if(copycats) CopycatArrangement.compose(sources,node.copy(copycatArrangement=true,offsets=transforms.map { it.offset })) else SegmentGeometry.union(sources.zip(transforms))
    }
    fun apply(node:BuildingNode):BuildingNode {
        result()
        return if(copycats) node.copy(copycatArrangement=true,offsets=transforms.map { it.offset }) else node.copy(patternAxis="transform",transforms=transforms)
    }

    companion object {
        fun initial(node:BuildingNode,sources:List<FixedSegment>):List<SegmentTransform> {
            if(node.kind==BuildingKind.SEGMENT) return CopycatArrangement.initial(node).map { SegmentTransform(it) }
            require(sources.size==node.inputs.size) { "Connect every segment input" }
            var cursor=0
            return sources.mapIndexed { i,s ->
                if(node.patternAxis=="transform") node.transforms.getOrNull(i) ?: SegmentTransform()
                else {
                    val offset=when(node.patternAxis) { "vertical"->P(0,cursor,0);"depth"->P(0,0,cursor);else->P(cursor,0,0) }
                    cursor+=when(node.patternAxis) { "vertical"->s.height;"depth"->s.depth;else->s.length }
                    SegmentTransform(offset)
                }
            }
        }
    }
}
