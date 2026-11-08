@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package de.teutonstudio.copycatarchitect.domain

import kotlin.io.encoding.Base64

internal fun encodeMask(bytes: ByteArray): String = Base64.encode(bytes)
internal fun decodeMask(mask: String): ByteArray = Base64.decode(mask.padEnd((mask.length + 3) / 4 * 4, '='))

internal class GraphCache<K, V> : MutableMap<K, V> by mutableMapOf<K, V>() {
    fun computeIfAbsent(key: K, compute: (K) -> V): V = getOrPut(key) { compute(key) }
}

/** Browser implementation of the JVM bit set operations used by the solid raster. */
internal class GraphBits(size: Int) {
    private val words = IntArray((size + 31) / 32)
    val isEmpty get() = words.all { it == 0 }

    operator fun get(index: Int): Boolean = words[index / 32] and (1 shl (index % 32)) != 0
    fun set(index: Int) { words[index / 32] = words[index / 32] or (1 shl (index % 32)) }
    fun clear(index: Int) { words[index / 32] = words[index / 32] and (1 shl (index % 32)).inv() }
    fun cardinality(): Int = words.sumOf { it.countOneBits() }
    fun clone(): GraphBits = GraphBits(words.size * 32).also { words.copyInto(it.words) }
    fun or(other: GraphBits) { words.indices.forEach { words[it] = words[it] or other.words[it] } }
    fun and(other: GraphBits) { words.indices.forEach { words[it] = words[it] and other.words[it] } }
    fun andNot(other: GraphBits) { words.indices.forEach { words[it] = words[it] and other.words[it].inv() } }
    fun nextSetBit(start: Int): Int {
        var index = start / 32
        if (index >= words.size) return -1
        var word = words[index] and (-1 shl (start % 32))
        while (word == 0) {
            index++
            if (index == words.size) return -1
            word = words[index]
        }
        return index * 32 + word.countTrailingZeroBits()
    }
    fun toByteArray(): ByteArray {
        val last = words.indexOfLast { it != 0 }
        if (last < 0) return ByteArray(0)
        val size = last * 4 + (32 - words[last].countLeadingZeroBits() + 7) / 8
        return ByteArray(size) { (words[it / 4] ushr (it % 4 * 8)).toByte() }
    }
    fun toLongArray(): LongArray {
        val last = words.indexOfLast { it != 0 }
        return LongArray((last + 2) / 2) {
            (words[it * 2].toLong() and 0xffffffffL) or ((words.getOrNull(it * 2 + 1) ?: 0).toLong() shl 32)
        }
    }
    companion object {
        fun valueOf(bytes: ByteArray): GraphBits = GraphBits(4096).also { bits ->
            bytes.forEachIndexed { i, value ->
                bits.words[i / 4] = bits.words[i / 4] or ((value.toInt() and 255) shl (i % 4 * 8))
            }
        }
    }
}
