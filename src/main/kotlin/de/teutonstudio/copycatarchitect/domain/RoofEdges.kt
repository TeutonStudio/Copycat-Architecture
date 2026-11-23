package de.teutonstudio.copycatarchitect.domain

enum class RoofEdgeMode { ROOF, FACADE }
/** A straight, geometrically identified footprint edge. A dormant source survives a mode switch. */
data class RoofEdgeSetting(val id:String="",val mode:RoofEdgeMode=RoofEdgeMode.ROOF,val source:String="")

object RoofEdges {
    fun runs(mask:Set<P>):List<WallRun> = if(mask.isEmpty()) emptyList() else RoomGeometry.boundary(mask).also {
        require(it.size<=128) { "Die Dachfläche darf höchstens 128 gerade Randkanten besitzen" }
    }
    fun runs(graph:BuildingGraph,node:BuildingNode):List<WallRun> = runs(graph.areaOf(node,true)?.points.orEmpty().toSet())
    fun defaultMode(run:WallRun,settings:RoofTemplateSettings):RoofEdgeMode=when(settings.form) {
        RoofForm.GABLE,RoofForm.CROSS -> if(run.direction%2==settings.axis%2) RoofEdgeMode.FACADE else RoofEdgeMode.ROOF
        RoofForm.PENT -> if(run.direction==listOf(3,0,1,2)[settings.axis]) RoofEdgeMode.ROOF else RoofEdgeMode.FACADE
        RoofForm.FLAT -> RoofEdgeMode.FACADE
        else -> RoofEdgeMode.ROOF
    }
    fun validate(node:BuildingNode) {
        val edges=node.roofEdges ?: return
        require(node.kind==BuildingKind.ROOF && node.roofInputMode!=null) { "Kantenregeln benötigen ein Dach mit Vorlage oder Segmenten" }
        require(edges.size<=128 && edges.map { it.id }.distinct().size==edges.size && edges.all { it.id.matches(Regex("[0-3]:-?[0-9]{1,4}:-?[0-9]{1,4}")) }) { "Dachkanten benötigen eindeutige geometrische Kennungen" }
    }
}

/** Read-only editor projection, independent of roof mesh generation. */
data class RoofEdgeView(val id:String,val direction:Int,val start:P,val end:P,val length:Int,val mode:RoofEdgeMode,val height:Int?,val message:String="")
data class RoofEdgeModel(val points:Set<P>,val edges:List<RoofEdgeView>)
