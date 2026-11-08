package de.teutonstudio.copycatarchitect

import com.google.gson.GsonBuilder
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.*

/** Checks the printable graph, then exports views from its actual resolved material surfaces. */
class CommerceQuarterExampleTest {
    private val example=CommerceQuarterExample
    private val graph get()=assertNotNull(example.building.graph)
    private val compiled by lazy { BuildingGraphCompiler.compile(example.building,example.forms,example.modules) }
    private val plan get()=compiled.plan
    private val byPosition by lazy { plan.placements.associateBy { it.pos } }
    private val pretty=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val compact=GsonBuilder().disableHtmlEscaping().create()
    private val directory=File("examples/commerce-quarter")

    @Test fun `native plan keeps a five block street and two inhabited storeys with a physical stair`() {
        assertEquals(listOf(0,1),compiled.storeys.take(2).map { it.level })
        assertEquals(listOf(1,7),compiled.storeys.take(2).map { it.base })
        assertEquals(1,graph.nodes.count { it.kind==BuildingKind.STAIRWELL })
        assertTrue(plan.placements.any { it.component=="stairs" })
        assertTrue(plan.placements.any { it.component=="landing" })
        assertTrue(compiled.accessFootprints.isNotEmpty())
        for(x in 5..6) for(y in 1..2) assertTrue(P(x,y,0) !in byPosition,"The inn entrance is obstructed")
        for(x in 24..25) for(y in 1..2) assertTrue(P(x,y,0) !in byPosition,"The shop entrance is obstructed")
        for(z in 9..10) for(y in 1..2) assertTrue(P(12,y,z) !in byPosition,"The inn's lane entrance is obstructed")
        for(z in 13..14) for(y in 1..2) assertTrue(P(18,y,z) !in byPosition,"The shop's lane entrance is obstructed")
        // A route through the complete site remains open at a player's full standing height.
        for(x in 13..17) for(z in 0..23) for(y in 1..3)
            assertTrue(P(x,y,z) !in byPosition,"The five-block street is obstructed at $x,$y,$z")
        val access=graph.nodes.single { it.kind==BuildingKind.BUILDING }.accessInstances
        assertEquals(1,access.size)
        // Chimney construction levels must never silently extend the occupied stair range.
        assertTrue(access.single().toStorey.isNotEmpty(),"The stair must stop at the inhabited upper floor")
    }

    @Test fun `furniture leaves connected standing routes between doors and the stair landings`() {
        fun reachable(start:P,target:P,rectangle:Rect):Boolean {
            fun free(p:P)=p.x in rectangle.x until rectangle.x+rectangle.width && p.z in rectangle.z until rectangle.z+rectangle.length &&
                p !in byPosition && p+P(0,1,0) !in byPosition
            val visited=mutableSetOf<P>();val pending=ArrayDeque<P>()
            if(!free(start) || !free(target)) return false
            pending.add(start);visited.add(start)
            while(pending.isNotEmpty()) {
                val at=pending.removeFirst()
                if(at==target) return true
                for(direction in RoomGeometry.directions) {
                    val next=at+direction
                    if(free(next) && visited.add(next)) pending.add(next)
                }
            }
            return false
        }
        assertTrue(reachable(P(5,1,1),P(11,1,9),Rect(1,1,11,13)),"Inn furniture disconnects the lane entrance")
        assertTrue(reachable(P(24,1,1),P(25,1,13),Rect(19,1,13,22)),"Shop furniture disconnects the lower stair landing")
        assertTrue(reachable(P(25,7,13),P(21,7,2),Rect(19,1,13,22)),"Upper furniture disconnects the stair landing")
        val arches=example.forms.values.filter { "Bogen" in it.name }
        assertEquals(2,arches.size)
        assertTrue(arches.all { form->form.parts.any(ExactGeometry::usesExact) && Geometry.solid(form.parts).count() in 1 until 4096 })
    }

    @Test fun `framed glass remains transparent through the full depth of every facade`() {
        var checked=0
        for(p in plan.placements.filter { "minecraft:glass" in it.surfaceItems() }) {
            val materials=Geometry.resolve(p.parts,p.groups).materialSolids(p.material,p.surfaces)
            val glass=materials.getValue("minecraft:glass")
            val opaque=materials.filterKeys { it!="minecraft:glass" }.values.fold(Solid()) { a,b->a.union(b) }
            assertTrue(glass.count()>0)
            assertTrue(opaque.count()>0,"A window needs a physical frame at ${p.pos}")
            fun rays(axis:Int):Int=(0..15).sumOf { tangent -> (0..15).count { y ->
                val ray=(0..15).map { normal -> if(axis==0) P(tangent,y,normal) else P(normal,y,tangent) }
                ray.any { glass.has(it.x,it.y,it.z) } && ray.none { opaque.has(it.x,it.y,it.z) }
            } }
            assertTrue(maxOf(rays(0),rays(1))>=36,"Opaque backing closes the window at ${p.pos}")
            checked++
        }
        assertTrue(checked>=24,"Both wings and the upper floor need real windows")
    }

    @Test fun `printed cells and explicit air never overlap and all materials have real volume`() {
        assertTrue(plan.warnings.isEmpty(),plan.warnings.joinToString())
        assertEquals(plan.placements.size,byPosition.size)
        assertEquals(plan.clear.size,plan.clear.distinct().size)
        assertTrue(plan.clear.none { it in byPosition })
        assertTrue(plan.placements.any { it.component=="roof" })
        val bill=linkedMapOf<String,Int>()
        for(p in plan.placements) {
            assertNotEquals("minecraft:air",p.block)
            p.surfaceItems().forEach { (material,count)->bill[material]=(bill[material] ?: 0)+count }
            if(p.block.isEmpty()) {
                val resolved=Geometry.resolve(p.parts,p.groups)
                assertTrue(resolved.solid.count()>0,"An empty printed cell is present at ${p.pos}")
                // Exact material boundaries can share a conservative collision voxel.
                assertEquals(resolved.solid.key(),resolved.owned.values.fold(Solid()) { a,b->a.union(b) }.key())
            }
        }
        assertEquals(bill,plan.materials)
        assertEquals(plan.raw,plan.placements.filter { it.block.isEmpty() }.sumOf { Extrusion.cost(it.parts,it.groups) })
        assertTrue(plan.materials.keys.any { "andesite" in it },"The reference's stone character needs an actual andesite surface")
    }

    data class Bundle(val forms:Map<String,Form>,val modules:Map<String,Module>,val buildings:Map<String,Building>)
    data class SceneMesh(val material:String,val component:String,val pos:List<Int>,val vertices:List<List<Double>>,val faces:List<List<Int>>)
    private data class MeshBuffers(val vertices:MutableList<List<Double>> = mutableListOf(),val faces:MutableList<List<Int>> = mutableListOf())

    /** Each polygon comes directly from the same exact geometry used by the native preview. */
    private fun scene():Map<String,Any> {
        val surfaceCache=mutableMapOf<String,List<OwnedExactSurface>>()
        val meshes=plan.placements.flatMap { p ->
            val resolved=if(p.block.isNotEmpty()) Geometry.resolve(listOf(Part("copycats:copycat_block"))) else Geometry.resolve(p.parts,p.groups)
            val groups=if(p.block.isNotEmpty()) DEFAULT_GROUPS else p.groups
            val transparent=groups.filter { (p.surfaces[it.id] ?: p.material).contains("glass") }.map { it.id }.toSet()
            // Group IDs are part of the cache identity: equal material geometry can still
            // use different ownership names, which must resolve against this placement.
            val key=if(p.block.isNotEmpty()) "full_block" else compact.toJson(listOf(p.parts,p.groups,transparent))
            val surfaces=surfaceCache.getOrPut(key) { resolved.visibleSurfaces(transparent) }
            val buffers=linkedMapOf<String,MeshBuffers>()
            for(owned in surfaces) {
                val material=if(p.block.isNotEmpty()) p.block else p.surfaces[owned.group] ?: p.material
                val buffer=buffers.getOrPut(material) { MeshBuffers() }
                val first=buffer.vertices.size
                buffer.vertices+=owned.surface.vertices.map { vertex -> listOf(
                    p.pos.x+vertex.x.toDouble()/16.0,
                    p.pos.y+vertex.y.toDouble()/16.0,
                    p.pos.z+vertex.z.toDouble()/16.0
                ) }
                buffer.faces+=(owned.surface.vertices.indices.map { first+it })
            }
            buffers.map { (material,buffer)->SceneMesh(material,p.component,listOf(p.pos.x,p.pos.y,p.pos.z),buffer.vertices,buffer.faces) }
        }
        return linkedMapOf(
            "schema" to 1,
            "name" to example.building.name,
            "source" to "CommerceQuarterExample / BuildingGraphCompiler",
            "units" to "Minecraft blocks; 16 native geometry units per block",
            "axes" to mapOf("x" to "east","y" to "up","z" to "south"),
            "provenance" to "All building, interior, roof and site polygons are native Plan placement geometry. Lighting and background are presentation only.",
            "positions" to plan.placements.size,
            "bounds" to mapOf(
                "min" to listOf(plan.placements.minOf { it.pos.x },plan.placements.minOf { it.pos.y },plan.placements.minOf { it.pos.z }),
                "max" to listOf(plan.placements.maxOf { it.pos.x }+1,plan.placements.maxOf { it.pos.y }+1,plan.placements.maxOf { it.pos.z }+1)
            ),
            "clear" to plan.clear.map { listOf(it.x,it.y,it.z) },
            "storeys" to compiled.storeys,
            "accessFootprints" to compiled.accessFootprints.mapValues { (_,points)->points.map { listOf(it.x,it.y,it.z) } },
            "rooms" to graph.nodes.filter { it.kind==BuildingKind.ROOM }.map { n ->
                val room=graph.resolvedNode(n)
                mapOf("id" to room.id,"name" to room.name,"height" to room.height,"rectangles" to room.rectangles)
            },
            "cameras" to listOf(
                mapOf("id" to "exterior_overview","position" to listOf(43.0,29.0,-35.0),"target" to listOf(16.0,5.5,11.0)),
                mapOf("id" to "exterior_street","position" to listOf(15.5,4.0,-10.0),"target" to listOf(15.5,5.0,13.0)),
                mapOf("id" to "exterior_court","position" to listOf(-17.0,18.0,36.0),"target" to listOf(12.0,5.0,15.0)),
                mapOf("id" to "interior_inn","position" to listOf(2.0,3.2,11.0),"target" to listOf(9.0,2.6,4.0)),
                mapOf("id" to "interior_workshop","position" to listOf(20.5,3.2,3.0),"target" to listOf(28.0,2.5,17.0)),
                mapOf("id" to "interior_upper","position" to listOf(20.5,9.2,3.0),"target" to listOf(28.0,8.5,17.0))
            ),
            "meshes" to meshes
        )
    }

    private fun fixtures():Map<String,Any> = buildMap {
        put("building.json",example.building)
        put("library.json",Bundle(example.forms.toSortedMap(),example.modules.toSortedMap(),mapOf("${example.building.id}@${example.building.version}" to example.building)))
        example.forms.toSortedMap().values.forEach { put("forms/${it.id}.json",it) }
        example.modules.toSortedMap().values.forEach { put("modules/${it.id}.json",it) }
        put("material-bill.json",mapOf("positions" to plan.placements.size,"zincMillibuckets" to plan.raw,"surfaceItems" to plan.materials.toSortedMap(),"storeys" to compiled.storeys))
    }

    @Test fun `exported graph and exact render scene reproduce the native plan`() {
        val expected=fixtures()
        if(System.getenv("ARCHITECT_WRITE_COMMERCE_QUARTER_EXAMPLE")=="true") {
            expected.forEach { (path,value)->File(directory,path).apply { parentFile.mkdirs();writeText(pretty.toJson(value)+"\n") } }
            File(directory,"scene.json").writeText(compact.toJson(scene())+"\n")
        }
        val bundle=pretty.fromJson(File(directory,"library.json").readText(),Bundle::class.java)
        val restored=bundle.buildings.getValue("${example.building.id}@${example.building.version}")
        assertEquals(example.building,restored)
        assertEquals(example.forms,bundle.forms.mapValues { Geometry.validate(it.value) })
        assertEquals(example.modules,bundle.modules)
        assertEquals(plan,Generator.generate(restored,bundle.forms,bundle.modules))
        expected.forEach { (path,value)->assertEquals(pretty.toJson(value)+"\n",File(directory,path).readText(),"Stale reproducible fixture: $path") }
        val sceneFile=File(directory,"scene.json")
        assertTrue(sceneFile.isFile,"The actual native surface export is required for the views")
        val exportedScene=compact.fromJson(sceneFile.readText(),Map::class.java)
        assertEquals(plan.placements.size,(exportedScene["positions"] as Number).toInt())
        assertTrue((exportedScene["meshes"] as List<*>).isNotEmpty())
    }
}
