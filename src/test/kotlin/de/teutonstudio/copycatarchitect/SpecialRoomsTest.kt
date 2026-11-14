package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class SpecialRoomsTest {
    private val palette=Building().palette
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun base(size:Int=7):BuildingGraph {
        val initial=BuildingGraph.assembledStarter()
        return initial.copy(nodes=initial.nodes.map {
            when(it.id) {
                "room" -> it.copy(rectangles=listOf(Rect(width=size,length=size)),assembly=false,walls=RoomGeometry.edges(listOf(Rect(width=size,length=size))).associate { e->e.id to "wall" })
                "wall" -> it.copy(length=size,height=4,assembly=false,module="")
                "roof" -> it.copy(roofType="flat",walls=emptyMap())
                else -> it
            }
        })
    }
    private fun evaluate(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,palette,forms,emptyMap())
    private fun bay(g:BuildingGraph,direction:Int=0,wallDepth:Int=1):BuildingGraph {
        val host=g.nodes.first { it.id=="room" }
        val edge=RoomWallJoints.runs(host).first { it.direction==direction }
        return g.copy(nodes=g.nodes.map {
            when(it.id) {
                "room" -> it.copy(bayWindows=listOf(BayWindowInstance("bay_0","bay",edge.id,2)))
                "wall" -> it.copy(depth=wallDepth)
                else -> it
            }
        }+BuildingNode("bay",BuildingKind.BAY_WINDOW,bayWindow=BayWindowConfig(wall="wall",front="wall",floor="floor",ceiling="ceiling"))).withCurrentSchema()
    }
    @Test fun `bay opens only the selected wall in every direction and preserves roof footprint`() {
        for(direction in 0..3) for(depth in 1..2) {
            val g=bay(base(),direction,depth);val ev=evaluate(g)
            val raw=evaluate(g.replace(g.nodes.first { it.id=="room" }.copy(bayWindows=emptyList())))
            val before=raw.room("room");val room=ev.room("room")
            assertEquals(before.footprint.size+6,room.footprint.size,"direction $direction depth $depth")
            val storey=ev.storey("storey")
            assertEquals(raw.storey("storey").footprint,storey.roofFootprint)
            assertEquals(3*4*depth,storey.bayOpenings.size,"direction $direction depth $depth")
            val removed=before.plan.placements.filter { it.component=="wall" && room.plan.placements.none { p->p.pos==it.pos } }
            assertEquals(3*4*depth,removed.size)
            assertTrue(removed.all { it.pos in storey.bayOpenings })
            assertTrue(ev.building().plan.raw>raw.building().plan.raw)
        }
    }
    @Test fun `joined and segment authored rooms accept bays with existing horizontal segment plates`() {
        val initial=BuildingGraph.explicitStarter().editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=7,length=7))).toList())
        var g=initial.replace(initial.nodes.first { it.id=="wall_layout" }.copy(length=7,height=4))
            .replace(initial.nodes.first { it.id=="plate_layout" }.copy(length=7,height=7))
        val definition=BuildingNode("bay",BuildingKind.BAY_WINDOW,bayWindow=BayWindowConfig(wall="wall",front="wall",floor="floor",ceiling="ceiling"))
        g=g.copy(nodes=g.nodes+definition).withCurrentSchema()
        val host=g.nodes.first { it.id=="room" };val edge=SpecialRooms.anchors(g,host).first { it.direction==0 }.edge
        val attached=g.replace(host.copy(bayWindows=listOf(BayWindowInstance("bay_0","bay",edge,2))))
        assertEquals(evaluate(g).room("room").footprint.size+6,evaluate(attached).room("room").footprint.size)
        assertEquals(evaluate(g).storey("storey").footprint,evaluate(attached).storey("storey").roofFootprint)
        val layout=BuildingNode("semantic_layout",BuildingKind.ROOM_LAYOUT,inputs=listOf("wall"),roomLayout=RoomLayoutConfig(instances=listOf(
            RoomSegmentInstance("north",0,P()),RoomSegmentInstance("east",0,P(6,0,0),1),
            RoomSegmentInstance("south",0,P(0,0,6),2),RoomSegmentInstance("west",0,P(),3))))
        val semantic=g.copy(nodes=g.nodes+layout).replace(host.copy(roomConfig=RoomConfig(mode=RoomMode.SEGMENTS),layout=layout.id,geometry="",assembly=false))
        val semanticHost=semantic.nodes.first { it.id=="room" }
        val anchor=SpecialRooms.anchors(semantic,semanticHost).first { it.direction==0 }
        assertEquals(7,anchor.cells.size)
        val extended=semantic.replace(semanticHost.copy(bayWindows=listOf(BayWindowInstance("bay_0","bay",anchor.edge,2))))
        assertEquals(evaluate(semantic).storey("storey").footprint,evaluate(extended).storey("storey").roofFootprint)
        assertEquals(evaluate(semantic).room("room").footprint.size+6,evaluate(extended).room("room").footprint.size)
    }
    @Test fun `occupied floor and ceiling layers stay out of thick bay walls`() {
        for(floor in listOf(false,true)) for(ceiling in listOf(false,true)) {
            val initial=bay(base(),wallDepth=2)
            val graph=initial.copy(nodes=initial.nodes.map { n -> when(n.id) {
                "floor"->n.copy(occupied=floor,materialMode=MaterialMode.GROUPED,role="frame")
                "ceiling"->n.copy(occupied=ceiling,materialMode=MaterialMode.GROUPED,role="trim")
                else->n
            } })
            val room=evaluate(graph).room("room")
            assertTrue(room.plan.placements.filter { it.component=="wall" }.all { it.material==palette.getValue("wall") })
            assertEquals(3*(4-(if(floor) 1 else 0)-(if(ceiling) 1 else 0))*2,evaluate(graph).storey("storey").bayOpenings.size)
            assertTrue(room.plan.clear.none { p->room.plan.placements.any { it.pos==p } })
        }
    }
    @Test fun `bay passage width and height preserve surrounding host wall`() {
        var g=bay(base());val host=g.nodes.first { it.id=="room" }
        g=g.replace(host.copy(bayWindows=host.bayWindowInstances.map { it.copy(openingWidth=1,openingHeight=2) }))
        val ev=evaluate(g);assertEquals(2,ev.storey("storey").bayOpenings.size)
        val original=evaluate(g.replace(host.copy(bayWindows=emptyList()))).room("room")
        val expected=original.plan.placements.count { it.component=="wall" }-2
        assertTrue(ev.room("room").plan.placements.count { it.component=="wall" }>expected)
    }
    @Test fun `bay rejects short walls missing definitions and collisions`() {
        val g=bay(base());val host=g.nodes.first { it.id=="room" }
        assertFails { evaluate(g.replace(host.copy(bayWindows=host.bayWindowInstances.map { it.copy(offset=6) }))).room("room") }
        assertFails { evaluate(g.replace(g.nodes.first { it.id=="bay" }.copy(bayWindow=BayWindowConfig()))).room("room") }
        assertFails { evaluate(g.replace(host.copy(bayWindows=host.bayWindowInstances+host.bayWindowInstances.single().copy(id="other")))).storey("storey") }
        val storey=g.nodes.first { it.id=="storey" }
        val extra=host.copy(id="neighbor",bayWindows=emptyList())
        val overlap=g.copy(nodes=g.nodes.map { if(it.id==storey.id) it.copy(inputs=listOf("room","neighbor"),offsets=listOf(P(),P(0,0,-8))) else it }+extra)
        assertFails { evaluate(overlap).storey("storey") }
    }
    private fun roof(form:RoofForm=RoofForm.GABLE,open:Boolean=false,position:P=P(2,0,0),direction:Int=0):Pair<BuildingGraph,BuildingNode> {
        val level=BuildingNode("level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=form,axis=if(form==RoofForm.PENT) (direction+1)%4 else if(direction%2==0) 1 else 0,overhang16=0),dormers=listOf(DormerInstance("gaube","dormer",position,direction,open)))
        val roof=BuildingNode("roof",BuildingKind.ROOF,assembly=true,layout="layout",role="roof")
        return BuildingGraph(nodes=listOf(level,roof,BuildingNode("layout",BuildingKind.ROOF_LAYOUT,inputs=listOf("level")),
            BuildingNode("dormer",BuildingKind.DORMER,dormer=DormerConfig(wall="wall",front="front",roof="copy")))) to roof
    }
    private fun roofCompile(g:BuildingGraph,r:BuildingNode,cuts:MutableMap<P,ExactVolume>?=null,contacts:MutableList<RoofContact>?=null):Plan {
        val support=RoofSupport("storey_0",0,5,buildSet { for(x in 0 until 10) for(z in 0 until 14) add(P(x,0,z)) })
        val value=FixedSegment(1,1,listOf(SegmentCell(Cell())),depth=1)
        return Roofs.compile(g,r,listOf(support),palette,{ Placement(parts=listOf(Part("copycats:copycat_block"))) },
            contacts=contacts,wallSegment={ value },wallPlacement={ p,_,_ -> Placement(p,parts=listOf(Part("copycats:copycat_block"))) },ceilingCuts=cuts)
    }
    @Test fun `dormers carve both supported roofs with exact seams and explicit ceiling option`() {
        for(form in listOf(RoofForm.GABLE,RoofForm.PENT)) {
            val (g,r)=roof(form);val cuts=mutableMapOf<P,ExactVolume>();val contacts=mutableListOf<RoofContact>()
            val plan=roofCompile(g,r,cuts,contacts)
            assertTrue(plan.placements.any { it.component=="wall" })
            assertTrue(plan.placements.flatMap { it.parts }.any { it.exactClip!=null })
            assertTrue(cuts.isEmpty())
            assertTrue(contacts.any { it.instance=="storey_0/dormer:gaube" })
            val parent=contacts.single { it.id=="level" }
            assertTrue(parent.below(P(4,7,2)).equivalent(ExactVolume.box(0,0,0,16,16,16)),"The original roof-level contact must include the new dormer interior")
            val toggled=g.copy(nodes=g.nodes.map { if(it.id=="level") it.copy(dormers=it.dormerInstances.map { d->d.copy(openCeiling=true) }) else it })
            val opened=roofCompile(toggled,r,cuts)
            assertEquals(plan.placements,opened.placements)
            assertTrue(cuts.isNotEmpty())
            assertEquals(9,cuts.keys.map { it.copy(y=0) }.toSet().size)
        }
    }
    @Test fun `dormer cell and boundary frames agree in all four orientations`() {
        val positions=listOf(P(2,0,0),P(9,0,2),P(7,0,13),P(0,0,10))
        for(direction in 0..3) for(form in listOf(RoofForm.GABLE,RoofForm.PENT)) {
            val (g,r)=roof(form,position=positions[direction],direction=direction)
            val plan=roofCompile(g,r)
            val front=positions[direction]+P(2,6,0).turn(direction)
            assertTrue(plan.placements.any { it.pos==front && it.component=="wall" },"$form facing $direction needs a front wall in its actual front cell")
            val air=positions[direction]+P(2,6,2).turn(direction)
            assertTrue(air in plan.clear,"$form facing $direction needs clear interior air")
            assertTrue(plan.placements.none { it.pos==air })
        }
    }
    @Test fun `drag validation includes the other levels of the connected roof`() {
        val graph=SpecialRoomsExample.building().graph!!
        val other=graph.nodes.first { it.id=="roof_level" }.copy(id="other_level",dormers=emptyList())
        val changed=graph.copy(nodes=graph.nodes.map { if(it.id=="roof_layout") it.copy(inputs=it.inputs+other.id) else it }+other)
        assertFails { evaluate(changed).validateDormerPlacement("roof_level") }
        assertFails { evaluate(changed).validateDormerPlacement("roof_level",true) }
    }
    @Test fun `a shallower main roof uses the same exact rational seam`() {
        val (g,r)=roof()
        val shallower=g.copy(nodes=g.nodes.map { n -> when(n.id) {
            "level"->n.copy(roofLevel=n.roofLevel!!.copy(slope=RoofSlope(1,2)))
            "dormer"->n.copy(dormer=n.dormer!!.copy(wallHeight=1,slope=RoofSlope(1,2)))
            else->n
        } })
        assertTrue(roofCompile(shallower,r).placements.isNotEmpty())
    }
    @Test fun `dormer rejects uphill facing ridge crossings unsupported forms and overlap`() {
        for(args in listOf(Triple(RoofForm.HIP,P(2,0,0),0),Triple(RoofForm.GABLE,P(2,0,0),2),Triple(RoofForm.GABLE,P(2,0,5),0),Triple(RoofForm.GABLE,P(-2,0,0),0))) {
            val (g,r)=roof(args.first,position=args.second,direction=args.third);assertFails { roofCompile(g,r) }
        }
        val (g,r)=roof();val both=g.copy(nodes=g.nodes.map { if(it.id=="level") it.copy(dormers=it.dormerInstances+it.dormerInstances.single().copy(id="other")) else it })
        assertFails { roofCompile(both,r) }
    }
    @Test fun `complete example cuts only the selected ceiling and preserves building offsets`() {
        val closed=SpecialRoomsExample.building(false);val opened=SpecialRoomsExample.building(true)
        val a=Generator.generate(closed,forms,emptyMap());val b=Generator.generate(opened,forms,emptyMap())
        assertEquals(a.placements.filter { it.component!="ceiling" },b.placements.filter { it.component!="ceiling" })
        assertEquals(9,a.placements.count { it.component=="ceiling" }-b.placements.count { it.component=="ceiling" })
        assertTrue(b.raw<a.raw)
        val graph=opened.graph!!;val result=graph.resultNode();val offset=P(20,0,-12)
        val shifted=Generator.generate(opened.copy(graph=graph.replace(result.copy(buildingOffset=offset))),forms,emptyMap())
        assertEquals(b.placements.map { it.copy(pos=it.pos+offset) }.toSet(),shifted.placements.toSet())
        assertEquals(b.clear.map { it+offset }.toSet(),shifted.clear.toSet())
        val ev=evaluate(graph);assertTrue(ev.preview("bay").warnings.isNotEmpty());assertTrue(ev.preview("dormer").warnings.isNotEmpty())
    }
    @Test fun `group instances remap attached definitions and share their edits`() {
        val g=SpecialRoomsExample.building().graph!!
        val selection=setOf("room","bay","roof_level","dormer")
        val grouped=GraphGroups.create(g,selection,"Aufbauten")
        val instantiated=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copy=instantiated.nodeGroups.last();val copied=copy.nodes.map { id->instantiated.nodes.first { it.id==id } }
        val room=copied.single { it.kind==BuildingKind.ROOM };val bay=copied.single { it.kind==BuildingKind.BAY_WINDOW }
        val roof=copied.single { it.kind==BuildingKind.ROOF_LEVEL };val dormer=copied.single { it.kind==BuildingKind.DORMER }
        assertEquals(bay.id,room.bayWindowInstances.single().source);assertEquals(dormer.id,roof.dormerInstances.single().source)
        val original=instantiated.nodes.first { it.id=="bay" }
        val changed=instantiated.replace(original.copy(bayWindow=original.bayWindow!!.copy(width=4)))
        assertEquals(4,changed.nodes.first { it.id==bay.id }.bayWindow!!.width)
        BuildingGraphCompiler.structure(changed)
        val removed=changed.remove(bay.id)
        BuildingGraphCompiler.structure(removed)
    }
    @Test fun `window front segment masks and inserts survive repetition on both additions`() {
        val building=SpecialRoomsExample.building();val graph=building.graph!!
        val window=graph.nodes.first { it.id=="window" }.copy(inputs=listOf("full"),features=SegmentFeatures(openings=listOf(
            OpeningSpec("pane",size=P(1,1,1),insetSource="glass",retainSource=false))))
        val changed=graph.replace(window);val ev=evaluate(changed)
        val original=evaluate(graph)
        fun equivalent(a:Plan,b:Plan) {
            val actual=b.placements.associateBy { it.pos }
            assertEquals(a.placements.map { it.pos }.toSet(),actual.keys)
            for(p in a.placements) assertTrue(p.sameVisual(actual.getValue(p.pos)),"Fenstereinsatz bei ${p.pos}: ${p.surfaceItems()} versus ${actual.getValue(p.pos).surfaceItems()}")
        }
        equivalent(original.room("room").plan,ev.room("room").plan)
        equivalent(original.preview("roof_level"),ev.preview("roof_level"))
    }
    @Test fun `schema four roundtrip references and deletion retain old schema compatibility`() {
        val old=base();assertEquals(old.schema,old.withCurrentSchema().schema)
        val g=bay(old);assertEquals(4,g.schema)
        val restored=Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java)
        assertEquals(g,restored);BuildingGraphCompiler.structure(restored)
        val host=g.nodes.first { it.id=="room" }
        assertEquals("bay",BuildingPorts.of(host,g).single { it.key=="semantic:bay:0:source" }.source)
        assertEquals("bay",BuildingSemantics.references(host)["bay:0:source"])
        val removed=g.remove("bay");assertEquals("",removed.nodes.first { it.id=="room" }.bayWindowInstances.single().source)
        BuildingGraphCompiler.structure(removed)
        for(schema in 1..3) BuildingGraphCompiler.structure(old.copy(schema=schema))
    }
}
