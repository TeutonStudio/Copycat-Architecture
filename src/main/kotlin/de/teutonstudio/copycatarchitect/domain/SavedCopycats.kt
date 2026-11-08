package de.teutonstudio.copycatarchitect.domain

import java.util.UUID

/** A bounded, flattened snapshot. Reuse never depends on a mutable library or nests whole graphs. */
data class SavedCopycat(val id:String="",val version:Int=1,val name:String="Copycat",val parts:List<Part> = emptyList())

object SavedCopycats {
    data class Insertion(val form:Form,val nodeId:String,val groups:Map<String,String>)

    fun insert(target:Form,source:Form,x:Int=0,y:Int=0,replaceId:String?=null,turn:Int=0):Insertion {
        require(turn in 0..3) { "Invalid saved Copycat rotation" }
        val saved=Geometry.validate(source)
        val graph=GraphPresentation.shape(target)
        val previous=replaceId?.let { id -> graph.nodes.single { it.id==id && it.kind==NodeKind.SAVED } }
        val otherNodes=graph.nodes.filter { it.id!=replaceId }
        val used=otherNodes.flatMap { n -> GraphCompiler.sourceParts(n).map { it.group }+n.inputGroups+listOf(n.materialGroup,n.previewGroup) }.filter(String::isNotEmpty).toSet()
        val obsolete=previous?.savedCopycat?.parts.orEmpty().map { it.group }.toSet()-used
        var existing=target.groups.filter { it.id !in obsolete }
        val sourceGroups=saved.groups.filter { g -> saved.parts.any { it.group==g.id } }.sortedBy { it.priority }
        if(existing==DEFAULT_GROUPS && sourceGroups.size==8 && used.isEmpty()) existing=emptyList()
        require(existing.size+sourceGroups.size<=8) { "A form supports at most 8 material groups; remove unused groups before inserting a saved Copycat" }
        require(previous!=null || graph.nodes.size<64) { "At most 64 nodes" }
        require(previous!=null || graph.nodes.count { it.kind==NodeKind.INPUT || it.kind==NodeKind.SAVED }<32) { "At most 32 source nodes" }
        val mapping=sourceGroups.associate { it.id to UUID.randomUUID().toString() }
        // Preserve both relative priority orders, with the inserted source in the foreground.
        val added=sourceGroups.map { it.copy(id=mapping.getValue(it.id)) }
        val priorities=(existing.sortedBy { it.priority }+added).mapIndexed { i,g -> g.id to i }.toMap()
        val groups=(existing+added).map { it.copy(priority=priorities.getValue(it.id)) }
        val snapshot=SavedCopycat(saved.id,saved.version,saved.name,Geometry.rotate(saved.parts,turn).map { it.copy(group=mapping.getValue(it.group)) })
        val node=previous?.copy(savedCopycat=snapshot) ?: GraphNode(UUID.randomUUID().toString(),NodeKind.SAVED,x=x.coerceIn(-8000,8000),y=y.coerceIn(-8000,8000),savedCopycat=snapshot)
        val changed=if(previous==null) graph.copy(nodes=graph.nodes+node) else graph.replace(node)
        GraphCompiler.structure(changed,groups)
        return Insertion(target.copy(parts=emptyList(),groups=groups,graph=changed),node.id,mapping)
    }
}
