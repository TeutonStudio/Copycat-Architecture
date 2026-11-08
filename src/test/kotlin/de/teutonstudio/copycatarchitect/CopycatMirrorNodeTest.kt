package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.*

class CopycatMirrorNodeTest {
    private val full=Form(id="full",parts=listOf(Part("copycats:copycat_block")))
    private val byte=Form(id="byte",parts=listOf(Part("copycats:copycat_byte")))
    private val forms=mapOf("full@1" to full,"byte@1" to byte)
    private val source=BuildingNode("source",BuildingKind.COPYCAT,form="byte@1",role="frame",rotationAxis=RotationAxis.X,quarterTurns=1)
    private fun mirror(id:String="mirror",input:String=source.id,plane:CopycatMirrorPlane=CopycatMirrorPlane.X)=
        BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,id).copy(inputs=listOf(input),mirror=CopycatMirror(plane))
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+nodes) }
    private fun eval(graph:BuildingGraph,definitions:Map<String,Form> = forms)=BuildingGraphCompiler.Evaluation(graph,Building().palette,definitions,emptyMap())

    @Test fun `Copycat mirror has one typed socket and validates drafts arity kinds cycles and settings ownership`() {
        val node=BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,"mirror")
        assertEquals(listOf(""),node.inputs)
        assertEquals(CopycatMirror(),node.mirror)
        assertEquals(BuildingKind.SEGMENT_MIRROR.ordinal+1,BuildingKind.COPYCAT_MIRROR.ordinal)
        assertEquals(listOf(BuildingPort("in:0","",setOf(BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE))),BuildingPorts.of(node,true))
        val graph=graph(source,node)
        BuildingGraphCompiler.structure(graph)
        assertContains(assertFails { eval(graph).preview(node.id) }.message.orEmpty(),"Connect the Copycat")
        for(inputs in listOf(emptyList(),listOf(source.id,source.id)))
            assertFails { BuildingGraphCompiler.structure(graph.replace(node.copy(inputs=inputs))) }
        assertFails { graph.connect(node.id,"in:0","wall") }
        assertFails { graph.connect(node.id,"in:1",source.id) }
        assertFails { graph.connect(node.id,"in:0",node.id) }
        assertFails { BuildingGraphCompiler.structure(graph.replace(source.copy(mirror=CopycatMirror()))) }
        assertFails { BuildingGraphCompiler.structure(graph.replace(source.copy(mirrorPositionsOnly=true))) }
        val connected=graph.connect(node.id,"in:0",source.id)
        assertEquals(listOf(source.id),connected.nodes.single { it.id==node.id }.inputs)
        assertEquals(listOf(""),connected.connect(node.id,"in:0","").nodes.single { it.id==node.id }.inputs)
    }

    @Test fun `source rotation precedes Copycat reflection layout rotation and room mounting`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val reflected=mirror()
        val layout=initial.nodes.single { it.id=="wall_layout" }.copy(layoutCells=listOf(SegmentLayoutCell(0,0,0,1)))
        val wall=initial.nodes.single { it.id=="wall" }.copy(inputs=listOf(reflected.id))
        val graph=initial.copy(nodes=initial.nodes+listOf(source,reflected)).replace(layout).replace(wall)
        val evaluation=eval(graph)
        val original=Geometry.solid(evaluation.preview(source.id).placements.single().parts)
        val reflectedSolid=Geometry.solid(evaluation.preview(reflected.id).placements.single().parts)
        for(x in 0..15) for(y in 0..15) for(z in 0..15) assertEquals(original.has(x,y,z),reflectedSolid.has(15-x,y,z))
        assertEquals(reflectedSolid.rotate(1).key(),Geometry.solid(evaluation.preview(wall.id).placements.single().parts).key())
        val east=evaluation.room("room").plan.placements.single { it.component=="wall" && it.pos==P(3,0,0) }
        assertEquals(reflectedSolid.rotate(2).key(),Geometry.solid(east.parts).key())
        assertEquals(Building().palette.getValue("frame"),east.material)
        val twice=mirror("twice",reflected.id)
        val restored=eval(graph.copy(nodes=graph.nodes+twice)).preview(twice.id).placements.single()
        assertTrue(Geometry.exact(restored.parts).equivalent(Geometry.exact(evaluation.preview(source.id).placements.single().parts)))
    }

    @Test fun `Copycat mirrors preserve composite finish groups and paid receipts and grouped overrides survive consumers`() {
        val composite=Form(id="composite",groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","glass",1)),graph=NodeGraph(nodes=listOf(
            GraphNode("panel",part=Part("create:copycat_panel",tilt=3)),
            GraphNode("cut",part=Part("copycats:copycat_byte",turn=2)),
            GraphNode("difference",NodeKind.DIFFERENCE,listOf("panel","cut")),
            GraphNode("width",part=Part("copycats:copycat_slab",turn=1,tilt=1)),
            GraphNode("body",NodeKind.DIFFERENCE,listOf("difference","width")),
            GraphNode("accent",NodeKind.INTERSECTION,listOf("difference","width")),
            GraphNode("union",NodeKind.UNION,listOf("body","accent"),inputGroups=listOf("body","accent")),
            GraphNode("result",NodeKind.OUTPUT,listOf("union")))))
        val source=source.copy(form="composite@1")
        val reflected=mirror()
        val segment=BuildingNode("segment",BuildingKind.SEGMENT,module="",inputs=listOf(reflected.id),length=1,height=1)
        val graph=graph(source,reflected,segment)
        val definitions=forms+("composite@1" to composite)
        val evaluation=eval(graph,definitions)
        val before=evaluation.preview(source.id);val after=evaluation.preview(reflected.id)
        assertEquals(before.raw,after.raw);assertEquals(before.materials,after.materials)
        assertEquals(before.placements.single().surfaces,after.placements.single().surfaces)
        assertEquals(before.placements.single().groups,after.placements.single().groups)
        assertEquals(before.placements.single().parts.map { it.source to it.recipeSource },after.placements.single().parts.map { it.source to it.recipeSource })
        assertTrue(after.placements.single().parts.all { it.recipeSource=="panel" })
        assertEquals(after,evaluation.preview(segment.id))
        val grouped=graph.replace(reflected.copy(materialMode=MaterialMode.GROUPED,role="roof"))
        val groupedPlan=eval(grouped,definitions).preview(segment.id)
        assertEquals(setOf(Building().palette.getValue("roof")),groupedPlan.materials.keys)
        val outer=mirror("outer",reflected.id,CopycatMirrorPlane.Z)
        val chained=grouped.copy(nodes=grouped.nodes+outer).replace(segment.copy(inputs=listOf(outer.id)))
        assertEquals(groupedPlan.materials,eval(chained,definitions).preview(segment.id).materials)
    }

    @Test fun `all Copycat sockets accept mirrors including region assignments landscape plates corners and access`() {
        val initial=BuildingGraph.assembledStarter()
        val source=source.copy(form="full@1",quarterTurns=0)
        val reflected=mirror()
        val stairSource=source.copy(id="stair_source",form="stairs@1")
        val stairMirror=mirror("stair_mirror",stairSource.id)
        val nodes=listOf(
            BuildingNode("stairs",BuildingKind.STAIRWELL,stairwell=StairwellConfig(tread=stairMirror.id,landing=reflected.id,enclosure=reflected.id)),
            BuildingNode("elevator",BuildingKind.ELEVATOR_SHAFT,elevatorShaft=ElevatorShaftConfig(enclosure=reflected.id)),
            BuildingNode("service",BuildingKind.SERVICE_SHAFT,serviceShaft=ServiceShaftConfig(enclosure=reflected.id)),
            BuildingAssembly.newNode(BuildingKind.GARDEN,"garden").copy(floor=reflected.id),
            BuildingAssembly.newNode(BuildingKind.PATH,"path").copy(floor=reflected.id),
            BuildingNode("region_layout",BuildingKind.SEGMENT_LAYOUT,length=1,height=1),
            BuildingAssembly.newNode(BuildingKind.SEGMENT,"region_segment").copy(layout="region_layout",regionCopies=mapOf("region:xb_yb_zb" to reflected.id)))
        var graph=initial.copy(nodes=initial.nodes+listOf(source,reflected,stairSource,stairMirror)+nodes)
        for(id in listOf("wall","floor","ceiling","roof")) graph=graph.replace(graph.nodes.single { it.id==id }.copy(inputs=listOf(reflected.id)))
        graph=graph.replace(graph.nodes.single { it.id=="room" }.copy(corner=reflected.id))
        BuildingGraphCompiler.structure(graph)
        for(node in graph.nodes) for(port in BuildingPorts.of(node,graph,true))
            if(BuildingKind.COPYCAT in port.accepts) assertContains(port.accepts,BuildingKind.COPYCAT_MIRROR)
        val evaluation=eval(graph,forms+("stairs@1" to Form(id="stairs",parts=listOf(Part("copycats:copycat_stairs")))))
        assertTrue(evaluation.building().plan.placements.isNotEmpty())
        for(id in listOf("garden","path","region_segment","stairs","elevator","service"))
            assertTrue(evaluation.preview(id).placements.isNotEmpty(),id)
    }

    @Test fun `mirrored air remains explicit air while native item geometry is rejected clearly`() {
        val air=source.copy(block="minecraft:air",form="missing@1")
        val reflected=mirror()
        val initial=graph(air,reflected)
        val floor=initial.nodes.single { it.id=="floor" }.copy(inputs=listOf(reflected.id))
        val evaluation=eval(initial.replace(floor))
        assertEquals(listOf(P()),evaluation.preview(reflected.id).clear)
        assertTrue(evaluation.preview(reflected.id).placements.isEmpty())
        assertEquals(9,evaluation.preview(floor.id).clear.size)
        val native=air.copy(block="minecraft:white_carpet")
        val rejected=eval(initial.replace(native))
        assertContains(assertFails { rejected.preview(reflected.id) }.message.orEmpty(),"native blocks")
    }

    @Test fun `face diagonal Copycat mirrors use the selected centered plane normal`() {
        val original=Geometry.solid(eval(graph(source)).preview(source.id).placements.single().parts)
        for(plane in CopycatMirrorPlane.entries.filterNot { it.name.startsWith("BODY_") }) {
            val reflected=mirror(plane=plane)
            val actual=Geometry.solid(eval(graph(source,reflected)).preview(reflected.id).placements.single().parts)
            for(x in 0..15) for(y in 0..15) for(z in 0..15) {
                val p=when(plane.name) {
                    "X"->P(15-x,y,z);"Y"->P(x,15-y,z);"Z"->P(x,y,15-z)
                    "XY"->P(15-y,15-x,z);"XY_NEGATIVE"->P(y,x,z)
                    "XZ"->P(15-z,y,15-x);"XZ_NEGATIVE"->P(z,y,x)
                    "YZ"->P(x,15-z,15-y);"YZ_NEGATIVE"->P(x,z,y)
                    else->error("Unexpected face plane $plane")
                }
                assertEquals(original.has(x,y,z),actual.has(p.x,p.y,p.z),"$plane at $x,$y,$z")
            }
        }
    }

    @Test fun `roof wall templates and valid slope variants compile mirrored Copycat sources`() {
        val original=BuildingGraph.assembledStarter()
        val source=source.copy(form="full@1",quarterTurns=0)
        val reflected=mirror()
        // Interior columns of this five-block gable use phases 1 and 2 of the 1:3 period.
        // The thick phase-2 roof profile covers both interior backing profiles without spill rows.
        val profile=Form(id="roof_profile",parts=listOf(Part(RoofPartParameters.SOURCE,roof=RoofPartParameters(1,3,phase=2,thickness=16))))
        val profileSource=source.copy(id="profile_source",form="roof_profile@1",role="glass")
        val profileMirror=mirror("profile_mirror",profileSource.id)
        val level=BuildingNode("roof_level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(slope=RoofSlope(1,3),mode=RoofMode.HEIGHT,height16=96,overhang16=0))
        val layout=BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
        val roof=original.nodes.single { it.id=="roof" }.copy(assembly=true,layout=layout.id,inputs=emptyList(),
            roofConfig=RoofConfig(wall=reflected.id,variants=mapOf("slope" to profileMirror.id)))
        val graph=original.copy(nodes=original.nodes+listOf(source,reflected,profileSource,profileMirror,level,layout)).replace(roof)
        val definitions=forms+("roof_profile@1" to profile)
        BuildingGraphCompiler.structure(graph)
        val plan=eval(graph,definitions).building().plan
        assertTrue(plan.placements.any { it.component=="roof" })
        assertTrue(plan.placements.any { it.component=="gable" })
        assertContains(plan.materials,Building().palette.getValue("frame"))
        assertContains(plan.materials,Building().palette.getValue("glass"))
        assertTrue(plan.placements.any { placement -> placement.parts.any { it.source==RoofPartParameters.SOURCE && it.group=="body" } })
        val direct=graph.replace(roof.copy(roofConfig=RoofConfig(wall=reflected.id,variants=mapOf("slope" to profileSource.id))))
        assertEquals(eval(direct,definitions).building().plan.materials,plan.materials)
        // A single full block lacks the spill row of a slope period, whether reflected or direct.
        // Accept the Copycat type, then preserve the existing physical roof-profile validation.
        val invalidProfile=graph.replace(level.copy(roofLevel=level.roofLevel!!.copy(slope=RoofSlope())))
            .replace(roof.copy(roofConfig=RoofConfig(wall=reflected.id,variants=mapOf("slope" to reflected.id))))
        BuildingGraphCompiler.structure(invalidProfile)
        assertContains(assertFails { eval(invalidProfile,definitions).building() }.message.orEmpty(),"hole")
    }

    @Test fun `body diagonal Copycat reflection retains rational planes and rejects geometry outside its cell`() {
        val centered=Form(id="centered",parts=listOf(Part("copycats:copycat_byte",offset=P(4,4,4))))
        val source=source.copy(form="centered@1",quarterTurns=0)
        for(plane in CopycatMirrorPlane.entries.filter { it.name.startsWith("BODY_") }) {
            val reflected=mirror(plane=plane)
            val evaluation=eval(graph(source,reflected),forms+("centered@1" to centered))
            val plan=evaluation.preview(reflected.id)
            val part=plan.placements.single().parts.single()
            assertEquals("copycats:copycat_byte",part.source)
            val volume=Geometry.exact(listOf(part))
            assertTrue(volume.cells.flatMap { it.vertices() }.any { p -> listOf(p.x,p.y,p.z).any { it.denominator!=BigInteger.ONE } })
            val restored=mirror("restored",reflected.id,plane)
            val roundtrip=eval(evaluation.graph.copy(nodes=evaluation.graph.nodes+restored),forms+("centered@1" to centered)).preview(restored.id)
            assertTrue(Geometry.exact(roundtrip.placements.single().parts).equivalent(Geometry.exact(centered.parts)))
            val outside=evaluation.graph.replace(source.copy(form="full@1"))
            assertFails { eval(outside).preview(reflected.id) }
        }
    }

    @Test fun `segment positions mode moves cells without changing oriented Copycat geometry`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val layout=initial.nodes.single { it.id=="wall_layout" }.copy(layoutCells=listOf(SegmentLayoutCell(0,0,0,1)))
        val wall=initial.nodes.single { it.id=="wall" }.copy(inputs=listOf(source.id))
        val positions=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"positions").copy(inputs=listOf(wall.id),mirrorPositionsOnly=true)
        val full=positions.copy(id="full_mirror",mirrorPositionsOnly=false)
        val graph=initial.copy(nodes=initial.nodes+listOf(source,positions,full)).replace(layout).replace(wall)
        val evaluation=eval(graph)
        val original=evaluation.preview(wall.id).placements.single()
        val moved=evaluation.preview(positions.id).placements.single()
        val mirrored=evaluation.preview(full.id).placements.single()
        assertEquals(P(2,0,0),moved.pos)
        assertEquals(original.parts,moved.parts)
        assertEquals(original.surfaces,moved.surfaces)
        assertEquals(moved.pos,mirrored.pos)
        assertNotEquals(Geometry.solid(moved.parts).key(),Geometry.solid(mirrored.parts).key())
        val originalCell=evaluation.segment(wall.id).cells.single { !it.cell.empty }.cell
        val movedCell=evaluation.segment(positions.id).cells.single { !it.cell.empty }.cell
        assertEquals(originalCell.copy(pos=P(2,0,0)),movedCell)
    }

    @Test fun `new mirror settings roundtrip and synchronize shared groups while legacy JSON omits them`() {
        val gson=Gson();val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val legacyJson=gson.toJson(original)
        assertFalse(legacyJson.contains("\"mirror\""));assertFalse(legacyJson.contains("mirrorPositionsOnly"))
        val reflected=mirror()
        val positions=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"positions").copy(inputs=listOf("wall"),mirrorPositionsOnly=true)
        val graph=original.copy(nodes=original.nodes+listOf(source,reflected,positions))
        assertEquals(graph,gson.fromJson(gson.toJson(graph),BuildingGraph::class.java))
        assertEquals(original,gson.fromJson(legacyJson,BuildingGraph::class.java))
        val grouped=GraphGroups.create(graph,setOf(source.id,reflected.id))
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copy=instances.nodeGroups.last().nodes.map { id -> instances.nodes.single { it.id==id } }.single { it.kind==BuildingKind.COPYCAT_MIRROR }
        val changed=instances.replace(reflected.copy(mirror=CopycatMirror(CopycatMirrorPlane.Z),materialMode=MaterialMode.GROUPED,role="roof"))
        BuildingGraphCompiler.structure(changed)
        val synchronized=changed.nodes.single { it.id==copy.id }
        assertEquals(CopycatMirror(CopycatMirrorPlane.Z),synchronized.mirror)
        assertEquals(MaterialMode.GROUPED,synchronized.materialMode)
        assertEquals("roof",synchronized.role)
        assertEquals(eval(changed).preview(reflected.id),eval(changed).preview(copy.id))
    }
}
