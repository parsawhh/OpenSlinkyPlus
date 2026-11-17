// @slinky {"id":"rise_anticheat","name":"Cheat Detector (Rise)","description":"Port of the Rise 6.9.5 CheatDetector: speed, flight, tower, reach checks with violation buffers and alerts",
//          "category":"utility",
//          "settings":[{"key":"notify_chat","label":"Chat Alerts","type":"boolean","default":true},
//                       {"key":"min_violations","label":"Min Violations","type":"number","default":2,"min":1,"max":20,"step":1},
//                       {"key":"alert_cooldown","label":"Alert Cooldown (ms)","type":"number","default":3000,"min":0,"max":30000,"step":250},
//                       {"key":"track_range","label":"Track Range","type":"number","default":48,"min":8,"max":128,"step":1},
//                       {"key":"ignore_friends","label":"Ignore Friends","type":"boolean","default":true},
//                       {"key":"speed_check","label":"Speed Check","type":"boolean","default":true},
//                       {"key":"speed_limit","label":"Speed Limit (b/t)","type":"number","default":0.42,"min":0.2,"max":2,"step":0.01},
//                       {"key":"speed_buffer","label":"Speed Buffer","type":"number","default":6,"min":1,"max":30,"step":0.5},
//                       {"key":"flight_check","label":"Flight Check","type":"boolean","default":true},
//                       {"key":"flight_ticks","label":"Flight Ticks","type":"number","default":20,"min":5,"max":80,"step":1},
//                       {"key":"tower_check","label":"Tower Check","type":"boolean","default":true},
//                       {"key":"reach_check","label":"Reach Check","type":"boolean","default":true},
//                       {"key":"reach_limit","label":"Reach Limit","type":"number","default":3.1,"min":2.5,"max":6,"step":0.05}],
//          "listens":[]}

// Port of com.alan.clients.anticheat (Rise 6.9.5). The packet driven checks are rewritten on top of
// the client entity snapshots: SpeedLimit keeps the base speed + buffer model, FlightPrediction is
// reduced to a hover/ascent watchdog, TowerWatchdog keeps the impossible motion signature and a
// server side reach check is added because the reach packet data is not exposed to scripts.

var tracked = {};
var violations = {};
var lastHurt = 0;

function onEnable(api) {
   tracked = {};
   violations = {};
   lastHurt = 0;
   api.log('cheat detector armed');
}

function onDisable(api) {
   tracked = {};
   violations = {};
}

function onTick(api) {
   var player = api.player();
   if (!player || !player.inWorld) {
      return;
   }

   var list = api.entities();
   var seen = {};
   var range = api.num('track_range');

   for (var i = 0; i < list.length; i++) {
      var entity = list[i];
      if (!entity || entity.error || !entity.player) {
         continue;
      }

      var name = entity.name || '?';
      if (Number(entity.distance) > range) {
         delete tracked[name];
         continue;
      }

      if (api.bool('ignore_friends') && api.relationshipFlags(name) !== 0) {
         continue;
      }

      seen[name] = true;
      var state = tracked[name];
      if (!state) {
         tracked[name] = {x: entity.x, y: entity.y, z: entity.z, buffer: 0, hover: 0, rise: 0};
         continue;
      }

      var dx = entity.x - state.x;
      var dy = entity.y - state.y;
      var dz = entity.z - state.z;
      state.x = entity.x;
      state.y = entity.y;
      state.z = entity.z;

      inspect(api, name, state, dx, dy, dz, supported(api, entity));
   }

   for (var known in tracked) {
      if (!seen[known]) {
         delete tracked[known];
      }
   }

   var hurt = Number(player.hurtTime || 0);
   if (hurt > lastHurt) {
      reach(api, player, list);
   }

   lastHurt = hurt;
}

function inspect(api, name, state, dx, dy, dz, support) {
   var horizontal = Math.sqrt(dx * dx + dz * dz);

   if (api.bool('speed_check')) {
      var limit = api.num('speed_limit');
      if (!support) {
         limit *= 1.15;
      }

      if (horizontal > limit) {
         state.buffer += horizontal - limit;
      } else {
         state.buffer = Math.max(0, state.buffer - 0.2);
      }

      if (state.buffer > api.num('speed_buffer')) {
         state.buffer = 0;
         flag(api, name, 'Speed', 'Limit', 'moved ' + horizontal.toFixed(3) + ' blocks');
      }
   }

   if (api.bool('tower_check') && Math.abs(dy - 0.65625) < 0.0001 && Math.abs(horizontal - 0.75) < 0.001) {
      flag(api, name, 'Tower', 'Watchdog', 'impossible motion');
   }

   if (api.bool('flight_check')) {
      if (!support && Math.abs(dy) < 0.0001) {
         state.hover++;
      } else {
         state.hover = 0;
      }

      if (!support && dy > 0.01) {
         state.rise++;
      } else {
         state.rise = 0;
      }

      if (state.hover > api.num('flight_ticks')) {
         state.hover = 0;
         flag(api, name, 'Flight', 'Prediction', 'hovering');
      }

      if (state.rise > api.num('flight_ticks')) {
         state.rise = 0;
         flag(api, name, 'Flight', 'Prediction', 'ascending');
      }
   }
}

function reach(api, player, list) {
   if (!api.bool('reach_check')) {
      return;
   }

   var best = null;
   var distance = Infinity;
   for (var i = 0; i < list.length; i++) {
      var entity = list[i];
      if (!entity || entity.error || !entity.player || entity.name === player.name) {
         continue;
      }

      if (api.bool('ignore_friends') && api.relationshipFlags(entity.name) !== 0) {
         continue;
      }

      if (Number(entity.distance) < distance) {
         distance = Number(entity.distance);
         best = entity;
      }
   }

   if (best && distance > api.num('reach_limit')) {
      flag(api, best.name, 'Reach', 'Distance', distance.toFixed(2) + ' blocks');
   }
}

function supported(api, entity) {
   var x = Math.floor(entity.x);
   var y = Math.floor(entity.y);
   var z = Math.floor(entity.z);
   var below = api.blockAt(x, y - 1, z);
   var lower = api.blockAt(x, y - 2, z);
   var solid = (below && below.air === false) || (lower && lower.air === false);
   if (solid) {
      return true;
   }

   var name = String((below && below.name) || '').toLowerCase();
   return name.indexOf('ladder') >= 0 || name.indexOf('vine') >= 0 || name.indexOf('water') >= 0 || name.indexOf('web') >= 0;
}

function flag(api, name, check, type, detail) {
   var key = name + '.' + check + '.' + type;
   var entry = violations[key];
   if (!entry) {
      entry = {count: 0, last: 0};
      violations[key] = entry;
   }

   entry.count++;
   var now = api.now();
   if (entry.count < api.num('min_violations')) {
      return;
   }

   if (now - entry.last < api.num('alert_cooldown')) {
      return;
   }

   entry.last = now;
   var text = '[Detector] ' + name + ' failed ' + check + ' (' + type + ') VL ' + entry.count + ' ' + detail;
   api.log(text);
   if (api.bool('notify_chat')) {
      api.message('\u00a7c' + text);
   }
}
