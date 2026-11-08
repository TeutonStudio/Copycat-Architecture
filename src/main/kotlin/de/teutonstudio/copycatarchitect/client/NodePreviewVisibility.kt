package de.teutonstudio.copycatarchitect.client

/** Explicit view overrides keep intermediate previews compact and result previews open by default. */
class NodePreviewVisibility {
    private val overrides=mutableMapOf<String,Boolean>()

    fun isExpanded(id:String,expandedByDefault:Boolean=false):Boolean=overrides[id] ?: expandedByDefault

    fun setExpanded(id:String,expanded:Boolean) { overrides[id]=expanded }

    fun toggle(id:String,expandedByDefault:Boolean=false):Boolean {
        val expanded=!isExpanded(id,expandedByDefault)
        setExpanded(id,expanded)
        return expanded
    }

    /** Call only for a complete document, so temporarily hidden nodes retain their preference. */
    fun retainAll(ids:Set<String>) { overrides.keys.retainAll(ids) }
}
