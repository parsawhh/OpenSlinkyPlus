// @slinky {"id":"bow_aim","name":"BowAim","description":"CrewX BowAimbot - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"mode","label":"Mode","type":"choice","default":1,"options":["Normal","Silent"]},
//   {"key":"target","label":"Target","type":"choice","default":0,"options":["Distance","Angle","Health"]},
//   {"key":"range","label":"Range","type":"number","default":60,"min":5,"max":128,"step":1},
//   {"key":"fov","label":"FOV","type":"number","default":180,"min":30,"max":360,"step":1},
//   {"key":"min_charge","label":"Min Charge","type":"number","default":15,"min":0,"max":100,"step":1},
//   {"key":"smoothing","label":"Smoothing","type":"number","default":65,"min":0,"max":100,"step":1},
//   {"key":"predict","label":"Predict","type":"number","default":1.0,"min":0,"max":5,"step":0.1},
//   {"key":"through_walls","label":"Through Walls","type":"boolean","default":false},
//   {"key":"teams","label":"Teams","type":"boolean","default":true},
//   {"key":"anti_bot","label":"Anti Bot","type":"boolean","default":true},
//   {"key":"move_fix","label":"Move Fix","type":"boolean","default":true}
// ],
// "listens":["tick","rotation"]}

var target = null;
var silentYaw = NaN, silentPitch = NaN;
var ROTATION_PRIORITY = 2;

function onEnable(api){ reset(); }
function onDisable(api){ reset(); api.clearRotation && api.clearRotation(); }
function reset(){ target=null; silentYaw=NaN; silentPitch=NaN; }

function wrapAngle(a){ a=((a+180)%360); if(a<0)a+=360; a-=180; return a; }
function clamp(v,lo,hi){ return v<lo?lo:(v>hi?hi:v); }
function randf(a,b){ return a + Math.random()*(b-a); }

function getBowPower(api){
  var dur = 0;
  try{ var pr=api.rawPlayer(); if(pr) dur = (pr.call('getItemInUseDuration')|0); }catch(e){}
  var charge = dur/20.0;
  charge = (charge*charge + charge*2.0)/3.0;
  return Math.min(charge, 1.0);
}

function isHoldingDrawnBow(api){
  var h = api.heldItem(); if(!h) return false;
  var cls = (h.class||'')+''; var name=(h.name||'');
  if(h.id!==261 && cls.indexOf('Bow')<0 && name.indexOf('Bow')<0) return false;
  if(!getBowPower(api)) {}
  // using item
  var using=false; try{ var pr=api.rawPlayer(); if(pr) using = !!pr.call('isUsingItem'); }catch(e){}
  if(!using) return false;
  return getBowPower(api) >= (api.num('min_charge')/100.0);
}

function angleToEntity(api, player, ent){
  var dx=ent.x-player.x, dz=ent.z-player.z;
  var dy=(ent.y + (ent.eyeHeight||1.62)) - (player.y + 1.62);
  var horiz=Math.sqrt(dx*dx+dz*dz);
  var yaw=Math.atan2(dz,dx)*180/Math.PI - 90.0;
  var pitch=-Math.atan2(dy,horiz)*180/Math.PI;
  var dyaw=Math.abs(wrapAngle(yaw-player.yaw));
  var dp=Math.abs(pitch-player.pitch);
  return Math.sqrt(dyaw*dyaw+dp*dp);
}

function isValidTarget(api, ent){
  var p=api.player(); if(!p||!ent) return false;
  if(ent.id === p.id) return false;
  if(ent.dead || (ent.health!=null && ent.health<=0)) return false;
  if(ent.invisible) return false;
  if((ent.distance||999) > api.num('range')) return false;
  if(angleToEntity(api,p,ent) > api.num('fov')) return false;
  // through walls: skip raytrace (needs world raytrace) unless false -> we can't reliably check, so be permissive only if on
  // teams/bots via relationship
  if(api.bool('teams') && (ent.flags&4)) return false;
  if(api.bool('anti_bot') && (ent.flags&2)) return false;
  if(ent.flags&1) return false; // friend
  return true;
}

function findTarget(api){
  var ents=api.entities(), cand=[];
  for(var i=0;i<ents.length;i++){ var e=ents[i]; if(!e.player) continue; if(isValidTarget(api,e)) cand.push(e); }
  if(cand.length===0) return null;
  var p=api.player(), mode=api.num('target')|0;
  cand.sort(function(a,b){
    if(mode===1) return angleToEntity(api,p,a)-angleToEntity(api,p,b);
    if(mode===2) return (a.health||20)-(b.health||20);
    return (a.distance||0)-(b.distance||0);
  });
  return cand[0];
}

function getBallisticRotations(api, ent, power){
  var p=api.player();
  var predictSize=api.num('predict');
  var motionX=(ent.vx||0)*predictSize*20.0, motionY=(ent.vy||0)*predictSize*20.0, motionZ=(ent.vz||0)*predictSize*20.0;
  var deltaX=(ent.x+motionX)-p.x;
  var deltaY=((ent.y+motionY)+(ent.eyeHeight||1.62)-0.15)-(p.y+1.62);
  var deltaZ=(ent.z+motionZ)-p.z;
  var horizontal=Math.sqrt(deltaX*deltaX+deltaZ*deltaZ);
  var yaw=Math.atan2(deltaZ,deltaX)*180/Math.PI - 90.0;
  var pitch; var gravity=0.006; var powerSq=power*power;
  var discriminant=powerSq*powerSq - gravity*(gravity*horizontal*horizontal + 2.0*deltaY*powerSq);
  if(discriminant<0.0 || horizontal<=0.0) pitch = -Math.atan2(deltaY,horizontal)*180/Math.PI;
  else pitch = -Math.atan((powerSq - Math.sqrt(discriminant)) / (gravity*horizontal)) * 180/Math.PI;
  if(!isFinite(pitch)) pitch = -Math.atan2(deltaY,horizontal)*180/Math.PI;
  return [yaw, clamp(pitch,-90,90)];
}

function applySmoothing(api, curYaw, curPitch, tgtYaw, tgtPitch){
  var factor = 1.0 - clamp(api.num('smoothing')/100.0, 0.0, 0.95);
  var yawDelta = wrapAngle(tgtYaw-curYaw);
  var pitchDelta = clamp(tgtPitch-curPitch, -90, 90);
  var yawStep = yawDelta*factor*randf(0.85,1.15);
  var pitchStep = pitchDelta*factor*randf(0.85,1.15);
  if(Math.abs(yawStep)>Math.abs(yawDelta)) yawStep=yawDelta;
  if(Math.abs(pitchStep)>Math.abs(pitchDelta)) pitchStep=pitchDelta;
  return [curYaw + yawStep, clamp(curPitch + pitchStep, -90, 90)];
}

function onEvent(api,event){
  if(!api.isEnabled || !api.isEnabled()) return;
  var t=event.getType();
  var p=api.player(); if(!p||!p.inWorld) return;
  if(t===0){ // tick/update
    if(!isHoldingDrawnBow(api)){ reset(); return; }
    target = findTarget(api);
    if(!target){ silentYaw=NaN; silentPitch=NaN; return; }
    var wanted = getBallisticRotations(api, target, getBowPower(api));
    if((api.num('mode')|0)===0){
      var stepped = applySmoothing(api, p.yaw, p.pitch, wanted[0], wanted[1]);
      if(api.claimRotation) api.claimRotation(ROTATION_PRIORITY);
      api.look(stepped[0], stepped[1]);
    } else {
      if(isNaN(silentYaw)||isNaN(silentPitch)){ silentYaw=p.yaw; silentPitch=p.pitch; }
      var stepped2 = applySmoothing(api, silentYaw, silentPitch, wanted[0], wanted[1]);
      silentYaw=stepped2[0]; silentPitch=stepped2[1];
      if(api.claimRotation) api.claimRotation(ROTATION_PRIORITY);
      api.look(silentYaw, silentPitch);
    }
  }
}
