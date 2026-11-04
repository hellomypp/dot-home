package kr.dothome;

public enum PetSpecies {
    DOG("FLOOR", "dog"), CAT("FLOOR", "cat"), BIRD("PERCH", "perch"),
    SMALL_MAMMAL("SMALL_HABITAT", "small-habitat"), REPTILE("TERRARIUM", "terrarium"),
    AMPHIBIAN("TERRARIUM", "terrarium"), FISH("TANK", "tank"),
    INVERTEBRATE("TERRARIUM", "terrarium"), OTHER("SMALL_HABITAT", "small-habitat");

    final String habitat;
    final String appearanceId;

    PetSpecies(String habitat,String appearanceId){
        this.habitat=habitat;this.appearanceId=appearanceId;
    }

    static PetSpecies resolve(PetSpecies group,String kind){
        if(kind==null&&group!=null)return group;
        var legacy=switch(kind==null?"":kind){
            case "cat" -> CAT;
            case "dog" -> DOG;
            default -> throw ApiException.invalid("반려동물 종류를 선택해 주세요.");
        };
        if(group!=null&&group!=legacy)throw ApiException.invalid("반려동물 종류가 일치하지 않아요.");
        return legacy;
    }

    String legacyKind(){
        return habitat.equals("FLOOR")?appearanceId:"other";
    }
}
