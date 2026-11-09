package de.teutonstudio.copycatarchitect.domain

import kotlin.math.abs

/** An authored segment keeps its identity and metadata after placement in a room. */
data class ResolvedRoomSegment(val definition:RoomSegmentInstance,val source:String,val value:FixedSegment,
                               val position:P,val start:P,val end:P)
data class RoomBoundarySpan(val id:String,val start:P,val end:P,val policy:WallPolicy,
                            val instanceId:String="",val source:String="") {
    val length get()=abs(end.x-start.x)+abs(end.z-start.z)
    val direction get()=when { end.x>start.x->0;end.z>start.z->1;end.x<start.x->2;else->3 }
}
data class ResolvedRoomLayout(val instances:List<ResolvedRoomSegment>,val boundary:List<P>,val spans:List<RoomBoundarySpan>,
                              val footprint:Set<P>,val interior:Set<P>,val wallColumns:Set<P>,val shell:Set<P>,
                              val width:Int,val depth:Int,val height:Int,val closed:Boolean,val warnings:List<String>)
data class RoomPlateResult(val plan:Plan=Plan(emptyList(),emptyList(),emptyList(),0,emptyMap()),val minimum:Int=0,
                           val maximum:Int=0,val bottomThickness:Int=0,val topThickness:Int=0,val usableHeight:Int=0)
data class ResolvedRoomOpening(val id:String,val instanceId:String,val frame:SemanticTransform,val spec:OpeningSpec,val sourceDimensions:P=P())
data class SemanticRoom(val compiled:CompiledRoom,val topology:ResolvedRoomLayout,val openings:List<ResolvedRoomOpening>,val definition:BuildingNode?=null)

/** Room layout works in integer block coordinates; Copycat geometry is never resized to close gaps. */
object RoomTopology {
    /** Geometry-only context for maps and placement tools, independent of materials and finished plates. */
    fun outline(graph:BuildingGraph,room:BuildingNode,segment:((String)->FixedSegment)?=null):ResolvedRoomLayout {
        val layout=if(room.kind==BuildingKind.ROOM_LAYOUT) room else {
            require(room.kind==BuildingKind.ROOM && room.roomConfig?.mode==RoomMode.SEGMENTS) { "Select a segment-based room" }
            graph.nodes.firstOrNull { it.id==room.layout && it.kind==BuildingKind.ROOM_LAYOUT } ?: error("Connect a room arrangement source")
        }
        return resolve(layout,segment ?: dimensionSources(graph))
    }

    /** Reads authored boxes only; missing module-library dimensions remain pending instead of using a legacy room rectangle. */
    private fun dimensionSources(graph:BuildingGraph):(String)->FixedSegment {
        val nodes=graph.nodes.associateBy { it.id };val cache=mutableMapOf<String,FixedSegment>();val visiting=mutableSetOf<String>()
        fun source(id:String):FixedSegment {
            cache[id]?.let { return it }
            require(visiting.add(id)) { "Cyclic segment dimension dependency" }
            try {
                val original=nodes[id] ?: error("Connect the missing segment source")
                val n=graph.resolvedNode(original)
                val value=when(n.kind) {
                    BuildingKind.SEGMENT -> {
                        require(!n.assembly || n.layout.isNotEmpty() && nodes[n.layout]?.kind==BuildingKind.SEGMENT_LAYOUT) { "Connect a segment arrangement source" }
                        require(n.assembly || n.inputs.isNotEmpty() || n.block.isNotEmpty() || n.module.isEmpty()) { "Module-library dimensions are pending" }
                        FixedSegment(n.length,n.height,emptyList(),air=n.block=="minecraft:air",depth=n.depth)
                    }
                    BuildingKind.SEGMENT_MIRROR -> source(n.inputs.single()).copy(cells=emptyList())
                    BuildingKind.SEGMENT_ROTATE -> source(n.inputs.single()).let { SegmentGeometry.rotated(it.copy(air=false),n.rotationAxis,n.quarterTurns).copy(air=it.air) }
                    BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE -> {
                        require(n.inputs.all(String::isNotEmpty)) { "Connect every segment operation input" }
                        val dimensions=SegmentBooleans.dimensions(n,n.inputs.map(::source))
                        FixedSegment(dimensions.x,dimensions.y,emptyList(),depth=dimensions.z)
                    }
                    BuildingKind.PATTERN -> {
                        require(n.inputs.isNotEmpty() && n.inputs.all(String::isNotEmpty)) { "Connect every segment pattern input" }
                        val values=n.inputs.map(::source)
                        if(n.patternAxis in setOf("transform","depth")) {
                            var z=0
                            SegmentGeometry.union(values.mapIndexed { i,v->
                                val t=if(n.patternAxis=="transform") n.transforms.getOrNull(i) ?: SegmentTransform() else SegmentTransform(offset=P(0,0,z))
                                z+=v.depth;v.copy(air=false) to t
                            })
                        } else {
                            val vertical=n.patternAxis=="vertical"
                            val solid=values.filterNot { it.air };val depths=solid.map { it.depth }.distinct();val across=solid.map { if(vertical) it.length else it.height }.distinct()
                            require(depths.size<=1 && across.size<=1) { "Segment pattern dimensions do not match" }
                            val transverse=across.firstOrNull() ?: if(vertical) n.length else n.height
                            FixedSegment(if(vertical) transverse else values.sumOf { it.length },if(vertical) values.sumOf { it.height } else transverse,emptyList(),depth=depths.firstOrNull() ?: values.maxOf { it.depth })
                        }
                    }
                    else -> error("Connect a segment source to the room arrangement")
                }
                require(value.length in 1..128 && value.height in 1..128 && value.depth in 1..128) { "Segment dimensions exceed 128 blocks" }
                cache[id]=value
                return value
            } finally { visiting-=id }
        }
        return ::source
    }

    fun resolve(layout:BuildingNode,segment:(String)->FixedSegment):ResolvedRoomLayout {
        val config=layout.roomLayout ?: RoomLayoutConfig()
        require(config.instances.size<=256) { "A room layout exceeds 256 segment instances" }
        require(config.instances.map { it.id }.distinct().size==config.instances.size && config.instances.all { it.id.isNotBlank() }) { "Room segment instance IDs must be unique and nonempty" }
        val instances=config.instances.map { item ->
            require(item.quarterTurns in 0..3 && item.position.y in -64..128 && item.position.x in -128..128 && item.position.z in -128..128) { "Room segment placement exceeds the block grid" }
            val source=layout.inputs.getOrNull(item.sourceIndex).orEmpty()
            require(source.isNotEmpty()) { "Connect room segment palette input ${item.sourceIndex+1}" }
            val original=segment(source);val value=SegmentGeometry.rotated(original,RotationAxis.Y,item.quarterTurns)
            val l=original.length;val d=original.depth;val p=item.position.copy(y=0)
            val (a,b)=when(item.quarterTurns) {
                0->p to p+P(l,0,0)
                1->p+P(d,0,0) to p+P(d,0,l)
                2->p+P(l,0,d) to p+P(0,0,d)
                else->p+P(0,0,l) to p
            }
            ResolvedRoomSegment(item,source,value,item.position,a,b)
        }
        val boundarySegments=instances.filter { it.definition.contribution in setOf(RoomContribution.BOUNDARY,RoomContribution.OPENING) }
        val inferred=inferBoundary(boundarySegments.map { it.start to it.end })
        val boundary=if(config.boundary.isNotEmpty()) normalizeBoundary(config.boundary) else inferred
        if(boundary.isNotEmpty()) validateBoundary(boundary)
        val closed=boundary.isNotEmpty();val warnings=mutableListOf<String>()
        if(!closed) warnings+="Room outline is open; floor and ceiling await a closed logical boundary"
        val spans=if(closed) boundary.indices.flatMap { i ->
            val a=boundary[i];val b=boundary[(i+1)%boundary.size]
            val units=units(a,b)
            units.mapIndexed { j,(start,end)->
                val owners=boundarySegments.filter { onSegment(start,it.start,it.end) && onSegment(end,it.start,it.end) }
                val policies=owners.map { it.definition.policy }.distinct()
                require(policies.size<=1) { "Conflicting wall policies on room boundary at $start" }
                val owner=owners.firstOrNull()
                val rules=config.boundaryRules.filter { onSegment(start,it.start,it.end) && onSegment(end,it.start,it.end) }.map { it.policy }.distinct()
                require(rules.size<=1) { "Conflicting explicit boundary rules at $start" }
                val rule=rules.firstOrNull()
                require(rule==null || owner==null || owner.definition.policy==rule) { "Boundary rule conflicts with an explicit segment at $start" }
                RoomBoundarySpan("$i:$j",start,end,rule ?: owner?.definition?.policy ?: WallPolicy.AUTO,owner?.definition?.id.orEmpty(),owner?.source.orEmpty())
            }
        } else boundarySegments.flatMap { item->units(item.start,item.end).mapIndexed { j,(a,b)->RoomBoundarySpan("${item.definition.id}:$j",a,b,item.definition.policy,item.definition.id,item.source) } }
        val cells=if(closed) polygonCells(boundary) else emptySet()
        val authoredColumns=instances.filter { it.definition.policy!=WallPolicy.OPEN && !it.value.air }.flatMap { item->
            if(item.definition.contribution !in setOf(RoomContribution.BOUNDARY,RoomContribution.OPENING)) emptyList()
            else item.value.cells.filterNot { it.cell.empty || it.cell.block=="minecraft:air" }.map { (item.position+it.cell.pos).copy(y=0) }
        }.toSet()
        // A closed logical edge is not a physical wall: open canopies keep their whole usable area.
        val columns=authoredColumns.intersect(cells)
        val shell=if(closed) cells+authoredColumns else instances.flatMap { item->buildList {
            for(x in 0 until item.value.length) for(z in 0 until item.value.depth) add((item.position+P(x,0,z)).copy(y=0))
        } }.toSet()
        val extent=shell.ifEmpty { instances.map { it.position.copy(y=0) }.toSet() }
        val height=boundarySegments.filter { it.definition.contribution==RoomContribution.BOUNDARY && it.definition.policy!=WallPolicy.OPEN && !it.value.air }
            .maxOfOrNull { it.position.y+it.value.height } ?: 0
        return ResolvedRoomLayout(instances,boundary,spans,cells,cells-columns,columns,shell,
            if(extent.isEmpty()) 0 else extent.maxOf { it.x }-extent.minOf { it.x }+1,
            if(extent.isEmpty()) 0 else extent.maxOf { it.z }-extent.minOf { it.z }+1,height,closed,warnings)
    }

    /** Existing enclosures are exact prefabs; opting a stockwork in never guesses their windows. */
    fun legacy(node:BuildingNode,compiled:CompiledRoom):SemanticRoom {
        val area=compiled.shell.map { it.copy(y=0) }.toSet()
        val edges=buildList {
            for(p in area) for(direction in 0..3) if(p+RoomGeometry.directions[direction] !in area) {
                val a=when(direction) { 0->p;1->p+P(1,0,0);2->p+P(1,0,1);else->p+P(0,0,1) }
                val b=when(direction) { 0->p+P(1,0,0);1->p+P(1,0,1);2->p+P(0,0,1);else->p }
                add(a to b)
            }
        }
        val boundary=runCatching { inferBoundary(edges) }.getOrDefault(emptyList())
        val spans=edges.mapIndexed { i,(a,b)->RoomBoundarySpan("legacy:$i",a,b,WallPolicy.EXPLICIT) }
        val topology=ResolvedRoomLayout(emptyList(),boundary,spans,area,compiled.footprint,area-compiled.footprint,area,
            if(area.isEmpty()) 0 else area.maxOf { it.x }-area.minOf { it.x }+1,
            if(area.isEmpty()) 0 else area.maxOf { it.z }-area.minOf { it.z }+1,compiled.usableHeight,true,emptyList())
        return SemanticRoom(compiled,topology,emptyList(),node)
    }

    /** Returns proposed links only. The caller explicitly accepts them as geometry or open boundary. */
    fun closureSuggestions(layout:BuildingNode,segment:(String)->FixedSegment):List<Pair<P,P>> {
        val resolved=resolve(layout,segment)
        if(resolved.closed) return emptyList()
        val edges=resolved.instances.filter { it.definition.contribution in setOf(RoomContribution.BOUNDARY,RoomContribution.OPENING) }.map { it.start to it.end }
        val degree=edges.flatMap { listOf(it.first,it.second) }.groupingBy { it }.eachCount()
        val leaves=degree.filterValues { it==1 }.keys.toMutableSet();val result=mutableListOf<Pair<P,P>>()
        while(leaves.size>=2) {
            val a=leaves.minWith(compareBy({it.z},{it.x}));leaves-=a
            val b=leaves.minWith(compareBy<P>({abs(it.x-a.x)+abs(it.z-a.z)},{it.z},{it.x}));leaves-=b
            if(a.x==b.x || a.z==b.z) result+=a to b
            else { val corner=P(b.x,0,a.z);result+=a to corner;result+=corner to b }
        }
        return result
    }

    fun compile(room:BuildingNode,layout:BuildingNode,segment:(String)->FixedSegment,
                placement:(P,List<SegmentCell>,Int,String)->Placement,
                plates:(ResolvedRoomLayout,Int)->RoomPlateResult={ _,h->RoomPlateResult(maximum=h,usableHeight=h) },
                reserved:(P)->Boolean={false}):SemanticRoom {
        var topology=resolve(layout,segment)
        val height=room.roomConfig?.heightOverride ?: topology.height.takeIf { it>0 } ?: room.height
        require(height in 1..128) { "Room height must be between 1 and 128" }
        val contributions=linkedMapOf<P,MutableList<Placement>>();val clears=linkedSetOf<P>()
        val openings=mutableListOf<ResolvedRoomOpening>()
        for(item in topology.instances) {
            val isBoundary=item.definition.contribution in setOf(RoomContribution.BOUNDARY,RoomContribution.OPENING)
            if(item.definition.policy==WallPolicy.OPEN) continue
            for(entries in item.value.cells.groupBy { it.cell.pos }.values) {
                val c=entries.first().cell;val p=c.pos+item.position
                if(reserved(p)) continue
                if(c.empty || c.block=="minecraft:air") { clears+=p;continue }
                contributions.getOrPut(p) { mutableListOf() }+=placement(p,entries,0,if(isBoundary) "wall" else "trim")
            }
            for(feature in item.value.metadata.instances) for(opening in feature.features.openings) {
                val frame=feature.transform.copy(origin=feature.transform.origin+item.position)
                openings+=ResolvedRoomOpening("${item.definition.id}/${feature.path}/${opening.id}",item.definition.id,frame,opening,feature.dimensions)
            }
        }
        val warnings=topology.warnings.toMutableList()
        val filler=layout.roomLayout?.filler.orEmpty().ifEmpty { room.roomConfig?.defaultPartition.orEmpty() }
        val missing=topology.spans.filter { it.instanceId.isEmpty() && it.policy!=WallPolicy.OPEN }
        if(filler.isNotEmpty()) {
            val value=segment(filler)
            require(value.length==1 && (value.air || value.height==height)) { "Closure filler must be one block wide and match room height; profiles are never stretched" }
            for(span in missing) {
                val rotated=SegmentGeometry.rotated(value,RotationAxis.Y,span.direction)
                val p=wallOrigin(span.start,span.direction,value.depth)
                for(entries in rotated.cells.groupBy { it.cell.pos }.values) {
                    val c=entries.first().cell;val target=p+c.pos
                    if(reserved(target)) continue
                    if(c.empty || c.block=="minecraft:air") clears+=target else contributions.getOrPut(target) { mutableListOf() }+=placement(target,entries,0,"wall")
                }
            }
        } else if(missing.isNotEmpty()) warnings+="Unspecified wall sections await a stockwork facade or a closure filler"
        val physicalColumns=contributions.filterValues { entries->entries.any { it.component=="wall" } }.keys.map { it.copy(y=0) }.toSet()
        topology=topology.copy(wallColumns=physicalColumns.intersect(topology.footprint),interior=topology.footprint-physicalColumns,shell=topology.shell+physicalColumns)
        val layers=if(topology.closed) plates(topology,height) else RoomPlateResult(maximum=height,usableHeight=height)
        layers.plan.placements.filterNot { reserved(it.pos) }.forEach { contributions.getOrPut(it.pos) { mutableListOf() }+=it }
        clears+=layers.plan.clear.filterNot(reserved)
        if(topology.closed) for(p in topology.interior) for(y in 0 until height) if(!reserved(p+P(0,y,0))) clears+=p+P(0,y,0)
        val resolver=Resolver()
        contributions.forEach { (p,values)->resolver.add(merge(values));clears-=p }
        clears.forEach(resolver::clear)
        val plan=resolver.plan(warnings+layers.plan.warnings)
        return SemanticRoom(CompiledRoom(plan,topology.interior,topology.shell,layers.minimum,
            maxOf(height,layers.maximum),layers.bottomThickness,layers.topThickness,layers.usableHeight.takeIf { it>0 } ?: height),topology,openings,room)
    }

    internal fun merge(values:List<Placement>):Placement {
        val unique=values.distinctBy { it.visualKey() }
        if(unique.size==1) return unique.first()
        require(unique.all { it.block.isEmpty() }) { "Room wall blocks conflict at ${unique.first().pos}" }
        return ExactPlacementUnion.merge(unique)
    }
    internal fun wallOrigin(p:P,direction:Int,depth:Int)=when(direction) {
        0->p;1->p+P(-depth,0,0);2->p+P(-1,0,-depth);else->p+P(0,0,-1)
    }
    internal fun interiorColumn(p:P,direction:Int)=when(direction) {
        0->p;1->p+P(-1,0,0);2->p+P(-1,0,-1);else->p+P(0,0,-1)
    }
    internal fun units(a:P,b:P):List<Pair<P,P>> {
        require(a.y==0 && b.y==0 && (a.x==b.x || a.z==b.z) && a!=b) { "Room boundaries must be nonzero horizontal orthogonal edges" }
        val count=abs(b.x-a.x)+abs(b.z-a.z);val step=P((b.x-a.x).compareTo(0),0,(b.z-a.z).compareTo(0))
        return (0 until count).map { i -> val p=a+P(step.x*i,0,step.z*i);p to p+step }
    }
    private fun onSegment(p:P,a:P,b:P)=if(a.x==b.x) p.x==a.x && p.z in minOf(a.z,b.z)..maxOf(a.z,b.z)
        else p.z==a.z && p.x in minOf(a.x,b.x)..maxOf(a.x,b.x)
    private fun inferBoundary(edges:List<Pair<P,P>>):List<P> {
        if(edges.size<4) return emptyList()
        val degree=edges.flatMap { listOf(it.first,it.second) }.groupingBy { it }.eachCount()
        if(degree.values.any { it!=2 }) return emptyList()
        val unused=edges.toMutableList();val ring=mutableListOf(unused.first().first);var cursor=ring.first()
        while(unused.isNotEmpty()) {
            val edge=unused.firstOrNull { it.first==cursor || it.second==cursor } ?: return emptyList()
            unused.remove(edge);cursor=if(edge.first==cursor) edge.second else edge.first
            if(cursor==ring.first()) return if(unused.isEmpty()) normalizeBoundary(ring) else emptyList()
            ring+=cursor
        }
        return emptyList()
    }
    private fun normalizeBoundary(input:List<P>):List<P> {
        val ring=input.toMutableList();if(ring.size>1 && ring.first()==ring.last()) ring.removeAt(ring.lastIndex)
        require(ring.size in 4..256 && ring.all { it.y==0 && it.x in -128..256 && it.z in -128..256 }) { "Room boundary needs 4–256 grid vertices" }
        var area=0L;ring.indices.forEach { i->val a=ring[i];val b=ring[(i+1)%ring.size];area+=a.x.toLong()*b.z-b.x.toLong()*a.z }
        require(area!=0L) { "Room boundary has no enclosed area" }
        return if(area<0) ring.reversed() else ring
    }
    private fun validateBoundary(boundary:List<P>) {
        val visited=mutableSetOf<Pair<P,P>>()
        boundary.indices.forEach { i -> units(boundary[i],boundary[(i+1)%boundary.size]).forEach { (a,b)->
            val canonical=if(a.x<b.x || a.z<b.z) a to b else b to a
            require(visited.add(canonical)) { "Room boundary overlaps itself" }
        } }
        val endpoints=boundary.indices.flatMap { i->units(boundary[i],boundary[(i+1)%boundary.size]).map { it.first } }
        require(endpoints.distinct().size==endpoints.size) { "Room boundary crosses itself" }
    }
    private fun polygonCells(boundary:List<P>):Set<P> {
        val minX=boundary.minOf { it.x };val maxX=boundary.maxOf { it.x };val minZ=boundary.minOf { it.z };val maxZ=boundary.maxOf { it.z }
        require((maxX-minX).toLong()*(maxZ-minZ)<=16384) { "Room layout exceeds 16384 footprint cells" }
        return buildSet {
            for(x in minX until maxX) for(z in minZ until maxZ) {
                var inside=false
                boundary.indices.forEach { i->val a=boundary[i];val b=boundary[(i+1)%boundary.size]
                    if((a.z*2>z*2+1)!=(b.z*2>z*2+1) && a.x*2>x*2+1) inside=!inside
                }
                if(inside) add(P(x,0,z))
            }
        }
    }
}
