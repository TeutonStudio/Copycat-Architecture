package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingSegmentVolumeTest {
    private val panel=Part("create:copycat_panel")
    private val forms=mapOf("panel@1" to Form(id="panel",parts=listOf(panel)),"full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun graph(vararg definitions:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+definitions) }
    private fun evaluation(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun segment(id:String,length:Int,height:Int,depth:Int,block:String="minecraft:stone")=
        BuildingNode(id,BuildingKind.SEGMENT,module="",length=length,height=height,depth=depth,block=block)

    @Test fun `each volume region accepts its own copycat and collapsed axes expose unique sockets`() {
        val regions=SegmentRegions.of(3,3,3)
        val copies=regions.indices.map { BuildingNode("copy_$it",BuildingKind.COPYCAT,role="role_$it") }
        val layout=BuildingNode("volume_layout",BuildingKind.SEGMENT_LAYOUT,length=3,height=3,depth=3)
        val volume=BuildingNode("volume",BuildingKind.SEGMENT,module="",assembly=true,layout=layout.id,
            regionCopies=regions.mapIndexed { index,region -> region.key to copies[index].id }.toMap())
        val graph=graph(layout,volume,*copies.toTypedArray())
        val result=evaluation(graph).segment(volume.id)
        assertEquals(P(3,3,3),P(result.length,result.height,result.depth))
        for((index,region) in regions.withIndex()) assertEquals(region.cells,result.cells.filter { it.cell.role=="role_$index" }.map { it.cell.pos }.toSet())
        val collapsed=graph.replace(layout.copy(length=1,depth=1))
        assertEquals(3,BuildingPorts.of(volume,collapsed).count { it.key.startsWith("region:") })
        val thin=evaluation(collapsed).segment(volume.id)
        assertEquals(P(1,3,1),P(thin.length,thin.height,thin.depth))
        assertEquals(3,thin.cells.size)
        assertEquals(3,thin.cells.map { it.cell.pos }.distinct().size)
    }

    @Test fun `air copycat assignments clear cells throughout segment depth`() {
        val layout=BuildingNode("volume_layout",BuildingKind.SEGMENT_LAYOUT,length=2,height=2,depth=2)
        val solid=BuildingNode("solid_copy",BuildingKind.COPYCAT)
        val air=BuildingNode("air_copy",BuildingKind.COPYCAT,block="minecraft:air")
        val volume=BuildingNode("volume",BuildingKind.SEGMENT,module="",assembly=true,layout=layout.id,inputs=listOf(solid.id,air.id))
        val plan=evaluation(graph(layout,solid,air,volume)).preview(volume.id)
        assertEquals(setOf(P(1,0,0),P(1,1,0),P(1,0,1),P(1,1,1)),plan.clear.toSet())
        assertEquals(4,plan.placements.size)
        assertFalse("minecraft:air" in plan.materials)
    }

    @Test fun `collapsed socket edits update every alias and retain choices when expanded`() {
        val regions=SegmentRegions.of(3,3,3)
        val copies=regions.indices.map { BuildingNode("copy_$it",BuildingKind.COPYCAT) }
        val replacement=BuildingNode("replacement",BuildingKind.COPYCAT)
        val layout=BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,length=3,height=3,depth=3)
        val volume=BuildingNode("volume",BuildingKind.SEGMENT,module="",assembly=true,layout=layout.id,
            regionCopies=regions.mapIndexed { index,region -> region.key to copies[index].id }.toMap())
        val original=graph(layout,volume,replacement,*copies.toTypedArray())
        val shrunk=original.replace(layout.copy(length=1))
        assertEquals(volume.regionCopies,shrunk.replace(layout).resolvedNode(volume).effectiveRegionCopies())
        val key="region:xb_y0_z0"
        val changed=shrunk.connect(volume.id,key,replacement.id)
        assertEquals(replacement.id,BuildingPorts.of(changed.nodes.single { it.id==volume.id },changed).single { it.key==key }.source)
        val expanded=changed.replace(layout).resolvedNode(volume.id).effectiveRegionCopies()
        for(x in listOf("0","i","1")) {
            assertEquals(replacement.id,expanded["region:x${x}_y0_z0"])
            assertEquals(volume.regionCopies["region:x${x}_y1_z1"],expanded["region:x${x}_y1_z1"])
        }
        assertTrue(changed.nodes.single { it.id==volume.id }.regionCopies.size<=27)
        val disconnected=changed.connect(volume.id,key,"")
        assertTrue(disconnected.resolvedNode(volume.id).effectiveRegionCopies()[key].isNullOrEmpty())
        for(x in listOf("0","i","1")) assertNull(disconnected.replace(layout).resolvedNode(volume.id).effectiveRegionCopies()["region:x${x}_y0_z0"])
        assertTrue(changed.remove(replacement.id).resolvedNode(volume.id).effectiveRegionCopies()[key].isNullOrEmpty())
    }

    @Test fun `diagram disconnect clears collapsed boundary aliases instead of reviving hidden wires`() {
        val copy=BuildingNode("copy",BuildingKind.COPYCAT)
        val layout=BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,length=1,height=3,depth=1)
        val volume=BuildingNode("volume",BuildingKind.SEGMENT,module="",assembly=true,layout=layout.id,
            regionCopies=mapOf("region:x0_y0_z0" to copy.id,"region:x1_y0_z1" to copy.id,"region:xi_y0_zi" to copy.id))
        val graph=graph(copy,layout,volume)
        val removed="region:xb_y0_zb"
        val positions=graph.nodes.map { DiagramPosition(it.id,it.x,it.y) }
        val wires=graph.nodes.flatMap { node -> BuildingPorts.of(node,graph,true).filter { it.source.isNotEmpty() && !(node.id==volume.id && it.key==removed) }.map { DiagramConnection(it.source,node.id,it.key) } }
        val changed=DiagramEdits.building(graph,positions,wires)
        assertTrue(changed.nodes.single { it.id==volume.id }.regionCopies.isEmpty())
        assertTrue(BuildingPorts.of(changed.nodes.single { it.id==volume.id },changed).single { it.key==removed }.source.isEmpty())
        assertTrue(changed.replace(layout.copy(length=3,depth=3)).resolvedNode(volume.id).effectiveRegionCopies().isEmpty())
    }

    @Test fun `diagram source deletion removes hidden interior aliases before a segment grows again`() {
        val endpoint=BuildingNode("endpoint",BuildingKind.COPYCAT)
        val interior=BuildingNode("interior",BuildingKind.COPYCAT)
        val layout=BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,length=3,height=1,depth=1)
        val volume=BuildingNode("volume",BuildingKind.SEGMENT,module="",assembly=true,layout=layout.id,
            regionCopies=mapOf("region:x0_yb_zb" to endpoint.id,"region:xi_yb_zb" to interior.id,"region:x1_yb_zb" to endpoint.id))
        val shrunk=graph(endpoint,interior,layout.copy(length=2),volume)
        assertTrue(BuildingPorts.of(volume,shrunk).none { it.source==interior.id })
        val positions=shrunk.nodes.filter { it.id!=interior.id }.map { DiagramPosition(it.id,it.x,it.y) }
        val wires=shrunk.nodes.flatMap { node -> BuildingPorts.of(node,shrunk,true).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,node.id,it.key) } }
        val changed=DiagramEdits.building(shrunk,positions,wires)
        assertTrue(changed.nodes.single { it.id==volume.id }.regionCopies.values.none { it==interior.id })
        val grown=changed.replace(layout)
        BuildingGraphCompiler.structure(grown)
        val result=evaluation(grown).segment(volume.id)
        assertEquals(setOf(P(1,0,0)),result.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
        assertEquals(setOf(P(),P(2,0,0)),result.cells.filter { !it.cell.empty }.map { it.cell.pos }.toSet())
    }

    @Test fun `patterns adapt air spans over every depth layer`() {
        val solid=segment("volume",2,2,3)
        val air=segment("gap",1,1,3,"minecraft:air")
        val horizontal=BuildingNode("horizontal",BuildingKind.PATTERN,inputs=listOf(solid.id,air.id,solid.id))
        val vertical=horizontal.copy(id="vertical",patternAxis="vertical")
        val evaluation=evaluation(graph(solid,air,horizontal,vertical))
        val row=evaluation.segment(horizontal.id)
        assertEquals(P(5,2,3),P(row.length,row.height,row.depth))
        assertEquals(buildSet { for(y in 0..1) for(z in 0..2) add(P(2,y,z)) },row.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
        val stack=evaluation.segment(vertical.id)
        assertEquals(P(2,5,3),P(stack.length,stack.height,stack.depth))
        assertEquals(buildSet { for(x in 0..1) for(z in 0..2) add(P(x,2,z)) },stack.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
        val standardGap=horizontal.copy(inputs=listOf(solid.id,"empty_wall",solid.id))
        val adapted=evaluation(graph(solid,standardGap)).segment(horizontal.id)
        assertEquals(3,adapted.depth)
        assertEquals(buildSet { for(y in 0..1) for(z in 0..2) add(P(2,y,z)) },adapted.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
    }

    @Test fun `transformed segment nodes preserve source geometry groups and air through preview and JSON`() {
        val layout=BuildingNode("panel_layout",BuildingKind.SEGMENT_LAYOUT,length=2,height=3,
            layoutCells=listOf(SegmentLayoutCell(0,0,0,1)))
        val copy=BuildingNode("panel_copy",BuildingKind.COPYCAT,form="panel@1",role="glass")
        val volume=BuildingNode("panel_volume",BuildingKind.SEGMENT,module="",assembly=true,layout=layout.id,inputs=listOf(copy.id))
        val block=segment("end",1,1,1)
        val united=BuildingNode("united",BuildingKind.PATTERN,assembly=true,patternAxis="transform",inputs=listOf(volume.id,block.id),
            transforms=listOf(SegmentTransform(P(0,1,-2),RotationAxis.X,1),SegmentTransform(P(-1,0,0))),materialMode=MaterialMode.GROUPED,role="frame")
        val graph=graph(layout,copy,volume,block,united)
        val evaluation=evaluation(graph)
        val result=evaluation.segment(united.id)
        assertEquals(P(3,2,3),P(result.length,result.height,result.depth))
        assertEquals(7,result.cells.size)
        assertTrue(result.cells.all { it.overrideRole=="frame" })
        val plan=evaluation.preview(united.id)
        assertEquals(5,plan.clear.size)
        val placement=plan.placements.single { it.block.isEmpty() }
        assertEquals(P(1,1,0),placement.pos)
        assertEquals("minecraft:oak_planks",placement.material)
        assertEquals(Solid.part(panel).rotate(1).rotate(RotationAxis.X,1).key(),Geometry.solid(placement.parts).key())
        val gson=Gson();val roundtrip=gson.fromJson(gson.toJson(graph),BuildingGraph::class.java)
        assertEquals(graph,roundtrip)
        assertEquals(plan,evaluation(roundtrip).preview(united.id))
    }

    @Test fun `depth composition creates a single volume and rejects oversized union bounds`() {
        val first=segment("first",2,2,2)
        val second=segment("second",2,2,3,"minecraft:glass")
        val pattern=BuildingNode("depth_pattern",BuildingKind.PATTERN,patternAxis="depth",inputs=listOf(first.id,second.id))
        val graph=graph(first,second,pattern)
        val result=evaluation(graph).segment(pattern.id)
        assertEquals(P(2,2,5),P(result.length,result.height,result.depth))
        assertEquals(20,result.cells.size)
        assertTrue(result.cells.filter { it.cell.pos.z<2 }.all { it.cell.block=="minecraft:stone" })
        assertTrue(result.cells.filter { it.cell.pos.z>=2 }.all { it.cell.block=="minecraft:glass" })
        val tooWide=pattern.copy(patternAxis="transform",transforms=listOf(SegmentTransform(),SegmentTransform(P(127,0,0))))
        assertFailsWith<IllegalArgumentException> { evaluation(graph.replace(tooWide)).segment(pattern.id) }
        assertFailsWith<IllegalArgumentException> { evaluation(graph.replace(first.copy(depth=127))).segment(pattern.id) }
    }

    @Test fun `overlapping clipped copycats unite with separate material finishes and paid receipts`() {
        fun splitPanel(id:String,turn:Int,upper:Boolean):Form {
            val groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",1))
            return Form(id=id,groups=groups,graph=NodeGraph(nodes=listOf(
                GraphNode("panel_source",part=Part("create:copycat_panel",turn=turn,tilt=1)),
                GraphNode("height_cut",part=Part("copycats:copycat_slab")),
                GraphNode("width_cut",part=Part("copycats:copycat_slab",turn=1,tilt=1)),
                GraphNode("half",if(upper) NodeKind.DIFFERENCE else NodeKind.INTERSECTION,listOf("panel_source","height_cut")),
                GraphNode("a",NodeKind.INTERSECTION,listOf("half","width_cut")),
                GraphNode("b",NodeKind.DIFFERENCE,listOf("half","width_cut")),
                GraphNode("union",NodeKind.UNION,listOf("a","b"),inputGroups=listOf("body","accent")),
                GraphNode("result",NodeKind.OUTPUT,listOf("union")))))
        }
        val lower=splitPanel("lower",0,false);val upper=splitPanel("upper",2,true)
        val first=BuildingNode("first",BuildingKind.SEGMENT,module="",form="lower@1",length=1,height=1)
        val second=first.copy(id="second",form="upper@1",role="frame")
        val united=BuildingNode("united",BuildingKind.PATTERN,patternAxis="transform",assembly=true,inputs=listOf(first.id,second.id),
            transforms=listOf(SegmentTransform(),SegmentTransform(quarterTurns=2)))
        val graph=graph(first,second,united)
        fun evaluate(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms+mapOf("lower@1" to lower,"upper@1" to upper),emptyMap())
        val evaluation=evaluate(graph)
        val plan=evaluation.preview(united.id)
        val placement=plan.placements.single()
        val expected=Geometry.solid(Geometry.validate(lower).parts).union(Geometry.solid(Geometry.validate(upper).parts).rotate(2))
        assertEquals(expected.key(),Geometry.solid(placement.parts).key())
        assertEquals(768,expected.count())
        assertEquals(4,placement.parts.size)
        assertEquals(listOf(2,2),placement.parts.groupingBy { it.recipeSource }.eachCount().values.sorted())
        assertTrue(placement.parts.all { it.recipeSource.isNotEmpty() })
        val materials=Geometry.resolve(placement.parts,placement.groups).materialSolids(placement.material,placement.surfaces)
        assertEquals(mapOf("minecraft:stone_bricks" to 192,"minecraft:oak_planks" to 192,"minecraft:glass" to 384),materials.mapValues { it.value.count() })
        assertEquals(48,plan.raw)
        assertEquals(setOf("minecraft:stone_bricks","minecraft:oak_planks","minecraft:glass"),plan.materials.keys)
        val air=second.copy(block="minecraft:air")
        val native=second.copy(block="minecraft:stone")
        assertEquals(evaluation.preview(first.id),evaluate(graph.replace(air)).preview(united.id))
        assertFailsWith<IllegalArgumentException> { evaluation(graph.replace(native)).segment(united.id) }
    }

    @Test fun `overlapping copycats through air filled layouts preserve group identities and individual materials`() {
        val groups=listOf(MaterialGroup("body","Panel","wall"),MaterialGroup("cap","Cap","frame",1))
        val form=Form(id="split",groups=groups,parts=listOf(
            Part("create:copycat_panel",tilt=1,group="body"),Part("copycats:copycat_layer",top=true,group="cap")))
        val first=BuildingNode("first_copy",BuildingKind.COPYCAT,form="split@1",surfaces=mapOf("body" to "minecraft:gold_block","cap" to "minecraft:glass"))
        val second=first.copy(id="second_copy",quarterTurns=2,surfaces=mapOf("body" to "minecraft:copper_block","cap" to "minecraft:glass"))
        val air=BuildingNode("air_copy",BuildingKind.COPYCAT,block="minecraft:air")
        val layout=BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,length=3,height=1)
        val left=BuildingNode("left",BuildingKind.SEGMENT,assembly=true,layout=layout.id,inputs=listOf(first.id,air.id),module="")
        val right=left.copy(id="right",inputs=listOf(second.id,air.id))
        val united=BuildingNode("united",BuildingKind.PATTERN,assembly=true,patternAxis="transform",inputs=listOf(left.id,right.id))
        val original=graph(first,second,air,layout,left,right,united)
        for(inputs in listOf(united.inputs,united.inputs.reversed())) {
            val graph=original.replace(united.copy(inputs=inputs))
            fun evaluate(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms+("split@1" to form),emptyMap())
            val evaluation=evaluate(graph);val plan=evaluation.preview(united.id)
            assertEquals(listOf(P(1,0,0)),plan.clear)
            assertEquals(setOf(P(),P(2,0,0)),plan.placements.map { it.pos }.toSet())
            val originals=listOf(first.id,second.id).map { evaluation.preview(it).placements.single() }
            val expected=originals.map { Geometry.solid(it.parts) }.reduce(Solid::union)
            for(placement in plan.placements) {
                assertEquals(expected.key(),Geometry.solid(placement.parts).key())
                assertEquals(listOf("Cap","Panel","Panel"),placement.groups.map { it.name }.sorted())
                assertEquals(setOf("minecraft:gold_block","minecraft:copper_block","minecraft:glass"),placement.surfaces.values.toSet())
                assertEquals(3,placement.groups.map { it.id }.distinct().size)
                val occupied=Geometry.resolve(placement.parts,placement.groups).materialSolids(placement.material,placement.surfaces)
                assertTrue(occupied.values.all { it.count()>0 })
                assertEquals(expected.count(),occupied.values.sumOf { it.count() })
            }
            val restored=Gson().fromJson(Gson().toJson(graph),BuildingGraph::class.java)
            assertEquals(plan,evaluate(restored).preview(united.id))
        }
    }
}
