package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class TableBuildingPersistenceTest {
    @Test fun `older worlds and jobs load with no inferred ownership`() {
        val db=Json.decode<Database>("""{"schema":1,"libraries":{},"jobs":[{"id":"legacy"}]}""")
        assertTrue(db.tableBuildings.isEmpty());assertEquals("",db.jobs.single().buildingId)
    }
    @Test fun `world ledger preserves exact tokens owner dimension and draft through save and reload`() {
        val record=TableBuilding(owner="owner",dimension="minecraft:overworld",table=P(12,64,-9),origin=P(17,65,-3),turn=3,attached=false,
            building=Building(schema=2,graph=BuildingGraph.explicitStarter(joinWallEnds=false)),blocks=mutableListOf(OwnedCopycat(P(17,65,-3),"block-instance")))
        val db=Database(tableBuildings=linkedMapOf(record.id to record),jobs=mutableListOf(Job(buildingId=record.id)))
        val loaded=Json.decode<Database>(Json.encode(db))
        assertEquals(db,loaded);assertEquals(record.id,loaded.jobs.single().buildingId)
    }
    @Test fun `relocated table keeps the same world origin and rotation in every orientation`() {
        for(turn in 0..3) {
            val record=TableBuilding(table=P(12,64,-9),origin=P(17,65,-3),turn=turn,building=Building(schema=2,graph=BuildingGraph.explicitStarter(joinWallEnds=false)))
            val document=record.building
            for(table in listOf(P(12,64,-9),P(14,66,-5),P(-8,60,11),P(-400,60,11))) {
                record.table=table
                val projection=record.relativeBuilding().graph!!.projection
                assertEquals(record.origin,projection.origin(table));assertEquals(turn,projection.turn)
                assertEquals(document,record.building)
            }
        }
    }
}
