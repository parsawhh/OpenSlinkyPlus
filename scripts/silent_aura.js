// @slinky {"id":"silent_aura","name":"SilentAura","description":"Vape v4 SilentAura - silent rotation, prioritized target selection, randomized click rate",
//          "category":"combat",
//          "settings":[{"key":"target_mode","label":"Target Mode","type":"choice","default":0,"options":["Distance","Angle","Health"]},
//                       {"key":"target_area","label":"Target Area","type":"choice","default":0,"options":["Center","Closest"]},
//                       {"key":"switch","label":"Switch","type":"boolean","default":false},
//                       {"key":"range","label":"Range","type":"number","default":3.0,"min":3,"max":4.2,"step":0.05},
//                       {"key":"cps","label":"Attacks Per Second","type":"number","default":10,"min":1,"max":20,"step":1},
//                       {"key":"max_angle","label":"Max Angle","type":"number","default":120,"min":5,"max":360,"step":5},
//                       {"key":"aim_speed","label":"Aim Speed","type":"number","default":7,"min":1,"max":10,"step":0.5},
//                       {"key":"require_mouse_down","label":"Require Mouse Down","type":"boolean","default":false}],
//          "listens":[]}

var ROTATION_PRIORITY = 5;
var ANGLE_READY = 3.0;
var targetId = null;
var aimYaw = NaN;
var aimPitch = NaN;
var lastAttack = 0;

function onEnable(api) {
   reset();
}

function onDisable(api) {
   reset();
   if (api.clearRotation) {
      api.clearRotation();
   }
}

function reset() {
   targetId = null;
   aimYaw = NaN;
   aimPitch = NaN;
}

function wrap(angle) {
   angle = (angle + 180) % 360;
   if (angle < 0) {
      angle += 360;
   }
   return angle - 180;
}

function clamp(value, min, max) {
   return value < min ? min : (value > max ? max : value);
}

function playerEye(player) {
   return player.y + (typeof player.eyeHeight === 'number' ? player.eyeHeight : 1.62);
}

function aimPoint(player, target) {
   var height = typeof target.height === 'number' ? target.height : 1.8;
   var half = (typeof target.width === 'number' ? target.width : 0.6) / 2.0;
   var x = target.x;
   var z = target.z;
   if ((apiNumTargetArea === 1)) {
      var eyeY = playerEye(player);
      x = clamp(player.x, target.x - half, target.x + half);
      z = clamp(player.z, target.z - half, target.z + half);
      return {x: x, y: clamp(eyeY, target.y, target.y + height), z: z};
   }
   var eyeY = playerEye(player);
   var y = eyeY < target.y ? target.y : Math.min(eyeY, target.y + height) - 0.275;
   return {x: x, y: y, z: z};
}

var apiNumTargetArea = 0;

function anglesTo(player, point) {
   var dx = point.x - player.x;
   var dy = point.y - playerEye(player);
   var dz = point.z - player.z;
   var horizontal = Math.sqrt(dx * dx + dz * dz);
   return {
      yaw: wrap(Math.atan2(dz, dx) * 180 / Math.PI - 90),
      pitch: -Math.atan2(dy, horizontal) * 180 / Math.PI
   };
}

function angleDistance(player, point) {
   var wanted = anglesTo(player, point);
   var dyaw = Math.abs(wrap(wanted.yaw - player.yaw));
   var dpitch = Math.abs(wanted.pitch - player.pitch);
   return Math.sqrt(dyaw * dyaw + dpitch * dpitch);
}

function rangeTo(player, point) {
   var dx = point.x - player.x;
   var dy = point.y - player.y;
   var dz = point.z - player.z;
   return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function isValid(api, player, row) {
   if (!row || row.dead === true) {
      return false;
   }
   if (row.id === player.id) {
      return false;
   }
   if (typeof row.health === 'number' && row.health <= 0) {
      return false;
   }
   if (row.health === undefined || row.health === null) {
      return false;
   }
   if ((row.flags & 4) !== 0) {
      return false;
   }
   var point = aimPoint(player, row);
   if (rangeTo(player, point) > api.num('range')) {
      return false;
   }
   return angleDistance(player, point) <= api.num('max_angle') / 2.0;
}

function pick(api, player) {
   var list = api.entities();
   if (!list) {
      return null;
   }
   var candidates = [];
   for (var i = 0; i < list.length; i++) {
      var row = list[i];
      if (isValid(api, player, row)) {
         candidates.push(row);
      }
   }
   if (candidates.length === 0) {
      return null;
   }

   var mode = api.num('target_mode') | 0;
   candidates.sort(function (a, b) {
      if (mode === 1) {
         return angleDistance(player, aimPoint(player, a)) - angleDistance(player, aimPoint(player, b));
      }
      if (mode === 2) {
         return a.health - b.health;
      }
      return a.distance - b.distance;
   });
   if (api.bool('switch')) {
      candidates.sort(function (a, b) {
         return a.id - b.id;
      });
   }
   return candidates[0];
}

function onTick(api) {
   var player = api.player();
   if (!player || !player.inWorld) {
      reset();
      return;
   }
   if (api.bool('require_mouse_down') && !api.keyDown('Attack')) {
      reset();
      return;
   }

   apiNumTargetArea = api.num('target_area') | 0;
   var target = pick(api, player);
   if (!target) {
      reset();
      return;
   }

   targetId = target.id;
   var point = aimPoint(player, target);
   var wanted = anglesTo(player, point);
   if (isNaN(aimYaw)) {
      aimYaw = player.yaw;
      aimPitch = player.pitch;
   }

   var step = Math.max(1.0, api.num('aim_speed') * 1.6) * (0.8 + Math.random() * 0.4);
   var yawDelta = wrap(wanted.yaw - aimYaw);
   aimYaw = wrap(aimYaw + clamp(yawDelta, -step, step));
   aimPitch = clamp(aimPitch + clamp(wanted.pitch - aimPitch, -step, step), -90, 90);

   if (api.claimRotation) {
      api.claimRotation(ROTATION_PRIORITY);
   }
   api.look(aimYaw, aimPitch);

   var aimNow = {yaw: aimYaw, pitch: aimPitch};
   var angular = Math.sqrt(Math.pow(wrap(wanted.yaw - aimNow.yaw), 2) + Math.pow(wanted.pitch - aimNow.pitch, 2));
   if (angular >= ANGLE_READY || rangeTo(player, point) > api.num('range')) {
      return;
   }

   var interval = 1000.0 / Math.max(1.0, api.num('cps'));
   interval *= 0.85 + Math.random() * 0.3;
   var now = api.now();
   if (now - lastAttack < interval) {
      return;
   }
   lastAttack = now;

   var ref = api.entityById(target.id);
   if (!ref) {
      return;
   }
   api.swing();
   api.attackEntity(ref);
}
