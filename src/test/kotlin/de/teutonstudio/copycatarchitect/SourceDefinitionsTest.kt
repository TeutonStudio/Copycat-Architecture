package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SourceDefinitionsTest {
    private val stairs=Part(SourceDefinitions.STAIRS_SOURCE)
    private val byteSource="copycats:copycat_byte"

    @Test fun `stairs definition is six disjoint bytes and retains the previous stair volume`() {
        val definition=assertNotNull(SourceDefinitions.definition(stairs))
        val graph=assertNotNull(definition.graph)
        val inputs=graph.nodes.filter { it.kind==NodeKind.INPUT }
        assertEquals(6,inputs.size)
        assertTrue(inputs.all { it.part.source==byteSource })
        assertEquals(1,graph.nodes.count { it.kind==NodeKind.UNION })
        assertEquals(1,graph.nodes.count { it.kind==NodeKind.OUTPUT })
        assertEquals(6,definition.parts.size)
        assertEquals(6,definition.parts.map { Solid.part(it).key() }.distinct().size)
        assertTrue(definition.parts.all { Solid.part(it).count()==512 })
        val solid=Geometry.solid(definition.parts)
        for(x in 0..15) for(y in 0..15) for(z in 0..15) {
            assertEquals(y<8 || z>=8,solid.has(x,y,z))
        }
        assertEquals(Solid.part(stairs).key(),solid.key())
        assertEquals(192,Extrusion.cost(definition.parts))
        assertNull(SourceDefinitions.definition(Part(byteSource)))
    }

    @Test fun `every saved stair frame expands into the same oriented bytes`() {
        for(top in listOf(false,true)) for(tilt in 0..3) for(turn in 0..3) for(spin in 0..3) {
            val source=stairs.copy(top=top,tilt=tilt,turn=turn,spin=spin,group="roof",recipeSource="shared")
            val expanded=SourceDefinitions.expand(source)
            assertEquals(6,expanded.size)
            assertTrue(expanded.all { it.source==byteSource && it.group=="roof" && it.recipeSource=="shared" })
            assertEquals(Solid.part(source).key(),Geometry.solid(expanded).key())
            val definition=assertNotNull(SourceDefinitions.definition(source))
            assertEquals(listOf("roof"),definition.groups.map { it.id })
            assertEquals(Solid.part(source).key(),Geometry.solid(definition.parts).key())
            assertTrue(definition.parts.all { it.recipeSource.isEmpty() })
        }
    }

    @Test fun `stairs world rotations preserve their source and complete four turns`() {
        val source=stairs.copy(turn=3,top=true,spin=2,group="roof",recipeSource="shared")
        for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) {
            var rotated=source
            repeat(4) { q ->
                rotated=assertNotNull(PartRotations.rotate(rotated,axis,1))
                assertEquals(Solid.part(source).rotate(axis,q+1).key(),Solid.part(rotated).key())
                assertEquals(source.source,rotated.source)
                assertEquals(source.group,rotated.group)
                assertEquals(source.recipeSource,rotated.recipeSource)
                assertEquals(Solid.part(rotated).key(),Geometry.solid(SourceDefinitions.expand(rotated)).key())
            }
            assertEquals(Solid.part(source).key(),Solid.part(rotated).key())
            assertEquals(PartRotations.normal(source),PartRotations.normal(rotated))
        }
    }

    @Test fun `boolean stair cuts rotate with their material and byte decomposition`() {
        val groups=listOf(MaterialGroup(),MaterialGroup("roof","Roof","surface",1))
        val cutter=Part(byteSource)
        val graph=NodeGraph(nodes=listOf(GraphNode("stairs",part=stairs.copy(group="roof")),GraphNode("cutter",part=cutter),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf("stairs","cutter")),
            GraphNode("rotate",NodeKind.ROTATE,listOf("cut",""),rotationAxis=RotationAxis.Z,quarterTurns=1),
            GraphNode("result",NodeKind.OUTPUT,listOf("rotate"))))
        val form=Geometry.validate(Form(id="rotated_stairs",graph=graph,groups=groups))
        val expected=Solid.part(stairs).minus(Solid.part(cutter)).rotate(RotationAxis.Z,1)
        assertEquals(expected.key(),Geometry.solid(form.parts).key())
        assertEquals(setOf("roof"),Geometry.resolve(form.parts,groups).owned.keys)
        assertEquals(160,Extrusion.cost(form.parts,groups))
        val part=form.parts.single()
        assertEquals(stairs.source,part.source)
        assertTrue(part.cutMask.isNotEmpty())
        val expanded=SourceDefinitions.expand(part)
        assertEquals(5,expanded.size)
        assertEquals(expected.key(),Geometry.solid(expanded).key())
        assertTrue(expanded.all { it.group=="roof" })
        val restored=Geometry.validate(Gson().fromJson(Gson().toJson(form),Form::class.java))
        assertEquals(form,restored)
    }

    @Test fun `shared stair branches preserve separate materials and charge the occupied volume once`() {
        val groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","surface",1))
        val graph=NodeGraph(nodes=listOf(GraphNode("stairs",part=stairs),GraphNode("slab",part=Part("copycats:copycat_slab")),
            GraphNode("bottom",NodeKind.INTERSECTION,listOf("stairs","slab"),materialMode=MaterialMode.GROUPED,materialGroup="cap"),
            GraphNode("top",NodeKind.DIFFERENCE,listOf("stairs","slab")),
            GraphNode("join",NodeKind.UNION,listOf("bottom","top")),
            GraphNode("rotate",NodeKind.ROTATE,listOf("join",""),rotationAxis=RotationAxis.Z,quarterTurns=3),
            GraphNode("result",NodeKind.OUTPUT,listOf("rotate"))))
        val parts=GraphCompiler.compile(graph,groups)
        val resolved=Geometry.resolve(parts,groups)
        assertEquals(Solid.part(stairs).rotate(RotationAxis.Z,3).key(),resolved.solid.key())
        assertEquals(2048,resolved.owned.getValue("cap").count())
        assertEquals(1024,resolved.owned.getValue("body").count())
        assertEquals(192,Extrusion.cost(parts,groups))
        val expanded=parts.flatMap(SourceDefinitions::expand)
        assertEquals(resolved.visualKey("minecraft:stone",mapOf("cap" to "minecraft:glass")),
            Geometry.resolve(expanded,groups).visualKey("minecraft:stone",mapOf("cap" to "minecraft:glass")))
        assertEquals(192,Extrusion.cost(expanded,groups))
    }

    @Test fun `old saved stairs keep their source and invalid composed states are rejected`() {
        val gson=Gson()
        for(top in listOf(false,true)) for(turn in 0..3) {
            val json="""{"schema":2,"id":"old_stairs","name":"Old stairs","parts":[{"source":"copycats:copycat_stairs","turn":$turn,"top":$top}]}"""
            val restored=Geometry.validate(gson.fromJson(json,Form::class.java))
            assertEquals(listOf(stairs.copy(turn=turn,top=top)),restored.parts)
            assertEquals(Solid.part(stairs.copy(turn=turn,top=top)).key(),Geometry.solid(restored.parts).key())
        }
        assertFails { SourceDefinitions.expand(stairs.copy(tilt=4)) }
        assertFails { SourceDefinitions.expand(stairs.copy(size=2)) }
        assertFails { SourceDefinitions.expand(stairs.copy(offset=P(1,0,0))) }
        assertFails { SourceDefinitions.expand(stairs.copy(cutMask=Solid.part(Part("copycats:copycat_block")).mask())) }
    }
}
