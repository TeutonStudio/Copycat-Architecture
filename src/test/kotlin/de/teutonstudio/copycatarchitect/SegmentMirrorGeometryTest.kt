package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SegmentMirrorGeometryTest {
    private val axes=listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)
    private val groups=listOf(MaterialGroup(),MaterialGroup("glass","Glass","glass",1))

    @Test fun `mirrors preserve sparse bounds explicit air roles and source metadata on every axis`() {
        val occupied=SegmentCell(Cell(P(1,2,3),form="corner@4",role="trim",turn=1,flexible=true),"frame")
        val air=SegmentCell(Cell(P(),block="minecraft:air",empty=true),"opening")
        val source=FixedSegment(2,3,listOf(occupied,air),depth=4)
        val positions=listOf(P(0,2,3),P(1,0,3),P(1,2,0))
        val airPositions=listOf(P(1,0,0),P(0,2,0),P(0,0,3))
        for((index,axis) in axes.withIndex()) {
            val mirrored=SegmentGeometry.mirror(source,axis)
            assertEquals(P(2,3,4),P(mirrored.length,mirrored.height,mirrored.depth))
            assertEquals(2,mirrored.cells.size)
            assertEquals(occupied.copy(cell=occupied.cell.copy(pos=positions[index],rotations=listOf(SegmentRotation(axis,0,true)))),mirrored.cells.first())
            assertEquals(airPositions[index],mirrored.cells.last().cell.pos)
            assertTrue(mirrored.cells.last().cell.empty)
            assertEquals("minecraft:air",mirrored.cells.last().cell.block)
            assertEquals("opening",mirrored.cells.last().overrideRole)
            val twice=SegmentGeometry.mirror(mirrored,axis)
            assertEquals(source.cells.map { it.cell.pos },twice.cells.map { it.cell.pos })
            assertFalse(mirrored.air)
            val empty=FixedSegment(2,3,emptyList(),depth=4)
            assertEquals(empty,SegmentGeometry.mirror(empty,axis))
            val completeAir=empty.copy(air=true)
            assertEquals(completeAir,SegmentGeometry.mirror(completeAir,axis))
        }
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.mirror(source,RotationAxis.NORMAL) }
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.mirror(source.copy(length=1),RotationAxis.X) }
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.mirror(source.copy(length=129),RotationAxis.X) }
    }

    @Test fun `source mirrors prefer native orientations and preserve materials provenance and volume cost`() {
        for(shape in BaseShapes.all.filter { it.source!=RoofPartParameters.SOURCE }) {
            val original=shape.part().copy(recipeSource="source",size=minOf(3,Adapters.get(shape.source).max))
            for(axis in axes) {
                val mirrored=PartRotations.mirror(original,axis)
                assertEquals(Solid.part(original).reflected(axis).key(),Solid.part(mirrored).key(),"${shape.id}/$axis")
                assertEquals(original.source,mirrored.source)
                assertEquals(original.size,mirrored.size)
                assertEquals(original.discCut,mirrored.discCut)
                assertEquals(original.group,mirrored.group)
                assertEquals(original.recipeSource,mirrored.recipeSource)
                if(Adapters.get(shape.source).tiltable) assertNull(mirrored.exact,"Available native mirror should remain native: ${shape.id}/$axis")
                assertEquals(Extrusion.cost(listOf(original)),Extrusion.cost(listOf(mirrored)))
                assertEquals(Solid.part(original).key(),Solid.part(PartRotations.mirror(mirrored,axis)).key())
            }
        }
    }

    @Test fun `mirrors transform clipped masks and displaced pane fractions without replacing their source`() {
        val original=Part(BaseShapes.DISC_SOURCE,discCut=2,offset=P(1,1,1),group="glass",recipeSource="pane")
        val clipped=Solid.part(original).intersect(Solid.part(Part("copycats:copycat_byte",top=true)))
        val part=original.copy(cutMask=clipped.mask())
        assertTrue(clipped.count() in 1 until Solid.part(original).count())
        for(axis in axes) {
            val mirrored=PartRotations.mirror(part,axis)
            assertEquals(Solid.part(part).reflected(axis).key(),Solid.part(mirrored).key())
            assertEquals(part.source,mirrored.source);assertEquals(part.discCut,mirrored.discCut)
            assertEquals(part.group,mirrored.group);assertEquals(part.recipeSource,mirrored.recipeSource)
            val twice=PartRotations.mirror(mirrored,axis)
            assertEquals(Solid.part(part).key(),Solid.part(twice).key())
            assertEquals(part.offset,twice.offset)
        }
    }

    @Test fun `exact rational faces and clipping reflect by coordinates on every axis and close after two mirrors`() {
        val part=Part(RoofPartParameters.SOURCE,roof=RoofPartParameters(rise=2,run=3,phase=1,thickness=3),
            exactClip=ExactVolume.box(1,0,2,15,16,14),group="glass",recipeSource="roof")
        val original=ExactGeometry.part(part)
        assertTrue(original.cells.flatMap { it.vertices() }.any { p -> listOf(p.x,p.y,p.z).any { it.denominator.toString()!="1" } })
        for(axis in axes) {
            val expectedVertices=original.cells.flatMap { it.vertices() }.map { p ->
                ExactPoint(if(axis==RotationAxis.X) ExactNumber.of(16)-p.x else p.x,
                    if(axis==RotationAxis.Y) ExactNumber.of(16)-p.y else p.y,
                    if(axis==RotationAxis.Z) ExactNumber.of(16)-p.z else p.z)
            }.toSet()
            val mirrored=PartRotations.mirror(part,axis)
            val actual=ExactGeometry.part(mirrored)
            assertEquals(expectedVertices,actual.cells.flatMap { it.vertices() }.toSet())
            assertTrue(actual.equivalent(original.reflected(axis)))
            assertTrue(ExactGeometry.part(PartRotations.mirror(mirrored,axis)).equivalent(original))
            assertEquals(part.roof,mirrored.roof);assertEquals(part.recipeSource,mirrored.recipeSource)
            assertEquals(part.group,mirrored.group);assertEquals(part.source,mirrored.source)
            assertEquals(Extrusion.cost(listOf(part),groups),Extrusion.cost(listOf(mirrored),groups))
        }
        assertFailsWith<IllegalArgumentException> { original.reflected(RotationAxis.NORMAL) }
    }

    @Test fun `reflection retains separately owned exact material regions and their paid source receipts`() {
        val whole=ExactVolume.box(0,0,0,16,16,16)
        val lower=ExactVolume.convex(listOf(ExactPlane(2,3,5,47)))
        val parts=listOf(
            Part(RoofPartParameters.SOURCE,exact=whole,recipeSource="solid"),
            Part(RoofPartParameters.SOURCE,exact=lower,group="glass",recipeSource="overlay"))
        val placement=Placement(parts=parts,groups=groups,material="minecraft:stone",surfaces=mapOf("glass" to "minecraft:glass"))
        val resolved=Geometry.resolve(parts,groups)
        for(axis in axes) {
            val mirrored=SegmentGeometry.parts(Cell(rotations=listOf(SegmentRotation(axis,0,true))),parts)
            val actual=Geometry.resolve(mirrored,groups)
            groups.forEach { group -> assertTrue(actual.exactOwned.getValue(group.id).equivalent(resolved.exactOwned.getValue(group.id).reflected(axis))) }
            assertEquals(parts.map { it.source },mirrored.map { it.source })
            assertEquals(parts.map { it.recipeSource },mirrored.map { it.recipeSource })
            assertEquals(placement.surfaceItems(),placement.copy(parts=mirrored).surfaceItems())
            assertEquals(Extrusion.cost(parts,groups),Extrusion.cost(mirrored,groups))
        }
    }

    @Test fun `nested mirrors retain their order with source rotations compositions and wall mounting`() {
        val part=Part("create:copycat_panel",turn=1,group="glass",recipeSource="panel")
        var segment=FixedSegment(2,3,listOf(SegmentCell(Cell(P(1,2,3),turn=1))),depth=4)
        segment=SegmentGeometry.union(listOf(segment to SegmentTransform(axis=RotationAxis.Y,quarterTurns=1)))
        segment=SegmentGeometry.mirror(segment,RotationAxis.X)
        segment=SegmentGeometry.union(listOf(segment to SegmentTransform(axis=RotationAxis.Z,quarterTurns=3)))
        segment=SegmentGeometry.mirror(segment,RotationAxis.Y)
        segment=SegmentGeometry.union(listOf(segment to SegmentTransform(axis=RotationAxis.Y,quarterTurns=1)))
        val cell=segment.cells.single().cell
        val actual=SegmentGeometry.parts(cell,listOf(part),worldTurn=2).single()
        val expected=Solid.part(part).rotate(1).rotate(RotationAxis.Y,1).reflected(RotationAxis.X)
            .rotate(RotationAxis.Z,3).reflected(RotationAxis.Y).rotate(RotationAxis.Y,1).rotate(2)
        assertEquals(expected.key(),Solid.part(actual).key())
        assertEquals(listOf(SegmentRotation(RotationAxis.X,0,true),SegmentRotation(RotationAxis.Z,3),
            SegmentRotation(RotationAxis.Y,0,true),SegmentRotation(RotationAxis.Y,1)),cell.rotations)
        assertEquals("glass",actual.group);assertEquals("panel",actual.recipeSource)
    }

    @Test fun `exact fallback preserves rational geometry for a restricted source and later rotations`() {
        val source=Part("copycats:copycat_slice",size=2,exact=ExactVolume.convex(listOf(ExactPlane(2,3,5,31))),group="glass",recipeSource="leaf")
        val mirrored=PartRotations.mirror(source,RotationAxis.X)
        assertNotNull(mirrored.exact)
        assertTrue(ExactGeometry.part(mirrored).equivalent(ExactGeometry.part(source).reflected(RotationAxis.X)))
        val rotated=assertNotNull(PartRotations.rotate(mirrored,RotationAxis.X,1))
        assertTrue(ExactGeometry.part(rotated).equivalent(ExactGeometry.part(source).reflected(RotationAxis.X).rotate(RotationAxis.X,1)))
        assertEquals(source.source,rotated.source);assertEquals(source.size,rotated.size)
        assertEquals(source.group,rotated.group);assertEquals(source.recipeSource,rotated.recipeSource)
        assertTrue(ExactGeometry.part(PartRotations.mirror(mirrored,RotationAxis.X)).equivalent(ExactGeometry.part(source)))
    }

    @Test fun `ordered reflection operations persist while legacy rotations retain their meaning`() {
        val old=Gson().fromJson("""{"axis":"X","quarterTurns":1}""",SegmentRotation::class.java)
        assertEquals(SegmentRotation(RotationAxis.X,1),old)
        val cell=Cell(rotations=listOf(SegmentRotation(RotationAxis.X,0,true),old,SegmentRotation(RotationAxis.Z,0,true)))
        assertEquals(cell,Gson().fromJson(Gson().toJson(cell),Cell::class.java))
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.parts(Cell(rotations=listOf(SegmentRotation(RotationAxis.X,1,true))),listOf(Part())) }
    }
}
