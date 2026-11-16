// @slinky {"id":"kill_aura","name":"Kill Aura","description":"Aura with velocity prediction and a configurable click rate",
//          "category":"combat",
//          "settings":[{"key":"range","label":"Range","type":"number","default":3.4,"min":2,"max":4.2,"step":0.1},
//                       {"key":"cps","label":"CPS","type":"number","default":11,"min":4,"max":20,"step":1},
//                       {"key":"smooth","label":"Smooth","type":"number","default":0.55,"min":0.05,"max":1,"step":0.05},
//                       {"key":"lead","label":"Lead","type":"number","default":1,"min":0,"max":2,"step":0.1},
//                       {"key":"fov","label":"FOV","type":"number","default":120,"min":10,"max":360,"step":5},
//                       {"key":"only_sprint","label":"Only Sprinting","type":"boolean","default":false},
//                       {"key":"swing","label":"Swing","type":"boolean","default":true}],
//          "listens":[]}

var target = null;
var lastAttack = 0;
var yaw = 0;
var pitch = 0;
var started = false;

function onEnable(api) {
   target = null;
   lastAttack = 0;
   started = false;
   api.log('aura armed');
}

function onDisable(api) {
   target = null;
}

function onTick(api) {
   var player = api.player();
   if (!player || !player.inWorld) {
      target = null;
      return;
   }

   var found = pick(api, player);
   if (!found) {
      target = null;
      return;
   }

   target = found;
   var aim = predict(api, player, found);
   if (!aim) {
      return;
   }

   var smooth = api.num('smooth');
   yaw = yaw === 0 && !started ? aim.yaw : wrap(yaw + shortest(aim.yaw - yaw) * smooth);
   pitch = pitch + (aim.pitch - pitch) * smooth;
   started = true;
   api.look(yaw, pitch);

   var now = api.now();
   if (now - lastAttack < 1000 / api.num('cps')) {
      return;
   }

   if (found.distance > api.num('range')) {
      return;
   }

   if (api.bool('only_sprint') && !player.sprinting) {
      return;
   }

   lastAttack = now;
   api.attack(found.name);
   if (api.bool('swing')) {
      api.swing();
   }
}

function pick(api, player) {
   var list = api.entities();
   if (!list) {
      return null;
   }

   var best = null;
   var limit = api.num('range');
   for (var i = 0; i < list.length; i++) {
      var row = list[i];
      if (!row || row.player !== true || row.dead === true) {
         continue;
      }

      if ((api.relationshipFlags(row.name) & 7) !== 0) {
         continue;
      }

      if (row.distance > limit) {
         continue;
      }

      if (!best || row.distance < best.distance) {
         best = row;
      }
   }

   return best;
}

function predict(api, player, row) {
   var lead = api.num('lead');
   var tx = row.x + (row.vx || 0) * lead * 0.1;
   var ty = row.y + (row.vy || 0) * lead * 0.1 + 1.62;
   var tz = row.z + (row.vz || 0) * lead * 0.1;
   var eye = player.y + 1.62;
   return angles(player.x, eye, player.z, tx, ty, tz);
}

function angles(fromX, fromY, fromZ, toX, toY, toZ) {
   var dx = toX - fromX;
   var dy = toY - fromY;
   var dz = toZ - fromZ;
   var horizontal = Math.sqrt(dx * dx + dz * dz);
   var targetYaw = wrap(Math.atan2(dz, dx) * 180 / Math.PI - 90);
   var targetPitch = -Math.atan2(dy, horizontal) * 180 / Math.PI;
   return {yaw: targetYaw, pitch: targetPitch};
}

function wrap(value) {
   while (value > 180) {
      value -= 360;
   }

   while (value < -180) {
      value += 360;
   }

   return value;
}

function shortest(delta) {
   while (delta > 180) {
      delta -= 360;
   }

   while (delta < -180) {
      delta += 360;
   }

   return delta;
}
