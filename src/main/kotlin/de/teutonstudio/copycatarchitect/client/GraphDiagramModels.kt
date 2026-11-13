package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*

/** Shared diagram DTOs used by Minecraft and Graphstudio. */
data class KilaNode(val id:String,val title:String,val x:Int,val y:Int,val inputs:List<String>,
                    val output:Boolean=true,val deletable:Boolean=true,
                    val inputTypes:List<String> = emptyList(),val outputType:String="shape",val source:String="",val discCut:Int=0,val part:Part?=null,
                    val rotatable:Boolean=false,val rotationAxis:RotationAxis=RotationAxis.NORMAL,val accent:Int?=null,
                    val previewExpandedByDefault:Boolean=false,val outputs:List<KilaOutput> = emptyList(),val sourceLabel:String="",
                    val inputKeys:List<String> = inputs.indices.map(Int::toString))
data class KilaOutput(val label:String,val type:String)
internal fun outputPortId(slot:Int)=if(slot==0) "out" else "out$slot"
internal fun outputPortSlot(id:String)=if(id=="out") 0 else id.removePrefix("out").toIntOrNull()
data class KilaLink(val source:String,val target:String,val slot:Int,val sourceSlot:Int=0)
data class KilaGraphData(val nodes:List<KilaNode>,val links:List<KilaLink>,val nodeGroups:List<GraphGroup> = emptyList())
