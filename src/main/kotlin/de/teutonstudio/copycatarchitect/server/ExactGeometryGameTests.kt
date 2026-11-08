package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object ExactGeometryGameTests {
    /** Valid source data can be larger than a history entry even when its resolved shape is simple. */
    internal fun oversizedHistoryForm():Form {
        val cell=ExactVolume.box(0,0,0,16,16,16).cells.single()
        return Geometry.validate(Form(id="oversized_exact_history",name="Oversized exact source",
            parts=listOf(Part(RoofPartParameters.SOURCE,exact=ExactVolume(List(512) { cell })))))
    }
    private fun diskRoundTrip(tag:CompoundTag):CompoundTag {
        val output=java.io.ByteArrayOutputStream()
        net.minecraft.nbt.NbtIo.writeCompressed(tag,output)
        return net.minecraft.nbt.NbtIo.readCompressed(java.io.ByteArrayInputStream(output.toByteArray()),net.minecraft.nbt.NbtAccounter.unlimitedHeap())
    }

    @JvmStatic @GameTest(template="empty") fun largeExactJsonSurvivesRealNbtIoForDevicesPreviewsAndComposites(h:GameTestHelper) {
        val form=oversizedHistoryForm();val registries=h.level.registryAccess();val pos=h.absolutePos(BlockPos(1,1,1))
        val surfaces=mapOf("body" to "minecraft:stone")
        val device=ItemStack(Architect.GUN.get())
        PrinterStack.draft(device,form,surfaces,3)
        h.assertTrue(PrinterStack.tag(device).contains("draft",7),"Large printer definitions must use a byte array instead of NBT's bounded StringTag")
        val restored=ItemStack.parseOptional(registries,diskRoundTrip(device.save(registries) as CompoundTag))
        h.assertTrue(PrinterStack.form(restored)==form && PrinterStack.surfaces(restored)==surfaces && PrinterStack.tag(restored).getInt("turn")==3,"Actual compressed item NBT must retain the complete exact printer draft and settings")
        val printer=PrinterEntity(pos,Architect.PRINTER.get().defaultBlockState())
        printer.original=device;printer.preview=form;printer.surfaces=surfaces;printer.turn=3
        val previewTag=diskRoundTrip(printer.saveWithoutMetadata(registries))
        val loadedPrinter=PrinterEntity(pos,printer.blockState);loadedPrinter.loadWithComponents(previewTag,registries)
        h.assertTrue(previewTag.contains("preview",7) && loadedPrinter.preview==form && loadedPrinter.surfaces==surfaces && loadedPrinter.turn==3,"A placed printer's large preview and original device must survive actual NBT I/O")
        val composite=CompositeEntity(pos,Architect.COMPOSITE.get().defaultBlockState())
        composite.parts=form.parts;composite.surfaces=surfaces;composite.receipt=Receipt(units=256)
        val partsTag=diskRoundTrip(composite.saveWithoutMetadata(registries))
        val loadedComposite=CompositeEntity(pos,composite.blockState);loadedComposite.loadWithComponents(partsTag,registries)
        h.assertTrue(partsTag.contains("parts",7) && loadedComposite.parts==form.parts && loadedComposite.surfaces==surfaces && loadedComposite.receipt==composite.receipt,"Large placed exact geometry, materials and receipt must survive compressed NBT")
        val saved=CompositeFormItem.stack(form,surfaces,2)
        val loadedSaved=ItemStack.parseOptional(registries,diskRoundTrip(saved.save(registries) as CompoundTag))
        h.assertTrue(Geometry.validate(Json.decode<Form>(JsonNbt.get(PrinterStack.tag(loadedSaved),"form_definition")))==form,"Large reusable saved-form item definitions must retain their complete geometry")
        val small=Geometry.validate(Form(parts=listOf(Part("copycats:copycat_block"))))
        PrinterStack.draft(device,small,emptyMap(),0)
        val legacy=ItemStack.parseOptional(registries,diskRoundTrip(device.save(registries) as CompoundTag))
        h.assertTrue(PrinterStack.tag(legacy).contains("draft",8) && PrinterStack.form(legacy)==small,"Existing string-based printer drafts must remain readable")
        val legacyParts=CompoundTag().apply { putString("parts",Json.encode(small.parts)) }
        loadedComposite.loadWithComponents(diskRoundTrip(legacyParts),registries)
        h.assertTrue(loadedComposite.parts==small.parts,"Existing string-based placed geometry must remain readable")
        val unicode=CompoundTag();val supplementary="\uD83D\uDE00".repeat(12000)
        JsonNbt.put(unicode,"json",supplementary)
        h.assertTrue(unicode.contains("json",7) && JsonNbt.get(diskRoundTrip(unicode),"json")==supplementary,"The byte-array threshold must account for modified UTF's six-byte supplementary characters")
        h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun savedRoofVersionsHistoryAndJobsRetainExactSnapshots(h:GameTestHelper) {
        val level=h.level
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"ArchitectRoofHistory"))
        actor.setPos(h.absolutePos(BlockPos(1,1,1)).x.toDouble(),h.absolutePos(BlockPos(1,1,1)).y.toDouble(),h.absolutePos(BlockPos(1,1,1)).z.toDouble())
        actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Architect.GUN.get()))
        val parameters=RoofPartParameters(2,3,1)
        val groups=listOf(MaterialGroup(),MaterialGroup("frame","Frame","frame",1))
        val graph=NodeGraph(nodes=listOf(
            GraphNode("roof",part=Part(RoofPartParameters.SOURCE,exact=ExactGeometry.roof(parameters),roof=parameters)),
            GraphNode("frame",part=Part("copycats:copycat_beam",group="frame")),
            GraphNode("union",NodeKind.UNION,listOf("roof","frame")),GraphNode("result",NodeKind.OUTPUT,listOf("union"))))
        val form=Geometry.validate(Form(id="exact_saved_roof",version=77,name="Exact roof history",groups=groups,graph=graph))
        val materials=mapOf("body" to "minecraft:stone","frame" to "minecraft:oak_planks")
        val data=ArchitectData.get(level)
        val old=form.copy(version=3)
        data.library(actor.uuid).forms["${form.id}@3"]=old
        Network.handle(actor,Json.encode(Command("saveForm",payload=Json.encode(form),surfaces=materials,turn=3)))
        val library=data.library(actor.uuid)
        val saved=library.forms["${form.id}@4"]
        h.assertTrue(saved!=null,"The actual saveForm command must accept the procedural roof item without requiring a native BlockState")
        h.assertTrue(saved!!.version==4 && saved.schema==4 && saved.graph!!.schema==3 && library.forms["${form.id}@3"]==old,"Saving must create the next exact form version and retain the previous immutable version")
        h.assertTrue(library.printSettings["${form.id}@4"]==PrintSettings(materials,3),"The saved roof version must retain its coatings and turn")
        PrinterService.record(library,saved,materials,3)
        val record=library.history.single()
        val resolver=Resolver()
        resolver.add(Placement(P(3,4,5),Geometry.rotate(saved.parts,3),material="",groups=saved.groups,surfaces=materials))
        val plan=resolver.plan(emptyList())
        val frozen=Job(owner=actor.uuid.toString(),dimension=level.dimension().location().toString(),name=saved.name,plan=plan)
        // Neither history nor an already compiled job may depend on live library form references.
        library.forms.clear()
        val snapshot=ArchitectData(Database(libraries=linkedMapOf(actor.uuid.toString() to library),jobs=mutableListOf(frozen)))
        val tag=diskRoundTrip(snapshot.save(CompoundTag(),level.registryAccess()))
        val loaded=Json.decode<Database>(String(tag.getByteArray("architect"),Charsets.UTF_8))
        val loadedLibrary=loaded.libraries.getValue(actor.uuid.toString())
        val history=loadedLibrary.history.single()
        h.assertTrue(history.form==saved && history.surfaces==materials && history.turn==3 && history.form.parts.any { it.exact!=null },"The full exact form, coatings and turn must survive print history NBT without any live form reference")
        val actual=loaded.jobs.single().plan.placements.single()
        h.assertTrue(actual.sameVisual(plan.placements.single()) && loaded.jobs.single().plan.raw==plan.raw && loaded.jobs.single().plan.materials==plan.materials,"Frozen jobs must preserve exact material geometry, billing and rotation through database NBT")
        val key=loadedLibrary.savePrintHistory(record.id,"Recovered exact roof")
        val recovered=loadedLibrary.forms.getValue(key)
        h.assertTrue(recovered.schema==4 && recovered.graph!!.schema==3 && recovered.parts==saved.parts && loadedLibrary.printSettings[key]==PrintSettings(materials,3),"Saving an exact history snapshot as a new form must retain all geometry and saved print settings")
        h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun rationalRoofPersistsItsSurfacesCollisionAndPaidReceipt(h:GameTestHelper) {
        val level=h.level;val position=h.absolutePos(BlockPos(1,1,1))
        level.setBlockAndUpdate(position,Blocks.AIR.defaultBlockState())
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"ArchitectExactRoof"))
        actor.setPos(position.x+4.0,position.y.toDouble(),position.z+4.0)
        val inventory=SimpleContainer(4)
        inventory.setItem(0,Economy.cartridge(512));inventory.setItem(1,ItemStack(Items.STONE,2));inventory.setItem(2,ItemStack(Items.OAK_PLANKS,2))
        val volume=ExactVolume.convex(listOf(ExactPlane(0,3,-2,1)))
        val groups=listOf(MaterialGroup(),MaterialGroup("frame","Frame","frame",1))
        val parts=listOf(Part(RoofPartParameters.SOURCE,exact=volume,roof=RoofPartParameters(2,3,1)),Part("copycats:copycat_beam",group="frame"))
        val placement=Placement(parts=parts,groups=groups,material="minecraft:stone",surfaces=mapOf("frame" to "minecraft:oak_planks"),component="roof")
        val cost=Extrusion.cost(parts,groups)
        h.assertTrue(PlacementService.place(level,position,placement,inventory,actor,"exact-roof"),"A rational mixed roof must place normally")
        h.assertTrue(Economy.units(inventory.getItem(0))==512-cost && inventory.getItem(1).count==1 && inventory.getItem(2).count==1,"The roof must debit its conservative voxel volume and its two coatings once")
        h.assertTrue(!PlacementService.place(level,position,placement,inventory,actor,"exact-roof"),"An identical rational roof must be idempotent before charging")
        val entity=level.getBlockEntity(position) as CompositeEntity
        val expected=Geometry.resolve(parts,groups)
        val bounds=entity.shape().toAabbs()
        for(x in 0..15) for(y in 0..15) for(z in 0..15) {
            val occupied=bounds.any { it.contains((x+0.5)/16,(y+0.5)/16,(z+0.5)/16) }
            h.assertTrue(occupied==expected.solid.has(x,y,z),"Actual collision must match every conservative exact-volume voxel")
        }
        val saved=entity.saveWithoutMetadata(level.registryAccess())
        val receipt=entity.receipt
        entity.parts=listOf(Part("copycats:copycat_block"))
        val loaded=CompositeEntity(position,entity.blockState)
        loaded.loadWithComponents(saved,level.registryAccess())
        val actual=Geometry.resolve(loaded.parts,loaded.groups)
        h.assertTrue(actual.sameVisual(loaded.material,loaded.surfaces,expected,placement.material,placement.surfaces) && loaded.receipt==receipt,"NBT must keep exact planes, material ownership and paid receipt independently of subsequent edits")
        h.assertTrue(loaded.parts.first().roof==parts.first().roof && actual.exactSolid!!.surfaces().any { face -> face.vertices.any { it.y.denominator.toString()=="3" } },"Rational source parameters and non-grid model vertices must survive NBT reload")
        level.removeBlock(position,false);h.succeed()
    }
}
