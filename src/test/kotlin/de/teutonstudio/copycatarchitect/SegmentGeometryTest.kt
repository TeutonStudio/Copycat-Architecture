package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SegmentGeometryTest {
    @Test fun `regions partition volumes without duplicate sockets when dimensions collapse`() {
        for((dimensions,count) in listOf(P(3,3,3) to 27,P(1,3,1) to 3,P(1,1,1) to 1,P(2,2,2) to 8,P(5,4,3) to 27)) {
            val regions=SegmentRegions.of(dimensions.x,dimensions.y,dimensions.z)
            assertEquals(count,regions.size)
            assertEquals(count,regions.map { it.key }.distinct().size)
            val cells=regions.flatMap { it.cells }
            assertEquals(dimensions.x*dimensions.y*dimensions.z,cells.size)
            assertEquals(cells.size,cells.distinct().size)
            assertTrue(cells.all { it.x in 0 until dimensions.x && it.y in 0 until dimensions.y && it.z in 0 until dimensions.z })
        }
        assertEquals(listOf("region:xb_y0_zb","region:xb_yi_zb","region:xb_y1_zb"),SegmentRegions.of(1,3,1).map { it.key })
        assertEquals(setOf(P(1,1,1),P(2,1,1),P(3,1,1),P(1,2,1),P(2,2,1),P(3,2,1)),SegmentRegions.of(5,4,3).single { it.key=="region:xi_yi_zi" }.cells)
        assertFailsWith<IllegalArgumentException> { SegmentRegions.of(0,1,1) }
    }

    @Test fun `union rotates all three dimensions and retains sparse and air bounds`() {
        val source=FixedSegment(2,3,listOf(
            SegmentCell(Cell(P(1,2,3),form="corner@4",role="trim",turn=1),"frame"),
            SegmentCell(Cell(P(0,0,0),block="minecraft:air",empty=true))
        ),depth=4)
        val expected=listOf(
            Triple(RotationAxis.X,P(2,4,3),P(1,0,2)),
            Triple(RotationAxis.Y,P(4,3,2),P(0,2,1)),
            Triple(RotationAxis.Z,P(3,2,4),P(0,1,3))
        )
        for((axis,dimensions,position) in expected) {
            val result=SegmentGeometry.union(listOf(source to SegmentTransform(P(-5,7,-2),axis,1)))
            assertEquals(dimensions,P(result.length,result.height,result.depth))
            val cell=result.cells.single { !it.cell.empty }
            assertEquals(position,cell.cell.pos)
            assertEquals("corner@4",cell.cell.form)
            assertEquals("trim",cell.cell.role)
            assertEquals("frame",cell.overrideRole)
            assertEquals(1,result.cells.count { it.cell.empty })
        }
        val sparse=SegmentGeometry.union(listOf(FixedSegment(2,3,emptyList(),depth=4) to SegmentTransform()))
        assertEquals(P(2,3,4),P(sparse.length,sparse.height,sparse.depth))
        assertTrue(sparse.cells.isEmpty())
    }

    @Test fun `translated unions normalize negative offsets and reject conflicting cells`() {
        val stone=FixedSegment(1,1,listOf(SegmentCell(Cell(block="minecraft:stone"))))
        val glass=FixedSegment(1,1,listOf(SegmentCell(Cell(block="minecraft:glass"))))
        val result=SegmentGeometry.union(listOf(stone to SegmentTransform(P(-2,-1,-3)),glass to SegmentTransform(P(2,1,3))))
        assertEquals(P(5,3,7),P(result.length,result.height,result.depth))
        assertEquals(setOf(P(),P(4,2,6)),result.cells.map { it.cell.pos }.toSet())
        assertEquals(1,SegmentGeometry.union(listOf(stone to SegmentTransform(),stone to SegmentTransform())).cells.size)
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.union(listOf(stone to SegmentTransform(),glass to SegmentTransform())) }
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.union(emptyList()) }
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.union(listOf(stone to SegmentTransform(),stone to SegmentTransform(P(128,0,0)))) }
        assertFailsWith<IllegalArgumentException> {
            SegmentGeometry.union(listOf(FixedSegment(128,64,emptyList(),depth=32) to SegmentTransform(),stone to SegmentTransform(P(0,0,32))))
        }
    }

    @Test fun `air segments contribute their complete transformed volume without filling gaps`() {
        val source=FixedSegment(2,3,emptyList(),air=true,depth=4)
        val rotated=SegmentGeometry.union(listOf(source to SegmentTransform(axis=RotationAxis.X,quarterTurns=-1)))
        assertEquals(P(2,4,3),P(rotated.length,rotated.height,rotated.depth))
        assertEquals(24,rotated.cells.size)
        assertTrue(rotated.air)
        assertTrue(rotated.cells.all { it.cell.empty && it.cell.block=="minecraft:air" })
        val spaced=SegmentGeometry.union(listOf(FixedSegment(1,1,emptyList(),true) to SegmentTransform(),FixedSegment(1,1,emptyList(),true) to SegmentTransform(P(2,0,0))))
        assertEquals(3,spaced.length)
        assertEquals(setOf(P(),P(2,0,0)),spaced.cells.map { it.cell.pos }.toSet())
        assertFalse(spaced.air)
    }

    @Test fun `overlapping copycats retain ordered contributions while native conflicts fail`() {
        val first=FixedSegment(1,1,listOf(SegmentCell(Cell(form="lower@1",role="wall"))))
        val second=FixedSegment(1,1,listOf(SegmentCell(Cell(form="upper@1",role="frame"))))
        val united=SegmentGeometry.union(listOf(first to SegmentTransform(),second to SegmentTransform(),first to SegmentTransform()))
        assertEquals(listOf("lower@1","upper@1"),united.cells.map { it.cell.form })
        assertTrue(united.cells.all { it.cell.pos==P() })
        val air=FixedSegment(1,1,emptyList(),true)
        val native=FixedSegment(1,1,listOf(SegmentCell(Cell(block="minecraft:stone"))))
        for(items in listOf(listOf(first,air,second,air),listOf(air,first,air,second))) {
            assertEquals(united,SegmentGeometry.union(items.map { it to SegmentTransform() }))
        }
        assertFailsWith<IllegalArgumentException> { SegmentGeometry.union(listOf(first to SegmentTransform(),native to SegmentTransform())) }
    }

    @Test fun `copycats replace transformed air only where solid contributions exist`() {
        val source=FixedSegment(2,1,listOf(SegmentCell(Cell(form="panel@1")),SegmentCell(Cell(P(1,0,0),empty=true))))
        val air=FixedSegment(1,2,emptyList(),air=true,depth=2)
        val transform=SegmentTransform(P(-1,0,0),RotationAxis.Z,1)
        for(items in listOf(listOf(source to transform,air to SegmentTransform()),listOf(air to SegmentTransform(),source to transform))) {
            val united=SegmentGeometry.union(items)
            assertEquals(P(2,2,2),P(united.length,united.height,united.depth))
            assertEquals(listOf(P()),united.cells.filter { !it.cell.empty }.map { it.cell.pos })
            assertEquals(setOf(P(0,1,0),P(1,0,0),P(1,1,0),P(1,0,1),P(1,1,1)),united.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
            assertFalse(united.air)
        }
    }

    @Test fun `nested rotations preserve source voxel geometry and material groups in order`() {
        val part=Part("create:copycat_panel",turn=1,group="glass",recipeSource="panel")
        val source=FixedSegment(1,1,listOf(SegmentCell(Cell(turn=1))))
        val x=SegmentGeometry.union(listOf(source to SegmentTransform(axis=RotationAxis.X,quarterTurns=1)))
        val z=SegmentGeometry.union(listOf(x to SegmentTransform(axis=RotationAxis.Z,quarterTurns=3)))
        val cell=z.cells.single().cell
        val result=SegmentGeometry.parts(cell,listOf(part),worldTurn=1).single()
        val expected=Solid.part(part).rotate(1).rotate(RotationAxis.X,1).rotate(RotationAxis.Z,3).rotate(1)
        assertEquals(expected.key(),Solid.part(result).key())
        assertEquals("glass",result.group)
        assertEquals("panel",result.recipeSource)
        assertEquals(listOf(SegmentRotation(RotationAxis.X,1),SegmentRotation(RotationAxis.Z,3)),cell.rotations)
    }
}
