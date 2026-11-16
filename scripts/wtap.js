// @slinky {"id":"wtap","name":"WTap","description":"Vape WTap - faithful behavior port","category":"combat",
// "settings":[
//   {"key":"chance","label":"Chance","type":"number","default":90,"min":0,"max":100,"step":1},
//   {"key":"release_delay","label":"Release delay","type":"number","default":0,"min":0,"max":500,"step":50},
//   {"key":"repress_delay","label":"Re-press delay","type":"number","default":0,"min":0,"max":500,"step":50},
//   {"key":"select_hits","label":"Select hits","type":"boolean","default":true}
// ],
// "listens":["pre_interact","tick"]}

// Faithful to gg.vape.module.combat.WTap
var chance = 90.0;
var releaseDelay = 0.0;
var rePressDelay = 0.0;
var selectHits = true;

var releaseTimer = 0, rePressTimer = 0;
var releasePending = false, rePressPending = false;

function nowMs(api){ return api.now ? api.now() : Date.now(); }
function hasTimeElapsed(since, time, api){ return nowMs(api) - since >= time; }
function resetTimer(which, api){ if(which==='r') releaseTimer = nowMs(api); else rePressTimer = nowMs(api); }

function sync(api){ chance=api.num('chance'); releaseDelay=api.num('release_delay'); rePressDelay=api.num('repress_delay'); selectHits=api.bool('select_hits'); }

function onEnable(api){ sync(api); releasePending=false; rePressPending=false; releaseTimer=nowMs(api); rePressTimer=nowMs(api); }
function onDisable(api){ restoreForwardKey(api); }

function handleRePress(api){
  if(hasTimeElapsed(rePressTimer, Number(rePressDelay), api)){
    // if physical key down, set pressed true
    if(api.keyDown && api.keyDown('Forward')){ api.setKeyDown('Forward', true); }
    else { api.setKeyDown('Forward', true); }
    rePressPending = false;
  }
}

function isTargetVulnerable(api){
  if(!selectHits) return true;
  var t = api.target();
  if(!t) return true;
  // target.V() <= 14 in deobf (hurtTime/hurtResistance window) -> vulnerable when <=14
  var ht = (t.hurtTime != null) ? t.hurtTime : 0;
  return ht <= 14;
}

function restoreForwardKey(api){
  if(api && api.setKeyDown){
    var phys = api.keyDown ? api.keyDown('Forward') : true;
    api.setKeyDown('Forward', phys);
  }
  releasePending = false;
  rePressPending = false;
}

function handleRelease(api){
  if(hasTimeElapsed(releaseTimer, Number(releaseDelay), api)){
    api.setKeyDown('Forward', false);
    releasePending = false;
    resetTimer('p', api);
    rePressPending = true;
  }
}

function shouldTrigger(){ return Number(chance) >= Math.random()*100.0; }

function onEvent(api, event){
  sync(api);
  var type = event.getType();
  if(type === 14){ // PRE_INTERACT
    if(!event.isActive || !event.isActive()) return; // attack
    var t = api.target();
    if(t == null) return;
    // must be living target (MappedClasses.lG)
    if(t.player === false && !(t.class && (t.class.indexOf('Player')>=0 || t.class.indexOf('Living')>=0 || t.class.indexOf('Entity')>=0))) {
      // allow; many entities living
    }
    if(releasePending || rePressPending) return;
    if(!isTargetVulnerable(api)) return;
    if(shouldTrigger()){
      releasePending = true;
      resetTimer('r', api);
      handleRelease(api);
    }
    return;
  }
  if(type === 0){ // tick
    var p = api.player();
    if(!p || !p.inWorld){ if(releasePending||rePressPending) restoreForwardKey(api); return; }
    // if current screen open -> restore
    // script api lacks direct screen check; best-effort: if not input focus, still run timers? original restores on screen
    if(releasePending){ handleRelease(api); return; }
    if(rePressPending){ handleRePress(api); return; }
  }
}
