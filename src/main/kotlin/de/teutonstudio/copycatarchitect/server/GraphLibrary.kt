package de.teutonstudio.copycatarchitect.server

import de.teutonstudio.copycatarchitect.domain.*

/** All mutations are preflighted against an isolated candidate, then committed as one library update. */
object GraphLibrary {
    fun saveDraft(library:Library,draft:GraphDocument) {
        require(draft.version==0) { "Published versions are immutable; edit a draft" }
        require(library.graphs.values.none { it.graphId==draft.graphId && it.mode!=draft.mode }) { "A graph identity cannot change modes" }
        val frozen=Json.decode<GraphDocument>(Json.encode(draft))
        VersionedGraphs.validate(frozen,allowBrokenConnections=true)
        val candidate=library.copy(graphDrafts=(library.graphDrafts+(frozen.graphId to frozen)).toMutableMap())
        candidate.validateStorageSize()
        library.graphDrafts[frozen.graphId]=frozen
    }
    fun publish(library:Library,draft:GraphDocument):GraphDocument {
        val published=Json.decode<GraphDocument>(Json.encode(VersionedGraphs.nextVersion(draft,library.graphs)))
        VersionedGraphs.validate(published)
        VersionedGraphs.dependencies(published,library.graphs)
        GraphPackages.export(published,library.graphs,library.forms,library.modules)
        val candidate=library.copy(graphs=(library.graphs+(published.key to published)).toMutableMap())
        candidate.validateStorageSize()
        library.graphs[published.key]=published
        // Do not expose the mutable collection instances stored in persistence.
        return Json.decode(Json.encode(published))
    }
    fun importPackage(library:Library,bundle:GraphPackage,replaceDraft:Boolean=false):GraphDocument {
        val frozen=Json.decode<GraphPackage>(Json.encode(bundle))
        GraphPackages.checkConflicts(frozen,library.graphs,library.forms,library.modules)
        val graphs=library.graphs+frozen.graphs+if(frozen.root.version>0) mapOf(frozen.root.key to frozen.root) else emptyMap()
        if(frozen.root.version==0 && !replaceDraft) require(frozen.root.graphId !in library.graphDrafts || library.graphDrafts[frozen.root.graphId]==frozen.root) { "Draft conflict: ${frozen.root.graphId}" }
        val drafts=library.graphDrafts+if(frozen.root.version==0) mapOf(frozen.root.graphId to frozen.root) else emptyMap()
        val candidate=library.copy(graphDrafts=drafts.toMutableMap(),graphs=graphs.toMutableMap(),forms=(library.forms+frozen.forms).toMutableMap(),modules=(library.modules+frozen.modules).toMutableMap())
        frozen.forms.values.forEach { form -> form.versionedGraph?.let { importPackage(candidate,it,replaceDraft=true) } }
        candidate.validateStorageSize()
        library.graphDrafts.clear();library.graphDrafts.putAll(candidate.graphDrafts)
        library.graphs.clear();library.graphs.putAll(candidate.graphs)
        library.forms.clear();library.forms.putAll(candidate.forms)
        library.modules.clear();library.modules.putAll(candidate.modules)
        return Json.decode(Json.encode(frozen.root))
    }
}
