package dev.slinky.script;

import dev.slinky.client.ClientContext;
import dev.slinky.client.event.SemanticEvent;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.inject.Platform;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ScriptRuntime {
   private final ModuleManager manager;
   private final Path statusDirectory;
   private final ScriptManifest manifest;
   private final ScriptGame game = new ScriptGame();
   private final List<String> recent = Collections.synchronizedList(new ArrayList<String>());
   private final Queue<Runnable> deferred = new ConcurrentLinkedQueue<Runnable>();
   private ScriptModule module;
   private ScriptEngine engine;
   private ScriptApi api;
   private boolean retainRotation;
   private float retainYaw;
   private float retainPitch;
   private long retainAt;
   private volatile boolean failed;
   private volatile String error;
   private volatile long ticks;
   private volatile ClientContext context;
   private volatile Thread owner;
   private final ThreadLocal<SemanticEvent> currentEvent = new ThreadLocal<SemanticEvent>();
   private final ThreadLocal<ScriptDraw> currentDraw = new ThreadLocal<ScriptDraw>();

   public ScriptRuntime(ModuleManager manager, Path statusDirectory, ScriptManifest manifest) {
      this.manager = manager;
      this.statusDirectory = statusDirectory;
      this.manifest = manifest;
   }

   public ScriptManifest manifest() {
      return this.manifest;
   }

   public ScriptModule module() {
      return this.module;
   }

   public void attach(ScriptModule module) {
      this.module = module;
      this.api = new ScriptApi(module, this, this.game);
   }

   public void prepare(String source) throws ScriptException {
      this.owner = Thread.currentThread();
      this.engine = ScriptEngines.create();
      this.engine.bind("api", this.api);
      this.engine.bind("slinky", new ScriptBridge(this));
      this.engine.evaluate(this.manifest.id(), source);
   }

   public boolean hasFunction(String name) {
      return this.engine != null && this.engine.hasFunction(name);
   }

   public boolean failed() {
      return this.failed;
   }

   public String error() {
      return this.error;
   }

   public long ticks() {
      return this.ticks;
   }

   public ClientContext context() {
      return this.context;
   }

   public SemanticEvent currentEvent() {
      return this.currentEvent.get();
   }

   public ScriptDraw draw() {
      ScriptDraw d = this.currentDraw.get();
      if (d == null) {
         d = new ScriptDraw(this);
         this.currentDraw.set(d);
      }
      return d;
   }

   public List<String> recent() {
      synchronized (this.recent) {
         return new ArrayList<String>(this.recent);
      }
   }

   public void tick(final ClientContext context) {
      this.context = context;

      if (this.defer(new Runnable() {
         public void run() {
            ScriptRuntime.this.runTick();
         }
      })) {
         return;
      }

      this.runTick();
   }

   public void event(final ClientContext context, final SemanticEvent event) {
      this.context = context;

      if (this.defer(new Runnable() {
         public void run() {
            ScriptRuntime.this.runEvent(event);
         }
      })) {
         return;
      }

      this.runEvent(event);
   }

   public void enable(final ClientContext context) {
      this.context = context;

      if (this.defer(new Runnable() {
         public void run() {
            ScriptRuntime.this.runEnable();
         }
      })) {
         return;
      }

      this.runEnable();
   }

   public void disable(final ClientContext context) {
      this.context = context;

      if (this.defer(new Runnable() {
         public void run() {
            ScriptRuntime.this.runDisable();
         }
      })) {
         return;
      }

      this.runDisable();
   }

   private void runTick() {
      this.drain();
      this.ticks++;

      if (this.failed || !this.hasFunction("onTick")) {
         return;
      }

      try {
         this.engine.call("onTick", this.api);
      } catch (ScriptException e) {
         this.fail(e);
      }
   }

   private void runEvent(SemanticEvent event) {
      if (this.failed || !this.hasFunction("onEvent")) {
         return;
      }

      this.currentEvent.set(event);
      this.currentDraw.set(null);
      try {
         this.engine.call("onEvent", this.api, new ScriptEvent(event));
         this.applyRetain(event);
      } catch (ScriptException e) {
         this.fail(e);
      } finally {
         ScriptDraw d = this.currentDraw.get();
         if (d != null && !event.cancelled) {
            try {
               RenderAccess.submit(event, d.plan());
            } catch (Throwable ignored) {
            }
         }
         this.currentDraw.remove();
         this.currentEvent.remove();
      }
   }

   private void runEnable() {
      if (this.failed || !this.hasFunction("onEnable")) {
         return;
      }

      try {
         this.engine.call("onEnable", this.api);
      } catch (ScriptException e) {
         this.fail(e);
      }
   }

   private void runDisable() {
      if (this.failed || !this.hasFunction("onDisable")) {
         return;
      }

      try {
         this.engine.call("onDisable", this.api);
      } catch (ScriptException e) {
         this.fail(e);
      }
   }

   private boolean defer(Runnable action) {
      Thread current = Thread.currentThread();
      if (this.owner == null) {
         this.owner = current;
         return false;
      }

      if (current == this.owner) {
         return false;
      }

      this.deferred.add(action);
      return true;
   }

   private void drain() {
      Runnable action;
      while ((action = this.deferred.poll()) != null) {
         action.run();
      }
   }

   public void log(String message) {
      String line = System.currentTimeMillis() + " " + message;
      this.recent.add(line);
      while (this.recent.size() > 200) {
         this.recent.remove(0);
      }

      Platform.log("[script " + this.manifest.id() + "] " + message);
      try {
         Files.createDirectories(this.statusDirectory);
         Files.write(
            this.statusDirectory.resolve(this.manifest.id() + ".log"),
            Collections.singletonList(line),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
         );
      } catch (IOException e) {
         Platform.log("script log failed: " + e);
      }
   }

   public void persist() {
      if (this.manager != null) {
         this.manager.persist();
      }
   }

   public List<Module> modules() {
      return this.manager == null ? Collections.<Module>emptyList() : this.manager.all();
   }

   public Module findModule(String id) {
      return this.manager == null ? null : this.manager.find(id);
   }

   public void setModuleEnabled(Module module, boolean enabled) {
      if (this.manager != null) {
         this.manager.setEnabled(module, enabled, true);
      }
   }

   public void retain(float yaw, float pitch) {
      this.retainRotation = true;
      this.retainYaw = yaw;
      this.retainPitch = pitch;
      this.retainAt = System.currentTimeMillis();
      if (this.context != null) {
         RotationService.retain(this.context, true, yaw, pitch);
      }
   }

   public void clearRetain() {
      this.retainRotation = false;
      RotationService.clearManual();
   }

   private void applyRetain(SemanticEvent event) {
      if (event.type == 13 && this.retainRotation && System.currentTimeMillis() - this.retainAt < 500L) {
         event.active = true;
         event.overrideRotation = true;
         event.yaw = this.retainYaw;
         event.pitch = this.retainPitch;
      }
   }

   public void rotate(float yaw, float pitch) {
      RotationService.add(yaw, pitch);
   }

   public void close() {
      if (this.engine != null) {
         this.engine.close();
      }
   }

   private void fail(ScriptException e) {
      this.failed = true;
      this.error = e.getMessage() == null ? e.toString() : e.getMessage();
      this.log("error: " + this.error);
   }

   public static final class ScriptBridge {
      private final ScriptRuntime runtime;

      ScriptBridge(ScriptRuntime runtime) {
         this.runtime = runtime;
      }

      public void log(String message) {
         this.runtime.log(message);
      }

      public double setting(String key) {
         Setting<?> setting = this.runtime.module == null ? null : this.runtime.module.findSetting(key);
         Object value = setting == null ? null : setting.get();
         return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
      }
   }
}
