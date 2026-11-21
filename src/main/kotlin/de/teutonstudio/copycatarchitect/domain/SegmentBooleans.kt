package de.teutonstudio.copycatarchitect.domain

/** Sparse, position-wise operations using the same exact compiler as Copycat graphs. */
object SegmentBooleans {
    val kinds=setOf(BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,
        BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE)

    fun offset(node:BuildingNode,index:Int):P = node.operandOffsets?.getOrNull(index)
        ?: if(node.operandOffsets==null && node.kind==BuildingKind.SEGMENT_UNION) node.offsets.getOrNull(index) ?: P() else P()

    // Preserve relative offsets while keeping the resulting segment in its local positive box.
    private fun unionOrigin(node:BuildingNode,count:Int):P {
        val offsets=(0 until count).map { node.offsets.getOrNull(it) ?: P() }
        return P(minOf(0,offsets.minOf { it.x }),minOf(0,offsets.minOf { it.y }),minOf(0,offsets.minOf { it.z }))
    }

    private fun spatialBounds(node:BuildingNode,inputs:List<FixedSegment>):Pair<P,P> {
        val lower=inputs.mapIndexed { i,v->v.origin+offset(node,i) }
        val upper=inputs.indices.map { i->lower[i]+P(inputs[i].length,inputs[i].height,inputs[i].depth) }
        val start=when(node.kind) {
            BuildingKind.SEGMENT_DIFFERENCE->lower[0]
            BuildingKind.SEGMENT_INTERSECTION->P(lower.maxOf { it.x },lower.maxOf { it.y },lower.maxOf { it.z })
            else->P(lower.minOf { it.x },lower.minOf { it.y },lower.minOf { it.z })
        }
        val end=when(node.kind) {
            BuildingKind.SEGMENT_DIFFERENCE->upper[0]
            BuildingKind.SEGMENT_INTERSECTION->P(upper.minOf { it.x },upper.minOf { it.y },upper.minOf { it.z })
            else->P(upper.maxOf { it.x },upper.maxOf { it.y },upper.maxOf { it.z })
        }
        val size=P(maxOf(1,end.x-start.x),maxOf(1,end.y-start.y),maxOf(1,end.z-start.z))
        require(listOf(size.x,size.y,size.z).all { it in 1..128 } && size.x.toLong()*size.y*size.z<=262144) { "Segment operation exceeds its bounds" }
        return start to size
    }

    /** Authored bounds are available to room placement tools without loading material recipes. */
    fun dimensions(node:BuildingNode,inputs:List<FixedSegment>):P {
        require(node.kind in kinds && inputs.size in 2..if(node.kind==BuildingKind.SEGMENT_DIFFERENCE) 2 else 16)
        if(node.operandOffsets!=null || inputs.any { it.origin!=P() }) return spatialBounds(node,inputs).second
        if(node.kind==BuildingKind.SEGMENT_UNION && node.offsets.any { it!=P() }) {
            val lower=unionOrigin(node,inputs.size)
            val upper=P(inputs.indices.maxOf { inputs[it].length+(node.offsets.getOrNull(it)?.x ?: 0) },inputs.indices.maxOf { inputs[it].height+(node.offsets.getOrNull(it)?.y ?: 0) },inputs.indices.maxOf { inputs[it].depth+(node.offsets.getOrNull(it)?.z ?: 0) })
            val size=P(upper.x-lower.x,upper.y-lower.y,upper.z-lower.z)
            require(size.x.toLong()*size.y*size.z<=262144) { "Segment operation exceeds 262144 cells" }
            return size
        }
        val length=if(node.kind==BuildingKind.SEGMENT_DIFFERENCE) inputs[0].length else if(node.kind==BuildingKind.SEGMENT_INTERSECTION) inputs.minOf { it.length } else inputs.maxOf { it.length }
        val height=if(node.kind==BuildingKind.SEGMENT_DIFFERENCE) inputs[0].height else if(node.kind==BuildingKind.SEGMENT_INTERSECTION) inputs.minOf { it.height } else inputs.maxOf { it.height }
        val depth=if(node.kind==BuildingKind.SEGMENT_DIFFERENCE) inputs[0].depth else if(node.kind==BuildingKind.SEGMENT_INTERSECTION) inputs.minOf { it.depth } else inputs.maxOf { it.depth }
        require(length.toLong()*height*depth<=262144) { "Segment operation exceeds 262144 cells" }
        return P(length,height,depth)
    }

    fun evaluate(node:BuildingNode,inputs:List<FixedSegment>,forms:MutableMap<String,Form>,palette:Map<String,String>):FixedSegment {
        val dimensions=dimensions(node,inputs)
        // Semantic operations need a topology-aware rewrite; never silently drop their contracts.
        require(inputs.all { it.metadata.instances.isEmpty() }) { "Segment operations cannot yet clip semantic openings or wall attachments" }
        val spatial=node.operandOffsets!=null || inputs.any { it.origin!=P() }
        val origin=if(spatial) spatialBounds(node,inputs).first else P()
        val lower=if(!spatial && node.kind==BuildingKind.SEGMENT_UNION && node.offsets.isNotEmpty()) unionOrigin(node,inputs.size) else P()
        val maps=inputs.mapIndexed { index,segment -> val offset=if(node.operandOffsets!=null) offset(node,index) else if(node.kind==BuildingKind.SEGMENT_UNION) (node.offsets.getOrNull(index) ?: P())+P(-lower.x,-lower.y,-lower.z) else P()
            segment.cells.map { it.copy(cell=it.cell.copy(pos=it.cell.pos+offset)) }.filter { !it.cell.empty && it.cell.block!="minecraft:air" }.groupBy { it.cell.pos } }
        val positions=when(node.kind) {
            BuildingKind.SEGMENT_DIFFERENCE->maps[0].keys
            BuildingKind.SEGMENT_INTERSECTION->maps.drop(1).fold(maps[0].keys.toSet()) { a,b -> a.intersect(b.keys) }
            else->maps.flatMap { it.keys }.toSet()
        }
        val cache=mutableMapOf<List<List<SegmentCell>>,List<SegmentCell>>()
        val cells=positions.sortedWith(compareBy({ it.y },{ it.z },{ it.x })).flatMap { pos ->
            val values=maps.map { map -> map[pos].orEmpty().map { it.copy(cell=it.cell.copy(pos=P())) } }
            cache.getOrPut(values) { cell(node,values,forms,palette) }.map { it.copy(cell=it.cell.copy(pos=pos)) }
        }
        val air=if(cells.isEmpty()) emptySet() else {
            val domains=inputs.mapIndexed { i,v ->
                val shift=if(node.operandOffsets!=null) offset(node,i) else if(node.kind==BuildingKind.SEGMENT_UNION) (node.offsets.getOrNull(i) ?: P())+P(-lower.x,-lower.y,-lower.z) else P()
                buildSet { for(x in 0 until v.length) for(y in 0 until v.height) for(z in 0 until v.depth) add(v.origin+P(x,y,z)+shift) }
            }
            val domain=when(node.kind) {
                BuildingKind.SEGMENT_INTERSECTION->domains.drop(1).fold(domains[0]) { a,b->a.intersect(b) }
                BuildingKind.SEGMENT_DIFFERENCE->domains[0]-domains[1]
                BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE->domains.fold(emptySet<P>()) { a,b->(a-b)+(b-a) }
                else->domains.flatten().toSet()
            }
            val candidates=inputs.flatMapIndexed { i,v ->
                val shift=if(node.operandOffsets!=null) offset(node,i) else if(node.kind==BuildingKind.SEGMENT_UNION) (node.offsets.getOrNull(i) ?: P())+P(-lower.x,-lower.y,-lower.z) else P()
                v.cells.filter { it.cell.empty || it.cell.block=="minecraft:air" }.map { it.cell.pos+shift }
            }.toSet()
            candidates.intersect(domain)-cells.map { it.cell.pos }.toSet()
        }
        return FixedSegment(dimensions.x,dimensions.y,cells+air.map { SegmentCell(Cell(it,block="minecraft:air",empty=true)) },depth=dimensions.z,origin=origin)
    }

    private fun cell(node:BuildingNode,rawInputs:List<List<SegmentCell>>,forms:MutableMap<String,Form>,palette:Map<String,String>):List<SegmentCell> {
        if(node.kind==BuildingKind.SEGMENT_INTERSECTION && rawInputs.any { it.isEmpty() } || node.kind==BuildingKind.SEGMENT_DIFFERENCE && rawInputs[0].isEmpty()) return emptyList()
        val inputs=rawInputs.filter { it.isNotEmpty() }
        if(inputs.isEmpty()) return emptyList()
        if(inputs.size==1) return inputs[0].map { if(node.materialMode==MaterialMode.GROUPED) it.copy(overrideRole=node.role) else it }
        val groups=mutableListOf<MaterialGroup>()
        val surfaces=linkedMapOf<String,String>()
        val materialGroups=linkedMapOf<Triple<String,Int,Boolean>,String>()
        val blockGroups=mutableMapOf<String,SegmentCell>()
        val leaves=inputs.mapIndexed { index,entries ->
            val parts=entries.flatMapIndexed { entryIndex,entry ->
                val c=entry.cell
                val source=if(c.block.isNotEmpty()) Form(parts=listOf(Part(source="copycats:copycat_block"))) else forms[c.form]?.let(Geometry::validate) ?: error("Missing form ${c.form}")
                val forced=if(node.materialMode==MaterialMode.GROUPED) node.role else entry.overrideRole
                val receipts=source.parts.map { it.recipeSource }.filter(String::isNotEmpty).distinct().withIndex().associate { it.value to it.index }
                val mapping=source.groups.sortedBy { it.priority }.associate { group ->
                    val material=if(c.block.isNotEmpty()) c.block else if(forced.isNotEmpty()) palette[forced] ?: error("Missing material role $forced")
                        else c.surfaces?.get(group.id) ?: palette[if(group.role=="surface") c.role else group.role] ?: error("Missing material role ${c.role}")
                    // Keep the original foreground ordering within each incoming form.
                    val key=Triple(material,group.priority,c.block.isNotEmpty())
                    val id=materialGroups.getOrPut(key) {
                        val id="g${groups.size}";groups+=MaterialGroup(id,id,priority=groups.size);surfaces[id]=material;id
                    }
                    if(c.block.isNotEmpty()) blockGroups[id]=entry
                    group.id to id
                }
                SegmentGeometry.parts(c,source.parts).map { it.copy(group=mapping.getValue(it.group),
                    recipeSource=if(it.recipeSource.isEmpty()) "" else "s${index}_${entryIndex}_${receipts.getValue(it.recipeSource)}") }
            }
            GraphNode("s$index",NodeKind.SAVED,savedCopycat=SavedCopycat(parts=parts))
        }
        require(groups.size<=8) { "Segment operation exceeds eight material groups in one block" }
        if(groups.isEmpty()) return emptyList()
        // Reused materials may precede a newly encountered background group. Resolve
        // priorities after collecting every input so that source ownership never reverses.
        val priorities=materialGroups.entries.sortedBy { it.key.second }.mapIndexed { index,entry -> entry.value to index }.toMap()
        for(index in groups.indices) groups[index]=groups[index].copy(priority=priorities.getValue(groups[index].id))
        val operation=NodeKind.valueOf(node.kind.name.removePrefix("SEGMENT_"))
        val graph=NodeGraph(nodes=leaves+GraphNode("op",operation,leaves.map { it.id })+GraphNode("result",NodeKind.OUTPUT,listOf("op")))
        val parts=GraphCompiler.intermediate(graph,"op",groups)
        if(parts.isEmpty()) return emptyList()
        val visible=Geometry.resolve(parts,groups)
        val survivingBlocks=blockGroups.filterKeys { visible.exactOwned[it]?.isEmpty()==false || (visible.owned[it]?.count() ?: 0)>0 }
        if(survivingBlocks.isNotEmpty()) {
            val entry=survivingBlocks.values.first()
            require(Geometry.exact(parts).contains(Geometry.exact(Part(source="copycats:copycat_block"))) && parts.all { it.group in survivingBlocks }) {
                "A partial normal block cannot become a Copycat without changing its item and zinc cost; use a saved full Copycat as the source"
            }
            return listOf(entry)
        }
        val id="boolean_${node.id}_${forms.size}@1"
        forms[id]=Form(name="Segment operation",groups=groups,parts=parts,graph=graph)
        return listOf(SegmentCell(Cell(form=id,role=node.role,surfaces=surfaces)))
    }
}
