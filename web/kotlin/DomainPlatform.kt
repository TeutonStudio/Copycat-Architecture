package de.teutonstudio.copycatarchitect.domain

/** Browser equivalents of the integer operations used by the shared domain. */
internal object Math {
    fun floorMod(a:Int,b:Int)=a.mod(b)
    fun floorDiv(a:Int,b:Int)=a.floorDiv(b)
    fun multiplyExact(a:Int,b:Int):Int {
        val value=a.toLong()*b
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "integer overflow" }
        return value.toInt()
    }
    fun toDegrees(value:Double)=value*180.0/kotlin.math.PI
}
internal fun <K:Comparable<K>,V> Map<K,V>.toSortedMap():Map<K,V> = entries.sortedBy { it.key }.associate { it.toPair() }
internal fun <K,V> Map<K,V>.toSortedMap(comparator:Comparator<K>):Map<K,V> = entries.sortedWith { a,b -> comparator.compare(a.key,b.key) }.associate { it.toPair() }
internal fun String.lowercase(locale:java.util.Locale)=lowercase()
internal object Charsets { val UTF_8=Unit }
internal fun String.toByteArray(charset:Unit)=encodeToByteArray()
internal fun <K,V> MutableMap<K,V>.putIfAbsent(key:K,value:V):V? {
    val previous=get(key);if(previous==null) put(key,value);return previous
}
internal inline fun <T> synchronized(lock:Any,block:()->T):T=block()
