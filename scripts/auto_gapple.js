// @slinky {"id":"auto_gapple","name":"Auto Gapple","description":"Eats a golden apple when you are low and nobody is hitting you",
//          "category":"utility",
//          "settings":[{"key":"health","label":"Health","type":"number","default":16,"min":1,"max":20,"step":1},
//                       {"key":"refill","label":"Refill","type":"boolean","default":true},
//                       {"key":"safe_range","label":"Safe Range","type":"number","default":4,"min":0,"max":12,"step":0.5}],
//          "listens":[]}

var used = 0;

function onEnable(api) {
   used = 0;
   api.log('auto gapple armed');
}

function onTick(api) {
   var player = api.player();
   if (!player || !player.inWorld) {
      return;
   }

   if (player.health > api.num('health')) {
      return;
   }

   if (api.num('safe_range') > 0 && enemyClose(api) <= api.num('safe_range')) {
      return;
   }

   var slot = findApple(api);
   if (slot < 0) {
      if (api.bool('refill')) {
         api.log('no golden apple left');
      }

      return;
   }

   api.selectSlot(slot);
   api.useItem();
   used++;
   api.log('ate a golden apple (' + used + ')');
}

function enemyClose(api) {
   var list = api.entities();
   if (!list) {
      return 999;
   }

   var best = 999;
   for (var i = 0; i < list.length; i++) {
      var row = list[i];
      if (row && row.player === true && row.dead !== true && row.distance < best) {
         best = row.distance;
      }
   }

   return best;
}

function findApple(api) {
   for (var slot = 0; slot < 9; slot++) {
      var item = api.hotbar(slot);
      if (item && !item.empty && (item.id === 396 || item.id === 397)) {
         return slot;
      }
   }

   return -1;
}
