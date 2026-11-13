package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import java.util.UUID

/** Adapts named graph terminals to the existing typed compiler sockets. */
internal object GroupGraphInterfaces {
    fun initializeInstances(data:KilaGraphData,id:String):KilaGraphData {
        val original=data.nodeGroups.first { it.id==id }
        if(original.graphInterface!=null) return data.copy(nodeGroups=GraphGroups.captureLayouts(data.nodeGroups,data.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) }))
        val definition=data.nodeGroups.firstOrNull { it.id==original.effectiveDefinitionId } ?: data.nodeGroups.first { it.effectiveDefinitionId==original.effectiveDefinitionId }
        val edited=initialize(data,definition)
        val face=checkNotNull(edited.graphInterface)
        val nodes=data.nodes.associateBy { it.id }
        return data.copy(nodeGroups=data.nodeGroups.map { group ->
            if(group.effectiveDefinitionId!=original.effectiveDefinitionId) group else group.copy(graphInterface=face,inputConnections=face.inputs.mapNotNull { input ->
                input.targets.firstNotNullOfOrNull { target -> group.nodes.getOrNull(target.member)?.let { member ->
                    data.links.firstOrNull { it.target==member && nodes[member]?.inputKeys?.getOrNull(it.slot)==target.port && it.source !in group.nodes }?.source
                } }?.let { input.id to it }
            }.toMap())
        })
    }
    fun inputId(id:String)="@input:$id"
    fun outputId(id:String)="@output:$id"
    fun initialize(data:KilaGraphData,group:GraphGroup):GraphGroup {
        if(group.graphInterface!=null) return group
        val nodes=data.nodes.associateBy { it.id };val members=group.nodes.toSet()
        val positions=group.nodes.map { nodes.getValue(it).let { node -> GraphGroupPosition(node.x,node.y) } }
        val top=positions.minOf { it.y }
        val incoming=data.links.filter { it.target in members && it.source !in members }
        val inputs=incoming.groupBy { it.source }.values.mapIndexed { index,links ->
            val first=links.first();val target=nodes.getValue(first.target)
            GraphGroupInput(UUID.randomUUID().toString(),target.inputs[first.slot],nodes.getValue(first.source).outputPorts().getOrNull(first.sourceSlot)?.type ?: target.inputTypes[first.slot],
                links.map { GraphGroupTarget(group.nodes.indexOf(it.target),nodes.getValue(it.target).inputKeys[it.slot]) },
                (positions.minOf { it.x }-240).coerceIn(-8192,8192),(top+index*100).coerceIn(-8192,8192))
        }
        val outputs=group.nodes.filter { id -> nodes[id]?.outputPorts()?.isNotEmpty()==true && (data.links.none { it.source==id } || data.links.any { it.source==id && it.target !in members }) }.mapIndexed { i,id ->
            val node=nodes.getValue(id)
            GraphGroupOutput(UUID.randomUUID().toString(),node.title,node.outputPorts().first().type,group.nodes.indexOf(id),(positions.maxOf { it.x }+250).coerceIn(-8192,8192),(top+i*100).coerceIn(-8192,8192))
        }
        return group.copy(graphInterface=GraphGroupInterface(inputs,outputs,positions),inputConnections=inputs.mapIndexed { i,input -> input.id to incoming.groupBy { it.source }.values.elementAt(i).first().source }.toMap())
    }

    fun withInterface(data:KilaGraphData,edited:GraphGroup):KilaGraphData {
        val groups=GraphGroups.interfaceForInstances(data.nodeGroups,edited)
        val before=data.nodeGroups.associateBy { it.id }
        val nodes=data.nodes.associateBy { it.id }
        val removed=mutableSetOf<Pair<String,Int>>()
        val added=mutableListOf<KilaLink>()
        val outputs=mutableMapOf<String,String>()
        groups.filter { it.effectiveDefinitionId==edited.effectiveDefinitionId }.forEach { group ->
            val old=before[group.id]
            old?.graphInterface?.outputs.orEmpty().forEach { port ->
                val source=old?.nodes?.getOrNull(port.member) ?: return@forEach
                val next=group.graphInterface?.outputs?.firstOrNull { it.id==port.id }?.member?.let { group.nodes.getOrNull(it) }.orEmpty()
                if(source!=next) outputs[source]=next
            }
            before[group.id]?.graphInterface?.inputs.orEmpty().flatMap { it.targets }.forEach { target ->
                group.nodes.getOrNull(target.member)?.let { id -> nodes[id]?.inputKeys?.indexOf(target.port)?.takeIf { it>=0 }?.let { removed+=id to it } }
            }
            group.graphInterface?.inputs.orEmpty().forEach { input ->
                input.targets.forEach { target -> group.nodes.getOrNull(target.member)?.let { id ->
                    nodes[id]?.inputKeys?.indexOf(target.port)?.takeIf { it>=0 }?.let { slot ->
                        removed+=id to slot
                        group.inputConnections[input.id]?.let { added+=KilaLink(it,id,slot) }
                    }
                } }
            }
        }
        return data.copy(nodeGroups=groups,links=data.links.filterNot { it.target to it.slot in removed }.mapNotNull { link ->
            val owner=groups.firstOrNull { link.source in it.nodes }
            if(owner!=null && link.target !in owner.nodes && link.source in outputs) outputs.getValue(link.source).takeIf(String::isNotEmpty)?.let { link.copy(source=it) }
            else link
        }+added)
    }
}

/** Only the group's definition and its explicit terminal nodes exist in this view. */
internal class KilaGroupGraphProjection(private val original:KilaGraphData,groupId:String):KilaProjection {
    private val group=GroupGraphInterfaces.initialize(original,original.nodeGroups.first { it.id==groupId })
    private val face=checkNotNull(group.graphInterface)
    private val nodes=original.nodes.associateBy { it.id }
    private val memberSet=group.nodes.toSet()
    private val positions=face.positions ?: checkNotNull(GraphGroups.captureLayouts(original.nodeGroups,original.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) }).first { it.id==groupId }.graphInterface?.positions)
    private val memberPositions=group.nodes.zip(positions).toMap()
    override val display=KilaGraphData(
        original.nodes.filter { it.id in memberSet }.map { node -> memberPositions.getValue(node.id).let { node.copy(x=it.x,y=it.y,deletable=false) } }+
            face.inputs.map { KilaNode(GroupGraphInterfaces.inputId(it.id),it.name,it.x,it.y,emptyList(),deletable=false,outputType=it.type) }+
            face.outputs.map { KilaNode(GroupGraphInterfaces.outputId(it.id),it.name,it.x,it.y,listOf(it.name),output=false,deletable=false,inputTypes=listOf(it.type)) },
        original.links.filter { it.source in memberSet && it.target in memberSet }+
            face.inputs.flatMap { input -> input.targets.mapNotNull { target -> group.nodes.getOrNull(target.member)?.let { id ->
                nodes[id]?.inputKeys?.indexOf(target.port)?.takeIf { it>=0 }?.let { KilaLink(GroupGraphInterfaces.inputId(input.id),id,it) }
            } } }+
            face.outputs.mapNotNull { output -> group.nodes.getOrNull(output.member)?.let { KilaLink(it,GroupGraphInterfaces.outputId(output.id),0) } })

    override fun groupForView(id:String):GraphGroup?=null
    override fun inspectorSelection(viewIds:Set<String>)=viewIds.singleOrNull().orEmpty()
    override fun actualSelection(viewIds:Set<String>)=viewIds.filterTo(linkedSetOf()) { id -> display.nodes.any { it.id==id } }
    override fun viewIdForNode(id:String)=id

    override fun restore(displaySnapshot:KilaGraphData):KilaGraphData {
        val moved=displaySnapshot.nodes.associateBy { it.id }
        val retained=group.nodes.filter { it in moved }
        val indices=retained.withIndex().associate { it.value to it.index }
        val inputs=face.inputs.map { input ->
            val id=GroupGraphInterfaces.inputId(input.id);val position=moved[id]
            input.copy(x=position?.x ?: input.x,y=position?.y ?: input.y,targets=displaySnapshot.links.filter { it.source==id }.map { link ->
                val member=checkNotNull(indices[link.target]) { "Group inputs connect to member nodes" }
                val node=nodes.getValue(link.target)
                require(input.type in node.inputTypes[link.slot].split('|')) { "Incompatible group input" }
                GraphGroupTarget(member,node.inputKeys[link.slot])
            })
        }
        val outputs=face.outputs.map { output ->
            val id=GroupGraphInterfaces.outputId(output.id);val position=moved[id]
            val source=displaySnapshot.links.singleOrNull { it.target==id }?.source
            if(source!=null) require(nodes[source]?.outputPorts()?.firstOrNull()?.type==output.type) { "Incompatible group output" }
            output.copy(x=position?.x ?: output.x,y=position?.y ?: output.y,member=indices[source] ?: -1)
        }
        val layout=retained.map { id -> moved.getValue(id).let { GraphGroupPosition(it.x,it.y) } }
        val edited=group.copy(nodes=retained,graphInterface=face.copy(inputs=inputs,outputs=outputs,
            positions=layout.takeIf { face.positions!=null || layout!=positions }))
        val outside=original.links.filter { it.target !in memberSet && it.source !in (memberSet-retained.toSet()) }
        val inside=displaySnapshot.links.filter { it.source in retained && it.target in retained }
        val changed=original.copy(nodes=original.nodes.mapNotNull { old ->
            old.takeIf { it.id !in memberSet || it.id in moved }
        },links=outside+inside)
        // Shared definitions keep independent external bindings for each instance.
        val result=GroupGraphInterfaces.withInterface(changed,edited)
        val links=result.links.toSet()
        return result.copy(links=original.links.filter { it in links }+result.links.filter { it !in original.links })
    }
}
