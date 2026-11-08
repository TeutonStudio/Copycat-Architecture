package java.util

internal typealias BitSet = de.teutonstudio.copycatarchitect.domain.GraphBits

object Base64 {
    fun getEncoder()=this
    fun getDecoder()=this
    fun encodeToString(bytes:ByteArray)=de.teutonstudio.copycatarchitect.domain.encodeMask(bytes)
    fun decode(value:String)=de.teutonstudio.copycatarchitect.domain.decodeMask(value)
}

object Locale { val ROOT=this }

class UUID private constructor(private val value:String) {
    override fun toString()=value
    companion object {
        fun randomUUID():UUID=UUID(random())
        // RFC 4122 version 3, identical to java.util.UUID.nameUUIDFromBytes.
        fun nameUUIDFromBytes(bytes:ByteArray):UUID {
            val count=((bytes.size+8)/64+1)*64
            val data=ByteArray(count);bytes.copyInto(data);data[bytes.size]=0x80.toByte()
            val bits=bytes.size.toLong()*8
            for(i in 0..7) data[count-8+i]=(bits ushr (8*i)).toByte()
            val shifts=intArrayOf(7,12,17,22,5,9,14,20,4,11,16,23,6,10,15,21)
            val constants=IntArray(64) { (kotlin.math.abs(kotlin.math.sin((it+1).toDouble()))*4294967296.0).toLong().toInt() }
            val state=intArrayOf(0x67452301,0xefcdab89.toInt(),0x98badcfe.toInt(),0x10325476)
            for(offset in 0 until count step 64) {
                val words=IntArray(16) { word -> (0..3).fold(0) { acc,i -> acc or ((data[offset+word*4+i].toInt() and 255) shl (8*i)) } }
                var a=state[0];var b=state[1];var c=state[2];var d=state[3]
                for(i in 0..63) {
                    val f=when(i/16) { 0->(b and c) or (b.inv() and d);1->(d and b) or (d.inv() and c);2->b xor c xor d;else->c xor (b or d.inv()) }
                    val g=when(i/16) { 0->i;1->(5*i+1)%16;2->(3*i+5)%16;else->(7*i)%16 }
                    val sum=a+f+constants[i]+words[g];val shift=shifts[(i/16)*4+i%4]
                    val next=b+((sum shl shift) or (sum ushr (32-shift)))
                    a=d;d=c;c=b;b=next
                }
                state[0]+=a;state[1]+=b;state[2]+=c;state[3]+=d
            }
            val digest=ByteArray(16) { (state[it/4] ushr (8*(it%4))).toByte() }
            digest[6]=((digest[6].toInt() and 15) or 48).toByte()
            digest[8]=((digest[8].toInt() and 63) or 128).toByte()
            val hex=digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
            return UUID(hex.substring(0,8)+"-"+hex.substring(8,12)+"-"+hex.substring(12,16)+"-"+hex.substring(16,20)+"-"+hex.substring(20))
        }
        private fun random():String=js("globalThis.crypto.randomUUID()")
    }
}
