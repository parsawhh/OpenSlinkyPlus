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
      return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
   }

   public boolean bool(String key) {
      Object value = this.setting(key);
      return value instanceof Boolean ? (Boolean)value : false;
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

   public boolean attack(String targetName) {
      return this.game.attack(targetName);
   }

   public boolean say(String message) {
      return this.game.say(message);
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
      RotationService.clearManual();
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
      return yaw instanceof Number ? ((Number)yaw).doubleValue() : 0.0;
   }

   public double currentPitch() {
      Map<String, Object> player = this.player();
      Object pitch = player.get("pitch");
      return pitch instanceof Number ? ((Number)pitch).doubleValue() : 0.0;
   }

   public boolean claimRotation(int priority) {
      return RotationService.claim(priority);
   }

   public double noise(double value) {
      return AimNoise.sample((float)value);
   }

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

   public Map<String, Object> snapshot() {
      Map<String, Object> result = new HashMap<>();
      result.put("id", this.getId());
      result.put("name", this.getName());
      result.put("enabled", this.isEnabled());
      result.put("tick", this.getTick());
      result.put("player", this.player());
      return result;
   }
}
