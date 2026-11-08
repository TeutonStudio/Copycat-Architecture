package de.teutonstudio.copycatarchitect.server

import net.minecraft.nbt.CompoundTag

/** StringTag uses Java's 65,535-byte modified UTF limit; larger JSON retains its full UTF-8 bytes. */
object JsonNbt {
    fun put(tag:CompoundTag,key:String,json:String) {
        val modifiedUtfBytes=json.sumOf { c -> when(c.code) { in 1..127->1;in 0..2047->2;else->3 } }
        if(modifiedUtfBytes<=65535) tag.putString(key,json) else tag.putByteArray(key,json.toByteArray(Charsets.UTF_8))
    }
    fun get(tag:CompoundTag,key:String):String =
        if(tag.contains(key,7)) String(tag.getByteArray(key),Charsets.UTF_8) else tag.getString(key)
}
