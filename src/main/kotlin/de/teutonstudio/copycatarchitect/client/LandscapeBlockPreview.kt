package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Economy
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.WallBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.WallSide

/** Preview perimeter connections from the draft, before any of its blocks exist in the world. */
object LandscapeBlockPreview {
    fun state(cell:Placement,cells:Map<P,Placement>):BlockState {
        var state=Economy.nativeState(cell.block)
        if(cell.block !in LandscapeGeometry.fenceBlocks) return state
        val connected=RoomGeometry.directions.map { direction ->
            val neighbor=cells[cell.pos+direction]
            neighbor!=null && (neighbor.block==cell.block || neighbor.block in LandscapeGeometry.surfaceBlocks || neighbor.block in LandscapeGeometry.groundBlocks)
        }
        if(state.block is FenceBlock) {
            val sides=listOf(FenceBlock.NORTH,FenceBlock.EAST,FenceBlock.SOUTH,FenceBlock.WEST)
            sides.forEachIndexed { i,side -> state=state.setValue(side,connected[i]) }
        } else if(state.block is WallBlock) {
            val sides=listOf(WallBlock.NORTH_WALL,WallBlock.EAST_WALL,WallBlock.SOUTH_WALL,WallBlock.WEST_WALL)
            sides.forEachIndexed { i,side -> state=state.setValue(side,if(connected[i]) WallSide.LOW else WallSide.NONE) }
        }
        return state
    }
}
