package de.teutonstudio.copycatarchitect.domain

/** One typed card can contain every building/Copycat/value recipe. Legacy graphs retain their terminal. */
object GraphCards {
    val types=BuildingKind.entries.map(::buildingGraphType).filter { it!="VALUE" }.toSet()
    fun validate(n:BuildingNode) {
        require(n.resultType==null || (n.kind==BuildingKind.RESULT || n.resultMode==GraphResultMode.CONSTRUCT || n.kind==BuildingKind.BUILDING && n.inputs.size<=1) && n.resultType in types) { "Graph results need a supported output type and one input" }
        require(n.inlinePart==null || GraphPrograms.producer(n).kind==BuildingKind.COPYCAT) { "Inline Copycat parts belong to Copycat sources" }
        n.inlinePart?.let { Geometry.validate(Form(parts=listOf(it.copy(group="body")))) }
    }
    fun starter(type:String="COPYCAT"):BuildingGraph = BuildingGraph(schema=14,groupInputOrder=emptyList(),nodes=listOf(
        BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",block="minecraft:air",length=1),
        GraphPrograms.newResult(type).copy(id="result",x=340,y=100)))
    fun legacyStarter(type:String="COPYCAT"):BuildingGraph {
        require(type in types) { "Unknown graph output type" }
        return BuildingGraph(schema=8,nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",block="minecraft:air",length=1),
            BuildingNode("source",BuildingKind.COPYCAT,"Copycat",80,100,inlinePart=Part("copycats:copycat_block")),
            BuildingNode("result",BuildingKind.BUILDING,"Ergebnis",440,100,resultType=type,inputs=listOf(if(type=="COPYCAT") "source" else ""))))
    }
}
