package de.teutonstudio.copycatarchitect.domain

import java.util.UUID

/** Saved editor organization. Members keep their original IDs, sockets, and evaluation order. */
data class GraphGroup(val id:String="",val name:String="Gruppe",val nodes:List<String> = emptyList(),val definitionId:String="") {
    val effectiveDefinitionId get()=definitionId.ifEmpty { id }
}

/** Groups contain nodes directly; moving a selection into a group never creates nested groups. */
object GraphGroups {
    private val idPattern=Regex("[a-zA-Z0-9_-]{1,36}")

    fun validate(groups:List<GraphGroup>,nodeIds:Set<String>,protectedNodes:Set<String> = emptySet()) {
        require(groups.size<=nodeIds.size) { "Too many node groups" }
        require(groups.map { it.id }.distinct().size==groups.size && groups.all { it.id.matches(idPattern) && it.id !in nodeIds }) { "Node group IDs must be unique and distinct from node IDs" }
        val assigned=mutableSetOf<String>()
        groups.forEach { group ->
            require(group.definitionId.isEmpty() || group.definitionId.matches(idPattern)) { "Invalid node group definition ID" }
            require(group.name.isNotBlank() && group.name.length<=64 && group.name.none(Char::isISOControl)) { "Node group names need 1–64 visible characters" }
            require(group.nodes.isNotEmpty() && group.nodes.distinct().size==group.nodes.size && group.nodes.all { it in nodeIds && it !in protectedNodes }) { "Node groups must contain existing editable nodes exactly once" }
            require(group.nodes.all(assigned::add)) { "A node can belong to only one node group" }
        }
        require(groups.groupBy { it.effectiveDefinitionId }.values.all { instances -> instances.map { it.nodes.size }.distinct().size==1 }) { "Instances of a node group must have matching members" }
    }

    fun validate(graph:NodeGraph) {
        val nodes=graph.nodes.associateBy { it.id }
        validate(graph.nodeGroups,nodes.keys,graph.nodes.filter { it.kind==NodeKind.OUTPUT }.map { it.id }.toSet())
        graph.nodeGroups.groupBy { it.effectiveDefinitionId }.values.forEach { instances ->
            require(instances.map { group -> group.nodes.map { shapeTemplate(nodes.getValue(it),group) } }.distinct().size==1) { "Instances of a node group must share the same definition" }
        }
    }

    fun validate(graph:BuildingGraph) {
        val nodes=graph.nodes.associateBy { it.id }
        validate(graph.nodeGroups,nodes.keys,graph.nodes.filter { it.kind==BuildingKind.BUILDING || it.id=="empty_wall" }.map { it.id }.toSet())
        graph.nodeGroups.groupBy { it.effectiveDefinitionId }.values.forEach { instances ->
            require(instances.map { group -> group.nodes.map { buildingTemplate(nodes.getValue(it),group) } }.distinct().size==1) { "Instances of a node group must share the same definition" }
        }
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
        group.nodes.filter { it in nodeIds }.takeIf { it.isNotEmpty() }?.let {
            group.copy(nodes=it)
        }
    }

    /** A fully duplicated group becomes a separate group; partial selections stay ungrouped. */
    fun duplicate(groups:List<GraphGroup>,copiedIds:Map<String,String>,nodeIds:Set<String>):List<GraphGroup> {
        var result=groups
        groups.filter { it.nodes.all(copiedIds::containsKey) }.forEach { group ->
            result=result+GraphGroup(freshId(result,nodeIds),uniqueName(result,group.name),group.nodes.map(copiedIds::getValue),group.effectiveDefinitionId)
        }
        return result
    }

    fun instantiate(graph:NodeGraph,id:String):NodeGraph {
        val group=graph.nodeGroups.first { it.id==id }
        val members=group.nodes.map { member -> graph.nodes.first { it.id==member } }
        require(members.none { it.kind==NodeKind.OUTPUT } && graph.nodes.size+members.size<=64 && graph.nodes.count { it.kind==NodeKind.INPUT || it.kind==NodeKind.SAVED }+members.count { it.kind==NodeKind.INPUT || it.kind==NodeKind.SAVED }<=32) { "Group instance exceeds shape node limits" }
        val ids=members.associate { it.id to UUID.randomUUID().toString() }
        val (dx,dy)=placement(graph.nodes.map { it.x to it.y },members.map { it.x to it.y })
        val copies=members.map { n -> n.copy(id=ids.getValue(n.id),x=n.x+dx,y=n.y+dy,inputs=n.inputs.map { ids[it] ?: it }) }
        val groups=graph.nodeGroups+GraphGroup(freshId(graph.nodeGroups,(graph.nodes+copies).map { it.id }.toSet()),uniqueName(graph.nodeGroups,group.name),group.nodes.map(ids::getValue),group.effectiveDefinitionId)
        return graph.copy(nodes=graph.nodes+copies,nodeGroups=groups)
    }

    fun instantiate(graph:BuildingGraph,id:String):BuildingGraph {
        val group=graph.nodeGroups.first { it.id==id }
        val members=group.nodes.map { member -> graph.nodes.first { it.id==member } }
        require(members.none { it.kind==BuildingKind.BUILDING || it.id=="empty_wall" } && graph.nodes.size+members.size<=96) { "Group instance exceeds building node limits" }
        val ids=members.associate { it.id to UUID.randomUUID().toString() }
        val (dx,dy)=placement(graph.nodes.map { it.x to it.y },members.map { it.x to it.y })
        val copies=members.map { n -> remap(n) { _,ref -> ids[ref] ?: ref }.copy(id=ids.getValue(n.id),x=n.x+dx,y=n.y+dy) }
        val groups=graph.nodeGroups+GraphGroup(freshId(graph.nodeGroups,(graph.nodes+copies).map { it.id }.toSet()),uniqueName(graph.nodeGroups,group.name),group.nodes.map(ids::getValue),group.effectiveDefinitionId)
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
        return changed.copy(nodes=changed.nodes.map { nodes.getValue(it.id) },nodeGroups=groups)
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
    private fun remap(node:BuildingNode,reference:(String,String)->String):BuildingNode = BuildingSemantics.remapReferences(node.copy(
        inputs=node.inputs.mapIndexed { i,ref -> reference("in:$i",ref) },
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
