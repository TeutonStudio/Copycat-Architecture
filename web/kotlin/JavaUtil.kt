package java.util

internal typealias BitSet = de.teutonstudio.copycatarchitect.domain.GraphBits

object Base64 {
    fun getEncoder()=this
    fun getDecoder()=this
    fun encodeToString(bytes:ByteArray)=de.teutonstudio.copycatarchitect.domain.encodeMask(bytes)
    fun decode(value:String)=de.teutonstudio.copycatarchitect.domain.decodeMask(value)
}

object Locale { val ROOT=this }

class UUID private constructor(private val value:String) {
    override fun toString()=value
    companion object {
        fun randomUUID():UUID=UUID(random())
        // Saved sources are outside the browser contract; never invent receipt identities.
        fun nameUUIDFromBytes(bytes:ByteArray):UUID=error("Saved Copycat sources require the native editor")
        private fun random():String=js("globalThis.crypto.randomUUID()")
    }
}
