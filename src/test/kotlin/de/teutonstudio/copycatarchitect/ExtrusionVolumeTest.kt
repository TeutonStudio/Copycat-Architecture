package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.BitSet
import kotlin.test.*

class ExtrusionVolumeTest {
    private val full=Part("copycats:copycat_block")
    private fun clipped(indices:Iterable<Int>,group:String="body")=full.copy(group=group,
        cutMask=Base64.getEncoder().encodeToString(BitSet(4096).apply { indices.forEach(::set) }.toByteArray()))
    private fun operation(kind:NodeKind,a:Part,b:Part)=GraphCompiler.compile(NodeGraph(nodes=listOf(
        GraphNode("a",part=a),GraphNode("b",part=b),GraphNode("operation",kind,listOf("a","b")),
        GraphNode("result",NodeKind.OUTPUT,listOf("operation")))))

    @Test fun `native shapes consume zinc by occupied grid volume`() {
        val costs=listOf(
            full to 256,
            Part("copycats:copycat_slab") to 128,
            Part("copycats:copycat_stairs") to 192,
            Part("create:copycat_panel") to 48,
            Part("copycats:copycat_half_panel") to 24,
            Part("copycats:copycat_byte_panel") to 12,
            Part("copycats:copycat_slice") to 4,
            Part("copycats:copycat_vertical_slice") to 4,
            Part("copycats:copycat_corner_slice") to 1,
            Part("copycats:copycat_flat_pane") to 32,
            Part("copycats:copycat_flat_pane",discCut=1) to 16,
            Part("copycats:copycat_flat_pane",discCut=2) to 8)
        for((part,millibuckets) in costs) for(turn in 0..3) {
            assertEquals(millibuckets,Extrusion.cost(listOf(part.copy(turn=turn))),part.toString())
        }
    }

    @Test fun `Boolean cuts and overlapping sources charge only the final occupied cells`() {
        val slab=Part("copycats:copycat_slab")
        val raised=slab.copy(offset=P(0,4,0))
        assertEquals(192,Extrusion.cost(operation(NodeKind.UNION,slab,raised)))
        assertEquals(64,Extrusion.cost(operation(NodeKind.INTERSECTION,slab,raised)))
        assertEquals(64,Extrusion.cost(operation(NodeKind.DIFFERENCE,slab,raised)))
        assertEquals(128,Extrusion.cost(operation(NodeKind.SYMMETRIC_DIFFERENCE,slab,raised)))
        assertEquals(256,Extrusion.cost(listOf(full,slab,slab)))
        val groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","frame",1))
        assertEquals(256,Extrusion.cost(listOf(full,slab.copy(group="cap")),groups))
        assertEquals(256,Extrusion.cost(listOf(full,slab.copy(group="cap")),groups.map { it.copy(priority=1-it.priority) }))
    }

    @Test fun `one to sixteen occupied cells need one mB and the seventeenth needs two`() {
        for(count in 1..16) assertEquals(1,Extrusion.cost(listOf(clipped(0 until count))))
        assertEquals(2,Extrusion.cost(listOf(clipped(0..16))))
        // Widely separated boundary cells must not be replaced by a bounding volume.
        val corners=listOf(0,15,240,255,3840,3855,4080,4095)
        val boundary=clipped(corners)
        val solid=Solid.part(boundary)
        assertEquals(8,solid.count())
        for(index in 0 until 4096) assertEquals(index in corners,solid.has(index%16,index/256,index/16%16))
        assertEquals(1,Extrusion.cost(listOf(boundary)))
    }

    @Test fun `rounding happens after union within each printed block`() {
        val left=clipped(0..7)
        val right=clipped(8..15)
        assertEquals(1,Extrusion.cost(listOf(left,right)))
        val groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","frame",1))
        assertEquals(1,Extrusion.cost(listOf(left,right.copy(group="cap")),groups))
        val resolver=Resolver()
        resolver.add(Placement(P(),listOf(left),material=""))
        resolver.add(Placement(P(1,0,0),listOf(right),material=""))
        assertEquals(2,resolver.plan(emptyList()).raw)
    }

    @Test fun `legacy cutting metadata cannot change printing volume`() {
        for(recipes in listOf(emptyMap(),mapOf(full.source to 1),mapOf(full.source to 2),mapOf(full.source to 0)))
            assertEquals(256,Extrusion.cost(listOf(full),recipes=recipes))
    }
}
