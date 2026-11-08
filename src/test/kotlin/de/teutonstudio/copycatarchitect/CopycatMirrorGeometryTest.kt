package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CopycatMirrorGeometryTest {
    private val planes=CopycatMirrorPlane.entries
    private val groups=listOf(MaterialGroup(),MaterialGroup("glass","Glass","glass",1))
    private fun voxels(solid:Solid)=buildSet { solid.voxels { x,y,z -> add(P(x,y,z)) } }
    private fun normal(plane:CopycatMirrorPlane)=when(plane) {
        CopycatMirrorPlane.X->P(1,0,0);CopycatMirrorPlane.Y->P(0,1,0);CopycatMirrorPlane.Z->P(0,0,1)
        CopycatMirrorPlane.XY->P(1,1,0);CopycatMirrorPlane.XY_NEGATIVE->P(1,-1,0)
        CopycatMirrorPlane.XZ->P(1,0,1);CopycatMirrorPlane.XZ_NEGATIVE->P(1,0,-1)
        CopycatMirrorPlane.YZ->P(0,1,1);CopycatMirrorPlane.YZ_NEGATIVE->P(0,1,-1)
        CopycatMirrorPlane.BODY_PPP->P(1,1,1);CopycatMirrorPlane.BODY_PPN->P(1,1,-1)
        CopycatMirrorPlane.BODY_PNP->P(1,-1,1);CopycatMirrorPlane.BODY_PNN->P(1,-1,-1)
    }
    private fun reflected(point:ExactPoint,plane:CopycatMirrorPlane):ExactPoint {
        val n=normal(plane);val eight=ExactNumber.of(8)
        val signedDistance=(point.x-eight)*n.x.toLong()+(point.y-eight)*n.y.toLong()+(point.z-eight)*n.z.toLong()
        val factor=signedDistance*2L/ExactNumber.of((n.x*n.x+n.y*n.y+n.z*n.z).toLong())
        return ExactPoint(point.x-factor*n.x.toLong(),point.y-factor*n.y.toLong(),point.z-factor*n.z.toLong())
    }
    private fun gridReflection(p:P,plane:CopycatMirrorPlane)=when(plane) {
        CopycatMirrorPlane.X->P(15-p.x,p.y,p.z)
        CopycatMirrorPlane.Y->P(p.x,15-p.y,p.z)
        CopycatMirrorPlane.Z->P(p.x,p.y,15-p.z)
        CopycatMirrorPlane.XY->P(15-p.y,15-p.x,p.z)
        CopycatMirrorPlane.XY_NEGATIVE->P(p.y,p.x,p.z)
        CopycatMirrorPlane.XZ->P(15-p.z,p.y,15-p.x)
        CopycatMirrorPlane.XZ_NEGATIVE->P(p.z,p.y,p.x)
        CopycatMirrorPlane.YZ->P(p.x,15-p.z,15-p.y)
        CopycatMirrorPlane.YZ_NEGATIVE->P(p.x,p.z,p.y)
        else->error("Not a voxel-grid mirror")
    }

    @Test fun `axis and face normals mirror actual voxels and preserve receipts and costs`() {
        val part=Part("copycats:copycat_corner_slice",size=2,offset=P(-1,2,-3),group="glass",recipeSource="corner")
        val original=Solid.part(part)
        for(plane in planes.filterNot { it.requiresExact }) {
            val expected=voxels(original).map { gridReflection(it,plane) }.toSet()
            val result=PartRotations.mirror(part,plane)
            assertEquals(expected,voxels(Solid.part(result)),plane.name)
            assertEquals(expected,voxels(original.reflected(plane)))
            assertEquals(part.source,result.source);assertEquals(part.size,result.size)
            assertEquals(part.group,result.group);assertEquals(part.recipeSource,result.recipeSource)
            assertEquals(Extrusion.cost(listOf(part),groups),Extrusion.cost(listOf(result),groups))
            assertEquals(voxels(original),voxels(Solid.part(PartRotations.mirror(result,plane))))
        }
    }

    @Test fun `every plane matches independent exact Householder vertices and is an involution`() {
        val volume=ExactVolume.box(6,5,6,10,11,9).intersect(ExactVolume.convex(listOf(ExactPlane(2,3,-1,29))))
        val part=Part(RoofPartParameters.SOURCE,exact=volume,group="glass",recipeSource="leaf")
        val vertices=volume.cells.flatMap { it.vertices() }.toSet()
        assertTrue(vertices.isNotEmpty())
        for(plane in planes) {
            assertEquals(normal(plane),plane.normal)
            val result=PartRotations.mirror(part,plane)
            val actual=ExactGeometry.part(result)
            assertEquals(vertices.map { reflected(it,plane) }.toSet(),actual.cells.flatMap { it.vertices() }.toSet(),plane.name)
            assertTrue(ExactGeometry.part(PartRotations.mirror(result,plane)).equivalent(volume),plane.name)
            assertEquals(part.source,result.source);assertEquals(part.group,result.group);assertEquals(part.recipeSource,result.recipeSource)
            if(plane.requiresExact) assertTrue(actual.cells.flatMap { it.vertices() }.any { p ->
                listOf(p.x,p.y,p.z).any { it.denominator.toString()!="1" }
            },"Body reflection must retain rational coordinates: ${plane.name}")
        }
    }

    @Test fun `body reflection accepts centered clipped geometry and rejects visible volume outside the block`() {
        val clipped=Part("copycats:copycat_block",exactClip=ExactVolume.box(6,6,6,10,10,10),recipeSource="clipped")
        for(plane in planes.filter { it.requiresExact }) {
            val mirrored=PartRotations.mirror(clipped,plane)
            val expected=ExactGeometry.part(clipped).reflected(plane)
            assertTrue(ExactGeometry.part(mirrored).equivalent(expected))
            assertTrue(ExactGeometry.part(PartRotations.mirror(mirrored,plane)).equivalent(ExactGeometry.part(clipped)))
            assertEquals(clipped.source,mirrored.source);assertEquals(clipped.recipeSource,mirrored.recipeSource)
            assertNotNull(mirrored.exact)
            val failure=assertFailsWith<IllegalArgumentException> { PartRotations.mirror(Part("copycats:copycat_block"),plane) }
            assertTrue(failure.message.orEmpty().contains("leaves block bounds"))
            assertFailsWith<IllegalArgumentException> { Solid.part(Part("copycats:copycat_block")).reflected(plane) }
        }
    }

    @Test fun `mirrored clipping stays separate from the full source whenever that source fits`() {
        val source=Part("copycats:copycat_byte",offset=P(4,4,4),group="glass",recipeSource="byte")
        val clipped=source.copy(exactClip=ExactVolume.box(6,6,6,10,10,10))
        for(plane in planes) {
            val result=PartRotations.mirror(clipped,plane)
            val originalBounds=ExactGeometry.part(result.copy(cutMask="",exactClip=null))
            assertTrue(originalBounds.equivalent(ExactGeometry.part(source).reflected(plane)),plane.name)
            assertTrue(ExactGeometry.part(result).equivalent(ExactGeometry.part(clipped).reflected(plane)),plane.name)
        }
        val native=Part("copycats:copycat_vertical_half_layer",size=3,recipeSource="restricted")
        val mask=Solid.part(native).intersect(Solid.part(Part("copycats:copycat_slab"))).mask()
        val result=PartRotations.mirror(native.copy(cutMask=mask),CopycatMirrorPlane.X)
        assertNotNull(result.exact)
        assertTrue(ExactGeometry.part(result.copy(cutMask="",exactClip=null)).equivalent(ExactGeometry.part(native).reflected(CopycatMirrorPlane.X)))
    }

    @Test fun `all mirror planes retain exact material ownership and source payment metadata`() {
        val whole=ExactVolume.box(6,6,6,10,10,10)
        val overlay=whole.intersect(ExactVolume.convex(listOf(ExactPlane(2,3,-1,31))))
        val parts=listOf(Part(RoofPartParameters.SOURCE,exact=whole,recipeSource="base"),
            Part(RoofPartParameters.SOURCE,exact=overlay,group="glass",recipeSource="overlay"))
        val original=Geometry.resolve(parts,groups)
        val placement=Placement(parts=parts,groups=groups,material="minecraft:stone",surfaces=mapOf("glass" to "minecraft:glass"))
        for(plane in planes) {
            val mirrored=parts.map { PartRotations.mirror(it,plane) }
            val actual=Geometry.resolve(mirrored,groups)
            groups.forEach { group -> assertTrue(actual.exactOwned.getValue(group.id).equivalent(original.exactOwned.getValue(group.id).reflected(plane)),"${plane.name}/${group.id}") }
            assertEquals(parts.map { it.source },mirrored.map { it.source })
            assertEquals(parts.map { it.recipeSource },mirrored.map { it.recipeSource })
            assertEquals(placement.surfaceItems(),placement.copy(parts=mirrored).surfaceItems())
            if(!plane.requiresExact) assertEquals(Extrusion.cost(parts,groups),Extrusion.cost(mirrored,groups))
        }
    }

    @Test fun `ordered Copycat mirrors compose with source turns legacy segment mirrors and mounting`() {
        val source=Part(RoofPartParameters.SOURCE,exact=ExactVolume.box(6,6,6,9,10,8),group="glass",recipeSource="ordered")
        val cell=Cell(turn=1,rotations=listOf(
            SegmentRotation(mirror=CopycatMirror(CopycatMirrorPlane.XY)),SegmentRotation(RotationAxis.Z,1),
            SegmentRotation(mirror=CopycatMirror(CopycatMirrorPlane.BODY_PNN)),SegmentRotation(RotationAxis.X,0,true),
            SegmentRotation(RotationAxis.Y,1)))
        val actual=SegmentGeometry.parts(cell,listOf(source),worldTurn=3).single()
        val expected=ExactGeometry.part(source).rotate(RotationAxis.Y,1).reflected(CopycatMirrorPlane.XY)
            .rotate(RotationAxis.Z,1).reflected(CopycatMirrorPlane.BODY_PNN).reflected(RotationAxis.X)
            .rotate(RotationAxis.Y,1).rotate(RotationAxis.Y,3)
        assertTrue(ExactGeometry.part(actual).equivalent(expected))
        assertEquals(source.group,actual.group);assertEquals(source.recipeSource,actual.recipeSource)
        assertEquals(cell,Gson().fromJson(Gson().toJson(cell),Cell::class.java))
    }

    @Test fun `position only segment mirroring preserves every source metadata field and ordered operation`() {
        val cell=Cell(P(1,2,3),form="custom@2",role="trim",turn=1,flexible=true,
            rotations=listOf(SegmentRotation(RotationAxis.Z,1),SegmentRotation(mirror=CopycatMirror(CopycatMirrorPlane.XZ))))
        val entry=SegmentCell(cell,"frame")
        val segment=FixedSegment(2,3,listOf(entry,SegmentCell(Cell(P(),block="minecraft:air",empty=true))),depth=4)
        val expected=listOf(RotationAxis.X to P(0,2,3),RotationAxis.Y to P(1,0,3),RotationAxis.Z to P(1,2,0))
        for((axis,position) in expected) {
            val result=SegmentGeometry.mirror(segment,axis,positionsOnly=true)
            assertEquals(entry.copy(cell=cell.copy(pos=position)),result.cells.first())
            assertEquals(segment,SegmentGeometry.mirror(result,axis,positionsOnly=true))
            val full=SegmentGeometry.mirror(segment,axis)
            assertEquals(cell.rotations+SegmentRotation(axis,0,true),full.cells.first().cell.rotations)
        }
    }

    @Test fun `mirror configuration defaults and all selected diagonal normals survive JSON`() {
        assertEquals(CopycatMirrorPlane.X,CopycatMirror().plane)
        for(plane in planes) {
            val config=CopycatMirror(plane)
            assertEquals(config,Gson().fromJson(Gson().toJson(config),CopycatMirror::class.java))
        }
        val legacy=Gson().fromJson("""{"axis":"X","quarterTurns":1}""",SegmentRotation::class.java)
        assertNull(legacy.mirror);assertFalse(legacy.reflected)
        assertEquals(SegmentRotation(RotationAxis.X,1),legacy)
    }

    @Test fun `face reflections reduce their derived scale before applying coefficient limits`() {
        val original=ExactPlane(750_000_001,1,-1,1).normalized()
        assertEquals(original,original.reflected(CopycatMirrorPlane.YZ))
        assertEquals(ExactPlane(750_000_001,-1,1,1),original.reflected(CopycatMirrorPlane.YZ_NEGATIVE))
    }
}
