import com.google.gson.*;
import de.teutonstudio.copycatarchitect.domain.*;
import de.teutonstudio.copycatarchitect.server.Library;
import de.teutonstudio.copycatarchitect.server.Json;
import java.nio.file.*;
import java.util.*;

/** Uses the actual server Gson and Library.seed(), rather than raw source JSON lengths. */
public class NormalizeCompact {
  public static void main(String[] args)throws Exception {
    Path out=Path.of(args[0]); Gson g=new Gson();
    JsonObject input=JsonParser.parseString(Files.readString(out.resolve("library.json"))).getAsJsonObject();
    Library seed=new Library(); seed.seed();
    int seedCharacters=Json.INSTANCE.encode(seed).length();
    Map<String,Form> forms=new LinkedHashMap<>();
    for(var e:input.getAsJsonObject("forms").entrySet())forms.put(e.getKey(),Geometry.INSTANCE.validate(g.fromJson(e.getValue(),Form.class)));
    Map<String,de.teutonstudio.copycatarchitect.domain.Module> modules=new LinkedHashMap<>();
    for(var e:input.getAsJsonObject("modules").entrySet())modules.put(e.getKey(),g.fromJson(e.getValue(),de.teutonstudio.copycatarchitect.domain.Module.class).validate());
    Map<String,Building> buildings=new LinkedHashMap<>();
    for(var e:input.getAsJsonObject("buildings").entrySet())buildings.put(e.getKey(),g.fromJson(e.getValue(),Building.class));
    Map<String,Object> project=new LinkedHashMap<>();project.put("forms",forms);project.put("modules",modules);project.put("buildings",buildings);project.put("history",List.of());project.put("printSettings",Map.of());
    String normal=Json.INSTANCE.encode(project);
    seed.getForms().putAll(forms);seed.getModules().putAll(modules);seed.getBuildings().putAll(buildings);
    String merged=Json.INSTANCE.encode(seed);
    if(merged.length()>170000)throw new IllegalStateException("Compact variant exceeds 170000 including seed: "+merged.length());
    Files.writeString(out.resolve("library.json"),normal+"\n");
    Files.writeString(out.resolve("library-with-defaults.json"),merged+"\n");
    for(var f:forms.values())Files.writeString(out.resolve("forms/"+f.getId()+".json"),Json.INSTANCE.encode(f)+"\n");
    Files.writeString(out.resolve("building.json"),Json.INSTANCE.encode(buildings.values().iterator().next())+"\n");
    var report=new LinkedHashMap<String,Object>();
    report.put("measurement","actual server Json.encode; Geometry.validate; Module.validate; real Library.seed merged before measuring");
    report.put("storageLimitCharacters",180000);report.put("targetCharacters",170000);
    report.put("formsCharacters",Json.INSTANCE.encode(forms).length());report.put("modulesCharacters",Json.INSTANCE.encode(modules).length());report.put("buildingsCharacters",Json.INSTANCE.encode(buildings).length());
    report.put("standaloneNormalizedCharacters",normal.length());report.put("defaultSeedCharacters",seedCharacters);report.put("includingDefaultSeedCharacters",merged.length());report.put("remainingCharactersWithDefaults",180000-merged.length());report.put("fitsStorageLimit",true);
    Files.writeString(out.resolve("storage-validation.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report)+"\n");
    System.out.println(new Gson().toJson(report));
  }
}
