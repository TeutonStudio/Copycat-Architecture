package de.teutonstudio.copycatarchitect.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Preserve the established UTF wire field while bounding both compressed and expanded commands. */
object CommandTransport {
    const val MAX_WIRE_CHARS=30000
    const val MAX_JSON_CHARS=250000
    private const val PREFIX="gzip:"

    fun encode(json:String,maxJsonChars:Int=MAX_JSON_CHARS,maxWireChars:Int=MAX_WIRE_CHARS):String {
        require(json.length<=maxJsonChars) { "Editor command exceeds the JSON size limit" }
        if(json.length<=maxWireChars) return json
        val compressed=ByteArrayOutputStream().use { bytes ->
            GZIPOutputStream(bytes).use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
            bytes.toByteArray()
        }
        return (PREFIX+Base64.getEncoder().encodeToString(compressed)).also {
            require(it.length<=maxWireChars) { "Editor command exceeds the transport size limit" }
        }
    }

    fun decode(wire:String,maxJsonChars:Int=MAX_JSON_CHARS,maxWireChars:Int=MAX_WIRE_CHARS):String {
        require(wire.length<=maxWireChars) { "Editor command exceeds the transport size limit" }
        if(!wire.startsWith(PREFIX)) return wire
        val compressed=Base64.getDecoder().decode(wire.substring(PREFIX.length))
        val maxUtf8Bytes=maxJsonChars*3
        val bytes=GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readNBytes(maxUtf8Bytes+1) }
        require(bytes.size<=maxUtf8Bytes) { "Editor command exceeds the expanded size limit" }
        val json=StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        require(json.length<=maxJsonChars) { "Editor command exceeds the JSON size limit" }
        return json
    }
}
