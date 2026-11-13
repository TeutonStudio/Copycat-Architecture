package de.teutonstudio.copycatarchitect.domain

/** A presentation change contains references, never a replacement geometry or building recipe. */
data class DiagramPosition(val id:String,val x:Int,val y:Int)
data class DiagramConnection(val source:String,val target:String,val port:String)

object DiagramEdits {
    private fun roomPlacementFootprint(graph:BuildingGraph,room:BuildingNode):Set<P> = if(room.roomConfig?.mode==RoomMode.SEGMENTS)
        runCatching { RoomTopology.outline(graph,room).shell }.getOrDefault(emptySet()) else RoomGeometry.cells(graph.resolvedNode(room).rectangles)
    fun toggleRoomCell(original:BuildingGraph,id:String,cell:P):BuildingGraph {
        val room=original.nodes.first { it.id==id }
        require(room.kind==BuildingKind.ROOM && room.roomConfig?.mode!=RoomMode.SEGMENTS) { "Select a footprint room or edit the segment arrangement" }
        return original.reshapeRoom(id,RoomGeometry.toggleCell(original.resolvedNode(room),cell).rectangles)
    }
    /** The index identifies a room instance, including repeated inputs of the same room node. */
    fun moveStoreyRoom(original:BuildingGraph,id:String,index:Int,offset:P):BuildingGraph {
        val storey=original.nodes.first { it.id==id }
        require(storey.kind==BuildingKind.STOREY && storey.storeyMode=="rooms") { "Select a storey assembled from rooms" }
        require(index in storey.inputs.indices && original.nodes.any { it.id==storey.inputs[index] && it.kind==BuildingKind.ROOM }) { "Select a connected room" }
        val changed=original.replace(storey.copy(offsets=List(storey.inputs.size) { if(it==index) offset else storey.offsets.getOrNull(it) ?: P() }))
        BuildingGraphCompiler.structure(changed)
        return changed
    }
    private fun validate(known:Set<String>,positions:List<DiagramPosition>,connections:List<DiagramConnection>) {
        val ids=positions.map { it.id }
        require(ids.size==ids.toSet().size && ids.all { it in known }) { "Unknown or duplicated diagram node" }
        require(connections.all { it.source in ids && it.target in ids }) { "Unknown diagram connection" }
        require(connections.map { it.target to it.port }.distinct().size==connections.size) { "An input accepts one connection" }
    }
    fun shape(original:NodeGraph,groups:List<MaterialGroup>,positions:List<DiagramPosition>,connections:List<DiagramConnection>):NodeGraph {
        validate(original.nodes.map { it.id }.toSet(),positions,connections)
        require(original.nodes.single { it.kind==NodeKind.OUTPUT }.id in positions.map { it.id }) { "Keep the result node" }
        val ports=original.nodes.associate { it.id to it.inputs.indices.map(Int::toString).toSet() }
        require(connections.all { it.port in ports.getValue(it.target) }) { "Unknown shape input" }
        val byId=positions.associateBy { it.id };val wires=connections.associateBy { it.target to it.port }
        val changed=GraphGroups.synchronize(original,original.copy(nodeGroups=GraphGroups.prune(original.nodeGroups,byId.keys),nodes=original.nodes.filter { it.id in byId }.map { n ->
            val p=byId.getValue(n.id)
            n.copy(x=p.x,y=p.y,inputs=n.inputs.mapIndexed { i,_ -> wires[n.id to i.toString()]?.source.orEmpty() })
        }))
        GraphCompiler.structure(changed,groups)
        return changed
    }
    fun building(original:BuildingGraph,positions:List<DiagramPosition>,connections:List<DiagramConnection>):BuildingGraph {
        validate(original.nodes.map { it.id }.toSet(),positions,connections)
        val byId=positions.associateBy { it.id }
        require(original.resultNode().id in byId && "empty_wall" in byId) { "Keep the result and Empty wall source" }
        val ports=original.nodes.associate { it.id to BuildingPorts.of(it,original,true).associateBy(BuildingPort::key) }
        require(connections.all { it.port in ports.getValue(it.target) }) { "Unknown building input" }
        val wires=connections.associateBy { it.target to it.port }
        val changed=GraphGroups.synchronize(original,original.copy(nodeGroups=GraphGroups.prune(original.nodeGroups,byId.keys),nodes=original.nodes.filter { it.id in byId }.map { n ->
            val p=byId.getValue(n.id);var next=n.copy(x=p.x,y=p.y)
            val changedPorts=ports.getValue(n.id).values.filter { port ->
                val source=wires[n.id to port.key]?.source.orEmpty()
                source!=port.source && !(source.isEmpty() && port.source=="empty_wall" && port.key.startsWith("wall:"))
            }
            for(port in changedPorts) {
                val source=wires[n.id to port.key]?.source.orEmpty()
                if(source.isNotEmpty()) require(original.nodes.first { it.id==source }.kind in port.accepts) { "Incompatible building connection" }
                when {
                    port.key.startsWith("semantic:") -> next=BuildingSemantics.remapReferences(next) { key,ref -> if("semantic:$key"==port.key) source else ref }
                    port.key=="rotation" -> next=next.copy(rotation=source.takeIf(String::isNotEmpty))
                    port.key=="mirroring" -> next=next.copy(mirroring=source.takeIf(String::isNotEmpty))
                    port.key=="layout" -> next=next.copy(layout=source,assembly=true)
                    port.key.startsWith("region:") -> next=next.copy(regionCopies=original.resolvedNode(next).withRegionCopy(port.key,source).regionCopies)
                    port.key=="geometry" && n.kind!=BuildingKind.ROOM -> next=next.copy(geometry=source)
                    port.key=="geometry" -> {
                        if(source.isEmpty()) next=next.copy(geometry="",assembly=true)
                        else {
                            val connected=original.resolvedNode(next.copy(geometry=source,assembly=true))
                            next=RoomGeometry.reshape(original.resolvedNode(n),connected.rectangles).copy(x=p.x,y=p.y,geometry=source,assembly=true,rectangles=n.rectangles)
                        }
                    }
                    port.key=="floor" -> next=next.copy(floor=source)
                    port.key=="ceiling" -> next=next.copy(ceiling=source)
                    port.key=="corner" -> next=next.copy(corner=source)
                    port.key.startsWith("edge:") -> next=next.copy(verticalEdges=if(source.isEmpty()) next.verticalEdges.orEmpty()-port.key.removePrefix("edge:") else next.verticalEdges.orEmpty()+(port.key.removePrefix("edge:") to source))
                    port.key=="roofPeak" -> next=next.copy(roofPeak=source.takeIf(String::isNotEmpty))
                    port.key=="roof" -> next=next.copy(roof=source)
                    port.key=="area" -> next=next.copy(roofLevel=(next.roofLevel ?: RoofLevelConfig()).copy(area=source))
                    // Access ports refer to the original instance indices. Apply them together below.
                    port.key.startsWith("access:") -> Unit
                    port.key=="tread" -> next=next.copy(stairwell=(next.stairwell ?: StairwellConfig()).copy(tread=source))
                    port.key=="landing" -> next=next.copy(stairwell=(next.stairwell ?: StairwellConfig()).copy(landing=source))
                    port.key=="enclosure" -> next=when(next.kind) {
                        BuildingKind.STAIRWELL->next.copy(stairwell=(next.stairwell ?: StairwellConfig()).copy(enclosure=source))
                        BuildingKind.ELEVATOR_SHAFT->next.copy(elevatorShaft=(next.elevatorShaft ?: ElevatorShaftConfig()).copy(enclosure=source))
                        else->next.copy(serviceShaft=(next.serviceShaft ?: ServiceShaftConfig()).copy(enclosure=source))
                    }
                    port.key.startsWith("wall:") -> {
                        val edge=port.key.removePrefix("wall:")
                        next=next.copy(walls=if(n.kind==BuildingKind.ROOF && source.isEmpty()) next.walls-edge else next.walls+(edge to source.ifEmpty { "empty_wall" }))
                    }
                    port.key.startsWith("in:") -> {
                        val i=port.key.removePrefix("in:").toInt()
                        if(i<next.inputs.size || source.isNotEmpty()) {
                            val inputs=next.inputs.toMutableList();if(i==inputs.size) inputs+="";inputs[i]=source
                            var offset=next.offsets.getOrNull(i) ?: P()
                            if(n.kind==BuildingKind.STOREY && n.storeyMode=="rooms" && source.isNotEmpty() && n.inputs.getOrNull(i).isNullOrEmpty() && i>0) {
                                val room=original.nodes.first { it.id==source }
                                val max=next.inputs.mapIndexedNotNull { j,id -> original.nodes.firstOrNull { it.id==id && it.kind==BuildingKind.ROOM }?.let {
                                    roomPlacementFootprint(original,it).maxOfOrNull { p->p.x }?.plus(next.offsets.getOrNull(j)?.x ?: 0)
                                } }.maxOrNull() ?: -2
                                offset=P(max+2-(roomPlacementFootprint(original,room).minOfOrNull { it.x } ?: 0),0,0)
                            }
                            next=next.copy(inputs=inputs,offsets=if(n.kind==BuildingKind.STOREY) List(inputs.size) { j -> if(j==i) offset else next.offsets.getOrNull(j) ?: P() } else next.offsets,
                                sitePaths=if(n.inputs.getOrNull(i)!=source) next.sitePaths-i else next.sitePaths)
                        }
                    }
                }
            }
            if(changedPorts.any { it.key.startsWith("access:") }) {
                val entries=mutableListOf<VerticalAccessInstance>()
                for(port in ports.getValue(n.id).values.filter { it.key.startsWith("access:") }.sortedBy { it.key.substringAfter(':').toInt() }) {
                    val source=wires[n.id to port.key]?.source ?: continue
                    val index=port.key.substringAfter(':').toInt()
                    entries+=n.accessInstances.getOrNull(index)?.copy(source=source)
                        ?: VerticalAccessInstance(freshAccessInstanceId(n.accessInstances+entries),source)
                }
                next=next.copy(accessInstances=entries)
            }
            if(changedPorts.any { it.key.startsWith("level:") }) {
                val levels=ports.getValue(n.id).values.filter { it.key.startsWith("level:") }.mapNotNull { port ->
                    wires[n.id to port.key]?.source?.let { port.key.removePrefix("level:").toInt() to it }
                }.toMap()
                next=next.withLevels(levels)
            }
            BuildingSemantics.remapReferences(next.copy(regionCopies=next.regionCopies.filterValues { it in byId },roofConfig=next.roofConfig?.let { config->
                config.copy(wall=config.wall.takeIf { it in byId }.orEmpty(),variants=config.variants.filterValues { it in byId },families=config.families.map { family->family.copy(variants=family.variants.filterValues { it in byId }) })
            })) { _,ref -> ref.takeIf { it in byId }.orEmpty() }.trimCopycatInputs()
        }))
        BuildingGraphCompiler.structure(changed)
        val rewiredRooms=changed.nodes.filter { it.kind==BuildingKind.ROOM && it.geometry!=original.nodes.first { before -> before.id==it.id }.geometry }.map { it.id }.toSet()
        changed.validateRoomPlacements(rewiredRooms)
        return changed.remapGeometryDependents(original).withCurrentSchema()
    }
}
