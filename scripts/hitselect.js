// @slinky {"id":"hitselect","name":"HitSelect","description":"Myau HitSelect - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"mode","label":"Mode","type":"choice","default":0,"options":["SECOND","CRITICALS","W_TAP"]}
// ],
// "listens":["packet_outbound","tick"]}

var sprintState = false;
var set = false;
var keepSprintWasEnabled = false;
var savedMotion = 1.0;
var blockedHits = 0, allowedHits = 0;

function onEnable(api){ sprintState=false; set=false; keepSprintWasEnabled=false; savedMotion=1.0; blockedHits=0; allowedHits=0; }
function onDisable(api){ resetMotion(api); sprintState=false; set=false; savedMotion=1.0; blockedHits=0; allowedHits=0; }
function onTick(api){ if(!api.isEnabled || !api.isEnabled()) return; resetMotion(api); }

function findSnapshot(api, id){
  var ents=api.entities();
  for(var i=0;i<ents.length;i++) if(ents[i] && ents[i].id===(id|0)) return ents[i];
  return null;
}

function isMovingTowards(source, target, maxAngle){
  if(!source||!target) return false;
  var cx=source.x, cz=source.z;
  var lx=(source.lastX!=null?source.lastX:source.x);
  var lz=(source.lastZ!=null?source.lastZ:source.z);
  var mx=cx-lx, mz=cz-lz;
  var ml=Math.sqrt(mx*mx+mz*mz); if(ml===0.0) return false; mx/=ml; mz/=ml;
  var vx=target.x-cx, vz=target.z-cz; var tl=Math.sqrt(vx*vx+vz*vz); if(tl===0.0) return false; vx/=tl; vz/=tl;
  var dot=mx*vx+mz*vz; return dot >= Math.cos(maxAngle*Math.PI/180.0);
}

function fixMotion(api){
  if(set) return;
  keepSprintWasEnabled = api.moduleEnabled('keepsprint');
  savedMotion = api.moduleSetting('keepsprint','motion_mult');
  if(savedMotion===null || savedMotion===undefined) savedMotion = 1.0;
  api.setModuleSetting('keepsprint','motion_mult',1.0);
  if(!keepSprintWasEnabled) api.setModuleEnabled('keepsprint',true);
  set = true;
}

function resetMotion(api){
  if(!set) return;
  api.setModuleSetting('keepsprint','motion_mult',savedMotion);
  if(!keepSprintWasEnabled) api.setModuleEnabled('keepsprint',false);
  set=false; keepSprintWasEnabled=false; savedMotion=1.0;
}

function prioritizeSecondHit(api, player, target){
  if((target.hurtTime||0) !== 0) return true;
  var maxHurt = target.maxHurtTime || 10;
  if((player.hurtTime||0) <= maxHurt - 1) return true;
  var dist = target.distance!=null ? target.distance : Math.sqrt((player.x-target.x)*(player.x-target.x)+(player.y-target.y)*(player.y-target.y)+(player.z-target.z)*(player.z-target.z));
  if(dist < 2.5) return true;
  if(!isMovingTowards(target, player, 60.0)) return true;
  if(!isMovingTowards(player, target, 60.0)) return true;
  fixMotion(api); return false;
}

function prioritizeCriticalHits(api, player){
  if(player.onGround) return true;
  if((player.hurtTime||0) !== 0) return true;
  if((player.fallDistance||0) > 0.0) return true;
  fixMotion(api); return false;
}

function prioritizeWTapHits(api, player, sprinting){
  if(player.collidedHorizontally) return true;
  if(!api.keyDown('Forward')) return true;
  if(sprinting) return true;
  fixMotion(api); return false;
}

function onEvent(api,event){
  if(!api.isEnabled || !api.isEnabled()) return;
  if(event.getType()!==3) return;
  var cls = event.packetClass()||'';
  var ref = event.packetRef();
  if(!ref) return;

  if(cls.indexOf('C0BPacketEntityAction')>=0){
    var sa = String(ref.get('action')||'');
    if(sa.indexOf('START_SPRINTING')>=0) sprintState=true;
    else if(sa.indexOf('STOP_SPRINTING')>=0) sprintState=false;
    return;
  }

  if(cls.indexOf('C02PacketUseEntity')<0) return;
  var action = String(ref.get('action')||'');
  if(action.indexOf('ATTACK')<0) return;
  var id = ref.get('entityId');
  if(id===null || id===undefined) id = ref.call('getEntityId');
  if(id===null || id===undefined) return;

  var target = findSnapshot(api, id);
  if(!target || typeof target.health !== 'number') return;
  var p = api.player(); if(!p) return;

  var allow=true; var mode=api.num('mode')|0;
  if(mode===0) allow=prioritizeSecondHit(api,p,target);
  else if(mode===1) allow=prioritizeCriticalHits(api,p);
  else if(mode===2) allow=prioritizeWTapHits(api,p,sprintState);
  if(!allow){ event.setDispatchMode(2); blockedHits++; }
  else { allowedHits++; }
}
