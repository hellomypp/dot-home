package kr.dothome;

import java.util.*;
import tools.jackson.databind.*;
import org.springframework.core.io.ClassPathResource;
import jakarta.validation.constraints.*;

record SpaceView(String presetId,long version,Map<String,String> variants) {}
record SpaceInput(@NotBlank String presetId,@PositiveOrZero long version,@NotNull Map<String,String> variants) {}
final class Spaces {
    static final ObjectMapper JSON=new ObjectMapper();
    static final JsonNode DATA;
    static {try(var in=new ClassPathResource("spaces/space-catalog.json").getInputStream()){DATA=JSON.readTree(in);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static String mapId(String preset){return "classic_home".equals(preset)?"garden_home":preset;}
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
    static SpaceView view(Household h){return new SpaceView(h.presetId,h.designVersion,Map.copyOf(overrides(h)));}
    static JsonNode map(String preset){return DATA.get("maps").get(mapId(preset));}
    static boolean walkable(double x,double y,String preset){
        if(!Double.isFinite(x)||!Double.isFinite(y))return false;
        var map=map(preset);if(map==null)return false;
        for(double dx:new double[]{-6,5})for(double dy:new double[]{-8,-1}){
            int tx=(int)Math.floor((x+dx)/16),ty=(int)Math.floor((y+dy)/16);
            if(tx<0||ty<0||tx>=28||ty>=20||map.get("collision").get(ty*28+tx).asInt()==1)return false;
        }
        return true;
    }
    static double[] position(Member member,String preset){return "legacy".equals(member.avatarSpace)||!walkable(member.x,member.y,preset)?new double[]{224,272}:new double[]{member.x,member.y};}
}
