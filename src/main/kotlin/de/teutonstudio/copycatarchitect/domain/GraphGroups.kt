package de.teutonstudio.copycatarchitect.domain

import java.util.UUID

/** The interface uses member indices so every instance shares the same graph definition. */
data class GraphGroupTarget(val member:Int=0,val port:String="0")
data class GraphGroupInput(val id:String="",val name:String="Eingang",val type:String="shape",
    val targets:List<GraphGroupTarget> = emptyList(),val x:Int=-240,val y:Int=0)
data class GraphGroupOutput(val id:String="",val name:String="Ausgang",val type:String="shape",
    val member:Int=-1,val x:Int=620,val y:Int=0)
data class GraphGroupPosition(val x:Int=0,val y:Int=0)
data class GraphGroupInterface(val inputs:List<GraphGroupInput> = emptyList(),val outputs:List<GraphGroupOutput> = emptyList(),
    /** Member-indexed layout in the same definition-local frame as the terminals. Null is legacy. */
    val positions:List<GraphGroupPosition>?=null)

/** Members are stored in compilation order; the editor opens their definition as a separate graph. */
data class GraphGroup(val id:String="",val name:String="Gruppe",val nodes:List<String> = emptyList(),val definitionId:String="",
    val graphInterface:GraphGroupInterface?=null,val inputConnections:Map<String,String> = emptyMap()) {
    val effectiveDefinitionId get()=definitionId.ifEmpty { id }
}

/** Groups contain nodes directly; moving a selection into a group never creates nested groups. */
object GraphGroups {
    private val idPattern=Regex("[a-zA-Z0-9_-]{1,36}")

    /** Freeze legacy layouts before changing instance positions; prefer the original definition. */
    fun captureLayouts(groups:List<GraphGroup>,positions:Map<String,GraphGroupPosition>):List<GraphGroup> {
        val layouts=groups.groupBy { it.effectiveDefinitionId }.mapValues { (definition,instances) ->
            val source=instances.firstOrNull { it.id==definition } ?: instances.first()
            source.nodes.map { positions.getValue(it) }
        }
        return groups.map { group -> group.graphInterface?.takeIf { it.positions==null }?.let { face ->
            group.copy(graphInterface=face.copy(positions=layouts.getValue(group.effectiveDefinitionId)))
        } ?: group }
    }

    /** Named terminals reserve optional slots before an instance is wired. */
    fun reserveInputs(graph:BuildingGraph):BuildingGraph {
        val sizes=mutableMapOf<String,Int>()
        graph.nodeGroups.forEach { group -> group.graphInterface?.inputs.orEmpty().flatMap { it.targets }.forEach { target ->
            val index=target.port.takeIf { it.startsWith("in:") }?.substringAfter(':')?.toIntOrNull()
            val member=group.nodes.getOrNull(target.member)
            if(index!=null && index in 0..15 && member!=null) sizes[member]=maxOf(sizes[member] ?: 0,index+1)
        } }
        return graph.copy(nodes=graph.nodes.map { node ->
            val size=sizes[node.id] ?: 0
            if(node.inputs.size>=size) node else node.copy(inputs=node.inputs+List(size-node.inputs.size) { "" })
        })
    }

    fun interfaceForInstances(groups:List<GraphGroup>,edited:GraphGroup):List<GraphGroup> = groups.map { group ->
        if(group.effectiveDefinitionId!=edited.effectiveDefinitionId) group else group.copy(nodes=if(group.id==edited.id) edited.nodes else group.nodes,graphInterface=edited.graphInterface,
            inputConnections=(if(group.id==edited.id) edited.inputConnections else group.inputConnections).filterKeys { key ->
                val next=edited.graphInterface?.inputs.orEmpty().firstOrNull { it.id==key }
                next!=null && group.graphInterface?.inputs.orEmpty().firstOrNull { it.id==key }?.type==next.type
            })
    }

    fun addMembers(graph:NodeGraph,id:String,members:List<String>):NodeGraph {
        if(members.isEmpty()) return graph
        require(graph.nodeGroups.none { group -> group.nodes.any { it in members } })
        val existing=captureLayouts(graph.nodeGroups,graph.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        val source=existing.first { it.id==id }
        val anchor=graph.nodes.first { it.id==source.nodes.first() }
        val local=source.graphInterface?.positions?.firstOrNull() ?: GraphGroupPosition(anchor.x,anchor.y)
        val added=members.map { member -> graph.nodes.first { it.id==member }.let { GraphGroupPosition(it.x-anchor.x+local.x,it.y-anchor.y+local.y) } }
        var nodes=graph.nodes
        val groups=existing.map { original ->
            if(original.effectiveDefinitionId!=source.effectiveDefinitionId) return@map original
            val target=original.copy(graphInterface=original.graphInterface?.let { face -> face.copy(positions=face.positions?.plus(added)) })
            if(target.id==id) target.copy(nodes=target.nodes+members) else {
                val mapping=source.nodes.zip(target.nodes).toMap()+members.associateWith { UUID.randomUUID().toString() }
                val targetAnchor=graph.nodes.first { it.id==target.nodes.first() }
                nodes+=members.map { member -> graph.nodes.first { it.id==member }.let { n -> n.copy(id=mapping.getValue(member),
                    x=(n.x-anchor.x+targetAnchor.x).coerceIn(-8192,8192),y=(n.y-anchor.y+targetAnchor.y).coerceIn(-8192,8192),inputs=n.inputs.map { mapping[it] ?: it }) } }
                target.copy(nodes=target.nodes+members.map(mapping::getValue))
            }
        }
        return graph.copy(nodes=nodes,nodeGroups=groups)
    }

    fun addMembers(graph:BuildingGraph,id:String,members:List<String>):BuildingGraph {
        if(members.isEmpty()) return graph
        require(graph.nodeGroups.none { group -> group.nodes.any { it in members } })
        val existing=captureLayouts(graph.nodeGroups,graph.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        val source=existing.first { it.id==id }
        val anchor=graph.nodes.first { it.id==source.nodes.first() }
        val local=source.graphInterface?.positions?.firstOrNull() ?: GraphGroupPosition(anchor.x,anchor.y)
        val added=members.map { member -> graph.nodes.first { it.id==member }.let { GraphGroupPosition(it.x-anchor.x+local.x,it.y-anchor.y+local.y) } }
        var nodes=graph.nodes
        val groups=existing.map { original ->
            if(original.effectiveDefinitionId!=source.effectiveDefinitionId) return@map original
            val target=original.copy(graphInterface=original.graphInterface?.let { face -> face.copy(positions=face.positions?.plus(added)) })
            if(target.id==id) target.copy(nodes=target.nodes+members) else {
                val mapping=source.nodes.zip(target.nodes).toMap()+members.associateWith { UUID.randomUUID().toString() }
                val targetAnchor=graph.nodes.first { it.id==target.nodes.first() }
                nodes+=members.map { member -> graph.nodes.first { it.id==member }.let { n -> remap(n) { _,ref -> mapping[ref] ?: ref }.copy(id=mapping.getValue(member),
                    x=(n.x-anchor.x+targetAnchor.x).coerceIn(-8192,8192),y=(n.y-anchor.y+targetAnchor.y).coerceIn(-8192,8192)) } }
                target.copy(nodes=target.nodes+members.map(mapping::getValue))
            }
        }
        return graph.copy(nodes=nodes,nodeGroups=groups)
    }

    fun validate(groups:List<GraphGroup>,nodeIds:Set<String>,protectedNodes:Set<String> = emptySet()) {
        require(groups.size<=nodeIds.size) { "Too many node groups" }
        require(groups.map { it.id }.distinct().size==groups.size && groups.all { it.id.matches(idPattern) && it.id !in nodeIds }) { "Node group IDs must be unique and distinct from node IDs" }
        val assigned=mutableSetOf<String>()
        groups.forEach { group ->
            require(group.definitionId.isEmpty() || group.definitionId.matches(idPattern)) { "Invalid node group definition ID" }
            require(group.name.isNotBlank() && group.name.length<=64 && group.name.none(Char::isISOControl)) { "Node group names need 1–64 visible characters" }
            require(group.nodes.isNotEmpty() && group.nodes.distinct().size==group.nodes.size && group.nodes.all { it in nodeIds && it !in protectedNodes }) { "Node groups must contain existing editable nodes exactly once" }
            require(group.nodes.all(assigned::add)) { "A node can belong to only one node group" }
            group.graphInterface?.let { face ->
                require(face.positions==null || face.positions.size==group.nodes.size && face.positions.all { it.x in -8192..8192 && it.y in -8192..8192 }) { "Invalid group member layout" }
                val ports=face.inputs.map { it.id }+face.outputs.map { it.id }
                require(ports.size<=64 && ports.distinct().size==ports.size && ports.all { it.matches(idPattern) }) { "Invalid group port IDs" }
                require((face.inputs.map { it.name }+face.outputs.map { it.name }).all { it.isNotBlank() && it.length<=64 && it.none(Char::isISOControl) }) { "Invalid group port name" }
                require(face.inputs.all { it.x in -8192..8192 && it.y in -8192..8192 } && face.outputs.all { it.x in -8192..8192 && it.y in -8192..8192 }) { "Group terminal position exceeds graph bounds" }
                require(face.inputs.all { it.targets.distinct().size==it.targets.size && it.targets.all { t -> t.member in group.nodes.indices && t.port.isNotBlank() } }) { "Invalid group input binding" }
                val targets=face.inputs.flatMap { it.targets }
                require(targets.distinct().size==targets.size) { "A group socket accepts only one input" }
                require(face.outputs.all { it.member==-1 || it.member in group.nodes.indices }) { "Invalid group output binding" }
                require(face.outputs.filter { it.member>=0 }.map { it.member }.distinct().size==face.outputs.count { it.member>=0 }) { "Export each result once" }
                require(group.inputConnections.keys.all { key -> face.inputs.any { it.id==key } } && group.inputConnections.values.all { it in nodeIds && it !in group.nodes }) { "Invalid group input connection" }
            }
        }
        require(groups.groupBy { it.effectiveDefinitionId }.values.all { instances -> instances.map { it.nodes.size }.distinct().size==1 }) { "Instances of a node group must have matching members" }
        require(groups.groupBy { it.effectiveDefinitionId }.values.all { it.map { g -> g.graphInterface }.distinct().size==1 }) { "Instances must share a graph interface" }
    }

    fun validate(graph:NodeGraph) {
        val nodes=graph.nodes.associateBy { it.id }
        validate(graph.nodeGroups,nodes.keys,graph.nodes.filter { it.kind==NodeKind.OUTPUT }.map { it.id }.toSet())
        graph.nodeGroups.groupBy { it.effectiveDefinitionId }.values.forEach { instances ->
            require(instances.map { group -> group.nodes.map { shapeTemplate(nodes.getValue(it),group) } }.distinct().size==1) { "Instances of a node group must share the same definition" }
        }
        graph.nodeGroups.forEach { group -> group.graphInterface?.let { face ->
            face.inputs.forEach { input ->
                require(input.type in setOf("shape","rotation")) { "Unknown Copycat graph input type" }
                val connection=group.inputConnections[input.id].orEmpty()
                require(connection.isEmpty() || GraphModels.shape(nodes.getValue(connection).kind).output==input.type) { "Incompatible Copycat group instance connection" }
                input.targets.forEach { target ->
                    val node=nodes.getValue(group.nodes[target.member]);val slot=target.port.toIntOrNull()
                    require(slot!=null && slot in node.inputs.indices && GraphModels.shape(node.kind).inputs?.type(slot)==input.type) { "Incompatible Copycat group input" }
                    require(node.inputs[slot]==connection) { "Connect named group inputs on the instance node" }
                }
            }
            face.outputs.forEach { output -> require(output.type in setOf("shape","rotation") && (output.member==-1 || GraphModels.shape(nodes.getValue(group.nodes[output.member]).kind).output==output.type)) { "Incompatible Copycat group output" } }
        } }
    }

    fun validate(graph:BuildingGraph) {
        val nodes=graph.nodes.associateBy { it.id }
        validate(graph.nodeGroups,nodes.keys,graph.nodes.filter { n -> n.id=="empty_wall" || n.kind==BuildingKind.BUILDING && graph.nodeGroups.none { n.id in it.nodes } }.mapTo(hashSetOf()) { it.id })
        graph.nodeGroups.groupBy { it.effectiveDefinitionId }.values.forEach { instances ->
            require(instances.map { group -> group.nodes.map { buildingTemplate(nodes.getValue(it),group) } }.distinct().size==1) { "Instances of a node group must share the same definition" }
        }
        graph.nodeGroups.forEach { group -> group.graphInterface?.let { face ->
            val types=BuildingKind.entries.map(::buildingGraphType).toSet()
            face.inputs.forEach { input ->
                require(input.type in types) { "Unknown building graph input type" }
                val connection=group.inputConnections[input.id].orEmpty()
                require(connection.isEmpty() || buildingGraphType(nodes.getValue(connection).kind)==input.type) { "Incompatible building group instance connection" }
                input.targets.forEach { target ->
                    val node=nodes.getValue(group.nodes[target.member])
                    val port=BuildingPorts.of(node,graph,true).firstOrNull { it.key==target.port }
                    require(port!=null && port.accepts.any { buildingGraphType(it)==input.type }) { "Incompatible building group input" }
                    require(port.source==connection) { "Connect named group inputs on the instance node" }
                }
            }
            face.outputs.forEach { output -> require(output.type in types && (output.member==-1 || buildingGraphType(nodes.getValue(group.nodes[output.member]).kind)==output.type)) { "Incompatible building group output" } }
        } }
    }

    fun create(graph:NodeGraph,selection:Set<String>,name:String="Gruppe"):NodeGraph =
        graph.copy(nodeGroups=create(graph.nodeGroups,graph.nodes.filter { it.kind!=NodeKind.OUTPUT }.map { it.id },selection,name))

    fun create(graph:BuildingGraph,selection:Set<String>,name:String="Gruppe"):BuildingGraph =
        graph.copy(nodeGroups=create(graph.nodeGroups,graph.nodes.filter { it.kind!=BuildingKind.BUILDING && it.id!="empty_wall" }.map { it.id },selection,name))

    fun ungroup(graph:NodeGraph,id:String):NodeGraph=graph.copy(nodeGroups=graph.nodeGroups.filter { it.id!=id })
    fun ungroup(graph:BuildingGraph,id:String):BuildingGraph=graph.copy(nodeGroups=graph.nodeGroups.filter { it.id!=id })

    fun rename(graph:NodeGraph,id:String,name:String):NodeGraph=graph.copy(nodeGroups=rename(graph.nodeGroups,id,name))
    fun rename(graph:BuildingGraph,id:String,name:String):BuildingGraph=graph.copy(nodeGroups=rename(graph.nodeGroups,id,name))

    /** Deletion retains surviving members and discards groups after their last node is removed. */
    fun prune(groups:List<GraphGroup>,nodeIds:Set<String>):List<GraphGroup> = groups.mapNotNull { group ->
            group.nodes.filter { it in nodeIds }.takeIf { it.isNotEmpty() }?.let { retained ->
            val indices=group.nodes.indices.filter { group.nodes[it] in nodeIds }.withIndex().associate { it.value to it.index }
            group.copy(nodes=retained,inputConnections=group.inputConnections.filterValues { it in nodeIds },graphInterface=group.graphInterface?.let { face -> face.copy(
                inputs=face.inputs.map { input -> input.copy(targets=input.targets.mapNotNull { t -> indices[t.member]?.let { t.copy(member=it) } }) },
                outputs=face.outputs.map { output -> output.copy(member=indices[output.member] ?: -1) },
                positions=face.positions?.filterIndexed { index,_ -> index in indices }) })
        }
    }

    /** A fully duplicated group becomes a separate group; partial selections stay ungrouped. */
    fun duplicate(groups:List<GraphGroup>,copiedIds:Map<String,String>,nodeIds:Set<String>):List<GraphGroup> {
        var result=groups
        groups.filter { it.nodes.all(copiedIds::containsKey) }.forEach { group ->
            result=result+group.copy(id=freshId(result,nodeIds),name=uniqueName(result,group.name),nodes=group.nodes.map(copiedIds::getValue),definitionId=group.effectiveDefinitionId,
                inputConnections=group.inputConnections.mapValues { copiedIds[it.value] ?: it.value })
        }
        return result
    }

    fun instantiate(graph:NodeGraph,id:String):NodeGraph {
        val existing=captureLayouts(graph.nodeGroups,graph.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        val group=existing.first { it.id==id }
        val members=group.nodes.map { member -> graph.nodes.first { it.id==member } }
        require(members.none { it.kind==NodeKind.OUTPUT } && graph.nodes.size+members.size<=64 && graph.nodes.count { it.kind==NodeKind.INPUT || it.kind==NodeKind.SAVED }+members.count { it.kind==NodeKind.INPUT || it.kind==NodeKind.SAVED }<=32) { "Group instance exceeds shape node limits" }
        val ids=members.associate { it.id to UUID.randomUUID().toString() }
        val (dx,dy)=placement(graph.nodes.map { it.x to it.y },members.map { it.x to it.y })
        val copies=members.map { n -> n.copy(id=ids.getValue(n.id),x=n.x+dx,y=n.y+dy,inputs=n.inputs.map { ids[it] ?: it }) }
        val groups=existing+group.copy(id=freshId(existing,(graph.nodes+copies).map { it.id }.toSet()),name=uniqueName(existing,group.name),nodes=group.nodes.map(ids::getValue),definitionId=group.effectiveDefinitionId)
        return graph.copy(nodes=graph.nodes+copies,nodeGroups=groups)
    }

    fun instantiate(graph:BuildingGraph,id:String):BuildingGraph {
        val existing=captureLayouts(graph.nodeGroups,graph.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        val group=existing.first { it.id==id }
        val members=group.nodes.map { member -> graph.nodes.first { it.id==member } }
        require(members.none { it.id=="empty_wall" } && graph.nodes.size+members.size<=96) { "Group instance exceeds building node limits" }
        val ids=members.associate { it.id to UUID.randomUUID().toString() }
        val (dx,dy)=placement(graph.nodes.map { it.x to it.y },members.map { it.x to it.y })
        val copies=members.map { n -> remap(n) { _,ref -> ids[ref] ?: ref }.copy(id=ids.getValue(n.id),x=n.x+dx,y=n.y+dy) }
        val groups=existing+group.copy(id=freshId(existing,(graph.nodes+copies).map { it.id }.toSet()),name=uniqueName(existing,group.name),nodes=group.nodes.map(ids::getValue),definitionId=group.effectiveDefinitionId)
        return graph.copy(nodes=graph.nodes+copies,nodeGroups=groups)
    }

    /** Shared settings and internal cables follow the definition; incoming cables and positions stay local. */
    fun synchronize(before:NodeGraph,after:NodeGraph):NodeGraph {
        var changed=after
        val removed=removedMembers(before.nodeGroups,before.nodes.map { it.id }.toSet()-after.nodes.map { it.id }.toSet())
        removed.filter { id -> changed.nodes.any { it.id==id } }.forEach { changed=changed.removeLocal(it) }
        val groups=prune(changed.nodeGroups,changed.nodes.map { it.id }.toSet())
        val original=before.nodes.associateBy { it.id };val nodes=changed.nodes.associateBy { it.id }.toMutableMap()
        val previousGroups=before.nodeGroups.associateBy { it.id }
        for(instances in groups.groupBy { it.effectiveDefinitionId }.values.filter { it.size>1 }) {
            require(instances.map { it.nodes.size }.distinct().size==1) { "Instances of a node group must have matching members" }
            for(slot in instances.first().nodes.indices) {
                val source=instances.firstOrNull { instance ->
                    val previous=previousGroups[instance.id] ?: return@firstOrNull false
                    if(previous.nodes.size!=instance.nodes.size) return@firstOrNull false
                    val old=original[previous.nodes[slot]] ?: return@firstOrNull false
                    shapeTemplate(old,previous)!=shapeTemplate(nodes.getValue(instance.nodes[slot]),instance)
                } ?: continue
                val changed=nodes.getValue(source.nodes[slot]);val mapping=source.nodes.withIndex().associate { it.value to it.index }
                instances.filter { it.id!=source.id }.forEach { target ->
                    val current=nodes.getValue(target.nodes[slot]);val targetMembers=target.nodes.toSet()
                    nodes[current.id]=changed.copy(id=current.id,x=current.x,y=current.y,inputs=changed.inputs.mapIndexed { index,ref ->
                        mapping[ref]?.let { target.nodes[it] } ?: current.inputs.getOrNull(index).orEmpty().takeUnless { it in targetMembers }.orEmpty()
                    })
                }
            }
        }
        return changed.copy(nodes=changed.nodes.map { nodes.getValue(it.id) },nodeGroups=groups)
    }

    fun synchronize(before:BuildingGraph,after:BuildingGraph):BuildingGraph {
        var changed=after
        val removed=removedMembers(before.nodeGroups,before.nodes.map { it.id }.toSet()-after.nodes.map { it.id }.toSet())
        removed.filter { id -> changed.nodes.any { it.id==id } }.forEach { changed=changed.removeLocal(it) }
        val groups=prune(changed.nodeGroups,changed.nodes.map { it.id }.toSet())
        val original=before.nodes.associateBy { it.id };val nodes=changed.nodes.associateBy { it.id }.toMutableMap()
        val previousGroups=before.nodeGroups.associateBy { it.id }
        for(instances in groups.groupBy { it.effectiveDefinitionId }.values.filter { it.size>1 }) {
            require(instances.map { it.nodes.size }.distinct().size==1) { "Instances of a node group must have matching members" }
            for(slot in instances.first().nodes.indices) {
                val source=instances.firstOrNull { instance ->
                    val previous=previousGroups[instance.id] ?: return@firstOrNull false
                    if(previous.nodes.size!=instance.nodes.size) return@firstOrNull false
                    val old=original[previous.nodes[slot]] ?: return@firstOrNull false
                    buildingTemplate(old,previous)!=buildingTemplate(nodes.getValue(instance.nodes[slot]),instance)
                } ?: continue
                val changed=nodes.getValue(source.nodes[slot]);val mapping=source.nodes.withIndex().associate { it.value to it.index }
                instances.filter { it.id!=source.id }.forEach { target ->
                    val current=nodes.getValue(target.nodes[slot])
                    val reshaped=if(current.rectangles!=changed.rectangles && (current.kind==BuildingKind.ROOM || current.kind==BuildingKind.STOREY && current.storeyMode=="walls"))
                        RoomGeometry.reshape(current,changed.rectangles) else current
                    val incoming=references(reshaped);val targetMembers=target.nodes.toSet()
                    val transferred=remap(changed) { key,ref ->
                        mapping[ref]?.let { target.nodes[it] } ?: incoming[key].orEmpty().takeUnless { it in targetMembers }.orEmpty()
                    }
                    // Sparse edge maps may use different external sockets in each instance.
                    val externalEdges=reshaped.verticalEdges.orEmpty().filterValues { it !in targetMembers }
                    nodes[current.id]=transferred.copy(id=current.id,x=current.x,y=current.y,
                        verticalEdges=(externalEdges+transferred.verticalEdges.orEmpty()).filterValues(String::isNotEmpty).takeIf { it.isNotEmpty() })
                }
            }
        }
        return reserveInputs(changed.copy(nodes=changed.nodes.map { nodes.getValue(it.id) },nodeGroups=groups))
    }

    private fun shapeTemplate(node:GraphNode,group:GraphGroup):GraphNode {
        val members=group.nodes.withIndex().associate { it.value to "member_${it.index}" }
        return node.copy(id="",x=0,y=0,inputs=node.inputs.map { members[it].orEmpty() })
    }

    private fun buildingTemplate(node:BuildingNode,group:GraphGroup):BuildingNode {
        val members=group.nodes.withIndex().associate { it.value to "member_${it.index}" }
        val mapped=remap(node) { _,ref -> members[ref].orEmpty() }.copy(id="",x=0,y=0).trimCopycatInputs()
        // Map keys for external sockets can differ when their geometry inputs create different edges.
        return mapped.copy(walls=mapped.walls.filterValues(String::isNotEmpty),verticalEdges=mapped.verticalEdges.orEmpty().filterValues(String::isNotEmpty),regionCopies=mapped.regionCopies.filterValues(String::isNotEmpty),
            roofConfig=mapped.roofConfig?.let { config -> config.copy(variants=config.variants.filterValues(String::isNotEmpty),
                families=config.families.map { family -> family.copy(variants=family.variants.filterValues(String::isNotEmpty)) }) })
    }

    private fun references(node:BuildingNode):Map<String,String> = buildMap {
        remap(node) { key,ref -> put(key,ref);ref }
    }

    /** All node references, including configuration references which have no visible input socket. */
    internal fun remap(node:BuildingNode,reference:(String,String)->String):BuildingNode = BuildingSemantics.remapReferences(node.copy(
        inputs=node.inputs.mapIndexed { i,ref -> reference("in:$i",ref) },
        rotation=node.rotation?.let { reference("rotation",it) },mirroring=node.mirroring?.let { reference("mirroring",it) },
        walls=node.walls.mapValues { (key,ref) -> reference("wall:$key",ref) },
        verticalEdges=node.verticalEdges?.mapValues { (key,ref) -> reference("edge:$key",ref) },
        floor=reference("floor",node.floor),ceiling=reference("ceiling",node.ceiling),corner=reference("corner",node.corner),
        roof=reference("roof",node.roof),layout=reference("layout",node.layout),geometry=reference("geometry",node.geometry),
        levels=node.levels.mapValues { (key,ref) -> reference("level:$key",ref) },
        regionCopies=node.regionCopies.mapValues { (key,ref) -> reference(key,ref) },
        roofLevel=node.roofLevel?.let { it.copy(area=reference("area",it.area)) },
        stairwell=node.stairwell?.let { it.copy(tread=reference("tread",it.tread),landing=reference("landing",it.landing),enclosure=reference("enclosure",it.enclosure)) },
        elevatorShaft=node.elevatorShaft?.let { it.copy(enclosure=reference("enclosure",it.enclosure)) },
        serviceShaft=node.serviceShaft?.let { it.copy(enclosure=reference("enclosure",it.enclosure)) },
        accessInstances=node.accessInstances.mapIndexed { i,instance -> instance.copy(source=reference("access:$i",instance.source)) },
        roofConfig=node.roofConfig?.let { config -> config.copy(wall=reference("template:wall",config.wall),
            variants=config.variants.mapValues { (key,ref) -> reference("template:$key",ref) },
            families=config.families.mapIndexed { i,family -> family.copy(variants=family.variants.mapValues { (key,ref) -> reference("family:$i:$key",ref) }) }) }
    )) { key,ref -> reference("semantic:$key",ref) }

    private fun removedMembers(groups:List<GraphGroup>,removed:Set<String>):Set<String> = buildSet {
        for(instances in groups.groupBy { it.effectiveDefinitionId }.values) {
            // Removing the entire member list deletes that instance, rather than its shared definition.
            val slots=instances.filter { group -> group.nodes.any { it !in removed } }.flatMap { group ->
                group.nodes.withIndex().filter { it.value in removed }.map { it.index }
            }.toSet()
            instances.forEach { group -> slots.mapNotNullTo(this) { group.nodes.getOrNull(it) } }
        }
    }

    /** Prefer unused space around the complete graph, preserving the members' relative positions. */
    private fun placement(nodes:List<Pair<Int,Int>>,members:List<Pair<Int,Int>>):Pair<Int,Int> {
        val minX=members.minOf { it.first };val maxX=members.maxOf { it.first }
        val minY=members.minOf { it.second };val maxY=members.maxOf { it.second }
        val candidates=listOf(
            nodes.maxOf { it.first }+240-minX to 0,
            0 to nodes.maxOf { it.second }+180-minY,
            nodes.minOf { it.first }-240-maxX to 0,
            0 to nodes.minOf { it.second }-180-maxY)
        return candidates.firstOrNull { (dx,dy) ->
            minX+dx>=-8192 && maxX+dx<=8192 && minY+dy>=-8192 && maxY+dy<=8192
        } ?: (216.coerceIn(-8192-minX,8192-maxX) to 32.coerceIn(-8192-minY,8192-maxY))
    }

    private fun create(groups:List<GraphGroup>,nodeIds:List<String>,selection:Set<String>,name:String):List<GraphGroup> {
        val members=nodeIds.filter { it in selection }
        require(members.isNotEmpty()) { "Select at least one node to group" }
        val remaining=prune(groups,groups.flatMap { it.nodes }.toSet()-members.toSet()).map { group ->
            val previous=groups.first { it.id==group.id }
            if(group.nodes==previous.nodes) group else group.copy(definitionId=UUID.randomUUID().toString())
        }
        return remaining+GraphGroup(freshId(groups,nodeIds.toSet()),uniqueName(remaining,checkedName(name)),members)
    }

    private fun rename(groups:List<GraphGroup>,id:String,name:String):List<GraphGroup> {
        val definition=groups.firstOrNull { it.id==id }?.effectiveDefinitionId ?: error("Unknown node group")
        val next=uniqueName(groups.filter { it.effectiveDefinitionId!=definition },checkedName(name))
        return groups.map { if(it.effectiveDefinitionId==definition) it.copy(name=next) else it }
    }

    private fun checkedName(name:String):String = name.trim().also {
        require(it.isNotEmpty() && it.length<=64 && it.none(Char::isISOControl)) { "Node group names need 1–64 visible characters" }
    }

    private fun uniqueName(groups:List<GraphGroup>,base:String):String {
        val names=groups.map { it.name }.toSet()
        if(base !in names) return base
        var index=2
        while(true) {
            val suffix=" $index"
            val candidate=base.take(64-suffix.length)+suffix
            if(candidate !in names) return candidate
            index++
        }
    }

    private fun freshId(groups:List<GraphGroup>,nodeIds:Set<String>):String {
        val occupied=nodeIds+groups.map { it.id }
        return generateSequence { UUID.randomUUID().toString() }.first { it !in occupied }
    }
}
