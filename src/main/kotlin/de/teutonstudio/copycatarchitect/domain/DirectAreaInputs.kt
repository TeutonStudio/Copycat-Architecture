package de.teutonstudio.copycatarchitect.domain

/** Resolve AREA at the consumer while retaining the legacy arrangement node contract. */
fun BuildingGraph.segmentLayout(node:BuildingNode):BuildingNode {
    val source=node.layout.takeIf(String::isNotEmpty)?.let { GraphConditions.buildingNode(this,it) } ?: error("Connect a segment area")
    if(source.kind==BuildingKind.SEGMENT_LAYOUT) return resolvedNode(source)
    require(source.kind==BuildingKind.AREA) { "Connect an area or legacy segment arrangement" }
    val area=areaOf(source,true) ?: error("Segment area is empty")
    SegmentVolume(area,node.depth)
    return node.copy(length=area.width,height=area.length,geometry=source.id)
}

fun BuildingGraph.roomAreaPoints(node:BuildingNode):List<P> {
    val source=node.geometry.takeIf(String::isNotEmpty)?.let { GraphConditions.buildingNode(this,it) } ?: return emptyList()
    return when(source.kind) {
        BuildingKind.AREA -> areaOf(source,true)?.points.orEmpty()
        BuildingKind.ROOM_GEOMETRY -> areaOf(source,true)?.points ?: resolvedNode(source).coordinates
        else -> error("Connect an area or legacy room geometry")
    }
}

data class RoofSection(val id:String="section_1",val name:String="Dachfläche 1",val config:RoofLevelConfig=RoofLevelConfig(),val dormers:List<DormerInstance> = emptyList())

object RoofSections {
    fun validate(node:BuildingNode,nodes:Map<String,BuildingNode>) {
        val sections=node.roofSections ?: return
        require(node.kind==BuildingKind.ROOF && node.assembly && node.layout.isEmpty()) { "Embedded roof areas belong to an explicit roof without a legacy arrangement" }
        require(sections.size<=16 && sections.map { it.id }.distinct().size==sections.size && sections.all { it.id.matches(Regex("[a-zA-Z0-9_-]{1,36}")) && it.name.length<=64 }) { "Use at most sixteen uniquely named roof areas" }
        sections.forEach { section ->
            Roofs.validate(section.config)
            require(section.config.area.isEmpty() || nodes[section.config.area]?.let { it.kind==BuildingKind.AREA || it.kind==BuildingKind.CONDITIONAL && GraphTypes.building(nodes,it.id)=="AREA" }==true) { "Roof areas require AREA inputs" }
            SpecialRooms.validate(BuildingNode(section.id,BuildingKind.ROOF_LEVEL,roofLevel=section.config,dormers=section.dormers),nodes)
        }
    }
    fun levels(graph:BuildingGraph,roof:BuildingNode):List<BuildingNode> {
        roof.roofSections?.let { sections -> return sections.map { section ->
            BuildingNode("${roof.id}:${section.id}",BuildingKind.ROOF_LEVEL,name=section.name,roofLevel=section.config,dormers=section.dormers)
        } }
        require(roof.layout.isNotEmpty()) { "Connect a roof arrangement source" }
        val layout=graph.nodes.firstOrNull { it.id==roof.layout }?.let { GraphConditions.buildingNode(graph,it.id) }
            ?: error("Connect a roof arrangement source")
        require(layout.kind==BuildingKind.ROOF_LAYOUT) { "Connect a roof arrangement source" }
        require(layout.inputs.all(String::isNotEmpty)) { "Connect every legacy roof level" }
        return layout.inputs.map { id -> GraphConditions.buildingNode(graph,id).also { require(it.kind==BuildingKind.ROOF_LEVEL) } }
    }
}
