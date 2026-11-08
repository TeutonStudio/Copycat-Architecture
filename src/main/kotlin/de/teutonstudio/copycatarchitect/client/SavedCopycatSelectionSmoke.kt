package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.PrinterView
import de.teutonstudio.copycatarchitect.server.PrintSettings
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen

/** Actual modal and inspector events exercise the same saved forms at both supported scales. */
internal object SavedCopycatSelectionSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var screen:BuildingScreen
    private var original=""
    private var libraryJson=""
    private var chosen=""
    private lateinit var shape:ShapeScreen
    private var shapeBefore=""
    private var shapeAfter=""
    private var savedNode=""
    private var contextPosition=0 to 0
    private val sourceId="wall_copycat"
    private val targetKey="saved_picker_target@7"
    private val target=Form(id="saved_picker_target",version=7,name="Fixtures / Framing / Copper cap",graph=NodeGraph(nodes=listOf(
        GraphNode("cap",part=Part("copycats:copycat_byte")),GraphNode("result",NodeKind.OUTPUT,listOf("cap")))))

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> begin(mc)
                8 -> cancelFromInspector(mc)
                15 -> browseFromInspector(mc)
                20 -> screenshot(mc,"folders-normal")
                22 -> {
                    check(picker(mc).keyPressed(257,0,0))
                    val versions=picker(mc);check(versions.isVersionDialog() && versions.selectedKey()=="saved_picker_target@16")
                    click(versions,checkNotNull(versions.rowPoint(targetKey)))
                    check(versions.selectedKey()==targetKey && document()==original)
                }
                23 -> screenshot(mc,"versions-normal")
                25 -> chooseFromDialog(mc)
                30 -> rotation(RotationAxis.X,1)
                35 -> { mc.options.guiScale().set(2);mc.resizeDisplay();screen.selectBuildingNode(sourceId);screen.openBuildingGraph(true) }
                42 -> searchAndScroll(mc)
                47 -> screenshot(mc,"search-compact")
                52 -> {
                    val before=document();val h=history();val dialog=picker(mc)
                    check(dialog.keyPressed(256,0,0));check(mc.screen===screen && document()==before && history()==h)
                }
                58 -> rotation(RotationAxis.Z,3)
                64 -> {
                    cancelFromInspector(mc)
                    check(Json.encode(ClientState.snapshot.library)==libraryJson) { "Browsing and selecting must preserve the shared library and saved appearance" }
                    beginShape(mc,false)
                }
                70 -> {
                    val editor=checkNotNull(shape.kilaGraph)
                    val x=editor.x+editor.width-8.0;val y=editor.y+editor.height-8.0
                    contextPosition=editor.graphPoint(x,y)
                    check(shape.mouseClicked(x,y,1));shape.mouseReleased(x,y,1)
                    check(editor.hasOpenMenu())
                }
                75 -> hoverShapeMenu("add_node")
                80 -> hoverShapeMenu("copycat_library")
                85 -> click(shape,checkNotNull(shape.kilaGraph!!.menuItemPoint(ui("node_saved"))))
                90 -> screenshot(mc,"shape-library-normal")
                95 -> {
                    check(picker(mc).keyPressed(256,0,0))
                    check(mc.screen===shape && Json.encode(shape.form)==shapeBefore)
                    check(!shapeButton("undo").active)
                }
                100 -> {
                    shape.graphCanvas!!.addAt(NodeKind.SAVED,Adapters.all.keys.first(),contextPosition.first,contextPosition.second)
                    chooseShapeSource(mc,targetKey)
                    val node=shape.form.graph!!.nodes.single { it.kind==NodeKind.SAVED }
                    savedNode=node.id
                    check(node.x==contextPosition.first && node.y==contextPosition.second)
                    check(node.savedCopycat!!.id==target.id && node.savedCopycat!!.version==7)
                    check(shape.printerMaterials[node.savedCopycat!!.parts.single().group]=="minecraft:copper_block")
                    check(shape.graphCanvas!!.connect(savedNode,"result",0))
                    shapeAfter=Json.encode(shape.form);shape.kilaGraph!!.fit()
                }
                106 -> {
                    val actual=Geometry.validate(shape.form);val expected=Geometry.validate(target)
                    check(Geometry.solid(actual.parts).key()==Geometry.solid(expected.parts).key())
                    check(Extrusion.cost(actual.parts,actual.groups)==Extrusion.cost(expected.parts,expected.groups))
                    screenshot(mc,"shape-source-normal")
                    click(shape,checkNotNull(shape.kilaGraph!!.sourceButtonPoint(savedNode)))
                }
                112 -> {
                    val dialog=picker(mc);check(dialog.selectedKey()==targetKey)
                    check(dialog.keyPressed(256,0,0));check(Json.encode(shape.form)==shapeAfter)
                    shapeAction("undo");shapeAction("undo")
                    check(Json.encode(shape.form)==shapeBefore && shape.printerMaterials.isEmpty())
                    shapeAction("redo");shapeAction("redo")
                    check(Json.encode(shape.form)==shapeAfter && shape.printerMaterials.values.single()=="minecraft:copper_block")
                    shape.kilaGraph!!.fit()
                }
                118 -> click(shape,checkNotNull(shape.kilaGraph!!.sourceButtonPoint(savedNode)))
                124 -> {
                    chooseShapeSource(mc,"picker_0@1")
                    check(shape.form.graph!!.nodes.single { it.id==savedNode }.savedCopycat!!.id=="picker_0")
                    check(shape.form.groups.size==2)
                    shapeAction("undo");check(Json.encode(shape.form)==shapeAfter)
                    shapeAction("redo");shapeAction("undo")
                    mc.options.guiScale().set(2);mc.resizeDisplay();beginShape(mc,true)
                }
                130 -> {
                    shape.graphCanvas!!.addAt(NodeKind.SAVED,Adapters.all.keys.first(),40,80)
                    chooseShapeSource(mc,targetKey)
                    savedNode=shape.form.graph!!.nodes.single { it.kind==NodeKind.SAVED }.id
                    check(shape.graphCanvas!!.connect(savedNode,"result",0))
                    shape.graphCanvas!!.rotateSource(savedNode,RotationAxis.Z,1)
                    val expected=PartRotations.rotate(Geometry.validate(target).parts.single(),RotationAxis.Z,1)!!
                    check(Geometry.solid(Geometry.validate(shape.form).parts).key()==Solid.part(expected).key())
                    shapeAfter=Json.encode(shape.form);shape.kilaGraph!!.fit()
                }
                136 -> screenshot(mc,"shape-source-compact")
                142 -> {
                    click(shape,checkNotNull(shape.kilaGraph!!.sourceButtonPoint(savedNode)))
                    check(picker(mc).keyPressed(256,0,0));check(Json.encode(shape.form)==shapeAfter)
                    shapeAction("undo");shapeAction("redo");check(Json.encode(shape.form)==shapeAfter)
                    check(Json.encode(ClientState.snapshot.library)==libraryJson)
                    verifyVersionDialog(mc)
                }
                148 -> screenshot(mc,"versions-compact")
                154 -> {
                    check(picker(mc).isVersionDialog());check(picker(mc).keyPressed(256,0,0))
                    check(!picker(mc).isVersionDialog() && picker(mc).selectedKey()==targetKey)
                    check(picker(mc).keyPressed(256,0,0));check(mc.screen===shape && Json.encode(shape.form)==shapeAfter)
                    check(Json.encode(ClientState.snapshot.library)==libraryJson)
                    ClientState.snapshot=previousSnapshot
                    mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT SAVED COPYCAT UI SMOKE PASSED: actual building inspector source opens shared selector, slash folders, graph geometry preview, search, scrolling, resize, Escape with unchanged history, exact id@version selection, atomic source and X/Z rotation Undo/Redo, and normal/compact widget bounds, native form-graph library insertion, cancellation, independent saved input, materials, source replacement, table/printer composition, Z rotation and atomic Undo/Redo; grouped same-name versions, numeric newest-first ordering, separate version dialogs, exact older-version choice, version cancellation, search, scrolling and resize at both scales verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT SAVED COPYCAT UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Saved Copycat selection UI smoke failed at tick $ticks",error)
        }
        return acted
    }

    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        val library=Library().apply {
            seed()
            (1..16).forEach { version ->
                val form=if(version==7) target else target.copy(version=version,graph=NodeGraph.fromParts(listOf(Part("copycats:copycat_slab"))))
                val key="${form.id}@$version"
                forms[key]=form;printSettings[key]=PrintSettings(mapOf("body" to if(version==7) "minecraft:copper_block" else "minecraft:gold_block"),0)
            }
            repeat(32) { i -> forms["picker_$i@1"]=Form(id="picker_$i",name="Fixtures / Framing / Trim ${i.toString().padStart(2,'0')}",parts=listOf(Part("copycats:copycat_byte"))) }
        }
        ClientState.snapshot=Snapshot(library=library);libraryJson=Json.encode(library)
        mc.options.guiScale().set(1);mc.resizeDisplay()
        screen=BuildingScreen().apply { building=Building(schema=2,id="saved_picker_smoke",graph=BuildingGraph.assembledStarter());selectBuildingNode(sourceId) }
        mc.setScreen(screen);screen.openBuildingGraph(true);original=document()
    }

    private fun beginShape(mc:Minecraft,printer:Boolean) {
        val draft=Form(id="derived",name="Derived Copycat",graph=NodeGraph())
        if(!printer) { mc.options.guiScale().set(1);mc.resizeDisplay() }
        shape=if(printer) ShapeScreen(printerSession=PrinterView(form=draft)) else ShapeScreen(parent=screen).apply { form=draft }
        mc.setScreen(shape);shapeBefore=Json.encode(shape.form)
    }
    private fun hoverShapeMenu(key:String) {
        val editor=checkNotNull(shape.kilaGraph)
        val point=checkNotNull(editor.menuItemPoint(ui(key))) { "Native saved Copycat menu row must exist: $key" }
        editor.mouseMoved(point.first,point.second)
    }
    private fun chooseShapeSource(mc:Minecraft,key:String) {
        val dialog=picker(mc)
        click(dialog,dialog.searchPoint())
        dialog.children().filterIsInstance<EditBox>().single().value=key
        check(dialog.visibleKeys()==listOf(key))
        check(dialog.keyPressed(257,0,0))
        (mc.screen as? SavedCopycatSelectionScreen)?.takeIf { it.isVersionDialog() }?.let { versions ->
            check(versions.selectedKey()==key);check(versions.keyPressed(257,0,0))
        }
        check(mc.screen===shape)
    }
    private fun verifyVersionDialog(mc:Minecraft) {
        shape.kilaGraph!!.fit()
        shape.graphCanvas!!.openSourceSelection(savedNode)
        val names=picker(mc)
        check(names.rowPoint(targetKey)==names.rowPoint("saved_picker_target@16")) { "All saved versions share one name row" }
        check(names.keyPressed(257,0,0))
        val versions=picker(mc)
        check(versions.isVersionDialog() && versions.selectedKey()==targetKey)
        check(versions.keyPressed(268,0,0));check(versions.selectedKey()=="saved_picker_target@16")
        val first=versions.visibleKeys();val point=checkNotNull(versions.rowPoint(first.first()))
        repeat(5) { check(versions.mouseScrolled(point.first,point.second,0.0,-1.0)) }
        check(versions.visibleKeys()!=first)
        click(versions,versions.searchPoint())
        versions.children().filterIsInstance<EditBox>().single().value=targetKey
        check(versions.visibleKeys()==listOf(targetKey))
        versions.resize(mc,mc.window.guiScaledWidth,mc.window.guiScaledHeight)
        check(versions.visibleKeys()==listOf(targetKey) && versions.selectedKey()=="saved_picker_target@16")
        check(versions.keyPressed(257,0,0))
        check(mc.screen===shape && Json.encode(shape.form)==shapeAfter) { "Choosing the existing exact version changes no source" }
        shape.graphCanvas!!.openSourceSelection(savedNode)
        check(picker(mc).keyPressed(257,0,0))
        check(picker(mc).isVersionDialog() && picker(mc).selectedKey()==targetKey)
        bounds(picker(mc))
    }
    private fun shapeButton(key:String)=shape.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui(key) }
    private fun shapeAction(key:String) { val button=shapeButton(key);check(button.active);click(shape,button.x+button.width/2.0 to button.y+button.height/2.0) }

    private fun document()=Json.encode(screen.building)
    private fun node()=screen.building.graph!!.nodes.single { it.id==sourceId }
    private fun history()=listOf("undo","redo").map { key -> screen.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui(key) }.active }
    private fun click(target:Screen,point:Pair<Double,Double>) {
        check(point.first>=0 && point.first<target.width && point.second>=0 && point.second<target.height) { "Picker hit target must fit ${target.width}x${target.height}: $point" }
        check(target.mouseClicked(point.first,point.second,0)) { "Actual picker or inspector widget must receive the click" }
        target.mouseReleased(point.first,point.second,0)
    }
    private fun action(key:String) {
        val button=screen.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui(key) }
        check(button.active);click(screen,button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun picker(mc:Minecraft)=mc.screen as? SavedCopycatSelectionScreen ?: error("Actual source row must open the saved Copycat dialog")
    private fun openInspectorPicker(mc:Minecraft):SavedCopycatSelectionScreen {
        screen.selectBuildingNode(sourceId);screen.openBuildingGraph(true)
        val inspector=checkNotNull(screen.buildingGraphCanvas)
        click(screen,inspector.propertyPoint(sourceId,0));return picker(mc)
    }
    private fun cancelFromInspector(mc:Minecraft) {
        val before=document();val h=history()
        val dialog=openInspectorPicker(mc)
        check(dialog.keyPressed(256,0,0))
        check(mc.screen===screen && document()==before && history()==h) { "Escape must preserve the graph and Undo/Redo" }
    }
    private fun browseFromInspector(mc:Minecraft) {
        check(document()==original)
        val dialog=openInspectorPicker(mc)
        click(dialog,checkNotNull(dialog.folderPoint("Fixtures")))
        click(dialog,checkNotNull(dialog.folderPoint("Fixtures/Framing")))
        check(dialog.currentFolder()=="Fixtures/Framing")
        click(dialog,checkNotNull(dialog.rowPoint(targetKey)))
        check(dialog.selectedKey()=="saved_picker_target@16" && document()==original)
        check(dialog.rowPoint(targetKey)==dialog.rowPoint("saved_picker_target@1"))
        check(dialog.keyPressed(257,0,0))
        val versions=picker(mc);check(versions.isVersionDialog())
        check(versions.keyPressed(269,0,0));check(versions.selectedKey()=="saved_picker_target@1")
        check(versions.keyPressed(256,0,0))
        check(mc.screen===dialog && dialog.currentFolder()=="Fixtures/Framing" && dialog.selectedKey()=="saved_picker_target@16")
        check(document()==original && history()==listOf(false,false))
        check(Geometry.validate(target).parts.isNotEmpty())
        bounds(dialog)
    }
    private fun chooseFromDialog(mc:Minecraft) {
        val dialog=picker(mc)
        check(dialog.keyPressed(257,0,0));check(mc.screen===screen)
        check(node().form==targetKey && node().block.isEmpty()) { "The chosen exact shared-library key must reach the building source" }
        chosen=document();check(chosen!=original && history()==listOf(true,false))
        action("undo");check(document()==original)
        action("redo");check(document()==chosen)
    }

    private fun revealProperty(row:Int):Pair<Double,Double> {
        val inspector=checkNotNull(screen.buildingGraphCanvas)
        var point=inspector.propertyPoint(sourceId,row)
        val bottom=inspector.y+inspector.height-12.0
        if(point.second>bottom) {
            check(screen.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(bottom-point.second)/25.0))
            point=inspector.propertyPoint(sourceId,row)
        }
        return point
    }
    private fun rotation(axis:RotationAxis,turns:Int) {
        check(Minecraft.getInstance().screen===screen)
        screen.selectBuildingNode(sourceId);screen.openBuildingGraph(true)
        val before=screen.building;val old=node()
        click(screen,revealProperty(66))
        check(screen.keyPressed(268,0,0))
        repeat(listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z).indexOf(axis)) { check(screen.keyPressed(264,0,0)) }
        check(screen.keyPressed(257,0,0))
        val axisChanged=screen.building
        check(node()==old.copy(rotationAxis=axis))
        click(screen,revealProperty(88))
        check(screen.keyPressed(268,0,0));repeat(turns) { check(screen.keyPressed(264,0,0)) };check(screen.keyPressed(257,0,0))
        val after=screen.building
        check(node()==old.copy(rotationAxis=axis,quarterTurns=turns))
        val evaluated=BuildingGraphCompiler.Evaluation(after.graph!!,after.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules).preview(sourceId)
        val expected=Geometry.validate(target).parts.map { PartRotations.rotate(it,axis,turns)!! }
        check(Geometry.solid(evaluated.placements.single().parts).key()==Geometry.solid(expected).key()) { "Inspector source orientation must reach evaluated geometry" }
        action("undo");check(screen.building==axisChanged)
        action("redo");check(screen.building==after)
        action("undo")
        if(axisChanged!=before) { action("undo");check(screen.building==before);action("redo");check(screen.building==axisChanged) }
        action("redo");check(screen.building==after)
    }

    private fun searchAndScroll(mc:Minecraft) {
        val before=document();val h=history();val dialog=openInspectorPicker(mc)
        check(dialog.currentFolder()=="Fixtures/Framing")
        val first=dialog.visibleKeys()
        val point=checkNotNull(dialog.rowPoint(first.first()))
        repeat(8) { check(dialog.mouseScrolled(point.first,point.second,0.0,-1.0)) }
        check(dialog.visibleKeys()!=first) { "Mouse wheel must browse saved forms beyond the visible rows" }
        click(dialog,dialog.searchPoint())
        targetKey.forEach { check(dialog.charTyped(it,0)) }
        check(dialog.visibleKeys()==listOf(targetKey)) { "Global search must find exact id@version keys" }
        dialog.resize(mc,mc.window.guiScaledWidth,mc.window.guiScaledHeight)
        check(dialog.visibleKeys()==listOf(targetKey) && dialog.currentFolder()=="Fixtures/Framing")
        dialog.children().filterIsInstance<EditBox>().single().value="unavailable_picker_fixture"
        check(dialog.visibleKeys().isEmpty())
        dialog.children().filterIsInstance<EditBox>().single().value=targetKey
        check(document()==before && history()==h);bounds(dialog)
    }
    private fun bounds(target:Screen) {
        target.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=target.width && widget.y+widget.height<=target.height) { "Saved Copycat widget must fit ${target.width}x${target.height}: ${widget.message.string}" }
        }
    }
    private fun screenshot(mc:Minecraft,name:String) {
        mc.screen?.let(::bounds)
        Screenshot.grab(mc.gameDirectory,"architect-saved-copycat-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
}
