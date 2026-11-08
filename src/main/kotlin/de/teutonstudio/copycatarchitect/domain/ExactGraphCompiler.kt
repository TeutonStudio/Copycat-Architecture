package de.teutonstudio.copycatarchitect.domain

/** Same provenance/material rules as the voxel compiler, with exact volume clipping. */
internal object ExactGraphCompiler {
    private data class Contribution(val leaf:String,val group:String,val part:Part)
    fun intermediate(graph:NodeGraph,nodeId:String,groups:List<MaterialGroup>):List<Part> {
        val nodes=graph.nodes.associateBy { it.id }
        val memo=mutableMapOf<String,Map<Contribution,ExactVolume>>()
        fun total(value:Map<Contribution,ExactVolume>)=value.values.fold(ExactVolume()) { a,b -> a.union(b) }
        fun evaluate(id:String):Map<Contribution,ExactVolume> = memo.getOrPut(id) {
            val n=nodes.getValue(id)
            if(n.kind==NodeKind.INPUT || n.kind==NodeKind.SAVED) {
                val contributions=linkedMapOf<Contribution,ExactVolume>()
                GraphCompiler.sources(n).forEach { (leaf,source) ->
                    val part=PartRotations.rotate(source,n.rotationAxis,n.quarterTurns)
                        ?: error("Copycat ${source.source} cannot rotate around ${n.rotationAxis}")
                    val key=Contribution(leaf,part.group,part.copy(cutMask="",exactClip=null))
                    contributions[key]=(contributions[key] ?: ExactVolume()).union(ExactGeometry.part(part))
                };contributions
            } else {
                require(n.inputs.withIndex().all { (slot,input) -> input.isNotEmpty() || n.kind==NodeKind.ROTATE && slot==1 }) { "Connect every input of ${n.kind.name.lowercase()} node (${n.id.take(8)})" }
                if(n.kind==NodeKind.ROTATE || n.kind==NodeKind.MIRROR) {
                    val rotation=if(n.kind==NodeKind.ROTATE) n.inputs[1].takeIf(String::isNotEmpty)?.let(nodes::getValue) ?: n else n
                    val contributions=linkedMapOf<Contribution,ExactVolume>()
                    evaluate(n.inputs[0]).forEach { (source,volume) ->
                        val clipped=source.part.copy(exactClip=volume)
                        val rotated=if(n.kind==NodeKind.MIRROR) PartRotations.mirror(clipped,(n.mirror ?: CopycatMirror()).plane)
                            else PartRotations.rotate(clipped,rotation.rotationAxis,rotation.quarterTurns)
                                ?: error("Copycat ${source.part.source} cannot rotate around ${rotation.rotationAxis}")
                        val key=source.copy(part=rotated.copy(cutMask="",exactClip=null))
                        contributions[key]=(contributions[key] ?: ExactVolume()).union(ExactGeometry.part(rotated))
                    };contributions
                } else {
                    require(n.kind!=NodeKind.ROTATION) { "A rotation value needs a rotation input" }
                    val args=n.inputs.map { evaluate(it) }
                    if(n.kind==NodeKind.OUTPUT) args[0] else {
                        val visible=when(n.kind) {
                            NodeKind.INTERSECTION->args.drop(1).fold(total(args[0])) { a,b -> a.intersect(total(b)) }
                            NodeKind.DIFFERENCE->total(args[0]).minus(total(args[1]))
                            NodeKind.SYMMETRIC_DIFFERENCE->args.fold(ExactVolume()) { a,b -> val other=total(b);a.minus(other).union(other.minus(a)) }
                            else->args.fold(ExactVolume()) { a,b -> a.union(total(b)) }
                        }
                        val contributions=linkedMapOf<Contribution,ExactVolume>()
                        (if(n.kind==NodeKind.DIFFERENCE || n.kind==NodeKind.INTERSECTION) args.take(1) else args).forEachIndexed { slot,value -> value.forEach { (source,v) ->
                            val clipped=v.intersect(visible)
                            val explicit=if(n.kind==NodeKind.UNION || n.kind==NodeKind.SYMMETRIC_DIFFERENCE) n.inputGroups.getOrNull(slot).orEmpty() else ""
                            val key=when {
                                n.materialMode==MaterialMode.GROUPED->source.copy(group=n.materialGroup.ifEmpty { groups.first().id })
                                explicit.isNotEmpty()->source.copy(group=explicit)
                                else->source
                            }
                            if(!clipped.isEmpty()) contributions[key]=(contributions[key] ?: ExactVolume()).union(clipped)
                        } };contributions
                    }
                }
            }
        }
        val result=evaluate(nodeId)
        val shared=result.keys.groupingBy { it.leaf }.eachCount().filterValues { it>1 }.keys
        val parts=result.entries.sortedWith(compareByDescending<Map.Entry<Contribution,ExactVolume>> { it.key.leaf in shared }.thenBy { it.key.leaf }.thenBy { it.key.group }).map { (source,volume) ->
            val original=ExactGeometry.part(source.part)
            source.part.copy(group=source.group,exactClip=if(volume.contains(original)) source.part.exactClip else volume,recipeSource=if(source.leaf in shared) source.leaf else "")
        }
        return if(parts.isEmpty()) emptyList() else Geometry.normalized(parts,groups)
    }
}
