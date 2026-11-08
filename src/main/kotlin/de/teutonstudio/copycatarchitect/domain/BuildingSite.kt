package de.teutonstudio.copycatarchitect.domain

/** The building node owns placement instances; garden/path nodes remain reusable definitions. */
object BuildingSite {
    const val BODY="building"
    data class Part(val key:String,val source:BuildingNode,val index:Int=-1,val position:P=P(),val points:List<P> = emptyList()) {
        val isBody get()=index<0
        val isPath get()=!isBody && source.kind==BuildingKind.PATH
        val isAccess get()=key.startsWith("access:")
    }
    fun validate(graph:BuildingGraph,node:BuildingNode) {
        require(node.kind==BuildingKind.BUILDING)
        validatePosition(node.buildingOffset)
        require(node.sitePaths.size<=16) { "Too many path instances" }
        node.sitePaths.forEach { (index,points) ->
            val source=graph.nodes.firstOrNull { it.id==node.inputs.getOrNull(index) && it.kind==BuildingKind.PATH }
            require(index in node.inputs.indices && source!=null) { "Path points need a connected path input" }
            LandscapeGeometry.validate(source.copy(coordinates=points))
        }
    }
    private fun validatePosition(p:P) { require(p.y==0 && p.x in -128..128 && p.z in -128..128) { "Site position must be within -128..128 on X/Z" } }
    fun parts(graph:BuildingGraph,id:String):List<Part> {
        val building=graph.nodes.first { it.id==id && it.kind==BuildingKind.BUILDING }
        return listOf(Part(BODY,building,position=building.buildingOffset))+building.inputs.mapIndexedNotNull { index,source ->
            graph.nodes.firstOrNull { it.id==source && it.kind in LandscapeGeometry.kinds }?.let {
                Part("in:$index",it,index,(building.offsets.getOrNull(index) ?: P())+it.landscapeOffset,building.sitePaths[index] ?: it.coordinates)
            }
        }+building.accessInstances.mapIndexedNotNull { index,instance ->
            graph.nodes.firstOrNull { it.id==instance.source }?.let { Part("access:$index",it,index,instance.position+building.buildingOffset) }
        }
    }
    fun part(graph:BuildingGraph,id:String,key:String)=parts(graph,id).first { it.key==key }
    fun footprint(graph:BuildingGraph,part:Part,segment:((String)->FixedSegment)?=null):Set<P> = if(part.isBody)
        LandscapeGeometry.buildingFootprint(graph,segment).map { it+part.position }.toSet()
    else if(part.isAccess) {
        val building=graph.nodes.single { it.kind==BuildingKind.BUILDING };val instance=building.accessInstances[part.index]
        val kind=when(part.source.kind) { BuildingKind.STAIRWELL->VerticalAccessKind.STAIRWELL;BuildingKind.ELEVATOR_SHAFT->VerticalAccessKind.ELEVATOR_SHAFT;else->VerticalAccessKind.SERVICE_SHAFT }
        val definition=AccessDefinition(part.source.id,kind,part.source.stairwell ?: StairwellConfig(),part.source.elevatorShaft ?: ElevatorShaftConfig(),part.source.serviceShaft ?: ServiceShaftConfig())
        VerticalAccess.previewFootprint(definition).map { it.turn(instance.quarterTurns)+part.position }.toSet()
    } else LandscapeGeometry.localFootprint(graph,part.source.copy(coordinates=part.points)).map { it+part.position }.toSet()
    fun points(part:Part):List<P> = if(part.isPath) part.points.map { it+part.position+P(0,part.source.elevation,0) } else emptyList()

    fun move(graph:BuildingGraph,id:String,key:String,position:P):BuildingGraph {
        validatePosition(position)
        val part=part(graph,id,key);val building=graph.nodes.first { it.id==id }
        if(position==part.position) return graph
        val changed=if(part.isBody) building.copy(buildingOffset=position) else if(part.isAccess) {
            val local=position+P(-building.buildingOffset.x,0,-building.buildingOffset.z);validatePosition(local)
            building.copy(accessInstances=building.accessInstances.mapIndexed { index,instance -> if(index==part.index) instance.copy(position=local) else instance })
        } else {
            val offset=position+P(-part.source.landscapeOffset.x,0,-part.source.landscapeOffset.z)
            validatePosition(offset)
            building.copy(offsets=List(building.inputs.size) { if(it==part.index) offset else building.offsets.getOrNull(it) ?: P() })
        }
        return graph.replace(changed).also(BuildingGraphCompiler::structure)
    }
    fun rotateAccess(graph:BuildingGraph,id:String,key:String,quarterTurns:Int):BuildingGraph {
        require(quarterTurns in 0..3)
        val part=part(graph,id,key);require(part.isAccess)
        val building=graph.nodes.first { it.id==id }
        return graph.replace(building.copy(accessInstances=building.accessInstances.mapIndexed { index,instance -> if(index==part.index) instance.copy(quarterTurns=quarterTurns) else instance })).also(BuildingGraphCompiler::structure)
    }
    fun pathPoints(graph:BuildingGraph,id:String,key:String,worldPoints:List<P>):BuildingGraph {
        val part=part(graph,id,key);require(part.isPath) { "Select a path instance" }
        val origin=part.position+P(0,part.source.elevation,0)
        val local=worldPoints.map { it+P(-origin.x,-origin.y,-origin.z) }
        LandscapeGeometry.pathCells(part.source.copy(coordinates=local))
        if(local==part.points) return graph
        val building=graph.nodes.first { it.id==id }
        return graph.replace(building.copy(sitePaths=if(local==part.source.coordinates) building.sitePaths-part.index else building.sitePaths+(part.index to local))).also(BuildingGraphCompiler::structure)
    }
}
