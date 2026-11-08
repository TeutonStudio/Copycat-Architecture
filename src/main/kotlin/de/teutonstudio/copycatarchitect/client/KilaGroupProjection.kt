package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.GraphGroup

/** Folds groups for navigation while keeping the saved nodes and their internal wires intact. */
class KilaGroupProjection(private val original:KilaGraphData,expanded:Set<String>) {
    private val nodes=original.nodes.associateBy { it.id }
    private val groups=original.nodeGroups.filter { group -> group.nodes.any { it in nodes } }
    private val groupsByView=groups.associateBy { groupViewId(it.id) }
    private val collapsed=groups.filter { it.id !in expanded }
    private val collapsedByNode=collapsed.flatMap { group -> group.nodes.map { it to group } }.toMap()
    private val linksByTarget=if(collapsed.isEmpty()) emptyMap() else original.links.groupBy(KilaLink::target)
    private val linksBySource=if(collapsed.isEmpty()) emptyMap() else original.links.groupBy(KilaLink::source)
    private val groupInputs=collapsed.associate { group ->
        val members=group.nodes.toSet()
        group.id to group.nodes.mapNotNull(nodes::get).flatMap { node ->
            val internalSlots=linksByTarget[node.id].orEmpty().filter { it.source in members }.mapTo(hashSetOf(),KilaLink::slot)
            node.inputs.indices.filter { slot -> slot !in internalSlots }
                .map { node.id to it }
        }
    }
    private val groupOutputs=collapsed.associate { group ->
        val members=group.nodes.toSet()
        group.id to group.nodes.mapNotNull(nodes::get).filter { node ->
            val outgoing=linksBySource[node.id].orEmpty()
            hasOutput(node) && (outgoing.isEmpty() || outgoing.any { it.target !in members })
        }.map { it.id }
    }
    private val inputSlots=groupInputs.mapValues { (_,inputs) -> inputs.withIndex().associate { (slot,ref) -> ref to slot } }
    private val outputSlots=groupOutputs.mapValues { (_,outputs) -> outputs.withIndex().associate { (slot,ref) -> ref to slot } }
    private val proxies=groups.associate { group ->
        val members=group.nodes.mapNotNull(nodes::get)
        val folded=group.id !in expanded
        val inputs=if(folded) groupInputs.getValue(group.id) else emptyList()
        val outputs=if(folded) groupOutputs.getValue(group.id) else emptyList()
        group.id to KilaNode(groupViewId(group.id),group.name,members.minOf { it.x },members.minOf { it.y }-if(folded) 0 else 72,
            inputs.map { (id,slot) -> "${nodes.getValue(id).title} · ${nodes.getValue(id).inputs[slot]}" },
            output=false,deletable=members.all { it.deletable },
            inputTypes=inputs.map { (id,slot) -> nodes.getValue(id).inputTypes.getOrNull(slot) ?: "shape" },
            outputs=outputs.map { id -> nodes.getValue(id).let { KilaOutput(it.title,it.outputs.firstOrNull()?.type ?: it.outputType) } })
    }

    val display=KilaGraphData(
        original.nodes.filter { it.id !in collapsedByNode }+groups.map { proxies.getValue(it.id) },
        original.links.mapNotNull { link ->
            val from=collapsedByNode[link.source]
            val to=collapsedByNode[link.target]
            if(from!=null && from==to) return@mapNotNull null
            val sourceSlot=if(from!=null) outputSlots.getValue(from.id)[link.source] ?: return@mapNotNull null else link.sourceSlot
            val targetSlot=if(to!=null) inputSlots.getValue(to.id)[link.target to link.slot] ?: return@mapNotNull null else link.slot
            if(sourceSlot<0 || targetSlot<0) return@mapNotNull null
            KilaLink(from?.let { groupViewId(it.id) } ?: link.source,to?.let { groupViewId(it.id) } ?: link.target,targetSlot,sourceSlot)
        },original.nodeGroups)

    fun groupViewId(groupId:String):String="@group:$groupId"
    fun groupForView(id:String):GraphGroup?=groupsByView[id]

    /** The inspector keeps a group header's identity; edit commands still use its members. */
    fun inspectorSelection(viewIds:Set<String>):String = viewIds.singleOrNull()?.let { id ->
        id.takeIf { it in groupsByView || it in nodes }
    }.orEmpty()

    fun actualSelection(viewIds:Set<String>):Set<String> = viewIds.flatMap { id ->
        groupForView(id)?.nodes ?: listOfNotNull(id.takeIf { it in nodes })
    }.filterTo(linkedSetOf()) { it in nodes }

    fun viewIdForNode(id:String):String=collapsedByNode[id]?.let { groupViewId(it.id) } ?: id

    fun inputRef(viewId:String,slot:Int):Pair<String,Int>? {
        val group=groupForView(viewId)
        if(group!=null) return groupInputs[group.id]?.getOrNull(slot)
        return nodes[viewId]?.takeIf { slot in it.inputs.indices }?.let { viewId to slot }
    }

    fun outputRef(viewId:String,slot:Int):String? {
        val group=groupForView(viewId)
        if(group!=null) return groupOutputs[group.id]?.getOrNull(slot)
        val node=nodes[viewId] ?: return null
        val count=if(node.outputs.isNotEmpty()) node.outputs.size else if(node.output) 1 else 0
        return viewId.takeIf { slot in 0 until count }
    }

    /** Reverses only visible edits; collapsed members and internal wires never come from the canvas. */
    fun restore(displaySnapshot:KilaGraphData):KilaGraphData {
        val snapshot=displaySnapshot.nodes.associateBy { it.id }
        val removedMembers=groups.filter { groupViewId(it.id) !in snapshot }.flatMap { it.nodes }.toSet()
        val positions=mutableMapOf<String,Pair<Int,Int>>()
        groups.filter { groupViewId(it.id) in snapshot }.forEach { group ->
            val proxy=proxies.getValue(group.id)
            val movedProxy=snapshot.getValue(proxy.id)
            val requestedDx=movedProxy.x.toLong()-proxy.x
            val requestedDy=movedProxy.y.toLong()-proxy.y
            val members=group.nodes.mapNotNull { id ->
                val old=nodes[id] ?: return@mapNotNull null
                if(id in collapsedByNode) old else snapshot[id]?.let { moved ->
                    // A native multi-selection may already have moved the header and members.
                    old.copy(x=if(moved.x.toLong()-old.x==requestedDx) old.x else moved.x,
                        y=if(moved.y.toLong()-old.y==requestedDy) old.y else moved.y)
                }
            }
            if(members.isNotEmpty()) {
                val dx=translation(requestedDx,members.minOf { it.x },members.maxOf { it.x })
                val dy=translation(requestedDy,members.minOf { it.y },members.maxOf { it.y })
                members.forEach { positions[it.id]=(it.x+dx).toInt() to (it.y+dy).toInt() }
            }
        }
        val restoredNodes=original.nodes.mapNotNull { old ->
            if(old.id in removedMembers) return@mapNotNull null
            if(old.id !in collapsedByNode && old.id !in snapshot) return@mapNotNull null
            val point=positions[old.id] ?: snapshot[old.id]?.let { it.x to it.y } ?: return@mapNotNull null
            old.copy(x=point.first,y=point.second)
        }
        val remaining=restoredNodes.mapTo(mutableSetOf()) { it.id }
        val hiddenLinks=original.links.filter { link ->
            collapsedByNode[link.source]?.let { it==collapsedByNode[link.target] }==true
        }
        val visibleLinks=displaySnapshot.links.mapNotNull { link ->
            val source=outputRef(link.source,link.sourceSlot) ?: return@mapNotNull null
            val target=inputRef(link.target,link.slot) ?: return@mapNotNull null
            KilaLink(source,target.first,target.second,if(groupForView(link.source)!=null) 0 else link.sourceSlot)
        }
        val editedLinks=(hiddenLinks+visibleLinks).filter { it.source in remaining && it.target in remaining }.toSet()
        // An unchanged native snapshot must compare equal, including the document's wire order.
        val originalLinks=original.links.toHashSet()
        val links=original.links.filter { it in editedLinks }+editedLinks.filter { it !in originalLinks }
        return KilaGraphData(restoredNodes,links,original.nodeGroups.mapNotNull { group ->
            group.nodes.filter { it in remaining }.takeIf { it.isNotEmpty() }?.let { group.copy(nodes=it) }
        })
    }

    private fun hasOutput(node:KilaNode)=node.output || node.outputs.isNotEmpty()

    private fun translation(requested:Long,min:Int,max:Int):Long {
        if(requested==0L) return 0L
        val lower=-8192L-min;val upper=8192L-max
        // Invalid visible member edits are left for document validation; a translation cannot
        // repair an over-wide selection without changing the members' relative positions.
        return if(lower<=upper) requested.coerceIn(lower,upper) else 0L
    }
}
