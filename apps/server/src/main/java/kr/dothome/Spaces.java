package kr.dothome;

import java.util.*;
import jakarta.validation.Valid;
import tools.jackson.databind.*;
import tools.jackson.core.type.TypeReference;
import org.springframework.core.io.ClassPathResource;
import jakarta.validation.constraints.*;

record SpaceView(String presetId,long version,Map<String,String> variants,Map<String,FurniturePosition> positions) {
    SpaceView{positions=positions==null?Map.of():Map.copyOf(positions);}
    SpaceView(String presetId,long version,Map<String,String> variants){this(presetId,version,variants,Map.of());}
}
record SpaceInput(@NotBlank String presetId,@PositiveOrZero long version,@NotNull Map<String,String> variants,
    @Size(max=8) Map<String,@NotNull @Valid FurniturePosition> positions) {
    SpaceInput(String presetId,long version,Map<String,String> variants){this(presetId,version,variants,null);}
}
final class Spaces {
    static final ObjectMapper JSON=new ObjectMapper();
    static final JsonNode DATA;
    static {try(var in=new ClassPathResource("spaces/space-catalog.json").getInputStream()){DATA=JSON.readTree(in);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static String mapId(String preset){var alias=DATA.get("aliases").get(preset);return alias==null?preset:alias.asText();}
    static void validate(String preset,Map<String,String> variants){
        if(preset==null||!DATA.get("maps").has(mapId(preset)))throw ApiException.invalid("지원하지 않는 집 프리셋이에요.");
        if(variants==null||variants.size()>3)throw ApiException.invalid("가구 외형을 확인해 주세요.");
        for(var entry:variants.entrySet()){
            var allowed=DATA.get("variants").get(entry.getKey());boolean found=false;
            if(allowed!=null)for(var value:allowed)if(value.asText().equals(entry.getValue()))found=true;
            if(!found)throw ApiException.invalid("지원하지 않는 가구 외형이에요.");
        }
    }
    static Map<String,String> overrides(Household house){try{return JSON.readValue(house.objectVariants,Map.class);}catch(Exception e){throw new IllegalStateException("Invalid stored space design");}}
    static Map<String,FurniturePosition> positions(Household house){
        return JSON.readValue(house.objectPositions,new TypeReference<Map<String,FurniturePosition>>(){});
    }
    static SpaceView view(Household h){return new SpaceView(h.presetId,h.designVersion,Map.copyOf(overrides(h)),positions(h));}
    static boolean sameMap(String a,String b){return mapId(a).equals(mapId(b));}
    static SpaceView proposed(Household house,SpaceInput input){
        validate(input.presetId(),input.variants());
        var placements=input.positions()==null
            ? sameMap(house.presetId,input.presetId())?positions(house):Map.<String,FurniturePosition>of()
            : input.positions();
        if(placements.size()>DATA.get("movable").size() || placements.values().stream().anyMatch(Objects::isNull))
            throw ApiException.invalid("가구의 위치를 확인해 주세요.");
        var design=new SpaceView(input.presetId(),input.version(),Map.copyOf(input.variants()),placements);
        SpaceLayout.validate(design);
        return design;
    }
    static JsonNode map(String preset){return DATA.get("maps").get(mapId(preset));}
    static boolean tankSlot(String preset,int slot){
        for(var allowed:map(preset).path("tankSlots"))if(allowed.asInt()==slot)return true;
        return false;
    }
    static boolean walkable(double x,double y,String preset){
        return map(preset)!=null && SpaceLayout.room(new SpaceView(preset,0,Map.of())).walkable(x,y);
    }
    static double[] position(Member member,SpaceLayout.Room room){
        if(!"legacy".equals(member.avatarSpace)&&room.walkable(member.x,member.y))return new double[]{member.x,member.y};
        var spawn=room.spawns().getFirst();
        return new double[]{spawn.x(),spawn.y()};
    }
    static double[] spawn(String preset,long index){
        var spawns=map(preset).get("spawns");
        var point=spawns.get((int)(index%spawns.size()));
        return new double[]{point.get(0).asDouble(),point.get(1).asDouble()};
    }
}
