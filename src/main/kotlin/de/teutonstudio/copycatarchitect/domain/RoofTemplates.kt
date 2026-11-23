package de.teutonstudio.copycatarchitect.domain

enum class RoofInputMode { TEMPLATE, SEGMENTS }
enum class RoofContourMode { BOUNDS, CONTOUR }
enum class RoofVariant { SLOPE, INNER, OUTER, RIDGE, RIDGE_CURVE, RIDGE_T, RIDGE_X, PEAK, EAVE, VERGE, TRANSITION, FLAT, WALL }
data class RoofTemplateSettings(val form:RoofForm=RoofForm.GABLE,val axis:Int=0,val mode:RoofMode=RoofMode.ANGLE,
    val slope:RoofSlope=RoofSlope(),val upperSlope:RoofSlope=RoofSlope(1,2),val height16:Int=64,val knee16:Int=0,
    val overhang16:Int=16,val breakRatio:RoofSlope=RoofSlope(1,3),val contourMode:RoofContourMode=RoofContourMode.BOUNDS) {
    fun level(area:String="")=RoofLevelConfig(area=area,form=form,axis=axis,mode=mode,slope=slope,upperSlope=upperSlope,
        height16=height16,knee16=knee16,overhang16=overhang16,breakRatio=breakRatio,contourMode=contourMode)
}
object RoofTemplates {
    val labels=listOf("Dachschräge gerade","Innenecke","Außenecke","First gerade","Firstkurve 90°","First T","First X","Einzelne Spitze","Traufe","Ortgang","Dachknick / Übergang","Flachdach","Giebelwand")
    val variantCount get()=labels.size
    /** Keep the original eight socket identities; new edge variants inherit the slope when open. */
    fun input(n:BuildingNode,variant:String):String {
        val connected=n.inputs.getOrNull(index(variant)).orEmpty()
        return connected.ifEmpty { if(variant in setOf("eave","edge","transition")) n.inputs.firstOrNull().orEmpty() else "" }
    }
    fun expand(graph:BuildingGraph,n:BuildingNode):BuildingNode {
        if(n.roofInputMode==null) return n
        val source=if(n.roofInputMode==RoofInputMode.TEMPLATE) {
            val id=n.roofTemplate?.takeIf(String::isNotEmpty) ?: error("Dachvorlage verbinden")
            GraphConditions.buildingNode(graph,id).also { require(it.kind==BuildingKind.ROOF_TEMPLATE) { "Dachvorlage erforderlich" } }
        } else n
        val settings=(source.roofSettings ?: RoofTemplateSettings()).level(n.geometry).let { if(n.roofEdges==null) it.copy(contourMode=n.roofContourMode ?: it.contourMode) else it.copy(mode=RoofMode.ANGLE,knee16=0,contourMode=RoofContourMode.CONTOUR) }
        require(n.geometry.isNotEmpty()) { "Dachfläche verbinden" }
        return n.copy(inputs=List(variantCount) { source.inputs.getOrNull(it).orEmpty() },assembly=true,adaptiveRoof=false,layout="",roofSettings=source.roofSettings,
            roofConfig=(n.roofConfig ?: RoofConfig()).copy(wall=input(source,"wall").ifEmpty { n.roofConfig?.wall.orEmpty() }),
            roofSections=listOf(RoofSection(config=settings,dormers=n.dormerInstances)))
    }
    fun index(variant:String)=when(variant) {
        "slope"->0;"valley"->1;"hip"->2;"ridge"->3;"ridge_curve"->4;"ridge_t"->5;"ridge_x"->6;"peak"->7;
        "eave"->8;"edge"->9;"transition"->10;"flat"->11;"wall"->12;else->error("Unbekannte Dachvariante $variant")
    }
    fun junction(directions:Set<Int>,point:Boolean=false):String=when {
        directions.size==4->"ridge_x"
        directions.size==3->"ridge_t"
        directions.size==2 && directions.first().let { (it+2)%4 !in directions }->"ridge_curve"
        directions.isNotEmpty()->"ridge"
        point->"peak"
        else->"hip"
    }
}
