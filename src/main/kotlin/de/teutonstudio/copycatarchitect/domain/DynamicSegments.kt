package de.teutonstudio.copycatarchitect.domain

/** A dynamic dimension is a recipe parameter, resolved independently at each consumer. */
object DynamicSegments {
    fun validate(n:BuildingNode) {
        require(n.dynamicAxes.isNullOrEmpty() || n.kind==BuildingKind.SEGMENT && n.id!="empty_wall" && n.dynamicAxes.all { it!=RotationAxis.NORMAL }) { "Dynamic axes X/Y/Z belong to segment sources" }
    }
    fun component(p:P,axis:RotationAxis)=when(axis) { RotationAxis.X->p.x;RotationAxis.Y->p.y;RotationAxis.Z->p.z;else->error("Use X/Y/Z") }
    fun with(p:P,axis:RotationAxis,value:Int)=when(axis) { RotationAxis.X->p.copy(x=value);RotationAxis.Y->p.copy(y=value);RotationAxis.Z->p.copy(z=value);else->error("Use X/Y/Z") }
    fun turnSize(p:P,axis:RotationAxis,turns:Int):P=if(Math.floorMod(turns,2)==0) p else when(axis) { RotationAxis.X->P(p.x,p.z,p.y);RotationAxis.Y->P(p.z,p.y,p.x);RotationAxis.Z->P(p.y,p.x,p.z);else->error("Use X/Y/Z") }
    fun turnAxes(axes:Set<RotationAxis>,axis:RotationAxis,turns:Int):Set<RotationAxis> = axes.map { a ->
        val unit=with(P(),a,1);val rotated=turnSize(unit,axis,turns)
        listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z).single { component(rotated,it)==1 }
    }.toSet()
    fun axes(graph:BuildingGraph,id:String):Set<RotationAxis> {
        val nodes=GraphNumbers.buildingContext(graph);val visiting=mutableSetOf<String>();val cache=mutableMapOf<String,Set<RotationAxis>>()
        fun read(key:String):Set<RotationAxis> {
            if(key.isEmpty()) return emptySet()
            cache[key]?.let { return it }
            require(visiting.add(key)) { "Cyclic segment sizing dependency" }
            val n=nodes[key] ?: error("Missing segment $key")
            val result=when(n.kind) {
                BuildingKind.SEGMENT -> n.dynamicAxes.orEmpty()
                BuildingKind.SERVICE_SHAFT_SEGMENT,BuildingKind.ELEVATOR_SHAFT_SEGMENT -> setOf(RotationAxis.Y)
                BuildingKind.CONDITIONAL -> if(n.inputs.size==3 && n.inputs.all(String::isNotEmpty)) runCatching { read(GraphConditions.buildingNode(graph,key).id) }.getOrDefault(emptySet()) else emptySet()
                BuildingKind.SEGMENT_ROTATE -> BuildingTransformValues.rotations(n,graph).fold(read(n.inputs.single())) { axes,step -> turnAxes(axes,step.axis,step.quarterTurns) }
                BuildingKind.SEGMENT_TRANSFORM -> n.transformationValue?.takeIf(String::isNotEmpty)?.let { TransformationValues.building(nodes.getValue(it).copy(translation=null,positionValue=null),nodes) }.orEmpty().fold(read(n.inputs.single())) { axes,step -> if(step.kind==CopycatTransformKind.ROTATE) turnAxes(axes,step.axis,step.quarterTurns) else axes }
                BuildingKind.SEGMENT_MIRROR -> read(n.inputs.single())
                in SegmentBooleans.kinds -> n.inputs.flatMap { read(it) }.toSet()
                BuildingKind.PATTERN -> n.inputs.flatMapIndexed { i,source ->
                    val axes=read(source);val t=n.transforms.getOrNull(i)
                    if(n.patternAxis=="transform" && t!=null) turnAxes(axes,t.axis,t.quarterTurns) else axes
                }.toSet()
                else -> emptySet()
            }
            visiting-=key;cache[key]=result;return result
        }
        return read(id)
    }
    fun sized(n:BuildingNode,requested:P?):BuildingNode {
        if(requested==null || n.dynamicAxes.isNullOrEmpty()) return n
        var size=P(n.length,n.height,n.depth)
        for(axis in n.dynamicAxes) size=with(size,axis,component(requested,axis))
        require(listOf(size.x,size.y,size.z).all { it in 1..128 } && size.x.toLong()*size.y*size.z<=262144) { "Dynamic segment dimensions must be 1–128 and at most 262144 cells" }
        return n.copy(length=size.x,height=size.y,depth=size.z)
    }

    /** Propagate constraints through composition before evaluating geometry. Fixed operands stay fixed. */
    fun requests(graph:BuildingGraph,n:BuildingNode,target:P?,base:(String)->P):List<P?> {
        if(target==null) return n.inputs.map { null }
        if(n.kind==BuildingKind.SEGMENT_TRANSFORM) {
            val steps=n.transformationValue?.takeIf(String::isNotEmpty)?.let { TransformationValues.building(GraphConditions.buildingNode(graph,it),GraphConditions.buildingNodes(GraphNumbers.buildingContext(graph))) }.orEmpty()
            val local=steps.asReversed().fold(target) { size,step -> when(step.kind) {
                CopycatTransformKind.ROTATE->turnSize(size,step.axis,step.quarterTurns)
                CopycatTransformKind.POSITION->P(size.x-if(Math.floorMod(step.offset.x,16)>0) 1 else 0,size.y-if(Math.floorMod(step.offset.y,16)>0) 1 else 0,size.z-if(Math.floorMod(step.offset.z,16)>0) 1 else 0)
                else->size
            } }
            return listOf(local)
        }
        if(n.kind==BuildingKind.SEGMENT_ROTATE) {
            val size=BuildingTransformValues.rotations(n,graph).asReversed().fold(target) { p,step -> turnSize(p,step.axis,step.quarterTurns) }
            return listOf(size)
        }
        if(n.kind==BuildingKind.SEGMENT_MIRROR) return listOf(target)
        if(n.kind==BuildingKind.PATTERN && n.patternAxis in setOf("horizontal","vertical","depth")) {
            val axis=when(n.patternAxis) { "vertical"->RotationAxis.Y;"depth"->RotationAxis.Z;else->RotationAxis.X }
            val sizes=n.inputs.map(base);val dynamic=n.inputs.indices.filter { axis in axes(graph,n.inputs[it]) }
            if(dynamic.isEmpty()) return n.inputs.map { target }
            val remaining=component(target,axis)-sizes.indices.filter { it !in dynamic }.sumOf { component(sizes[it],axis) }
            require(remaining>=dynamic.size) { "The requested segment is too small for its fixed parts" }
            return n.inputs.indices.map { index -> if(index !in dynamic) target else with(target,axis,remaining/dynamic.size+if(dynamic.indexOf(index)<remaining%dynamic.size) 1 else 0) }
        }
        if(n.kind==BuildingKind.SEGMENT_UNION || n.kind==BuildingKind.PATTERN && n.patternAxis=="transform") {
            val offsets=n.inputs.indices.map { i -> if(n.kind==BuildingKind.PATTERN) n.transforms.getOrNull(i)?.offset ?: P() else SegmentBooleans.offset(n,i) }
            val lower=P(minOf(if(n.kind==BuildingKind.PATTERN || n.operandOffsets!=null) Int.MAX_VALUE else 0,offsets.minOf { it.x }),minOf(if(n.kind==BuildingKind.PATTERN || n.operandOffsets!=null) Int.MAX_VALUE else 0,offsets.minOf { it.y }),minOf(if(n.kind==BuildingKind.PATTERN || n.operandOffsets!=null) Int.MAX_VALUE else 0,offsets.minOf { it.z }))
            return offsets.mapIndexed { i,p ->
                var size=P(target.x-(p.x-lower.x),target.y-(p.y-lower.y),target.z-(p.z-lower.z))
                if(n.kind==BuildingKind.PATTERN) n.transforms.getOrNull(i)?.let { size=turnSize(size,it.axis,it.quarterTurns) }
                size
            }
        }
        return n.inputs.map { target }
    }
}
