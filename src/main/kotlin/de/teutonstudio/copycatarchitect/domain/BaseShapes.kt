package de.teutonstudio.copycatarchitect.domain

/** A selectable base keeps the real source item ID, including centered pane fractions. */
data class BaseShape(val source:String,val discCut:Int=0) {
    val id:String get() = source+when(discCut) { 1->"#half_disc";2->"#quarter_disc";else->"" }
    val name:String get() = when(discCut) { 1->"half_disc";2->"quarter_disc";else->Adapters.get(source).name }
    fun part(group:String="body")=Part(source=source,group=group,discCut=discCut)
}

object BaseShapes {
    const val DISC_SOURCE="copycats:copycat_flat_pane"
    val all:List<BaseShape> = checkNotNull(GraphModels.copycat.baseShapes)
    fun get(id:String):BaseShape = all.firstOrNull { it.id==id } ?: error("Unsupported base shape: $id")
    fun find(source:String,discCut:Int=0):BaseShape = all.firstOrNull { it.source==source && it.discCut==discCut }
        ?: error("Unsupported base shape: $source/$discCut")
}

enum class RotationAxis { X,Y,Z,NORMAL }

/** Quarter-turns around block center; vectors use the same rotation without its translation. */
internal fun rotatePoint(point:P,axis:RotationAxis,quarterTurns:Int,vector:Boolean=false):P {
    require(axis!=RotationAxis.NORMAL) { "A point rotation needs a world axis" }
    val edge=if(vector) 0 else 15
    var p=point
    repeat(Math.floorMod(quarterTurns,4)) {
        p=when(axis) {
            RotationAxis.X->P(p.x,edge-p.z,p.y)
            RotationAxis.Y->P(edge-p.z,p.y,p.x)
            RotationAxis.Z->P(edge-p.y,p.x,p.z)
            RotationAxis.NORMAL->error("A point rotation needs a world axis")
        }
    }
    return p
}

/** Reflection through the block center, or through the origin for displacement vectors. */
internal fun reflectPoint(point:P,axis:RotationAxis,vector:Boolean=false):P {
    return reflectPoint(point,CopycatMirrorPlane.fromAxis(axis),vector)
}

/** Finds a legal orientation for a native source or for every part in a built-in composition. */
object PartRotations {
    private val basis=listOf(P(1,0,0),P(0,1,0),P(0,0,1))
    private data class Key(val part:Part,val axis:RotationAxis,val quarterTurns:Int)
    private data class Cached(val part:Part?)
    private val cache=java.util.concurrent.ConcurrentHashMap<Key,Cached>()
    private val reflectionCache=java.util.concurrent.ConcurrentHashMap<Pair<Part,CopycatMirrorPlane>,Part>()
    fun normal(part:Part):P = part.oriented(P(0,1,0),true)

    fun rotate(part:Part,axis:RotationAxis,quarterTurns:Int):Part? {
        if(cache.size>2048) cache.clear()
        val key=Key(part,axis,Math.floorMod(quarterTurns,4))
        return cache.computeIfAbsent(key) { Cached(rotateUncached(it.part,it.axis,it.quarterTurns)) }.part
    }

    /** Prefer a native source orientation; arbitrary reflected geometry retains its paid source. */
    fun mirror(part:Part,axis:RotationAxis):Part {
        require(axis!=RotationAxis.NORMAL) { "A source reflection needs a world axis" }
        return mirror(part,CopycatMirrorPlane.fromAxis(axis))
    }
    fun mirror(part:Part,mirror:CopycatMirror):Part=mirror(part,mirror.plane)
    fun mirror(part:Part,plane:CopycatMirrorPlane):Part {
        if(reflectionCache.size>2048) reflectionCache.clear()
        return reflectionCache.computeIfAbsent(part to plane) { (source,reflection) -> mirrorUncached(source,reflection) }
    }

    private fun mirrorUncached(part:Part,plane:CopycatMirrorPlane):Part {
        if(plane.requiresExact) {
            val visible=ExactGeometry.part(part).reflected(plane)
            requireCopycatMirrorBounds(visible,plane)
            val source=ExactGeometry.part(part.copy(cutMask="",exactClip=null)).reflected(plane)
            // Keep source and clipping separate whenever that source still fits in a block.
            // Otherwise only the visible, in-bounds result is representable; its source receipt stays intact.
            return if(source.withinCopycatBlock()) transformedExact(part,source,"",if(part.cutMask.isEmpty() && part.exactClip==null) null else visible)
                else transformedExact(part,visible,"",null)
        }
        Solid.part(part)
        val offset=reflectPoint(part.offset,plane,true)
        val mask=if(part.cutMask.isEmpty()) "" else Solid.fromMask(part.cutMask).reflected(plane).mask()
        val exactClip=part.exactClip?.reflected(plane)
        val targetBasis=basis.map { reflectPoint(part.oriented(it,true),plane,true) }
        val adapter=Adapters.get(part.source)
        val candidates=buildList {
            for(top in listOf(part.top,!part.top)) for(tilt in (if(adapter.tiltable) 0..3 else 0..0)) for(turn in 0..3) for(spin in 0..3)
                add(part.copy(top=top,tilt=tilt,turn=turn,spin=spin,offset=offset,cutMask=mask,exactClip=exactClip))
        }.sortedWith(compareBy<Part> {
            (if(it.top==part.top) 0 else 4)+distance(it.turn,part.turn)+distance(it.tilt,part.tilt)+distance(it.spin,part.spin)
        }.thenBy { it.top!=part.top }.thenBy { it.spin }.thenBy { it.tilt }.thenBy { it.turn })
        val frame=candidates.firstOrNull { candidate -> basis.map { candidate.oriented(it,true) }==targetBasis }
        if(frame!=null) return frame.also { Solid.part(it) }
        val native=runCatching {
            if(ExactGeometry.usesExact(part)) {
                val target=ExactGeometry.part(part.copy(offset=P(),cutMask="",exactClip=null)).reflected(plane)
                candidates.firstOrNull { ExactGeometry.part(it.copy(offset=P(),cutMask="",exactClip=null)).equivalent(target) }
            } else {
                val target=Solid.part(part.copy(offset=P(),cutMask="")).reflected(plane).key()
                candidates.firstOrNull { Solid.part(it.copy(offset=P(),cutMask="")).key()==target }
            }
        }.getOrNull()
        if(native!=null) return native.also { Solid.part(it) }
        // An existing exact volume carries the reflected boundary without introducing a new
        // source item, rasterizing rational slopes, or losing clipped source provenance.
        return transformedExact(part,ExactGeometry.part(part.copy(cutMask="",exactClip=null)).reflected(plane),mask,exactClip)
    }

    private fun rotateUncached(part:Part,axis:RotationAxis,quarterTurns:Int):Part? = runCatching {
        Solid.part(part)
        val q=Math.floorMod(quarterTurns,4)
        if(q==0) return@runCatching part
        val normal=normal(part)
        val worldAxis=if(axis!=RotationAxis.NORMAL) axis else when {
            normal.x!=0->RotationAxis.X
            normal.y!=0->RotationAxis.Y
            else->RotationAxis.Z
        }
        // Keep local clockwise spin consistent after mounting; legacy world-Y turn has the opposite angular sense to X/Z.
        val signedQ=if(axis==RotationAxis.NORMAL) q*(normal.x+normal.y+normal.z)*(if(worldAxis==RotationAxis.Y) 1 else -1) else q
        val offset=rotatePoint(part.offset,worldAxis,signedQ,true)
        val mask=if(part.cutMask.isEmpty()) "" else Solid.fromMask(part.cutMask).rotate(worldAxis,signedQ).mask()
        val exactClip=part.exactClip?.rotate(worldAxis,signedQ)
        val targetBasis=basis.map { rotatePoint(part.oriented(it,true),worldAxis,signedQ,true) }
        val adapter=Adapters.get(part.source)
        val candidates=buildList {
            for(top in listOf(part.top,!part.top)) for(tilt in (if(adapter.tiltable) 0..3 else 0..0)) for(turn in 0..3) for(spin in 0..3) {
                add(part.copy(top=top,tilt=tilt,turn=turn,spin=spin,offset=offset,cutMask=mask,exactClip=exactClip))
            }
        }.sortedWith(compareBy<Part> {
            (if(it.top==part.top) 0 else 4)+distance(it.turn,part.turn)+distance(it.tilt,part.tilt)+distance(it.spin,part.spin)
        }.thenBy { it.top!=part.top }.thenBy { it.spin }.thenBy { it.tilt }.thenBy { it.turn })

        // Matching the frame preserves an invisible pane normal and makes later local spins predictable.
        val frame=candidates.firstOrNull { candidate -> basis.map { candidate.oriented(it,true) }==targetBasis }
        val result=frame ?: run {
            // Symmetric sources may have an equivalent native state with a different frame, e.g. upside-down stairs.
            if(ExactGeometry.usesExact(part)) {
                val target=ExactGeometry.part(part.copy(offset=P(),cutMask="",exactClip=null)).rotate(worldAxis,signedQ)
                candidates.firstOrNull { ExactGeometry.part(it.copy(offset=P(),cutMask="",exactClip=null)).equivalent(target) }
            } else {
                val target=Solid.part(part.copy(offset=P(),cutMask="")).rotate(worldAxis,signedQ).key()
                candidates.firstOrNull { Solid.part(it.copy(offset=P(),cutMask="")).key()==target }
            }
        } ?: return@runCatching if(part.exact!=null)
            transformedExact(part,ExactGeometry.part(part.copy(cutMask="",exactClip=null)).rotate(worldAxis,signedQ),mask,exactClip) else null
        Solid.part(result)
        result
    }.getOrNull()

    private fun distance(a:Int,b:Int):Int = minOf(Math.floorMod(a-b,4),Math.floorMod(b-a,4))
    private fun transformedExact(part:Part,volume:ExactVolume,mask:String,clip:ExactVolume?)=part.copy(turn=0,top=false,offset=P(),tilt=0,spin=0,
        cutMask=mask,exact=volume,exactClip=clip).also { Solid.part(it) }
}
