import {mountNativeWorld} from './nativeWorld';
import Phaser from 'phaser';
import { makeHouse, makeOtherPlace, makeFurniture, makeAvatar, makeCat, makeDog, type Direction } from './art';
import { findPath, walkable, nearestWalkable, HOME_SOLIDS, OTHER_SOLIDS, type Point } from './navigation';
import type { WorldView, WorldEvent, WorldHandle, WorldMember } from './types';

interface Actor { sprite:Phaser.GameObjects.Image;label:Phaser.GameObjects.Text;bubble:Phaser.GameObjects.Text;path:Point[];member:WorldMember;direction:Direction;frame:number;lastManual:number;lastAi:number;remote:string;after?:()=>void }
const furniture=[{kind:'kitchen',x:332,y:100,depth:166},{kind:'fridge',x:538,y:80,depth:165,target:'fridge',label:'냉장고 열기',point:{x:560,y:185}},{kind:'sofa',x:65,y:184,depth:263,target:'sofa',label:'소파 곁에서 잠깐 쉬어요.',point:{x:140,y:280}},{kind:'table',x:353,y:195,depth:277,target:'table',label:'같이 먹고 싶은 음식이 있나요?',point:{x:400,y:300}},{kind:'bed',x:505,y:224,depth:355},{kind:'plant',x:48,y:298,depth:365},{kind:'shelf',x:237,y:108,depth:190},{kind:'cabinet',x:232,y:308,depth:362},{kind:'cabinet',x:102,y:286,depth:336},{kind:'lamp',x:204,y:200,depth:267}];
const directions:Direction[]=['down','up','left','right'];
const seed=(s:string)=>[...s].reduce((v,c)=>(v*31+c.charCodeAt(0))>>>0,7);
function typing(){const el=document.activeElement;return !!el?.matches('input,textarea,select,[contenteditable=true]')||!!document.querySelector('dialog[open]');}

class HomeScene extends Phaser.Scene {
  noteMarkers:Phaser.GameObjects.GameObject[]=[];view:WorldView;emit:(e:WorldEvent)=>void;actors=new Map<string,Actor>();petActors:{sprite:Phaser.GameObjects.Image;path:Point[];index:number;texturePrefix:string;name:Phaser.GameObjects.Text;lastAi:number}[]=[];keys=new Set<string>();lastInput=0;artVersion=0;
  constructor(view:WorldView,emit:(e:WorldEvent)=>void){super('home');this.view=view;this.emit=emit;}
  create(){
    this.drawRoom();this.syncActors();this.resize();
    this.input.on('pointerdown',(pointer:Phaser.Input.Pointer)=>{if(typing()||!this.canControl())return;const actor=this.actors.get(this.view.ownMemberId);if(!actor)return;const point=pointer.positionToCamera(this.cameras.main) as Phaser.Math.Vector2;this.move(actor,nearestWalkable(point,this.solids()),true);});
    const down=(e:KeyboardEvent)=>{if(typing()||!this.canControl())return;if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','w','a','s','d'].includes(e.key)){e.preventDefault();this.keys.add(e.key);}};
    const up=(e:KeyboardEvent)=>this.keys.delete(e.key);
    const blur=()=>this.keys.clear();
    window.addEventListener('keydown',down);window.addEventListener('keyup',up);window.addEventListener('blur',blur);
    const visible=()=>{this.keys.clear();if(document.hidden)this.game.loop.sleep();else{this.game.loop.wake();this.emit({type:'visibility-resume'});}};
    document.addEventListener('visibilitychange',visible);
    this.scale.on('resize',this.resize,this);
    this.events.once('shutdown',()=>{window.removeEventListener('keydown',down);window.removeEventListener('keyup',up);window.removeEventListener('blur',blur);document.removeEventListener('visibilitychange',visible);this.scale.off('resize',this.resize,this);});
    this.emit({type:'ready'});
  }
  solids(){return this.view.place==='home'?HOME_SOLIDS:OTHER_SOLIDS;}
  canControl(){return (!this.view.observedMemberId||this.view.observedMemberId===this.view.ownMemberId)&&this.view.members.some(m=>m.id===this.view.ownMemberId&&m.place===this.view.place);}
  texture(key:string,canvas:HTMLCanvasElement){if(this.textures.exists(key))return key;this.textures.addCanvas(key,canvas);return key;}
  drawRoom(){
    this.children.removeAll(true);this.actors.clear();this.petActors=[];
    for(const key of this.textures.getTextureKeys())if(key.startsWith('world-'))this.textures.remove(key);
    this.artVersion++;
    this.add.image(0,0,this.texture('world-room',this.view.place==='home'?makeHouse(this.view.palette||'warm'):makeOtherPlace(this.view.place,this.view.palette||'warm'))).setOrigin(0).setDepth(-1000);
    const objects=this.view.place==='home'?furniture:[{kind:'table',x:108,y:164,depth:248},{kind:'table',x:410,y:164,depth:248},{kind:'plant',x:62,y:130,depth:195},{kind:'shelf',x:548,y:105,depth:190}];
    for(const item of objects){const image=this.add.image(item.x,item.y,this.texture(`world-furniture-${item.kind}`,makeFurniture(item.kind))).setOrigin(0).setDepth(item.depth);if('target' in item&&item.target){image.setInteractive({useHandCursor:true});image.on('pointerdown',(_p:Phaser.Input.Pointer,_x:number,_y:number,event:Phaser.Types.Input.EventData)=>{event.stopPropagation();const target=item.target as 'fridge'|'sofa'|'table';const act=()=>this.emit({type:'interact',target,label:item.label!});const own=this.actors.get(this.view.ownMemberId);if(this.canControl()&&own){this.move(own,item.point!,true);own.after=act;}else act();});}}
    if(this.view.palette==='dusk')this.add.rectangle(320,224,640,448,0x47597a,.13).setDepth(9000);
    if(this.view.place!=='home')this.add.text(320,94,`${{office:'조용한 작업실',school:'작은 교실',outside:'집 앞 작은 정원'}[this.view.place]} · 기본 공간 시안`,{fontFamily:'sans-serif',fontSize:'12px',color:'#66735b',backgroundColor:'#f1eddb',padding:{x:12,y:6}}).setOrigin(.5).setDepth(9100);
  }
  syncNotes(){for(const marker of this.noteMarkers)marker.destroy();this.noteMarkers=[];if(this.view.place!=='home')return;const notes=this.view.objectNotes?.filter(n=>n.objectId==='fridge')||[];if(!notes.length)return;const marker=this.add.rectangle(560,121,16,16,0xf0da86).setStrokeStyle(2,0xb69b5b).setDepth(200).setInteractive({hitArea:new Phaser.Geom.Rectangle(-14,-14,44,44),hitAreaCallback:Phaser.Geom.Rectangle.Contains,useHandCursor:true});marker.on('pointerdown',(_p:unknown,_x:unknown,_y:unknown,event:Phaser.Types.Input.EventData)=>{event.stopPropagation();this.emit({type:'interact',target:'fridge',label:'냉장고 쪽지'});});this.noteMarkers.push(marker);if(notes.length>1)this.noteMarkers.push(this.add.text(571,112,String(notes.length),{fontSize:'11px',color:'#514b35',backgroundColor:'#fff7d9'}).setDepth(201));}
  syncActors(){this.syncNotes();
    const members=this.view.members.filter(m=>m.place===this.view.place);
    for(const [id,actor]of this.actors)if(!members.some(m=>m.id===id)){actor.sprite.destroy();actor.label.destroy();actor.bubble.destroy();this.actors.delete(id);}
    for(const member of members){let actor=this.actors.get(member.id);const prefix=`world-${member.id}-${this.artVersion}-${JSON.stringify(member.appearance)}`;
      if(!this.textures.exists(`${prefix}-down-0`))for(const d of directions)for(let f=0;f<4;f++)this.texture(`${prefix}-${d}-${f}`,makeAvatar(member,d,f));
      if(!actor){const initial=nearestWalkable({x:member.x??(280+seed(member.id)%160),y:member.y??(290+seed(member.id)%60)},this.solids());actor={member,sprite:this.add.image(initial.x,initial.y,`${prefix}-down-0`).setOrigin(.5,1),label:this.add.text(0,0,member.name,{fontFamily:'sans-serif',fontSize:'10px',color:'#576348',backgroundColor:'#eee9d7',padding:{x:5,y:2}}).setOrigin(.5,0).setDepth(9500),bubble:this.add.text(0,0,'',{fontFamily:'sans-serif',fontSize:'11px',color:'#5f604b',backgroundColor:'#fff8e5',padding:{x:9,y:6},wordWrap:{width:125}}).setOrigin(.5,1).setDepth(9600),path:[],direction:'down',frame:0,lastManual:0,lastAi:-1,remote:'',};this.actors.set(member.id,actor);}
      actor.member=member;actor.label.setText(member.name+(member.id===this.view.ownMemberId?' · 나':''));actor.bubble.setText(member.status||'').setVisible(!!member.status);
      const remote=`${member.x},${member.y}`;if(actor.remote!==remote&&member.x!==undefined&&member.y!==undefined){actor.remote=remote;this.move(actor,{x:member.x,y:member.y},false);actor.lastManual=Date.now();}
    }
    const petIds=this.view.place==='home'?JSON.stringify(this.view.pets.map(p=>[p.id,p.kind,p.color])):'';
    if(this.registry.get('petIds')!==`${this.artVersion}:${petIds}`){this.registry.set('petIds',`${this.artVersion}:${petIds}`);for(const pet of this.petActors){pet.sprite.destroy();pet.name.destroy();}this.petActors=[];if(this.view.place==='home')this.view.pets.forEach((pet,index)=>{const texturePrefix=`world-pet-${pet.id}-${pet.kind}-${pet.color}`;for(let f=0;f<4;f++)this.texture(`${texturePrefix}-${f}`,pet.kind==='dog'?makeDog(f,pet.color):makeCat(f,pet.color));const point=nearestWalkable({x:270+index*20,y:280});const sprite=this.add.image(point.x,point.y,`${texturePrefix}-0`).setOrigin(.5,1).setInteractive({useHandCursor:true});sprite.on('pointerdown',(_p:unknown,_x:unknown,_y:unknown,event:Phaser.Types.Input.EventData)=>{event.stopPropagation();this.emit({type:'interact',target:'pet',label:this.view.pets.find(p=>p.id===pet.id)?.name||'반려동물'});});this.petActors.push({sprite,path:[],index,texturePrefix,lastAi:-1,name:this.add.text(point.x,point.y+3,pet.name,{fontSize:'9px',fontFamily:'sans-serif',color:'#806e50',backgroundColor:'#eee1c5',padding:{x:4,y:2}}).setOrigin(.5,0).setDepth(9500)});});}
    for(const actor of this.petActors)actor.name.setText(this.view.pets[actor.index].name);
  }
  move(actor:Actor,target:Point,manual:boolean){actor.path=findPath({x:actor.sprite.x,y:actor.sprite.y},target,this.solids());actor.after=undefined;if(manual){actor.lastManual=Date.now();this.emit({type:'move-intent',x:target.x,y:target.y});this.emit({type:'activity',text:'천천히, 가고 싶은 곳으로 걸어가요.'});}}
  resize(){const {width,height}=this.scale;const fit=Math.min(width/640,height/448);const zoom=width<500?Math.max(fit,1):fit;this.cameras.main.setZoom(zoom).setBounds(0,0,640,448);this.cameras.main.centerOn(320,224);}
  setView(next:WorldView){const redraw=next.place!==this.view.place||next.palette!==this.view.palette;this.view=next;if(!this.sys.isActive())return;if(redraw)this.drawRoom();this.syncActors();this.resize();}
  advance(sprite:Phaser.GameObjects.Image,path:Point[],delta:number,speed:number){let remaining=Math.min(delta,50)/1000*speed;while(path.length&&remaining>0){const target=path[0],dx=target.x-sprite.x,dy=target.y-sprite.y,dist=Math.hypot(dx,dy);if(dist<.1){path.shift();continue;}const step=Math.min(dist,remaining),point={x:sprite.x+dx/dist*step,y:sprite.y+dy/dist*step};if(!walkable(point,this.solids())){path.length=0;break;}sprite.setPosition(point.x,point.y);remaining-=step;if(dist<=step+.01)path.shift();}}
  update(time:number,delta:number){
    const stamp=Date.now(),own=this.actors.get(this.view.ownMemberId);
    if(own&&this.keys.size&&this.canControl()&&!typing()){
      const dx=(this.keys.has('ArrowRight')||this.keys.has('d')?1:0)-(this.keys.has('ArrowLeft')||this.keys.has('a')?1:0),dy=(this.keys.has('ArrowDown')||this.keys.has('s')?1:0)-(this.keys.has('ArrowUp')||this.keys.has('w')?1:0);
      if(stamp-this.lastInput>180){const target={x:own.sprite.x+dx*16,y:own.sprite.y+dy*16};if(walkable(target,this.solids()))this.move(own,nearestWalkable(target,this.solids()),true);this.lastInput=stamp;}
    }
    for(const actor of this.actors.values()){
      const cycle=Math.floor(stamp/14000),hash=seed(actor.member.id);
      if(!this.view.reducedMotion&&stamp-actor.lastManual>12000&&actor.lastAi!==cycle){actor.lastAi=cycle;const points=[{x:224,y:281},{x:316,y:195},{x:464,y:306},{x:340,y:370},{x:102,y:180}];actor.path=findPath({x:actor.sprite.x,y:actor.sprite.y},points[(cycle+hash)%points.length],this.solids());if(actor.member.id===this.view.ownMemberId)this.emit({type:'activity',text:['창가의 햇살을 바라봐요.','집 안을 천천히 걷는 중이에요.','잠깐, 나만의 시간을 보내요.'][(cycle+hash)%3]});}
      const before={x:actor.sprite.x,y:actor.sprite.y};this.advance(actor.sprite,actor.path,delta,58);const dx=actor.sprite.x-before.x,dy=actor.sprite.y-before.y;
      if(Math.abs(dx)+Math.abs(dy)>.01){actor.direction=Math.abs(dx)>Math.abs(dy)?dx>0?'right':'left':dy>0?'down':'up';actor.frame=Math.floor(time/150)%4;}else{actor.frame=0;if(actor.after){actor.after();actor.after=undefined;}}
      actor.sprite.setTexture(`world-${actor.member.id}-${this.artVersion}-${JSON.stringify(actor.member.appearance)}-${actor.direction}-${actor.frame}`).setDepth(actor.sprite.y);
      actor.label.setPosition(Math.round(actor.sprite.x),Math.round(actor.sprite.y+3));actor.bubble.setPosition(Math.round(actor.sprite.x),Math.round(actor.sprite.y-52));
    }
    for(const pet of this.petActors){const cycle=Math.floor(stamp/17000);if(!this.view.reducedMotion&&pet.lastAi!==cycle){pet.lastAi=cycle;const points=[{x:270,y:270},{x:332,y:336},{x:460,y:370}];pet.path=findPath({x:pet.sprite.x,y:pet.sprite.y},points[(cycle+pet.index)%3]);}this.advance(pet.sprite,pet.path,delta,32);pet.sprite.setDepth(pet.sprite.y).setTexture(`${pet.texturePrefix}-${pet.path.length?Math.floor(time/180)%4:0}`);pet.name.setPosition(pet.sprite.x,pet.sprite.y+3);}
    const bounds=this.cameras.main.worldView;for(const actor of this.actors.values()){const half=actor.bubble.width/2;actor.bubble.x=Phaser.Math.Clamp(actor.sprite.x,bounds.x+half+5,bounds.right-half-5);}
    const target=this.actors.get(this.view.observedMemberId||this.view.ownMemberId);if(this.scale.width<500&&target)this.cameras.main.centerOn(target.sprite.x,target.sprite.y-80);
  }
}

function mountLegacyWorld(parent:HTMLElement,initial:WorldView,onEvent:(event:WorldEvent)=>void):WorldHandle {
  const scene=new HomeScene(initial,onEvent);
  const game=new Phaser.Game({type:Phaser.AUTO,parent,width:parent.clientWidth||640,height:parent.clientHeight||448,backgroundColor:'#ede9dc',pixelArt:true,roundPixels:true,antialias:false,scene,scale:{mode:Phaser.Scale.RESIZE},fps:{target:30},audio:{noAudio:true},render:{preserveDrawingBuffer:true}});
  const observer=new ResizeObserver(()=>{if(game.isBooted)game.scale.resize(parent.clientWidth,parent.clientHeight);});observer.observe(parent);
  return {update:view=>scene.setView(view),destroy:()=>{observer.disconnect();game.destroy(true);}};
}

export function mountWorld(parent:HTMLElement,initial:WorldView,onEvent:(event:WorldEvent)=>void):WorldHandle{
 let home=initial.place==='home',current=home?mountNativeWorld(parent,initial,onEvent):mountLegacyWorld(parent,initial,onEvent);
 return {update:view=>{const next=view.place==='home';if(home!==next){current.destroy();home=next;current=home?mountNativeWorld(parent,view,onEvent):mountLegacyWorld(parent,view,onEvent);}else current.update(view);},destroy:()=>current.destroy()};
}
