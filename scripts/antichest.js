// @slinky {"id":"antichest","name":"AntiCheat","description":"Reads how opponents are fighting and adapts your reach, cps or SilentAura to match them",
//          "category":"combat",
//          "settings":[{"key":"reach_threshold","label":"Reach Threshold","type":"number","default":3.9,"min":3,"max":6,"step":0.1},
//                       {"key":"cps_threshold","label":"CPS Threshold","type":"number","default":11,"min":5,"max":25,"step":1},
//                       {"key":"window","label":"CPS Window (ms)","type":"number","default":4000,"min":1000,"max":15000,"step":500},
//                       {"key":"flag_memory","label":"Flag Memory (ms)","type":"number","default":15000,"min":2000,"max":60000,"step":1000},
//                       {"key":"engage_range","label":"Engage Range","type":"number","default":6,"min":2,"max":12,"step":0.5},
//                       {"key":"restore_delay","label":"Restore Delay (ms)","type":"number","default":2500,"min":500,"max":10000,"step":250},
//                       {"key":"mitigate_reach","label":"Mitigate Reach","type":"boolean","default":true},
//                       {"key":"mitigate_cps","label":"Mitigate CPS","type":"boolean","default":true},
//                       {"key":"mitigate_silent_aura","label":"Mitigate With SilentAura","type":"boolean","default":false},
//                       {"key":"boost_reach","label":"Boost Reach","type":"number","default":3.5,"min":3,"max":4.2,"step":0.05},
//                       {"key":"boost_cps","label":"Boost CPS","type":"number","default":13,"min":5,"max":25,"step":1},
//                       {"key":"aura_range","label":"Aura Range","type":"number","default":3.6,"min":3,"max":4.2,"step":0.05},
//                       {"key":"aura_cps","label":"Aura CPS","type":"number","default":16,"min":5,"max":20,"step":1},
//                       {"key":"escalate","label":"Escalate On New Flags","type":"boolean","default":false},
//                       {"key":"reach_step","label":"Reach Step","type":"number","default":0.1,"min":0.05,"max":0.5,"step":0.05},
//                       {"key":"cps_step","label":"CPS Step","type":"number","default":1,"min":1,"max":5,"step":1}],
//          "listens":[]}

var lastHealth = 0;
var lastSeen = 0;
var hits = [];
var cheaters = {};
var boosted = false;
var escalation = 0;
var saved = null;

function onEnable(api) {
   lastHealth = 0;
   lastSeen = 0;
   hits = [];
   cheaters = {};
   boosted = false;
   escalation = 0;
   saved = null;
   api.log('antichest watching');
}

function onDisable(api) {
   restore(api);
}

function onTick(api) {
   var player = api.player();
   if (!player || !player.inWorld) {
      restore(api);
      return;
   }

   var now = api.now();
   detect(api, player, now);
   prune(api, now);

   var opponent = closest(api);
   var engaged = opponent !== null && opponent.distance <= api.num('engage_range');
   if (engaged && hasCheater(api, now)) {
      boost(api);
   } else if (!engaged && boosted && now - lastSeen > api.num('restore_delay')) {
      restore(api);
   }
}

function hasCheater(api, now) {
   var memory = api.num('flag_memory');
   for (var name in cheaters) {
      if (!cheaters.hasOwnProperty(name)) {
         continue;
      }
      var record = cheaters[name];
      if ((record.reach || record.clicker) && now - record.lastFlag <= memory) {
         return true;
      }
   }
   return false;
}

function detect(api, player, now) {
   var health = typeof player.health === 'number' ? player.health : 0;
   if (lastHealth > 0 && health < lastHealth) {
      var opponent = closest(api);
      var distance = opponent ? opponent.distance : 0;
      hits.push({time: now, distance: distance});
      lastSeen = now;

      var name = opponent ? opponent.name : 'unknown';
      var record = cheaters[name];
      if (!record) {
         record = {name: name, hits: 0, maxDistance: 0, reach: false, clicker: false, lastFlag: 0};
         cheaters[name] = record;
      }

      record.hits++;
      if (distance > record.maxDistance) {
         record.maxDistance = distance;
      }

      if (record.maxDistance > api.num('reach_threshold')) {
         record.reach = true;
      }

      var recent = 0;
      for (var i = 0; i < hits.length; i++) {
         if (now - hits[i].time <= 1000) {
            recent++;
         }
      }
      if (recent > api.num('cps_threshold')) {
         record.clicker = true;
      }

      if (record.reach || record.clicker) {
         var fresh = record.lastFlag === 0 || now - record.lastFlag > 1000;
         record.lastFlag = now;
         if (fresh) {
            api.log(name + ' flagged: reach=' + record.maxDistance.toFixed(2) + ' hits/s=' + recent
               + (record.reach ? ' (reach)' : '') + (record.clicker ? ' (autoclicker)' : ''));
         }
      }
   }

   lastHealth = health;
}

function prune(api, now) {
   var window = api.num('window');
   while (hits.length > 0 && now - hits[0].time > window) {
      hits.shift();
   }
}

function closest(api) {
   var best = null;
   var list = api.entities();
   if (!list) {
      return null;
   }

   for (var i = 0; i < list.length; i++) {
      var row = list[i];
      if (!row || row.player !== true || row.dead === true) {
         continue;
      }
      if ((api.relationshipFlags(row.name) & 7) !== 0) {
         continue;
      }
      if (!best || row.distance < best.distance) {
         best = row;
      }
   }

   return best;
}

function boost(api) {
   if (!saved) {
      saved = {
         reach: capture(api, 'reach', ['distance']),
         cps: capture(api, 'autoclicker', ['target_cps']),
         aura: capture(api, 'silent_aura', ['range', 'cps'])
      };
      escalation = 1;
   } else if (api.bool('escalate')) {
      escalation++;
   }

   var reach = api.num('boost_reach') + (escalation - 1) * api.num('reach_step');
   var cps = api.num('boost_cps') + (escalation - 1) * api.num('cps_step');

   if (api.bool('mitigate_reach') && saved.reach.exists) {
      api.setModuleSetting('reach', 'distance', Math.min(4.2, reach));
      api.setModuleEnabled('reach', true);
   }
   if (api.bool('mitigate_cps') && saved.cps.exists) {
      api.setModuleSetting('autoclicker', 'target_cps', Math.min(25, cps));
      api.setModuleEnabled('autoclicker', true);
   }
   if (api.bool('mitigate_silent_aura') && saved.aura.exists) {
      api.setModuleSetting('silent_aura', 'range', api.num('aura_range'));
      api.setModuleSetting('silent_aura', 'cps', api.num('aura_cps'));
      api.setModuleEnabled('silent_aura', true);
   }

   boosted = true;
   api.log('mitigation level ' + escalation + ' reach=' + Math.min(4.2, reach).toFixed(2)
      + ' cps=' + Math.min(25, cps) + (api.bool('mitigate_silent_aura') ? ' aura on' : ''));
}

function capture(api, id, keys) {
   var values = {};
   for (var i = 0; i < keys.length; i++) {
      values[keys[i]] = api.moduleSetting(id, keys[i]);
   }
   return {exists: !!(api.modules && api.modules().indexOf(id) >= 0), enabled: api.moduleEnabled(id), values: values};
}

function restore(api) {
   if (!boosted || !saved) {
      boosted = false;
      saved = null;
      escalation = 0;
      return;
   }

   restoreModule(api, 'reach', saved.reach, ['distance']);
   restoreModule(api, 'autoclicker', saved.cps, ['target_cps']);
   restoreModule(api, 'silent_aura', saved.aura, ['range', 'cps']);

   api.log('mitigation restored');
   boosted = false;
   saved = null;
   escalation = 0;
}

function restoreModule(api, id, state, keys) {
   if (!state || !state.exists) {
      return;
   }
   for (var i = 0; i < keys.length; i++) {
      var value = state.values[keys[i]];
      if (value !== null && value !== undefined) {
         api.setModuleSetting(id, keys[i], value);
      }
   }
   if (state.enabled === false) {
      api.setModuleEnabled(id, false);
   }
}
