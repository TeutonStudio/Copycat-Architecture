package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import java.util.UUID

object WindowEditors {
    fun definition(node:BuildingNode,commit:(BuildingNode)->Unit) {
        var draft=node
        StructurePropertyScreen.open(tr("bg_window"),{
            int("Breite",draft.length,1..32) { draft=draft.copy(length=it) }
            int("Höhe",draft.height,1..16) { draft=draft.copy(height=it) }
            hint("Rahmen und Glas über den Segmentanschluss verbinden.")
        }) { commit(draft) }
    }
    fun instances(node:BuildingNode,graph:BuildingGraph,commit:(BuildingNode)->Unit) {
        var items=node.windows.orEmpty();var selected=0
        val walls=runCatching { StoreyWindows.walls(graph,node) }.getOrDefault(emptyList())
        val definitions=graph.nodes.filter { it.kind==BuildingKind.WINDOW }
        StructurePropertyScreen.open(tr("bg_window"),{
            action("Fensterinstanz hinzufügen") {
                require(items.size<64)
                val a=walls.firstOrNull()
                items=items+WindowInstance(UUID.randomUUID().toString(),definitions.firstOrNull()?.id.orEmpty(),a?.roomIndex ?: 0,a?.edge.orEmpty());selected=items.lastIndex
            }
            if(items.isNotEmpty()) {
                selected=selected.coerceIn(items.indices)
                val i=items[selected]
                fun patch(next:WindowInstance) { items=items.mapIndexed { index,item->if(index==selected) next else item } }
                choice("Fensterinstanz",selected,items.indices.toList(),{ "${it+1}: ${items[it].id}" }) { selected=it }
                choice("Fensterdefinition",i.source,listOf("")+definitions.map { it.id },{ id->definitions.firstOrNull { it.id==id }?.name?.ifBlank { id } ?: "Nicht verbunden" }) { patch(i.copy(source=it)) }
                val a=walls.firstOrNull { it.roomIndex==i.roomIndex && it.edge==i.edge }
                choice("Zielwand",a,walls.map { it as WindowWall? },{ wall->wall?.let { "Raum ${it.roomIndex+1}: ${it.edge} (${it.cells.size})" } ?: "Wand wählen" }) { wall->if(wall!=null) patch(i.copy(roomIndex=wall.roomIndex,edge=wall.edge,offset=0)) }
                int("Position entlang der Wand",i.offset,0..128) { patch(i.copy(offset=it)) }
                int("Einbauhöhe",i.sill,0..128) { patch(i.copy(sill=it)) }
                int("Tiefenversatz",i.depthOffset,-128..128) { patch(i.copy(depthOffset=it)) }
                toggle("Innenwand und Fassade automatisch öffnen",i.autoOpening) { patch(i.copy(autoOpening=it)) }
                action("Fensterinstanz entfernen") { items=items.filterIndexed { index,_->index!=selected };selected=0 }
            }
        }) { commit(node.copy(windows=items)) }
    }
}
