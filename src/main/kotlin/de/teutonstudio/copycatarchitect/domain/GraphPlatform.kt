package de.teutonstudio.copycatarchitect.domain

import java.util.Base64
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap

internal typealias GraphBits = BitSet
internal typealias GraphCache<K, V> = ConcurrentHashMap<K, V>
internal fun encodeMask(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
internal fun decodeMask(mask: String): ByteArray = Base64.getDecoder().decode(mask)
