package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import com.google.gson.JsonParser
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import kotlin.test.*

/** Read-only check against an exported world snapshot; no player save is modified. */
@EnabledIfEnvironmentVariable(named="ARCHITECT_ROOM_SNAPSHOT",matches=".+")
class VorlagenRoomSnapshotTest {
    @Test fun `saved Lagerhalle assembles unchanged wall segments on shared end rows`() {
        val db=JsonParser.parseString(File(System.getenv("ARCHITECT_ROOM_SNAPSHOT")).readText()).asJsonObject
        val gson=Gson();val library=db.getAsJsonObject("libraries").entrySet().first().value.asJsonObject
        val forms=library.getAsJsonObject("forms").entrySet().associate { it.key to gson.fromJson(it.value,Form::class.java) }
        val modules=library.getAsJsonObject("modules").entrySet().associate { it.key to gson.fromJson(it.value,Module::class.java) }
        val table=db.getAsJsonObject("tableBuildings").entrySet().map { it.value.asJsonObject }.single { it.getAsJsonObject("building")["name"].asString=="Lagerhalle" }
        val building=gson.fromJson(table["building"],Building::class.java)
        val graph=assertNotNull(building.graph)
        val evaluation=BuildingGraphCompiler.Evaluation(graph,building.palette,forms,modules)
        val roomNode=graph.resolvedNode("ffc27a43-fbef-4588-bb16-4b82c65d9453")
        val room=evaluation.room(roomNode.id)
        assertEquals(23,room.outerWidth);assertEquals(37,room.outerDepth)
        val bounds=RoomGeometry.cells(roomNode.rectangles)
        assertEquals(bounds,room.shell)
        assertTrue(room.plan.placements.all { it.pos.copy(y=0) in bounds })
        assertTrue(room.plan.placements.none { it.pos in room.plan.clear })
        val byPosition=room.plan.placements.associateBy { it.pos }
        for(edge in RoomVerticalEdges.of(roomNode.rectangles)) {
            val pos=edge.pos-edge.normalX-edge.normalZ
            assertEquals(2,RoomWallJoints.runs(roomNode).count { pos in it.cells })
            assertTrue((0 until roomNode.height).all { pos+P(0,it,0) in byPosition })
        }
        println("VORLAGEN JOINED ROOM VERIFIED: outer ${room.outerWidth} × ${room.outerDepth}, ${room.plan.placements.size} blocks, four shared terminal columns")
    }
}
