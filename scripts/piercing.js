// @slinky {"id":"piercing","name":"Piercing","description":"Myau Piercing - faithful behavior port (script-adapted raytrace)","category":"combat",
// "settings":[
//   {"key":"sort_mode","label":"Sort Mode","type":"choice","default":0,"options":["HURT_TIME","HEALTH"]},
//   {"key":"ignore_blocks","label":"Ignore Blocks","type":"boolean","default":false},
//   {"key":"ignore_teammates","label":"Ignore Teammates","type":"boolean","default":true},
//   {"key":"ignore_non_players","label":"Ignore Non Players","type":"boolean","default":true},
//   {"key":"weapon_only","label":"Weapon Only","type":"boolean","default":false},
//   {"key":"inside_hitbox_only","label":"Inside Hitbox Only","type":"boolean","default":false}
// ],
// "listens":["pre_interact"]}

function holdingWeapon(api){ var h=api.heldItem(); if(!h||h.empty) return false; var c=(h.class||'')+(h.name||''); return c.indexOf('Sword')>=0||c.indexOf('Axe')>=0||c.indexOf('sword')>=0||c.indexOf('axe')>=0; }

function shouldSkip(api, e){
  var p=api.player(); if(!p) return true;
  if(e.id===p.id) return true;
  if(api.bool('ignore_non_players') && !e.player) return true;
  if(e.player){
    if(api.bool('ignore_teammates') && (e.flags&1)) return true;
    if((e.flags&2) || (e.flags&4)) return true;
  }
  return false;
}

function vanillaBlockTarget(api){
  try{
    var mc=api.mc(); if(!mc) return false;
    var mo=mc.get('objectMouseOver');
    if(!mo) return false;
    var ref=api.ref(mo);
    var type=String(ref.get('typeOfHit')||'');
    return type.indexOf('BLOCK')>=0;
  }catch(e){ return false; }
}

function reachDistance(api){
  var reach=3.0;
  if(api.moduleEnabled('reach')){
    var value=api.moduleSetting('reach','distance');
    if(value!==null && value!==undefined) reach=Math.max(reach, Number(value));
  }
  return reach;
}

function onEvent(api,event){
  if(!api.isEnabled || !api.isEnabled()) return;
  if(event.getType()!==14) return;
  if(!event.isActive || !event.isActive()) return;
  if(api.bool('weapon_only') && !holdingWeapon(api)) return;
  if(!api.bool('ignore_blocks') && vanillaBlockTarget(api)) return;

  var p=api.player(); if(!p||!p.inWorld) return;
  var reach=reachDistance(api);
  var extended=reach>3.0;
  var ents=api.entities();
  var best=null, bestDist=1e9, bestLiving=false, bestHurt=1e9, bestHp=1e9;
  var mode = api.num('sort_mode')|0;
  var yaw=p.yaw*Math.PI/180.0, pitch=p.pitch*Math.PI/180.0;
  var lx=-Math.sin(yaw)*Math.cos(pitch), ly=-Math.sin(pitch), lz=Math.cos(yaw)*Math.cos(pitch);
  var ex=p.x, ey=p.y+(p.eyeHeight||1.62), ez=p.z;

  for(var i=0;i<ents.length;i++){
    var e=ents[i]; if(shouldSkip(api,e)) continue;
    var w=(e.width||0.6), h=(e.height||1.8), border=0.1;
    var minx=e.x-w/2-border, maxx=e.x+w/2+border, miny=e.y-border, maxy=e.y+h+border, minz=e.z-w/2-border, maxz=e.z+w/2+border;
    var inside = ex>=minx&&ex<=maxx&&ey>=miny&&ey<=maxy&&ez>=minz&&ez<=maxz;
    var tmin=-1e9,tmax=1e9;
    function slab(o,d,mn,mx){ if(Math.abs(d)<1e-8){ return !(o<mn||o>mx); } var t1=(mn-o)/d, t2=(mx-o)/d; if(t1>t2){var tmp=t1;t1=t2;t2=tmp;} if(t1>tmin) tmin=t1; if(t2<tmax) tmax=t2; return tmin<=tmax; }
    var ok = slab(ex,lx,minx,maxx)&&slab(ey,ly,miny,maxy)&&slab(ez,lz,minz,maxz);
    if(!inside && (!ok || tmax<0)) continue;
    var dist = inside?0.0:Math.max(0.0,tmin);
    if(dist>reach || dist>=bestDist) continue;
    if(!extended && dist>3.0) continue;
    if(api.bool('inside_hitbox_only') && dist > 0.10000000149011612) continue;
    var living = !!e.player || ((e.class||'').indexOf('Living')>=0);
    var hurt = living ? (e.hurtTime||0) : 1e9;
    var hp = living ? (e.health!=null?e.health:20) : 1e9;
    var take=false;
    if(!best || (living && !bestLiving)) take=true;
    else if(living===bestLiving){
      if(!living) take = dist < bestDist;
      else if(mode===0) take = hurt < bestHurt || (hurt===bestHurt && dist<bestDist);
      else take = hp < bestHp || (hp===bestHp && dist<bestDist);
    }
    if(take){ best=e; bestDist=dist; bestLiving=living; bestHurt=hurt; bestHp=hp; }
  }
  if(!best) return;
  var ref = api.entityById ? api.entityById(best.id) : null;
  if(ref){
    event.cancel();
    api.swing();
    api.attackEntity(ref);
  }
}
