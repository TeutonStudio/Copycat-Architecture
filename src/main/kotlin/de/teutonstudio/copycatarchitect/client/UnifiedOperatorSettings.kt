package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Native editing for imported operators, using the existing CC widgets and document commit path. */
internal object UnifiedOperatorSettings {
    fun open(node:GraphNode,nodes:List<GraphNode>,commit:(GraphNode)->Unit) {
        var current=node
        fun settings(n:GraphNode)=Settings(n.kind.name,n.operation,n.numberValue,n.comparisonValue,n.booleanValue,n.translation,n.rotationAxis,n.quarterTurns,n.mirror,n.inputs.size)
        show({ settings(current) },{ op -> UnifiedOperators.switch(current,op,(nodes.filter { it.id!=current.id }+current).associateBy { it.id }).also { commit(it);current=it } },{ key,value ->
            val next=when(key) {
                "number"->current.copy(numberValue=value as Int);"comparison"->current.copy(comparisonValue=value as Int);"boolean"->current.copy(booleanValue=value as Boolean)
                "position"->current.copy(translation=value as P);"axis"->current.copy(rotationAxis=value as RotationAxis);"turns"->current.copy(quarterTurns=value as Int);"mirror"->current.copy(mirror=value as CopycatMirror)
                "add"->current.copy(inputs=current.inputs+"");"remove"->current.copy(inputs=current.inputs.dropLast(1),inputGroups=current.inputGroups.dropLast(1));else->current
            };commit(next);current=next
        })
    }
    fun open(node:BuildingNode,nodes:List<BuildingNode>,commit:(BuildingNode)->Unit) {
        var current=node
        fun settings(n:BuildingNode)=Settings(n.kind.name,n.operation,n.numberValue,n.comparisonValue,n.booleanValue,n.translation,n.rotationAxis,n.quarterTurns,n.mirror,n.inputs.size)
        show({ settings(current) },{ op -> UnifiedOperators.switch(current,op,(nodes.filter { it.id!=current.id }+current).associateBy { it.id }).also { commit(it);current=it } },{ key,value ->
            val next=when(key) {
                "number"->current.copy(numberValue=value as Int);"comparison"->current.copy(comparisonValue=value as Int);"boolean"->current.copy(booleanValue=value as Boolean)
                "position"->current.copy(translation=value as P);"axis"->current.copy(rotationAxis=value as RotationAxis);"turns"->current.copy(quarterTurns=value as Int);"mirror"->current.copy(mirror=value as CopycatMirror)
                "add"->current.copy(inputs=current.inputs+"");"remove"->current.copy(inputs=current.inputs.dropLast(1));else->current
            };commit(next);current=next
        })
    }
    private data class Settings(val kind:String,val operation:String?,val number:Int?,val comparison:Int?,val boolean:Boolean?,val position:P?,val axis:RotationAxis,val turns:Int,val mirror:CopycatMirror?,val count:Int)
    private fun show(data:()->Settings,operation:(String)->Unit,edit:(String,Any)->Unit) {
        val mc=Minecraft.getInstance()
        mc.setScreen(object:Screen(Component.literal(UnifiedOperators.family(data().kind).label)) {
            val parent=mc.screen
            var error=""
            override fun init() {
                val n=data();val family=UnifiedOperators.family(n.kind);val op=n.operation ?: family.default
                val left=12;val column=(width-36)/2;var row=40
                fun button(x:Int,y:Int,text:String,action:()->Unit):EditorButton = addRenderableWidget(EditorButton(x,y,column,Component.literal(text)) { try { action();error="";rebuildWidgets() } catch(e:Exception) { error=e.message.orEmpty() } })
                family.operations.forEach { (key,spec) -> button(left,row,spec.label) { operation(key) }.apply { selected=key==op };row+=23 }
                var y=40;val x=24+column
                fun integer(label:String,value:Int,key:String) { button(x,y,"$label: $value") { EditorValueInput.integer(Component.literal(label),value,Int.MIN_VALUE..Int.MAX_VALUE) { edit(key,it) } };y+=24 }
                if(n.kind=="NUMBER_OPERATOR" && op=="VALUE") integer("Anzahl",n.number ?: 0,"number")
                if(n.kind=="RELATION_OPERATOR") integer("Vergleichswert B",n.comparison ?: 0,"comparison")
                if(n.kind=="STATEMENT_OPERATOR" && op=="VALUE") { button(x,y,if(n.boolean==true) "Wahr" else "Falsch") { edit("boolean",n.boolean!=true) };y+=24 }
                if(op=="TRANSLATE" || n.kind=="POSITION_OPERATOR" && op=="VALUE") for(axis in 0..2) {
                    val p=n.position ?: P();val value=listOf(p.x,p.y,p.z)[axis]
                    button(x,y,"${"XYZ"[axis]}: $value") { EditorValueInput.integer(Component.literal("${"XYZ"[axis]}"),value,Int.MIN_VALUE..Int.MAX_VALUE) { v -> edit("position",when(axis) { 0->p.copy(x=v);1->p.copy(y=v);else->p.copy(z=v) }) } };y+=24
                }
                if(op=="ROTATE") {
                    button(x,y,"Achse: ${n.axis}") { edit("axis",listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z).let { it[(it.indexOf(n.axis)+1)%3] }) };y+=24
                    button(x,y,"Drehung: ${n.turns*90}°") { edit("turns",(n.turns+1)%4) };y+=24
                }
                if(op=="MIRROR") { button(x,y,"Ebene: ${(n.mirror ?: CopycatMirror()).plane}") {
                    val planes=if(n.kind=="COPYCAT_OPERATOR") CopycatMirrorPlane.entries.toList() else listOf(CopycatMirrorPlane.X,CopycatMirrorPlane.Y,CopycatMirrorPlane.Z)
                    edit("mirror",CopycatMirror(planes[(planes.indexOf((n.mirror ?: CopycatMirror()).plane)+1)%planes.size]))
                };y+=24 }
                val spec=UnifiedOperators.spec(n.kind,op)
                if(spec.min!=spec.max) {
                    button(x,y,"+ Eingang") { edit("add",true) }.active=n.count<spec.max;y+=24
                    button(x,y,"− Eingang") { edit("remove",true) }.active=n.count>spec.min;y+=24
                }
                button(x,height-34,"Zurück") { onClose() }
            }
            override fun onClose() { mc.setScreen(parent) }
            override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
                ArchitectTheme.background(g,width,height);ArchitectTheme.header(g,width,title.string,width-16)
                super.render(g,mx,my,delta)
                if(error.isNotEmpty()) g.drawString(font,error,12,height-12,ArchitectTheme.danger,false)
            }
        })
    }
}
