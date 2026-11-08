import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.teutonstudio.copycatarchitect.domain.*;
import de.teutonstudio.copycatarchitect.domain.Module;
import java.nio.file.*;
import java.util.*;

/** Calls the actual mod compiler; exports its material-owned exact polygons. */
public class CompileQuartier {
  static final Gson G = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  static class Bundle { Map<String,Form> forms; Map<String,Module> modules; Map<String,Building> buildings; }
  static class Mesh {
    String material,component;
    List<double[]> vertices=new ArrayList<>(); List<int[]> faces=new ArrayList<>();
    Mesh(String m,String c){material=m;component=c;}
  }
  static void save(Path p,Object o)throws Exception{Files.createDirectories(p.getParent());Files.writeString(p,G.toJson(o)+"\n");}
  public static void main(String[] args)throws Exception{
    Path out=Path.of(args.length>0?args[0]:"examples/quartier-horloge");
    Bundle bundle=G.fromJson(Files.readString(out.resolve("library.json")),Bundle.class);
    boolean structureOnly="true".equals(System.getenv("ARCHITECT_STRUCTURE_ONLY"));
    if(structureOnly){
      JsonElement project=G.toJsonTree(bundle);
      for(var be:project.getAsJsonObject().getAsJsonObject("buildings").entrySet())
        for(var ne:be.getValue().getAsJsonObject().getAsJsonObject("graph").getAsJsonArray("nodes")){
          JsonObject n=ne.getAsJsonObject();
          if("BUILDING".equals(n.get("kind").getAsString()))n.addProperty("roof","");
        }
      bundle=G.fromJson(project,Bundle.class);
    }
    Map<String,Form> validated=new LinkedHashMap<>();
    for(var e:bundle.forms.entrySet()){
      try { validated.put(e.getKey(),Geometry.INSTANCE.validate(e.getValue())); }
      catch(Throwable t){throw new RuntimeException("Invalid form "+e.getKey(),t);}
    }
    System.out.println("Validated "+validated.size()+" editable Copycat forms");
    bundle.forms=validated;
    Building b=bundle.buildings.values().iterator().next();
    for(Module m:bundle.modules.values())m.validate();
    System.out.println("Compiling complete building, stairs and exact roofs");
    CompiledBuilding compiled=BuildingGraphCompiler.INSTANCE.compile(b,bundle.forms,bundle.modules);
    System.out.println("Compiled complete building; checking gallery, rails, stairs and interiors");
    Plan plan=compiled.getPlan();
    if(!plan.getWarnings().isEmpty())throw new IllegalStateException(plan.getWarnings().toString());
    Set<P> occupied=new HashSet<>();
    for(Placement placement:plan.getPlacements())if(!occupied.add(placement.getPos()))throw new IllegalStateException("Duplicate "+placement.getPos());
    for(P clear:plan.getClear())if(occupied.contains(clear))throw new IllegalStateException("Obstructed interior "+clear);
    // Architecturally meaningful acceptance checks, independent of node IDs.
    for(int x=5;x<=9;x++)for(int z=0;z<=16;z++)
      if(occupied.contains(new P(x,6,z)))throw new IllegalStateException("Gallery void closed");
    for(int z=0;z<=16;z++)for(int x:new int[]{4,10})
      if(!occupied.contains(new P(x,7,z)))throw new IllegalStateException("Missing gallery rail at "+x+","+z);
    if(compiled.getAccessFootprints().size()!=4)throw new IllegalStateException("Four staircases required");
    if(compiled.getStoreys().size()!=5)throw new IllegalStateException("Five tower levels required");

    Map<String,Mesh> meshes=new TreeMap<>();
    double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
    double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
    int count=0;
    for(Placement placement:plan.getPlacements()){
      P pos=placement.getPos();
      Map<String,ExactVolume> materials;
      if(!placement.getBlock().isEmpty())materials=Map.of(placement.getBlock(),ExactVolume.Companion.box(0,0,0,16,16,16));
      else materials=Geometry.INSTANCE.resolve(placement.getParts(),placement.getGroups()).exactMaterials(placement.getMaterial(),placement.getSurfaces());
      for(var e:materials.entrySet()){
        Mesh mesh=meshes.computeIfAbsent(e.getKey()+"/"+placement.getComponent(),k->new Mesh(e.getKey(),placement.getComponent()));
        for(ExactSurface surface:e.getValue().surfaces()){
          int start=mesh.vertices.size();int[] face=new int[surface.getVertices().size()];
          int i=0;
          for(ExactPoint v:surface.getVertices()){
            double[] vertex={pos.getX()+v.getX().toDouble()/16,pos.getY()+v.getY().toDouble()/16,pos.getZ()+v.getZ().toDouble()/16};
            for(int axis=0;axis<3;axis++){min[axis]=Math.min(min[axis],vertex[axis]);max[axis]=Math.max(max[axis],vertex[axis]);}
            mesh.vertices.add(vertex);face[i]=start+i;i++;
          }
          if(face.length>=3)mesh.faces.add(face);
        }
      }
      if(++count%500==0)System.out.println("Exported "+count+" / "+plan.getPlacements().size()+" actual placements");
    }
    Map<String,Object> report=new LinkedHashMap<>();
    report.put("validation","passed: actual BuildingGraphCompiler; unique positions; unobstructed clear cells; 5 x 17 gallery void; both gallery rails; four real staircases; five tower levels");
    report.put("graphNodes",b.getGraph().getNodes().size());report.put("forms",bundle.forms.size());report.put("modules",bundle.modules.size());
    report.put("positions",plan.getPlacements().size());report.put("requiredClearCells",plan.getClear().size());report.put("zincMillibuckets",plan.getRaw());report.put("surfaceItems",plan.getMaterials());
    report.put("storeys",compiled.getStoreys());
    Map<String,Object> accessSummary=new LinkedHashMap<>();
    for(var e:compiled.getAccessFootprints().entrySet()){
      int x0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
      for(P p:e.getValue()){x0=Math.min(x0,p.getX());z0=Math.min(z0,p.getZ());x1=Math.max(x1,p.getX());z1=Math.max(z1,p.getZ());}
      accessSummary.put(e.getKey(),Map.of("cells",e.getValue().size(),"min",new int[]{x0,z0},"max",new int[]{x1+1,z1+1}));
    }
    report.put("accessFootprints",accessSummary);
    String libraryJson=new GsonBuilder().disableHtmlEscaping().create().toJson(bundle);
    report.put("libraryStorageCharacters",libraryJson.length());report.put("currentLibraryStorageLimit",180000);
    report.put("bounds",Map.of("min",min,"max",max));
    Map<String,Object> geometry=new LinkedHashMap<>();
    geometry.put("metadata",Map.of("source","Actual Copycat Architect BuildingGraphCompiler output","units","Minecraft blocks; X/Y/Z; 1/16 details and exact rational roof polygons","positions",plan.getPlacements().size()));
    geometry.put("bounds",Map.of("min",min,"max",max));geometry.put("meshes",meshes.values());
    save(out.resolve(structureOnly?"plan-structure.json":"plan-geometry.json"),geometry);
    save(out.resolve(structureOnly?"structure-validation.json":"material-bill.json"),report);
    if(structureOnly){System.out.println("VALIDATED STRUCTURE: "+plan.getPlacements().size()+" positions; roofing intentionally omitted for this separate structural diagnostic");return;}
    // Save validated forms, including their compiled shape and editable source graph.
    for(Form f:bundle.forms.values())save(out.resolve("forms/"+f.getId()+".json"),f);
    save(out.resolve("library.json"),bundle);
    save(out.resolve("building.json"),b);
    System.out.println("VALIDATED: "+plan.getPlacements().size()+" positions, "+plan.getRaw()+" mB zinc, "+meshes.size()+" material meshes");
  }
}
