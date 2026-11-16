// @slinky {"id":"lag_break","name":"LagBreak","description":"Rise LagBreak - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"max_lag_ticks","label":"Max Lag Ticks","type":"number","default":9,"min":1,"max":40,"step":1},
//   {"key":"range","label":"Range","type":"number","default":10,"min":8,"max":15,"step":0.1},
//   {"key":"minimum_range","label":"Minimum Range","type":"number","default":0,"min":0,"max":3,"step":0.1},
//   {"key":"visual_mode","label":"Visual Mode","type":"choice","default":0,"options":["Percentage","Circle","Text"]},
//   {"key":"circle_radius","label":"Circle Radius","type":"number","default":16,"min":8,"max":40,"step":1},
//   {"key":"circle_thickness","label":"Circle Thickness","type":"number","default":1.5,"min":0.5,"max":5,"step":0.1},
//   {"key":"dispatch_on_attack","label":"Dispatch On Attack","type":"boolean","default":true},
//   {"key":"dispatch_on_hurttime","label":"Dispatch On Hurttime","type":"boolean","default":false}
// ],
// "listens":["tick","packet_outbound","pre_move","render_ortho"]}

// Faithful logic port of Rise LagBreak (BlinkComponent + dispatch conditions + release progress)
var ps=0, blinkTicks=0, RELEASE_TICKS=6;
var pendingDispatch=false, blinkStartTick=0, releaseTicks=0, lastReleaseTick=0;
var releaseStartProgress=0.0, radius=16, lastRadius=16;
var blinking=false, releasing=false, releaseClear=0;

function onEnable(api){ gi(api); releaseTicks=0; lastReleaseTick=0; releaseStartProgress=0; lastRadius=radius=api.num('circle_radius'); blinking=false; releasing=false; releaseClear=0; blinkStartTick=ticksExisted(api); blinkTicks=0; pendingDispatch=false; }
function onDisable(api){ dispatchBlink(api); }
function gi(api){}

function ticksExisted(api){ var p=api.player(); return p?(p.ticksExisted||api.getTick()||0):(api.getTick()||0); }
function isMoving(api){ var p=api.player(); return !!p && ((p.moveForward||0)!==0 || (p.moveStrafing||0)!==0 || Math.abs(p.vx||0)+Math.abs(p.vz||0)>0.01); }

function getNearestTarget(api, maxDist){
  var ents=api.entities(), best=null, bd=1e9, p=api.player(); if(!p) return null;
  for(var i=0;i<ents.length;i++){ var e=ents[i]; if(e.id===p.id) continue; if(e.dead) continue; if(!e.player && !(e.class&&e.class.indexOf('Living')>=0)) continue; var d=e.distance!=null?e.distance:999; if(d<bd && d<=maxDist){ bd=d; best=e; } }
  return best;
}

function isLookingAtPlayer(api, entity, yawTol, pitchTol){
  var p=api.player(); if(!p||!entity) return false;
  var dx=p.x-entity.x, dz=p.z-entity.z;
  var d4=p.y+1.62-(entity.y+(entity.eyeHeight||1.62));
  var d5=Math.sqrt(dx*dx+dz*dz);
  var f4=(Math.atan2(dz,dx)*180/Math.PI - 90.0);
  var f5=(-Math.atan2(d4,d5)*180/Math.PI);
  var eyaw=entity.yaw||0, epitch=entity.pitch||0;
  var dy=((eyaw-f4+540)%360)-180; var dp=((epitch-f5+540)%360)-180;
  return Math.abs(dy)<=yawTol && Math.abs(dp)<=pitchTol;
}

function getLagProgress(api){
  var n=Math.max(1,(api.num('max_lag_ticks')|0)-1);
  return Math.max(0,Math.min(1, blinkTicks/n));
}
function getDisplayProgress(api){
  if(releaseTicks<=0){ if(blinkTicks<=0) return 0; return getLagProgress(api); }
  var f3=1.0 - (releaseTicks-1)/RELEASE_TICKS;
  var f4=1.0 - Math.pow(1.0-f3,3.0);
  return Math.max(0,Math.min(1, releaseStartProgress + (1.0-releaseStartProgress)*f4));
}
function updateRadius(api,f2){ var f3=api.num('circle_radius')+f2*5.0; lastRadius=radius; radius = lastRadius + (f3-lastRadius)*0.35; }

function dispatchBlink(api){
  if(blinkTicks>0){ releaseStartProgress=getLagProgress(api); releaseTicks=RELEASE_TICKS; lastReleaseTick=0; }
  blinking=false; releasing=true; releaseClear=2;
  blinkStartTick=ticksExisted(api); blinkTicks=0;
}
function cancelBlink(api){
  if(blinkTicks>0){ blinking=false; releasing=false; }
  releaseTicks=0; releaseStartProgress=0; lastReleaseTick=0; blinkTicks=0; pendingDispatch=false; releaseClear=0;
  blinkStartTick=ticksExisted(api);
}

function onEvent(api,event){
  var type=event.getType(); var p=api.player(); if(!p||!p.inWorld) return;
  if(type===3){ // outbound packet
    var cls=event.packetClass()||'';
    if(releasing){ event.setDispatchMode(0); return; }
    if(blinking || blinkTicks>0){ event.setDispatchMode(1); }
    if(!api.bool('dispatch_on_attack') || blinkTicks<=0) return;
    if(cls.indexOf('C02PacketUseEntity')>=0 && cls.indexOf('Action')>=0 || cls.indexOf('C02PacketUseEntity')>=0){
      // detect ATTACK
      var s=String(cls);
      try{ var ref=event.packetRef(); var act=ref&&ref.call?String(ref.call('getAction')||''):''; if(act.indexOf('ATTACK')>=0) pendingDispatch=true; else if(s.indexOf('ATTACK')>=0) pendingDispatch=true; }catch(e){ pendingDispatch=true; }
    }
    return;
  }
  if(type===21 || type===0){ // pre move / tick logic
    if(releaseClear>0 && type===0){ releaseClear--; if(releaseClear<=0) releasing=false; }
    if(type===0){
      if(blinkTicks>0) blinking=true;
      if(blinkTicks<=0 && releaseTicks<=0) return;
      updateRadius(api,getDisplayProgress());
      return;
    }
    blinkTicks = ticksExisted(api) - blinkStartTick - 1;
    if(blinkTicks>0 && releaseTicks>0){ releaseTicks=0; releaseStartProgress=0; lastReleaseTick=0; }
    var target = getNearestTarget(api, api.num('range'));
    var speed = Math.sqrt((p.vx||0)*(p.vx||0)+(p.vz||0)*(p.vz||0));
    var bl = target && (target.distance!=null?target.distance:999) <= 3.0 + speed/2.0;
    var bl2 = target && (target.distance!=null?target.distance:999) <= 5.0 && isLookingAtPlayer(api,target,12.0,20.0);
    var n2 = target ? (9 + Math.floor(Math.random()*8)) : (10 + Math.floor(Math.random()*9));
    var bl3=false;
    if(blinkTicks >= (api.num('max_lag_ticks')|0) || !isMoving(api) || !target || (target && target.distance > api.num('range'))){ bl3=true; }
    // hurt condition analog: player hurtTime < 2 (ae/Zl)
    if(!api.bool('dispatch_on_attack')){
      if(!bl3 && bl) bl3=true;
      if(!bl3 && blinkTicks>=n2 && !bl2) bl3=true;
    }
    if(bl3) dispatchBlink(api);
    if(target && target.distance < api.num('minimum_range')) dispatchBlink(api);
    if((p.hurtTime||0) < 2 && api.bool('dispatch_on_hurttime')) dispatchBlink(api);
    if(pendingDispatch){ dispatchBlink(api); pendingDispatch=false; }
    if(!blinking && !releasing && blinkTicks<=0){ blinking=true; blinkStartTick=ticksExisted(api); }
    return;
  }
  if(type===7){ // render ortho
    if(blinkTicks<=0 && releaseTicks<=0) return;
    var mode = api.num('visual_mode')|0;
    var d = api.draw(); if(!d) return;
    var sw=854, sh=480; try{ sw=event.getRenderWidth?event.getRenderWidth():sw; sh=event.getRenderHeight?event.getRenderHeight():sh; }catch(e){}
    if(mode===2 || mode===0 || mode===1){
      var txt = 'Blinking: '+blinkTicks;
      if(d.text){ d.text(txt, sw/2 + 1, sh/2 + 11, 0xC8000000, false, 9); d.text(txt, sw/2, sh/2 + 10, 0xFFFFFFFF, false, 9); }
    }
    if(releaseTicks>0 && ticksExisted(api)!==lastReleaseTick){ lastReleaseTick=ticksExisted(api); releaseTicks--; }
  }
}
