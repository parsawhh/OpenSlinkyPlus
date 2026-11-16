// @slinky {"id":"legit_reach","name":"LegitReach","description":"Rise LegitReach (Back Track) - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"max_ping_spoof","label":"Max Ping Spoof","type":"number","default":1000,"min":50,"max":10000,"step":1},
//   {"key":"render_real_location","label":"Render Real Location","type":"boolean","default":true},
//   {"key":"watchdog_mode","label":"Watchdog Mode","type":"boolean","default":false}
// ],
// "listens":["packet_inbound","tick","render_world","render_ortho"]}

var realTargetPosition = {x:0,y:0,z:0};
var anim = {x:0,y:0,z:0};
var targetId = null;
var holding = false, holdUntil = 0;

function onEnable(api){ targetId=null; holding=false; }
function onDisable(api){ targetId=null; holding=false; }

function distTo(p, pos){ var dx=p.x-pos.x, dy=p.y-pos.y, dz=p.z-pos.z; return Math.sqrt(dx*dx+dy*dy+dz*dz); }

function getNearestTarget(api, maxDist){
  var p=api.player(), best=null, bd=1e9, es=api.entities();
  for(var i=0;i<es.length;i++){ var e=es[i]; if(!e.player) continue; if(e.id===p.id) continue; if(e.dead) continue; if(e.distance<bd && e.distance<=maxDist){ bd=e.distance; best=e; } }
  return best;
}

function onTick(api){ // PostMotion equivalent
  if(!api.isEnabled || !api.isEnabled()) return;
  var p=api.player(); if(!p||!p.inWorld){ holding=false; targetId=null; return; }
  var entity = getNearestTarget(api, 9.0);
  if(!entity){ targetId=null; if(holding && api.now()>=holdUntil) holding=false; return; }
  if(entity.id !== targetId){ targetId=entity.id; realTargetPosition={x:entity.x,y:entity.y,z:entity.z}; anim={x:entity.x,y:entity.y,z:entity.z}; }
  var d2 = distTo({x:p.x,y:p.y,z:p.z}, realTargetPosition);
  var d3 = entity.distance;
  var flag1;
  if(api.bool('watchdog_mode')){
    flag1 = d2 > d3 && d2 > 2.5 && d2 < 4.4 && (p.hurtTime||0) > 2;
  } else {
    flag1 = d2 > d3 && d2 > 2.5 && d2 < 6.0;
  }
  // activate when swinging/attacking and condition met
  if(flag1 && (p.swinging || p.isSwingInProgress || false)){
    holding = true;
    holdUntil = api.now() + api.num('max_ping_spoof');
  } else {
    // also allow hold if condition persists recently; approximate by keeping while flag1 true
    if(holding && !flag1) holding=false;
  }
  // fallback: if attacking recently keep; detect via target presence + flag
  if(flag1 && api.target && api.target() && api.target().id===targetId){ holding=true; holdUntil=Math.max(holdUntil, api.now()+api.num('max_ping_spoof')); }
  if(holding && api.now()>=holdUntil){ holding=false; }
}

function onEvent(api,event){
  var t=event.getType();
  if(t===2){ // inbound
    var cls=event.packetClass()||'';
    // track real pos
    if(targetId!=null){
      try{
        var ref=event.packetRef();
        if(cls.indexOf('S14PacketEntity')>=0){
          var eid=null; try{ eid=ref.get('entityId'); }catch(e){}
          if(eid==null) try{ eid=ref.call('getEntityId'); }catch(e){}
          if(eid==null) try{ var f=ref.raw().getClass().getDeclaredField('field_149074_a'); f.setAccessible(true); eid=f.getInt(ref.raw()); }catch(e){}
          if((eid|0)===targetId){
            var dx=0,dy=0,dz=0;
            try{ dx=(ref.call('func_149062_c')||0)/32.0; dy=(ref.call('func_149061_d')||0)/32.0; dz=(ref.call('func_149064_e')||0)/32.0; }catch(e){}
            if(dx===0&&dy===0&&dz===0){ try{ dx=((ref.get('posX')||0))/32.0; dy=((ref.get('posY')||0))/32.0; dz=((ref.get('posZ')||0))/32.0; }catch(e2){} }
            realTargetPosition.x+=dx; realTargetPosition.y+=dy; realTargetPosition.z+=dz;
          }
        } else if(cls.indexOf('S18PacketEntityTeleport')>=0){
          var eid2=null; try{ eid2=ref.call('getEntityId'); }catch(e){}
          if((eid2|0)===targetId){
            try{ realTargetPosition.x=(ref.call('getX')||0)/32.0; realTargetPosition.y=(ref.call('getY')||0)/32.0; realTargetPosition.z=(ref.call('getZ')||0)/32.0; }catch(e){}
          }
        }
      }catch(e){}
    }
    // hold inbound packets
    if(holding && (cls.indexOf('S14PacketEntity')>=0 || cls.indexOf('S18PacketEntityTeleport')>=0 || cls.indexOf('S12PacketEntityVelocity')>=0)){
      if(api.now() < holdUntil){ event.setDispatchMode(1); } else { holding=false; event.setDispatchMode(0); }
    }
    return;
  }
  if(t===6){ // render world
    if(!api.bool('render_real_location') || targetId==null) return;
    var dur = api.bool('watchdog_mode') ? 0 : 150;
    var lerp = dur<=0 ? 1.0 : 0.33;
    anim.x += (realTargetPosition.x - anim.x)*lerp;
    anim.y += (realTargetPosition.y - anim.y)*lerp;
    anim.z += (realTargetPosition.z - anim.z)*lerp;
    var d=api.draw(); if(d && d.box) d.box(anim.x, anim.y, anim.z, 0.6, 1.8, 0.6, 0x33FF0000, 2.0);
    return;
  }
  if(t===7){ // render ortho watchdog reach text
    if(!api.bool('watchdog_mode') || targetId==null) return;
    var p=api.player(); if(!p) return;
    var tgt=null; var es=api.entities(); for(var i=0;i<es.length;i++) if(es[i].id===targetId){tgt=es[i];break;}
    if(!tgt) return;
    var d2=distTo({x:p.x,y:p.y,z:p.z}, realTargetPosition), d3=tgt.distance;
    if(d2>d3 && d2>2.8 && d2<4.4 && d2>3.0){
      var d=api.draw(); if(d&&d.text){ var sw=854,sh=480; try{sw=event.getRenderWidth?event.getRenderWidth():sw; sh=event.getRenderHeight?event.getRenderHeight():sh;}catch(e){} d.text('Reach: '+d2.toFixed(2), sw/2, sh/2+20, 0xFFFFFFFF, true, 9); }
    }
  }
}
