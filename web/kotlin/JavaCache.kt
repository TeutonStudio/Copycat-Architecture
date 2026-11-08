package java.util.concurrent

class ConcurrentHashMap<K,V> : MutableMap<K,V> by mutableMapOf<K,V>() {
    fun computeIfAbsent(key:K,compute:(K)->V):V = getOrPut(key) { compute(key) }
    fun putIfAbsent(key:K,value:V):V? { val previous=get(key);if(previous==null) put(key,value);return previous }
}
