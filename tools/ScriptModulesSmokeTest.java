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
      autoGapple(context, directory.resolve("auto_gapple.js"));
      riseAntiCheat(context, directory.resolve("rise_anticheat.js"));

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

      Map<String, Object> event = map("type", Integer.valueOf(21), "forward", Double.valueOf(0.0),
         "sideways", Double.valueOf(0.0), "factor", Double.valueOf(1.0));
      call(ladderScope, "onEvent", ladder, event);
      check("vape clutch handles the movement event", true, event.containsKey("forward"));
   }

   private static void antichest(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
      api.playerState.put("inWorld", Boolean.TRUE);
      api.playerState.put("health", 20.0);
      api.entities.add(map("name", "cheater", "player", Boolean.TRUE, "distance", 4.6, "dead", Boolean.FALSE,
         "x", 5.0, "y", 64.0, "z", 0.0));

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
      check("antichest logs the detection", true, api.logs.toString().contains("cheater"));
   }

   private static void killAura(Context context, Path file) throws IOException {
      MockApi api = new MockApi(load(file));
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

   private static Scriptable run(Context context, Path file, MockApi api) throws IOException {
      Scriptable scope = context.initStandardObjects();
      ScriptableObject.putProperty(scope, "api", Context.javaToJS(api, scope));
      context.evaluateString(scope, new String(Files.readAllBytes(file), StandardCharsets.UTF_8), file.getFileName().toString(), 1, null);
      return scope;
   }

   private static void call(Scriptable scope, String name, MockApi api) {
      call(scope, name, api, null);
   }

   private static void call(Scriptable scope, String name, MockApi api, Map<String, Object> event) {
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
      public long clock = 1000;
      public int selectedSlot = -1;
      public boolean used;

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
   }
}
