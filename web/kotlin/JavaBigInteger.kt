package java.math

/** Exact browser integers use native BigInt, including the JVM overflow checks. */
class BigInteger private constructor(private val value:dynamic):Comparable<BigInteger> {
    operator fun plus(other:BigInteger)=BigInteger(add(value,other.value))
    operator fun minus(other:BigInteger)=BigInteger(subtract(value,other.value))
    operator fun times(other:BigInteger)=BigInteger(multiply(value,other.value))
    operator fun div(other:BigInteger)=BigInteger(divide(value,other.value))
    operator fun unaryMinus()=BigInteger(negate(value))
    override fun compareTo(other:BigInteger):Int=compare(value,other.value)
    override fun equals(other:Any?)=other is BigInteger && compareTo(other)==0
    override fun hashCode()=toString().hashCode()
    override fun toString():String=value.toString()
    fun signum():Int=compare(value,ZERO.value)
    fun abs()=if(signum()<0) -this else this
    fun gcd(other:BigInteger):BigInteger {
        var a=abs();var b=other.abs()
        while(b.signum()!=0) { val r=BigInteger(remainder(a.value,b.value));a=b;b=r }
        return a
    }
    fun toDouble():Double=number(value)
    fun longValueExact():Long {
        require(compare(value,MIN_LONG)>=0 && compare(value,MAX_LONG)<=0) { "BigInteger out of long range" }
        return toString().toLong()
    }
    companion object {
        val ZERO=BigInteger(big("0"))
        val ONE=BigInteger(big("1"))
        private val MIN_LONG=big("-9223372036854775808")
        private val MAX_LONG=big("9223372036854775807")
        fun valueOf(value:Long)=BigInteger(big(value.toString()))
        private fun big(value:String):dynamic=js("BigInt(value)")
        private fun add(a:dynamic,b:dynamic):dynamic=js("a+b")
        private fun subtract(a:dynamic,b:dynamic):dynamic=js("a-b")
        private fun multiply(a:dynamic,b:dynamic):dynamic=js("a*b")
        private fun divide(a:dynamic,b:dynamic):dynamic=js("a/b")
        private fun remainder(a:dynamic,b:dynamic):dynamic=js("a%b")
        private fun negate(a:dynamic):dynamic=js("-a")
        private fun compare(a:dynamic,b:dynamic):Int=js("a<b?-1:a>b?1:0")
        private fun number(a:dynamic):Double=js("Number(a)")
    }
}
