package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import kotlin.test.*

class CopycatArrangementTest {
    private val a=FixedSegment(1,1,listOf(SegmentCell(Cell(P(),block="minecraft:stone"))))
    private val b=FixedSegment(1,1,listOf(SegmentCell(Cell(P(),block="minecraft:glass"))))
    @Test fun `arrangement is transactional and preserves the hidden axis in every plane`() {
        val n=BuildingNode("wall",BuildingKind.SEGMENT,inputs=listOf("a","b"),length=5,height=4,depth=3)
        for(view in SegmentView.entries) {
            val draft=SegmentArrangement(listOf(a,b),n)
            val old=draft.transforms[1]
            draft.move(1,old,view,1,-1)
            assertEquals(old.offset+view.displacement(1,-1),draft.transforms[1].offset)
            assertEquals(null,n.copycatArrangement)
            val committed=draft.apply(n)
            assertTrue(committed.copycatArrangement==true)
            assertEquals(committed,Json.decode<BuildingNode>(Json.encode(committed)))
            assertEquals(setOf("minecraft:stone","minecraft:glass"),draft.result().cells.map { it.cell.block }.toSet())
        }
    }
    @Test fun `explicit positions affect geometry while keeping declared dimensions and source materials`() {
        val n=BuildingNode("wall",BuildingKind.SEGMENT,inputs=listOf("a","b"),length=4,height=3,depth=2,copycatArrangement=true,offsets=listOf(P(1,0,0),P(3,2,1)))
        val result=CopycatArrangement.compose(listOf(a,b),n)
        assertEquals(listOf(P(1,0,0),P(3,2,1)),result.cells.map { it.cell.pos })
        assertEquals(4,result.length);assertEquals(3,result.height);assertEquals(2,result.depth)
        assertFails { CopycatArrangement.compose(listOf(a,b),n.copy(offsets=listOf(P(),P(4,0,0)))) }
        assertFails { CopycatArrangement.compose(listOf(a,b),n.copy(offsets=listOf(P(),P()))) }
    }
    @Test fun `explicit air clears only its own cell rather than the entire declared segment`() {
        val air=FixedSegment(1,1,listOf(SegmentCell(Cell(P(),block="minecraft:air",empty=true))))
        val n=BuildingNode("wall",BuildingKind.SEGMENT,inputs=listOf("air"),length=8,height=4,depth=2,copycatArrangement=true,offsets=listOf(P(2,1,1)))
        val result=CopycatArrangement.compose(listOf(air),n)
        assertFalse(result.air)
        assertEquals(listOf(P(2,1,1)),result.cells.map { it.cell.pos })
    }
    @Test fun `all 128 segment columns are reachable without changing other axes`() {
        val n=BuildingNode("wall",BuildingKind.SEGMENT,inputs=listOf("a"),length=128,height=4,depth=2)
        val draft=SegmentArrangement(listOf(a),n)
        draft.move(0,draft.transforms[0],SegmentView.Z,127,0)
        assertEquals(P(127,0,0),draft.result().cells.single().cell.pos)
        assertEquals(P(127,0,0),draft.apply(n).offsets.single())
    }
}
