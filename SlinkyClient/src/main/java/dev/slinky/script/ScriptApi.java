package dev.slinky.script;

import dev.slinky.client.rotation.AimNoise;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.Module;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScriptApi {
   private final ScriptModule module;
   private final ScriptRuntime runtime;
   private final ScriptGame game;

   ScriptApi(ScriptModule module, ScriptRuntime runtime, ScriptGame game) {
      this.module = module;
      this.runtime = runtime;
      this.game = game;
   }

   public String getId() {
      return this.module.getId();
   }

   public String getName() {
      return this.module.getName();
   }

   public boolean isEnabled() {
      return this.module.isEnabled();
   }

   public long getTick() {
      return this.runtime.ticks();
   }

   public double now() {
      return System.currentTimeMillis();
   }

   public void log(String message) {
      this.runtime.log(message == null ? "" : message);
   }

   public Object setting(String key) {
      Setting<?> setting = this.module.findSetting(key);
      return setting == null ? null : setting.get();
   }

   public void set(String key, Object value) {
      Setting<?> setting = this.module.findSetting(key);
      if (setting == null) {
         throw new IllegalArgumentException("unknown setting " + key);
      }

      setting.parseAndSet(String.valueOf(value));
      this.runtime.persist();
   }

   public double num(String key) {
      Object value = this.setting(key);
      return value instanceof Number ? ((Number)value).doubleValue() : 0.0D;
   }

   public boolean bool(String key) {
      Object value = this.setting(key);
      return value instanceof Boolean ? ((Boolean)value).booleanValue() : false;
   }

   public String text(String key) {
      Object value = this.setting(key);
      return value == null ? "" : String.valueOf(value);
   }

   public Map<String, Object> player() {
      return this.game.player();
   }

   public List<Map<String, Object>> entities() {
      return this.game.entities();
   }

   public Map<String, Object> target() {
      return this.game.target();
   }

   public Map<String, Object> blockAt(int x, int y, int z) {
      return this.game.blockAt(x, y, z);
   }

   public Map<String, Object> heldItem() {
      return this.game.heldItem();
   }

   public Map<String, Object> hotbar(int slot) {
      return this.game.hotbar(slot);
   }

   public boolean swing() {
      return this.game.swing();
   }

   public boolean placeBlock(int x, int y, int z, String face) {
      return this.game.placeBlock(x, y, z, face);
   }

   public boolean selectSlot(int slot) {
      return this.game.selectSlot(slot);
   }

   public boolean useItem() {
      return this.game.useItem();
   }

   public boolean holdingBlock() {
      return this.game.holdingBlock();
   }

   public int heldSlot() {
      return this.game.heldSlot();
   }

   public boolean keyDown(String binding) {
      return this.game.keyDown(binding);
   }

   public boolean setKeyDown(String binding, boolean down) {
      return this.game.setKeyDown(binding, down);
   }
   public boolean physicalKeyDown(String binding) {
      return this.game.physicalKeyDown(binding);
   }

   public boolean screenOpen() {
      return this.game.screenOpen();
   }

   public boolean attack(String targetName) {
      return this.game.attack(targetName);
   }

   public boolean attackEntity(Object ref) {
      return this.game.attackEntity(raw(ref));
   }

   public boolean say(String message) {
      return this.game.say(message);
   }

   public boolean message(String text) {
      return this.game.message(text);
   }

   public int relationshipFlags(String entityName) {
      return this.game.relationshipFlags(entityName);
   }

   public boolean setFriend(String entityName, boolean friend) {
      return this.game.setFriend(entityName, friend);
   }

   public void look(double yaw, double pitch) {
      this.runtime.retain((float)yaw, (float)pitch);
   }

   public void lookDelta(double yaw, double pitch) {
      this.runtime.rotate((float)yaw, (float)pitch);
   }

   public void lookAt(double x, double y, double z) {
      double[] angles = this.game.angles(x, y, z);
      if (angles != null) {
         this.look(angles[0], angles[1]);
      }
   }

   public void clearRotation() {
      this.runtime.clearRetain();
   }

   public double cameraYaw() {
      return RotationService.cameraYaw();
   }

   public double cameraPitch() {
      return RotationService.cameraPitch();
   }

   public double currentYaw() {
      Map<String, Object> player = this.player();
      Object yaw = player.get("yaw");
      return yaw instanceof Number ? ((Number)yaw).doubleValue() : 0.0D;
   }

   public double currentPitch() {
      Map<String, Object> player = this.player();
      Object pitch = player.get("pitch");
      return pitch instanceof Number ? ((Number)pitch).doubleValue() : 0.0D;
   }

   public boolean claimRotation(int priority) {
      return RotationService.claim(priority);
   }

   public double noise(double value) {
      return AimNoise.sample((float)value);
   }

   // --- raw bridge / reflect ---
   public ScriptRef mc() { return this.game.mcRef(); }
   public ScriptRef rawPlayer() { return this.game.playerRef(); }
   public ScriptRef rawWorld() { return this.game.worldRef(); }
   public ScriptRef entity(String name) { return this.game.entityRef(name); }
   public ScriptRef entityById(int id) { return this.game.entityById(id); }
   public ScriptRef ref(Object o) { return o instanceof ScriptRef ? (ScriptRef)o : ScriptRef.of(raw(o)); }

   // --- packets ---
   public boolean sendPacket(Object packet) { return this.game.sendPacket(raw(packet)); }
   public ScriptRef newPacket(String className) { return this.game.newPacket(className); }
   public ScriptRef newPacket(String className, Object a) { return this.game.newPacket(className, new Object[]{raw(a)}); }
   public ScriptRef newPacket(String className, Object a, Object b) { return this.game.newPacket(className, new Object[]{raw(a), raw(b)}); }
    public ScriptRef newPacket(String className, Object a, Object b, Object c) { return this.game.newPacket(className, new Object[]{raw(a), raw(b), raw(c)}); }
    public ScriptRef newPacket(String className, Object a, Object b, Object c, Object d) { return this.game.newPacket(className, new Object[]{raw(a), raw(b), raw(c), raw(d)}); }
   public boolean sendSprintPacket(boolean start) { return this.game.sendSprintPacket(start); }

   // --- draw ---
   public ScriptDraw draw() { return this.runtime.draw(); }

   // --- files ---
   public String readFile(String path) { return this.game.readFile(path); }
   public boolean writeFile(String path, String content) { return this.game.writeFile(path, content); }
   public List<String> listFiles(String path) { return this.game.listFiles(path); }
   public String dataDir() { return this.game.dataDirPath(); }

   // --- movement / misc ---
   public boolean setSprint(boolean sprinting) { return this.game.setSprint(sprinting); }
   public boolean isSprinting() { return this.game.isSprinting(); }
   public double distanceToEntity(Object ref) { return this.game.distanceToEntity(raw(ref)); }

   public List<String> modules() {
      List<String> result = new ArrayList<String>();

      for (Module module : this.runtime.modules()) {
         result.add(module.getName());
      }

      return result;
   }

   public boolean setModuleEnabled(String id, boolean enabled) {
      Module module = this.runtime.findModule(id);
      if (module == null) {
         return false;
      }

      this.runtime.setModuleEnabled(module, enabled);
      return true;
   }

   public Object moduleSetting(String id, String key) {
      Module module = this.runtime.findModule(id);
      if (module == null) {
         return null;
      }

      Setting<?> setting = module.findSetting(key);
      return setting == null ? null : setting.get();
   }

   public boolean setModuleSetting(String id, String key, Object value) {
      Module module = this.runtime.findModule(id);
      if (module == null) {
         return false;
      }

      Setting<?> setting = module.findSetting(key);
      if (setting == null) {
         return false;
      }

      setting.parseAndSet(String.valueOf(value));
      this.runtime.persist();
      return true;
   }

   public boolean moduleEnabled(String id) {
      Module module = this.runtime.findModule(id);
      return module != null && module.isEnabled();
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> result = new HashMap<String, Object>();
      result.put("id", this.getId());
      result.put("name", this.getName());
      result.put("enabled", this.isEnabled());
      result.put("tick", this.getTick());
      result.put("player", this.player());
      return result;
   }

   private static Object raw(Object o) {
      return o instanceof ScriptRef ? ((ScriptRef)o).raw() : o;
   }
}
