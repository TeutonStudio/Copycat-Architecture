package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SegmentArrangementTest {
    private fun source(x:Int,y:Int,z:Int,block:String="minecraft:stone")=FixedSegment(x,y,
        buildList { for(a in 0 until x) for(b in 0 until y) for(c in 0 until z) add(SegmentCell(Cell(P(a,b,c),block=block))) },depth=z)
    private fun node(axis:String="horizontal",transforms:List<SegmentTransform> = emptyList())=
        BuildingNode("pattern",BuildingKind.PATTERN,inputs=listOf("a","b","c"),patternAxis=axis,transforms=transforms)
    private val sources get()=listOf(source(2,3,1),source(1,1,2),source(3,2,1))

    @Test fun `opening each linear layout retains input order and its placement axis`() {
        val expected=mapOf("horizontal" to listOf(P(),P(2,0,0),P(3,0,0)),
            "vertical" to listOf(P(),P(0,3,0),P(0,4,0)),"depth" to listOf(P(),P(0,0,1),P(0,0,3)))
        expected.forEach { (axis,offsets) -> assertEquals(offsets,SegmentArrangement(sources,node(axis)).transforms.map { it.offset }) }
    }
    @Test fun `dragging any view retains its hidden coordinate and rotation`() {
        val original=SegmentTransform(P(7,-4,11),RotationAxis.X,3)
        for((view,expected) in listOf(SegmentView.X to P(7,-7,9),SegmentView.Y to P(5,-4,14),SegmentView.Z to P(5,-7,11))) {
            val draft=SegmentArrangement(sources,node("transform",listOf(original,original,original)))
            draft.move(1,original,view,-2,3)
            assertEquals(original.copy(offset=expected),draft.transforms[1])
            assertEquals(original,draft.transforms[0]);assertEquals(original,draft.transforms[2])
            // Projection and picking use precisely the same signed two-dimensional displacement.
            val before=view.project(original.offset);val after=view.project(expected)
            assertEquals(P(-2,0,3),P(after.x-before.x,0,after.z-before.z))
        }
    }
    @Test fun `projection shows asymmetric rotated geometry at negative positions`() {
        val sparse=FixedSegment(2,3,listOf(SegmentCell(Cell(P(1,2,0),block="minecraft:stone"))),depth=1)
        val draft=SegmentArrangement(listOf(sparse,sources[1],sources[2]),node("transform",listOf(SegmentTransform(P(-4,5,-2),RotationAxis.Z,1))))
        assertEquals(P(3,2,1),draft.oriented(0).let { P(it.length,it.height,it.depth) })
        assertEquals(setOf(P(-4,0,-7)),draft.projected(0,SegmentView.Z))
        assertEquals(setOf(P(-2,0,-7)),draft.projected(0,SegmentView.X))
        assertEquals(setOf(P(-4,0,-2)),draft.projected(0,SegmentView.Y))
    }
    @Test fun `different heights and depths compile after moving multiple segments on the grid`() {
        val definitions=sources.mapIndexed { i,s -> BuildingNode(('a'+i).toString(),BuildingKind.SEGMENT,module="",length=s.length,height=s.height,depth=s.depth,block="minecraft:stone") }
        val original=node();val draft=SegmentArrangement(sources,original)
        draft.move(1,draft.transforms[1],SegmentView.Z,1,-2)
        draft.move(2,draft.transforms[2],SegmentView.Y,4,2)
        val changed=draft.apply(original)
        val graph=BuildingGraph.starter().let { it.copy(nodes=it.nodes+definitions+changed) }
        val evaluation=BuildingGraphCompiler.Evaluation(graph,Building().palette,emptyMap(),emptyMap())
        assertEquals(draft.result(),evaluation.segment(original.id))
        assertEquals("transform",changed.patternAxis)
        assertEquals(original,node())
        val gson=Gson();assertEquals(changed,gson.fromJson(gson.toJson(changed),BuildingNode::class.java))
    }
    @Test fun `conflicts can be corrected without changing the source document`() {
        val original=node("transform")
        val draft=SegmentArrangement(listOf(sources[0],source(1,1,2,"minecraft:oak_planks"),sources[2]),original)
        assertFailsWith<IllegalArgumentException> { draft.apply(original) }
        draft.update(1,SegmentTransform(P(4,0,0)));draft.update(2,SegmentTransform(P(8,0,0)))
        assertTrue(draft.result().cells.isNotEmpty())
        assertTrue(original.transforms.isEmpty())
    }
    @Test fun `out of range moves fail atomically and excessive union cannot be applied`() {
        val draft=SegmentArrangement(sources,node())
        val before=draft.transforms
        assertFailsWith<IllegalArgumentException> { draft.move(1,before[1],SegmentView.Y,129,0) }
        assertEquals(before,draft.transforms)
        draft.update(1,SegmentTransform(P(128,0,0)))
        assertFailsWith<IllegalArgumentException> { draft.apply(node()) }
    }
    @Test fun `air projects its entire box and remains an explicit clearing requirement`() {
        val air=FixedSegment(2,2,emptyList(),air=true,depth=2)
        val draft=SegmentArrangement(listOf(air,sources[1],sources[2]),node())
        for(view in SegmentView.entries) assertEquals(4,draft.projected(0,view).size)
        assertEquals(8,draft.result().cells.count { it.cell.empty })
    }
    @Test fun `stored transformations survive reopening including defaulted trailing inputs`() {
        val stored=SegmentTransform(P(-3,4,5),RotationAxis.Z,2)
        val draft=SegmentArrangement(sources,node("transform",listOf(stored)))
        assertEquals(listOf(stored,SegmentTransform(),SegmentTransform()),draft.transforms)
        draft.update(2,SegmentTransform(P(8,0,0)))
        assertEquals(stored,draft.transforms[0])
    }
}
