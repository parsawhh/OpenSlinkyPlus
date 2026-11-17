// Local Node simulation of the Rhino smoke tests (no Minecraft/JDK needed).
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const DIR = path.join(__dirname, '..', 'scripts');
let failures = 0;

function check(label, expected, actual) {
  const ok = expected === null ? actual === null : expected === actual;
  if (!ok) failures++;
  console.log((ok ? '  ok   ' : '  FAIL ') + label + ' expected=' + expected + ' actual=' + actual);
}

function makeApi(file) {
  const source = fs.readFileSync(path.join(DIR, file), 'utf8');
  const lines = source.split('\n');
  const header = [];
  let started = false;
  for (const line of lines) {
    const t = line.trim();
    if (!started && t.indexOf('// @slinky') === 0) started = true;
    if (!started) continue;
    if (t.indexOf('//') !== 0) break;
    header.push(t.replace(/^\/\/\s?/, ''));
  }
  header[0] = header[0].replace(/^@slinky\s*/, '');
  const manifest = JSON.parse(header.join(''));

  const api = {
    settings: {}, playerState: {}, blocks: {}, hotbar: {}, modules: {},
    moduleSettings: {}, moduleEnabled: {}, ents: [], placements: [], looks: [],
    attacks: [], logs: [], clock: 1000, selectedSlot: -1, used: false, sprinting: false,
    screenOpenFlag: false, heldStack: null, keyState: {}, sprintPackets: [], sentPackets: [],
    isEnabled() { return true; },
    player() { return this.playerState; },
    blockAt(x, y, z) { return this.blocks[x + ',' + y + ',' + z] || { air: true }; },
    hotbar(slot) { return this.hotbar[slot] || { empty: true }; },
    heldItem() { return this.heldStack || { empty: true }; },
    holdingBlock() { return true; },
    selectSlot(slot) { this.selectedSlot = slot; },
    heldSlot() { return this.selectedSlot; },
    placeBlock(x, y, z, face) {
      this.placements.push({ x, y, z, face });
      this.blocks[x + ',' + y + ',' + z] = { air: false, id: 1, meta: 0 };
      return true;
    },
    look(yaw, pitch) { this.looks.push(yaw); this.playerState.yaw = yaw; this.playerState.pitch = pitch; },
    now() { return this.clock; },
    log(m) { this.logs.push(m); },
    setting(k) { return this.settings[k]; },
    num(k) { const v = this.settings[k]; return typeof v === 'number' ? v : 0; },
    bool(k) { return this.settings[k] === true; },
    entitiesList() { return this.ents; },
    entities() { return this.ents; },
    relationshipFlags() { return 0; },
    moduleSetting(id, key) { return this.moduleSettings[id + '.' + key]; },
    setModuleSetting(id, key, value) { this.moduleSettings[id + '.' + key] = String(value); return true; },
    setModuleEnabled(id, on) { this.moduleEnabled[id] = on; return true; },
    moduleEnabled(id) { return this.moduleEnabled[id] === true; },
    modules() { return ['reach', 'autoclicker', 'silent_aura']; },
    claimRotation() { return true; },
    clearRotation() {},
    keyDown() { return true; },
    physicalKeyDown() { return true; },
    setKeyDown(b, d) { this.keyState[b] = d; return true; },
    screenOpen() { return this.screenOpenFlag; },
    entityById(id) { return this.ents.find(e => e.id === id) || null; },
    attackEntity(ref) { this.attacks.push(ref && ref.name != null ? ref.name : String(ref)); return true; },
    attack(name) { this.attacks.push(name); return true; },
    swing() { return true; },
    message(t) { this.logs.push('chat:' + t); return true; },
    say(t) { this.logs.push('say:' + t); return true; },
    useItem() { this.used = true; return true; },
    sendSprintPacket(start) { this.sprintPackets.push(start); return true; },
    setSprint(v) { this.sprinting = v; return true; },
    newPacket(name) { return { packet: name }; },
    sendPacket(p) { this.sentPackets.push(p); return true; },
    target() { return this.ents.length ? this.ents[0] : null; },
    draw() { return null; },
    ref(o) { return o && typeof o.call === 'function' ? o : { call(m) { if (m.indexOf('UnformattedText') >= 0 || m.indexOf('FormattedText') >= 0) return o.text; if (m === 'getMessage') return o; return o[m]; } }; },
    mc() { return null; },
    rawPlayer() { return null; },
    getTick() { return Math.floor(this.clock / 50); }
  };

  const rows = manifest.settings || [];
  for (const row of rows) {
    api.settings[row.key] = row.type === 'boolean' ? (row.default === true)
      : row.type === 'number' ? (typeof row.default === 'number' ? row.default : row.min || 0)
      : (typeof row.default === 'number' ? row.default : 0);
  }

  const sandbox = { api, Math, Date, console, JSON };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: file });
  return {
    api,
    call(name, event) {
      const fn = sandbox[name];
      if (typeof fn === 'function') return event === undefined ? fn(api) : fn(api, event);
    }
  };
}

function event(type) {
  return {
    type,
    active: true,
    cancelled: false,
    dispatchMode: 0,
    remapMovement: false,
    forward: 0,
    sideways: 0,
    factor: 1,
    packetClassName: null,
    packetData: null,
    getType() { return this.type; },
    isActive() { return this.active; },
    cancel() { this.cancelled = true; },
    setCancelled(v) { this.cancelled = v; },
    getDispatchMode() { return this.dispatchMode; },
    setDispatchMode(m) { this.dispatchMode = m; },
    isRemapMovement() { return this.remapMovement; },
    setRemapMovement(v) { this.remapMovement = v; },
    packetClass() { return this.packetClassName; },
    getPacket() { return this.packetData; },
    packetRef() {
      const data = this.packetData || {};
      return {
        data,
        get(f) { return this.data[f]; },
        call(m) {
          if (m === 'getChatComponent') return { text: (this.data.component || {}).text };
          if (m === 'getMessage') return this;
          if (m === 'getEntityId') return this.data.entityId;
          if (m.indexOf('UnformattedText') >= 0 || m.indexOf('FormattedText') >= 0) return this.data.text;
          return this.data[m];
        }
      };
    },
    getRenderWidth() { return 854; },
    getRenderHeight() { return 480; }
  };
}

// --- bob aim ---
{
  const { api, call } = makeApi('bow_aim.js');
  Object.assign(api.playerState, { inWorld: true, x: 0, y: 64, z: 0, yaw: 0, pitch: 0, eyeHeight: 1.62, health: 20, usingItem: true, itemInUseDuration: 20 });
  api.heldStack = { id: 261, name: 'Bow', class: 'ItemBow', empty: false };
  api.ents.push({ id: 7, name: 'target', player: true, distance: 10, dead: false, health: 20, flags: 0, x: 0, y: 64, z: 10, height: 1.8, width: 0.6, eyeHeight: 1.62, vx: 0, vy: 0, vz: 0 });
  call('onEnable');
  for (let i = 0; i < 25; i++) { api.clock += 50; call('onEvent', event(0)); }
  check('bow aim rotates toward the target', true, api.looks.length > 0);
}

// --- auto bed def ---
{
  const { api, call } = makeApi('auto_bed_def.js');
  Object.assign(api.playerState, { inWorld: true, x: 0, y: 64, z: 0, yaw: 0, pitch: 0, eyeHeight: 1.62, health: 20 });
  api.hotbar[3] = { id: 35, name: 'Wool', empty: false };
  api.heldStack = { id: 35, name: 'Wool', class: 'ItemBlock', empty: false };
  api.blocks['2,64,0'] = { air: false, id: 26, meta: 0 };
  api.blocks['2,64,1'] = { air: false, id: 26, meta: 8 };
  api.blocks['3,63,0'] = { air: false, id: 1, meta: 0 };
  call('onEnable');
  api.clock += 100;
  call('onTick');
  check('bed def selects the wool slot', 3, api.selectedSlot);
  check('bed def places a block', true, api.placements.length > 0);
  check('bed def places at the first defense position', '3,64,0', api.placements.length ? api.placements[0].x + ',' + api.placements[0].y + ',' + api.placements[0].z : null);
  check('bed def clicks the supporting face', 'UP', api.placements.length ? api.placements[0].face : null);
}

// --- auto login ---
{
  const { api, call } = makeApi('auto_login.js');
  api.playerState.inWorld = true;
  call('onEnable');
  const prompt = event(2);
  prompt.packetClassName = 'net.minecraft.network.play.server.S02PacketChat';
  prompt.packetData = { component: { text: 'Please /register to play' } };
  call('onEvent', prompt);
  check('auto login queues on the register prompt', true, api.logs.join('|').indexOf('Queueing register') >= 0);
  api.clock += 1000;
  call('onEvent', event(0));
  check('auto login sends the register command', true, api.logs.join('|').indexOf('say:/register axolotlaxolotl axolotlaxolotl') >= 0);
}

// --- legit reach ---
{
  const { api, call } = makeApi('legit_reach.js');
  Object.assign(api.playerState, { inWorld: true, x: 0, y: 64, z: 0, swinging: true, health: 20 });
  api.ents.push({ id: 7, name: 'target', player: true, distance: 4, dead: false, health: 20, flags: 0, x: 0, y: 64, z: 4, height: 1.8, width: 0.6 });
  call('onEnable');
  call('onTick');
  let e = event(2);
  e.packetClassName = 'net.minecraft.network.play.server.S18PacketEntityTeleport';
  e.packetData = { entityId: 7, posX: 0, posY: 2048, posZ: 160 };
  call('onEvent', e);
  call('onTick');
  e = event(2);
  e.packetClassName = 'net.minecraft.network.play.server.S14PacketEntity';
  e.packetData = { entityId: 7, posX: 0, posY: 0, posZ: 0 };
  call('onEvent', e);
  check('legit reach holds delayed entity packets', 1, e.dispatchMode);
}

// --- lag break ---
{
  const { api, call } = makeApi('lag_break.js');
  Object.assign(api.playerState, { inWorld: true, moveForward: 1, ticksExisted: 100, health: 20 });
  api.ents.push({ id: 7, name: 'target', player: true, distance: 3, dead: false, health: 20, flags: 0, x: 0, y: 64, z: 3 });
  call('onEnable');
  api.playerState.ticksExisted = 101;
  call('onEvent', event(21));
  const out = event(3);
  out.packetClassName = 'net.minecraft.network.play.client.C02PacketUseEntity';
  out.packetData = { action: 'ATTACK' };
  call('onEvent', out);
  check('lag break holds outbound packets while blinking', 1, out.dispatchMode);
}

// --- wtap ---
{
  const { api, call } = makeApi('wtap.js');
  Object.assign(api.playerState, { inWorld: true, health: 20 });
  api.settings.chance = 100;
  api.settings.release_delay = 0;
  api.settings.repress_delay = 0;
  api.ents.push({ id: 7, name: 'target', player: true, distance: 2, dead: false, health: 20, hurtTime: 0, flags: 0 });
  call('onEnable');
  call('onEvent', event(14));
  check('wtap releases forward', false, api.keyState.Forward);
  api.clock += 100;
  call('onEvent', event(0));
  check('wtap re-presses forward', true, api.keyState.Forward);
}

// --- sprint reset ---
{
  const { api, call } = makeApi('sprint_reset.js');
  Object.assign(api.playerState, { inWorld: true, onGround: true, sprinting: true, moveForward: 1, x: 0, y: 64, z: 0, health: 20 });
  api.ents.push({ id: 7, name: 'target', player: true, distance: 3, dead: false, health: 20, flags: 0 });
  call('onEnable');
  call('onEvent', event(14));
  check('sprint reset stops sprinting', true, api.sprintPackets.indexOf(false) >= 0);
}

// --- hit select ---
{
  const { api, call } = makeApi('hitselect.js');
  Object.assign(api.playerState, { inWorld: true, onGround: false, hurtTime: 0, fallDistance: 0, health: 20 });
  api.settings.mode = 1;
  api.ents.push({ id: 7, name: 'target', player: true, distance: 3, dead: false, health: 20, hurtTime: 0, flags: 0 });
  call('onEnable');
  const attack = event(3);
  attack.packetClassName = 'net.minecraft.network.play.client.C02PacketUseEntity';
  attack.packetData = { action: 'ATTACK', entityId: 7 };
  call('onEvent', attack);
  check('hit select blocks the non-crit hit', 2, attack.dispatchMode);
  check('hit select enables keep sprint', true, api.moduleEnabled.keepsprint);
}

// --- anti fireball ---
{
  const { api, call } = makeApi('anti_fireball.js');
  Object.assign(api.playerState, { inWorld: true, x: 0, y: 64, z: 0, yaw: 0, pitch: 0, eyeHeight: 1.62, health: 20 });
  api.ents.push({ id: 9, name: 'fireball', class: 'net.minecraft.entity.projectile.EntityLargeFireball', type: 'EntityLargeFireball', distance: 4, dead: false, x: 0, y: 64, z: 4, height: 1 });
  call('onEnable');
  call('onTick');
  call('onEvent', event(13));
  check('anti fireball aims at the fireball', true, api.looks.length > 0);
  check('anti fireball attacks the fireball', true, api.attacks.length > 0);
}

// --- piercing ---
{
  const { api, call } = makeApi('piercing.js');
  Object.assign(api.playerState, { inWorld: true, x: 0, y: 64, z: 0, yaw: 0, pitch: 0, eyeHeight: 1.62, health: 20 });
  api.ents.push({ id: 7, name: 'target', player: true, distance: 2, dead: false, health: 20, flags: 0, x: 0, y: 64, z: 2, height: 1.8, width: 0.6, hurtTime: 0 });
  call('onEnable');
  const attack = event(14);
  call('onEvent', attack);
  check('piercing cancels the vanilla interaction', true, attack.cancelled);
  check('piercing attacks through the block', true, api.attacks.length > 0);
}

if (failures === 0) console.log('js sim: OK');
else { console.log('js sim: ' + failures + ' FAILURES'); process.exit(1); }
