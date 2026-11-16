// @slinky {"id":"hitselect","name":"HitSelect","description":"Myau HitSelect - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"mode","label":"Mode","type":"choice","default":0,"options":["SECOND","CRITICALS","W_TAP"]}
// ],
// "listens":["packet_outbound","tick"]}

var sprintState = false;
var set = false;
var keepSprintWasEnabled = false;
var savedSlowdown = 0.0;
var blockedHits = 0, allowedHits = 0;

function onEnable(api){ sprintState=false; set=false; keepSprintWasEnabled=false; savedSlowdown=0.0; blockedHits=0; allowedHits=0; }
function onDisable(api){ resetMotion(api); sprintState=false; set=false; savedSlowdown=0.0; blockedHits=0; allowedHits=0; }

function onTick(api){ if(!api.isEnabled || !api.isEnabled()) return; resetMotion(api); }

function getTargetEntity(api, ref){
  try{
    var pr = api.rawPlayer(); var w = api.rawWorld(); if(!pr||!w||!ref) return null;
    var ent = ref.call ? ref.call('getEntityFromWorld', w.raw()) : null;
    if(ent) return api.entityById ? null : null; // we'll work from packet/entity id via snapshot
  }catch(e){}
  return null;
}

function isMovingTowards(source, target, maxAngle){
  if(!source||!target) return false;
  var cx=source.x, cy=source.y, cz=source.z;
  var lx=(source.prevX!=null?source.prevX:source.lastTickPosX!=null?source.lastTickPosX:source.x);
  var ly=(source.prevY!=null?source.prevY:source.lastTickPosY!=null?source.lastTickPosY:source.y);
  var lz=(source.prevZ!=null?source.prevZ:source.lastTickPosZ!=null?source.lastTickPosZ:source.z);
  var tx=target.x, tz=target.z;
  var mx=cx-lx, mz=cz-lz;
  var ml=Math.sqrt(mx*mx+mz*mz); if(ml===0.0) return false; mx/=ml; mz/=ml;
  var vx=tx-cx, vz=tz-cz; var tl=Math.sqrt(vx*vx+vz*vz); if(tl===0.0) return false; vx/=tl; vz/=tl;
  var dot=mx*vx+mz*vz; return dot >= Math.cos(maxAngle*Math.PI/180.0);
}

function targetSnapshotByEntity(api, entRef){
  // try to find entity snapshot matching id
  try{
    var id = entRef.get ? entRef.get('entityId') : (entRef.call?entRef.call('getEntityId'):null);
    if(id==null) return null;
    var ents=api.entities();
    for(var i=0;i<ents.length;i++) if(ents[i].id===(id|0)) return ents[i];
  }catch(e){}
  return null;
}

function fixMotion(api){
  if(set) return;
  try{
    // emulate KeepSprint handling: enable KeepSprint and set slowdown 0 if possible
    keepSprintWasEnabled = api.moduleEnabled ? api.moduleEnabled('Keep Sprint') : false;
    if(!keepSprintWasEnabled && api.setModuleEnabled) api.setModuleEnabled('Keep Sprint', true);
    if(api.setModuleSetting) api.setModuleSetting('Keep Sprint','slowdown','0');
    set = true;
  }catch(e){ set=true; }
}

function resetMotion(api){
  if(!set) return;
  try{
    if(!keepSprintWasEnabled && api.setModuleEnabled) api.setModuleEnabled('Keep Sprint', false);
  }catch(e){}
  set=false; keepSprintWasEnabled=false; savedSlowdown=0.0;
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
  if(!api.keyDown || !api.keyDown('Forward')) return true;
  if(sprinting) return true;
  fixMotion(api); return false;
}

function onEvent(api,event){
  if(!api.isEnabled || !api.isEnabled()) return;
  if(event.getType()===3){
    var cls = event.packetClass()||'';
    if(cls.indexOf('C0BPacketEntityAction')>=0){
      try{
        var ref = event.packetRef();
        var act = ref.call ? (ref.call('getAction')||'') : '';
        var s = String(act);
        if(s.indexOf('START_SPRINTING')>=0) sprintState=true;
        else if(s.indexOf('STOP_SPRINTING')>=0) sprintState=false;
      }catch(e){}
      return;
    }
    if(cls.indexOf('C02PacketUseEntity')>=0){
      try{
        var ref = event.packetRef();
        var action = ref.call ? String(ref.call('getAction')||'') : '';
        if(action.indexOf('ATTACK')<0) return;
        var w = api.rawWorld ? api.rawWorld() : null;
        var ent = null;
        try{ if(w) ent = ref.call('getEntityFromWorld', w.raw()); }catch(e){}
        if(!ent) return;
        var er = api.ref(ent);
        // skip fireball
        var en = (er.raw().getClass().getName()||'');
        if(en.indexOf('EntityLargeFireball')>=0) return;
        // need living
        if(en.indexOf('EntityLivingBase')<0 && en.indexOf('EntityPlayer')<0 && en.indexOf('EntityOtherPlayerMP')<0) return;
        var target = targetSnapshotByEntity(api, er);
        if(!target){
          // build minimal snapshot
          target = {x:0,y:0,z:0,hurtTime:0,distance:3,maxHurtTime:10};
          try{ target.x = Number(er.call('posX')||er.get('posX')||0); target.y=Number(er.call('posY')||er.get('posY')||0); target.z=Number(er.call('posZ')||er.get('posZ')||0); target.hurtTime=Number(er.get('hurtTime')||0);}catch(e2){}
        }
        var p = api.player(); if(!p) return;
        var allow=true; var mode=api.num('mode')|0;
        if(mode===0) allow=prioritizeSecondHit(api,p,target);
        else if(mode===1) allow=prioritizeCriticalHits(api,p);
        else if(mode===2) allow=prioritizeWTapHits(api,p,sprintState);
        if(!allow){ event.setDispatchMode(2); blockedHits++; }
        else { allowedHits++; }
      }catch(e){}
    }
  }
}
