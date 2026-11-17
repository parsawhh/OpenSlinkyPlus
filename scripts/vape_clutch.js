// @slinky {"id":"vape_clutch","name":"Clutch (Vape)","description":"Port of the Vape v4 clutch: path finding clutch placement with ladder catch and movement steering",
//          "category":"utility",
//          "settings":[{"key":"on_void","label":"On Void","type":"boolean","default":true},
//                       {"key":"on_lethal_fall","label":"On Lethal Fall","type":"boolean","default":true},
//                       {"key":"on_more_than_x","label":"On More Than X Blocks","type":"boolean","default":false},
//                       {"key":"blocks_threshold","label":"Blocks","type":"number","default":3,"min":3,"max":10,"step":1},
//                       {"key":"speed","label":"Speed","type":"number","default":1,"min":1,"max":10,"step":0.1},
//                       {"key":"silent_aim","label":"Silent Aim","type":"boolean","default":false},
//                       {"key":"reset_angle","label":"Reset Angle","type":"boolean","default":true},
//                       {"key":"reset_angle_delay","label":"Reset Angle Delay","type":"number","default":250,"min":0,"max":2000,"step":50},
//                       {"key":"return_to_last_slot","label":"Return To Last Slot","type":"boolean","default":true},
//                       {"key":"return_delay","label":"Return Delay","type":"number","default":300,"min":0,"max":3000,"step":50},
//                       {"key":"clutch_move_delay","label":"Clutch Move Delay","type":"number","default":150,"min":0,"max":2000,"step":50},
//                       {"key":"fail_delay","label":"Fail Delay","type":"number","default":100,"min":0,"max":500,"step":50},
//                       {"key":"limit_blocks","label":"Limit Blocks","type":"boolean","default":false},
//                       {"key":"max_blocks","label":"Max Blocks","type":"number","default":5,"min":1,"max":10,"step":1},
//                       {"key":"blacklist","label":"Blacklist","type":"boolean","default":true},
//                       {"key":"held_whitelist","label":"Held Whitelist","type":"boolean","default":false},
//                       {"key":"allow_staircase_up","label":"Allow Staircase Up","type":"boolean","default":true},
//                       {"key":"extra_depth","label":"Extra Depth","type":"number","default":2,"min":0,"max":6,"step":1},
//                       {"key":"rotation_threshold","label":"Rotation Threshold","type":"number","default":12,"min":1,"max":90,"step":1},
//                       {"key":"auto_ladder","label":"Auto Ladder","type":"boolean","default":true},
//                       {"key":"ladder_catch","label":"Ladder Catch","type":"boolean","default":true},
//                       {"key":"ladder_side_entry","label":"Ladder Side Entry","type":"number","default":0.28,"min":0.05,"max":0.9,"step":0.01},
//                       {"key":"catch_radius","label":"Catch Radius","type":"number","default":0.67,"min":0.1,"max":2,"step":0.01},
//                       {"key":"catch_cell_inset","label":"Catch Cell Inset","type":"number","default":0.02,"min":0,"max":0.4,"step":0.01},
//                       {"key":"simulation_ticks","label":"Simulation Ticks","type":"number","default":20,"min":4,"max":40,"step":1},
//                       {"key":"extension_blocks","label":"Max Extension Blocks","type":"number","default":6,"min":0,"max":12,"step":1},
//                       {"key":"movement_steering","label":"Movement Steering","type":"boolean","default":true},
//                       {"key":"centering_deadzone","label":"Centering Deadzone","type":"number","default":0.03,"min":0,"max":0.5,"step":0.005},
//                       {"key":"axis_press_threshold","label":"Axis Press Threshold","type":"number","default":0.5,"min":0.05,"max":1,"step":0.05},
//                       {"key":"air_decay","label":"Air Decay","type":"number","default":0.91,"min":0.5,"max":1,"step":0.005},
//                       {"key":"ground_decay","label":"Ground Decay","type":"number","default":0.546,"min":0.1,"max":1,"step":0.005},
//                       {"key":"ladder_thickness","label":"Ladder Thickness","type":"number","default":0.125,"min":0.05,"max":0.5,"step":0.0025},
//                       {"key":"counter_motion","label":"Counter Motion","type":"boolean","default":true},
//                       {"key":"steer_speed","label":"Steer Speed","type":"number","default":1,"min":0.1,"max":3,"step":0.1}],
//          "listens":[21]}

// Port of gg.vape.module.blatant.Clutch (vape v4) with the ladder planner, the movement steering
// and the auto ladder. The path planner, the path scoring, the fall triggers, the ladder catch
// sampling, the analytic centering and the auto ladder modes follow the original.

var GRID = 0;
var LADDER_ID = 139;
var HORIZONTAL_FACINGS = ['NORTH', 'SOUTH', 'EAST', 'WEST'];

var plan = null;
var planIndex = 0;
var placed = 0;
var failUntil = 0;
var savedYaw = null;
var savedPitch = null;
var savedSlot = -1;
var resetAt = 0;
var candidates = [];
var ladderPlan = null;
var ladderPlaced = false;
var ladderEngaged = false;
var steering = null;
var movement = {forward: 0, backward: 0, left: 0, right: 0};
var physical = {forward: 0, backward: 0, left: 0, right: 0};
var catchTick = -1;

function onEnable(api) {
   clear();
   api.log('vape clutch armed');
}

function onDisable(api) {
   clear();
}

function clear() {
   plan = null;
   planIndex = 0;
   placed = 0;
   candidates = [];
   ladderPlan = null;
   ladderPlaced = false;
   ladderEngaged = false;
   steering = null;
   savedYaw = null;
   savedPitch = null;
   savedSlot = -1;
   catchTick = -1;
}

function onEvent(api, event) {
   if (event.getType() !== 21) {
      return;
   }

   physical.forward = event.forward;
   physical.backward = -event.forward;
   physical.left = -event.sideways;
   physical.right = event.sideways;

   if (!api.bool('movement_steering') || steering === null) {
      return;
   }

   var input = steering;
   var forward = 0;
   var backward = 0;
   var left = 0;
   var right = 0;
   if (input.forward) {
      forward = 1;
   }

   if (input.backward) {
      backward = 1;
   }

   if (input.left) {
      left = 1;
   }

   if (input.right) {
      right = 1;
   }

   var speed = api.num('steer_speed');
   event.forward = (forward - backward) * speed;
   event.sideways = (right - left) * speed;
   event.factor = event.factor;
}

function onTick(api) {
   var player = api.player();
   if (!player || !player.inWorld) {
      clear();
      return;
   }

   var now = api.now();

   if (plan === null && ladderPlan === null) {
      if (now < failUntil) {
         return;
      }

      if (!shouldClutch(api, player)) {
         return;
      }

      var slot = findBlockSlot(api);
      if (slot < 0) {
         return;
      }

      var landing = findLandingBlock(api, player);
      candidates = candidatePositions(player, landing);

      if (api.bool('auto_ladder') && api.bool('ladder_catch')) {
         ladderPlan = planLadder(api, player, landing);
      }

      if (ladderPlan === null) {
         var path = search(api, player, landing);
         if (path === null || path.length === 0) {
            failUntil = now + api.num('fail_delay');
            api.log('no clutch path found');
            return;
         }

         if (api.bool('limit_blocks') && path.length > api.num('max_blocks')) {
            failUntil = now + api.num('fail_delay');
            api.log('clutch needs ' + path.length + ' blocks, limit is ' + api.num('max_blocks'));
            return;
         }

         plan = path;
         planIndex = 0;
         placed = 0;
         savedYaw = player.yaw;
         savedPitch = player.pitch;
         savedSlot = api.heldSlot();
         api.selectSlot(slot);
         api.log('clutch path planned, ' + path.length + ' blocks');
         return;
      }

      plan = ladderPlan.targets;
      planIndex = 0;
      placed = 0;
      savedYaw = player.yaw;
      savedPitch = player.pitch;
      savedSlot = api.heldSlot();
      api.selectSlot(ladderPlan.blockSlot >= 0 ? ladderPlan.blockSlot : slot);
      api.log('ladder catch planned at ' + ladderPlan.x.toFixed(2) + ', ' + ladderPlan.z.toFixed(2));
   }

   if (ladderPlan !== null) {
      runLadder(api, player);
      return;
   }

   if (planIndex >= plan.length) {
      finish(api, now);
      return;
   }

   var target = plan[planIndex];
   var angles = faceAngles(player, target);
   var yawDelta = Math.abs(shortest(angles.yaw - player.yaw));
   var pitchDelta = Math.abs(angles.pitch - player.pitch);

   if (yawDelta > api.num('rotation_threshold') || pitchDelta > api.num('rotation_threshold')) {
      api.look(angles.yaw, angles.pitch);
      return;
   }

   api.placeBlock(target.x, target.y, target.z, target.face);
   placed++;
   planIndex++;
}

function finish(api, now) {
   if (api.bool('reset_angle') && savedYaw !== null && savedYaw !== undefined && !api.bool('silent_aim')) {
      resetAt = now + api.num('reset_angle_delay');
   }

   if (api.bool('return_to_last_slot') && savedSlot >= 0) {
      api.selectSlot(savedSlot);
   }

   api.log('clutch finished with ' + placed + ' blocks');
   if (resetAt > 0) {
      api.look(savedYaw, savedPitch);
   }

   clear();
}

function shouldClutch(api, player) {
   if (player.onGround) {
      return false;
   }

   if (!api.bool('on_void') && !api.bool('on_lethal_fall') && !api.bool('on_more_than_x')) {
      return false;
   }

   var landing = findLandingBlock(api, player);
   var intoVoid = false;
   var lethal = false;
   var tooMany = false;

   if (landing === null) {
      intoVoid = api.bool('on_void');
   } else {
      if (api.bool('on_lethal_fall')) {
         lethal = (player.y - landing.y - 3.0) > (player.health || 0);
      }

      if (api.bool('on_more_than_x')) {
         tooMany = (player.y - (landing.y + 1)) >= api.num('blocks_threshold');
      }
   }

   return intoVoid || lethal || tooMany;
}

function findLandingBlock(api, player) {
   var x = Math.floor(player.x);
   var z = Math.floor(player.z);
   var top = Math.floor(player.y);

   for (var y = top; y > top - 50; y--) {
      var block = api.blockAt(x, y, z);
      if (block && !block.air && block.id !== 0) {
         return {x: x, y: y, z: z};
      }
   }

   return null;
}

function candidatePositions(player, landing) {
   var result = [];
   if (landing === null) {
      result.push({x: player.x, y: player.y, z: player.z});
      return result;
   }

   result.push({x: landing.x + 0.5, y: landing.y + 1.0, z: landing.z + 0.5});
   return result;
}

function findBlockSlot(api) {
   if (api.bool('held_whitelist')) {
      var held = api.heldSlot();
      var item = held >= 0 ? api.hotbar(held) : null;
      if (item && !item.empty && isPlaceable(api, item)) {
         return held;
      }

      return -1;
   }

   for (var slot = 0; slot < 9; slot++) {
      var stack = api.hotbar(slot);
      if (stack && !stack.empty && isPlaceable(api, stack)) {
         return slot;
      }
   }

   return -1;
}

function findLadderSlot(api) {
   for (var slot = 0; slot < 9; slot++) {
      var stack = api.hotbar(slot);
      if (stack && !stack.empty && stack.id === LADDER_ID) {
         return slot;
      }
   }

   return -1;
}

function isPlaceable(api, item) {
   if (!item || item.empty || item.id <= 0 || item.id >= 256) {
      return false;
   }

   if (api.bool('blacklist')) {
      var name = String(item.name || '').toLowerCase();
      if (name.indexOf('bedrock') >= 0 || name.indexOf('water') >= 0 || name.indexOf('lava') >= 0) {
         return false;
      }
   }

   return true;
}

function search(api, player, landing) {
   var start = findSupport(api, player);
   if (start === null || landing === null) {
      return null;
   }

   var destination = {x: landing.x, y: landing.y + 1, z: landing.z};
   var maxDepth = Math.abs(start.x - destination.x) + Math.abs(start.y - destination.y) + Math.abs(start.z - destination.z)
      + api.num('extra_depth');
   var memo = {};
   return descend(api, player, start, destination, 0, [], maxDepth, memo);
}

function findSupport(api, player) {
   var x = Math.floor(player.x);
   var z = Math.floor(player.z);
   var y = Math.floor(player.y) - 1;
   var around = [
      {x: x, y: y, z: z},
      {x: x + 1, y: y, z: z},
      {x: x - 1, y: y, z: z},
      {x: x, y: y, z: z + 1},
      {x: x, y: y, z: z - 1},
      {x: x, y: y + 1, z: z}
   ];

   for (var i = 0; i < around.length; i++) {
      var block = api.blockAt(around[i].x, around[i].y, around[i].z);
      if (block && !block.air && block.id !== 0) {
         return {x: around[i].x, y: around[i].y, z: around[i].z};
      }
   }

   for (var step = 1; step < 4; step++) {
      var below = api.blockAt(x, y - step, z);
      if (below && !below.air && below.id !== 0) {
         return {x: x, y: y - step, z: z};
      }
   }

   return null;
}

function descend(api, player, current, destination, depth, path, maxDepth, memo) {
   if (depth > maxDepth) {
      return null;
   }

   var key = current.x + ',' + current.y + ',' + current.z;
   if (memo[key] !== undefined) {
      return memo[key];
   }

   var directions = [];
   var dx = destination.x - current.x;
   var dy = destination.y - current.y;
   var dz = destination.z - current.z;
   if (dx > 0) {
      directions.push('EAST');
   } else if (dx < 0) {
      directions.push('WEST');
   }

   if (dy > 0) {
      directions.push('UP');
   } else if (dy < 0) {
      directions.push('DOWN');
   }

   if (dz > 0) {
      directions.push('SOUTH');
   } else if (dz < 0) {
      directions.push('NORTH');
   }

   var best = null;
   var bestScore = Number.MAX_SAFE_INTEGER;

   for (var i = 0; i < directions.length; i++) {
      var face = directions[i];
      var placedBlock = offset(current, face);
      if (!isFree(api, placedBlock)) {
         continue;
      }

      var step = {x: placedBlock.x, y: placedBlock.y, z: placedBlock.z, face: face, support: current};
      var candidate = path.slice();
      candidate.push(step);

      var next = placedBlock;
      if (next.x === destination.x && next.y === destination.y && next.z === destination.z) {
         best = candidate;
         bestScore = scorePath(candidate);
         break;
      }

      var recursive = descend(api, player, next, destination, depth + 1, candidate, maxDepth, memo);
      var evaluated = recursive !== null && recursive.length > 0 ? recursive : candidate;
      if (evaluated.length === 0) {
         break;
      }

      var score = scorePath(evaluated);
      if (best === null || score < bestScore) {
         best = evaluated;
         bestScore = score;
      }
   }

   memo[key] = best;
   return best;
}

function scorePath(path) {
   if (!path || path.length === 0) {
      return Number.MAX_SAFE_INTEGER;
   }

   var remaining = path.slice();
   var reached = false;
   var score = path.length * 100;

   for (var i = 0; i < candidates.length; i++) {
      if (remaining.length === 0) {
         break;
      }

      var target = remaining[remaining.length - 1];
      if (faceVisible(candidates[i], target)) {
         remaining.pop();
         reached = true;
      } else {
         score += 100000;
      }

      var dx = candidates[i].x - (target.x + 0.5);
      var dy = 0.0;
      var dz = candidates[i].z - (target.z + 0.5);
      score += Math.sqrt(dx * dx + dy * dy + dz * dz) * 50000;
   }

   score += remaining.length * 100000;
   if (!reached) {
      score = Number.MAX_SAFE_INTEGER;
   }

   return score;
}

function faceVisible(eye, target) {
   var x = target.x;
   var y = target.y;
   var z = target.z;
   if (target.face === 'UP') {
      y += 1;
   } else if (target.face === 'DOWN') {
      y -= 1;
   } else if (target.face === 'NORTH') {
      z += 1;
   } else if (target.face === 'SOUTH') {
      z -= 1;
   } else if (target.face === 'EAST') {
      x += 1;
   } else if (target.face === 'WEST') {
      x -= 1;
   }

   var dx = x + 0.5 - eye.x;
   var dy = y + 0.5 - eye.y;
   var dz = z + 0.5 - eye.z;
   var length = Math.sqrt(dx * dx + dy * dy + dz * dz);
   if (length < 0.0001) {
      return true;
   }

   var steps = Math.ceil(length * 3);
   for (var i = 1; i < steps; i++) {
      var t = i / steps;
      var bx = Math.floor(eye.x + dx * t);
      var by = Math.floor(eye.y + dy * t);
      var bz = Math.floor(eye.z + dz * t);
      if (bx === target.x && by === target.y && bz === target.z) {
         continue;
      }

      if (bx === x && by === y && bz === z) {
         return true;
      }
   }

   return false;
}

function isFree(api, position) {
   var block = api.blockAt(position.x, position.y, position.z);
   return block === null || block.air === true || block.id === 0;
}

function offset(block, face) {
   if (face === 'UP') {
      return {x: block.x, y: block.y + 1, z: block.z};
   }

   if (face === 'DOWN') {
      return {x: block.x, y: block.y - 1, z: block.z};
   }

   if (face === 'NORTH') {
      return {x: block.x, y: block.y, z: block.z + 1};
   }

   if (face === 'SOUTH') {
      return {x: block.x, y: block.y, z: block.z - 1};
   }

   if (face === 'EAST') {
      return {x: block.x + 1, y: block.y, z: block.z};
   }

   return {x: block.x - 1, y: block.y, z: block.z};
}

function opposite(face) {
   if (face === 'UP') {
      return 'DOWN';
   }

   if (face === 'DOWN') {
      return 'UP';
   }

   if (face === 'NORTH') {
      return 'SOUTH';
   }

   if (face === 'SOUTH') {
      return 'NORTH';
   }

   if (face === 'EAST') {
      return 'WEST';
   }

   return 'EAST';
}

function faceAngles(player, target) {
   var eyeY = player.y + 1.62;
   var x = target.x + 0.5;
   var y = target.y + 0.5;
   var z = target.z + 0.5;
   if (target.face === 'UP') {
      y = target.y + 1.0;
   } else if (target.face === 'DOWN') {
      y = target.y;
   } else if (target.face === 'NORTH') {
      z = target.z + 1.0;
   } else if (target.face === 'SOUTH') {
      z = target.z;
   } else if (target.face === 'EAST') {
      x = target.x + 1.0;
   } else if (target.face === 'WEST') {
      x = target.x;
   }

   var dx = x - player.x;
   var dy = y - eyeY;
   var dz = z - player.z;
   var horizontal = Math.sqrt(dx * dx + dz * dz);
   return {
      yaw: wrap(Math.atan2(dz, dx) * 180 / Math.PI - 90),
      pitch: -Math.atan2(dy, horizontal) * 180 / Math.PI
   };
}

// ------------------------------------------------------------------ ladder planner

function simulateTrajectory(api, player) {
   var points = [];
   var x = player.x;
   var y = player.y;
   var z = player.z;
   var vx = player.vx || 0.0;
   var vy = player.vy || 0.0;
   var vz = player.vz || 0.0;
   var ticks = api.num('simulation_ticks');
   var speed = Math.sqrt((physical.forward || 0) * (physical.forward || 0) + (physical.right || 0) * (physical.right || 0));

   for (var tick = 0; tick <= ticks; tick++) {
      var onGround = isOnGround(api, x, y, z);
      points.push({tick: tick, x: x, y: y, z: z, motionX: vx, motionY: vy, motionZ: vz, onGround: onGround});
      if (tick > 0 && onGround) {
         break;
      }

      if (y <= 0) {
         break;
      }

      var decay = onGround ? 0.546 : 0.91;
      vx *= decay;
      vz *= decay;
      vy = (vy - 0.08) * 0.98;
      x += vx;
      y += vy;
      z += vz;
      if (speed > 0.0 && !onGround) {
         x += (physical.right || 0) * 0.02;
         z += (physical.forward || 0) * 0.02;
      }
   }

   return points;
}

function isOnGround(api, x, y, z) {
   var block = api.blockAt(Math.floor(x), Math.floor(y) - 1, Math.floor(z));
   return block !== null && !block.air && block.id !== 0;
}

function findCatchSamples(api, trajectory) {
   var samples = [];
   var entry = api.num('ladder_side_entry');
   var last = trajectory[trajectory.length - 1];
   var minimumY = last.onGround ? Math.floor(last.y + 0.0001) : 0;

   for (var index = 0; index < trajectory.length; index++) {
      var point = trajectory[index];
      var ladderY = Math.floor(point.y + 0.0001);
      var sideEntryY = ladderY + 1.0 - entry;
      if (index === 0) {
         if (!point.onGround && point.motionY < 0.0 && ladderY >= minimumY && point.y <= sideEntryY + 0.0001) {
            samples.push({point: point, tick: point.tick, ladderY: ladderY});
         }

         continue;
      }

      var previous = trajectory[index - 1];
      if (point.y >= previous.y || (previous.motionY >= 0.0 && point.motionY >= 0.0)) {
         continue;
      }

      var highest = Math.floor(previous.y - 1.0 + entry + 0.0001);
      var lowest = Math.max(minimumY, Math.floor(point.y - 1.0 + entry + 0.0001) + 1);
      for (var ladder = highest; ladder >= lowest; ladder--) {
         var entryY = ladder + 1.0 - entry;
         if (previous.y < entryY || point.y >= entryY) {
            continue;
         }

         samples.push({point: interpolateAtY(previous, point, entryY), tick: previous.tick, ladderY: ladder});
      }

      var side = Math.floor(point.y + 0.0001);
      var sideEntry = side + 1.0 - entry;
      if (!point.onGround && point.motionY < 0.0 && side >= minimumY && point.y >= side && point.y <= sideEntry + 0.0001) {
         samples.push({point: point, tick: point.tick, ladderY: side});
      }
   }

   return samples;
}

function interpolateAtY(previous, current, y) {
   var span = current.y - previous.y;
   var t = span === 0.0 ? 0.0 : (y - previous.y) / span;
   return {
      tick: previous.tick,
      x: previous.x + (current.x - previous.x) * t,
      y: y,
      z: previous.z + (current.z - previous.z) * t,
      motionX: previous.motionX,
      motionY: previous.motionY,
      motionZ: previous.motionZ,
      onGround: false
   };
}

function planLadder(api, player, landing) {
   var trajectory = simulateTrajectory(api, player);
   if (trajectory.length === 0) {
      return null;
   }

   var samples = findCatchSamples(api, trajectory);
   if (samples.length === 0) {
      return null;
   }

   var radius = api.num('catch_radius');
   var inset = api.num('catch_cell_inset');
   var best = null;
   var bestScore = Number.MAX_SAFE_INTEGER;
   var ladderSlot = findLadderSlot(api);

   for (var s = 0; s < samples.length; s++) {
      var sample = samples[s];
      var baseX = Math.floor(sample.point.x);
      var baseZ = Math.floor(sample.point.z);

      for (var dx = -1; dx <= 1; dx++) {
         for (var dz = -1; dz <= 1; dz++) {
            var ladderX = baseX + dx;
            var ladderZ = baseZ + dz;
            var ladderY = sample.ladderY;

            for (var f = 0; f < HORIZONTAL_FACINGS.length; f++) {
               var facing = HORIZONTAL_FACINGS[f];
               var catchX = ladderX + 0.5;
               var catchZ = ladderZ + 0.5;
               var movementError = Math.sqrt((catchX - sample.point.x) * (catchX - sample.point.x)
                  + (catchZ - sample.point.z) * (catchZ - sample.point.z));
               if (movementError > radius) {
                  continue;
               }

               var support = offset({x: ladderX, y: ladderY, z: ladderZ}, opposite(facing));
               if (!isSolid(api, support)) {
                  continue;
               }

               if (!isFree(api, {x: ladderX, y: ladderY, z: ladderZ})) {
                  continue;
               }

               var extension = extensionPath(api, {x: ladderX, y: ladderY, z: ladderZ}, support);
               var score = movementError * 1000.0 + extension.length * 100.0 + Math.abs(sample.ladderY - Math.floor(player.y));
               if (score >= bestScore) {
                  continue;
               }

               bestScore = score;
               best = {
                  x: catchX,
                  z: catchZ,
                  y: ladderY,
                  facing: facing,
                  ladderBlock: {x: ladderX, y: ladderY, z: ladderZ},
                  support: support,
                  targets: extension,
                  tick: sample.tick,
                  mode: ladderSlot >= 0 ? 'DIRECT' : 'BUILD_SUPPORT',
                  blockSlot: ladderSlot >= 0 ? ladderSlot : findBlockSlot(api)
               };
            }
         }
      }
   }

   if (best === null) {
      return null;
   }

   best.score = bestScore;
   return best;
}

function extensionPath(api, ladderBlock, support) {
   var path = [];
   var current = {x: support.x, y: support.y, z: support.z};
   var limit = api.num('extension_blocks');

   while (current.x !== ladderBlock.x || current.y !== ladderBlock.y || current.z !== ladderBlock.z) {
      if (path.length >= limit) {
         return [];
      }

      var dx = ladderBlock.x - current.x;
      var dy = ladderBlock.y - current.y;
      var dz = ladderBlock.z - current.z;
      var face = null;
      if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) {
         face = dx > 0 ? 'EAST' : 'WEST';
      } else if (Math.abs(dz) >= Math.abs(dy)) {
         face = dz > 0 ? 'SOUTH' : 'NORTH';
      } else {
         face = dy > 0 ? 'UP' : 'DOWN';
      }

      var next = offset(current, face);
      if (!isFree(api, next)) {
         return [];
      }

      path.push({x: next.x, y: next.y, z: next.z, face: face, support: current});
      current = next;
   }

   return path;
}

function isSolid(api, position) {
   var block = api.blockAt(position.x, position.y, position.z);
   return block !== null && !block.air && block.id !== 0;
}

function runLadder(api, player) {
   var ladder = ladderPlan;

   if (ladderEngaged) {
      if (player.onGround) {
         finish(api, api.now());
         return;
      }
      steering = centeringInput(api, player, ladder);
      return;
   }

   if (!ladderPlaced) {
      if (ladder.targets.length > 0) {
         var step = ladder.targets[planIndex];
         var angles = faceAngles(player, step);
         if (Math.abs(shortest(angles.yaw - player.yaw)) > api.num('rotation_threshold')
            || Math.abs(angles.pitch - player.pitch) > api.num('rotation_threshold')) {
            api.look(angles.yaw, angles.pitch);
            return;
         }

         api.placeBlock(step.x, step.y, step.z, step.face);
         placed++;
         planIndex++;
         if (planIndex >= ladder.targets.length) {
            ladderPlaced = true;
         }

         return;
      }

      ladderPlaced = true;
   }

   var ladderSlot = findLadderSlot(api);
   if (ladderSlot >= 0) {
      api.selectSlot(ladderSlot);
      api.placeBlock(ladder.ladderBlock.x, ladder.ladderBlock.y, ladder.ladderBlock.z, ladder.facing);
      api.swing();
      api.log('ladder placed at ' + ladder.ladderBlock.x + ',' + ladder.ladderBlock.y + ',' + ladder.ladderBlock.z);
   }

   steering = centeringInput(api, player, ladder);
   catchTick = ladder.tick;
   ladderEngaged = true;
   api.log('ladder catch engaged');
}

function centeringInput(api, player, ladder) {
   var deadzone = api.num('centering_deadzone');
   var threshold = api.num('axis_press_threshold');
   var decay = player.onGround ? api.num('ground_decay') : api.num('air_decay');
   var drift = decay * (1.0 + decay);
   var speed = Math.sqrt((physical.forward || 0) * (physical.forward || 0) + (physical.right || 0) * (physical.right || 0));

   var residualX = ladder.x - player.x;
   var residualZ = ladder.z - player.z;
   residualX -= speed * drift;
   residualZ -= speed * drift;
   var magnitude = Math.sqrt(residualX * residualX + residualZ * residualZ);

   var maintain = magnitude < deadzone && maintainsLadderPress(api, player, ladder);
   if (magnitude < deadzone && !maintain) {
      return {forward: false, backward: false, left: false, right: false};
   }

   var unitX;
   var unitZ;
   if (magnitude < 1.0E-9) {
      var bounds = ladderBounds(api, ladder);
      unitX = bounds.centerX - player.x;
      unitZ = bounds.centerZ - player.z;
      var length = Math.sqrt(unitX * unitX + unitZ * unitZ);
      if (length < 1.0E-9) {
         return {forward: false, backward: false, left: false, right: false};
      }

      unitX /= length;
      unitZ /= length;
   } else {
      unitX = residualX / magnitude;
      unitZ = residualZ / magnitude;
   }

   var yaw = player.yaw * Math.PI / 180.0;
   var sinYaw = Math.sin(yaw);
   var cosYaw = Math.cos(yaw);
   var forwardProjection = unitX * (-sinYaw) + unitZ * cosYaw;
   var rightProjection = unitX * (-cosYaw) + unitZ * (-sinYaw);
   return {
      forward: forwardProjection > threshold,
      backward: forwardProjection < -threshold,
      left: rightProjection < -threshold,
      right: rightProjection > threshold
   };
}

function maintainsLadderPress(api, player, ladder) {
   var bounds = ladderBounds(api, ladder);
   var maxX = player.maxX === undefined ? player.x + 0.3 : player.maxX;
   var minX = player.minX === undefined ? player.x - 0.3 : player.minX;
   var maxZ = player.maxZ === undefined ? player.z + 0.3 : player.maxZ;
   var minZ = player.minZ === undefined ? player.z - 0.3 : player.minZ;

   if (maxX > bounds.minX && minX < bounds.maxX && maxZ > bounds.minZ && minZ < bounds.maxZ) {
      return true;
   }

   var thickness = api.num('ladder_thickness');
   var contactReach = api.num('centering_deadzone') + (maxX - minX) / 2.0;
   var contactDepthReach = api.num('centering_deadzone') + (maxZ - minZ) / 2.0;
   if (ladder.facing === 'EAST') {
      return ladder.x < ladder.ladderBlock.x + thickness + contactReach;
   }

   if (ladder.facing === 'WEST') {
      return ladder.x > ladder.ladderBlock.x + 1.0 - thickness - contactReach;
   }

   if (ladder.facing === 'SOUTH') {
      return ladder.z < ladder.ladderBlock.z + thickness + contactDepthReach;
   }

   if (ladder.facing === 'NORTH') {
      return ladder.z > ladder.ladderBlock.z + 1.0 - thickness - contactDepthReach;
   }

   return false;
}

function ladderBounds(api, ladder) {
   var thickness = api.num('ladder_thickness');
   var minX = ladder.ladderBlock.x;
   var maxX = ladder.ladderBlock.x + 1.0;
   var minZ = ladder.ladderBlock.z;
   var maxZ = ladder.ladderBlock.z + 1.0;
   if (ladder.facing === 'EAST') {
      maxX = minX + thickness;
   } else if (ladder.facing === 'WEST') {
      minX = maxX - thickness;
   } else if (ladder.facing === 'SOUTH') {
      maxZ = minZ + thickness;
   } else if (ladder.facing === 'NORTH') {
      minZ = maxZ - thickness;
   }

   return {
      minX: minX,
      maxX: maxX,
      minZ: minZ,
      maxZ: maxZ,
      centerX: (minX + maxX) / 2.0,
      centerZ: (minZ + maxZ) / 2.0
   };
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
