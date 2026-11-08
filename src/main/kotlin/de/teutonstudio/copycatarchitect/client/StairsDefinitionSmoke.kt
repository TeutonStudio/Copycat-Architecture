package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Opt-in real-widget acceptance for stair rotation and its immutable byte definition card. */
internal object StairsDefinitionSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var shape:ShapeScreen
    private lateinit var card:SourceDefinitionScreen
    private var parentJson=""
    private var parentHistory=emptyList<Boolean>()
    private var definitionJson=""
    private var quarterJson=""
    private var halfJson=""
    private val source=Part("copycats:copycat_stairs",turn=1,top=true,group="cap")

    private fun fixture()=Form(id="stairs_definition_smoke",version=7,name="Stairs from six bytes",
        groups=listOf(MaterialGroup(),MaterialGroup("cap","Copper stair","frame",4)),
        graph=NodeGraph(nodes=listOf(
            GraphNode("stairs",part=source,x=20,y=65),
            GraphNode("rotate",NodeKind.ROTATE,listOf("stairs",""),x=250,y=65,rotationAxis=RotationAxis.Z),
            GraphNode("result",NodeKind.OUTPUT,listOf("rotate"),x=490,y=65))))

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> begin(mc)
                10 -> { rotate(1);quarterJson=Json.encode(shape.form) }
                15 -> { rotate(1);halfJson=Json.encode(shape.form);press("undo");check(Json.encode(shape.form)==quarterJson) }
                20 -> { shape.selectShapeNode("stairs");shape.openGraph(true) }
                25 -> screenshot(mc,"inspector-normal")
                30 -> openDefinition(mc)
                40 -> checkNotNull(card.kilaGraph).fit()
                50 -> { verifyDefinition();screenshot(mc,"definition-normal") }
                60 -> verifyReadOnly(mc)
                70 -> closeDefinition(mc)
                80 -> {
                    mc.options.guiScale().set(2);mc.resizeDisplay()
                    check(shape.width in 400..499 && shape.height in 230..299) { "Compact stair acceptance must use the 427 x 240 viewport" }
                    shape.selectShapeNode("rotate");shape.openGraph(true)
                }
                90 -> { rotate(1);check(Json.encode(shape.form)==halfJson);press("undo");check(Json.encode(shape.form)==quarterJson) }
                95 -> { shape.selectShapeNode("stairs");shape.openGraph(true) }
                100 -> screenshot(mc,"inspector-compact")
                105 -> openDefinition(mc)
                115 -> checkNotNull(card.kilaGraph).fit()
                125 -> { verifyDefinition();screenshot(mc,"definition-compact") }
                135 -> verifyReadOnly(mc)
                140 -> screenshot(mc,"definition-inspector-compact")
                142 -> definitionTab(false)
                145 -> { verifyDefinition();closeDefinition(mc) }
                155 -> {
                    check(Json.encode(shape.form)==quarterJson && history()==listOf(true,true))
                    ClientState.snapshot=previousSnapshot
                    mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT STAIRS DEFINITION UI SMOKE PASSED: real Z rotation controls, exact occupancy and source retention, atomic Undo/Redo, actual inspector eye, six byte sources plus union/output in a view-only native graph, disabled source/numeric/material/wiring/deletion/drag controls, normal/compact layouts, and unchanged parent document/history verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT STAIRS DEFINITION UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Stairs definition UI smoke failed at tick $ticks",error)
        }
        return acted
    }

    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        ClientState.snapshot=Snapshot(library=Library().apply { seed() })
        mc.options.guiScale().set(1);mc.resizeDisplay()
        shape=ShapeScreen().apply {
            form=fixture();graphMode=true;graphInspector=true;material=2
            surfaceMaterials["body"]=3;surfaceMaterials["cap"]=4;selectShapeNode("rotate")
        }
        mc.setScreen(shape)
        check(shape.width==854 && shape.height==480) { "Stair acceptance requires the configured 854 x 480 client window" }
    }

    private fun click(screen:Screen,point:Pair<Double,Double>,button:Int=0,required:Boolean=true) {
        check(point.first>=0 && point.first<screen.width && point.second>=0 && point.second<screen.height) {
            "Stair hit target must fit ${screen.width} x ${screen.height}: $point"
        }
        val accepted=screen.mouseClicked(point.first,point.second,button)
        if(required) check(accepted) { "The actual stair widget must receive its click" }
        screen.mouseReleased(point.first,point.second,button)
    }
    private fun button(key:String)=shape.children().filterIsInstance<Button>().single { it.message.string==ui(key) }
    private fun history()=listOf(button("undo").active,button("redo").active)
    private fun definitionTab(inspector:Boolean) {
        if(card.width>=700) return
        val control=card.children().filterIsInstance<Button>().single { it.message.string==ui(if(inspector) "control_panel" else "node_graph") }
        click(card,control.x+control.width/2.0 to control.y+control.height/2.0)
        check(card.inspectorMode==inspector) { "The compact definition toolbar must switch its actual graph and inspector views" }
    }
    private fun press(key:String) {
        val button=button(key);check(button.active) { "Stair $key must be available" }
        click(shape,button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun revealRotation() {
        val inspector=checkNotNull(shape.graphCanvas)
        val point=checkNotNull(inspector.rotationButtonPoint(RotationAxis.Z,1))
        val target=point.second.coerceIn(inspector.y+54.0,inspector.y+inspector.height-12.0)
        if(point.second!=target) check(shape.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/21.0))
    }
    private fun rotate(turns:Int) {
        check(Minecraft.getInstance().screen===shape)
        shape.selectShapeNode("rotate");revealRotation()
        val before=shape.form;val graph=checkNotNull(before.graph);val node=graph.nodes.single { it.id=="rotate" }
        val q=Math.floorMod(node.quarterTurns+turns,4)
        val expected=before.copy(graph=graph.replace(node.copy(quarterTurns=q)))
        click(shape,checkNotNull(shape.graphCanvas!!.rotationButtonPoint(RotationAxis.Z,turns)))
        check(shape.form==expected) { "Clicking the Z rotation must only edit the rotation node" }
        check(shape.form.graph!!.nodes.single { it.id=="stairs" }.part==source) { "Rotation must preserve the semantic stair source" }
        val parts=Geometry.validate(shape.form).parts
        check(parts.size==1 && parts.single().source==source.source && parts.all { it.group=="cap" }) { "Rotation must retain the source and material receipt" }
        check(Geometry.solid(parts).key()==Solid.part(source).rotate(RotationAxis.Z,q).key()) { "The real Z rotation control must compile the exact rotated stair occupancy" }
        check(shape.material==2 && shape.surfaceMaterials==mapOf("body" to 3,"cap" to 4))
        press("undo");check(shape.form==before) { "Stair rotation must Undo atomically" }
        press("redo");check(shape.form==expected) { "Stair rotation must Redo atomically" }
    }

    private fun openDefinition(mc:Minecraft) {
        check(mc.screen===shape && shape.graphView.selected=="stairs")
        parentJson=Json.encode(shape.form);parentHistory=history()
        check(parentHistory==listOf(true,true)) { "Definition viewing must preserve both a real Undo and Redo entry" }
        val point=checkNotNull(shape.graphCanvas!!.sourceDefinitionPoint()) { "The stair inspector must expose its definition eye" }
        click(shape,point)
        card=mc.screen as? SourceDefinitionScreen ?: error("The actual inspector eye must open the source definition card")
        definitionJson=Json.encode(card.definition)
        check(Json.encode(shape.form)==parentJson)
    }
    private fun verifyDefinition() {
        check(Minecraft.getInstance().screen===card)
        val graph=checkNotNull(card.definition.graph)
        val bytes=graph.nodes.filter { it.kind==NodeKind.INPUT }
        val union=graph.nodes.single { it.kind==NodeKind.UNION }
        val output=graph.nodes.single { it.kind==NodeKind.OUTPUT }
        check(graph.nodes.size==8 && bytes.size==6 && bytes.all { it.part.source=="copycats:copycat_byte" }) { "The stair card must show its six byte sources, union, and output" }
        check(union.inputs.toSet()==bytes.map { it.id }.toSet() && output.inputs==listOf(union.id))
        val parts=Geometry.validate(card.definition).parts
        check(parts.size==6 && parts.all { it.group==source.group })
        check(Geometry.solid(parts).key()==Solid.part(source).key()) { "Displayed byte geometry must match the complete selected stair source" }
        check(card.diagram().nodes.size==8 && card.diagram().links.size==7)
        val native=checkNotNull(card.kilaGraph)
        graph.nodes.forEach { node ->
            val point=checkNotNull(native.nodeHeaderPoint(node.id)) { "Every definition node needs a real native widget" }
            check(point.first>=native.x && point.first<native.x+native.width && point.second>=native.y && point.second<native.y+native.height) { "Fit must keep every definition node inside the visible graph: ${node.id}: $point" }
        }
        card.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=card.width && widget.y+widget.height<=card.height) { "Definition widget must fit the normal and compact layout: ${widget.message.string}" }
        }
        check(card.graphCanvas!!.previewParts().isNotEmpty()) { "The readonly definition inspector needs the compiled stair preview" }
        check(Json.encode(card.definition)==definitionJson && Json.encode(shape.form)==parentJson)
    }

    private fun verifyReadOnly(mc:Minecraft) {
        val graph=checkNotNull(card.definition.graph);val byte=graph.nodes.first { it.kind==NodeKind.INPUT };val union=graph.nodes.single { it.kind==NodeKind.UNION }
        val native=checkNotNull(card.kilaGraph)
        val header=checkNotNull(native.nodeHeaderPoint(byte.id))
        click(card,header,required=false)
        check(card.graphView.selected==byte.id) { "The readonly graph must still allow source inspection" }
        check(card.mouseClicked(header.first,header.second,0))
        card.mouseDragged(header.first+14.0,header.second+8.0,0,14.0,8.0)
        card.mouseReleased(header.first+14.0,header.second+8.0,0)
        card.keyPressed(261,0,0)
        click(card,checkNotNull(native.sourceButtonPoint(byte.id)),required=false)
        check(mc.screen===card) { "A readonly byte source must never open the mutable source picker" }
        val from=checkNotNull(native.outputPortPoint(byte.id));val to=checkNotNull(native.inputPortPoint(union.id,0))
        card.mouseClicked(from.first,from.second,0)
        card.mouseDragged(to.first,to.second,0,to.first-from.first,to.second-from.second)
        card.mouseReleased(to.first,to.second,0)
        click(card,native.x+native.width-10.0 to native.y+native.height-10.0,1,required=false)
        // The native context menu may still offer Fit, which only changes this card's view.
        if(native.hasOpenMenu()) {
            check(card.keyPressed(256,0,0))
            check(mc.screen===card && !native.hasOpenMenu()) { "Escape must close the readonly navigation menu before closing the card" }
        }

        definitionTab(true)
        card.selectNode(byte.id)
        val inspector=checkNotNull(card.graphCanvas)
        check(inspector.sourceDefinitionPoint()==null) { "A primitive byte has no recursive definition eye" }
        // Actual disabled source, half and offset widgets remain visible for inspection.
        for(y in listOf(inspector.y+71.0,inspector.y+92.0,inspector.y+113.0)) {
            if(y<inspector.y+inspector.height) click(card,inspector.x+30.0 to y)
            check(mc.screen===card && !inspector.hasOpenMenu()) { "Readonly properties must not open pickers, dropdowns, or numeric prompts" }
        }
        inspector.deleteSelected();inspector.add(NodeKind.INPUT,"copycats:copycat_byte")
        check(!inspector.connect(byte.id,union.id,0)) { "Readonly inspector wiring must be rejected" }
        card.selectNode(union.id)
        inspector.materialModePoint(true)?.takeIf { it.second>=inspector.y+42 && it.second<inspector.y+inspector.height }?.let { click(card,it) }
        card.selectNode(byte.id)
        check(Json.encode(card.definition)==definitionJson && Json.encode(shape.form)==parentJson) { "Readonly graph and inspector events must preserve every definition and parent document field" }
    }

    private fun closeDefinition(mc:Minecraft) {
        check(card.keyPressed(256,0,0))
        check(mc.screen===shape && Json.encode(shape.form)==parentJson && history()==parentHistory) { "Closing the definition card must restore the exact parent screen and Undo/Redo state" }
        press("redo");check(Json.encode(shape.form)==halfJson) { "Viewing the definition must retain the original next Redo action" }
        press("undo");check(Json.encode(shape.form)==quarterJson && history()==parentHistory) { "Viewing the definition must retain the original Undo action" }
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-stairs-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
}
