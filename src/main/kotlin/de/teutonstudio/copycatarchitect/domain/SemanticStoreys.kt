package de.teutonstudio.copycatarchitect.domain

import kotlin.math.abs

data class SemanticRoomInstance(val room:SemanticRoom,val offset:P=P(),val id:String="")

/** Resolves walls jointly, then punches their whole depth before adding window or door inserts. */
object SemanticStoreys {
    private data class BoundaryKey(val a:P,val b:P)
    private data class Side(val room:SemanticRoomInstance,val span:RoomBoundarySpan)
    private data class Contribution(val placement:Placement,val owner:String,val boundary:Boolean=true,val keys:Set<BoundaryKey> = emptySet())
    private data class Cut(val opening:ResolvedRoomOpening,val room:SemanticRoomInstance,val volume:ExactVolume,val zMin:Int,val zMax:Int,val keys:Set<BoundaryKey>,val retained:Set<P>)

    fun compile(node:BuildingNode,rooms:List<SemanticRoomInstance>,segment:(String)->FixedSegment,
                placement:(P,List<SegmentCell>,Int,String)->Placement,copycat:(String)->Placement,
                reserved:(P)->Boolean={false}):CompiledStorey {
        require(rooms.isNotEmpty()) { "Connect at least one room to the stockwork" }
        val occupied=linkedSetOf<P>();val shell=linkedSetOf<P>();val boundary=linkedMapOf<BoundaryKey,MutableList<Side>>()
        val contributions=linkedMapOf<P,MutableList<Contribution>>();val clears=linkedSetOf<P>();val warnings=mutableListOf<String>()
        var minimum=0;var maximum=0;var bottom=Int.MAX_VALUE;var top=Int.MAX_VALUE
        for((index,instance) in rooms.withIndex()) {
            val room=instance.room.compiled;val offset=instance.offset;val owner=owner(instance,index)
            val inside=instance.room.topology.interior.map { it+offset }.toSet()
            require(inside.none { it in occupied }) { "Room interiors overlap; drag them apart in the stockwork preview" };occupied+=inside
            shell+=room.shell.map { it+offset }
            minimum=minOf(minimum,room.minimum+offset.y);maximum=maxOf(maximum,room.maximum+offset.y)
            bottom=minOf(bottom,room.bottomThickness);top=minOf(top,room.topThickness);warnings+=room.plan.warnings
            for(p in room.plan.placements) {
                val moved=p.copy(pos=p.pos+offset)
                if(!reserved(moved.pos)) contributions.getOrPut(moved.pos) { mutableListOf() }+=Contribution(moved,owner,moved.component in setOf("wall","trim"),keysAt(instance,moved.pos))
            }
            clears+=room.plan.clear.map { it+offset }.filterNot(reserved)
            for(span in instance.room.topology.spans) {
                val a=span.start+offset.copy(y=0);val b=span.end+offset.copy(y=0)
                val key=if(a.x<b.x || a.z<b.z) BoundaryKey(a,b) else BoundaryKey(b,a)
                boundary.getOrPut(key) { mutableListOf() }+=Side(instance,span)
            }
        }
        val retainedByOwner=rooms.mapIndexed { index,instance -> owner(instance,index) to instance.room.openings.filter { it.spec.retainSource }.flatMap { sourcePositions(instance,it) }.toSet() }.toMap()
        val physical=linkedMapOf<BoundaryKey,MutableSet<P>>()
        val facade=node.facade
        val wall=facade?.source?.takeIf(String::isNotEmpty)?.let(segment)
        val filler=facade?.filler?.takeIf(String::isNotEmpty)?.let(segment)
        // Unit sections naturally split partial overlaps and T junctions before a single wall is emitted.
        for((key,sides) in boundary) {
            require(sides.size<=2) { "More than two rooms share a wall section" }
            // OPEN omits this room's wall; it never removes an adjoining hall's explicit enclosure.
            val enclosed=sides.filter { it.span.policy!=WallPolicy.OPEN }
            if(enclosed.isEmpty() || enclosed.any { it.span.policy==WallPolicy.EXPLICIT }) continue
            val exterior=enclosed.size==1
            val template=if(exterior) wall else filler ?: wall
            if(template==null || template.air) continue
            require(template.length==1) { "Facade and partition defaults need a one-block source; place larger modules explicitly" }
            val side=enclosed.sortedBy { it.room.id+it.span.id }.first();val direction=side.span.direction
            val rotated=SegmentGeometry.rotated(template,RotationAxis.Y,direction)
            val low=enclosed.minOf { it.room.offset.y };val high=enclosed.maxOf { it.room.offset.y+wallHeight(it.room.room) }
            val origin=RoomTopology.wallOrigin(side.span.start,direction,template.depth)+side.room.offset.copy(y=low)
            val available=high-low
            require(template.height==available || template.height==1) { "Facade source must match room height or be one block high; profiles are never stretched" }
            for(y in 0 until available step template.height) for(entries in rotated.cells.groupBy { it.cell.pos }.values) {
                val c=entries.first().cell;val target=origin+c.pos+P(0,y,0)
                if(reserved(target) || c.empty || c.block=="minecraft:air") continue
                val value=placement(target,entries,0,"wall")
                contributions.getOrPut(target) { mutableListOf() }+=Contribution(value,"facade",true,setOf(key))
                shell+=target.copy(y=0)
                physical.getOrPut(key) { linkedSetOf() }+=target
            }
        }
        // AUTO segments contribute the room-facing finish. Their shared wall body belongs to the stockwork.
        // EXPLICIT bays retain their complete source geometry, including deep profiles.
        val finished=linkedMapOf<P,MutableList<Contribution>>()
        val physicalPositions=physical.values.flatten().toSet()
        val sourceDepth=mutableMapOf<Triple<String,BoundaryKey,Int>,Int>()
        for(values in contributions.values) for(value in values) for(key in value.keys) {
            val side=boundary[key].orEmpty().firstOrNull { owner(it.room,rooms.indexOf(it.room))==value.owner } ?: continue
            val id=Triple(value.owner,key,value.placement.pos.y)
            sourceDepth[id]=maxOf(sourceDepth[id] ?: Int.MIN_VALUE,inwardProjection(value.placement.pos,side.span.direction))
        }
        for((_,values) in contributions) for(value in values) {
            // Occupied plates stop at the actual wall body; outside plate layers remain underneath/above it.
            if(value.placement.component in setOf("floor","ceiling") && value.placement.pos in physicalPositions) continue
            val sides=value.keys.mapNotNull { key->boundary[key].orEmpty().firstOrNull { owner(it.room,rooms.indexOf(it.room))==value.owner }?.let { key to it } }
            val inherited=sides.filter { (key,side)->side.span.policy==WallPolicy.AUTO && physical[key]?.isNotEmpty()==true }
            if(value.owner=="facade" || value.placement.pos in retainedByOwner[value.owner].orEmpty() || inherited.isEmpty() || sides.any { it.second.span.policy==WallPolicy.EXPLICIT }) {
                finished.getOrPut(value.placement.pos) { mutableListOf() }+=value;continue
            }
            for((key,side) in inherited) {
                val direction=side.span.direction
                val candidates=physical[key].orEmpty().filter { it.y==value.placement.pos.y }
                if(candidates.isEmpty()) continue
                val target=candidates.maxWith(compareBy<P>({ inwardProjection(it,direction) },{ -it.x },{ -it.z }))
                if((sourceDepth[Triple(value.owner,key,value.placement.pos.y)] ?: Int.MIN_VALUE)>inwardProjection(value.placement.pos,direction)) continue
                val veneer=veneer(value.placement.copy(pos=target),direction) ?: continue
                finished.getOrPut(target) { mutableListOf() }+=value.copy(placement=veneer,keys=setOf(key))
            }
        }
        contributions.clear();contributions.putAll(finished)
        // An optional explicit room finish uses the same physical wall, including the opposite face of a partition.
        for((key,sides) in boundary) for(side in sides) {
            if(side.span.policy==WallPolicy.OPEN) continue
            val source=side.room.room.definition?.roomConfig?.interiorSource.orEmpty()
            if(source.isEmpty()) continue
            val value=segment(source)
            require(value.length==1 && value.depth==1 && (value.height==1 || value.height==wallHeight(side.room.room))) { "Interior finish must be one block wide/deep and one block high or match room height" }
            val rotated=SegmentGeometry.rotated(value,RotationAxis.Y,side.span.direction)
            val cells=physical[key].orEmpty()
            for(y in 0 until wallHeight(side.room.room)) {
                val target=cells.filter { it.y==y+side.room.offset.y }.maxByOrNull { inwardProjection(it,side.span.direction) } ?: continue
                val entries=rotated.cells.filter { it.cell.pos.y==y%value.height }
                if(entries.isEmpty()) continue
                val finish=veneer(placement(target,entries,0,"wall"),side.span.direction) ?: continue
                contributions.getOrPut(target) { mutableListOf() }.add(0,Contribution(finish,owner(side.room,rooms.indexOf(side.room)),true,setOf(key)))
            }
        }
        val cuts=rooms.flatMap { instance -> instance.room.openings.map { opening ->
            val moved=opening.copy(frame=opening.frame.moved(instance.offset));val depth=wall?.depth ?: 1
            val localNear=-(depth+2)*16;val localFar=(depth+instance.room.topology.instances.firstOrNull { it.definition.id==opening.instanceId }?.value?.depth.orOne()+2)*16
            Cut(moved,instance,openingVolume(moved,copycat,localNear,localFar),localNear,localFar,keysFor(instance,opening.instanceId),sourcePositions(instance,opening))
        } }
        val output=linkedMapOf<P,MutableList<Placement>>()
        val removedWallCells=linkedSetOf<P>()
        contributions.forEach { (position,values) ->
            // Relocated opposite-side finishes must precede the common body regardless of source-map order.
            for(value in values.sortedBy { if(it.owner=="facade") 1 else 0 }) {
                var remaining:Placement?=value.placement
                if(value.boundary) for(cut in cuts) {
                    if(remaining==null) break
                    if(value.keys.intersect(cut.keys).isEmpty()) continue
                    val owns=value.owner==owner(cut.room,rooms.indexOf(cut.room))
                    if(cut.opening.spec.retainSource && owns && position in cut.retained) continue
                    val before=remaining
                    remaining=cut(remaining,cut.volume)
                    if(remaining==null && before!=null) removedWallCells+=position
                }
                remaining?.let { output.getOrPut(position) { mutableListOf() }+=it }
            }
        }
        val inserts=linkedMapOf<P,MutableList<Placement>>()
        for(cut in cuts) {
            val spec=cut.opening.spec
            if(spec.insetSource.isEmpty()) continue
            val source=copycat(spec.insetSource)
            require(source.block.isEmpty()) { "Window and door inserts require Copycat geometry" }
            for(x in 0 until spec.size.x) for(y in 0 until spec.size.y) for(z in 0 until spec.size.z) {
                val p=cut.opening.frame.cell(spec.position+P(x,y,z))
                if(reserved(p)) continue
                val mask=cut.volume.translated(P(-p.x*16,-p.y*16,-p.z*16))
                val transformed=transformParts(source.parts,cut.opening.frame)
                val parts=transformed.mapNotNull { part -> ExactGeometry.part(part).intersect(mask).takeUnless { it.isEmpty() }?.let { part.copy(exactClip=it) } }
                if(parts.isEmpty()) continue
                val placed=source.copy(pos=p,parts=parts,component="trim")
                val previous=inserts.getOrPut(p) { mutableListOf() }
                for(old in previous) require(old.sameVisual(placed) || Geometry.exact(old.parts).intersect(Geometry.exact(placed.parts)).isEmpty()) { "Conflicting window or door inserts at $p" }
                if(previous.none { it.sameVisual(placed) }) previous+=placed
            }
        }
        inserts.forEach { (p,values)->output.getOrPut(p) { mutableListOf() }.addAll(values) }
        // Interior finishes are geometry, so openings also cut them before insertion.
        val resolver=Resolver()
        output.forEach { (p,values)->resolver.add(RoomTopology.merge(values));clears-=p }
        // A removed complete wall cell becomes air only when no frame or glass remains there.
        clears+=removedWallCells.filterNot { it in output || reserved(it) }
        clears.filterNot(output::containsKey).forEach(resolver::clear)
        return CompiledStorey(resolver.plan(warnings.distinct()),shell,minimum,maximum,bottom.takeUnless { it==Int.MAX_VALUE } ?: 0,top.takeUnless { it==Int.MAX_VALUE } ?: 0)
    }

    private fun key(span:RoomBoundarySpan,offset:P):BoundaryKey {
        val a=span.start+offset.copy(y=0);val b=span.end+offset.copy(y=0)
        return if(a.x<b.x || a.z<b.z) BoundaryKey(a,b) else BoundaryKey(b,a)
    }
    private fun keysFor(instance:SemanticRoomInstance,id:String):Set<BoundaryKey> = instance.room.topology.spans.filter { it.instanceId==id }.map { key(it,instance.offset) }.toSet()
    private fun keysAt(instance:SemanticRoomInstance,position:P):Set<BoundaryKey> {
        val ids=instance.room.topology.instances.filter { item->
            val p=position-P(item.position.x+instance.offset.x,item.position.y+instance.offset.y,item.position.z+instance.offset.z)
            p.x in 0 until item.value.length && p.y in 0 until item.value.height && p.z in 0 until item.value.depth
        }.map { it.definition.id }.toSet()
        return instance.room.topology.spans.filter { span->span.instanceId in ids &&
            if(span.start.x!=span.end.x) position.x==minOf(span.start.x,span.end.x)+instance.offset.x
            else position.z==minOf(span.start.z,span.end.z)+instance.offset.z
        }.map { key(it,instance.offset) }.toSet()
    }
    private fun inwardProjection(p:P,direction:Int)=when(direction) { 0->p.z;1->-p.x;2->-p.z;else->p.x }
    private fun veneer(value:Placement,direction:Int):Placement? {
        val slab=when(direction) {
            0->ExactVolume.box(0,0,15,16,16,16);1->ExactVolume.box(0,0,0,1,16,16)
            2->ExactVolume.box(0,0,0,16,16,1);else->ExactVolume.box(15,0,0,16,16,16)
        }
        if(value.block.isNotEmpty()) error("Room-facing finishes require Copycat geometry; ${value.block} cannot be clipped to an interior surface")
        val parts=value.parts.mapNotNull { p->ExactGeometry.part(p).intersect(slab).takeUnless { it.isEmpty() }?.let { p.copy(exactClip=it) } }
        return parts.takeIf { it.isNotEmpty() }?.let { value.copy(parts=it) }
    }
    private fun wallHeight(room:SemanticRoom)=room.definition?.roomConfig?.heightOverride ?: room.topology.height.takeIf { it>0 } ?: room.definition?.height ?: room.compiled.usableHeight
    private fun Int?.orOne()=this ?: 1
    private fun owner(room:SemanticRoomInstance,index:Int)=room.id.ifEmpty { "room_$index" }
    private fun sourcePositions(instance:SemanticRoomInstance,opening:ResolvedRoomOpening):Set<P> = instance.room.topology.instances.firstOrNull { it.definition.id==opening.instanceId }?.let { item ->
        val positions=item.value.cells.map { it.cell.pos+item.position+instance.offset }
        if(opening.sourceDimensions==P()) positions.toSet() else {
            val frame=opening.frame.moved(instance.offset)
            val corners=listOf(P(),opening.sourceDimensions).map(frame::point)
            val low=P(corners.minOf { it.x },corners.minOf { it.y },corners.minOf { it.z })
            val high=P(corners.maxOf { it.x },corners.maxOf { it.y },corners.maxOf { it.z })
            positions.filter { it.x in low.x until high.x && it.y in low.y until high.y && it.z in low.z until high.z }.toSet()
        }
    }.orEmpty()

    /** Converts a free mask's convex XY silhouette into a prism through every wall layer. */
    internal fun openingVolume(opening:ResolvedRoomOpening,copycat:(String)->Placement,zMin:Int,zMax:Int):ExactVolume {
        val spec=opening.spec
        val local=if(spec.maskSource.isEmpty()) ExactVolume.box(spec.position.x*16,spec.position.y*16,zMin,
            (spec.position.x+spec.size.x)*16,(spec.position.y+spec.size.y)*16,zMax)
        else {
            val source=copycat(spec.maskSource)
            require(source.block.isEmpty()) { "Free opening masks require Copycat geometry" }
            val exact=Geometry.exact(source.parts)
            ExactVolume(exact.cells.mapNotNull { cell ->
                val zero=cell.planes.filter { it.z==0L };val upper=cell.planes.filter { it.z>0 };val lower=cell.planes.filter { it.z<0 }
                val projected=zero+upper.flatMap { a->lower.map { b->ExactPlane(a.x*(-b.z)+b.x*a.z,a.y*(-b.z)+b.y*a.z,0,a.d*(-b.z)+b.d*a.z) } }
                val planes=projected.filter { it.x!=0L || it.y!=0L }.map { p ->
                    ExactPlane(p.x*spec.size.y,p.y*spec.size.x,0,p.d*spec.size.x*spec.size.y).normalized().translated(P(spec.position.x*16,spec.position.y*16,0))
                }+listOf(ExactPlane(z=1,d=zMax.toLong()),ExactPlane(z=-1,d=-zMin.toLong()))
                ExactCell(planes).takeUnless { it.isEmpty() }
            })
        }
        val frame=opening.frame
        if(frame.shapeXAxis==null && frame.shapeYAxis==null && frame.shapeZAxis==null) return transformed(local,frame,P(frame.origin.x*16,frame.origin.y*16,frame.origin.z*16))
        val shape=frame.copy(origin=P(),xAxis=frame.shapeXAxis ?: frame.xAxis,yAxis=frame.shapeYAxis ?: frame.yAxis,zAxis=frame.shapeZAxis ?: frame.zAxis,
            shapeXAxis=null,shapeYAxis=null,shapeZAxis=null)
        var result=ExactVolume()
        for(x in spec.position.x until spec.position.x+spec.size.x) for(y in spec.position.y until spec.position.y+spec.size.y) {
            val cell=P(x,y,0)
            val piece=local.intersect(ExactVolume.box(x*16,y*16,zMin,(x+1)*16,(y+1)*16,zMax)).translated(P(-x*16,-y*16,0))
            if(!piece.isEmpty()) result=result.union(transformed(piece,shape,frame.microCellPoint(cell,P())))
        }
        return result
    }
    private fun transformed(volume:ExactVolume,frame:SemanticTransform,microOrigin:P):ExactVolume = ExactVolume(volume.cells.map { cell -> ExactCell(cell.planes.map { p ->
        val n=frame.vector(P(p.x.toInt(),p.y.toInt(),p.z.toInt()))
        ExactPlane(n.x.toLong(),n.y.toLong(),n.z.toLong(),p.d+n.x.toLong()*microOrigin.x+n.y.toLong()*microOrigin.y+n.z.toLong()*microOrigin.z).normalized()
    }) })
    internal fun cut(value:Placement,global:ExactVolume):Placement? {
        val local=global.translated(P(-value.pos.x*16,-value.pos.y*16,-value.pos.z*16))
        if(value.block.isNotEmpty()) {
            val whole=ExactVolume.box(0,0,0,16,16,16)
            if(local.intersect(whole).isEmpty()) return value
            require(local.contains(whole)) { "A partial opening cannot cut Minecraft block ${value.block}; use a Copycat source" }
            return null
        }
        val parts=value.parts.mapNotNull { part->
            val exact=ExactGeometry.part(part);val clipped=exact.minus(local)
            if(clipped.isEmpty()) null else if(clipped==exact) part else part.copy(exactClip=clipped)
        }
        return parts.takeIf(List<Part>::isNotEmpty)?.let { value.copy(parts=it) }
    }
    private fun transformParts(parts:List<Part>,transform:SemanticTransform):List<Part> {
        val local=transform.copy(origin=P(),xAxis=transform.shapeXAxis ?: transform.xAxis,yAxis=transform.shapeYAxis ?: transform.yAxis,zAxis=transform.shapeZAxis ?: transform.zAxis,
            shapeXAxis=null,shapeYAxis=null,shapeZAxis=null)
        return parts.map { part ->
            val volume=ExactGeometry.part(part)
            val rotated=ExactVolume(volume.cells.map { cell->ExactCell(cell.planes.map { p->
                val n=local.vector(P(p.x.toInt(),p.y.toInt(),p.z.toInt()))
                val shift=P(if(local.xAxis.x+local.yAxis.x+local.zAxis.x<0) 16 else 0,
                    if(local.xAxis.y+local.yAxis.y+local.zAxis.y<0) 16 else 0,if(local.xAxis.z+local.yAxis.z+local.zAxis.z<0) 16 else 0)
                ExactPlane(n.x.toLong(),n.y.toLong(),n.z.toLong(),p.d+n.x.toLong()*shift.x+n.y.toLong()*shift.y+n.z.toLong()*shift.z).normalized()
            }) })
            part.copy(exact=rotated,exactClip=null,turn=0,tilt=0,spin=0,top=false,offset=P(),cutMask="",roof=null)
        }
    }
}
