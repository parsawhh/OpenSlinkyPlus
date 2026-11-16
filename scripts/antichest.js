// @slinky {"id":"antichest","name":"AntiCheat","description":"Reads how opponents are fighting and adapts your reach and cps",
//          "category":"combat",
//          "settings":[{"key":"reach_threshold","label":"Reach Threshold","type":"number","default":3.9,"min":3,"max":6,"step":0.1},
//                       {"key":"cps_threshold","label":"CPS Threshold","type":"number","default":11,"min":5,"max":25,"step":1},
//                       {"key":"boost_reach","label":"Boost Reach","type":"number","default":3.5,"min":3,"max":4.2,"step":0.05},
//                       {"key":"boost_cps","label":"Boost CPS","type":"number","default":13,"min":5,"max":25,"step":1},
//                       {"key":"window","label":"Window (ms)","type":"number","default":4000,"min":1000,"max":15000,"step":500},
//                       {"key":"engage_range","label":"Engage Range","type":"number","default":6,"min":2,"max":12,"step":0.5},
//                       {"key":"restore_delay","label":"Restore Delay (ms)","type":"number","default":2500,"min":500,"max":10000,"step":250}],
//          "listens":[]}

var lastHealth = 0;
var lastSeen = 0;
var hits = [];
var cheaters = {};
var boosted = false;
var savedReach = null;
var savedCps = null;
var restored = false;

function onEnable(api) {
   lastHealth = 0;
   lastSeen = 0;
   hits = [];
   cheaters = {};
   boosted = false;
   savedReach = null;
   savedCps = null;
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
   prune(now);

   var opponent = closest(api);
   var engaged = opponent !== null && opponent.distance <= api.num('engage_range');
   if (engaged && !boosted && hasCheater()) {
      boost(api);
   } else if (!engaged && boosted && now - lastSeen > api.num('restore_delay')) {
      restore(api);
   }
}

function hasCheater() {
   for (var name in cheaters) {
      if (cheaters.hasOwnProperty(name) && (cheaters[name].reach || cheaters[name].clicker)) {
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
      hits.push({time: now, distance: distance, name: opponent ? opponent.name : 'unknown'});
      lastSeen = now;

      var record = cheaters[opponent ? opponent.name : 'unknown'];
      if (!record) {
         record = {name: opponent ? opponent.name : 'unknown', hits: 0, maxDistance: 0, reach: false, clicker: false};
         cheaters[record.name] = record;
      }

      record.hits++;
      if (distance > record.maxDistance) {
         record.maxDistance = distance;
      }

      if (record.maxDistance > api.num('reach_threshold')) {
         record.reach = true;
      }

      var window = api.num('window');
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
         api.log(record.name + ' flagged: reach=' + record.maxDistance.toFixed(2) + ' hits/s=' + recent
            + (record.reach ? ' (reach)' : '') + (record.clicker ? ' (autoclicker)' : ''));
      }
   }

   lastHealth = health;
}

function prune(now) {
   var window = 4000;
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
   boosted = true;
   restored = false;
   savedReach = api.moduleSetting('reach', 'distance');
   savedCps = api.moduleSetting('autoclicker', 'target_cps');
   api.setModuleSetting('reach', 'distance', api.num('boost_reach'));
   api.setModuleSetting('autoclicker', 'target_cps', api.num('boost_cps'));
   api.setModuleEnabled('reach', true);
   api.setModuleEnabled('autoclicker', true);
   api.log('boosted reach=' + api.num('boost_reach') + ' cps=' + api.num('boost_cps'));
}

function restore(api) {
   if (!boosted) {
      return;
   }

   boosted = false;
   restored = true;
   if (savedReach !== null && savedReach !== undefined) {
      api.setModuleSetting('reach', 'distance', savedReach);
   }

   if (savedCps !== null && savedCps !== undefined) {
      api.setModuleSetting('autoclicker', 'target_cps', savedCps);
   }

   api.log('restored reach=' + savedReach + ' cps=' + savedCps);
}
