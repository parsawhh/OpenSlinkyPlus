// @slinky {"id":"auto_bed_def","name":"AutoBedDef","description":"Myau AutoBedDef - faithful behavior port (script-adapted placement/aim)","category":"world",
// "settings":[
//   {"key":"range","label":"Range","type":"number","default":5.0,"min":3.0,"max":7.0,"step":0.1},
//   {"key":"place_delay","label":"Place Delay","type":"number","default":80,"min":0,"max":300,"step":1},
//   {"key":"speed","label":"Speed","type":"number","default":100,"min":30,"max":100,"step":1},
//   {"key":"rot_tolerance","label":"Rot Tolerance","type":"number","default":35,"min":5,"max":180,"step":1},
//   {"key":"swing","label":"Swing","type":"boolean","default":true},
//   {"key":"show_progress","label":"Show Progress","type":"boolean","default":true},
//   {"key":"move_fix","label":"Move Fix","type":"choice","default":1,"options":["NONE","SILENT"]}
// ],
// "listens":["tick","rotation","render_ortho"]}

var ROT_PRIORITY=6, BED_SEARCH_RADIUS=8;
var serverYaw=0, serverPitch=0, aimYaw=0, aimPitch=0, lastSteppedYaw=0, lastSteppedPitch=0;
var targetBlock=null, targetFacing=null, targetHitVec=null;
var lastPlaceTime=0, lastSlot=-1, progress=0;
var lockedBedFoot=null, lockedBedFacing=null;

function onEnable(api){ var p=api.player(); if(p){ serverYaw=p.yaw; serverPitch=p.pitch; aimYaw=serverYaw; aimPitch=serverPitch; lastSteppedYaw=serverYaw; lastSteppedPitch=serverPitch; lastSlot=api.heldSlot?api.heldSlot():-1; } progress=0; resetTarget(); lockedBedFoot=null; lockedBedFacing=null; }
function onDisable(api){ if(lastSlot>=0 && api.selectSlot){} progress=0; resetTarget(); lockedBedFoot=null; lockedBedFacing=null; }
function resetTarget(){ targetBlock=null; targetFacing=null; targetHitVec=null; }

function isBed(api,x,y,z){ var b=api.blockAt(x,y,z); return b && b.id===26; }
function isBedFoot(api,pos){ if(!isBed(api,pos.x,pos.y,pos.z)) return false; var b=api.blockAt(pos.x,pos.y,pos.z); return (b.meta&8)===0; }
function isValidBed(api,pos){ if(!pos) return false; return isBed(api,pos.x,pos.y,pos.z); }
function getBedFacing(api,pos){ var b=api.blockAt(pos.x,pos.y,pos.z); if(!b) return null; switch(b.meta&3){case 0:return 'SOUTH';case 1:return 'WEST';case 2:return 'NORTH';case 3:return 'EAST';} return null; }
function leftOf(f){ switch(f){case 'NORTH':return 'WEST';case 'WEST':return 'SOUTH';case 'SOUTH':return 'EAST';case 'EAST':return 'NORTH';} return 'NORTH'; }
function opposite(f){ return f==='NORTH'?'SOUTH':f==='SOUTH'?'NORTH':f==='WEST'?'EAST':f==='EAST'?'WEST':f; }
function offset(pos,f){ if(f==='NORTH') return {x:pos.x,y:pos.y,z:pos.z-1}; if(f==='SOUTH') return {x:pos.x,y:pos.y,z:pos.z+1}; if(f==='WEST') return {x:pos.x-1,y:pos.y,z:pos.z}; if(f==='EAST') return {x:pos.x+1,y:pos.y,z:pos.z}; if(f==='UP') return {x:pos.x,y:pos.y+1,z:pos.z}; if(f==='DOWN') return {x:pos.x,y:pos.y-1,z:pos.z}; return {x:pos.x,y:pos.y,z:pos.z}; }

function isReplaceable(api,pos){ var b=api.blockAt(pos.x,pos.y,pos.z); if(!b) return true; return !!b.air || b.id===8||b.id===9||b.id===10||b.id===11||b.id===51; }

function findNearestBed(api){
  var p=api.player(); if(!p) return;
  var ox=Math.floor(p.x), oy=Math.floor(p.y), oz=Math.floor(p.z);
  var best=1e18;
  for(var dx=-BED_SEARCH_RADIUS;dx<=BED_SEARCH_RADIUS;dx++) for(var dy=-2;dy<=2;dy++) for(var dz=-BED_SEARCH_RADIUS;dz<=BED_SEARCH_RADIUS;dz++){
    var pos={x:ox+dx,y:oy+dy,z:oz+dz}; if(!isBedFoot(api,pos)) continue;
    var facing=getBedFacing(api,pos); if(!facing) continue;
    var d=(p.x-(pos.x+0.5))*(p.x-(pos.x+0.5))+(p.y-(pos.y+0.5))*(p.y-(pos.y+0.5))+(p.z-(pos.z+0.5))*(p.z-(pos.z+0.5));
    if(d<best){ best=d; lockedBedFoot=pos; lockedBedFacing=facing; }
  }
}

function getDefensePositions(){
  var list=[]; if(!lockedBedFoot||!lockedBedFacing) return list;
  var foot=lockedBedFoot, head=offset(foot,lockedBedFacing);
  var fwd=lockedBedFacing, back=opposite(fwd), left=leftOf(fwd), right=opposite(left);
  list.push(offset(foot,left)); list.push(offset(foot,right));
  list.push(offset(head,left)); list.push(offset(head,right));
  list.push(offset(foot,back)); list.push(offset(head,fwd));
  list.push({x:foot.x,y:foot.y+1,z:foot.z}); list.push({x:head.x,y:head.y+1,z:head.z});
  return list;
}

function findNextDefensePosIndex(api,positions){ for(var i=0;i<positions.length;i++) if(isReplaceable(api,positions[i])) return i; return -1; }

function findWoolSlot(api){ for(var s=0;s<9;s++){ var h=api.hotbar(s); if(h && h.id===35 && !h.empty) return s; } return -1; }

function wrap(a){ return ((a+540)%360)-180; }
function clamp(v,lo,hi){ return v<lo?lo:(v>hi?hi:v); }

function computeRotations(dx,dy,dz){ var hd=Math.sqrt(dx*dx+dz*dz); var yaw=wrap(Math.atan2(dz,dx)*180/Math.PI - 90.0); var pitch=-Math.atan2(dy,hd)*180/Math.PI; return [yaw,pitch]; }

function withinTol(useYaw,usePitch,tYaw,tPitch,api){ return Math.abs(wrap(useYaw-tYaw))<=api.num('rot_tolerance') && Math.abs(wrap(usePitch-tPitch))<=api.num('rot_tolerance'); }

function onTick(api){
  if(!api.isEnabled || !api.isEnabled()) return;
  var p=api.player(); if(!p||!p.inWorld) return;
  serverYaw=p.yaw; serverPitch=p.pitch;
  updateProgress(api);
  if(!isValidBed(api,lockedBedFoot)){ lockedBedFoot=null; lockedBedFacing=null; findNearestBed(api); }
  if(!lockedBedFoot) return;
  var wool=findWoolSlot(api); if(wool<0){ resetTarget(); return; }
  if(api.heldSlot && api.heldSlot()!==wool) api.selectSlot(wool);
  var positions=getDefensePositions(); var idx=findNextDefensePosIndex(api,positions);
  if(idx<0){ resetTarget(); return; }
  var next=positions[idx]; var isRoof=idx>=6;
  computePlacement(api,next,isRoof);
  if(!targetBlock) return;
  // smooth rotation stepping
  var yawDiff=wrap(aimYaw-serverYaw), pitchDiff=aimPitch-serverPitch;
  var maxTurn=api.num('speed');
  lastSteppedYaw = serverYaw + clamp(yawDiff,-maxTurn,maxTurn);
  lastSteppedPitch = clamp(serverPitch + clamp(pitchDiff,-maxTurn,maxTurn), -90, 90);
  api.claimRotation(ROT_PRIORITY);
  api.look(lastSteppedYaw,lastSteppedPitch);

  var useYaw = lastSteppedYaw, usePitch = lastSteppedPitch;
  if(!withinTol(useYaw,usePitch,aimYaw,aimPitch,api)) return;
  var now=api.now(); if(now-lastPlaceTime < api.num('place_delay')) return;
  // attempt place
  if(api.holdingBlock && api.holdingBlock()){
    api.swing();
    if(api.placeBlock) api.placeBlock(targetBlock.x,targetBlock.y,targetBlock.z,targetFacing);
    lastPlaceTime=now; resetTarget();
  }
}

function computePlacement(api,wantPlace,isRoof){
  resetTarget();
  var p=api.player(); var eyes={x:p.x,y:p.y+1.62,z:p.z};
  var reach=api.num('range'); var reachSq=reach*reach;
  var faces=['DOWN','UP','NORTH','SOUTH','WEST','EAST'];
  for(var i=0;i<faces.length;i++){
    var face=faces[i]; var support=offset(wantPlace,face);
    if(isReplaceable(api,support)) continue;
    var sb=api.blockAt(support.x,support.y,support.z);
    if(!isRoof && sb && sb.id===26) continue;
    var dx=support.x+0.5-eyes.x, dy=support.y+0.5-eyes.y, dz=support.z+0.5-eyes.z;
    if(dx*dx+dy*dy+dz*dz > (reach+1.5)*(reach+1.5)) continue;
    var clickFace=opposite(face);
    var offsets=[0.2,0.4,0.5,0.6,0.8];
    var bestDiff=isRoof?-1e9:1e9, bestYaw=NaN,bestPitch=NaN,bestHit=null;
    for(var u=0;u<offsets.length;u++) for(var v=0;v<offsets.length;v++){
      var hp=getFacePoint(support,clickFace,offsets[u],offsets[v]);
      var hdx=hp.x-eyes.x,hdy=hp.y-eyes.y,hdz=hp.z-eyes.z;
      if(hdx*hdx+hdy*hdy+hdz*hdz > reachSq) continue;
      var rot=computeRotations(hdx,hdy,hdz);
      // raytrace approximation: accept if within reach; real raytrace not exposed
      var diff=Math.abs(wrap(rot[0]-serverYaw))+Math.abs(rot[1]-serverPitch);
      var better=isRoof?(diff>bestDiff):(diff<bestDiff);
      if(better){ bestDiff=diff; bestYaw=rot[0]; bestPitch=rot[1]; bestHit=hp; }
    }
    if(bestHit){ aimYaw=bestYaw; aimPitch=bestPitch; targetBlock=support; targetFacing=clickFace; targetHitVec=bestHit; return; }
  }
}

function getFacePoint(block,face,u,v){ var x=block.x,y=block.y,z=block.z;
  switch(face){case 'DOWN':return {x:x+u,y:y,z:z+v};case 'UP':return {x:x+u,y:y+1.0,z:z+v};case 'NORTH':return {x:x+u,y:y+v,z:z};case 'SOUTH':return {x:x+u,y:y+v,z:z+1.0};case 'WEST':return {x:x,y:y+v,z:z+u};case 'EAST':return {x:x+1.0,y:y+v,z:z+u};}
  return {x:x+0.5,y:y+0.5,z:z+0.5}; }

function updateProgress(api){ if(!lockedBedFoot||!lockedBedFacing){ progress=0; return; } var positions=getDefensePositions(); var filled=0; for(var i=0;i<positions.length;i++) if(!isReplaceable(api,positions[i])) filled++; progress=positions.length?filled/positions.length:0; }

function getProgressColor(){ if(progress<=0.33) return 0xFF5555; if(progress<=0.66) return 0xFFFF55; return 0x55FF55; }

function onEvent(api,event){
  if(event.getType()===7 && api.bool('show_progress')){
    var d=api.draw(); if(!d||!d.text) return;
    var sw=854,sh=480; try{ sw=event.getRenderWidth?event.getRenderWidth():sw; sh=event.getRenderHeight?event.getRenderHeight():sh;}catch(e){}
    var txt=Math.round(progress*100)+'%';
    d.text(txt, sw/2-8, sh*2/5, 0xFF000000| (getProgressColor()&0xFFFFFF), true, 9);
  }
}
