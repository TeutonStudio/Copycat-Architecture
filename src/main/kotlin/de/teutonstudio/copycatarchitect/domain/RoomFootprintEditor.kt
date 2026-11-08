package de.teutonstudio.copycatarchitect.domain

object RoomFootprintEditor {
    private fun editable(node:BuildingNode) {
        require(node.kind==BuildingKind.ROOM || node.kind==BuildingKind.STOREY && node.storeyMode=="walls") { "Only a room has an editable block footprint" }
    }

    private fun bounded(cell:P)=cell.y==0 && cell.x in -32..63 && cell.z in -32..63

    fun validate(cells:Set<P>) {
        require(cells.isNotEmpty()) { "A room needs at least one block" }
        require(cells.size<=4096 && cells.all(::bounded)) { "Room blocks exceed grid bounds" }
        val remaining=cells.toMutableSet()
        val queue=ArrayDeque<P>()
        val start=cells.first();remaining.remove(start);queue.addLast(start)
        while(queue.isNotEmpty()) {
            val current=queue.removeFirst()
            RoomGeometry.directions.forEach { direction ->
                val neighbor=current+direction
                if(remaining.remove(neighbor)) queue.addLast(neighbor)
            }
        }
        require(remaining.isEmpty()) { "Room footprint must stay connected" }
        val minimumX=cells.minOf { it.x }-1;val maximumX=cells.maxOf { it.x }+1
        val minimumZ=cells.minOf { it.z }-1;val maximumZ=cells.maxOf { it.z }+1
        val outside=mutableSetOf(P(minimumX,0,minimumZ));queue.addLast(outside.first())
        while(queue.isNotEmpty()) {
            val current=queue.removeFirst()
            RoomGeometry.directions.forEach { direction ->
                val neighbor=current+direction
                if(neighbor.x in minimumX..maximumX && neighbor.z in minimumZ..maximumZ && neighbor !in cells && outside.add(neighbor)) queue.addLast(neighbor)
            }
        }
        require((minimumX..maximumX).all { column -> (minimumZ..maximumZ).all { row -> P(column,0,row) in cells || P(column,0,row) in outside } }) { "Room footprint must not enclose holes" }
    }

    fun toggleCell(node:BuildingNode,cell:P):BuildingNode {
        editable(node)
        return edit(node,setOf(cell),cell in RoomGeometry.cells(node.rectangles))
    }

    fun rectangle(node:BuildingNode,first:P,last:P,erase:Boolean=false):BuildingNode {
        require(bounded(first) && bounded(last)) { "Room blocks exceed grid bounds" }
        val cells=buildSet {
            for(column in minOf(first.x,last.x)..maxOf(first.x,last.x)) for(row in minOf(first.z,last.z)..maxOf(first.z,last.z)) add(P(column,0,row))
        }
        return edit(node,cells,erase)
    }

    fun edit(node:BuildingNode,cells:Set<P>,erase:Boolean=false):BuildingNode {
        editable(node)
        require(cells.all(::bounded)) { "Room blocks exceed grid bounds" }
        val previous=RoomGeometry.cells(node.rectangles)
        val next=if(erase) previous-cells else previous+cells
        validate(next)
        return if(next==previous) node else RoomGeometry.reshape(node,RoomGeometry.fromCells(next))
    }
}
