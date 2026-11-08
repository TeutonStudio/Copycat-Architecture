package de.teutonstudio.copycatarchitect.domain

/** Names are paths only in the picker. The stored form and its id@version key stay intact. */
class FormLibraryTree(forms:Map<String,Form>) {
    data class Version(val key:String,val form:Form)
    data class Entry(val path:List<String>,val label:String,val key:String="",val form:Form?=null,val count:Int=0,
                     val versions:List<Version> = emptyList()) {
        val folder get()=form==null
        val fullName get()=path.joinToString(" / ")
        fun contains(key:String)=this.key==key || versions.any { it.key==key }
    }
    private val leaves=forms.map { (key,form) ->
        val path=form.name.split('/').map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf(form.id.ifEmpty { key }) }
        path to Version(key,form)
    }.groupBy({ it.first },{ it.second }).map { (path,items) ->
        val versions=items.sortedWith(compareByDescending<Version> { it.form.version }.thenBy { it.key })
        val latest=versions.first()
        Entry(path,path.last(),latest.key,latest.form,versions=versions)
    }
    private val order=compareBy<Entry>({ !it.folder },{ it.label.lowercase(java.util.Locale.ROOT) },{ it.key })

    fun groupOf(key:String):Entry? = leaves.firstOrNull { it.contains(key) }
    fun folderOf(key:String):List<String> = groupOf(key)?.path?.dropLast(1).orEmpty()

    /** Search keeps names grouped, while exact key matches still select their original version. */
    fun entries(path:List<String> = emptyList(),query:String=""):List<Entry> {
        val needle=query.trim()
        if(needle.isNotEmpty()) return leaves.mapNotNull { entry ->
            val matches=entry.versions.filter { it.key.contains(needle,true) }
            val exact=matches.firstOrNull { it.key.equals(needle,true) }
            when {
                exact!=null->entry.copy(key=exact.key,form=exact.form)
                entry.fullName.contains(needle,true) || entry.path.joinToString("/").contains(needle,true)->entry
                matches.isNotEmpty()->entry.copy(key=matches.first().key,form=matches.first().form)
                else->null
            }
        }.sortedWith(order)
        val descendants=leaves.filter { it.path.size>path.size && it.path.take(path.size)==path }
        val folders=descendants.filter { it.path.size>path.size+1 }.groupBy { it.path[path.size] }.map { (name,children) ->
            Entry(path+name,name,count=children.size)
        }
        return (folders+descendants.filter { it.path.size==path.size+1 }).sortedWith(order)
    }
}
