package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GraphGroupsTest {
    private val shape=NodeGraph(nodes=listOf(
        GraphNode("source",part=Part("copycats:copycat_block"),x=10,y=30),
        GraphNode("external",part=Part("copycats:copycat_slab"),x=10,y=210),
        GraphNode("union",NodeKind.UNION,listOf("source","external"),x=260,y=100),
        GraphNode("result",NodeKind.OUTPUT,listOf("union"),x=520,y=30)))
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))

    @Test fun `named groups retain shape and building compilation and survive JSON`() {
        val grouped=GraphGroups.create(shape,setOf("source","union"),"Fassade")
        assertEquals(shape.nodes,grouped.nodes)
        assertEquals(GraphCompiler.compile(shape),GraphCompiler.compile(grouped))
        assertEquals(grouped,Gson().fromJson(Gson().toJson(grouped),NodeGraph::class.java))
        assertEquals(emptyList(),Gson().fromJson("""{"schema":2,"nodes":[]}""",NodeGraph::class.java).nodeGroups)

        val building=BuildingGraph.starter();val groupedBuilding=GraphGroups.create(building,setOf("wall","floor","ceiling","room"),"Raum")
        fun compile(graph:BuildingGraph)=BuildingGraphCompiler.compile(Building(schema=2,graph=graph),forms,emptyMap())
        assertEquals(compile(building),compile(groupedBuilding))
        assertEquals(groupedBuilding,Gson().fromJson(Gson().toJson(groupedBuilding),BuildingGraph::class.java))
        assertEquals(emptyList(),Gson().fromJson("""{"schema":1,"nodes":[]}""",BuildingGraph::class.java).nodeGroups)
    }

    @Test fun `selection creates disjoint groups and protects result nodes`() {
        val first=GraphGroups.create(shape,setOf("source","union","result"),"Gruppe")
        assertEquals(listOf("source","union"),first.nodeGroups.single().nodes)
        val next=GraphGroups.create(first,setOf("union","external"),"Gruppe")
        assertEquals(listOf("Gruppe","Gruppe 2"),next.nodeGroups.map { it.name })
        assertEquals(listOf("source"),next.nodeGroups.first().nodes)
        GraphCompiler.structure(next,DEFAULT_GROUPS)
        assertFails { GraphGroups.create(shape,setOf("result")) }
        val building=GraphGroups.create(BuildingGraph.starter(),setOf("building","empty_wall","room"))
        assertEquals(listOf("room"),building.nodeGroups.single().nodes)
        val renamed=GraphGroups.rename(next,next.nodeGroups.last().id,"Details")
        assertEquals("Details",renamed.nodeGroups.last().name)
        assertEquals(first.nodes,GraphGroups.ungroup(first,first.nodeGroups.single().id).nodes)
    }

    @Test fun `invalid identities memberships nesting and protected members are rejected`() {
        val good=GraphGroup("group","Fassade",listOf("source","union"))
        val invalid=listOf(
            listOf(good,good),
            listOf(good.copy(id="source")),
            listOf(good.copy(name=" ")),
            listOf(good.copy(nodes=emptyList())),
            listOf(good.copy(nodes=listOf("missing"))),
            listOf(good.copy(nodes=listOf("result"))),
            listOf(good.copy(nodes=listOf("source","source"))),
            listOf(good,GraphGroup("other","Other",listOf("union"))),
            listOf(good,GraphGroup("other","Other",listOf("group"))),
            listOf(good,GraphGroup("other","Other",listOf("external"),"group")))
        invalid.forEach { groups -> assertFails { GraphCompiler.structure(shape.copy(nodeGroups=groups),DEFAULT_GROUPS) } }
        val building=BuildingGraph.starter()
        assertFails { BuildingGraphCompiler.structure(building.copy(nodeGroups=listOf(GraphGroup("group","Invalid",listOf("empty_wall"))))) }
    }

    @Test fun `deletion cleans memberships and duplicating complete groups retains reusable definition`() {
        val grouped=GraphGroups.create(shape,setOf("source","union"))
        assertEquals(listOf("union"),grouped.remove("source").nodeGroups.single().nodes)
        assertTrue(GraphSelectionEdits.remove(grouped,setOf("source","union")).nodeGroups.isEmpty())
        val (duplicated,selection)=GraphSelectionEdits.duplicate(grouped,setOf("source","union"))
        assertEquals(2,duplicated.nodeGroups.size)
        assertEquals(selection,duplicated.nodeGroups.last().nodes.toSet())
        assertEquals(grouped.nodeGroups.single().effectiveDefinitionId,duplicated.nodeGroups.last().effectiveDefinitionId)
        val (partial,_)=GraphSelectionEdits.duplicate(grouped,setOf("source"))
        assertEquals(grouped.nodeGroups,partial.nodeGroups)
        val building=GraphGroups.create(BuildingGraph.starter(),setOf("floor","ceiling"))
        assertEquals(listOf("ceiling"),building.remove("floor").nodeGroups.single().nodes)
        assertTrue(building.remove("floor").remove("ceiling").nodeGroups.isEmpty())
    }

    @Test fun `shape instances share settings and internal connections while inputs and positions remain independent`() {
        val grouped=GraphGroups.create(shape,setOf("source","union"),"Fassade")
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        GraphCompiler.structure(instanced,DEFAULT_GROUPS)
        val original=instanced.nodeGroups.first();val instance=instanced.nodeGroups.last()
        val copySource=instanced.nodes.first { it.id==instance.nodes[0] };val copyUnion=instanced.nodes.first { it.id==instance.nodes[1] }
        assertEquals(listOf(copySource.id,"external"),copyUnion.inputs)
        assertEquals(GraphCompiler.compile(shape),GraphCompiler.compile(instanced))
        val restored=Gson().fromJson(Gson().toJson(instanced),NodeGraph::class.java)
        assertEquals(instanced,restored)
        assertEquals(GraphCompiler.compile(instanced),GraphCompiler.compile(restored))
        val renamed=GraphGroups.rename(instanced,instance.id,"Bauteil")
        assertTrue(renamed.nodeGroups.all { it.name=="Bauteil" })

        val wired=instanced.replace(copyUnion.copy(inputs=listOf(copySource.id,"source")))
        assertEquals(listOf("source","external"),wired.nodes.first { it.id=="union" }.inputs)
        val modified=wired.replace(wired.nodes.first { it.id=="source" }.copy(quarterTurns=1,x=100))
        assertEquals(1,modified.nodes.first { it.id==copySource.id }.quarterTurns)
        assertEquals(copySource.x,modified.nodes.first { it.id==copySource.id }.x)
        assertEquals(listOf(copySource.id,"source"),modified.nodes.first { it.id==copyUnion.id }.inputs)
        assertEquals(modified,GraphGroups.synchronize(wired,modified))
        val rewired=modified.replace(modified.nodes.first { it.id==original.nodes[1] }.copy(inputs=listOf("external","external")))
        assertEquals(listOf("","source"),rewired.nodes.first { it.id==copyUnion.id }.inputs)
        assertEquals(rewired,Gson().fromJson(Gson().toJson(rewired),NodeGraph::class.java))
    }

    @Test fun `building instances remap references and share settings while retaining incoming materials`() {
        val starter=BuildingGraph.starter()
        val grouped=GraphGroups.create(starter,setOf("floor","ceiling","room"),"Raum")
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        BuildingGraphCompiler.structure(instanced)
        val instance=instanced.nodeGroups.last();val copiedRoom=instanced.nodes.first { it.id==instance.nodes.last() }
        assertEquals(instance.nodes[0],copiedRoom.floor);assertEquals(instance.nodes[1],copiedRoom.ceiling)
        assertTrue(copiedRoom.walls.values.all { it=="wall" })
        val restored=Gson().fromJson(Gson().toJson(instanced),BuildingGraph::class.java)
        assertEquals(instanced,restored)
        fun compile(graph:BuildingGraph)=BuildingGraphCompiler.compile(Building(schema=2,graph=graph),forms,emptyMap())
        assertEquals(compile(starter),compile(restored))
        val copyWall=starter.nodes.first { it.id=="wall" }.copy(id="alternative",block="minecraft:stone")
        val withAlternative=instanced.copy(nodes=instanced.nodes+copyWall)
        val wired=withAlternative.replace(copiedRoom.copy(walls=copiedRoom.walls.mapValues { "alternative" }))
        assertTrue(wired.nodes.first { it.id=="room" }.walls.values.all { it=="wall" })
        val modified=wired.replace(wired.nodes.first { it.id=="room" }.copy(height=6,name="Hoher Raum"))
        val changedCopy=modified.nodes.first { it.id==copiedRoom.id }
        assertEquals(6,changedCopy.height);assertEquals("Hoher Raum",changedCopy.name)
        assertTrue(changedCopy.walls.values.all { it=="alternative" })
        assertEquals(instance.nodes[0],changedCopy.floor);assertEquals(copiedRoom.x,changedCopy.x)
        assertEquals(modified,GraphGroups.synchronize(wired,modified))
        BuildingGraphCompiler.structure(modified)
        assertEquals(modified,Gson().fromJson(Gson().toJson(modified),BuildingGraph::class.java))
    }

    @Test fun `removing a member edits every instance and disconnects surviving references`() {
        val grouped=GraphGroups.create(shape,setOf("source","union"))
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val changed=instanced.remove("source")
        GraphCompiler.structure(changed,DEFAULT_GROUPS)
        assertEquals(1,changed.nodeGroups.map { it.effectiveDefinitionId }.distinct().size)
        assertTrue(changed.nodeGroups.all { it.nodes.size==1 })
        assertTrue(changed.nodes.filter { it.kind==NodeKind.UNION }.all { it.inputs.first().isEmpty() })
        assertTrue(changed.nodes.none { it.id==instanced.nodeGroups.last().nodes.first() })
        assertEquals(changed,GraphGroups.synchronize(instanced,changed))

        val buildingGroup=GraphGroups.create(BuildingGraph.starter(),setOf("floor","ceiling","room"))
        val buildingInstances=GraphGroups.instantiate(buildingGroup,buildingGroup.nodeGroups.single().id)
        val removed=buildingInstances.remove("floor")
        BuildingGraphCompiler.structure(removed)
        assertTrue(removed.nodeGroups.all { it.nodes.size==2 })
        assertTrue(removed.nodes.filter { it.kind==BuildingKind.ROOM }.all { it.floor.isEmpty() })
    }

    @Test fun `instantiation preserves relative placement at graph bounds`() {
        val moved=shape.copy(nodes=shape.nodes.map { if(it.id=="source") it.copy(x=8190,y=-8192) else if(it.id=="union") it.copy(x=8192,y=-8180) else it })
        val grouped=GraphGroups.create(moved,setOf("source","union"))
        val instantiated=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copies=instantiated.nodeGroups.last().nodes.map { id -> instantiated.nodes.first { it.id==id } }
        assertEquals(2,copies[1].x-copies[0].x)
        assertEquals(12,copies[1].y-copies[0].y)
        assertEquals(8192,copies[1].x)
        GraphCompiler.structure(instantiated,DEFAULT_GROUPS)
    }

    @Test fun `new building instance uses free space beyond the result node`() {
        val graph=BuildingGraph(nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,x=20,y=20,block="minecraft:air",length=1),
            BuildingNode("wall",BuildingKind.SEGMENT,x=260,y=80,module=""),
            BuildingNode("building",BuildingKind.BUILDING,x=520,y=80)))
        val grouped=GraphGroups.create(graph,setOf("wall"))
        val instantiated=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copy=instantiated.nodes.first { it.id==instantiated.nodeGroups.last().nodes.single() }
        assertEquals(760,copy.x)
        assertEquals(80,copy.y)
        assertTrue(copy.x-graph.nodes.maxOf { it.x }>=240)
        BuildingGraphCompiler.structure(instantiated)
    }

    @Test fun `deleting an entire instance retains the other instance definition`() {
        val grouped=GraphGroups.create(shape,setOf("source","union"))
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val changed=GraphSelectionEdits.remove(instanced,grouped.nodeGroups.single().nodes.toSet())
        assertEquals(listOf(instanced.nodeGroups.last()),changed.nodeGroups)
        assertTrue(changed.nodeGroups.single().nodes.all { id -> changed.nodes.any { it.id==id } })
        GraphCompiler.structure(changed,DEFAULT_GROUPS)
        assertEquals(changed,GraphGroups.synchronize(instanced,changed))
    }

    @Test fun `imported inconsistent shared definitions are rejected`() {
        val grouped=GraphGroups.create(shape,setOf("source","union"))
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val sourceCopy=instanced.nodeGroups.last().nodes.first()
        val inconsistent=instanced.copy(nodes=instanced.nodes.map { if(it.id==sourceCopy) it.copy(quarterTurns=1) else it })
        assertFails { GraphCompiler.structure(Gson().fromJson(Gson().toJson(inconsistent),NodeGraph::class.java),DEFAULT_GROUPS) }
        val building=GraphGroups.create(BuildingGraph.starter(),setOf("floor","ceiling","room"))
        val buildingInstances=GraphGroups.instantiate(building,building.nodeGroups.single().id)
        val copiedRoom=buildingInstances.nodeGroups.last().nodes.last()
        val inconsistentBuilding=buildingInstances.copy(nodes=buildingInstances.nodes.map { if(it.id==copiedRoom) it.copy(height=6) else it })
        assertFails { BuildingGraphCompiler.structure(Gson().fromJson(Gson().toJson(inconsistentBuilding),BuildingGraph::class.java)) }
    }

    @Test fun `shared geometry edits rebuild external room consumers in every instance and reject overlap`() {
        val starter=BuildingAssembly.starter()
        val grouped=GraphGroups.create(starter,setOf("room_geometry"))
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val peerGeometry=instances.nodeGroups.last().nodes.single()
        val room=instances.nodes.first { it.id=="room" }
        val peer=room.copy(id="peer_room",geometry=peerGeometry)
        val graph=instances.copy(nodes=instances.nodes+peer)
        val coordinates=RoomGeometry.cells(listOf(Rect(width=4,length=3))).toList()
        val changed=graph.editRoomGeometry("room_geometry",coordinates)
        for(id in listOf("room","peer_room")) {
            val resolved=changed.resolvedNode(id)
            assertEquals(coordinates.toSet(),RoomGeometry.cells(resolved.rectangles))
            assertEquals(RoomGeometry.edges(resolved.rectangles).map { it.id }.toSet(),resolved.walls.keys)
            assertTrue(resolved.walls.values.all { it=="wall" })
        }
        val fixedGeometry=instances.nodes.first { it.id=="room_geometry" }.copy(id="fixed_geometry")
        val fixed=room.copy(id="fixed_room",geometry=fixedGeometry.id)
        val storey=graph.nodes.first { it.id=="storey" }.copy(inputs=listOf(peer.id,fixed.id),offsets=listOf(P(),P(4,0,0)))
        val withFixed=graph.copy(nodes=graph.nodes.map { if(it.id==storey.id) storey else it }+fixedGeometry+fixed)
        BuildingGraphCompiler.structure(withFixed)
        assertFails { withFixed.editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=5,length=3))).toList()) }
    }

    @Test fun `shared direct room reshaping preserves independent wall assignments on changed edges`() {
        val grouped=GraphGroups.create(BuildingGraph.starter(),setOf("room"))
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copiedRoom=instances.nodes.first { it.id==instances.nodeGroups.last().nodes.single() }
        val alternative=instances.nodes.first { it.id=="wall" }.copy(id="alternative",block="minecraft:stone")
        val graph=instances.copy(nodes=instances.nodes+alternative).replace(copiedRoom.copy(walls=copiedRoom.walls.mapValues { "alternative" }))
        val changed=graph.reshapeRoom("room",listOf(Rect(width=4,length=3)))
        BuildingGraphCompiler.structure(changed)
        val copy=changed.nodes.first { it.id==copiedRoom.id }
        assertEquals(listOf(Rect(width=4,length=3)),copy.rectangles)
        assertEquals(RoomGeometry.edges(copy.rectangles).map { it.id }.toSet(),copy.walls.keys)
        assertTrue(copy.walls.values.all { it=="alternative" })
    }
}
