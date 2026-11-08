package de.teutonstudio.copycatarchitect.client

/** Section preferences belong to the editor view, shared across node selections. */
class InspectorSections {
    private val expanded=mutableMapOf<String,Boolean>()
    fun isExpanded(key:String)=expanded[key] ?: (key!="2d")
    fun setExpanded(key:String,value:Boolean) { expanded[key]=value }
    fun toggle(key:String) { setExpanded(key,!isExpanded(key)) }
    fun layout(contents:List<Pair<String,Int>>):List<InspectorSection> {
        var top=0
        return contents.map { (key,height) ->
            val section=InspectorSection(key,top,if(isExpanded(key)) height.coerceAtLeast(0) else 0,isExpanded(key))
            top+=section.height+4;section
        }
    }
}

data class InspectorSection(val key:String,val top:Int,val contentHeight:Int,val expanded:Boolean) {
    val contentTop get()=top+22
    val height get()=22+contentHeight
}
