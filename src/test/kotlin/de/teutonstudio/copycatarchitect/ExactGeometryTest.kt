package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.*

class ExactGeometryTest {
    private fun wedge(extra:Long=0)=ExactVolume.convex(listOf(ExactPlane(0,3,-2,extra)))
    private fun source(volume:ExactVolume,group:String="body")=Part(RoofPartParameters.SOURCE,exact=volume,group=group)

    @Test fun `every preset repeats without seams across its full period in every horizontal orientation`() {
        for(slope in RoofSlope.presets) {
            fun column(index:Int):ExactVolume {
                val phase=Math.floorMod(slope.p*index,slope.q)
                val carry=Math.floorDiv(slope.p*index,slope.q)
                return (-1..slope.p).fold(ExactVolume()) { volume,slice ->
                    volume.union(ExactGeometry.roof(RoofPartParameters(slope.p,slope.q,phase,slice))
                        .translated(P(0,16*(slice+carry),16*index)))
                }
            }
            for(index in 0 until slope.q) {
                val a=column(index);val b=column(index+1)
                val seam=ExactNumber.of(16L*(index+1))
                val left=a.cells.flatMap { it.vertices() }.filter { it.z==seam }.toSet()
                val right=b.cells.flatMap { it.vertices() }.filter { it.z==seam }.toSet()
                assertTrue(left.isNotEmpty(),"Missing period boundary for ${slope.period}/$index")
                assertEquals(left,right,"Exact shared boundary for ${slope.period}/$index")
                for(turn in 0..3) {
                    val volume=a.union(b).rotate(RotationAxis.Y,turn)
                    val interfacePlane=ExactPlane(0,0,1,16L*(index+1)).rotate(RotationAxis.Y,turn)
                    assertTrue(volume.surfaces().none { it.plane==interfacePlane || it.plane==interfacePlane.opposite() },
                        "Internal seam must disappear for ${slope.period}/$index at $turn quarter turns")
                }
            }
            assertTrue(column(slope.q).equivalent(column(0).translated(P(0,16*slope.p,16*slope.q))))
        }
    }

    @Test fun `rational planes survive clipping rotation and serialization without rasterizing the surface`() {
        val part=source(wedge(1))
        val form=Geometry.validate(Form(id="exact",parts=listOf(part)))
        assertEquals(4,form.schema)
        val restored=Geometry.validate(Gson().fromJson(Gson().toJson(form),Form::class.java))
        assertEquals(form,restored)
        val sloped=ExactGeometry.part(part).surfaces().single { it.plane.y==3L }
        assertTrue(sloped.vertices.any { it.y.denominator.toString()=="3" })
        for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) {
            var rotated=part
            repeat(4) { q ->
                rotated=assertNotNull(PartRotations.rotate(rotated,axis,1))
                assertEquals(Solid.part(part).rotate(axis,q+1).key(),Solid.part(rotated).key())
            }
            assertTrue(ExactGeometry.part(rotated).equivalent(ExactGeometry.part(part)))
        }
    }

    @Test fun `difference and material priority preserve two regions inside the same voxel`() {
        val whole=ExactVolume.box(0,0,0,1,1,1)
        val lower=whole.intersect(wedge())
        val upper=whole.minus(lower)
        assertFalse(lower.isEmpty());assertFalse(upper.isEmpty())
        assertEquals(1,lower.solid().count());assertEquals(1,upper.solid().count())
        assertTrue(lower.intersect(upper).isEmpty())
        assertTrue(lower.union(upper).equivalent(whole))
        val groups=listOf(MaterialGroup(),MaterialGroup("roof","Roof","surface",1))
        val parts=Geometry.normalized(listOf(source(whole),source(lower,"roof")),groups)
        assertEquals(2,parts.size)
        val resolved=Geometry.resolve(parts,groups)
        assertTrue(resolved.exactOwned.getValue("body").equivalent(upper))
        assertTrue(resolved.exactOwned.getValue("roof").equivalent(lower))
        assertEquals(1,Extrusion.cost(parts,groups))
    }

    @Test fun `source graph booleans retain the exact slope and its leaf provenance`() {
        val graph=NodeGraph(nodes=listOf(
            GraphNode("full",part=Part("copycats:copycat_block")),
            GraphNode("roof",part=source(wedge(1))),
            GraphNode("difference",NodeKind.DIFFERENCE,listOf("full","roof")),
            GraphNode("result",NodeKind.OUTPUT,listOf("difference"))))
        val form=Geometry.validate(Form(id="cut",graph=graph))
        assertEquals(3,form.graph!!.schema)
        val part=form.parts.single()
        assertEquals("copycats:copycat_block",part.source)
        assertNotNull(part.exactClip)
        assertTrue(Geometry.exact(form.parts).equivalent(ExactVolume.box(0,0,0,16,16,16).minus(wedge(1))))
        val restored=Geometry.validate(Gson().fromJson(Gson().toJson(form),Form::class.java))
        assertTrue(Geometry.exact(restored.parts).equivalent(Geometry.exact(form.parts)))
    }

    @Test fun `grid masks clip exact source faces without replacing their rational slope`() {
        val mask=Solid.part(Part("copycats:copycat_slab")).mask()
        val part=source(wedge(1)).copy(cutMask=mask)
        val actual=ExactGeometry.part(part)
        assertTrue(actual.equivalent(wedge(1).intersect(ExactVolume.box(0,0,0,16,8,16))))
        assertTrue(actual.surfaces().any { it.plane.y==3L && it.plane.z== -2L })
        assertTrue(actual.surfaces().flatMap { it.vertices }.all { it.y.toDouble()<=8 })
    }

    @Test fun `union removes internal faces and equivalent decompositions compare visually equal`() {
        val left=ExactVolume.box(0,0,0,8,16,16)
        val right=ExactVolume.box(8,0,0,16,16,16)
        val whole=ExactVolume.box(0,0,0,16,16,16)
        val joined=left.union(right)
        assertTrue(joined.surfaces().none { it.plane.x!=0L && kotlin.math.abs(it.plane.d)==8L })
        val a=Geometry.resolve(listOf(source(joined)))
        val b=Geometry.resolve(listOf(source(whole)))
        assertTrue(a.sameVisual("minecraft:stone",emptyMap(),b,"minecraft:stone",emptyMap()))
    }

    @Test fun `third slope phase gives identical shared boundary coordinates`() {
        fun profile(phase:Int)=ExactGeometry.roof(RoofPartParameters(rise=1,run=3,phase=phase))
        val first=profile(1).surfaces().single { it.plane.y==3L }
        val second=profile(2).surfaces().single { it.plane.y==3L }
        val a=first.vertices.filter { it.z==ExactNumber.of(16) }.map { it.y }.toSet()
        val b=second.vertices.filter { it.z==ExactNumber.of(0) }.map { it.y }.toSet()
        assertEquals(a,b)
    }

    @Test fun `exact ray selection reaches the slope behind its conservative collision voxel`() {
        val volume=ExactVolume.convex(listOf(ExactPlane(0,3,-2,1)))
        val hit=assertNotNull(volume.rayEntry(doubleArrayOf(8.0,20.0,8.0),doubleArrayOf(0.0,-1.0,0.0)))
        assertEquals(20.0-17.0/3.0,hit,1e-9)
        assertNull(volume.rayEntry(doubleArrayOf(20.0,20.0,8.0),doubleArrayOf(0.0,-1.0,0.0)))
    }

    @Test fun `opaque side of a glass interface remains while hidden glass side is omitted`() {
        val groups=listOf(MaterialGroup(),MaterialGroup("glass","Glass","surface",1))
        val resolved=Geometry.resolve(listOf(source(ExactVolume.box(0,0,0,16,8,16)),source(ExactVolume.box(0,8,0,16,16,16),"glass")),groups)
        fun interfaceFaces(glass:Set<String>)=resolved.visibleSurfaces(glass).filter { it.surface.plane.let { p -> p.x==0L && p.z==0L && kotlin.math.abs(p.d)==8L } }
        val faces=interfaceFaces(setOf("glass"))
        assertEquals(1,faces.size)
        assertEquals("body",faces.single().group)
        assertEquals(1L,faces.single().surface.plane.y)
        assertTrue(interfaceFaces(emptySet()).isEmpty())
        assertTrue(interfaceFaces(setOf("glass","body")).isEmpty())
    }

    @Test fun `neighbor glass preserves the opaque boundary across a block seam`() {
        val cube=ExactVolume.box(0,0,0,16,16,16)
        val own=Geometry.resolve(listOf(source(cube)))
        val adjacent=cube.translated(P(16,0,0))
        val neighbor=ResolvedGeometry(Solid(),emptyMap(),adjacent,mapOf("glass" to adjacent))
        fun east(glass:Set<String>)=own.visibleSurfaces(neighbors=listOf(neighbor to glass)).filter { it.surface.plane==ExactPlane(1,0,0,16) }
        assertEquals(1,east(setOf("glass")).size)
        assertTrue(east(emptySet()).isEmpty())
        assertTrue(own.visibleSurfaces(setOf("body"),listOf(neighbor to setOf("glass"))).none { it.surface.plane==ExactPlane(1,0,0,16) })
    }

    @Test fun `volume algebra and conservative billing are rotation invariant without counting touching cells`() {
        val volume=wedge(1)
        assertTrue(volume.union(volume).equivalent(volume))
        assertTrue(volume.intersect(volume).equivalent(volume))
        assertTrue(volume.minus(volume).isEmpty())
        assertEquals(0,ExactVolume.convex(listOf(ExactPlane(1,0,0,0))).solid().count())
        val part=source(volume)
        val cost=Extrusion.cost(listOf(part))
        for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) for(q in 0..3) {
            val rotated=assertNotNull(PartRotations.rotate(part,axis,q))
            assertEquals(cost,Extrusion.cost(listOf(rotated)))
        }
    }

    @Test fun `exact polygon clipping matches independent triple plane intersections`() {
        val fixtures=listOf(
            ExactVolume.box(0,0,0,16,16,16),wedge(1),
            wedge(1).rotate(RotationAxis.Y,3).translatedY(ExactNumber.of(BigInteger.ONE,BigInteger.valueOf(3))),
            ExactVolume.convex(listOf(ExactPlane(-3,4,0,13),ExactPlane(0,4,-3,7))),
            ExactVolume.convex(listOf(ExactPlane(-3,5,2,31),ExactPlane(7,-2,3,11))),
            ExactVolume.convex(listOf(ExactPlane(-2,0,0,-3),ExactPlane(0,-3,0,-2),ExactPlane(0,0,-5,-7),ExactPlane(2,3,4,47))),
            ExactVolume.box(0,0,0,16,16,16).minus(wedge(1)))
        fun determinant(a:ExactPlane,b:ExactPlane,c:ExactPlane):BigInteger {
            fun n(v:Long)=BigInteger.valueOf(v)
            return n(a.x)*(n(b.y)*n(c.z)-n(b.z)*n(c.y))-n(a.y)*(n(b.x)*n(c.z)-n(b.z)*n(c.x))+n(a.z)*(n(b.x)*n(c.y)-n(b.y)*n(c.x))
        }
        for(volume in fixtures) for(cell in volume.cells) {
            val planes=cell.planes.distinct();val expected=linkedSetOf<ExactPoint>()
            for(i in planes.indices) for(j in i+1 until planes.size) for(k in j+1 until planes.size) {
                val a=planes[i];val b=planes[j];val c=planes[k];val d=determinant(a,b,c)
                if(d.signum()==0) continue
                val point=ExactPoint(
                    ExactNumber.of(determinant(a.copy(x=a.d),b.copy(x=b.d),c.copy(x=c.d)),d),
                    ExactNumber.of(determinant(a.copy(y=a.d),b.copy(y=b.d),c.copy(y=c.d)),d),
                    ExactNumber.of(determinant(a.copy(z=a.d),b.copy(z=b.d),c.copy(z=c.d)),d))
                if(planes.all { it.at(point).signum()<=0 }) expected+=point
            }
            assertEquals(expected,cell.vertices().toSet(),"Clipped rational polyhedron differs from independently intersected halfspaces: $planes")
        }
    }

    @Test fun `an unattached roof source does not replace a voxel branch with the exact compiler`() {
        val graph=NodeGraph(nodes=listOf(
            GraphNode("full",part=Part("copycats:copycat_block")),GraphNode("slab",part=Part("copycats:copycat_slab")),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf("full","slab")),GraphNode("unused",part=source(wedge(1))),
            GraphNode("result",NodeKind.OUTPUT,listOf("cut"))))
        val form=Geometry.validate(Form(graph=graph))
        assertEquals(4,form.schema);assertEquals(3,form.graph!!.schema)
        assertNotNull(form.parts.single().cutMask.takeIf { it.isNotEmpty() })
        assertNull(form.parts.single().exactClip)
    }
}
