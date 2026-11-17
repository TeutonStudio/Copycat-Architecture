package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import kotlin.test.*

class CopycatTransformContractTest {
    private val byte=Part("copycats:copycat_byte")
    private fun graph(vararg nodes:GraphNode)=NodeGraph(schema=4,nodes=nodes.toList()+GraphNode("result",NodeKind.OUTPUT,listOf(nodes.last().id)))
    private fun geometry(graph:NodeGraph)=Geometry.solid(GraphCompiler.compile(graph)).key()
    @Test fun `inline order equals a chain and retains receipts and source`() {
        val steps=listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=P(2,0,0)),CopycatTransform(axis=RotationAxis.Y,quarterTurns=1),CopycatTransform(CopycatTransformKind.MIRROR,plane=CopycatMirrorPlane.Z))
        val source=GraphNode("source",part=byte)
        val inline=graph(source.copy(copycatTransforms=steps))
        val chain=graph(source,GraphNode("move",NodeKind.TRANSLATE,listOf("source",""),translation=P(2,0,0)),GraphNode("rotate",NodeKind.ROTATE,listOf("move",""),rotationAxis=RotationAxis.Y,quarterTurns=1),GraphNode("mirror",NodeKind.MIRROR,listOf("rotate",""),mirror=CopycatMirror(CopycatMirrorPlane.Z)))
        assertEquals(geometry(chain),geometry(inline));assertEquals(byte,source.part)
        assertEquals(Extrusion.cost(GraphCompiler.compile(chain)),Extrusion.cost(GraphCompiler.compile(inline)))
        assertEquals(geometry(inline),geometry(Json.decode<NodeGraph>(Json.encode(inline))))
    }
    @Test fun `pivot is in hB and disabled steps are identity`() {
        val rotated=CopycatTransforms.apply(listOf(byte),listOf(CopycatTransform(axis=RotationAxis.Z,pivot=P(4,4,4))))
        assertEquals(Geometry.solid(listOf(byte)).key(),Geometry.solid(rotated).key())
        assertEquals(listOf(byte),CopycatTransforms.apply(listOf(byte),listOf(CopycatTransform(CopycatTransformKind.POSITION,false,offset=P(-16,0,0)))))
    }
    @Test fun `bounds are checked after each step without modifying a source`() {
        assertFailsWith<IllegalArgumentException> { CopycatTransforms.apply(listOf(byte),listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=P(-1,0,0)),CopycatTransform(CopycatTransformKind.POSITION,offset=P(1,0,0)))) }
        assertFailsWith<IllegalArgumentException> { CopycatTransforms.apply(listOf(byte),listOf(CopycatTransform(pivot=P(0,0,0)))) }
        assertEquals(512,Solid.part(byte).count())
    }
    @Test fun `position and mirroring values are typed and optional`() {
        val source=GraphNode("source",part=byte)
        val position=GraphNode("position",NodeKind.POSITION,translation=P(2,1,0))
        val actual=graph(source,position,GraphNode("move",NodeKind.TRANSLATE,listOf("source","position")))
        assertEquals(geometry(graph(source,GraphNode("move",NodeKind.TRANSLATE,listOf("source",""),translation=P(2,1,0)))),geometry(actual))
        assertFailsWith<IllegalArgumentException> { GraphCompiler.structure(actual.copy(nodes=actual.nodes.map { if(it.id=="position") it.copy(kind=NodeKind.ROTATION) else it }),DEFAULT_GROUPS) }
    }
    @Test fun `exact transformed cuts preserve rational surfaces and material groups`() {
        val roof=Part(RoofPartParameters.SOURCE,roof=RoofPartParameters(rise=2,run=3))
        val transformed=CopycatTransforms.apply(listOf(roof),listOf(CopycatTransform(axis=RotationAxis.Y,quarterTurns=1)))
        assertTrue(ExactGeometry.usesExact(transformed.single()))
        assertEquals(Extrusion.cost(listOf(roof)),Extrusion.cost(transformed))
    }
    @Test fun `duplicate keeps internal edges clears external ones and preserves source`() {
        val original=graph(GraphNode("external",part=byte),GraphNode("rotate",NodeKind.ROTATE,listOf("external",""),rotationAxis=RotationAxis.Y,quarterTurns=1))
        val one=GraphDuplicates.shape(original,DEFAULT_GROUPS,listOf("rotate"),listOf("copy"))
        assertEquals(listOf("",""),one.nodes.last().inputs);assertEquals(32,one.nodes.last().x)
        val both=GraphDuplicates.shape(original,DEFAULT_GROUPS,listOf("external","rotate"),listOf("copy1","copy2"))
        assertEquals("copy1",both.nodes.last().inputs[0]);assertEquals(original.nodes,both.nodes.take(original.nodes.size))
        assertFailsWith<IllegalArgumentException> { GraphDuplicates.shape(original,DEFAULT_GROUPS,listOf("result"),listOf("copy")) }
    }
    @Test fun `building instances keep source versions and own transforms independent`() {
        val form=Form(id="source",version=3,parts=listOf(byte))
        val source=BuildingNode("source",BuildingKind.COPYCAT,form="source@3")
        val transformed=source.copy(id="other",copycatTransforms=listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=P(2,0,0))))
        val value=BuildingNode("value",BuildingKind.POSITION,translation=P(2,0,0))
        val operation=BuildingNode("move",BuildingKind.COPYCAT_POSITION,inputs=listOf("source"),positionValue="value")
        val graph=BuildingGraph(schema=5,nodes=listOf(BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1),source,transformed,value,operation,BuildingNode("result",BuildingKind.BUILDING)))
        BuildingGraphCompiler.structure(graph)
        val evaluation=BuildingGraphCompiler.Evaluation(graph,Building().palette,mapOf("source@3" to form),emptyMap())
        val a=evaluation.preview("other");val b=evaluation.preview("move");val original=evaluation.preview("source")
        assertEquals(Geometry.solid(a.placements.single().parts).key(),Geometry.solid(b.placements.single().parts).key())
        assertNotEquals(Geometry.solid(original.placements.single().parts).key(),Geometry.solid(a.placements.single().parts).key())
        assertEquals(listOf(byte),form.parts);assertEquals("source@3",source.form);assertEquals(a.materials,b.materials);assertEquals(a.raw,b.raw)
    }
    @Test fun `building reference rotations retain clipped geometry through previews and downstream transforms`() {
        val groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",1))
        val cutters=listOf(Part("copycats:copycat_byte"),
            Part(RoofPartParameters.SOURCE,exact=ExactVolume.convex(listOf(ExactPlane(0,3,-2,1)))))
        for(cutter in cutters) {
            val form=Geometry.validate(Form(id="cut",version=3,groups=groups,graph=NodeGraph(nodes=listOf(
                GraphNode("source",part=Part("copycats:copycat_slab")),GraphNode("cutter",part=cutter),
                GraphNode("body",NodeKind.DIFFERENCE,listOf("source","cutter")),
                GraphNode("accent",NodeKind.INTERSECTION,listOf("source","cutter")),
                GraphNode("join",NodeKind.UNION,listOf("body","accent"),inputGroups=listOf("body","accent")),
                GraphNode("result",NodeKind.OUTPUT,listOf("join"))))))
            val original=Json.encode(form)
            assertTrue(form.parts.any { it.cutMask.isNotEmpty() || it.exactClip!=null })
            // The document boundary must still reject orphaned clipping data.
            assertFailsWith<IllegalArgumentException> { Geometry.validate(form.copy(graph=null)) }
            for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) {
                val step=CopycatTransform(axis=axis,quarterTurns=1)
                val source=BuildingNode("source",BuildingKind.COPYCAT,form="cut@3")
                val inline=source.copy(id="inline",copycatTransforms=listOf(step))
                val value=BuildingNode("value",BuildingKind.ROTATION,rotationAxis=axis,quarterTurns=1)
                val rotated=BuildingNode("rotate",BuildingKind.COPYCAT_ROTATE,inputs=listOf(source.id),rotation="value")
                val mirrored=BuildingNode("mirror",BuildingKind.COPYCAT_MIRROR,inputs=listOf(rotated.id),mirror=CopycatMirror(CopycatMirrorPlane.Z))
                val segment=BuildingNode("segment",BuildingKind.SEGMENT,module="",inputs=listOf(mirrored.id),length=1,height=1)
                val graph=BuildingGraph(schema=5,nodes=listOf(
                    BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1),
                    source,inline,value,rotated,mirrored,segment,BuildingNode("result",BuildingKind.BUILDING)))
                val evaluation=BuildingGraphCompiler.Evaluation(Json.decode(Json.encode(graph)),Building().palette,mapOf("cut@3" to form),emptyMap())
                val before=evaluation.preview(source.id)
                val expected=CopycatTransforms.apply(form.parts,listOf(step))
                for(id in listOf(inline.id,rotated.id)) {
                    val plan=evaluation.preview(id);val placement=plan.placements.single()
                    assertTrue(Geometry.exact(expected).equivalent(Geometry.exact(placement.parts)))
                    assertEquals(groups,placement.groups)
                    assertEquals(before.placements.single().surfaces,placement.surfaces)
                    assertEquals(before.raw,plan.raw);assertEquals(before.materials,plan.materials)
                    val owned=Geometry.resolve(expected,groups).exactMaterials("",groups.associate { it.id to it.id })
                    val actual=Geometry.resolve(placement.parts,groups).exactMaterials("",groups.associate { it.id to it.id })
                    assertEquals(owned.keys,actual.keys)
                    owned.forEach { (group,volume) -> assertTrue(volume.equivalent(actual.getValue(group))) }
                }
                val chained=CopycatTransforms.apply(expected,listOf(CopycatTransform(CopycatTransformKind.MIRROR,plane=CopycatMirrorPlane.Z)))
                val plan=evaluation.preview(segment.id)
                assertTrue(Geometry.exact(chained).equivalent(Geometry.exact(plan.placements.single().parts)))
                assertEquals(before.raw,plan.raw);assertEquals(before.materials,plan.materials)
                assertEquals(original,Json.encode(form))
                assertEquals("cut@3",source.form)
            }
        }
    }
    @Test fun `new functions require their explicit schemas while legacy graphs retain theirs`() {
        val legacy=graph(GraphNode("source",part=byte)).copy(schema=2)
        assertEquals(2,legacy.withTransformSchema().schema)
        val modern=legacy.copy(nodes=legacy.nodes.map {if(it.id=="source") it.copy(copycatTransforms=listOf(CopycatTransform(quarterTurns=0))) else it})
        assertFails {GraphCompiler.structure(modern,DEFAULT_GROUPS)}
        assertEquals(4,modern.withTransformSchema().schema)
        assertEquals(geometry(legacy),geometry(modern.withTransformSchema()))
    }

    @Test fun `shared group definitions retain independent Copycat transform lists`() {
        val source=GraphNode("source",part=byte)
        val base=graph(source).copy(nodeGroups=listOf(GraphGroup("group","Group",listOf("source"))))
        val instanced=GraphGroups.instantiate(base,"group")
        val copy=instanced.nodes.first {it.id !in setOf("source","result")}
        val step=listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=P(1,0,0)))
        val changed=instanced.replace(copy.copy(copycatTransforms=step))
        GraphCompiler.structure(changed,DEFAULT_GROUPS)
        assertNull(changed.nodes.first {it.id=="source"}.copycatTransforms)
        assertEquals(step,changed.nodes.first {it.id==copy.id}.copycatTransforms)
        val definition=changed.replace(changed.nodes.first {it.id=="source"}.copy(part=byte.copy(offset=P(0,1,0))))
        assertEquals(P(0,1,0),definition.nodes.first {it.id==copy.id}.part.offset)
        assertEquals(step,definition.nodes.first {it.id==copy.id}.copycatTransforms)
        val building=BuildingGraph(schema=5,nodes=listOf(BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1),BuildingNode("source",BuildingKind.COPYCAT),BuildingNode("result",BuildingKind.BUILDING)),nodeGroups=listOf(GraphGroup("group","Group",listOf("source"))))
        val instances=GraphGroups.instantiate(building,"group")
        val other=instances.nodes.first {it.id !in setOf("empty_wall","source","result")}
        val independent=instances.replace(other.copy(copycatTransforms=step))
        BuildingGraphCompiler.structure(independent)
        assertNull(independent.nodes.first {it.id=="source"}.copycatTransforms)
        val renamed=independent.replace(independent.nodes.first {it.id=="source"}.copy(name="Definition"))
        assertEquals("Definition",renamed.nodes.first {it.id==other.id}.name)
        assertEquals(step,renamed.nodes.first {it.id==other.id}.copycatTransforms)
    }

    @Test fun `material requirements use native quantities and registry identities`() {
        val groups=listOf(MaterialGroup("a","A",priority=0),MaterialGroup("b","B",priority=1))
        val parts=listOf(byte.copy(group="a"),byte.copy(group="b",offset=P(8,0,0)))
        assertEquals(mapOf("minecraft:oak_log" to 1),MaterialRequirements.form(parts,groups,mapOf("a" to "minecraft:oak_log[axis=x]","b" to "minecraft:oak_log[axis=z]")))
        assertEquals(mapOf("minecraft:oak_log" to 3),MaterialRequirements.aggregate(mapOf("minecraft:oak_log[axis=x]" to 1,"minecraft:oak_log[axis=z]" to 2)))
    }

}
