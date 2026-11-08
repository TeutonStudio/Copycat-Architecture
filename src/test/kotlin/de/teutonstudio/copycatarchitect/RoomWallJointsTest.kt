package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomWallJointsTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
        "panel@1" to Form(id="panel",parts=listOf(Part("create:copycat_panel",tilt=3))),
        "detail@1" to Form(id="detail",parts=listOf(Part("copycats:copycat_byte",offset=P(1,2,3)))))
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette+("wall" to "minecraft:stone")+("frame" to "minecraft:oak_planks"),forms,emptyMap())

    @Test fun `default room shares four end columns without adding pillars or stretching wall segments`() {
        val graph=BuildingGraph.explicitStarter()
        val room=eval(graph).room("room")
        assertEquals(3,room.outerWidth);assertEquals(3,room.outerDepth)
        assertEquals(setOf(P(1,0,1)),room.footprint)
        assertEquals(room.shell,LandscapeGeometry.buildingFootprint(graph))
        assertEquals(32,room.plan.placements.count { it.component=="wall" })
        assertEquals(0,room.plan.placements.count { it.component=="trim" })
        assertEquals(50,room.plan.placements.size)
        assertEquals(setOf(P(1,0,1),P(1,1,1),P(1,2,1),P(1,3,1)),room.plan.clear.toSet())
        assertTrue(room.plan.placements.all { it.pos.x in 0..2 && it.pos.z in 0..2 })
        assertTrue(room.plan.placements.none { it.pos in room.plan.clear })
        assertEquals(3,eval(graph).segment("wall").length)
        assertEquals(room.plan,eval(Gson().fromJson(Gson().toJson(graph),BuildingGraph::class.java)).room("room").plan)
        // Existing explicit JSON has no switch: it also receives the corrected assembly.
        assertEquals(room.plan,eval(graph.replace(graph.resolvedNode("room").copy(joinWallEnds=null))).room("room").plan)
    }

    @Test fun `joined corners union the actual terminal Copycats from both incident walls`() {
        val initial=BuildingGraph.explicitStarter()
        val walls=RoomWallJoints.runs(initial.resolvedNode("room")).mapIndexed { i,run ->
            run to BuildingNode("wall$i",BuildingKind.SEGMENT,module="",form="panel@1",length=3,height=4,
                materialMode=MaterialMode.GROUPED,role=if(i%2==0) "wall" else "frame")
        }
        val graph=initial.copy(nodes=initial.nodes+walls.map { it.second }).replace(initial.resolvedNode("room").copy(walls=walls.associate { it.first.id to it.second.id }))
        val evaluation=eval(graph);val plan=evaluation.room("room").plan
        for(corner in RoomVerticalEdges.of(graph.resolvedNode("room").rectangles)) {
            val position=corner.pos-corner.normalX-corner.normalZ
            val incident=walls.filter { position in it.first.cells }
            assertEquals(2,incident.size)
            for(y in 0..3) {
                val expected=incident.map { (run,source) ->
                    val p=evaluation.preview(source.id).placements.single { it.pos==P(run.cells.indexOf(position),y,0) }
                    Geometry.solid(Geometry.rotate(p.parts,run.direction))
                }.reduce(Solid::union)
                val actual=plan.placements.single { it.pos==position+P(0,y,0) }
                assertEquals(expected.key(),Geometry.solid(actual.parts).key())
                assertEquals(setOf("minecraft:stone","minecraft:oak_planks"),Geometry.resolve(actual.parts,actual.groups).materialSolids(actual.material,actual.surfaces).keys)
            }
        }
    }

    @Test fun `mirrored double layer walls keep six touching pixels on all four mounted sides`() {
        val initial=BuildingGraph.explicitStarter().editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=7,length=7))).toList())
        val skin=BuildingNode("skin",BuildingKind.SEGMENT,module="",form="panel@1",length=7,height=4)
        val back=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"back").copy(inputs=listOf(skin.id),rotationAxis=RotationAxis.Z,materialMode=MaterialMode.GROUPED,role="frame")
        val pair=BuildingAssembly.newNode(BuildingKind.PATTERN,"pair").copy(inputs=listOf(skin.id,back.id),patternAxis="depth")
        val graph=initial.copy(nodes=initial.nodes+listOf(skin,back,pair)).replace(initial.resolvedNode("room").copy(walls=initial.resolvedNode("room").walls.mapValues { pair.id }))
            .replace(initial.resolvedNode("plate_layout").copy(length=11,height=11))
        val evaluation=eval(graph);val room=evaluation.room("room")
        assertEquals(7,room.outerWidth);assertEquals(7,room.outerDepth)
        assertEquals(9,room.footprint.size)
        for(run in RoomWallJoints.runs(graph.resolvedNode("room"))) {
            val normal=RoomGeometry.directions[run.direction]
            // The middle is outside the corner intersections, so compare each whole source block.
            for(p in evaluation.preview(pair.id).placements.filter { it.pos.x==3 && it.pos.y==1 }) {
                val target=run.cells[3]-P(normal.x*p.pos.z,-p.pos.y,normal.z*p.pos.z)
                val actual=room.plan.placements.single { it.pos==target }
                assertTrue(Geometry.resolve(actual.parts,actual.groups).sameVisual(actual.material,actual.surfaces,
                    Geometry.resolve(Geometry.rotate(p.parts,run.direction),p.groups),p.material,p.surfaces))
            }
        }
        for(component in listOf("floor","ceiling")) assertEquals(room.shell,room.plan.placements.filter { it.component==component }.map { it.pos.copy(y=0) }.toSet())
        assertTrue(room.plan.placements.none { it.pos in room.plan.clear })
    }

    @Test fun `edge segments occupy shared end rows and air clears only those rows`() {
        val initial=BuildingGraph.explicitStarter()
        val edge=BuildingNode("edge",BuildingKind.SEGMENT,module="",form="detail@1",length=1,height=4,materialMode=MaterialMode.GROUPED,role="frame")
        val graph=initial.copy(nodes=initial.nodes+edge).connect("room","edge:0:0",edge.id)
        val room=eval(graph).room("room")
        assertEquals(3,room.outerWidth);assertEquals(3,room.outerDepth)
        assertEquals(4,room.plan.placements.count { it.component=="trim" })
        for(y in 0..3) {
            val p=room.plan.placements.single { it.pos==P(0,y,0) }
            // The edge source and the two existing terminal wall cells share one world block.
            assertEquals(4096,Geometry.solid(p.parts).count())
            assertEquals(setOf("minecraft:stone","minecraft:oak_planks"),Geometry.resolve(p.parts,p.groups).materialSolids(p.material,p.surfaces).keys)
        }
        val empty=eval(graph.connect("room","edge:0:0","empty_wall")).room("room")
        assertTrue((0..3).all { P(0,it,0) in empty.plan.clear })
        assertEquals(3,empty.outerWidth)
    }

    @Test fun `concave wall runs share their terminal column and ports report the required length`() {
        val initial=BuildingGraph.explicitStarter().editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=7,length=4),Rect(width=3,length=9))).toList())
        val runs=RoomWallJoints.runs(initial.resolvedNode("room"))
        val sources=runs.mapIndexed { i,r -> BuildingNode("wall$i",BuildingKind.SEGMENT,module="",length=r.length,height=4) }
        val graph=initial.copy(nodes=initial.nodes+sources).replace(initial.resolvedNode("room").copy(walls=runs.zip(sources).associate { it.first.id to it.second.id }))
            .replace(initial.resolvedNode("plate_layout").copy(length=9,height=11))
        val concave=RoomVerticalEdges.of(graph.resolvedNode("room").rectangles).single { it.concave }
        val pos=concave.pos-concave.normalX-concave.normalZ
        assertEquals(2,runs.count { pos in it.cells })
        val ports=BuildingPorts.of(graph.resolvedNode("room"),graph).filter { it.key.startsWith("wall:") }
        assertEquals(runs.map { it.length },ports.map { it.length })
        val room=eval(graph).room("room")
        assertEquals(7,room.outerWidth);assertEquals(9,room.outerDepth)
        assertEquals(4,room.plan.placements.count { it.pos.copy(y=0)==pos && it.component=="wall" })
        assertTrue(room.plan.placements.none { it.pos in room.plan.clear })
    }

    @Test fun `two block deep edge segment stays within the common wall end strips`() {
        val initial=BuildingGraph.explicitStarter().editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=7,length=7))).toList())
        val edge=BuildingNode("edge",BuildingKind.SEGMENT,module="",form="detail@1",length=2,height=4,depth=2)
        val graph=initial.copy(nodes=initial.nodes+edge).replace(initial.resolvedNode("wall_layout").copy(length=7,depth=2))
            .replace(initial.resolvedNode("plate_layout").copy(length=7,height=7)).connect("room","edge:0:0",edge.id)
        val room=eval(graph).room("room")
        assertEquals(7,room.outerWidth)
        assertEquals(setOf(P(),P(1,0,0),P(0,0,1),P(1,0,1)),room.plan.placements.filter { it.component=="trim" }.map { it.pos.copy(y=0) }.toSet())
        assertEquals(16,room.plan.placements.count { it.component=="trim" })
    }
    @Test fun `wall depth cannot silently create pillars beyond a joined outline`() {
        val initial=BuildingGraph.explicitStarter()
        val graph=initial.replace(initial.resolvedNode("wall_layout").copy(depth=4))
        assertContains(assertFails { eval(graph).room("room") }.message.orEmpty(),"exceeds the joined room outline")
    }

}
