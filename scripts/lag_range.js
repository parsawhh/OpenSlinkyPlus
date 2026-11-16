// @slinky {"id":"lag_range","name":"LagRange","description":"CrewX-style lag range: delays outbound packets while you gain distance advantage on target","category":"combat",
// "settings":[
//   {"key":"delay_ms","label":"Delay (ms)","type":"number","default":150,"min":0,"max":1000,"step":10},
//   {"key":"range","label":"Range","type":"number","default":10,"min":3,"max":100,"step":0.5},
//   {"key":"weapons_only","label":"Weapons Only","type":"boolean","default":true},
//   {"key":"render","label":"Show Position","type":"boolean","default":true}
// ],
// "listens":["tick","packet_outbound","render_world"]}

var holding=false, holdTicks=0, delayCounter=0, hasTarget=false;
var lastPos=null, curPos=null;

function onEnable(api){ reset(api); }
function onDisable(api){ reset(api); }
function reset(api){ holding=false; holdTicks=0; hasTarget=false; lastPos=null; curPos=null; }

function isWeapon(api){ var h=api.heldItem(); if(!h||h.empty) return false; var c=(h.class||''); return c.indexOf('Sword')>=0 || c.indexOf('Axe')>=0; }

function bestTarget(api){
  var p=api.player(); if(!p||!p.inWorld) return null;
  var ents=api.entities(); var best=null, bd=1e9;
  for(var i=0;i<ents.length;i++){
    var e=ents[i]; if(!e.player) continue; if(e.dead) continue;
    if(e.distance < bd && e.distance <= api.num('range')){ bd=e.distance; best=e; }
  }
  return best;
}

function onEvent(api,event){
  var t=event.getType();
  if(t===3){
    var cls=event.packetClass()||'';
    // reset on attack/interaction packets like CrewX
    if(cls.indexOf('C02PacketUseEntity')>=0 || cls.indexOf('C07PacketPlayerDigging')>=0){ holding=false; holdTicks=0; hasTarget=false; }
    if(holding && holdTicks>0){ event.setDispatchMode(1); }
    else if(holding){ event.setDispatchMode(0); }
  }
  if(t===6 && api.bool('render') && curPos){
    var d=api.draw();
    d.box(curPos.x, curPos.y, curPos.z, 0.6, 1.8, 0.6, 0x5500FFFF, 2.0);
  }
}

function onTick(api){
  if(!api.isEnabled()) return;
  var p=api.player(); if(!p||!p.inWorld){ reset(api); return; }
  // update tracked position
  var nowPos={x:p.x,y:p.y,z:p.z};
  if(!curPos){ lastPos=nowPos; } else { lastPos=curPos; }
  curPos=nowPos;
  if(api.bool('weapons_only') && !isWeapon(api)){ holding=false; holdTicks=0; hasTarget=false; return; }
  var tgt=bestTarget(api);
  if(!tgt){ holding=false; holdTicks=0; hasTarget=false; delayCounter=0; return; }
  // advantage: we are close now
  if(tgt.distance <= api.num('range')){
    hasTarget=true;
    if(holdTicks<=0){
      delayCounter += api.num('delay_ms');
      holdTicks = Math.max(1, Math.floor(delayCounter/50));
      delayCounter = Math.max(0, delayCounter - holdTicks*50);
    }
    holding=true;
  } else { holding=false; holdTicks=0; hasTarget=false; }
  if(holdTicks>0) holdTicks--; else holding=false;
}
