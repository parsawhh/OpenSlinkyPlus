// @slinky {"id":"sprint_reset","name":"SprintReset","description":"Myau SprintReset - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"mode","label":"Mode","type":"choice","default":2,"options":["PACKET","LEGIT","SILENT"]},
//   {"key":"only_while_sprinting","label":"Only While Sprinting","type":"boolean","default":true},
//   {"key":"only_while_moving","label":"Only While Moving","type":"boolean","default":true},
//   {"key":"reset_on_crit","label":"Reset On Crit","type":"boolean","default":true},
//   {"key":"smart_reset","label":"Smart Reset","type":"boolean","default":false},
//   {"key":"fast_reset","label":"Fast Reset","type":"boolean","default":false}
// ],
// "listens":["pre_interact","tick"]}

var attacked = false, needsReset = false, ticksSinceAttack = 0, lastTargetId = null;

function onEnable(api){ attacked=false; needsReset=false; ticksSinceAttack=0; lastTargetId=null; }
function onDisable(api){ attacked=false; needsReset=false; ticksSinceAttack=0; lastTargetId=null; }

function isMoving(p){ return (p.moveForward||0) !== 0 || (p.moveStrafing||0) !== 0 || Math.abs(p.vx||0)+Math.abs(p.vz||0) > 0.02; }

function canCrit(p){
  if(!p) return false;
  if(!p.onGround) return false;
  if(p.inWater) return false;
  if(p.inLava) return false;
  if(p.onLadder) return false;
  if(p.riding) return false;
  return true;
}

function sendC0B(api, action){
  // action: START_SPRINTING / STOP_SPRINTING
  try{
    var pr = api.rawPlayer(); if(!pr) return false;
    var cls = 'net.minecraft.network.play.client.C0BPacketEntityAction';
    var actCls = 'net.minecraft.network.play.client.C0BPacketEntityAction$Action';
    var act = pr.findClass ? pr.findClass(actCls) : null;
    var field = action==='START_SPRINTING' ? 'START_SPRINTING' : 'STOP_SPRINTING';
    var A = null;
    try{ A = java.lang.Class.forName(actCls.replace(/\$/g,'$')).getField(field).get(null); }catch(e){}
    if(!A){
      try{ var c = api.ref(pr.raw()).raw().getClass().getClassLoader().loadClass(actCls); A = c.getField(field).get(null); }catch(e2){}
    }
    var pkt = api.newPacket(cls, pr.raw(), A);
    if(pkt) return api.sendPacket(pkt);
  }catch(e){}
  // fallback
  if(action==='START_SPRINTING') api.sendSprintPacket(true); else api.sendSprintPacket(false);
  return true;
}

function performCritReset(api){
  var p = api.player(); if(!p) return;
  if(p.onGround && !p.inWater && !p.onLadder){
    var x=p.x,y=p.y,z=p.z;
    try{
      var C04='net.minecraft.network.play.client.C03PacketPlayer$C04PacketPlayerPosition';
      var p1=api.newPacket(C04,x,y+0.0625,z,false);
      var p2=api.newPacket(C04,x,y,z,false);
      var p3=api.newPacket(C04,x,y+1.1E-5,z,false);
      var p4=api.newPacket(C04,x,y,z,false);
      if(p1) api.sendPacket(p1); if(p2) api.sendPacket(p2); if(p3) api.sendPacket(p3); if(p4) api.sendPacket(p4);
    }catch(e){}
  }
}

function handlePacketReset(api){
  sendC0B(api,'STOP_SPRINTING');
  sendC0B(api,'START_SPRINTING');
  var p=api.player();
  if(api.bool('reset_on_crit') && canCrit(p)) performCritReset(api);
  attacked=false; ticksSinceAttack=0;
}

function handleLegitReset(api){
  api.setSprint(false);
  needsReset=true; ticksSinceAttack=0;
  var p=api.player();
  if(api.bool('reset_on_crit') && canCrit(p)) performCritReset(api);
}

function handleSilentReset(api){
  sendC0B(api,'STOP_SPRINTING');
  needsReset=true; ticksSinceAttack=0;
  var p=api.player();
  if(api.bool('reset_on_crit') && canCrit(p)) performCritReset(api);
}

function onEvent(api,event){
  if(event.getType()!==14) return;
  if(!event.isActive || !event.isActive()) return; // attack
  var p=api.player(); if(!p||!p.inWorld) return;
  var t=api.target(); if(!t) return;
  // must be living
  if(t.dead) return;
  if(api.bool('only_while_sprinting') && !p.sprinting) return;
  if(api.bool('only_while_moving') && !isMoving(p)) return;
  if(api.bool('smart_reset')){
    if((t.health||0) <= 0) return;
    if((t.distance||999) > 6.0) return;
  }
  if(api.bool('fast_reset') && lastTargetId != null && lastTargetId !== t.id){ handlePacketReset(api); }
  lastTargetId = t.id;
  attacked = true;
  var mode = api.num('mode')|0;
  if(mode===0) handlePacketReset(api);
  else if(mode===1) handleLegitReset(api);
  else handleSilentReset(api);
}

function onTick(api){
  if(!api.isEnabled || !api.isEnabled()) return;
  var p=api.player(); if(!p||!p.inWorld) return;
  if(attacked) ticksSinceAttack++;
  var mode = api.num('mode')|0;
  if(mode===1 && needsReset){
    if(ticksSinceAttack>=1){ api.setSprint(true); needsReset=false; attacked=false; ticksSinceAttack=0; }
  }
  if(mode===2 && needsReset){
    if(ticksSinceAttack>=1){ sendC0B(api,'START_SPRINTING'); needsReset=false; attacked=false; ticksSinceAttack=0; }
  }
  if(ticksSinceAttack>5){ attacked=false; needsReset=false; ticksSinceAttack=0; }
}
