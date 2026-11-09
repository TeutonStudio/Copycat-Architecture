package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SegmentMirrorGraphTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+nodes) }
    private fun eval(graph:BuildingGraph,definitions:Map<String,Form> = forms)=BuildingGraphCompiler.Evaluation(graph,Building().palette,definitions,emptyMap())
    private fun mirror(id:String,source:String="",axis:RotationAxis=RotationAxis.X)=
        BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,id).copy(inputs=listOf(source),rotationAxis=axis)
    private fun block(id:String,material:String)=BuildingNode(id,BuildingKind.SEGMENT,module="",block=material,length=1,height=2,depth=2)

    @Test fun `mirror factory exposes one fixed typed socket and validates arity types and cycles`() {
        val node=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"mirror")
        assertEquals(listOf(""),node.inputs)
        assertEquals(RotationAxis.X,node.rotationAxis)
        assertEquals(BuildingKind.PATH.ordinal+1,BuildingKind.SEGMENT_MIRROR.ordinal)
        val accepts=(setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds)
        assertEquals(listOf(BuildingPort("in:0","",accepts),BuildingPort("mirroring","",setOf(BuildingKind.MIRRORING))),BuildingPorts.of(node,true))
        val graph=graph(node)
        BuildingGraphCompiler.structure(graph)
        assertContains(assertFails { eval(graph).preview(node.id) }.message.orEmpty(),"Connect the segment")
        for(inputs in listOf(emptyList(),listOf("wall","wall")))
            assertFailsWith<IllegalArgumentException> { BuildingGraphCompiler.structure(graph.replace(node.copy(inputs=inputs))) }
        assertFailsWith<IllegalArgumentException> { graph.connect(node.id,"in:0","floor") }
        assertFailsWith<IllegalArgumentException> { graph.connect(node.id,"in:1","wall") }
        assertFailsWith<IllegalArgumentException> { graph.connect(node.id,"in:0",node.id) }
        assertFails { BuildingGraphCompiler.structure(graph.replace(node.copy(inputs=listOf("missing")))) }
        assertFailsWith<IllegalArgumentException> { BuildingGraphCompiler.structure(graph.replace(node.copy(rotationAxis=RotationAxis.NORMAL))) }
        val connected=graph.connect(node.id,"in:0","wall")
        assertEquals(listOf("wall"),connected.nodes.single { it.id==node.id }.inputs)
        assertEquals(listOf(""),connected.connect(node.id,"in:0","").nodes.single { it.id==node.id }.inputs)
        val cycle=graph.copy(nodes=graph.nodes+mirror("other",node.id)).replace(node.copy(inputs=listOf("other")))
        assertContains(assertFailsWith<IllegalArgumentException> { BuildingGraphCompiler.structure(cycle) }.message.orEmpty(),"Cycle")
    }

    @Test fun `mirror compiles nested pattern geometry native blocks and required air through every depth layer`() {
        val left=block("left","minecraft:glass")
        val right=block("right","minecraft:bricks")
        val row=BuildingNode("row",BuildingKind.PATTERN,inputs=listOf(left.id,"empty_wall",right.id))
        val reflected=mirror("mirror",row.id)
        val restored=mirror("restored",reflected.id)
        val joined=BuildingNode("joined",BuildingKind.PATTERN,inputs=listOf(row.id,reflected.id))
        val graph=graph(left,right,row,reflected,restored,joined)
        val evaluation=eval(graph)
        val result=evaluation.segment(reflected.id)
        assertEquals(P(3,2,2),P(result.length,result.height,result.depth))
        assertEquals(setOf(P(1,0,0),P(1,1,0),P(1,0,1),P(1,1,1)),evaluation.preview(reflected.id).clear.toSet())
        assertTrue(result.cells.filter { it.cell.pos.x==0 }.all { it.cell.block=="minecraft:bricks" })
        assertTrue(result.cells.filter { it.cell.pos.x==2 }.all { it.cell.block=="minecraft:glass" })
        assertEquals(evaluation.preview(row.id),evaluation.preview(restored.id))
        assertEquals(6,evaluation.segment(joined.id).length)
        assertEquals(16,evaluation.preview(joined.id).placements.size)
        val empty=eval(graph.replace(reflected.copy(inputs=listOf("empty_wall"))))
        assertTrue(empty.segment(reflected.id).air)
        assertEquals(4,empty.preview(reflected.id).clear.size)
        assertTrue(empty.preview(reflected.id).placements.isEmpty())
    }

    @Test fun `mirror preserves clipped source receipts and separated finishes while grouped mode overrides them`() {
        val panel=Form(id="panel",groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",1)),graph=NodeGraph(nodes=listOf(
            GraphNode("panel",part=Part("create:copycat_panel",tilt=3)),
            GraphNode("cut",part=Part("copycats:copycat_byte",turn=2)),
            GraphNode("difference",NodeKind.DIFFERENCE,listOf("panel","cut")),
            GraphNode("width",part=Part("copycats:copycat_slab",turn=1,tilt=1)),
            GraphNode("body",NodeKind.DIFFERENCE,listOf("difference","width")),
            GraphNode("accent",NodeKind.INTERSECTION,listOf("difference","width")),
            GraphNode("union",NodeKind.UNION,listOf("body","accent"),inputGroups=listOf("body","accent")),
            GraphNode("result",NodeKind.OUTPUT,listOf("union")))))
        val source=BuildingNode("source",BuildingKind.SEGMENT,module="",form="panel@1",length=1,height=1)
        val reflected=mirror("mirror",source.id)
        val graph=graph(source,reflected)
        val definitions=forms+("panel@1" to panel)
        val evaluation=eval(graph,definitions)
        val before=evaluation.preview(source.id)
        val after=evaluation.preview(reflected.id)
        val original=before.placements.single();val mirrored=after.placements.single()
        assertEquals(before.raw,after.raw)
        assertEquals(before.materials,after.materials)
        assertEquals(original.groups,mirrored.groups)
        assertEquals(original.surfaces,mirrored.surfaces)
        assertEquals(original.parts.map { it.source to it.recipeSource },mirrored.parts.map { it.source to it.recipeSource })
        assertTrue(mirrored.parts.all { it.recipeSource=="panel" })
        val solid=Geometry.solid(original.parts);val reflectedSolid=Geometry.solid(mirrored.parts)
        for(x in 0..15) for(y in 0..15) for(z in 0..15) assertEquals(solid.has(x,y,z),reflectedSolid.has(15-x,y,z))
        val grouped=eval(graph.replace(reflected.copy(materialMode=MaterialMode.GROUPED,role="frame")),definitions).preview(reflected.id)
        assertEquals(setOf(Building().palette.getValue("frame")),grouped.materials.keys)
        assertTrue(grouped.placements.single().surfaces.values.all { it==Building().palette.getValue("frame") })
        val sourceGrouped=graph.replace(source.copy(materialMode=MaterialMode.GROUPED,role="glass"))
        val separated=eval(sourceGrouped,definitions).preview(reflected.id)
        assertEquals(setOf(Building().palette.getValue("glass")),separated.materials.keys)
    }

    @Test fun `mirror outputs mount as room walls floors and ceilings and compile a complete building`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val wall=mirror("wall_mirror","wall")
        val floor=mirror("floor_mirror","floor",RotationAxis.Y)
        val ceiling=mirror("ceiling_mirror","ceiling",RotationAxis.Z)
        val room=original.nodes.single { it.id=="room" }
        val graph=original.copy(nodes=original.nodes+listOf(wall,floor,ceiling)).replace(room.copy(
            walls=room.walls.mapValues { wall.id },floor=floor.id,ceiling=ceiling.id))
        BuildingGraphCompiler.structure(graph)
        val before=eval(original).building().plan
        val after=eval(graph).building().plan
        assertEquals(before.placements.map { it.pos },after.placements.map { it.pos })
        assertEquals(before.clear,after.clear)
        assertEquals(before.materials,after.materials)
        assertEquals(before.raw,after.raw)
        assertTrue(after.placements.any { it.component=="wall" })
        assertTrue(after.placements.any { it.component=="floor" })
        assertTrue(after.placements.any { it.component=="ceiling" })
        val legacy=BuildingGraph.assembledStarter().let { it.copy(nodes=it.nodes+wall) }
        val legacyRoom=legacy.nodes.single { it.id=="room" }
        val withLegacyWall=legacy.replace(legacyRoom.copy(walls=legacyRoom.walls.mapValues { wall.id }))
        assertEquals(eval(legacy).room("room").plan.materials,eval(withLegacyWall).room("room").plan.materials)
        assertTrue(BuildingPorts.of(legacyRoom).filter { it.key.startsWith("wall:") }.all { BuildingKind.SEGMENT_MIRROR in it.accepts })
        assertTrue(BuildingPorts.of(legacy.nodes.single { it.id=="roof" }).filter { it.key.startsWith("wall:") }.all { BuildingKind.SEGMENT_MIRROR in it.accepts })
    }

    @Test fun `mirrored roof wall periods retain the requested materials in a real roof plan`() {
        val original=BuildingGraph.assembledStarter()
        val level=BuildingNode("roof_level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(mode=RoofMode.HEIGHT,height16=96,overhang16=0))
        val layout=BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
        val wall=BuildingNode("wall_period",BuildingKind.SEGMENT,module="",length=2,height=2,inputs=listOf("wall_copycat","roof_copycat"))
        val reflected=mirror("wall_mirror",wall.id)
        val roof=original.nodes.single { it.id=="roof" }.copy(assembly=true,layout=layout.id,inputs=emptyList(),roofConfig=RoofConfig(wall=reflected.id))
        val graph=original.copy(nodes=original.nodes+listOf(level,layout,wall,reflected)).replace(roof)
        BuildingGraphCompiler.structure(graph)
        val plan=eval(graph).building().plan
        assertTrue(plan.placements.any { it.component=="roof" })
        assertContains(plan.materials,Building().palette.getValue("roof"))
        assertContains(plan.materials,Building().palette.getValue("wall"))
    }

    @Test fun `mirror nodes roundtrip without schema changes and old nodes keep their rotation default`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val node=mirror("mirror","wall",RotationAxis.Z)
        val graph=original.copy(nodes=original.nodes+node)
        val gson=Gson()
        val restored=gson.fromJson(gson.toJson(graph),BuildingGraph::class.java)
        assertEquals(graph,restored)
        assertEquals(original.schema,restored.schema)
        assertEquals(eval(graph).preview(node.id),eval(restored).preview(node.id))
        val legacy=gson.toJsonTree(original).asJsonObject
        legacy.getAsJsonArray("nodes").forEach { it.asJsonObject.remove("rotationAxis") }
        val legacyRestored=gson.fromJson(legacy,BuildingGraph::class.java)
        assertTrue(legacyRestored.nodes.all { it.rotationAxis==RotationAxis.Y })
        assertEquals(eval(original).building(),eval(legacyRestored).building())
    }

    @Test fun `linked group instances remap mirror sources and share axis and material edits`() {
        val source=block("source","minecraft:stone")
        val reflected=mirror("mirror",source.id)
        val grouped=GraphGroups.create(graph(source,reflected),setOf(source.id,reflected.id))
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copy=instances.nodeGroups.last().nodes.map { id -> instances.nodes.single { it.id==id } }
        val copiedSource=copy.single { it.kind==BuildingKind.SEGMENT }
        val copiedMirror=copy.single { it.kind==BuildingKind.SEGMENT_MIRROR }
        assertEquals(listOf(copiedSource.id),copiedMirror.inputs)
        val changed=instances.replace(reflected.copy(rotationAxis=RotationAxis.Z,materialMode=MaterialMode.GROUPED,role="frame"))
        BuildingGraphCompiler.structure(changed)
        val synchronized=changed.nodes.single { it.id==copiedMirror.id }
        assertEquals(RotationAxis.Z,synchronized.rotationAxis)
        assertEquals(MaterialMode.GROUPED,synchronized.materialMode)
        assertEquals("frame",synchronized.role)
        assertEquals(listOf(copiedSource.id),synchronized.inputs)
        assertEquals(eval(changed).preview(reflected.id),eval(changed).preview(copiedMirror.id))
    }
}
