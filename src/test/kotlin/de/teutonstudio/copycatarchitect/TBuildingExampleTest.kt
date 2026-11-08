package de.teutonstudio.copycatarchitect

import com.google.gson.GsonBuilder
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Command
import de.teutonstudio.copycatarchitect.server.CommandTransport
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.*

/** Acceptance checks on the resulting building, independent of the graph's chosen node IDs. */
class TBuildingExampleTest {
    private val example=TBuildingExample
    private val graph get()=assertNotNull(example.building.graph)
    private val compiled by lazy { BuildingGraphCompiler.compile(example.building,example.forms,example.modules) }
    private val plan get()=compiled.plan
    private val expectedRectangles=listOf(Rect(0,0,15,5),Rect(5,5,5,10))
    private val expectedInterior=buildSet {
        for(x in 0..14) for(z in 0..4) add(P(x,0,z))
        for(x in 5..9) for(z in 5..14) add(P(x,0,z))
    }
    private val byPosition by lazy { plan.placements.associateBy { it.pos } }

    @Test fun `three complete storeys preserve the exact T footprint and both recessed sides`() {
        assertEquals(125,expectedInterior.size)
        assertEquals(expectedInterior,example.footprint)
        assertEquals(listOf(0,1,2),compiled.storeys.map { it.level })
        assertEquals(listOf(1,7,13),compiled.storeys.map { it.base })
        val evaluation=BuildingGraphCompiler.Evaluation(graph,example.building.palette,example.forms,example.modules)
        val rooms=graph.nodes.filter { it.kind==BuildingKind.ROOM }
        assertTrue(rooms.isNotEmpty(),"The test building must retain its editable room geometry")
        for(room in rooms) assertEquals(expectedInterior,evaluation.room(room.id).footprint)

        // A T has six convex and two reentrant vertices; an L or filled bounding rectangle fails.
        val vertices=(0..15).flatMap { x -> (0..15).map { z -> P(x,0,z) } }
        fun adjacentCount(v:P)=listOf(v,v+P(-1,0,0),v+P(0,0,-1),v+P(-1,0,-1)).count { it in expectedInterior }
        assertEquals(6,vertices.count { adjacentCount(it)==1 })
        assertEquals(setOf(P(5,0,5),P(10,0,5)),vertices.filter { adjacentCount(it)==3 }.toSet())

        val clear=plan.clear.toSet()
        val usable=buildSet {
            for(storey in compiled.storeys) for(y in 0..4) for(p in expectedInterior) add(p+P(0,storey.base+y,0))
        }
        assertEquals(1875,usable.size)
        assertTrue(usable.all { it in clear },"All five interior layers of every T-shaped floor must remain usable")
        assertTrue(usable.none { it in byPosition })
        for(storey in compiled.storeys) {
            assertTrue(expectedInterior.all { it+P(0,storey.base-1,0) in byPosition },"Each level needs a structural floor")
            for(y in storey.base..storey.base+4) {
                for(x in listOf(0,1,2,12,13,14)) for(z in 7..14)
                    assertTrue(P(x,y,z) !in byPosition,"The recessed sides of the T must not be filled: $x,$y,$z")
            }
        }
        assertEquals(RoomGeometry.shell(expectedRectangles),evaluation.storey(graph.nodes.single { it.kind==BuildingKind.BUILDING }.levels.getValue(0)).footprint)
    }

    @Test fun `every floor has framed transparent windows on all four facade directions`() {
        val edges=RoomGeometry.edges(expectedRectangles)
        var checked=0
        for(storey in compiled.storeys) for(direction in 0..3) {
            val windows=edges.filter { it.direction==direction }.flatMap { edge ->
                edge.cells.flatMap { p -> (0..4).mapNotNull { y ->
                    byPosition[p+P(0,storey.base+y,0)]?.takeIf { "minecraft:glass" in it.surfaceItems() }
                } }
            }.distinctBy { it.pos }
            assertTrue(windows.isNotEmpty(),"Missing windows on floor ${storey.level}, direction $direction")
            for(window in windows) {
                val resolved=Geometry.resolve(window.parts,window.groups)
                val materials=resolved.materialSolids(window.material,window.surfaces)
                val glass=materials.getValue("minecraft:glass")
                val opaque=materials.filterKeys { it!="minecraft:glass" }.values.fold(Solid()) { a,b -> a.union(b) }
                assertTrue(glass.count()>=128,"The window needs a substantial clear pane")
                assertTrue(opaque.count()>0,"The pane must retain a visible frame")
                assertEquals(0,glass.intersect(opaque).count())
                // Trace the complete block depth. A glass surface pasted onto an opaque wall fails.
                var transparentRays=0
                for(tangent in 0..15) for(y in 0..15) {
                    val ray=(0..15).map { normal -> if(direction%2==0) P(tangent,y,normal) else P(normal,y,tangent) }
                    if(ray.any { glass.has(it.x,it.y,it.z) } && ray.none { opaque.has(it.x,it.y,it.z) }) transparentRays++
                }
                assertTrue(transparentRays>=64,"Opaque backing closes the window at ${window.pos}, direction $direction")
                checked++
            }
        }
        assertTrue(checked>=12)
    }

    @Test fun `each storey has geometric wall ornaments repeated facade patterns and corner trim`() {
        assertTrue(plan.materials.size>=4,"The building must use several actual surface materials")
        for(storey in compiled.storeys) {
            val walls=plan.placements.filter { it.component=="wall" && it.pos.y in storey.base..storey.base+4 }
            val decorated=walls.filter { p ->
                val solids=Geometry.resolve(p.parts,p.groups).materialSolids(p.material,p.surfaces)
                "minecraft:glass" !in solids && solids.size>=2
            }
            assertTrue(decorated.isNotEmpty(),"Missing multi-material wall ornament on floor ${storey.level}")
            val north=RoomGeometry.edges(expectedRectangles).single { it.direction==0 }
            val frieze=north.cells.map { p -> assertNotNull(byPosition[p+P(0,storey.base+3,0)]) }
            assertTrue(frieze.map { it.visualKey() }.distinct().size>=2,"A flat uniform wall is not a facade pattern")
            assertEquals(frieze.take(5).map { it.visualKey() },frieze.drop(5).take(5).map { it.visualKey() })
            assertEquals(frieze.take(5).map { it.visualKey() },frieze.drop(10).take(5).map { it.visualKey() })
            for((index,p) in frieze.withIndex()) {
                val solids=Geometry.resolve(p.parts,p.groups).materialSolids(p.material,p.surfaces)
                val host=if(index%5%2==0) "minecraft:bricks" else "minecraft:white_terracotta"
                assertEquals(setOf(host,"minecraft:quartz_block"),solids.keys)
                val relief=solids.getValue("minecraft:quartz_block")
                val body=solids.getValue(host)
                assertTrue(relief.boxes().minOf { it.z }<body.boxes().minOf { it.z },"The quartz ornament must project out of the north facade")
                assertTrue((4..11).all { x -> (4..11).all { y -> relief.has(x,y,11) } },"The raised quartz square must cover its full 8 x 8 face")
            }
            val trim=plan.placements.filter { it.component=="trim" && it.pos.y in storey.base..storey.base+4 }
            assertTrue(trim.isNotEmpty(),"Missing geometric corner trim on floor ${storey.level}")
            assertTrue(trim.all { Geometry.solid(it.parts).count() in 1 until 4096 },"Corner trim must have its own relief geometry")
        }
    }

    @Test fun `complete plan has unique positions valid geometry exact quantities and no obstructed interiors`() {
        assertTrue(plan.warnings.isEmpty(),plan.warnings.joinToString())
        assertEquals(plan.placements.size,byPosition.size)
        assertEquals(plan.clear.size,plan.clear.distinct().size)
        assertTrue(plan.clear.none { it in byPosition })
        assertTrue(plan.placements.isNotEmpty())
        val bill=linkedMapOf<String,Int>()
        for(p in plan.placements) {
            assertNotEquals("minecraft:air",p.block)
            p.surfaceItems().forEach { (id,count) -> bill[id]=(bill[id] ?: 0)+count }
            if(p.block.isEmpty()) {
                val geometry=Geometry.resolve(p.parts,p.groups)
                assertTrue(geometry.solid.count()>0,"Empty printed block at ${p.pos}")
                assertEquals(geometry.solid.count(),geometry.owned.values.sumOf { it.count() })
                assertTrue(geometry.materialSolids(p.material,p.surfaces).keys.none { it.isBlank() || it=="minecraft:air" })
            }
        }
        assertEquals(bill,plan.materials)
        assertEquals(plan.raw,plan.placements.filter { it.block.isEmpty() }.sumOf { Extrusion.cost(it.parts,it.groups) })
        assertEquals(17,plan.placements.maxOf { it.pos.x }-plan.placements.minOf { it.pos.x }+1)
        assertEquals(17,plan.placements.maxOf { it.pos.z }-plan.placements.minOf { it.pos.z }+1)
        assertTrue(plan.placements.any { it.component=="roof" })
    }

    data class Bundle(val forms:Map<String,Form>,val modules:Map<String,Module>,val buildings:Map<String,Building>)
    private val gson=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private fun fixtures():Map<String,Any> = buildMap {
        put("building.json",example.building)
        put("library.json",Bundle(example.forms.toSortedMap(),example.modules.toSortedMap(),mapOf("${example.building.id}@${example.building.version}" to example.building)))
        example.forms.toSortedMap().values.forEach { put("forms/${it.id}.json",it) }
        example.modules.toSortedMap().values.forEach { put("modules/${it.id}.json",it) }
        put("material-bill.json",mapOf("positions" to plan.placements.size,"usableInteriorCells" to 1875,"zincMillibuckets" to plan.raw,"surfaceItems" to plan.materials.toSortedMap()))
    }
    private fun exportIfRequested() {
        if(System.getenv("ARCHITECT_WRITE_T_BUILDING_EXAMPLE")!="true") return
        fixtures().forEach { (path,value) -> File("examples/t-building/$path").apply { parentFile.mkdirs();writeText(gson.toJson(value)+"\n") } }
    }

    @Test fun `saving the exported T building compresses its full command and preserves all 1902 placements`() {
        exportIfRequested()
        val exported=Json.decode<Building>(File("examples/t-building/building.json").readText())
        assertEquals(example.building,exported)
        // Match the editor's save action: the escaped inner JSON and all command fields count too.
        val command=Command("saveBuilding",Json.encode(exported))
        val requestJson=Json.encode(command)
        assertTrue(requestJson.length>CommandTransport.MAX_WIRE_CHARS,"This real save must exercise the compressed transport path")
        assertTrue(requestJson.length<=CommandTransport.MAX_JSON_CHARS)
        val wire=CommandTransport.encode(requestJson)
        assertTrue(wire.startsWith("gzip:"))
        assertTrue(wire.length<=CommandTransport.MAX_WIRE_CHARS)
        val receivedJson=CommandTransport.decode(wire)
        assertEquals(requestJson,receivedJson)
        val restoredCommand=Json.decode<Command>(receivedJson)
        assertEquals(command,restoredCommand)
        val restored=Json.decode<Building>(restoredCommand.payload)
        assertEquals(exported,restored)
        assertEquals(graph,restored.graph)
        val restoredPlan=Generator.generate(restored,example.forms,example.modules)
        assertEquals(1902,restoredPlan.placements.size)
        assertEquals(plan,restoredPlan)
    }

    @Test fun `reusable JSON bundle imports the exact graph forms and complete plan`() {
        exportIfRequested()
        val directory=File("examples/t-building")
        val bundle=gson.fromJson(File(directory,"library.json").readText(),Bundle::class.java)
        val building=bundle.buildings.getValue("${example.building.id}@${example.building.version}")
        assertEquals(example.forms,bundle.forms.mapValues { Geometry.validate(it.value) })
        assertEquals(example.modules,bundle.modules)
        assertEquals(example.building,building)
        assertEquals(plan,Generator.generate(building,bundle.forms,bundle.modules))
        val standalone=gson.fromJson(File(directory,"building.json").readText(),Building::class.java)
        assertEquals(building,standalone)
        assertEquals(plan,Generator.generate(standalone,bundle.forms,bundle.modules))
        bundle.forms.values.forEach { form ->
            assertEquals(form,gson.fromJson(File(directory,"forms/${form.id}.json").readText(),Form::class.java))
        }
        fixtures().forEach { (path,value) -> assertEquals(gson.toJson(value)+"\n",File(directory,path).readText(),"Stale reproducible fixture: $path") }
    }
}
