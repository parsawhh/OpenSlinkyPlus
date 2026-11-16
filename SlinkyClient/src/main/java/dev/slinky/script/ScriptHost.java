package dev.slinky.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.client.ClientContext;
import dev.slinky.inject.Platform;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ScriptHost {
   public static final int DEFAULT_PORT = 47900;
   private static volatile ScriptHost instance;
   private final ModuleManager manager;
   private final Path directory;
   private final Path statusDirectory;
   private final Map<String, ScriptRuntime> runtimes = new LinkedHashMap<>();
   private final Map<String, ScriptModule> modules = new LinkedHashMap<>();
   private final Map<String, Long> scanned = new HashMap<>();
   private final Map<String, Long> applied = new HashMap<>();
   private final Queue<Runnable> pending = new ConcurrentLinkedQueue<Runnable>();
   private final HostModule hostModule;
   private final ScriptServer server;
   private volatile boolean running = true;
   private volatile long lastStatus;
   private Thread watcher;

   ScriptHost(ModuleManager manager) {
      this(manager, null);
   }

   ScriptHost(ModuleManager manager, Path directory) {
      this.manager = manager;
      this.directory = directory == null ? resolveDirectory() : directory;
      this.statusDirectory = this.directory.resolve(".status");
      this.hostModule = new HostModule();
      this.server = new ScriptServer(this, port(), token());
   }

   public static void install(ModuleManager manager) {
      if (instance != null) {
         return;
      }

      ScriptHost host = new ScriptHost(manager);
      instance = host;
      host.start();
   }

   public static ScriptHost get() {
      return instance;
   }

   public Path directory() {
      return this.directory;
   }

   public Path statusDirectory() {
      return this.statusDirectory;
   }

   public Map<String, ScriptRuntime> runtimes() {
      return this.runtimes;
   }

   private static Path resolveDirectory() {
      try {
         ScriptGame game = new ScriptGame();
         java.io.File data = game.dataDirectory();
         if (data != null && data.isDirectory()) {
            return data.toPath().resolve("slinky-scripts");
         }
      } catch (Throwable e) {
         Platform.log("script directory fallback: " + e);
      }

      return Paths.get(System.getProperty("user.home", ".")).resolve(".slinky").resolve("scripts");
   }

   private int port() {
      JsonObject config = this.config();
      if (config != null && config.has("port")) {
         try {
            return config.get("port").getAsInt();
         } catch (RuntimeException e) {
            Platform.log("invalid script port in config: " + e);
         }
      }

      return DEFAULT_PORT;
   }

   private String token() {
      JsonObject config = this.config();
      if (config != null && config.has("token")) {
         return config.get("token").getAsString();
      }

      return "";
   }

   private JsonObject config() {
      Path file = this.directory.resolve("config.json");
      if (!Files.isRegularFile(file)) {
         return null;
      }

      try {
         JsonElement parsed = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
         return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
      } catch (Exception e) {
         Platform.log("invalid script config: " + e);
         return null;
      }
   }

   private void start() {
      try {
         Files.createDirectories(this.directory);
         Files.createDirectories(this.statusDirectory);
      } catch (IOException e) {
         Platform.log("script host could not create " + this.directory + ": " + e);
      }

      this.manager.register(this.hostModule);
      this.manager.setEnabled(this.hostModule, true, false);
      this.pending.add(new Runnable() {
         public void run() {
            ScriptHost.this.scanNow();
         }
      });
      this.watcher = new Thread(new Runnable() {
         public void run() {
            ScriptHost.this.watch();
         }
      }, "slinky-script-watcher");
      this.watcher.setDaemon(true);
      this.watcher.start();
      this.server.start();
      Platform.log("script host watching " + this.directory);
   }

   public void submit(Runnable action) {
      this.pending.add(action);
   }

   private void watch() {
      while (this.running) {
         try {
            Thread.sleep(750L);
         } catch (InterruptedException e) {
            return;
         }

         try {
            this.scan();
         } catch (Throwable e) {
            Platform.log("script scan failed: " + e);
         }

         if (this.hostModule != null && !this.hostModule.isEnabled()) {
            this.manager.setEnabled(this.hostModule, true, false);
         }
      }
   }

   private void scan() {
      Map<String, Long> current = new HashMap<>();

      if (Files.isDirectory(this.directory)) {
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory, "*.js")) {
            for (Path file : stream) {
               String name = file.getFileName().toString();
               String id = name.substring(0, name.length() - 3);
               long stamp = Files.getLastModifiedTime(file).toMillis() * 1000000L + Files.size(file);
               current.put(id, stamp);
            }
         } catch (IOException e) {
            Platform.log("script scan failed: " + e);
         }
      }

      for (Map.Entry<String, Long> entry : current.entrySet()) {
         Long appliedStamp = this.applied.get(entry.getKey());
         if (appliedStamp == null || appliedStamp.longValue() != entry.getValue().longValue()) {
            this.queueReload(entry.getKey());
         }
      }

      for (String id : new ArrayList<String>(this.applied.keySet())) {
         if (!current.containsKey(id)) {
            this.queueReload(id);
         }
      }

      this.scanned.clear();
      this.scanned.putAll(current);
   }

   private void queueReload(final String id) {
      this.submit(new Runnable() {
         public void run() {
            ScriptHost.this.reload(id);
         }
      });
   }

   private void scanNow() {
      this.scan();
   }

   private void pump() {
      Runnable action;
      while ((action = this.pending.poll()) != null) {
         try {
            action.run();
         } catch (Throwable e) {
            Platform.log("script action failed: " + e);
         }
      }

      long now = System.currentTimeMillis();
      if (now - this.lastStatus > 2000L) {
         this.lastStatus = now;
         this.writeStatus();
      }
   }

   public void reload(String id) {
      Path file = this.directory.resolve(id + ".js");
      ScriptModule previous = this.modules.get(id);
      boolean wasEnabled = previous != null && previous.isEnabled();
      int binding = previous == null ? 0 : previous.getBinding();
      boolean holdBinding = previous != null && previous.isHoldBinding();
      Map<String, String> values = captureSettings(previous);

      this.unload(id);

      if (!Files.isRegularFile(file)) {
         this.applied.remove(id);
         return;
      }

      try {
         String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
         ScriptManifest manifest = ScriptManifest.parse(source, id);
         manifest = this.unique(manifest, id);
         ScriptRuntime runtime = new ScriptRuntime(this.manager, this.statusDirectory, manifest);
         ScriptModule module = new ScriptModule(runtime);
         runtime.prepare(source);
         restoreSettings(module, values);
         if (binding != 0) {
            module.setBinding(binding);
            module.setHoldBinding(holdBinding);
         }
         this.manager.register(module);
         this.runtimes.put(id, runtime);
         this.modules.put(id, module);
         this.applied.put(id, this.stamp(file));
         if (wasEnabled) {
            this.manager.setEnabled(module, true, false);
         }
         Platform.log("script loaded: " + manifest.id());
      } catch (Throwable e) {
         Platform.log("script failed: " + id + ": " + e);
         this.writeError(id, e.toString());
      }
   }

   public void setEnabled(String id, boolean enabled) {
      Module module = this.modules.get(id);
      if (module == null) {
         module = this.manager.find(id);
      }

      if (module != null) {
         this.manager.setEnabled(module, enabled, true);
      }
   }

   public void setSetting(String id, String key, String value) {
      Module module = this.modules.get(id);
      if (module == null) {
         module = this.manager.find(id);
      }

      if (module == null || value == null) {
         return;
      }

      Setting<?> setting = module.findSetting(key);
      if (setting != null) {
         setting.parseAndSet(value);
         this.manager.persist();
      }
   }

   private ScriptManifest unique(ScriptManifest manifest, String fallbackId) {
      String id = manifest.id();
      String name = manifest.name();
      Module existing = this.manager.find(id);
      if (existing != null) {
         id = "script_" + fallbackId;
      }

      existing = this.manager.find(name);
      if (existing != null) {
         name = id;
      }

      return manifest.withIdentity(id, name);
   }

   private void unload(String id) {
      ScriptModule module = this.modules.remove(id);
      if (module != null) {
         this.manager.setEnabled(module, false, false);
         this.unregister(module);
      }

      ScriptRuntime runtime = this.runtimes.remove(id);
      if (runtime != null) {
         runtime.close();
      }
   }

   private static Map<String, String> captureSettings(ScriptModule module) {
      Map<String, String> result = new LinkedHashMap<String, String>();
      if (module == null) {
         return result;
      }

      for (Setting<?> setting : module.getSettings()) {
         Object value = setting.get();
         result.put(setting.getKey(), value == null ? "" : String.valueOf(value));
      }

      return result;
   }

   private static void restoreSettings(ScriptModule module, Map<String, String> values) {
      if (module == null || values == null || values.isEmpty()) {
         return;
      }

      for (Setting<?> setting : module.getSettings()) {
         String value = values.get(setting.getKey());
         if (value != null) {
            try {
               setting.parseAndSet(value);
            } catch (Throwable ignored) {
            }
         }
      }
   }

   @SuppressWarnings("unchecked")
   private void unregister(Module module) {
      try {
         Field field = ModuleManager.class.getDeclaredField("byId");
         field.setAccessible(true);
         Map<String, Module> registry = (Map<String, Module>)field.get(this.manager);
         registry.remove(normalize(module.getId()));
      } catch (Throwable e) {
         Platform.log("script unregister failed: " + e);
      }
   }

   private long stamp(Path file) {
      try {
         return Files.getLastModifiedTime(file).toMillis() * 1000000L + Files.size(file);
      } catch (IOException e) {
         return 0L;
      }
   }

   public JsonArray describe() {
      JsonArray rows = new JsonArray();

      for (Map.Entry<String, ScriptRuntime> entry : this.runtimes.entrySet()) {
         ScriptRuntime runtime = entry.getValue();
         ScriptModule module = this.modules.get(entry.getKey());
         JsonObject record = new JsonObject();
         record.addProperty("id", runtime.manifest().id());
         record.addProperty("file", entry.getKey());
         record.addProperty("name", runtime.manifest().name());
         record.addProperty("category", runtime.manifest().category().name());
         record.addProperty("state", runtime.failed() ? "error" : "loaded");
         record.addProperty("error", runtime.error());
         record.addProperty("tick", runtime.ticks());
         record.addProperty("enabled", module != null && module.isEnabled());
         record.addProperty("updated", System.currentTimeMillis());
         rows.add(record);
      }

      return rows;
   }

   public List<String> logs(String id) {
      List<String> result = new ArrayList<String>();
      Path file = this.statusDirectory.resolve(id + ".log");
      if (!Files.isRegularFile(file)) {
         return result;
      }

      try {
         for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            result.add(line);
         }
      } catch (IOException e) {
         Platform.log("script log read failed: " + e);
      }

      return result;
   }

   private void writeError(String id, String error) {
      JsonObject record = new JsonObject();
      record.addProperty("id", id);
      record.addProperty("state", "error");
      record.addProperty("error", error);
      record.addProperty("updated", System.currentTimeMillis());
      this.write(id, record);
   }

   private void writeStatus() {
      JsonArray rows = this.describe();

      for (JsonElement element : rows) {
         JsonObject record = element.getAsJsonObject();
         this.write(record.get("file").getAsString(), record);
      }

      JsonObject index = new JsonObject();
      index.add("scripts", rows);
      index.addProperty("updated", System.currentTimeMillis());
      this.write("index", index);
   }

   private void write(String id, JsonObject record) {
      try {
         Files.createDirectories(this.statusDirectory);
         Files.write(
            this.statusDirectory.resolve(id + ".json"),
            record.toString().getBytes(StandardCharsets.UTF_8)
         );
      } catch (IOException e) {
         Platform.log("script status failed: " + e);
      }
   }

   private static String normalize(String value) {
      return value.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
   }

   private final class HostModule extends Module {
      HostModule() {
         super("slinky_script_host", "Script Host", "Runtime script host", ModuleCategory.UNKNOWN);
      }

      @Override
      public int metadataCategory() {
         return 9;
      }

      @Override
      public int descriptorFlags() {
         return 16;
      }

      @Override
      public boolean listens(int type) {
         return type == ScriptEvents.TICK;
      }

      @Override
      public void onEvent(ClientContext context, dev.slinky.client.event.SemanticEvent event) {
         ScriptHost.this.pump();
      }
   }
}
