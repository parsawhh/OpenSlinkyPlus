import dev.slinky.client.event.SemanticEvent;
import dev.slinky.script.ScriptException;
import dev.slinky.script.ScriptManifest;
import dev.slinky.script.ScriptModule;
import dev.slinky.script.ScriptRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Offline smoke test for the script host: manifest parsing, module construction, the JavaScript engine,
 * callbacks, settings and error handling. Does not need Minecraft.
 *
 *   java -cp <payload-classes>:<slinky-named.jar>:<gson.jar> ScriptHostSmokeTest.java
 */
public final class ScriptHostSmokeTest {
   private static int failures;

   public static void main(String[] args) throws Exception {
      Path status = Paths.get(System.getProperty("java.io.tmpdir"), "slinky-smoke");
      Files.createDirectories(status);

      String source = "// @slinky {\"id\":\"demo\",\"name\":\"Demo Module\",\"description\":\"smoke test\","
         + "\"category\":\"utility\",\"settings\":[{\"key\":\"range\",\"label\":\"Range\",\"type\":\"number\","
         + "\"default\":3.5,\"min\":1,\"max\":5,\"step\":0.5},{\"key\":\"flag\",\"label\":\"Flag\",\"type\":\"boolean\","
         + "\"default\":true}],\"listens\":[0,\"pre_interact\"]}\n"
         + "var count = 0;\n"
         + "function onEnable(api) { api.log('enabled ' + api.id + ' ' + api.name); }\n"
         + "function onTick(api) { count++; if (count % 5 === 0) { api.log('tick ' + count + ' range=' + api.setting('range')); } }\n"
         + "function onEvent(api, event) { api.log('event ' + event.name + ' type=' + event.type); }\n";

      ScriptManifest manifest = ScriptManifest.parse(source, "demo");
      check("manifest id", "demo", manifest.id());
      check("manifest name", "Demo Module", manifest.name());
      check("manifest settings", 2, manifest.settings().size());
      check("manifest category", "UTILITY", manifest.category().name());
      check("manifest listens numeric", true, manifest.listens(0));
      check("manifest listens named", true, manifest.listens(14));
      check("manifest ignores unknown", false, manifest.listens(99));

      ScriptRuntime runtime = new ScriptRuntime(null, status, manifest);
      ScriptModule module = new ScriptModule(runtime);
      check("module id", "demo", module.getId());
      check("module name", "Demo Module", module.getName());
      check("module category index", 4, module.metadataCategory());
      check("number setting", 3.5, module.findSetting("range").get());
      check("boolean setting", true, module.findSetting("flag").get());
      check("listens 0", true, module.listens(0));

      runtime.prepare(source);
      check("has onTick", true, runtime.hasFunction("onTick"));
      check("has onEvent", true, runtime.hasFunction("onEvent"));
      check("no bogus function", false, runtime.hasFunction("nope"));

      runtime.enable(null);
      for (int i = 0; i < 12; i++) {
         runtime.tick(null);
      }

      check("ticks", 12L, runtime.ticks());
      check("no failure", null, runtime.error());
      check("log lines", true, runtime.recent().size() >= 3);
      check("log content", true, runtime.recent().toString().contains("enabled demo Demo Module"));
      check("tick log", true, runtime.recent().toString().contains("range=3.5"));

      SemanticEvent event = new SemanticEvent(14);
      runtime.event(null, event);
      check("event log", true, runtime.recent().toString().contains("event pre_interact"));

      ScriptRuntime broken = new ScriptRuntime(null, status, ScriptManifest.parse("function onTick( {", "broken"));
      try {
         broken.prepare("function onTick( {");
         check("syntax error detected", true, false);
      } catch (ScriptException e) {
         check("syntax error detected", true, true);
      }

      ScriptRuntime throwing = new ScriptRuntime(null, status, ScriptManifest.parse("function onTick(){ throw 'boom'; }", "boom"));
      throwing.prepare("function onTick(){ throw 'boom'; }");
      throwing.tick(null);
      check("runtime error captured", true, throwing.failed());
      check("runtime error message", true, throwing.error() != null && throwing.error().contains("boom"));

      if (failures == 0) {
         System.out.println("script host smoke test: OK");
      } else {
         System.out.println("script host smoke test: " + failures + " FAILURES");
         System.exit(1);
      }
   }

   private static void check(String label, Object expected, Object actual) {
      boolean ok = expected == null ? actual == null : expected.equals(actual);
      if (!ok) {
         failures++;
      }

      System.out.println((ok ? "  ok   " : "  FAIL ") + label + " expected=" + expected + " actual=" + actual);
   }
}
