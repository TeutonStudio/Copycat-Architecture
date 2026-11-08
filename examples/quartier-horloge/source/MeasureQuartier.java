import com.google.gson.*;
import de.teutonstudio.copycatarchitect.domain.*;
import java.nio.file.*;
import java.util.*;

/** The server counts its full Gson representation, including default fields. */
public class MeasureQuartier {
  public static void main(String[] args)throws Exception{
    Gson g=new Gson();
    JsonObject input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
    Map<String,Form> forms=new LinkedHashMap<>();
    for(var e:input.getAsJsonObject("forms").entrySet())
      forms.put(e.getKey(),Geometry.INSTANCE.validate(g.fromJson(e.getValue(),Form.class)));
    Map<String,de.teutonstudio.copycatarchitect.domain.Module> modules=new LinkedHashMap<>();
    for(var e:input.getAsJsonObject("modules").entrySet())
      modules.put(e.getKey(),g.fromJson(e.getValue(),de.teutonstudio.copycatarchitect.domain.Module.class));
    Map<String,Building> buildings=new LinkedHashMap<>();
    for(var e:input.getAsJsonObject("buildings").entrySet())
      buildings.put(e.getKey(),g.fromJson(e.getValue(),Building.class));
    System.out.println(g.toJson(Map.of("forms",g.toJson(forms).length(),"modules",g.toJson(modules).length(),"buildings",g.toJson(buildings).length(),"total",g.toJson(Map.of("forms",forms,"modules",modules,"buildings",buildings)).length(),"limit",180000)));
  }
}
