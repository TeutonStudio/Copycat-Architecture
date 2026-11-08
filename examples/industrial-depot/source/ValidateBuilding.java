import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.teutonstudio.copycatarchitect.domain.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import de.teutonstudio.copycatarchitect.server.CommandTransport;
import de.teutonstudio.copycatarchitect.server.Command;
import de.teutonstudio.copycatarchitect.server.Library;
import de.teutonstudio.copycatarchitect.server.Json;

/** Standalone acceptance check and exact-surface export; uses the mod's compiled domain classes. */
public class ValidateBuilding {
  static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  static final Gson COMPACT=new GsonBuilder().disableHtmlEscaping().create();
  static class MeshObject {
    String name,component,material;
    List<double[]> vertices=new ArrayList<>();
    List<int[]> faces=new ArrayList<>();
    transient Map<String,Integer> indices=new HashMap<>();
    MeshObject(String component,String material) {this.component=component;this.material=material;name=component+"_"+material.replace(':','_');}
    int vertex(double[] p) {String key=Arrays.toString(p);return indices.computeIfAbsent(key,k->{vertices.add(p);return vertices.size()-1;});}
  }
  static void check(boolean yes,String message) {if(!yes) throw new IllegalStateException(message);}
  static void replaceReferences(JsonElement value,Map<String,String> replacements) {
    if(value.isJsonObject()) for(Map.Entry<String,JsonElement> entry:value.getAsJsonObject().entrySet()) {
      JsonElement child=entry.getValue();
      if(child.isJsonPrimitive() && child.getAsJsonPrimitive().isString() && replacements.containsKey(child.getAsString())) entry.setValue(new JsonPrimitive(replacements.get(child.getAsString())));
      else replaceReferences(child,replacements);
    } else if(value.isJsonArray()) for(int i=0;i<value.getAsJsonArray().size();i++) {
      JsonElement child=value.getAsJsonArray().get(i);
      if(child.isJsonPrimitive() && child.getAsJsonPrimitive().isString() && replacements.containsKey(child.getAsString())) value.getAsJsonArray().set(i,new JsonPrimitive(replacements.get(child.getAsString())));
      else replaceReferences(child,replacements);
    }
  }
  static String sha256(String content) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}
  public static void main(String[] args) throws Exception {
    Path library=Path.of(args.length>0 ? args[0] : "examples/industrial-depot/library.json");
    Path output=library.toAbsolutePath().getParent();
    JsonObject root=JsonParser.parseString(Files.readString(library)).getAsJsonObject();
    Map<String,Form> forms=JSON.fromJson(root.get("forms"),new TypeToken<Map<String,Form>>(){}.getType());
    Map<String,de.teutonstudio.copycatarchitect.domain.Module> modules=JSON.fromJson(root.get("modules"),new TypeToken<Map<String,de.teutonstudio.copycatarchitect.domain.Module>>(){}.getType());
    Map<String,Building> buildings=JSON.fromJson(root.get("buildings"),new TypeToken<Map<String,Building>>(){}.getType());
    for (Map.Entry<String,Form> form:forms.entrySet()) {
      Form validated=Geometry.INSTANCE.validate(form.getValue());
      check(form.getKey().equals(validated.getId()+"@"+validated.getVersion()),"Form version mismatch: "+form.getKey());
      Form restored=Geometry.INSTANCE.validate(JSON.fromJson(JSON.toJson(validated),Form.class));
      check(restored.equals(validated),"Form JSON roundtrip differs: "+form.getKey());
      form.setValue(validated);
    }
    for (de.teutonstudio.copycatarchitect.domain.Module module:modules.values()) module.validate();
    for(Map.Entry<String,Building> entry:buildings.entrySet()) {
      Building building=entry.getValue();
      check(Files.readString(output.resolve("building.json")).length()<=CommandTransport.MAX_JSON_CHARS,"Clipboard building exceeds editor import limit");
      Library savedLibrary=new Library();savedLibrary.seed();
      Map<String,String> replacementKeys=new HashMap<>();
      for(Map.Entry<String,Form> form:forms.entrySet()) {
        String uuid=UUID.nameUUIDFromBytes(form.getKey().getBytes(StandardCharsets.UTF_8)).toString();
        replacementKeys.put(form.getKey(),uuid+"@1");
        JsonObject stored=JsonParser.parseString(COMPACT.toJson(form.getValue())).getAsJsonObject();stored.addProperty("id",uuid);
        savedLibrary.getForms().put(uuid+"@1",Json.INSTANCE.getGson().fromJson(stored,Form.class));
      }
      JsonObject storedBuilding=JsonParser.parseString(COMPACT.toJson(building)).getAsJsonObject();
      replaceReferences(storedBuilding,replacementKeys);
      String buildingUuid=UUID.nameUUIDFromBytes(building.getId().getBytes(StandardCharsets.UTF_8)).toString();storedBuilding.addProperty("id",buildingUuid);
      Building rebound=Json.INSTANCE.getGson().fromJson(storedBuilding,Building.class);
      check(rebound.getGraph().equals(RoofsKt.withCurrentSchema(rebound.getGraph())),"Server schema upgrade unexpectedly changes graph");
      savedLibrary.getBuildings().put(buildingUuid+"@1",rebound);
      int storageChars=Json.INSTANCE.encode(savedLibrary).length();
      check(storageChars<=180000,"Seeded server library with imported UUID IDs exceeds 180000 characters: "+storageChars);
      String payload=Json.INSTANCE.encode(rebound);
      check(payload.length()<=180000,"Creative-build payload exceeds 180000 characters");
      Command command=new Command("saveBuilding",payload,"","minecraft:stone",0,new P(),new P(),-1,Collections.emptyMap(),-1,-1);
      String commandJson=Json.INSTANCE.encode(command);
      String wire=CommandTransport.INSTANCE.encode(commandJson,CommandTransport.MAX_JSON_CHARS,CommandTransport.MAX_WIRE_CHARS);
      check(commandJson.equals(CommandTransport.INSTANCE.decode(wire,CommandTransport.MAX_JSON_CHARS,CommandTransport.MAX_WIRE_CHARS)),"Editor CommandTransport roundtrip differs");
      System.out.println("Import gates: clipboard="+Files.readString(output.resolve("building.json")).length()+"/250000, UUID building="+payload.length()+"/180000, seeded UUID library="+storageChars+"/180000 ("+(180000-storageChars)+" free), actual save Command="+commandJson.length()+"/250000, compressed wire="+wire.length()+"/30000 characters");
      for(String action:List.of("creativeBuild","createJob")) {
        String actionJson=Json.INSTANCE.encode(new Command(action,payload,"","minecraft:stone",0,new P(),new P(),-1,Collections.emptyMap(),-1,-1));
        String actionWire=CommandTransport.INSTANCE.encode(actionJson,CommandTransport.MAX_JSON_CHARS,CommandTransport.MAX_WIRE_CHARS);
        check(actionJson.equals(CommandTransport.INSTANCE.decode(actionWire,CommandTransport.MAX_JSON_CHARS,CommandTransport.MAX_WIRE_CHARS)),action+" CommandTransport roundtrip differs");
        System.out.println("Actual "+action+" Command: JSON="+actionJson.length()+"/250000, compressed wire="+actionWire.length()+"/30000 characters");
      }
      CompiledBuilding compiled=BuildingGraphCompiler.INSTANCE.compile(building,forms,modules);
      Plan plan=compiled.getPlan();
      check(plan.equals(BuildingGraphCompiler.INSTANCE.compile(rebound,savedLibrary.getForms(),savedLibrary.getModules()).getPlan()),"UUID-rebound saved library creates a different plan");
      System.out.println("Storage JSON characters: building="+COMPACT.toJson(building).length()+", forms="+COMPACT.toJson(forms).length());
      Set<P> occupied=new HashSet<>();
      for(Placement p:plan.getPlacements()) check(occupied.add(p.getPos()),"Duplicate position "+p.getPos());
      for(P p:plan.getClear()) check(!occupied.contains(p),"Obstructed air "+p);
      check(plan.getWarnings().isEmpty(),"Compiler warning: "+plan.getWarnings());
      Building restored=JSON.fromJson(JSON.toJson(building),Building.class);
      check(building.equals(restored),"Building JSON roundtrip differs");
      check(plan.equals(BuildingGraphCompiler.INSTANCE.compile(restored,forms,modules).getPlan()),"Restored JSON creates a different plan");
      check(compiled.getAccessFootprints().containsKey("gallery_stairs"),"Gallery stairs missing");
      Set<P> clear=new HashSet<>(plan.getClear());
      for(int y=1;y<=11;y++) for(int x=6;x<=30;x++) for(int z=3;z<=24;z++)
        check(clear.contains(new P(x,y,z)),"Tall hall obstructed at "+x+","+y+","+z);
      for(int[] band:new int[][]{{11,13},{20,22}}) check(plan.getPlacements().stream().anyMatch(p->p.getComponent().equals("roof") && p.getPos().getZ()>=band[0] && p.getPos().getZ()<=band[1] && p.getSurfaces().containsValue("minecraft:glass")),"A real skylight band is missing");
      for(int x=42;x<=43;x++) for(int y=1;y<=3;y++) check(clear.contains(new P(x,y,28)),"Personnel door obstructed");
      check(occupied.contains(new P(42,0,29)) && occupied.contains(new P(43,0,29)),"External entrance stairs missing");
      check(plan.getPlacements().stream().anyMatch(p->p.getComponent().equals("roof") && p.getSurfaces().containsValue("minecraft:glass")),"Actual glass roof missing");
      check(plan.getPlacements().stream().anyMatch(p->p.getPos().getX()==42 && p.getPos().getY()==6 && p.getPos().getZ()==20),"Gallery walking floor missing");
      Map<String,MeshObject> objects=new LinkedHashMap<>();
      double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
      double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
      long sourceFaces=0;
      for(Placement placement:plan.getPlacements()) {
        check(placement.getBlock().isEmpty(),"Non-Copycat block needs native mesh adapter: "+placement.getBlock());
        ResolvedGeometry geometry=Geometry.INSTANCE.resolve(placement.getParts(),placement.getGroups());
        for(Map.Entry<String,ExactVolume> material:geometry.exactMaterials(placement.getMaterial(),placement.getSurfaces()).entrySet()) {
          String key=placement.getComponent()+"|"+material.getKey();
          MeshObject object=objects.computeIfAbsent(key,k->new MeshObject(placement.getComponent(),material.getKey()));
          for(ExactSurface surface:material.getValue().surfaces()) {
            int[] face=new int[surface.getVertices().size()];int j=0;
            for(ExactPoint point:surface.getVertices()) {
              double[] v={placement.getPos().getX()+point.getX().toDouble()/16.0,placement.getPos().getY()+point.getY().toDouble()/16.0,placement.getPos().getZ()+point.getZ().toDouble()/16.0};
              for(int axis=0;axis<3;axis++) {min[axis]=Math.min(min[axis],v[axis]);max[axis]=Math.max(max[axis],v[axis]);}
              face[j++]=object.vertex(v);
            }
            check(face.length>=3,"Degenerate exact surface");object.faces.add(face);sourceFaces++;
          }
        }
      }
      Map<String,Object> mesh=new LinkedHashMap<>();
      mesh.put("objects",new ArrayList<>(objects.values()));
      Map<String,Object> bounds=new LinkedHashMap<>();bounds.put("min",min);bounds.put("max",max);
      mesh.put("bounds",bounds);
      Path meshPath=output.resolve("render-mesh.json");
      String meshJson=COMPACT.toJson(mesh);
      Files.writeString(meshPath,meshJson+"\n");
      check(JsonParser.parseString(Files.readString(meshPath)).equals(JsonParser.parseString(meshJson)),"Mesh serialization differs");
      long exportedFaces=JsonParser.parseString(Files.readString(meshPath)).getAsJsonObject().getAsJsonArray("objects").asList().stream().mapToLong(o->o.getAsJsonObject().getAsJsonArray("faces").size()).sum();
      check(sourceFaces==exportedFaces,"Mesh face count differs from compiled exact geometry");
      Map<String,Object> bill=new LinkedHashMap<>();
      bill.put("positions",occupied.size());bill.put("clearCells",plan.getClear().size());bill.put("zincMillibuckets",plan.getRaw());bill.put("surfaceItems",new TreeMap<>(plan.getMaterials()));bill.put("buildingNodes",building.getGraph().getNodes().size());bill.put("forms",forms.size());bill.put("uuidSeededLibraryCharacters",storageChars);bill.put("uuidSeededLibraryFreeCharacters",180000-storageChars);bill.put("uuidBuildingCharacters",payload.length());bill.put("clipboardCharacters",Files.readString(output.resolve("building.json")).length());bill.put("saveCommandCharacters",commandJson.length());bill.put("compressedCommandCharacters",wire.length());bill.put("meshSha256",sha256(meshJson+"\n"));bill.put("exactMeshFaces",sourceFaces);bill.put("bounds",bounds);bill.put("storeys",compiled.getStoreys());
      Files.writeString(output.resolve("material-bill.json"),JSON.toJson(bill)+"\n");
      System.out.println(entry.getKey()+": "+forms.size()+" forms, "+building.getGraph().getNodes().size()+" nodes, "+occupied.size()+" placements, "+plan.getClear().size()+" clear cells, "+plan.getRaw()+" mB zinc");
      System.out.println("Exact mesh: "+objects.size()+" objects, "+sourceFaces+" faces; bounds "+Arrays.toString(min)+" .. "+Arrays.toString(max));
      System.out.println("Mesh SHA-256: "+sha256(meshJson+"\n"));
      System.out.println("Accepted: actual server UUID-library budget including seed, clipboard/build command limits, actual save/creativeBuild/createJob Commands, CommandTransport roundtrips, JSON roundtrip, identical recompiled and UUID-rebound plans, unique positions, free hall, real stairs, gallery floor, glass roof, deterministic exact mesh export and surface count.");
    }
  }
}
