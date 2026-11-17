package dev.slinky.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.slinky.shaded.rhino.Context;
import dev.slinky.shaded.rhino.Function;
import dev.slinky.shaded.rhino.Scriptable;
import dev.slinky.shaded.rhino.ScriptableObject;

/**
 * Runs the script modules against a mock game so their behaviour can be checked without Minecraft.
 *
 *   java -cp ... dev.slinky.script.ScriptModulesSmokeTest <scripts-dir>
 */
public final class ScriptModulesSmokeTest {
   private static int failures;

   public static void main(String[] args) throws Exception {
      Path directory = Paths.get(args.length > 0 ? args[0] : "scripts");
      Context context = Context.enter();
      context.setOptimizationLevel(-1);
      context.setLanguageVersion(Context.VERSION_1_8);
      context.setWrapFactory(ScriptValues.wrapFactory());

      vapeClutch(context, directory.resolve("vape_clutch.js"));
      antichest(context, directory.resolve("antichest.js"));
      killAura(context, directory.resolve("kill_aura.js"));
      silentAura(context, directory.resolve("silent_aura.js"));
      autoGapple(context, directory.resolve("auto_gapple.js"));
      riseAntiCheat(context, directory.resolve("rise_anticheat.js"));
      bowAim(context, directory.resolve("bow_aim.js"));
      autoBedDef(context, directory.resolve("auto_bed_def.js"));
      autoLogin(context, directory.resolve("auto_login.js"));
      legitReach(context, directory.resolve("legit_reach.js"));
      lagBreak(context, directory.resolve("lag_break.js"));
      wtap(context, directory.resolve("wtap.js"));
      sprintReset(context, directory.resolve("sprint_reset.js"));
      hitSelect(context, directory.resolve("hitselect.js"));
      antiFireball(context, directory.resolve("anti_fireball.js"));
      piercing(context, directory.resolve("piercing.js"));

      if (failures == 0) {
         System.out.println("script modules smoke test: OK");
      } else {
         System.out.println("script modules smoke test: " + failures + " FAILURES");
         System.exit(1);
      }
   }

   private static void riseAntiCheat(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 10.0);
      api.playerState.put("y", 70.0);
      api.playerState.put("z", 10.0);
      api.playerState.put("health", 20.0);
      api.playerState.put("hurtTime", 0.0);
      api.settings.put("min_violations", Double.valueOf(1));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);

      Map<String, Object> speeder = map("name", "Speeder", "player", Boolean.TRUE, "distance", Double.valueOf(4.0),
         "x", Double.valueOf(40.0), "y", Double.valueOf(70.0), "z", Double.valueOf(10.0));
      api.entities.add(speeder);

      for (int i = 0; i < 60; i++) {
         speeder.put("x", Double.valueOf(40.0 + i * 1.0));
         api.clock += 50;
         call(scope, "onTick", api);
      }

      check("rise detector flags speed", true, api.logs.toString().contains("failed Speed"));

      api.entities.clear();
      Map<String, Object> flyer = map("name", "Flyer", "player", Boolean.TRUE, "distance", Double.valueOf(6.0),
         "x", Double.valueOf(20.0), "y", Double.valueOf(100.0), "z", Double.valueOf(20.0));
      api.entities.add(flyer);

      for (int i = 0; i < 60; i++) {
         api.clock += 50;
         call(scope, "onTick", api);
      }

      check("rise detector flags flight", true, api.logs.toString().contains("failed Flight"));
      check("rise detector alerts in chat", true, api.logs.toString().contains("chat:"));
   }

   private static void vapeClutch(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("onGround", Boolean.FALSE);
      api.playerState.put("vy", -3.0);
      api.playerState.put("x", 10.3);
      api.playerState.put("y", 70.0);
      api.playerState.put("z", -2.7);
      api.playerState.put("yaw", 0.0);
      api.playerState.put("pitch", 0.0);
      api.playerState.put("health", 20.0);
      api.playerState.put("minX", 9.95);
      api.playerState.put("maxX", 10.65);
      api.playerState.put("minZ", -3.05);
      api.playerState.put("maxZ", -2.35);
      api.hotbar.put(Integer.valueOf(2), map("id", Integer.valueOf(1), "name", "Stone", "empty", Boolean.FALSE));
      api.hotbar.put(Integer.valueOf(4), map("id", Integer.valueOf(139), "name", "Ladder", "empty", Boolean.FALSE));
      api.settings.put("on_more_than_x", Boolean.TRUE);
      api.settings.put("auto_ladder", Boolean.FALSE);

      // the ledge next to the player and the floor it is falling towards
      api.blocks.put("11,69,-3", map("air", Boolean.FALSE, "id", Integer.valueOf(1)));
      api.blocks.put("10,60,-3", map("air", Boolean.FALSE, "id", Integer.valueOf(1)));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      for (int i = 0; i < 40; i++) {
         api.clock += 20;
         call(scope, "onTick", api);
      }

      check("vape clutch plans a path", true, api.logs.toString().contains("clutch path planned"));
      check("vape clutch places blocks", true, api.placements.size() > 0);
      check("vape clutch selects a block slot", 2, api.selectedSlot);
      check("vape clutch places from the ledge", "WEST", api.placements.isEmpty() ? null : api.placements.get(0).get("face"));
      check("vape clutch places the next block down", "DOWN", api.placements.size() < 2 ? null : api.placements.get(1).get("face"));
      check("vape clutch stays inside the block limit", true, api.placements.size() <= 10);

      // the ladder planner and the movement steering
      MockApi ladder = new MockApi(load(file));
      ladder.playerState.put("inWorld", Boolean.TRUE);
      ladder.playerState.put("onGround", Boolean.FALSE);
      ladder.playerState.put("vy", -3.0);
      ladder.playerState.put("x", 10.3);
      ladder.playerState.put("y", 70.0);
      ladder.playerState.put("z", -2.7);
      ladder.playerState.put("yaw", 0.0);
      ladder.playerState.put("pitch", 0.0);
      ladder.playerState.put("health", 20.0);
      ladder.playerState.put("minX", 9.95);
      ladder.playerState.put("maxX", 10.65);
      ladder.playerState.put("minZ", -3.05);
      ladder.playerState.put("maxZ", -2.35);
      ladder.hotbar.put(Integer.valueOf(2), map("id", Integer.valueOf(1), "name", "Stone", "empty", Boolean.FALSE));
      ladder.hotbar.put(Integer.valueOf(4), map("id", Integer.valueOf(139), "name", "Ladder", "empty", Boolean.FALSE));
      ladder.settings.put("on_more_than_x", Boolean.TRUE);
      ladder.settings.put("auto_ladder", Boolean.TRUE);
      ladder.blocks.put("11,69,-3", map("air", Boolean.FALSE, "id", Integer.valueOf(1)));
      ladder.blocks.put("11,66,-3", map("air", Boolean.FALSE, "id", Integer.valueOf(1)));
      ladder.blocks.put("10,60,-3", map("air", Boolean.FALSE, "id", Integer.valueOf(1)));

      Scriptable ladderScope = run(context, file, ladder);
      call(ladderScope, "onEnable", ladder);
      for (int i = 0; i < 40; i++) {
         ladder.clock += 20;
         call(ladderScope, "onTick", ladder);
      }

      check("vape clutch plans a ladder catch", true, ladder.logs.toString().contains("ladder catch planned")
         || ladder.logs.toString().contains("ladder catch engaged"));

      MockEvent event = new MockEvent(21);
      event.forward = 0.0;
      event.sideways = 0.0;
      event.factor = 1.0;
      call(ladderScope, "onEvent", ladder, event);
      check("vape clutch handles the movement event", true, event.forward != 0.0 || event.sideways != 0.0);
   }

   private static void antichest(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("health", 20.0);
      api.entities.add(map("name", "cheater", "player", Boolean.TRUE, "distance", 4.6, "dead", Boolean.FALSE,
         "x", 5.0, "y", 64.0, "z", 0.0));
      api.settings.put("mitigate_silent_aura", Boolean.TRUE);

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      call(scope, "onTick", api);
      check("antichest stays calm while healthy", 0, api.moduleSettings.size());

      api.playerState.put("health", 15.0);
      call(scope, "onTick", api);
      check("antichest reacts to a long hit", true, api.moduleSettings.size() > 0);
      check("antichest boosts reach", "3.5", String.valueOf(api.moduleSettings.get("reach.distance")));
      check("antichest boosts cps", "13.0", String.valueOf(api.moduleSettings.get("autoclicker.target_cps")));
      check("antichest enables reach", Boolean.TRUE, api.moduleEnabled.get("reach"));
      check("antichest boosts silent aura range", "3.6", String.valueOf(api.moduleSettings.get("silent_aura.range")));
      check("antichest boosts silent aura cps", "16.0", String.valueOf(api.moduleSettings.get("silent_aura.cps")));
      check("antichest enables silent aura", Boolean.TRUE, api.moduleEnabled.get("silent_aura"));
      check("antichest logs the detection", true, api.logs.toString().contains("cheater"));
   }

   private static void silentAura(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("yaw", 0.0);
      api.playerState.put("pitch", 0.0);
      api.playerState.put("eyeHeight", 1.62);
      api.playerState.put("health", 20.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 2.4, "dead", Boolean.FALSE, "health", 20.0, "flags", 0,
         "x", 0.0, "y", 64.0, "z", 2.4, "height", 1.8, "width", 0.6,
         "vx", 0.0, "vy", 0.0, "vz", 0.0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      for (int i = 0; i < 20; i++) {
         api.clock += 50;
         call(scope, "onTick", api);
      }

      check("silent aura aims", true, api.looks.size() > 0);
      check("silent aura attacks", true, api.attacks.size() > 0);
      check("silent aura attacks the selected target", "target", api.attacks.isEmpty() ? null : api.attacks.get(0));

      api.entities.clear();
      api.clock += 100;
      call(scope, "onTick", api);
      check("silent aura drops the target", true, api.attacks.size() > 0);
   }

   private static void killAura(Context context, Path file) throws IOException {      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("vx", 0.0);
      api.playerState.put("vy", 0.0);
      api.playerState.put("vz", 0.0);
      api.entities.add(map("name", "target", "player", Boolean.TRUE, "distance", 2.4, "dead", Boolean.FALSE,
         "x", 2.4, "y", 64.0, "z", 0.0, "vx", 0.0, "vy", 0.0, "vz", 0.0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      for (int i = 0; i < 40; i++) {
         api.clock += 25;
         call(scope, "onTick", api);
      }

      check("aura attacks", true, api.attacks.size() > 0);
      check("aura aims", true, api.looks.size() > 0);
      check("aura attacks the right target", "target", api.attacks.isEmpty() ? null : api.attacks.get(0));
   }

   private static void autoGapple(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("health", 12.0);
      api.hotbar.put(Integer.valueOf(3), map("id", Integer.valueOf(396), "name", "Golden Apple", "empty", Boolean.FALSE));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      call(scope, "onTick", api);
      check("auto gapple selects the apple", 3, api.selectedSlot);
      check("auto gapple eats", true, api.used);
      check("auto gapple logs", true, api.logs.toString().contains("golden apple"));
   }

   private static void bowAim(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("yaw", 0.0);
      api.playerState.put("pitch", 0.0);
      api.playerState.put("eyeHeight", 1.62);
      api.playerState.put("health", 20.0);
      api.playerState.put("usingItem", Boolean.TRUE);
      api.playerState.put("itemInUseDuration", 20.0);
      api.heldStack = map("id", Integer.valueOf(261), "name", "Bow", "class", "ItemBow", "empty", Boolean.FALSE);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 10.0, "dead", Boolean.FALSE, "health", 20.0, "flags", 0,
         "x", 0.0, "y", 64.0, "z", 10.0, "height", 1.8, "width", 0.6,
         "eyeHeight", 1.62, "vx", 0.0, "vy", 0.0, "vz", 0.0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      for (int i = 0; i < 25; i++) {
         api.clock += 50;
         call(scope, "onEvent", api, new MockEvent(0));
      }

      check("bow aim rotates toward the target", true, api.looks.size() > 0);
   }

   private static void autoBedDef(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("yaw", 0.0);
      api.playerState.put("pitch", 0.0);
      api.playerState.put("eyeHeight", 1.62);
      api.playerState.put("health", 20.0);
      api.hotbar.put(Integer.valueOf(3), map("id", Integer.valueOf(35), "name", "Wool", "empty", Boolean.FALSE));
      api.heldStack = map("id", Integer.valueOf(35), "name", "Wool", "class", "ItemBlock", "empty", Boolean.FALSE);
      api.blocks.put("2,64,0", map("air", Boolean.FALSE, "id", Integer.valueOf(26), "meta", Integer.valueOf(0)));
      api.blocks.put("2,64,1", map("air", Boolean.FALSE, "id", Integer.valueOf(26), "meta", Integer.valueOf(8)));
      api.blocks.put("3,63,0", map("air", Boolean.FALSE, "id", Integer.valueOf(1), "meta", Integer.valueOf(0)));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      api.clock += 100;
      call(scope, "onTick", api);

      check("bed def selects the wool slot", 3, api.selectedSlot);
      check("bed def places a block", true, api.placements.size() > 0);
      check("bed def places at the first defense position", "3,64,0", api.placements.isEmpty() ? null
         : api.placements.get(0).get("x") + "," + api.placements.get(0).get("y") + "," + api.placements.get(0).get("z"));
      check("bed def clicks the supporting face", "UP", api.placements.isEmpty() ? null : api.placements.get(0).get("face"));
   }

   private static void autoLogin(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);

      MockEvent prompt = new MockEvent(2);
      prompt.packetClassName = "net.minecraft.network.play.server.S02PacketChat";
      prompt.packetData = map("component", map("text", "Please /register to play"));
      call(scope, "onEvent", api, prompt);
      check("auto login queues on the register prompt", true, api.logs.toString().contains("Queueing register"));

      api.clock += 1000;
      call(scope, "onEvent", api, new MockEvent(0));
      check("auto login sends the register command", true,
         api.logs.toString().contains("say:/register axolotlaxolotl axolotlaxolotl"));
   }

   private static void legitReach(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("swinging", Boolean.TRUE);
      api.playerState.put("health", 20.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 4.0, "dead", Boolean.FALSE, "health", 20.0, "flags", 0,
         "x", 0.0, "y", 64.0, "z", 4.0, "height", 1.8, "width", 0.6));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      call(scope, "onTick", api);

      MockEvent teleport = new MockEvent(2);
      teleport.packetClassName = "net.minecraft.network.play.server.S18PacketEntityTeleport";
      teleport.packetData = map("entityId", Integer.valueOf(7), "posX", Integer.valueOf(0),
         "posY", Integer.valueOf(2048), "posZ", Integer.valueOf(160));
      call(scope, "onEvent", api, teleport);
      call(scope, "onTick", api);

      MockEvent relative = new MockEvent(2);
      relative.packetClassName = "net.minecraft.network.play.server.S14PacketEntity";
      relative.packetData = map("entityId", Integer.valueOf(7), "posX", Integer.valueOf(0),
         "posY", Integer.valueOf(0), "posZ", Integer.valueOf(0));
      call(scope, "onEvent", api, relative);
      check("legit reach holds delayed entity packets", 1, relative.dispatchMode);
   }

   private static void lagBreak(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("moveForward", 1.0);
      api.playerState.put("ticksExisted", 100.0);
      api.playerState.put("health", 20.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 3.0, "dead", Boolean.FALSE, "health", 20.0, "flags", 0,
         "x", 0.0, "y", 64.0, "z", 3.0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      api.playerState.put("ticksExisted", 101.0);
      call(scope, "onEvent", api, new MockEvent(21));

      MockEvent outbound = new MockEvent(3);
      outbound.packetClassName = "net.minecraft.network.play.client.C02PacketUseEntity";
      outbound.packetData = map("action", "ATTACK");
      call(scope, "onEvent", api, outbound);
      check("lag break holds outbound packets while blinking", 1, outbound.dispatchMode);
   }

   private static void wtap(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("health", 20.0);
      api.settings.put("chance", 100.0);
      api.settings.put("release_delay", 0.0);
      api.settings.put("repress_delay", 0.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 2.0, "dead", Boolean.FALSE, "health", 20.0, "hurtTime", 0.0, "flags", 0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);

      MockEvent attack = new MockEvent(14);
      call(scope, "onEvent", api, attack);
      check("wtap releases forward", Boolean.FALSE, api.keyState.get("Forward"));

      api.clock += 100;
      MockEvent tick = new MockEvent(0);
      call(scope, "onEvent", api, tick);
      check("wtap re-presses forward", Boolean.TRUE, api.keyState.get("Forward"));
   }

   private static void sprintReset(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("onGround", Boolean.TRUE);
      api.playerState.put("sprinting", Boolean.TRUE);
      api.playerState.put("moveForward", 1.0);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("health", 20.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 3.0, "dead", Boolean.FALSE, "health", 20.0, "flags", 0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      call(scope, "onEvent", api, new MockEvent(14));
      check("sprint reset stops sprinting", true, api.sprintPackets.contains(Boolean.FALSE));
   }

   private static void hitSelect(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("onGround", Boolean.FALSE);
      api.playerState.put("hurtTime", 0.0);
      api.playerState.put("fallDistance", 0.0);
      api.playerState.put("health", 20.0);
      api.settings.put("mode", 1.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 3.0, "dead", Boolean.FALSE, "health", 20.0, "hurtTime", 0.0, "flags", 0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);

      MockEvent attack = new MockEvent(3);
      attack.packetClassName = "net.minecraft.network.play.client.C02PacketUseEntity";
      attack.packetData = map("action", "ATTACK", "entityId", Integer.valueOf(7));
      call(scope, "onEvent", api, attack);

      check("hit select blocks the non-crit hit", 2, attack.dispatchMode);
      check("hit select enables keep sprint", Boolean.TRUE, api.moduleEnabled.get("keepsprint"));
   }

   private static void antiFireball(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("yaw", 0.0);
      api.playerState.put("pitch", 0.0);
      api.playerState.put("eyeHeight", 1.62);
      api.playerState.put("health", 20.0);
      api.entities.add(map("id", Integer.valueOf(9), "name", "fireball",
         "class", "net.minecraft.entity.projectile.EntityLargeFireball", "type", "EntityLargeFireball",
         "distance", 4.0, "dead", Boolean.FALSE, "x", 0.0, "y", 64.0, "z", 4.0, "height", 1.0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      call(scope, "onTick", api);
      MockEvent rotation = new MockEvent(13);
      call(scope, "onEvent", api, rotation);

      check("anti fireball aims at the fireball", true, api.looks.size() > 0);
      check("anti fireball attacks the fireball", true, api.attacks.size() > 0);
   }

   private static void piercing(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("x", 0.0);
      api.playerState.put("y", 64.0);
      api.playerState.put("z", 0.0);
      api.playerState.put("yaw", 0.0);
      api.playerState.put("pitch", 0.0);
      api.playerState.put("eyeHeight", 1.62);
      api.playerState.put("health", 20.0);
      api.entities.add(map("id", Integer.valueOf(7), "name", "target", "player", Boolean.TRUE,
         "distance", 2.0, "dead", Boolean.FALSE, "health", 20.0, "flags", 0,
         "x", 0.0, "y", 64.0, "z", 2.0, "height", 1.8, "width", 0.6, "hurtTime", 0.0));

      Scriptable scope = run(context, file, api);
      call(scope, "onEnable", api);
      MockEvent attack = new MockEvent(14);
      call(scope, "onEvent", api, attack);

      check("piercing cancels the vanilla interaction", true, attack.cancelled);
      check("piercing attacks through the block", true, api.attacks.size() > 0);
   }

   private static Scriptable run(Context context, Path file, MockApi api) throws IOException {
      Scriptable scope = context.initStandardObjects();
      ScriptableObject.putProperty(scope, "api", Context.javaToJS(api, scope));
      context.evaluateString(scope, new String(Files.readAllBytes(file), StandardCharsets.UTF_8), file.getFileName().toString(), 1, null);
      return scope;
   }

   private static void call(Scriptable scope, String name, MockApi api) {
      call(scope, name, api, null);
   }

   private static void call(Scriptable scope, String name, MockApi api, Object event) {
      Object value = ScriptableObject.getProperty(scope, name);
      if (value instanceof Function) {
         Object[] arguments = event == null
            ? new Object[]{Context.javaToJS(api, scope)}
            : new Object[]{Context.javaToJS(api, scope), Context.javaToJS(event, scope)};
         ((Function)value).call(Context.getCurrentContext(), scope, scope, arguments);
      }
   }

   private static JsonObject load(Path file) throws IOException {
      String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      int start = source.indexOf("// @slinky") + "// @slinky".length();
      int depth = 0;
      int end = -1;
      for (int i = start; i < source.length(); i++) {
         char c = source.charAt(i);
         if (c == '{') {
            depth++;
         } else if (c == '}') {
            depth--;
            if (depth == 0) {
               end = i + 1;
               break;
            }
         }
      }

      String block = source.substring(start, end).replaceAll("(?m)^\\s*//\\s?", "");
      JsonElement parsed = new JsonParser().parse(block);
      return parsed.getAsJsonObject();
   }

   private static Map<String, Object> map(Object... pairs) {
      Map<String, Object> result = new HashMap<String, Object>();
      for (int i = 0; i + 1 < pairs.length; i += 2) {
         result.put(String.valueOf(pairs[i]), pairs[i + 1]);
      }

      return result;
   }

   private static void check(String label, Object expected, Object actual) {
      boolean ok = expected == null ? actual == null : expected.equals(actual);
      if (!ok) {
         failures++;
      }

      System.out.println((ok ? "  ok   " : "  FAIL ") + label + " expected=" + expected + " actual=" + actual);
   }

   public static final class MockApi {
      public final Map<String, Object> settings = new HashMap<String, Object>();
      public final Map<String, Object> playerState = new HashMap<String, Object>();
      public final Map<String, Object> blocks = new HashMap<String, Object>();
      public final Map<Integer, Object> hotbar = new HashMap<Integer, Object>();
      public final Map<String, Object> moduleSettings = new HashMap<String, Object>();
      public final Map<String, Object> moduleEnabled = new HashMap<String, Object>();
      public final List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
      public final List<Map<String, Object>> placements = new ArrayList<Map<String, Object>>();
      public final List<Double> looks = new ArrayList<Double>();
      public final List<String> attacks = new ArrayList<String>();
      public final List<String> logs = new ArrayList<String>();
      public final Map<String, Boolean> keyState = new HashMap<String, Boolean>();
      public final List<Boolean> sprintPackets = new ArrayList<Boolean>();
      public final List<Object> sentPackets = new ArrayList<Object>();
      public long clock = 1000;
      public int selectedSlot = -1;
      public boolean used;
      public boolean sprinting;
      public boolean screenOpenFlag;
      public Map<String, Object> heldStack;

      MockApi(JsonObject manifest) {
         JsonArray rows = manifest.getAsJsonArray("settings");
         if (rows != null) {
            for (JsonElement element : rows) {
               JsonObject row = element.getAsJsonObject();
               JsonElement value = row.get("default");
               settings.put(row.get("key").getAsString(), value == null ? (Object)defaultFor(row) : (Object)plain(value));
            }
         }
      }

      private static Object defaultFor(JsonObject row) {
         String type = row.has("type") ? row.get("type").getAsString() : "boolean";
         if (type.equals("number")) {
            return Double.valueOf(row.has("min") ? row.get("min").getAsDouble() : 0.0);
         }

         if (type.equals("boolean")) {
            return Boolean.FALSE;
         }

         return "";
      }

      private static Object plain(JsonElement element) {
         if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
               return Boolean.valueOf(element.getAsBoolean());
            }

            if (element.getAsJsonPrimitive().isNumber()) {
               return Double.valueOf(element.getAsDouble());
            }

            return element.getAsString();
         }

         return element.toString();
      }

      public Map<String, Object> player() {
         return this.playerState;
      }

      public Map<String, Object> blockAt(int x, int y, int z) {
         Object value = this.blocks.get(x + "," + y + "," + z);
         return value == null ? map("air", Boolean.TRUE) : (Map<String, Object>)value;
      }

      public Map<String, Object> hotbar(int slot) {
         Object value = this.hotbar.get(Integer.valueOf(slot));
         return value == null ? map("empty", Boolean.TRUE) : (Map<String, Object>)value;
      }

      public boolean holdingBlock() {
         return Boolean.TRUE;
      }

      public void selectSlot(int slot) {
         this.selectedSlot = slot;
      }

      public int heldSlot() {
         return this.selectedSlot;
      }

      public boolean placeBlock(int x, int y, int z, String face) {
         this.placements.add(map("x", Integer.valueOf(x), "y", Integer.valueOf(y), "z", Integer.valueOf(z), "face", face));
         this.blocks.put(x + "," + y + "," + z, map("air", Boolean.FALSE, "id", Integer.valueOf(1)));
         return true;
      }

      public void look(double yaw, double pitch) {
         this.looks.add(Double.valueOf(yaw));
         this.playerState.put("yaw", Double.valueOf(yaw));
         this.playerState.put("pitch", Double.valueOf(pitch));
      }

      public double now() {
         return this.clock;
      }

      public void log(String message) {
         this.logs.add(message);
      }

      public Object setting(String key) {
         return this.settings.get(key);
      }

      public double num(String key) {
         Object value = this.settings.get(key);
         return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
      }

      public boolean bool(String key) {
         Object value = this.settings.get(key);
         return value instanceof Boolean && (Boolean)value;
      }

      public List<Map<String, Object>> entities() {
         return this.entities;
      }

      public int relationshipFlags(String name) {
         return 0;
      }

      public Object moduleSetting(String id, String key) {
         return this.moduleSettings.get(id + "." + key);
      }

      public boolean setModuleSetting(String id, String key, Object value) {
         this.moduleSettings.put(id + "." + key, String.valueOf(value));
         return true;
      }

      public boolean setModuleEnabled(String id, boolean enabled) {
         this.moduleEnabled.put(id, Boolean.valueOf(enabled));
         return true;
      }

      public List<String> modules() {
         List<String> ids = new ArrayList<String>();
         ids.add("reach");
         ids.add("autoclicker");
         ids.add("silent_aura");
         return ids;
      }

      public boolean moduleEnabled(String id) {
         return Boolean.TRUE.equals(this.moduleEnabled.get(id));
      }

      public boolean claimRotation(int priority) {
         return true;
      }

      public boolean keyDown(String binding) {
         return true;
      }

      public Object entityById(int id) {
         for (Map<String, Object> row : this.entities) {
            if (row.get("id") instanceof Number && ((Number)row.get("id")).intValue() == id) {
               return row;
            }
         }
         return null;
      }

      public boolean attackEntity(Object ref) {
         if (ref instanceof Map) {
            Object name = ((Map<?, ?>)ref).get("name");
            this.attacks.add(name == null ? "?" : String.valueOf(name));
         } else {
            this.attacks.add(String.valueOf(ref));
         }
         return true;
      }

      public void clearRotation() {
      }

      public boolean attack(String name) {
         this.attacks.add(name);
         return true;
      }

      public boolean swing() {
         return true;
      }

      public boolean message(String text) {
         this.logs.add("chat:" + text);
         return true;
      }

      public boolean useItem() {
         this.used = true;
         return true;
      }

      public boolean isEnabled() {
         return true;
      }

      public boolean screenOpen() {
         return this.screenOpenFlag;
      }

      public boolean physicalKeyDown(String binding) {
         return true;
      }

      public boolean setKeyDown(String binding, boolean down) {
         this.keyState.put(binding, Boolean.valueOf(down));
         return true;
      }

      public boolean sendSprintPacket(boolean start) {
         this.sprintPackets.add(Boolean.valueOf(start));
         return true;
      }

      public boolean setSprint(boolean value) {
         this.sprinting = value;
         return true;
      }

      public Object newPacket(String name) {
         return map("packet", name);
      }

      public Object newPacket(String name, Object a) {
         return map("packet", name);
      }

      public Object newPacket(String name, Object a, Object b, Object c, Object d) {
         return map("packet", name);
      }

      public boolean sendPacket(Object packet) {
         this.sentPackets.add(packet);
         return true;
      }

      public boolean say(String message) {
         this.logs.add("say:" + message);
         return true;
      }

      public Object target() {
         return this.entities.isEmpty() ? null : this.entities.get(0);
      }

      public Object draw() {
         return null;
      }

      public Object ref(Object value) {
         return value;
      }

      public Object mc() {
         return null;
      }

      public Object rawPlayer() {
         return null;
      }

      public Map<String, Object> heldItem() {
         return this.heldStack == null ? map("empty", Boolean.TRUE) : this.heldStack;
      }

      public long getTick() {
         return this.clock / 50L;
      }
   }

   public static final class MockEvent {
      public final int type;
      public boolean active = true;
      public boolean cancelled;
      public int dispatchMode;
      public boolean remapMovement;
      public double forward;
      public double sideways;
      public double factor;
      public String packetClassName;
      public Map<String, Object> packetData;

      MockEvent(int type) {
         this.type = type;
      }

      public int getType() {
         return this.type;
      }

      public String getName() {
         return "event_" + this.type;
      }

      public boolean isActive() {
         return this.active;
      }

      public void setActive(boolean value) {
         this.active = value;
      }

      public boolean isCancelled() {
         return this.cancelled;
      }

      public void cancel() {
         this.cancelled = true;
      }

      public void setCancelled(boolean value) {
         this.cancelled = value;
      }

      public int getDispatchMode() {
         return this.dispatchMode;
      }

      public void setDispatchMode(int mode) {
         this.dispatchMode = mode;
      }

      public boolean isRemapMovement() {
         return this.remapMovement;
      }

      public void setRemapMovement(boolean value) {
         this.remapMovement = value;
      }

      public String packetClass() {
         return this.packetClassName;
      }

      public Object getPacket() {
         return this.packetData;
      }

      public Object packetRef() {
         return this.packetData == null ? null : new MockRef(this.packetData);
      }

      public float getRenderWidth() {
         return 854.0F;
      }

      public float getRenderHeight() {
         return 480.0F;
      }
   }

   public static final class MockRef {
      private final Map<String, Object> data;

      MockRef(Map<String, Object> data) {
         this.data = data;
      }

      public Object get(String field) {
         return this.data.get(field);
      }

      public Object call(String method) {
         if (method.equals("getChatComponent")) {
            Object component = this.data.get("component");
            return component instanceof Map ? new MockRef((Map<String, Object>)component) : component;
         }

         if (method.equals("getMessage")) {
            return this;
         }

         if (method.equals("getEntityId")) {
            return this.data.get("entityId");
         }

         if (method.indexOf("UnformattedText") >= 0 || method.indexOf("FormattedText") >= 0) {
            return this.data.get("text");
         }

         return this.data.get(method);
      }

      public Object call(String method, Object a) {
         return call(method);
      }

      public Object call(String method, Object a, Object b) {
         return call(method);
      }
   }
}
