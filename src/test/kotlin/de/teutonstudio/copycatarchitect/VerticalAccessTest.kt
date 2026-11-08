package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class VerticalAccessTest {
    private val palette=Building().palette
    private val anchor=P(8,0,8)
    private val full=listOf(Part("copycats:copycat_block"))
    private fun stories(bases:List<Int> = listOf(1,6,11)):List<AccessStorey> = bases.mapIndexed { i,base ->
        val r=Resolver();val footprint=buildSet { for(x in 0..23) for(z in 0..23) add(P(x,0,z)) }
        for(p in footprint) {
            r.add(Placement(p+P(0,base-1,0),full,component="floor"))
            r.add(Placement(p+P(0,base+4,0),full,component="ceiling"))
            for(y in base until base+4) r.clear(p+P(0,y,0))
        }
        AccessStorey("level_$i",i,base,r.plan(emptyList()),footprint)
    }
    private fun instance(id:String="core",source:String="definition",position:P=anchor)=VerticalAccessInstance(id,source,position)
    private fun compile(d:AccessDefinition,instances:List<VerticalAccessInstance> = listOf(instance()),storeys:List<AccessStorey> = stories(),
                        copycat:(String,String)->Placement = { _,_->error("Unexpected template reference") }) =
        VerticalAccess.compile(instances,storeys,{ d },copycat,palette)
    private fun merged(access:AccessCompilation,stories:List<AccessStorey>):Plan {
        val r=Resolver()
        stories.forEach { s->val p=access.carve(s.instanceId,s.plan);p.placements.forEach { r.add(it,true) };p.clear.forEach(r::clear) }
        access.plan.placements.forEach { r.add(it,true) };access.plan.clear.forEach(r::clear)
        return r.plan(emptyList())
    }

    @Test fun `straight L and U stairs reserve exact openings for every quarter turn and handedness`() {
        for(layout in StairLayout.entries) for(clockwise in listOf(true,false)) for(turn in 0..3) {
            val d=AccessDefinition("definition",VerticalAccessKind.STAIRWELL,stairwell=StairwellConfig(layout=layout,clockwise=clockwise))
            val s=stories();val a=compile(d,listOf(instance().copy(quarterTurns=turn)),s)
            assertEquals(20,a.plan.placements.count { it.component=="stairs" })
            assertTrue(a.plan.placements.any { it.component=="landing" })
            assertTrue(a.plan.placements.any { it.component=="access_wall" })
            assertTrue(a.footprints.getValue("core").all { it in s.first().footprint })
            val p=merged(a,s)
            assertTrue(p.placements.none { it.pos in p.clear })
            assertEquals(p.placements.size,p.placements.map { it.pos }.distinct().size)
            assertTrue(a.plan.placements.all { p->a.shouldSkipInteriorAir("level_0",p.pos) })
            assertTrue(a.plan.clear.all { p->a.shouldCarvePlate("level_0",p) })
        }
    }

    @Test fun `shafts connect selected floors preserve terminal caps and create only selected elevator stops`() {
        val s=stories();val d=AccessDefinition("definition",VerticalAccessKind.ELEVATOR_SHAFT)
        val a=compile(d,listOf(instance().copy(stops=listOf("level_0","level_2"))),s)
        assertTrue(P(8,1,7) in a.plan.clear)
        assertTrue(P(8,11,7) in a.plan.clear)
        assertTrue(a.plan.placements.any { it.pos==P(8,6,7) && it.component=="access_wall" })
        assertTrue((1 until 15).all { P(8,it,8) in a.plan.clear })
        val p=merged(a,s)
        assertTrue(p.placements.any { it.pos==P(8,0,8) })
        assertTrue(p.placements.any { it.pos==P(8,15,8) })
        assertTrue(p.placements.none { it.pos==P(8,5,8) || it.pos==P(8,10,8) })
        assertTrue(a.plan.placements.none { it.component in setOf("stairs","landing") })
        val selected=compile(d,listOf(instance().copy(fromStorey="level_1",toStorey="level_2")),s)
        assertTrue(selected.plan.clear.all { it.y>=6 })
        assertTrue(merged(selected,s).placements.any { it.pos==P(8,5,8) })
    }

    @Test fun `service shafts are static closed cores and instances have independent placements and ranges`() {
        val d=AccessDefinition("definition",VerticalAccessKind.SERVICE_SHAFT)
        val s=stories();val first=instance().copy(toStorey="level_1")
        val second=instance("second",position=P(16,0,16)).copy(fromStorey="level_1")
        val a=compile(d,listOf(first,second),s)
        assertEquals(setOf("core","second"),a.footprints.keys)
        assertTrue(a.plan.placements.any { it.pos==P(8,1,7) })
        assertTrue(a.plan.clear.none { it.x==16 && it.z==16 && it.y<6 })
        assertTrue(a.plan.clear.none { it.x==8 && it.z==8 && it.y>=10 })
        merged(a,s)
        assertFailsWith<IllegalArgumentException> { compile(d,listOf(first,second.copy(position=anchor)),s) }
    }

    @Test fun `template collision and receipts remain authoritative`() {
        val d=AccessDefinition("definition",VerticalAccessKind.STAIRWELL,stairwell=StairwellConfig(layout=StairLayout.L,tread="custom"))
        val t=Placement(parts=listOf(Part("copycats:copycat_stairs",recipeSource="original")),material="minecraft:oak_planks",surfaces=mapOf("body" to "minecraft:oak_planks"))
        val a=compile(d,copycat={ id,_->assertEquals("custom",id);t })
        assertTrue(a.plan.placements.filter { it.component=="stairs" }.all { p->p.parts.single().recipeSource=="original" && p.material=="minecraft:oak_planks" })
        assertContains(assertFails { compile(d,copycat={ _,_->t.copy(parts=full) }) }.message.orEmpty(),"walking profile")
        val badLanding=d.copy(stairwell=d.stairwell.copy(tread="",landing="bad"))
        assertContains(assertFails { compile(badLanding,copycat={ _,_->t.copy(parts=listOf(Part("copycats:copycat_slab"))) }) }.message.orEmpty(),"walking surface")
    }

    @Test fun `wall conflicts missing storeys low headroom and out of bounds are rejected`() {
        val d=AccessDefinition("definition",VerticalAccessKind.STAIRWELL)
        val s=stories()
        val wall=Placement(P(8,1,8),full,component="wall")
        val obstructed=s.toMutableList();obstructed[0]=s[0].copy(plan=s[0].plan.copy(placements=s[0].plan.placements+wall))
        assertContains(assertFails { compile(d,storeys=obstructed) }.message.orEmpty(),"intersects wall")
        assertFails { compile(d,listOf(instance().copy(fromStorey="missing")),s) }
        assertFails { compile(d,listOf(instance().copy(fromStorey="level_2",toStorey="level_0")),s) }
        assertFails { compile(d,listOf(instance(position=P())),s) }
        assertFails { compile(d,storeys=stories(listOf(1))) }
        assertContains(assertFails { compile(d,storeys=stories(listOf(1,3,5))) }.message.orEmpty(),"headroom")
        assertFails { compile(d.copy(stairwell=d.stairwell.copy(width=3,landingDepth=2))) }
    }

    @Test fun `isolated core previews use physical generation without requiring a connected house`() {
        for(kind in VerticalAccessKind.entries) {
            val d=AccessDefinition("definition",kind)
            val p=VerticalAccess.preview(d,{ _,_->error("Built-in templates need no reference") },palette)
            assertTrue(p.placements.isNotEmpty())
            assertTrue(p.placements.none { it.component in setOf("floor","ceiling","wall") })
            assertTrue(p.placements.none { it.pos in p.clear })
            if(kind==VerticalAccessKind.STAIRWELL) assertTrue(p.placements.any { it.component=="stairs" })
        }
    }

    @Test fun `native structural smoke fixture compiles both roof levels and all three cores`() {
        val building=de.teutonstudio.copycatarchitect.client.StructuralSmoke.fixture()
        val library=de.teutonstudio.copycatarchitect.server.Library().apply { seed() }
        val result=BuildingGraphCompiler.compile(building,library.forms,library.modules)
        assertEquals(2,result.storeys.size)
        assertTrue(result.plan.placements.any { it.component=="roof" })
        assertTrue(result.plan.placements.any { it.component=="stairs" })
        assertTrue(result.plan.placements.none { it.pos in result.plan.clear })
    }

    @Test fun `configuration persistence and footprint preview preserve stable instance selections`() {
        val value=instance().copy(fromStorey="level_0",toStorey="level_2",stops=listOf("level_0","level_2"),quarterTurns=3)
        assertEquals(value,Gson().fromJson(Gson().toJson(value),VerticalAccessInstance::class.java))
        val d=AccessDefinition("definition",VerticalAccessKind.STAIRWELL)
        assertEquals(d,Gson().fromJson(Gson().toJson(d),AccessDefinition::class.java))
        val a=compile(d,storeys=stories(listOf(1,6)))
        assertEquals(VerticalAccess.previewFootprint(d).map { it+anchor }.toSet(),a.footprints.getValue("core"))
        val empty=VerticalAccess.compile(emptyList(),emptyList(),{ error("unused") },{ _,_->error("unused") },palette)
        assertTrue(empty.plan.placements.isEmpty() && empty.plan.clear.isEmpty())
    }
}
