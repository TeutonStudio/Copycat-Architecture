package de.teutonstudio.copycatarchitect.domain

/** UI-independent aggregation of quantities already resolved by the native planner. */
object MaterialRequirements {
    fun aggregate(items:Map<String,Int>):Map<String,Int> = items.entries.groupBy { it.key.substringBefore('[') }.mapValues { (_,items) -> items.sumOf { it.value } }
    fun form(parts:List<Part>,groups:List<MaterialGroup>,surfaces:Map<String,String>):Map<String,Int> = if(parts.isEmpty()) emptyMap() else aggregate(Placement(P(),parts,groups=groups,surfaces=surfaces.mapValues { it.value.substringBefore('[') }).surfaceItems())
}
