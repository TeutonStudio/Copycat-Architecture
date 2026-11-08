package de.teutonstudio.copycatarchitect.domain

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Plane coordinates are pixels (16 per block). The occupied side is n dot p <= d. */
data class ExactPlane(val x:Long=0,val y:Long=0,val z:Long=0,val d:Long=0) {
    fun opposite()=ExactPlane(-x,-y,-z,-d)
    fun translated(p:P)=copy(d=d+x*p.x+y*p.y+z*p.z)
    fun normalized():ExactPlane {
        require(x!=0L || y!=0L || z!=0L) { "An exact plane needs a normal" }
        require(listOf(x,y,z,d).all { it in -1_000_000_000L..1_000_000_000L }) { "Exact plane coefficient exceeds bounds" }
        fun gcd(a:Long,b:Long):Long=if(b==0L) abs(a) else gcd(b,a%b)
        val g=gcd(gcd(x,y),gcd(z,d)).coerceAtLeast(1)
        return ExactPlane(x/g,y/g,z/g,d/g)
    }
    fun rotate(axis:RotationAxis,q:Int):ExactPlane {
        var n=this
        repeat(Math.floorMod(q,4)) { n=when(axis) {
            RotationAxis.X->ExactPlane(n.x,-n.z,n.y,n.d-16*n.z)
            RotationAxis.Y->ExactPlane(-n.z,n.y,n.x,n.d-16*n.z)
            RotationAxis.Z->ExactPlane(-n.y,n.x,n.z,n.d-16*n.y)
            RotationAxis.NORMAL->error("An exact rotation needs a world axis")
        } }
        return n.normalized()
    }
    fun reflected(axis:RotationAxis):ExactPlane=reflected(CopycatMirrorPlane.fromAxis(axis))
    fun reflected(plane:CopycatMirrorPlane):ExactPlane {
        val n=plane.normal;val squared=(n.x*n.x+n.y*n.y+n.z*n.z).toLong()
        val dot=x*n.x+y*n.y+z*n.z
        val values=longArrayOf(squared*x-2*n.x*dot,squared*y-2*n.y*dot,squared*z-2*n.z*dot,
            squared*d-16L*(n.x+n.y+n.z)*dot)
        // Householder denominators introduce a common scale. Reduce that derived scale
        // before applying the existing coefficient limits to the resulting plane.
        fun gcd(a:Long,b:Long):Long=if(b==0L) abs(a) else gcd(b,a%b)
        val divisor=values.fold(0L,::gcd).coerceAtLeast(1)
        return ExactPlane(values[0]/divisor,values[1]/divisor,values[2]/divisor,values[3]/divisor).normalized()
    }
    internal fun at(p:ExactPoint)=p.x*x+p.y*y+p.z*z-ExactNumber.of(d)
    internal fun signAt(p:ExactPoint)=(p.hx*BigInteger.valueOf(x)+p.hy*BigInteger.valueOf(y)+p.hz*BigInteger.valueOf(z)-p.hd*BigInteger.valueOf(d)).signum()
}

/** Reduced arbitrary-precision rationals keep neighboring clipped planes bit-identical. */
data class ExactNumber private constructor(val numerator:BigInteger,val denominator:BigInteger):Comparable<ExactNumber> {
    operator fun plus(v:ExactNumber)=of(numerator*v.denominator+v.numerator*denominator,denominator*v.denominator)
    operator fun minus(v:ExactNumber)=of(numerator*v.denominator-v.numerator*denominator,denominator*v.denominator)
    operator fun times(v:ExactNumber)=of(numerator*v.numerator,denominator*v.denominator)
    operator fun times(v:Long)=of(numerator*BigInteger.valueOf(v),denominator)
    operator fun div(v:ExactNumber)=of(numerator*v.denominator,denominator*v.numerator)
    operator fun unaryMinus()=of(-numerator,denominator)
    override fun compareTo(other:ExactNumber)=(numerator*other.denominator).compareTo(other.numerator*denominator)
    fun signum()=numerator.signum()
    fun toDouble()=numerator.toDouble()/denominator.toDouble()
    override fun toString()=if(denominator==BigInteger.ONE) numerator.toString() else "$numerator/$denominator"
    companion object {
        val ZERO=of(0)
        fun of(n:Long)=ExactNumber(BigInteger.valueOf(n),BigInteger.ONE)
        fun of(n:BigInteger,d:BigInteger):ExactNumber {
            require(d.signum()!=0) { "Zero rational denominator" }
            val g=n.gcd(d);val s=if(d.signum()<0) -BigInteger.ONE else BigInteger.ONE
            return ExactNumber(n/g*s,d/g*s)
        }
    }
}
data class ExactPoint(val x:ExactNumber,val y:ExactNumber,val z:ExactNumber) {
    internal val hd:BigInteger by lazy { val a=x.denominator*y.denominator/x.denominator.gcd(y.denominator);a*z.denominator/a.gcd(z.denominator) }
    internal val hx:BigInteger by lazy { x.numerator*(hd/x.denominator) }
    internal val hy:BigInteger by lazy { y.numerator*(hd/y.denominator) }
    internal val hz:BigInteger by lazy { z.numerator*(hd/z.denominator) }
    operator fun plus(v:ExactPoint)=ExactPoint(x+v.x,y+v.y,z+v.z)
    operator fun minus(v:ExactPoint)=ExactPoint(x-v.x,y-v.y,z-v.z)
    operator fun times(v:ExactNumber)=ExactPoint(x*v,y*v,z*v)
    fun component(axis:Int)=when(axis) { 0->x;1->y;else->z }
}
data class ExactSurface(val plane:ExactPlane,val vertices:List<ExactPoint>)
data class OwnedExactSurface(val surface:ExactSurface,val group:String)
data class ExactCell(val planes:List<ExactPlane> = emptyList()) {
    internal fun canonical():ExactCell {
        fun gcd(a:Long,b:Long):Long=if(b==0L) abs(a) else gcd(b,a%b)
        val strongest=linkedMapOf<Triple<Long,Long,Long>,Pair<ExactPlane,Long>>()
        for(plane in planes) {
            val p=plane.normalized();val g=gcd(gcd(p.x,p.y),p.z)
            val key=Triple(p.x/g,p.y/g,p.z/g);val old=strongest[key]
            if(old==null || p.d*old.second<old.first.d*g) strongest[key]=p to g
        }
        return ExactCell(strongest.values.map { it.first }.sortedWith(compareBy({it.x},{it.y},{it.z},{it.d})))
    }
    fun surfaces():List<ExactSurface> = ExactGeometry.cellSurfaces(this)
    fun vertices()=ExactGeometry.cellVertices(this)
    fun isEmpty()=surfaces().isEmpty()
}

/** Serializable convex half-space cells. No mesh data or floating point enters saved forms. */
data class ExactVolume(val cells:List<ExactCell> = emptyList()) {
    fun isEmpty()=cells.none { !it.isEmpty() }
    fun union(other:ExactVolume)=ExactVolume((cells+other.cells).distinct())
    fun intersect(other:ExactVolume):ExactVolume=ExactGeometry.operation(0,this,other) { intersectUncached(other) }
    private fun intersectUncached(other:ExactVolume):ExactVolume {
        if(cells==other.cells) return this
        if(cells.isEmpty() || other.cells.isEmpty()) return ExactVolume()
        return checked(cells.flatMap { a -> other.cells.mapNotNull { b ->
            when {
                ExactGeometry.disjoint(a,b)->null
                ExactGeometry.cellContains(a,b)->b
                ExactGeometry.cellContains(b,a)->a
                else->ExactCell(a.planes+b.planes).canonical().takeUnless { it.isEmpty() }
            }
        } }.distinct())
    }
    fun minus(other:ExactVolume):ExactVolume=ExactGeometry.operation(1,this,other) { minusUncached(other) }
    private fun minusUncached(other:ExactVolume):ExactVolume {
        if(cells==other.cells) return ExactVolume()
        var remaining=cells
        for(cutter in other.cells) {
            if(cutter.isEmpty()) continue
            remaining=remaining.flatMap { source ->
                if(ExactGeometry.cellContains(cutter,source)) emptyList() else if(ExactGeometry.disjoint(source,cutter) || ExactCell(source.planes+cutter.planes).canonical().isEmpty()) listOf(source) else {
                    val pieces=mutableListOf<ExactCell>();var inside=source
                    for(plane in cutter.planes) {
                        val signs=inside.vertices().map { plane.signAt(it) }
                        if(signs.all { it<=0 }) continue
                        if(signs.all { it>=0 }) { pieces+=inside;break }
                        val outside=ExactCell(inside.planes+plane.opposite()).canonical()
                        if(!outside.isEmpty()) pieces+=outside
                        inside=ExactCell(inside.planes+plane).canonical()
                        if(inside.isEmpty()) break
                    };pieces
                }
            }.distinct()
            require(remaining.size<=4096) { "Exact geometry exceeds 4096 convex pieces" }
            if(remaining.isEmpty()) break
        }
        return checked(remaining)
    }
    fun contains(other:ExactVolume):Boolean {
        if(cells==other.cells || other.cells.isEmpty()) return true
        if(cells.isEmpty()) return other.isEmpty()
        if(!ExactGeometry.boundsContain(this,other)) return false
        if(other.cells.any { cell -> cell.vertices().any { !containsPoint(it) } }) return false
        return other.minus(this).isEmpty()
    }
    fun containsPoint(point:ExactPoint)=cells.any { cell -> cell.planes.all { it.signAt(point)<=0 } }
    fun equivalent(other:ExactVolume):Boolean {
        if(cells==other.cells) return true
        if(!ExactGeometry.boundsContain(this,other) || !ExactGeometry.boundsContain(other,this)) return false
        return contains(other) && other.contains(this)
    }
    /** Ray picking uses the exact planes; collision's conservative raster is never a material owner. */
    fun rayEntry(origin:DoubleArray,direction:DoubleArray):Double? {
        var nearest:Double?=null
        for(cell in cells) {
            if(cell.isEmpty()) continue
            var enter=0.0;var exit=Double.POSITIVE_INFINITY;var misses=false
            for(p in cell.planes) {
                val denominator=p.x*direction[0]+p.y*direction[1]+p.z*direction[2]
                val distance=p.d-(p.x*origin[0]+p.y*origin[1]+p.z*origin[2])
                if(kotlin.math.abs(denominator)<1e-12) { if(distance< -1e-9) { misses=true;break } }
                else if(denominator<0) enter=maxOf(enter,distance/denominator) else exit=minOf(exit,distance/denominator)
                if(exit<enter-1e-9) { misses=true;break }
            }
            if(!misses && exit>=0 && (nearest==null || enter<nearest)) nearest=enter
        }
        return nearest
    }
    fun translated(p:P)=ExactVolume(cells.map { c -> ExactCell(c.planes.map { it.translated(p) }) })
    fun translatedY(offset:ExactNumber)=ExactVolume(cells.map { cell -> ExactCell(cell.planes.map { p ->
        val d=ExactNumber.of(p.d)+offset*p.y
        fun scaled(n:Long)=(BigInteger.valueOf(n)*d.denominator).longValueExact()
        ExactPlane(scaled(p.x),scaled(p.y),scaled(p.z),d.numerator.longValueExact()).normalized()
    }) })
    fun rotate(axis:RotationAxis,q:Int)=ExactVolume(cells.map { c -> ExactCell(c.planes.map { it.rotate(axis,q) }) })
    fun reflected(axis:RotationAxis):ExactVolume {
        require(axis!=RotationAxis.NORMAL) { "An exact reflection needs a world axis" }
        return reflected(CopycatMirrorPlane.fromAxis(axis))
    }
    fun reflected(plane:CopycatMirrorPlane):ExactVolume = if(!plane.requiresExact)
        ExactVolume(cells.map { c -> ExactCell(c.planes.map { it.reflected(plane) }) })
    else ExactVolume(cells.mapNotNull { cell ->
        val points=cell.vertices().map { reflectPoint(it,plane) }
        if(points.isEmpty()) return@mapNotNull null
        // The exact surface builder starts from finite axis bounds. These planes bound the
        // transformed polyhedron without cropping it, including rational coordinates outside the block.
        val bounds=(0..2).flatMap { axis ->
            val minimum=points.minOf { it.component(axis) };val maximum=points.maxOf { it.component(axis) }
            fun bound(value:ExactNumber,sign:Long):ExactPlane {
                val coefficient=value.denominator.longValueExact()*sign
                return ExactPlane(if(axis==0) coefficient else 0,if(axis==1) coefficient else 0,if(axis==2) coefficient else 0,
                    value.numerator.longValueExact()*sign).normalized()
            }
            listOf(bound(minimum,-1),bound(maximum,1))
        }
        ExactCell(cell.planes.map { it.reflected(plane) }+bounds).canonical()
    })
    fun reflectedY()=reflected(RotationAxis.Y)
    fun surfaces():List<ExactSurface> {
        val result=mutableListOf<ExactSurface>()
        for((index,cell) in cells.withIndex()) for(face in cell.surfaces()) {
            var polygons=listOf(face.vertices)
            for((otherIndex,other) in cells.withIndex()) {
                if(otherIndex==index) continue
                // A cell across the face hides it. Coincident outside faces are owned once.
                val across=other.vertices().any { face.plane.signAt(it)>0 }
                val duplicate=otherIndex<index && other.planes.any { it.normalized()==face.plane }
                if(across || duplicate) polygons=polygons.flatMap { ExactGeometry.subtractPolygon(it,other.planes) }
                if(polygons.isEmpty()) break
            }
            polygons.filter { it.size>=3 }.forEach { result+=ExactSurface(face.plane,it) }
        }
        return result
    }
    fun solid():Solid=ExactGeometry.solid(this)
    internal fun rasterized():Solid {
        var result=Solid()
        for(cell in cells) {
            val vertices=cell.vertices();if(vertices.isEmpty()) continue
            for(y in 0..15) for(z in 0..15) for(x in 0..15) {
                if(result.has(x,y,z)) continue
                var outside=false;var inside=true
                for(p in cell.planes) {
                    val minimum=p.x*(if(p.x>=0) x else x+1)+p.y*(if(p.y>=0) y else y+1)+p.z*(if(p.z>=0) z else z+1)
                    val maximum=p.x*(if(p.x>=0) x+1 else x)+p.y*(if(p.y>=0) y+1 else y)+p.z*(if(p.z>=0) z+1 else z)
                    if(minimum>=p.d) { outside=true;break }
                    if(maximum>p.d) inside=false
                }
                if(outside) continue
                if(inside || !intersectCell(cell,boxCell(x,y,z,x+1,y+1,z+1)).isEmpty()) result.set(x,y,z)
            }
        }
        return result
    }
    companion object {
        fun box(x:Int,y:Int,z:Int,maxX:Int,maxY:Int,maxZ:Int)=if(maxX<=x || maxY<=y || maxZ<=z) ExactVolume() else ExactVolume(listOf(boxCell(x,y,z,maxX,maxY,maxZ)))
        fun convex(planes:List<ExactPlane>)=checked(listOf(ExactCell(boxCell(0,0,0,16,16,16).planes+planes).canonical()).filterNot { it.isEmpty() })
        fun fromSolid(solid:Solid)=ExactVolume(solid.boxes().map { boxCell(it.x,it.y,it.z,it.maxX,it.maxY,it.maxZ) })
        private fun checked(cells:List<ExactCell>):ExactVolume {
            require(cells.size<=4096 && cells.all { it.planes.size<=128 }) { "Exact geometry exceeds complexity bounds" }
            return ExactVolume(cells)
        }
        private fun boxCell(x:Int,y:Int,z:Int,X:Int,Y:Int,Z:Int)=ExactCell(listOf(
            ExactPlane(1,0,0,X.toLong()),ExactPlane(-1,0,0,-x.toLong()),ExactPlane(0,1,0,Y.toLong()),ExactPlane(0,-1,0,-y.toLong()),ExactPlane(0,0,1,Z.toLong()),ExactPlane(0,0,-1,-z.toLong())))
        private fun intersectCell(a:ExactCell,b:ExactCell)=ExactCell(a.planes+b.planes).canonical()
    }
}

/** The reduced ratio describes vertical rise per horizontal run; phase and slice select a repeat cell. */
data class RoofPartParameters(val rise:Int=1,val run:Int=1,val phase:Int=0,val slice:Int=0,val variant:String="slope",val thickness:Int=2) {
    companion object { const val SOURCE="copycat_architect:copycat_roof" }
}

object ExactGeometry {
    private data class Operation(val kind:Int,val a:ExactVolume,val b:ExactVolume)
    private val operationCache=ConcurrentHashMap<Operation,ExactVolume>()
    internal fun operation(kind:Int,a:ExactVolume,b:ExactVolume,calculate:()->ExactVolume):ExactVolume {
        val key=Operation(kind,a,b);operationCache[key]?.let { return it }
        val result=calculate()
        if(operationCache.size>1024) operationCache.clear()
        operationCache.putIfAbsent(key,result)
        return result
    }
    private val cellCache=ConcurrentHashMap<ExactCell,List<ExactSurface>>()
    private val vertexCache=ConcurrentHashMap<ExactCell,List<ExactPoint>>()
    private val boundsCache=ConcurrentHashMap<ExactCell,List<ExactNumber>>()
    private val partCache=ConcurrentHashMap<Part,ExactVolume>()
    private val solidCache=ConcurrentHashMap<ExactVolume,Solid>()
    internal fun cellVertices(cell:ExactCell):List<ExactPoint> {
        if(vertexCache.size>8192) vertexCache.clear()
        return vertexCache.computeIfAbsent(cell.canonical()) { cellSurfaces(it).flatMap { it.vertices }.distinct() }
    }
    private fun bounds(cell:ExactCell):List<ExactNumber> {
        if(boundsCache.size>8192) boundsCache.clear()
        return boundsCache.computeIfAbsent(cell.canonical()) { c ->
            val vertices=cellVertices(c)
            if(vertices.isEmpty()) emptyList() else (0..2).map { axis -> vertices.minOf { it.component(axis) } }+(0..2).map { axis -> vertices.maxOf { it.component(axis) } }
        }
    }
    internal fun disjoint(a:ExactCell,b:ExactCell):Boolean {
        val left=bounds(a);val right=bounds(b)
        return left.isEmpty() || right.isEmpty() || (0..2).any { left[it]>=right[it+3] || right[it]>=left[it+3] }
    }
    internal fun cellContains(a:ExactCell,b:ExactCell):Boolean {
        if(a==b) return true
        val vertices=cellVertices(b)
        return vertices.isNotEmpty() && vertices.all { p -> a.planes.all { it.signAt(p)<=0 } }
    }
    internal fun boundsContain(a:ExactVolume,b:ExactVolume):Boolean {
        val left=a.cells.map(::bounds).filter { it.isNotEmpty() };val right=b.cells.map(::bounds).filter { it.isNotEmpty() }
        if(right.isEmpty()) return true
        if(left.isEmpty()) return false
        return (0..2).all { axis -> left.minOf { it[axis] }<=right.minOf { it[axis] } && left.maxOf { it[axis+3] }>=right.maxOf { it[axis+3] } }
    }
    fun solid(volume:ExactVolume):Solid {
        if(solidCache.size>1024) solidCache.clear()
        return solidCache.computeIfAbsent(volume) { it.rasterized() }
    }
    fun visibleSurfaces(volume:ExactVolume,occluders:List<ExactVolume>):List<ExactSurface> {
        return volume.surfaces().flatMap { surface ->
            var polygons=listOf(surface.vertices)
            for(other in occluders) for(cell in other.cells) {
                if(cell.vertices().any { surface.plane.signAt(it)>0 }) polygons=polygons.flatMap { subtractPolygon(it,cell.planes) }
            }
            polygons.map { ExactSurface(surface.plane,it) }
        }
    }
    fun usesExact(part:Part)=part.exact!=null || part.exactClip!=null || part.source==RoofPartParameters.SOURCE
    fun part(part:Part):ExactVolume {
        if(partCache.size>2048) partCache.clear()
        return partCache.computeIfAbsent(part) { p ->
            require(p.turn in 0..3 && p.tilt in 0..3 && p.spin in 0..3) { "Invalid exact source rotation" }
            val adapter=Adapters.get(p.source)
            require(p.size in 1..adapter.max && (p.tilt==0 || adapter.tiltable) && p.discCut in 0..2 && (p.discCut==0 || p.source==BaseShapes.DISC_SOURCE)) { "Invalid exact source state" }
            require(listOf(p.offset.x,p.offset.y,p.offset.z).all { it in -15..15 }) { "Offset outside block" }
            var volume=if(p.exact!=null || p.source==RoofPartParameters.SOURCE) {
                var source=p.exact ?: roof(p.roof ?: RoofPartParameters())
                validate(source)
                if(p.top) source=source.reflectedY()
                source.rotate(RotationAxis.Y,p.spin).rotate(RotationAxis.X,p.tilt).rotate(RotationAxis.Y,p.turn).translated(p.offset)
            } else ExactVolume.fromSolid(Solid.part(p.copy(cutMask="",exactClip=null)))
            val zero=ExactNumber.of(0);val edge=ExactNumber.of(16)
            require(volume.cells.all { c -> c.vertices().all { v -> listOf(v.x,v.y,v.z).all { it>=zero && it<=edge } } }) { "Part exceeds block boundary" }
            if(p.cutMask.isNotEmpty()) volume=volume.intersect(ExactVolume.fromSolid(Solid.fromMask(p.cutMask)))
            if(p.exactClip!=null) { validate(p.exactClip);volume=volume.intersect(p.exactClip) }
            require(!volume.isEmpty()) { "Exact source clipping is empty" }
            volume
        }
    }
    fun roof(p:RoofPartParameters):ExactVolume {
        require(p.rise in 1..512 && p.run in 1..512 && p.phase in 0 until p.run && p.slice in -512..512 && p.thickness in 1..16) { "Invalid roof repeat profile" }
        require(p.variant.matches(Regex("[a-zA-Z0-9_-]{1,36}"))) { "Invalid roof part variant" }
        // Surface y=(rise*z + 16*phase)/run - 16*slice. Thickness is vertical pixels.
        val d=16L*p.phase-16L*p.slice*p.run
        val q=p.run.toLong();val rise=p.rise.toLong()
        val forward=ExactPlane(0,q,-rise,d)
        val backward=ExactPlane(0,q,rise,d+16*rise)
        val left=ExactPlane(-rise,q,0,d)
        val right=ExactPlane(rise,q,0,d+16*rise)
        val top=when(p.variant) {
            "ridge"->listOf(forward,backward)
            "hip","valley"->listOf(forward,left)
            "corner","cap"->listOf(forward,backward,left,right)
            else->listOf(forward)
        }
        fun lower(plane:ExactPlane)=plane.opposite().copy(d= -plane.d+q*p.thickness)
        return top.fold(ExactVolume()) { volume,active ->
            val planes=if(p.variant=="valley") listOf(active)+top.map(::lower) else top+lower(active)
            volume.union(ExactVolume.convex(planes))
        }
    }
    fun validate(volume:ExactVolume) {
        require(volume.cells.size<=4096 && volume.cells.all { it.planes.size in 4..128 }) { "Invalid exact geometry complexity" }
        volume.cells.forEach { c ->
            c.planes.forEach { it.normalized() }
            for(axis in 0..2) {
                val aligned=c.planes.filter { p -> listOf(p.x,p.y,p.z).withIndex().all { (i,n) -> i==axis || n==0L } }
                require(aligned.any { p -> listOf(p.x,p.y,p.z)[axis]>0 } && aligned.any { p -> listOf(p.x,p.y,p.z)[axis]<0 }) { "Exact cells need finite axis bounds" }
            }
        }
        require(!volume.isEmpty()) { "Exact source is empty" }
    }
    internal fun cellSurfaces(cell:ExactCell):List<ExactSurface> {
        if(cellCache.size>8192) cellCache.clear()
        return cellCache.computeIfAbsent(cell.canonical()) { c ->
            val bounds=(0..2).flatMap { axis -> listOf(-1,1).map { sign ->
                c.planes.firstOrNull { p -> val ns=listOf(p.x,p.y,p.z);ns[axis]*sign>0 && ns.withIndex().all { (i,n) -> i==axis || n==0L } }
            } }
            if(bounds.any { it==null }) return@computeIfAbsent emptyList()
            val planes=bounds.filterNotNull()
            fun coordinate(index:Int):ExactNumber { val p=planes[index];return ExactNumber.of(p.d)/ExactNumber.of(listOf(p.x,p.y,p.z)[index/2]) }
            val x0=coordinate(0);val x1=coordinate(1);val y0=coordinate(2);val y1=coordinate(3);val z0=coordinate(4);val z1=coordinate(5)
            if(x0>=x1 || y0>=y1 || z0>=z1) return@computeIfAbsent emptyList()
            fun p(x:ExactNumber,y:ExactNumber,z:ExactNumber)=ExactPoint(x,y,z)
            var faces=listOf(
                ExactSurface(planes[0],listOf(p(x0,y0,z0),p(x0,y0,z1),p(x0,y1,z1),p(x0,y1,z0))),
                ExactSurface(planes[1],listOf(p(x1,y0,z0),p(x1,y1,z0),p(x1,y1,z1),p(x1,y0,z1))),
                ExactSurface(planes[2],listOf(p(x0,y0,z0),p(x1,y0,z0),p(x1,y0,z1),p(x0,y0,z1))),
                ExactSurface(planes[3],listOf(p(x0,y1,z0),p(x0,y1,z1),p(x1,y1,z1),p(x1,y1,z0))),
                ExactSurface(planes[4],listOf(p(x0,y0,z0),p(x0,y1,z0),p(x1,y1,z0),p(x1,y0,z0))),
                ExactSurface(planes[5],listOf(p(x0,y0,z1),p(x1,y0,z1),p(x1,y1,z1),p(x0,y1,z1))))
            for(plane in c.planes.filterNot { it in planes }) {
                val vertices=faces.flatMap { it.vertices }.distinct()
                if(vertices.all { plane.signAt(it)<=0 }) continue
                if(vertices.all { plane.signAt(it)>=0 }) return@computeIfAbsent emptyList()
                val clipped=faces.mapNotNull { face -> clipPolygon(face.vertices,plane).takeIf { it.size>=3 }?.let { ExactSurface(face.plane,it) } }
                val cap=orderedSurface(plane,clipped.flatMap { it.vertices }.filter { plane.signAt(it)==0 }.distinct())
                faces=clipped+listOfNotNull(cap)
                if(faces.isEmpty()) return@computeIfAbsent emptyList()
            }
            val vertices=faces.flatMap { it.vertices }.distinct()
            if(vertices.size<4 || faces.any { face -> vertices.all { face.plane.signAt(it)==0 } }) emptyList() else faces
        }
    }
    private fun orderedSurface(plane:ExactPlane,points:List<ExactPoint>):ExactSurface? {
        if(points.size<3) return null
        val axis=when { abs(plane.x)>=abs(plane.y) && abs(plane.x)>=abs(plane.z)->0;abs(plane.y)>=abs(plane.z)->1;else->2 }
        val u=(axis+1)%3;val v=(axis+2)%3
        val sorted=points.sortedWith(compareBy({it.component(u)},{it.component(v)}))
        fun cross(a:ExactPoint,b:ExactPoint,c:ExactPoint)=(b.component(u)-a.component(u))*(c.component(v)-a.component(v))-(b.component(v)-a.component(v))*(c.component(u)-a.component(u))
        fun hull(sequence:List<ExactPoint>):List<ExactPoint> { val out=mutableListOf<ExactPoint>();for(p in sequence) { while(out.size>=2 && cross(out[out.size-2],out.last(),p).signum()<=0) out.removeAt(out.lastIndex);out+=p };return out }
        val polygon=hull(sorted).dropLast(1)+hull(sorted.reversed()).dropLast(1)
        return if(polygon.size<3) null else ExactSurface(plane,if(listOf(plane.x,plane.y,plane.z)[axis]>0) polygon else polygon.reversed())
    }
    internal fun clipPolygon(polygon:List<ExactPoint>,plane:ExactPlane):List<ExactPoint> {
        if(polygon.isEmpty()) return polygon
        val out=mutableListOf<ExactPoint>();var previous=polygon.last();var a=plane.at(previous)
        for(current in polygon) {
            val b=plane.at(current)
            if((a.signum()>0)!=(b.signum()>0)) out+=previous+(current-previous)*(a/(a-b))
            if(b.signum()<=0) out+=current
            previous=current;a=b
        }
        val distinct=out.distinct()
        if(distinct.size<3) return emptyList()
        val edge=distinct[1]-distinct[0]
        if(distinct.drop(2).none { p ->
            val b=p-distinct[0]
            (edge.y*b.z-edge.z*b.y).signum()!=0 || (edge.z*b.x-edge.x*b.z).signum()!=0 || (edge.x*b.y-edge.y*b.x).signum()!=0
        }) return emptyList()
        return distinct
    }
    internal fun subtractPolygon(polygon:List<ExactPoint>,planes:List<ExactPlane>):List<List<ExactPoint>> {
        var inside=polygon;val outside=mutableListOf<List<ExactPoint>>()
        for(plane in planes) {
            if(inside.isEmpty()) break
            if(inside.all { plane.signAt(it)==0 }) continue
            clipPolygon(inside,plane.opposite()).takeIf { it.size>=3 }?.let { outside+=it }
            inside=clipPolygon(inside,plane)
        }
        return outside
    }
}
