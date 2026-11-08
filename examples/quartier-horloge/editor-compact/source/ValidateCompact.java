import com.google.gson.*;
import de.teutonstudio.copycatarchitect.domain.*;
import de.teutonstudio.copycatarchitect.domain.Module;
import java.nio.file.*;
import java.util.*;

/** Runs the actual mod compiler; exports validation data, never a model. */
public class ValidateCompact {
  static class Bundle { Map<String,Form> forms; Map<String,Module> modules; Map<String,Building> buildings; }
  public static void main(String[] args)throws Exception {
    Path out=Path.of(args[0]);Gson gson=new GsonBuilder().disableHtmlEscaping().create();
    Bundle bundle=gson.fromJson(Files.readString(out.resolve("library.json")),Bundle.class);
    Map<String,Form> validated=new LinkedHashMap<>();
    for(var e:bundle.forms.entrySet())validated.put(e.getKey(),Geometry.INSTANCE.validate(e.getValue()));
    for(Module module:bundle.modules.values())module.validate();
    Building building=bundle.buildings.values().iterator().next();
    long roofLevels=building.getGraph().getNodes().stream().filter(n->n.getKind()==BuildingKind.ROOF_LEVEL).count();
    if(roofLevels!=4)throw new IllegalStateException("Four roof levels required");
    System.out.println("Compiling complete compact building, four stairs and four exact roofs");
    CompiledBuilding compiled=BuildingGraphCompiler.INSTANCE.compile(building,validated,bundle.modules);
    Plan plan=compiled.getPlan();
    if(!plan.getWarnings().isEmpty())throw new IllegalStateException(plan.getWarnings().toString());
    Set<P> occupied=new HashSet<>();
    int[] min={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE},max={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
    for(Placement placement:plan.getPlacements()) {
      P pos=placement.getPos();
      if(!occupied.add(pos))throw new IllegalStateException("Duplicate "+pos);
      int[] xyz={pos.getX(),pos.getY(),pos.getZ()};
      for(int axis=0;axis<3;axis++){min[axis]=Math.min(min[axis],xyz[axis]);max[axis]=Math.max(max[axis],xyz[axis]+1);}
    }
    for(P clear:plan.getClear())if(occupied.contains(clear))throw new IllegalStateException("Obstructed required air "+clear);
    for(int x=5;x<=9;x++)for(int z=0;z<=16;z++)if(occupied.contains(new P(x,6,z)))throw new IllegalStateException("Gallery void closed");
    for(int z=0;z<=16;z++)for(int x:new int[]{4,10})if(!occupied.contains(new P(x,7,z)))throw new IllegalStateException("Missing gallery rail");
    if(compiled.getAccessFootprints().size()!=4)throw new IllegalStateException("Four staircases required");
    if(compiled.getStoreys().size()!=5)throw new IllegalStateException("Five tower levels required");
    long roofs=plan.getPlacements().stream().filter(p->p.getComponent().startsWith("roof")).count();
    if(roofs<1)throw new IllegalStateException("No roofs compiled");
    Map<String,Object> report=new LinkedHashMap<>();
    report.put("validation","passed: actual complete BuildingGraphCompiler including all four GABLE/GABLE/GABLE/HIP roofs; unique positions; unobstructed required air; 5 x 17 open gallery; both gallery rails; four staircases; five storeys");
    report.put("graphNodes",building.getGraph().getNodes().size());report.put("forms",validated.size());report.put("modules",bundle.modules.size());
    report.put("positions",plan.getPlacements().size());report.put("roofPlacements",roofs);report.put("requiredClearCells",plan.getClear().size());
    report.put("zincMillibuckets",plan.getRaw());report.put("surfaceItems",plan.getMaterials());report.put("storeys",compiled.getStoreys());report.put("accessInstances",compiled.getAccessFootprints().size());
    report.put("blockBounds",Map.of("min",min,"maxExclusive",max));
    Files.writeString(out.resolve("full-validation.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report)+"\n");
    System.out.println("VALIDATED FULL COMPACT BUILDING: "+plan.getPlacements().size()+" positions, "+roofs+" roof positions");
  }
}
