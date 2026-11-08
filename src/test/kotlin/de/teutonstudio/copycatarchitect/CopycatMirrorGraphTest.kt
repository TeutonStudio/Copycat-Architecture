package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CopycatMirrorGraphTest {
    private val full=Part("copycats:copycat_block")
    private val slab=Part("copycats:copycat_slab")
    private val groups=listOf(MaterialGroup(),MaterialGroup("cap","Glass","glass",1))
    private fun graph(part:Part,plane:CopycatMirrorPlane?=null)=NodeGraph(nodes=listOf(
        GraphNode("source",part=part),GraphNode("mirror",NodeKind.MIRROR,listOf("source"),mirror=plane?.let(::CopycatMirror)),
        GraphNode("result",NodeKind.OUTPUT,listOf("mirror"))))
    private val voxelPlanes=listOf(CopycatMirrorPlane.X,CopycatMirrorPlane.Y,CopycatMirrorPlane.Z,
        CopycatMirrorPlane.XY,CopycatMirrorPlane.XY_NEGATIVE,CopycatMirrorPlane.XZ,CopycatMirrorPlane.XZ_NEGATIVE,
        CopycatMirrorPlane.YZ,CopycatMirrorPlane.YZ_NEGATIVE)

    @Test fun `axis and face diagonal mirror nodes perform the selected centered plane reflection`() {
        val source=Part("copycats:copycat_byte_panel",offset=P(1,2,3))
        val before=Solid.part(source)
        for(plane in voxelPlanes) {
            val after=Geometry.solid(GraphCompiler.compile(graph(source,plane)))
            assertEquals(before.count(),after.count(),plane.name)
            for(x in 0..15) for(y in 0..15) for(z in 0..15) {
                val expected=when(plane) {
                    CopycatMirrorPlane.X->P(15-x,y,z)
                    CopycatMirrorPlane.Y->P(x,15-y,z)
                    CopycatMirrorPlane.Z->P(x,y,15-z)
                    CopycatMirrorPlane.XY->P(15-y,15-x,z)
                    CopycatMirrorPlane.XY_NEGATIVE->P(y,x,z)
                    CopycatMirrorPlane.XZ->P(15-z,y,15-x)
                    CopycatMirrorPlane.XZ_NEGATIVE->P(z,y,x)
                    CopycatMirrorPlane.YZ->P(x,15-z,15-y)
                    CopycatMirrorPlane.YZ_NEGATIVE->P(x,z,y)
                    else->error("This reference table covers grid-preserving planes")
                }
                assertEquals(before.has(x,y,z),after.has(expected.x,expected.y,expected.z),"$plane at $x,$y,$z")
            }
        }
    }

    @Test fun `mirroring boolean intermediates preserves clipped ownership provenance and zinc cost`() {
        val initial=NodeGraph(nodes=listOf(GraphNode("source",part=full),GraphNode("mask",part=slab),
            GraphNode("lower",NodeKind.INTERSECTION,listOf("source","mask"),materialMode=MaterialMode.GROUPED,materialGroup="cap"),
            GraphNode("upper",NodeKind.DIFFERENCE,listOf("source","mask")),
            GraphNode("join",NodeKind.UNION,listOf("upper","lower")),
            GraphNode("mirror",NodeKind.MIRROR,listOf("join")),GraphNode("result",NodeKind.OUTPUT,listOf("mirror"))))
        val before=GraphCompiler.intermediate(initial,"join",groups)
        for(plane in voxelPlanes) {
            val changed=initial.replace(initial.nodes.single { it.id=="mirror" }.copy(mirror=CopycatMirror(plane)))
            val after=GraphCompiler.compile(changed,groups)
            assertEquals(setOf(full.source),after.map { it.source }.toSet())
            assertEquals(setOf("source"),after.map { it.recipeSource }.toSet())
            assertEquals(setOf("body","cap"),after.map { it.group }.toSet())
            assertEquals(Extrusion.cost(before,groups),Extrusion.cost(after,groups))
            val expected=Geometry.resolve(before,groups).owned
            val actual=Geometry.resolve(after,groups).owned
            expected.forEach { (group,solid) -> assertEquals(solid.reflected(plane).key(),actual.getValue(group).key(),"$plane/$group") }
            assertEquals(setOf("body","cap"),GraphCompiler.inputMaterialGroups(changed,changed.nodes.last(),0,groups))
        }
    }

    @Test fun `mirror keeps exact slopes and boolean clipping without restoring the cutter`() {
        val wedge=ExactVolume.convex(listOf(ExactPlane(0,3,-2,1)))
        val initial=NodeGraph(nodes=listOf(GraphNode("source",part=full),
            GraphNode("wedge",part=Part(RoofPartParameters.SOURCE,exact=wedge)),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf("source","wedge")),
            GraphNode("mirror",NodeKind.MIRROR,listOf("cut"),mirror=CopycatMirror(CopycatMirrorPlane.XY)),
            GraphNode("result",NodeKind.OUTPUT,listOf("mirror"))))
        val before=GraphCompiler.intermediate(initial,"cut")
        val after=GraphCompiler.compile(initial)
        assertEquals(setOf(full.source),after.map { it.source }.toSet())
        assertTrue(after.any { it.exactClip!=null })
        assertTrue(Geometry.exact(after).equivalent(Geometry.exact(before).reflected(CopycatMirrorPlane.XY)))
        assertEquals(Extrusion.cost(before),Extrusion.cost(after))
        val restored=Geometry.validate(Gson().fromJson(Gson().toJson(Form(graph=initial)),Form::class.java))
        assertTrue(Geometry.exact(restored.parts).equivalent(Geometry.exact(after)))
    }

    @Test fun `body diagonal mirrors retain rational thirds and undo exactly by reflection twice`() {
        val centered=Part("copycats:copycat_byte",offset=P(4,4,4))
        for(plane in listOf(CopycatMirrorPlane.BODY_PPP,CopycatMirrorPlane.BODY_PPN,CopycatMirrorPlane.BODY_PNP,CopycatMirrorPlane.BODY_PNN)) {
            val initial=graph(centered,plane)
            val parts=GraphCompiler.compile(initial)
            assertTrue(parts.any(ExactGeometry::usesExact))
            val volume=Geometry.exact(parts)
            assertTrue(volume.cells.flatMap { it.vertices() }.any { point ->
                listOf(point.x,point.y,point.z).any { it.denominator.toString()=="3" }
            },"$plane must preserve rational third coordinates instead of a voxel approximation")
            assertTrue(volume.equivalent(Geometry.exact(centered).reflected(plane)))
            assertEquals(centered.source,parts.single().source)
            val twice=initial.copy(nodes=initial.nodes.dropLast(1)+GraphNode("again",NodeKind.MIRROR,listOf("mirror"),mirror=CopycatMirror(plane))+
                initial.nodes.last().copy(inputs=listOf("again")))
            assertTrue(Geometry.exact(GraphCompiler.compile(twice)).equivalent(Geometry.exact(centered)))
            val checked=Geometry.validate(Form(id="body_mirror",graph=initial))
            val restored=Geometry.validate(Gson().fromJson(Gson().toJson(checked),Form::class.java))
            assertEquals(checked,restored)
            val invalid=assertFails { GraphCompiler.compile(graph(full,plane)) }
            assertTrue(invalid.message.orEmpty().contains("bound",ignoreCase=true) || invalid.message.orEmpty().contains("cube",ignoreCase=true),invalid.message)
        }
    }

    @Test fun `unattached exact mirror drafts leave legacy voxel compilation unchanged`() {
        val legacy=NodeGraph.fromParts(listOf(slab))
        val draft=GraphNode("draft",NodeKind.MIRROR,listOf(""),mirror=CopycatMirror(CopycatMirrorPlane.BODY_PPP))
        assertEquals(GraphCompiler.compile(legacy),GraphCompiler.compile(legacy.copy(nodes=legacy.nodes+draft)))
    }

    @Test fun `mirror sockets reject invalid arity rotation values cycles and missing inputs`() {
        val valid=graph(slab)
        GraphCompiler.structure(valid,DEFAULT_GROUPS)
        assertEquals(GraphCompiler.compile(graph(slab,CopycatMirrorPlane.X)),GraphCompiler.compile(valid))
        val mirror=valid.nodes.single { it.id=="mirror" }
        for(inputs in listOf(emptyList(),listOf("source","source"),listOf("mirror"),listOf("result"),listOf("missing"))) {
            assertFails { GraphCompiler.structure(valid.replace(mirror.copy(inputs=inputs)),DEFAULT_GROUPS) }
        }
        assertFails { GraphCompiler.compile(valid.replace(mirror.copy(inputs=listOf("")))) }
        val rotation=GraphNode("angle",NodeKind.ROTATION)
        assertFails { GraphCompiler.structure(valid.copy(nodes=valid.nodes+rotation).replace(mirror.copy(inputs=listOf(rotation.id))),DEFAULT_GROUPS) }
        assertFails { GraphCompiler.structure(valid.replace(valid.nodes.first().copy(mirror=CopycatMirror())),DEFAULT_GROUPS) }
    }

    @Test fun `legacy JSON omits unused mirror configuration and mirror definitions synchronize across instances`() {
        val gson=Gson()
        val legacy=NodeGraph.fromParts(listOf(slab))
        val json=gson.toJson(legacy)
        assertFalse(json.contains("\"mirror\""))
        val restored=gson.fromJson(json,NodeGraph::class.java)
        assertEquals(legacy,restored)
        val grouped=GraphGroups.create(graph(slab),setOf("mirror"),"Mirror")
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val source=instanced.nodes.single { it.id=="mirror" }
        val changed=instanced.replace(source.copy(mirror=CopycatMirror(CopycatMirrorPlane.YZ_NEGATIVE)))
        assertTrue(changed.nodeGroups.all { group -> changed.nodes.single { it.id==group.nodes.single() }.mirror==source.copy(mirror=CopycatMirror(CopycatMirrorPlane.YZ_NEGATIVE)).mirror })
        assertEquals(changed,gson.fromJson(gson.toJson(changed),NodeGraph::class.java))
    }
}
