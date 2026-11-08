package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingProjectionTest {
    @Test fun `default table reference retains the existing five block world offset`() {
        val projection=BuildingProjection()
        val tableWorld=P(100,64,-200)
        assertEquals(P(105,64,-195),projection.origin(tableWorld))
        assertEquals(tableWorld,projection.world(projection.table,tableWorld))
    }

    @Test fun `all four rotations anchor the selected model block at the actual table height`() {
        val table=P(-5,2,-7);val tableWorld=P(100,70,-200);val local=P(2,-3,4)
        val expected=listOf(P(107,65,-189),P(89,65,-193),P(93,65,-211),P(111,65,-207))
        for(turn in 0..3) {
            val projection=BuildingProjection(table,turn)
            assertEquals(tableWorld,projection.world(table,tableWorld),"Table anchor for rotation $turn")
            assertEquals(expected[turn],projection.world(local,tableWorld),"Relative block for rotation $turn")
            assertEquals(projection.origin(tableWorld),projection.world(P(),tableWorld))
            assertEquals(tableWorld+P(0,6,0),projection.world(table+P(0,6,0),tableWorld))
        }
    }

    @Test fun `projected plans rotate Copycat geometry and openings while preserving their material costs`() {
        val groups=listOf(MaterialGroup("inside","Inside","wall",0),MaterialGroup("outside","Outside","frame",1))
        val parts=listOf(
            Part("copycats:copycat_layer",size=2,tilt=1,group="inside"),
            Part("copycats:copycat_layer",size=2,tilt=1,turn=2,group="outside")
        )
        val composite=Placement(P(2,0,1),parts,"minecraft:stone_bricks",component="wall",groups=groups,
            surfaces=mapOf("inside" to "minecraft:stone_bricks","outside" to "minecraft:oak_planks"))
        val native=Placement(P(-1,3,2),block="minecraft:white_carpet",component="floor")
        val resolver=Resolver().apply { add(composite);add(native);clear(P(1,1,-2)) }
        val original=resolver.plan(listOf("Preview fixture"))
        val before=Gson().toJson(original)
        val projected=BuildingProjection(P(-5,2,-7),1).plan(original,P(100,70,-200))
        val rotated=projected.placements[0]

        assertEquals(P(92,68,-193),rotated.pos)
        assertEquals(P(91,71,-196),projected.placements[1].pos)
        assertEquals(listOf(P(95,69,-194)),projected.clear)
        assertEquals(composite.copy(pos=rotated.pos,parts=rotated.parts),rotated)
        assertEquals(native.copy(pos=P(91,71,-196)),projected.placements[1])
        val sourceGeometry=Geometry.resolve(composite.parts,groups)
        val rotatedGeometry=Geometry.resolve(rotated.parts,groups)
        assertEquals(sourceGeometry.solid.rotate(1).key(),rotatedGeometry.solid.key())
        for(group in groups) assertEquals(sourceGeometry.owned.getValue(group.id).rotate(1).key(),rotatedGeometry.owned.getValue(group.id).key())
        assertEquals(original.materials,projected.materials)
        assertEquals(original.raw,projected.raw)
        assertEquals(original.warnings,projected.warnings)
        assertEquals(composite.surfaceItems(),rotated.surfaceItems())
        assertEquals(before,Gson().toJson(original),"World projection must leave the saved local plan unchanged")
    }

    @Test fun `projection persists in building graphs while older documents retain the default reference`() {
        val gson=Gson();val original=BuildingGraph.assembledStarter()
        val graph=original.copy(projection=BuildingProjection(P(12,-8,19),3))
        val building=Building(schema=2,graph=graph)
        assertEquals(building,gson.fromJson(gson.toJson(building),Building::class.java))

        val legacy=gson.toJsonTree(Building(schema=2,graph=original)).asJsonObject
        legacy.getAsJsonObject("graph").remove("projection")
        val restored=gson.fromJson(legacy,Building::class.java)
        assertEquals(BuildingProjection(),checkNotNull(restored.graph).projection)
        assertEquals(original,restored.graph)
        BuildingGraphCompiler.structure(checkNotNull(restored.graph))
    }

    @Test fun `projection validates table bounds and quarter turns before compilation or projection`() {
        val graph=BuildingGraph.assembledStarter()
        for(valid in listOf(BuildingProjection(P(-128,-256,128),0),BuildingProjection(P(128,256,-128),3))) {
            assertEquals(valid,valid.validate())
            BuildingGraphCompiler.structure(graph.copy(projection=valid))
        }
        for(invalid in listOf(
            BuildingProjection(P(-513,0,0)),BuildingProjection(P(513,0,0)),
            BuildingProjection(P(0,0,-513)),BuildingProjection(P(0,0,513)),
            BuildingProjection(P(0,-257,0)),BuildingProjection(P(0,257,0)),
            BuildingProjection(turn=-1),BuildingProjection(turn=4)
        )) {
            assertFailsWith<IllegalArgumentException> { invalid.origin(P()) }
            assertFailsWith<IllegalArgumentException> { BuildingGraphCompiler.structure(graph.copy(projection=invalid)) }
        }
    }

    @Test fun `changing the table reference does not change local compilation or graph edits`() {
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        val original=BuildingGraph.assembledStarter()
        val graph=original.copy(projection=BuildingProjection(P(14,-6,-12),2))
        val local=Generator.generate(Building(schema=2,graph=original),forms,emptyMap())
        assertEquals(local,Generator.generate(Building(schema=2,graph=graph),forms,emptyMap()))
        assertEquals(graph.projection,graph.replace(graph.nodes.first { it.id=="room" }.copy(name="Renamed room")).projection)
        assertEquals(graph.projection,graph.connect("building","roof","").projection)
    }
}
