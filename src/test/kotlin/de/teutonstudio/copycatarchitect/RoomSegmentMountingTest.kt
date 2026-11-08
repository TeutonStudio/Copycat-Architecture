package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomSegmentMountingTest {
    private val forms=mapOf(
        "full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
        "panel@1" to Form(id="panel",parts=listOf(Part("create:copycat_panel",tilt=3))))

    @Test fun `mirrored three pixel layers stay joined with their finishes when mounted on every room wall`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val source=BuildingNode("skin",BuildingKind.SEGMENT,module="",form="panel@1",length=3,height=4)
        val mirror=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"back").copy(inputs=listOf(source.id),rotationAxis=RotationAxis.Z,
            materialMode=MaterialMode.GROUPED,role="frame")
        val pair=BuildingAssembly.newNode(BuildingKind.PATTERN,"pair").copy(inputs=listOf(source.id,mirror.id),patternAxis="depth")
        val room=initial.nodes.single { it.id=="room" }
        val graph=initial.copy(nodes=initial.nodes+listOf(source,mirror,pair)).replace(room.copy(walls=room.walls.mapValues { pair.id }))
            .replace(initial.nodes.single { it.id=="plate_layout" }.copy(length=7,height=7))
        val palette=Building().palette+("wall" to "minecraft:stone")+("frame" to "minecraft:oak_planks")
        val evaluation=BuildingGraphCompiler.Evaluation(graph,palette,forms,emptyMap())
        val preview=evaluation.preview(pair.id)
        val built=evaluation.room(room.id).plan
        for(edge in RoomGeometry.edges(room.rectangles)) {
            val outward=RoomGeometry.directions[edge.direction]
            // Local +Z points into the room, so the segment's minimum Z starts at its outermost layer.
            val origin=edge.cells.first()+P(outward.x,0,outward.z)
            for(p in preview.placements) {
                val local=p.pos.turn(edge.direction)
                val expected=p.copy(pos=origin+local,parts=Geometry.rotate(p.parts,edge.direction))
                val actual=built.placements.single { it.pos==expected.pos }
                assertTrue(Geometry.resolve(actual.parts,actual.groups).sameVisual(actual.material,actual.surfaces,
                    Geometry.resolve(expected.parts,expected.groups),expected.material,expected.surfaces),"Wall ${edge.direction} at ${expected.pos}")
            }
        }
    }

    @Test fun `floor and ceiling mount the whole asymmetric segment with the same rotation as their Copycats`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val copies=(0..2).map { i -> BuildingNode("finish$i",BuildingKind.COPYCAT,form="panel@1",
            surfaces=mapOf("body" to listOf("minecraft:stone","minecraft:glass","minecraft:oak_planks")[i])) }
        val plate=BuildingNode("plate",BuildingKind.SEGMENT,module="",inputs=copies.map { it.id },length=5,height=5,depth=2)
        val room=initial.nodes.single { it.id=="room" }.copy(floor=plate.id,ceiling=plate.id)
        val graph=initial.copy(nodes=initial.nodes+copies+plate).replace(room)
        val evaluation=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
        val before=evaluation.preview(plate.id)
        val compiled=evaluation.room(room.id)
        for(floor in listOf(true,false)) {
            val origin=if(floor) P(-1,compiled.minimum,3) else P(-1,4+plate.depth-1,-1)
            val q=if(floor) 3 else 1
            for(p in before.placements) {
                val position=origin+if(floor) P(p.pos.x,p.pos.z,-p.pos.y) else P(p.pos.x,-p.pos.z,p.pos.y)
                val expected=p.parts.map { PartRotations.rotate(it,RotationAxis.X,q)!! }
                val actual=compiled.plan.placements.single { it.pos==position }
                assertTrue(Geometry.resolve(actual.parts,actual.groups).sameVisual(actual.material,actual.surfaces,
                    Geometry.resolve(expected,p.groups),p.material,p.surfaces),"${if(floor) "Floor" else "Ceiling"} at $position")
            }
        }
    }
}
