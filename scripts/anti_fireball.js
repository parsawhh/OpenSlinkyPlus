// @slinky {"id":"anti_fireball","name":"AntiFireball","description":"Myau AntiFireball - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"range","label":"Range","type":"number","default":5.0,"min":3.0,"max":8.0,"step":0.1},
//   {"key":"fov","label":"FOV","type":"number","default":360,"min":1,"max":360,"step":1},
//   {"key":"rotations","label":"Rotations","type":"boolean","default":true},
//   {"key":"swing","label":"Swing","type":"boolean","default":true},
//   {"key":"move_fix","label":"Move Fix","type":"choice","default":1,"options":["NONE","SILENT","STRICT"]},
//   {"key":"show_target","label":"Show Target","type":"choice","default":0,"options":["NONE","DEFAULT","HUD"]}
// ],
// "listens":["tick","rotation","render_world"]}

var farList = [], nearList = [], targetId = null;

function onEnable(api){ farList=[]; nearList=[]; targetId=null; }
function onDisable(api){ farList=[]; nearList=[]; targetId=null; }
function contains(arr,id){ for(var i=0;i<arr.length;i++) if(arr[i]===id) return true; return false; }
function removeMissing(arr, ids){ for(var i=arr.length-1;i>=0;i--) if(ids.indexOf(arr[i])<0) arr.splice(i,1); }

function angleToEntity(api, p, e){
  var dx=e.x-p.x, dz=e.z-p.z;
  var dy=(e.y + (e.height||1.0)/2.0) - (p.y + 1.62);
  var horiz=Math.sqrt(dx*dx+dz*dz);
  var yaw=Math.atan2(dz,dx)*180/Math.PI - 90.0;
  var pitch=-Math.atan2(dy,horiz)*180/Math.PI;
  var dyaw=Math.abs(((yaw-p.yaw+540)%360)-180);
  var dp=Math.abs(pitch-p.pitch);
  return Math.sqrt(dyaw*dyaw+dp*dp);
}

function getRotationsToBox(api, e){
  var p=api.player();
  var cx=e.x, cy=e.y + (e.height||1.0)/2.0, cz=e.z;
  var dx=cx-p.x, dy=cy-(p.y+1.62), dz=cz-p.z;
  var horiz=Math.sqrt(dx*dx+dz*dz);
  var yaw=Math.atan2(dz,dx)*180/Math.PI - 90.0;
  var pitch=-Math.atan2(dy,horiz)*180/Math.PI;
  return [yaw,pitch];
}

function doAttackAnimation(api){
  if(api.bool('swing')) api.swing();
  else {
    // send C0A animation packet best-effort
    try{ var pkt=api.newPacket('net.minecraft.network.play.client.C0APacketAnimation'); if(pkt) api.sendPacket(pkt); }catch(e){ api.swing(); }
  }
}

function findEntity(api,id){ var es=api.entities(); for(var i=0;i<es.length;i++) if(es[i].id===id) return es[i]; return null; }

function onTick(api){
  if(!api.isEnabled || !api.isEnabled()) return;
  var p=api.player(); if(!p||!p.inWorld) return;
  var es=api.entities(), fireballs=[], ids=[];
  for(var i=0;i<es.length;i++){
    var e=es[i]; var c=(e.class||'')+(e.type||'');
    if(c.indexOf('Fireball')>=0 || c.indexOf('fireball')>=0){ fireballs.push(e); ids.push(e.id); }
  }
  removeMissing(farList, ids); removeMissing(nearList, ids);
  for(var j=0;j<fireballs.length;j++){
    var f=fireballs[j];
    if(!contains(farList,f.id) && !contains(nearList,f.id)){
      if(f.distance>3.0) farList.push(f.id); else nearList.push(f.id);
    }
  }
  targetId=null;
  if(p.flying || (p.capabilities && p.capabilities.allowFlying)) { targetId=null; return; }
  var best=null, bd=1e9;
  for(var k=0;k<farList.length;k++){
    var fe=findEntity(api,farList[k]); if(!fe) continue;
    if(fe.distance > api.num('range')+3.0) continue;
    if(angleToEntity(api,p,fe) > api.num('fov')) continue;
    var d=fe.distance; if(d<bd){ bd=d; best=fe; }
  }
  if(best) targetId=best.id;
}

function onEvent(api,event){
  if(!api.isEnabled || !api.isEnabled()) return;
  var t=event.getType(); var p=api.player(); if(!p||!p.inWorld) return;
  if(t===13){ // rotation pass
    var tgt = targetId!=null ? findEntity(api,targetId) : null;
    if(!tgt) return;
    var rots = getRotationsToBox(api,tgt);
    if(api.bool('rotations')){
      var h=api.heldItem();
      var holdingNonEmpty = h && !h.empty;
      var using = !!p.usingItem;
      // matches Myau: rotate unless a drawn/used item is in hand
      if(!holdingNonEmpty || !using){
        api.claimRotation(0);
        api.look(rots[0], rots[1]);
        if((api.num('move_fix')|0)!==0) event.setRemapMovement(true);
      }
    }
    doAttackAnimation(api);
    if(tgt.distance <= api.num('range')){
      var ref = api.entityById ? api.entityById(tgt.id) : null;
      if(ref){ api.attackEntity(ref); }
    }
  }
  if(t===6){ // render world
    var mode = api.num('show_target')|0;
    if(mode===0) return;
    var tgt = targetId!=null ? findEntity(api,targetId) : null; if(!tgt) return;
    var d=api.draw(); if(!d||!d.box) return;
    var col = 0x5533DD33;
    if(mode===1){
      var dx=tgt.x-(tgt.lastX!=null?tgt.lastX:tgt.x);
      var dy=tgt.y-(tgt.lastY!=null?tgt.lastY:tgt.y);
      var dz=tgt.z-(tgt.lastZ!=null?tgt.lastZ:tgt.z);
      var dot=dx*(p.x-tgt.x)+dy*((p.y+(p.eyeHeight||1.62))-tgt.y-(tgt.height||1.8)/2.0)+dz*(p.z-tgt.z);
      col = dot<0 ? 0x55FF5555 : 0x5533DD33;
    }
    d.box(tgt.x, tgt.y, tgt.z, tgt.width||0.8, tgt.height||1.8, tgt.width||0.8, col, 2.0);
  }
}
