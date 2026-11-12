package de.teutonstudio.copycatarchitect.server

import java.security.MessageDigest

/** Bounded, owner-scoped and idempotent uploads. State is temporary; library updates remain atomic. */
data class GraphTransferManifest(val id:String="",val characters:Int=0,val chunks:Int=0,val sha256:String="")
data class GraphTransferProgress(val id:String="",val missing:List<Int> = emptyList())
class GraphPackageTransfers {
    companion object {
        const val CHUNK_CHARACTERS=24000
        const val MAX_CHARACTERS=Library.MAX_JSON_CHARS
        const val TIMEOUT_MS=300000L
        fun split(value:String):List<String> {
            val chunks=mutableListOf<String>();var start=0
            while(start<value.length) {
                var end=minOf(start+CHUNK_CHARACTERS,value.length)
                if(end<value.length && value[end-1].isHighSurrogate() && value[end].isLowSurrogate()) end--
                chunks+=value.substring(start,end);start=end
            }
            return chunks
        }
        fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private data class Upload(val manifest:GraphTransferManifest,val parts:MutableMap<Int,String> = linkedMapOf(),var touched:Long)
    private val uploads=mutableMapOf<Pair<String,String>,Upload>()
    private fun expire(now:Long) { uploads.entries.removeAll { now-it.value.touched>TIMEOUT_MS } }
    fun begin(owner:String,manifest:GraphTransferManifest,now:Long) {
        expire(now)
        require(manifest.id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && manifest.characters in 1..MAX_CHARACTERS &&
            manifest.chunks in (manifest.characters+CHUNK_CHARACTERS-1)/CHUNK_CHARACTERS..(manifest.characters+CHUNK_CHARACTERS-2)/(CHUNK_CHARACTERS-1) && manifest.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid graph transfer manifest" }
        val key=owner to manifest.id
        uploads[key]?.let { require(it.manifest==manifest) { "Transfer ID already has another manifest" };it.touched=now;return }
        require(uploads.keys.count { it.first==owner }<2 && uploads.size<32) { "Too many graph transfers" }
        uploads[key]=Upload(manifest,touched=now)
    }
    fun append(owner:String,id:String,index:Int,payload:String,now:Long) {
        expire(now)
        val upload=uploads[owner to id] ?: error("Graph transfer expired or missing")
        val m=upload.manifest
        require(index in 0 until m.chunks && payload.length in 1..CHUNK_CHARACTERS) { "Invalid graph transfer chunk" }
        require(index !in upload.parts || upload.parts[index]==payload) { "Conflicting graph transfer chunk" }
        require(upload.parts.values.sumOf { it.length }-(upload.parts[index]?.length ?: 0)+payload.length<=m.characters) { "Graph transfer exceeds declared size" }
        upload.parts[index]=payload;upload.touched=now
    }
    fun missing(owner:String,id:String,now:Long):List<Int> {
        expire(now);val upload=uploads[owner to id] ?: error("Graph transfer expired or missing")
        return (0 until upload.manifest.chunks).filter { it !in upload.parts }
    }
    fun content(owner:String,id:String,now:Long):String {
        expire(now);val upload=uploads[owner to id] ?: error("Graph transfer expired or missing")
        require(upload.parts.size==upload.manifest.chunks) { "Graph transfer is incomplete" }
        val value=(0 until upload.manifest.chunks).joinToString("") { upload.parts.getValue(it) }
        require(value.length==upload.manifest.characters && digest(value)==upload.manifest.sha256) { "Graph transfer checksum mismatch" }
        upload.touched=now
        return value
    }
    /** Only call after successful import; failed imports retain their upload for repair/retry. */
    fun complete(owner:String,id:String) { uploads.remove(owner to id) }
}
