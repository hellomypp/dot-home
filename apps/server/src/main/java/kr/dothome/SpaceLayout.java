package kr.dothome;

import jakarta.validation.constraints.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

record FurniturePosition(@NotNull @Min(0) @Max(448) Integer x,@NotNull @Min(0) @Max(320) Integer y) {}

final class SpaceLayout {
    private static final int FOOT_HALF_WIDTH=6;
    private static final int FOOT_HEIGHT=8;
    private static final int PATH_STEP=4;
    private static final int GRID=Spaces.DATA.get("layoutGrid").asInt();
    private static final Set<String> MOVABLE=new HashSet<>();
    static {for(var id:Spaces.DATA.get("movable"))MOVABLE.add(id.asText());}

    record Point(int x,int y) {
        static Point read(JsonNode array){return new Point(array.get(0).asInt(),array.get(1).asInt());}
        Point translate(int dx,int dy){return new Point(x+dx,y+dy);}
    }
    record Box(int x,int y,int width,int height) {
        static Box read(JsonNode array){return new Box(array.get(0).asInt(),array.get(1).asInt(),array.get(2).asInt(),array.get(3).asInt());}
        int right(){return x+width;}
        int bottom(){return y+height;}
        Box translate(int dx,int dy){return new Box(x+dx,y+dy,width,height);}
        boolean contains(Box b){return b.x>=x && b.y>=y && b.right()<=right() && b.bottom()<=bottom();}
        boolean overlaps(Box b){return x<b.right() && right()>b.x && y<b.bottom() && bottom()>b.y;}
    }
    record Item(String id,Point origin,Box collision,Point anchor) {
        Item at(FurniturePosition position){
            if(position==null)return this;
            int dx=position.x()-origin.x,dy=position.y()-origin.y;
            return new Item(id,new Point(position.x(),position.y()),
                collision==null?null:collision.translate(dx,dy),anchor==null?null:anchor.translate(dx,dy));
        }
    }
    record Room(Box floor,List<Item> items,List<Point> spawns,List<Box> tankSites) {
        boolean walkable(double x,double y){
            if(!Double.isFinite(x)||!Double.isFinite(y))return false;
            double left=x-FOOT_HALF_WIDTH,right=x+FOOT_HALF_WIDTH,top=y-FOOT_HEIGHT;
            if(left<floor.x || right>floor.right() || top<floor.y || y>floor.bottom())return false;
            for(var item:items){
                var b=item.collision;
                if(b!=null && left<b.right() && right>b.x && top<b.bottom() && y>b.y)return false;
            }
            return true;
        }

        void checkPaths(){
            int stride=floor.width+1;
            int size=stride*(floor.height+1);
            var seen=new boolean[size];
            var queue=new int[size];
            int count=1;
            var spawn=aligned(spawns.getFirst());
            if(spawn==null)throw blocked();
            int start=(spawn.y-floor.y)*stride+spawn.x-floor.x;
            seen[start]=true;
            queue[0]=start;
            for(int i=0;i<count;i++){
                int x=queue[i]%stride,y=queue[i]/stride;
                for(var direction:DIRECTIONS){
                    int nextX=x+direction.x,nextY=y+direction.y;
                    if(nextX<0 || nextX>=stride || nextY<0 || nextY>floor.height)continue;
                    int next=nextY*stride+nextX;
                    if(!seen[next] && walkable(floor.x+nextX,floor.y+nextY)){
                        seen[next]=true;
                        queue[count++]=next;
                    }
                }
            }
            for(var point:spawns)if(!reached(point,seen,stride))throw blocked();
            for(var item:items)if(item.anchor!=null && !reached(item.anchor,seen,stride))throw blocked();
        }

        private boolean reached(Point point,boolean[] seen,int stride){
            var target=aligned(point);
            return target!=null && seen[(target.y-floor.y)*stride+target.x-floor.x];
        }

        private Point aligned(Point point){
            if(!walkable(point.x,point.y))return null;
            int x=Math.floorDiv(point.x+2,PATH_STEP)*PATH_STEP;
            int y=Math.floorDiv(point.y+2,PATH_STEP)*PATH_STEP;
            if(walkable(x,y))return new Point(x,y);
            // Use the same nearest-grid order as the client around an interaction anchor.
            for(int offset=-PATH_STEP;offset<=PATH_STEP;offset+=PATH_STEP){
                for(var candidate:List.of(new Point(x+offset,y-PATH_STEP),new Point(x+offset,y+PATH_STEP),
                    new Point(x-PATH_STEP,y+offset),new Point(x+PATH_STEP,y+offset)))
                    if(walkable(candidate.x,candidate.y))return candidate;
            }
            return null;
        }
    }

    private static final List<Point> DIRECTIONS=List.of(
        new Point(0,-PATH_STEP),new Point(0,PATH_STEP),new Point(-PATH_STEP,0),new Point(PATH_STEP,0));

    static Room room(SpaceView design){
        var map=Spaces.map(design.presetId());
        var items=new ArrayList<Item>();
        for(var node:map.get("objects")){
            var id=node.get("id").asText();
            var point=new Point(node.get("x").asInt(),node.get("y").asInt());
            var collision=node.path("collision");
            var anchor=node.path("interactionAnchor");
            items.add(new Item(id,point,collision.isArray()?Box.read(collision):null,anchor.isArray()?Point.read(anchor):null)
                .at(design.positions().get(id)));
        }
        var spawns=new ArrayList<Point>();
        for(var spawn:map.get("spawns"))spawns.add(Point.read(spawn));
        var crop=Point.read(map.get("previewCrop"));
        var tankGrid=Spaces.DATA.get("tankGrid");
        int columns=tankGrid.get("columns").asInt(),cell=tankGrid.get("cellSize").asInt();
        var tankSites=new ArrayList<Box>();
        for(var slot:map.get("tankSlots")){
            int i=slot.asInt();
            int x=crop.x+(i%columns)*cell+cell/2;
            int y=crop.y+(i/columns+1)*cell-tankGrid.get("baseInset").asInt();
            tankSites.add(new Box(x-14,y-20,28,20));
        }
        return new Room(Box.read(map.get("walkBounds")),items,spawns,tankSites);
    }

    static void validate(SpaceView design){
        if(design.positions().isEmpty())return;
        var base=room(new SpaceView(design.presetId(),design.version(),design.variants()));
        for(var entry:design.positions().entrySet()){
            var item=base.items.stream().filter(it->it.id.equals(entry.getKey())).findFirst().orElse(null);
            var target=entry.getValue();
            if(item==null || item.collision==null || !MOVABLE.contains(item.id) || target==null
                || target.x()==null || target.y()==null || target.x()<0 || target.x()>448 || target.y()<0 || target.y()>320
                || (target.x()-item.origin.x)%GRID!=0 || (target.y()-item.origin.y)%GRID!=0)
                throw ApiException.invalid("옮길 수 있는 가구와 격자 위치를 확인해 주세요.");
        }
        var updated=room(design);
        for(var item:updated.items){
            if(!design.positions().containsKey(item.id))continue;
            var footprint=item.collision;
            if(!updated.floor.contains(footprint))throw ApiException.invalid("가구를 바닥 안에 놓아 주세요.");
            if(updated.tankSites.stream().anyMatch(footprint::overlaps))
                throw ApiException.invalid("수조를 놓는 공간은 비워 주세요.");
            if(updated.items.stream().anyMatch(other->other!=item && other.collision!=null && footprint.overlaps(other.collision)))
                throw ApiException.invalid("다른 가구와 겹치지 않는 자리에 놓아 주세요.");
        }
        updated.checkPaths();
    }

    private static ApiException blocked(){return ApiException.invalid("입구나 주요 가구로 가는 길이 막혀 있어요.");}
}
