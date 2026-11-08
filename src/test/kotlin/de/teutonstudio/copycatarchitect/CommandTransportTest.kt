package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Command
import de.teutonstudio.copycatarchitect.server.CommandTransport
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Random
import java.util.zip.GZIPOutputStream
import kotlin.test.*

class CommandTransportTest {
    @Test fun `a full table draft and library can share a bounded response larger than one command`() {
        val snapshot="{\"library\":\""+"Gebäude Ä ".repeat(17000)+"\",\"tableBuilding\":\""+"Garten Weg ".repeat(12000)+"\"}"
        assertTrue(snapshot.length>CommandTransport.MAX_JSON_CHARS)
        val wire=CommandTransport.encode(snapshot,500000,250000)
        assertTrue(wire.length<=250000)
        assertEquals(snapshot,CommandTransport.decode(wire,500000,250000))
        assertFailsWith<IllegalArgumentException> { CommandTransport.decode(wire) }
        assertFailsWith<IllegalArgumentException> { CommandTransport.encode("x".repeat(500001),500000,250000) }
    }
    private fun gzip(bytes:ByteArray):String {
        val compressed=ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(bytes) };output.toByteArray()
        }
        return "gzip:"+Base64.getEncoder().encodeToString(compressed)
    }
    private fun gzip(text:String)=gzip(text.toByteArray(StandardCharsets.UTF_8))

    @Test fun `small commands preserve the original UTF wire format exactly`() {
        for(json in listOf("",Json.encode(Command()),Json.encode(Command("saveForm",payload="{\"name\":\"Ä \\\"Quelle\\\" 🏠\"}")),
            "x".repeat(CommandTransport.MAX_WIRE_CHARS))) {
            assertEquals(json,CommandTransport.encode(json))
            assertEquals(json,CommandTransport.decode(json))
        }
    }

    @Test fun `a real command carries 4096 room cells and escaped source JSON within the wire cap`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val geometry=initial.nodes.single { it.kind==BuildingKind.ROOM_GEOMETRY }
        val cells=buildList { for(z in 0..63) for(x in 0..63) add(P(x,0,z)) }
        val source=initial.nodes.first { it.kind==BuildingKind.COPYCAT }
        val graph=initial.replace(geometry.copy(coordinates=cells))
            .replace(source.copy(name="Quelle \"Fenster\" \\ Holz\nÄÖÜ 🏠"))
        val building=Building(schema=2,id="transport_geometry",name="4096 occupied cells",graph=graph)
        val command=Command("saveBuilding",payload=Json.encode(building),key="quoted \"source\" \\ path\n")
        val json=Json.encode(command)
        assertTrue(json.length>CommandTransport.MAX_WIRE_CHARS)
        assertTrue(json.length<=CommandTransport.MAX_JSON_CHARS)
        val wire=CommandTransport.encode(json)
        assertTrue(wire.startsWith("gzip:"))
        assertTrue(wire.length<=CommandTransport.MAX_WIRE_CHARS)
        assertEquals(json,CommandTransport.decode(wire))
        val decoded=Json.decode<Command>(CommandTransport.decode(wire))
        assertEquals(command,decoded)
        assertEquals(building,Json.decode<Building>(decoded.payload))
        assertEquals(cells,Json.decode<Building>(decoded.payload).graph!!.nodes.single { it.id==geometry.id }.coordinates)
    }

    @Test fun `the maximum expanded command accepts exact UTF8 and UTF16 character boundaries`() {
        for(text in listOf("x".repeat(CommandTransport.MAX_JSON_CHARS),"界".repeat(CommandTransport.MAX_JSON_CHARS),
            "🏠".repeat(CommandTransport.MAX_JSON_CHARS/2))) {
            assertEquals(CommandTransport.MAX_JSON_CHARS,text.length)
            assertEquals(text,CommandTransport.decode(CommandTransport.encode(text)))
        }
    }

    @Test fun `encoding rejects oversized JSON and compressed data that cannot fit the wire field`() {
        assertFailsWith<IllegalArgumentException> { CommandTransport.encode("x".repeat(CommandTransport.MAX_JSON_CHARS+1)) }
        val random=Random(174)
        val incompressible=buildString { repeat(CommandTransport.MAX_JSON_CHARS) { append(('!'.code+random.nextInt(90)).toChar()) } }
        assertFailsWith<IllegalArgumentException> { CommandTransport.encode(incompressible) }
    }

    @Test fun `decoding rejects oversized wire fields before handling plain or compressed content`() {
        assertFailsWith<IllegalArgumentException> { CommandTransport.decode("x".repeat(CommandTransport.MAX_WIRE_CHARS+1)) }
        assertFailsWith<IllegalArgumentException> { CommandTransport.decode("gzip:"+"A".repeat(CommandTransport.MAX_WIRE_CHARS)) }
    }

    @Test fun `decoding rejects malformed base64 non-gzip payloads and truncated gzip trailers`() {
        for(wire in listOf("gzip:","gzip:%%%", "gzip:"+Base64.getEncoder().encodeToString("plain bytes".toByteArray()))) {
            assertFails("Malformed transport must be rejected: $wire") { CommandTransport.decode(wire) }
        }
        val complete=Base64.getDecoder().decode(gzip("x".repeat(40000)).removePrefix("gzip:"))
        for(length in listOf(1,9,complete.size-1,complete.size-8)) {
            val truncated="gzip:"+Base64.getEncoder().encodeToString(complete.copyOf(length))
            assertFails("Truncated gzip must fail before exposing decoded JSON") { CommandTransport.decode(truncated) }
        }
    }

    @Test fun `decoding rejects malformed UTF8 instead of replacing command characters`() {
        for(bytes in listOf(byteArrayOf(0xc3.toByte(),0x28),byteArrayOf(0x80.toByte()),
            byteArrayOf(0xed.toByte(),0xa0.toByte(),0x80.toByte()))) {
            assertFailsWith<CharacterCodingException> { CommandTransport.decode(gzip(bytes)) }
        }
    }

    @Test fun `decoding caps both expanded characters and gzip bomb byte output`() {
        val tooManyCharacters=gzip("x".repeat(CommandTransport.MAX_JSON_CHARS+1))
        assertTrue(tooManyCharacters.length<CommandTransport.MAX_WIRE_CHARS)
        assertFailsWith<IllegalArgumentException> { CommandTransport.decode(tooManyCharacters) }
        val bomb=gzip("x".repeat(CommandTransport.MAX_JSON_CHARS*6))
        assertTrue(bomb.length<CommandTransport.MAX_WIRE_CHARS)
        assertFailsWith<IllegalArgumentException> { CommandTransport.decode(bomb) }
    }
}
