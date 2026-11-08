package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.client.BuildingPanelSection
import de.teutonstudio.copycatarchitect.client.BuildingSourceEditors
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class LandscapeTest {
    private fun garden()=BuildingAssembly.newNode(BuildingKind.GARDEN,"garden")
    private fun path()=BuildingAssembly.newNode(BuildingKind.PATH,"path")
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { g ->
        g.copy(nodes=g.nodes.map { if(it.id=="wall") it.copy(module="") else it }+nodes)
    }
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun evaluate(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap())

    @Test fun `garden keeps holes disconnected areas and fences each perimeter cell once`() {
        val area=(0..4).flatMap { x -> (0..4).map { z -> P(x,0,z) } }.toSet()-P(2,0,2)+P(8,0,0)
        val garden=garden().copy(coordinates=area.toList(),fenced=true,fenceHeight=2,elevation=3,groundDepth=2,clearance=4)
        val p=evaluate(graph(garden)).preview(garden.id)
        val boundary=LandscapeGeometry.fenceCells(area)
        assertEquals(area,p.placements.filter { it.block=="minecraft:grass_block" }.map { it.pos.copy(y=0) }.toSet())
        assertEquals(boundary.size*2,p.materials["minecraft:oak_fence"])
        assertEquals(area.size,p.materials["minecraft:dirt"])
        assertTrue(p.placements.none { it.pos.x==2 && it.pos.z==2 })
        assertTrue(p.placements.none { it.pos in p.clear })
        assertEquals(area.size*4-boundary.size*2,p.clear.size)
        assertEquals(p.placements.size,p.placements.map { it.pos }.distinct().size)
    }

    @Test fun `fence is optional and native garden blocks need no Copycat recipes`() {
        val garden=garden()
        val p=evaluate(graph(garden)).preview(garden.id)
        assertEquals(mapOf("minecraft:grass_block" to 25),p.materials)
        assertEquals(0,p.raw)
        assertTrue(p.clear.isEmpty())
    }

    @Test fun `garden can share and edit an area source without changing local fallback`() {
        val area=BuildingAssembly.newNode(BuildingKind.AREA,"area")
        val garden=garden().copy(geometry="area")
        val g=graph(area,garden)
        assertEquals(9,evaluate(g).preview(garden.id).placements.size)
        val coordinates=listOf(P(-10,0,-10),P(-9,0,-10))
        val changed=g.editLandscapeCoordinates(garden.id,coordinates)
        assertEquals(coordinates,changed.nodes.first { it.id=="area" }.coordinates)
        assertEquals(garden.coordinates,changed.nodes.first { it.id==garden.id }.coordinates)
        assertEquals(coordinates.toSet(),evaluate(changed).preview(garden.id).placements.map { it.pos }.toSet())
    }

    @Test fun `path follows ordered bends and interpolates endpoint elevations`() {
        val path=path().copy(coordinates=listOf(P(-3,0,0),P(0,0,0),P(0,3,3)),groundDepth=3,clearance=2)
        val p=evaluate(graph(path)).preview(path.id)
        val surface=p.placements.filter { it.block=="minecraft:gravel" }.map { it.pos }.toSet()
        assertEquals(setOf(P(-3,0,0),P(-2,0,0),P(-1,0,0),P(0,0,0),P(0,1,1),P(0,2,2),P(0,3,3)),surface)
        assertEquals(14,p.materials["minecraft:dirt"])
        assertEquals(14,p.clear.size)
        assertTrue(p.placements.none { it.pos in p.clear })
    }

    @Test fun `wide slopes keep one surface per horizontal cell and include the full requested width`() {
        for(width in 1..5) {
            val path=path().copy(coordinates=listOf(P(),P(0,4,4)),pathWidth=width)
            val cells=LandscapeGeometry.pathCells(path)
            assertEquals(width,cells.map { it.x }.distinct().size)
            assertEquals(cells.size,cells.map { it.x to it.z }.distinct().size)
            assertEquals(0,cells.first { it.x==0 && it.z==0 }.y)
            assertEquals(4,cells.first { it.x==0 && it.z==4 }.y)
            val plan=evaluate(graph(path)).preview(path.id)
            assertTrue(plan.placements.none { it.pos in plan.clear })
        }
    }

    @Test fun `paths reject vertical jumps out of bounds and inconsistent crossings`() {
        val path=path()
        for(points in listOf(listOf(P()),listOf(P(),P(0,2,0)),listOf(P(),P(1,2,0)),listOf(P(),P(128,0,0))))
            assertFails { evaluate(graph(path.copy(coordinates=points))) }
        assertFails { LandscapeGeometry.pathCells(path.copy(coordinates=listOf(P(),P(4,0,0),P(4,4,4),P(0,4,0)))) }
        assertFails { evaluate(graph(path.copy(pathWidth=17))) }
        assertFails { evaluate(graph(path.copy(groundDepth=0))) }
        assertFails { evaluate(graph(garden().copy(fenceBlock="minecraft:tnt"))) }
    }

    @Test fun `Copycat surface uses its form and material instead of the default ground block`() {
        val source=BuildingNode("surface",BuildingKind.COPYCAT,role="trim")
        val path=path().copy(floor=source.id)
        val p=evaluate(graph(path,source)).preview(path.id)
        assertTrue(p.placements.all { it.block.isEmpty() && it.material==Building().palette.getValue("trim") })
        assertEquals(5*Extrusion.cost(forms.getValue("full@1").parts),p.raw)
        assertFails { evaluate(graph(path,source.copy(block="minecraft:air"))).preview(path.id) }
    }

    @Test fun `exterior sockets are typed optional and included in complete building without roof`() {
        val garden=garden().copy(coordinates=listOf(P(12,0,0)),fenced=true)
        val path=path().copy(coordinates=listOf(P(16,0,0),P(16,0,2)))
        var g=graph(garden,path)
        val output=g.nodes.first { it.kind==BuildingKind.BUILDING }
        g=g.replace(output.copy(roof="")).connect(output.id,"in:0",garden.id).connect(output.id,"in:1",path.id)
        val p=evaluate(g).building().plan
        assertEquals(1,p.materials["minecraft:grass_block"])
        assertEquals(3,p.materials["minecraft:gravel"])
        assertEquals(1,p.materials["minecraft:oak_fence"])
        assertFails { g.connect(output.id,"in:2","room") }
        assertFails { g.connect(garden.id,"geometry",path.id) }
        assertFails { g.connect(path.id,"floor",garden.id) }
        g=g.connect(output.id,"in:1","")
        assertEquals(listOf(garden.id),g.nodes.first { it.id==output.id }.inputs)
        assertTrue(evaluate(g).building().plan.materials["minecraft:gravel"]==null)
    }

    @Test fun `diagram edits retain exterior wiring and deletion clears references`() {
        val garden=garden().copy(coordinates=listOf(P(12,0,0)))
        val source=BuildingNode("surface",BuildingKind.COPYCAT)
        var g=graph(garden,source).connect("garden","floor","surface").connect("building","in:0","garden")
        val wires=g.nodes.flatMap { n -> BuildingPorts.of(n,g).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,n.id,it.key) } }
        val changed=DiagramEdits.building(g,g.nodes.map { DiagramPosition(it.id,it.x+5,it.y+5) },wires)
        assertEquals(listOf("garden"),changed.nodes.first { it.id=="building" }.inputs)
        assertEquals("surface",changed.nodes.first { it.id=="garden" }.floor)
        g=changed.remove("surface").remove("garden")
        assertTrue(g.nodes.first { it.id=="building" }.inputs.isEmpty())
        BuildingGraphCompiler.structure(g)
    }

    @Test fun `landscape collisions with the building remain visible errors`() {
        val g=graph(garden().copy(coordinates=listOf(P(0,0,0)))).connect("building","in:0","garden")
        assertFails { evaluate(g).building() }
    }

    @Test fun `coordinates and all landscape settings survive JSON and legacy graphs keep defaults`() {
        val path=path().copy(pathWidth=4,elevation=-3,groundDepth=5,groundBlock="minecraft:stone",clearance=3)
        val garden=garden().copy(fenced=true,fenceBlock="minecraft:spruce_fence",fenceHeight=3)
        val gson=Gson();val g=graph(path,garden)
        assertEquals(g,gson.fromJson(gson.toJson(g),BuildingGraph::class.java))
        val legacy=gson.fromJson("{\"id\":\"old\",\"kind\":\"ROOM\"}",BuildingNode::class.java)
        assertEquals(1,legacy.groundDepth)
        assertEquals("minecraft:oak_fence",legacy.fenceBlock)
        assertFalse(legacy.fenced)
        assertEquals(BuildingPanelSection.LANDSCAPE,BuildingPanelSection.of(BuildingKind.GARDEN))
        assertEquals(BuildingPanelSection.LANDSCAPE,BuildingPanelSection.of(BuildingKind.PATH))
    }

    @Test fun `path coordinate editor preserves ordering and all three axes`() {
        val points=listOf(P(-4,2,1),P(0,0,1),P(0,1,5))
        assertEquals(points,BuildingSourceEditors.parsePath(BuildingSourceEditors.pathText(points)))
        assertFails { BuildingSourceEditors.parsePath("0,0; 1,1") }
        assertFails { BuildingSourceEditors.parsePath("0,0,0; 0,2,0") }
    }
    @Test fun `moving a garden translates its fence foundation and clearance without changing its source`() {
        val area=BuildingAssembly.newNode(BuildingKind.AREA,"shared")
        val garden=garden().copy(geometry=area.id,fenced=true,fenceHeight=2,groundDepth=3,clearance=4,elevation=-2)
        val sibling=garden.copy(id="second_garden")
        val original=graph(area,garden,sibling)
        val before=evaluate(original).preview(garden.id)
        val offset=P(17,0,-9);val changed=original.moveLandscape(garden.id,offset)
        val after=evaluate(changed).preview(garden.id)
        assertEquals(before.copy(placements=before.placements.map { it.copy(pos=it.pos+offset) },clear=before.clear.map { it+offset }),after)
        assertEquals(area,changed.nodes.first { it.id==area.id })
        assertEquals(sibling,changed.nodes.first { it.id==sibling.id })
        assertEquals(garden.coordinates,changed.nodes.first { it.id==garden.id }.coordinates)
        assertEquals(garden.x,changed.nodes.first { it.id==garden.id }.x)
        assertEquals(garden.y,changed.nodes.first { it.id==garden.id }.y)
        assertEquals(before,evaluate(changed).preview(sibling.id))
        assertEquals(changed,Gson().fromJson(Gson().toJson(changed),BuildingGraph::class.java))
        assertEquals(P(),Gson().fromJson("{\"id\":\"old\",\"kind\":\"ROOM\"}",BuildingNode::class.java).landscapeOffset)
        assertFails { original.moveLandscape(garden.id,P(129,0,0)) }
        assertFails { original.moveLandscape(garden.id,P(0,1,0)) }
        assertFails { original.moveLandscape("room",P(1,0,0)) }
    }

    @Test fun `garden placement resolves building overlap and preserves every other building position`() {
        val garden=garden().copy(coordinates=listOf(P()),fenced=true,groundDepth=2,clearance=3)
        val original=graph(garden)
        val base=evaluate(original).building().plan
        val connected=original.connect("building","in:0",garden.id)
        assertFails { evaluate(connected).building() }
        val changed=connected.moveLandscape(garden.id,P(9,0,-5))
        val plan=evaluate(changed).building().plan
        assertEquals(base.placements,plan.placements.filter { it.component !in setOf("garden","fence") })
        assertTrue(plan.placements.any { it.pos==P(9,0,-5) && it.block=="minecraft:grass_block" })
        assertTrue(plan.placements.any { it.pos==P(9,1,-5) && it.block=="minecraft:oak_fence" })
        assertTrue(P(9,2,-5) in plan.clear)
        assertEquals(setOf(P(9,0,-5)),LandscapeGeometry.placedFootprint(changed,changed.nodes.first { it.id==garden.id }))
        assertEquals(RoomGeometry.shell(listOf(Rect(width=3,length=3))),LandscapeGeometry.buildingFootprint(changed))
    }

    @Test fun `path placement composes with elevation table projection and diagram changes exactly once`() {
        val path=path().copy(coordinates=listOf(P(),P(0,2,3)),elevation=4,groundDepth=2,clearance=2)
        val original=graph(path);val before=evaluate(original).preview(path.id)
        val offset=P(-8,0,7);val moved=original.moveLandscape(path.id,offset)
        val relocated=moved.replace(moved.nodes.first { it.id==path.id }.copy(x=400,y=-200))
        val after=evaluate(relocated).preview(path.id)
        assertEquals(before.placements.map { it.copy(pos=it.pos+offset) },after.placements)
        assertEquals(before.clear.map { it+offset },after.clear)
        val projection=BuildingProjection(table=P(-3,0,-5),turn=1);val tableWorld=P(40,70,30)
        val projected=projection.plan(after,tableWorld)
        assertEquals(after.placements.map { projection.world(it.pos,tableWorld) },projected.placements.map { it.pos })
        assertEquals(after.clear.map { projection.world(it,tableWorld) },projected.clear)
        assertEquals(offset,relocated.nodes.first { it.id==path.id }.landscapeOffset)
        assertSame(relocated,relocated.moveLandscape(path.id,offset))
    }

}
