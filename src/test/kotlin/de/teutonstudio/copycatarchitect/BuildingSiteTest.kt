package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingSiteTest {
    private val garden=BuildingAssembly.newNode(BuildingKind.GARDEN,"garden").copy(coordinates=listOf(P(20,0,20)),fenced=true,groundDepth=2,clearance=3)
    private val path=BuildingAssembly.newNode(BuildingKind.PATH,"path").copy(coordinates=listOf(P(20,0,15),P(24,0,15)),groundDepth=2,clearance=2)
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun graph(inputs:List<String> = listOf("garden","path")):BuildingGraph {
        val base=BuildingGraph.starter()
        return base.copy(nodes=base.nodes.map { if(it.kind==BuildingKind.BUILDING) it.copy(inputs=inputs) else it }+garden+path)
    }
    private fun plan(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap()).building().plan
    private fun root(g:BuildingGraph)=g.nodes.single { it.kind==BuildingKind.BUILDING }

    @Test fun `core map moves and rotates one instance without changing its reusable definition`() {
        val source=BuildingAssembly.newNode(BuildingKind.SERVICE_SHAFT,"core").copy(serviceShaft=ServiceShaftConfig(width=2,depth=3))
        val base=graph(emptyList()).let { it.copy(nodes=it.nodes+source) }
        val building=root(base).copy(buildingOffset=P(7,0,-3),accessInstances=listOf(
            VerticalAccessInstance("one","core",P(2,0,3)),VerticalAccessInstance("two","core",P(12,0,3))))
        val original=base.replace(building)
        assertEquals(P(9,0,0),BuildingSite.part(original,"building","access:0").position)
        val moved=BuildingSite.move(original,"building","access:0",P(10,0,2))
        assertEquals(P(3,0,5),root(moved).accessInstances[0].position)
        assertEquals(building.accessInstances[1],root(moved).accessInstances[1])
        assertEquals(source,moved.nodes.first { it.id==source.id })
        val rotated=BuildingSite.rotateAccess(moved,"building","access:0",1)
        assertEquals(1,root(rotated).accessInstances[0].quarterTurns)
        assertNotEquals(BuildingSite.footprint(moved,BuildingSite.part(moved,"building","access:0")),BuildingSite.footprint(rotated,BuildingSite.part(rotated,"building","access:0")))
        assertEquals(rotated,Gson().fromJson(Gson().toJson(rotated),BuildingGraph::class.java))
        assertEquals(building.accessInstances,root(original).accessInstances)
    }

    @Test fun `moving the building carries cores exactly once while retaining their local positions`() {
        val source=BuildingAssembly.newNode(BuildingKind.SERVICE_SHAFT,"core")
        val base=graph(emptyList()).let { it.copy(nodes=it.nodes+source) }
        val original=base.replace(root(base).copy(accessInstances=listOf(VerticalAccessInstance("one","core",P(2,0,3)))))
        val before=BuildingSite.footprint(original,BuildingSite.part(original,"building","access:0"))
        val delta=P(-5,0,8)
        val moved=BuildingSite.move(original,"building",BuildingSite.BODY,delta)
        assertEquals(root(original).accessInstances,root(moved).accessInstances)
        assertEquals(before.map { it+delta }.toSet(),BuildingSite.footprint(moved,BuildingSite.part(moved,"building","access:0")))
    }

    @Test fun `removing several core wires preserves original surviving instance identities`() {
        val core=BuildingAssembly.newNode(BuildingKind.SERVICE_SHAFT,"core")
        val base=graph(emptyList()).let { it.copy(nodes=it.nodes+core) }
        val instances=(0..2).map { VerticalAccessInstance("instance_$it","core",P(it*3,0,2),quarterTurns=it) }
        val original=base.replace(root(base).copy(accessInstances=instances))
        val positions=original.nodes.map { DiagramPosition(it.id,it.x,it.y) }
        val wires=original.nodes.flatMap { n -> BuildingPorts.of(n,original,true).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,n.id,it.key) } }
        val changed=DiagramEdits.building(original,positions,wires.filterNot { it.target=="building" && it.port in setOf("access:0","access:1") })
        assertEquals(listOf(instances[2]),root(changed).accessInstances)
        val removed=DiagramEdits.building(original,positions.filterNot { it.id=="core" },wires.filterNot { it.source=="core" })
        assertTrue(root(removed).accessInstances.isEmpty())
        assertEquals(instances,root(original).accessInstances)
    }

    @Test fun `the building owns independent placements of repeated garden instances`() {
        val original=graph(listOf("garden","garden"))
        val moved=BuildingSite.move(original,"building","in:1",P(30,0,22))
        assertEquals(P(),root(moved).offsets[0])
        assertEquals(P(30,0,22),root(moved).offsets[1])
        assertEquals(original.nodes.filter { it.id!="building" },moved.nodes.filter { it.id!="building" })
        val actual=plan(moved)
        assertTrue(actual.placements.any { it.block=="minecraft:grass_block" && it.pos==P(20,0,20) })
        assertTrue(actual.placements.any { it.block=="minecraft:grass_block" && it.pos==P(50,0,42) })
        assertTrue(P(50,2,42) in actual.clear)
        assertEquals(setOf(P(50,0,42)),BuildingSite.footprint(moved,BuildingSite.part(moved,"building","in:1")))
        assertSame(moved,BuildingSite.move(moved,"building","in:1",P(30,0,22)))
    }

    @Test fun `the building body moves all storeys and its roof while gardens and paths stay placed`() {
        val original=graph();val before=plan(original);val offset=P(-7,0,4)
        val moved=BuildingSite.move(original,"building",BuildingSite.BODY,offset);val after=plan(moved)
        val exterior=setOf("garden","path","fence")
        assertEquals(before.placements.filter { it.component in exterior },after.placements.filter { it.component in exterior })
        assertEquals(before.placements.filter { it.component !in exterior }.map { it.copy(pos=it.pos+offset) },after.placements.filter { it.component !in exterior })
        val outsideClear=before.clear.filter { it.x>=20 }
        assertTrue(outsideClear.all { it in after.clear })
        assertTrue(before.clear.filter { it.x<20 }.all { it+offset in after.clear })
        assertEquals(before.materials,after.materials)
        assertEquals(before.raw,after.raw)
        assertEquals(offset,root(moved).buildingOffset)
        assertEquals(LandscapeGeometry.buildingFootprint(original).map { it+offset }.toSet(),BuildingSite.footprint(moved,BuildingSite.part(moved,"building",BuildingSite.BODY)))
    }

    @Test fun `waypoint edits belong to one building input and preserve shared path definitions`() {
        val original=graph(listOf("path","path"))
        val placed=BuildingSite.move(original,"building","in:1",P(0,0,6))
        val part=BuildingSite.part(placed,"building","in:1")
        val points=BuildingSite.points(part).toMutableList().apply { add(1,P(22,1,22)) }
        val edited=BuildingSite.pathPoints(placed,"building","in:1",points)
        assertEquals(path,edited.nodes.first { it.id=="path" })
        assertFalse(0 in root(edited).sitePaths)
        assertEquals(listOf(P(20,0,15),P(22,1,16),P(24,0,15)),root(edited).sitePaths[1])
        assertEquals(points,BuildingSite.points(BuildingSite.part(edited,"building","in:1")))
        assertEquals(path.coordinates,BuildingSite.points(BuildingSite.part(edited,"building","in:0")))
        val actual=plan(edited)
        assertTrue(actual.placements.any { it.pos==P(22,1,22) && it.block=="minecraft:gravel" })
        assertTrue(actual.placements.any { it.pos==P(22,0,15) && it.block=="minecraft:gravel" })
        assertEquals(edited,Gson().fromJson(Gson().toJson(edited),BuildingGraph::class.java))
        assertSame(edited,BuildingSite.pathPoints(edited,"building","in:1",points))
    }

    @Test fun `legacy source positions and elevation are applied once when editing world waypoints`() {
        val legacy=path.copy(landscapeOffset=P(-4,0,3),elevation=5)
        val original=graph().replace(legacy)
        val moved=BuildingSite.move(original,"building","in:1",P(8,0,-2))
        assertEquals(P(12,0,-5),root(moved).offsets[1])
        assertEquals(legacy,moved.nodes.first { it.id==path.id })
        val world=BuildingSite.points(BuildingSite.part(moved,"building","in:1"))
        assertEquals(listOf(P(28,5,13),P(32,5,13)),world)
        val changed=BuildingSite.pathPoints(moved,"building","in:1",world.map { it.copy(y=6) })
        assertEquals(legacy.coordinates.map { it.copy(y=1) },root(changed).sitePaths[1])
        assertTrue(plan(changed).placements.any { it.pos==P(28,6,13) && it.block=="minecraft:gravel" })
        val old=Gson().fromJson("{\"id\":\"building\",\"kind\":\"BUILDING\"}",BuildingNode::class.java)
        assertEquals(P(),old.buildingOffset);assertTrue(old.sitePaths.isEmpty())
    }

    @Test fun `disconnect replace and delete remove obsolete instance waypoints`() {
        val original=graph()
        val points=BuildingSite.points(BuildingSite.part(original,"building","in:1")).map { it.copy(y=1) }
        val edited=BuildingSite.pathPoints(original,"building","in:1",points)
        assertTrue(root(edited.connect("building","in:1","")).sitePaths.isEmpty())
        assertTrue(root(edited.connect("building","in:1","garden")).sitePaths.isEmpty())
        assertTrue(root(edited.remove("path")).sitePaths.isEmpty())
        val wires=edited.nodes.flatMap { n -> BuildingPorts.of(n,edited,true).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,n.id,it.key) } }
        val changed=DiagramEdits.building(edited,edited.nodes.map { DiagramPosition(it.id,it.x,it.y) },wires.filter { !(it.target=="building" && it.port=="in:1") })
        assertTrue(root(changed).sitePaths.isEmpty())
        BuildingGraphCompiler.structure(changed)
    }

    @Test fun `invalid site positions and invalid waypoint drafts do not alter the source graph`() {
        val original=graph()
        assertFails { BuildingSite.move(original,"building",BuildingSite.BODY,P(0,1,0)) }
        assertFails { BuildingSite.move(original,"building","in:0",P(129,0,0)) }
        assertFails { BuildingSite.pathPoints(original,"building","in:0",listOf(P(),P(1,0,0))) }
        assertFails { BuildingSite.pathPoints(original,"building","in:1",listOf(P(),P(0,10,0))) }
        assertFails { BuildingGraphCompiler.structure(original.replace(root(original).copy(sitePaths=mapOf(0 to listOf(P(),P(1,0,0)))))) }
        assertEquals(P(),root(original).buildingOffset);assertTrue(root(original).sitePaths.isEmpty())
    }
}
