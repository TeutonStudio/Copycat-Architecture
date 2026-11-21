package de.teutonstudio.copycatarchitect.domain

object ShaftSegments {
    val kinds=setOf(BuildingKind.SERVICE_SHAFT_SEGMENT,BuildingKind.ELEVATOR_SHAFT_SEGMENT)
    fun source(graph:BuildingGraph,id:String):BuildingNode? {
        val visited=mutableSetOf<String>()
        fun find(key:String):BuildingNode? {
            if(!visited.add(key)) return null
            val n=graph.nodes.firstOrNull { it.id==key }?.let { GraphConditions.buildingNode(graph,it.id) } ?: return null
            if(n.kind in kinds) return n
            if(n.kind !in SegmentBooleans.kinds && n.kind !in setOf(BuildingKind.PATTERN,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_TRANSFORM)) return null
            return n.inputs.firstNotNullOfOrNull(::find)
        }
        return find(id)
    }
    fun generate(n:BuildingNode,height:Int,stops:List<Int>,source:(String)->SegmentCell):FixedSegment {
        require(height in 1..128) { "Dynamische Schachthöhe muss 1–128 Blöcke betragen" }
        val elevator=n.kind==BuildingKind.ELEVATOR_SHAFT_SEGMENT
        val a=n.serviceShaft ?: ServiceShaftConfig();val b=n.elevatorShaft ?: ElevatorShaftConfig()
        a.validate();b.validate()
        val width=if(elevator) b.width else a.width;val depth=if(elevator) b.depth else a.depth
        val enclosed=if(elevator) b.enclosed else a.enclosed
        val ref=if(elevator) b.enclosure else a.enclosure
        val wall=if(ref.isEmpty()) SegmentCell(Cell(block="minecraft:stone_bricks")) else source(ref)
        val border=if(enclosed) 1 else 0
        val cells=buildList {
            for(x in 0 until width+2*border) for(z in 0 until depth+2*border) for(y in 0 until height) {
                val core=x in border until width+border && z in border until depth+border
                val door=elevator && z==0 && x in border until width+border && stops.any { y in it until minOf(height,it+b.headroom) }
                val pos=P(x-border,y,z-border)
                add(if(core||door) SegmentCell(Cell(pos,block="minecraft:air",empty=true)) else wall.copy(cell=wall.cell.copy(pos=pos)))
            }
        }
        return FixedSegment(width+2*border,height,cells,depth=depth+2*border,origin=P(-border,0,-border))
    }
}
