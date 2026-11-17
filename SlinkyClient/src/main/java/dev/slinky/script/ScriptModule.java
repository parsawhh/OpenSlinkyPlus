package dev.slinky.script;

import dev.slinky.client.ClientContext;
import dev.slinky.client.event.SemanticEvent;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.RangeSetting;
import dev.slinky.setting.StringListSetting;
import java.util.ArrayList;
import java.util.List;

public final class ScriptModule extends Module {
   private final ScriptRuntime runtime;
   private final int guiCategory;

   public ScriptModule(ScriptRuntime runtime) {
      super(runtime.manifest().id(), runtime.manifest().name(), runtime.manifest().description(), runtime.manifest().category());
      this.runtime = runtime;
      this.guiCategory = runtime.manifest().guiCategory();

      for (ScriptSetting spec : runtime.manifest().settings()) {
         this.add(setting(spec));
      }

      runtime.attach(this);
   }

   private static dev.slinky.setting.Setting<?> setting(ScriptSetting spec) {
      String type = spec.type == null ? "boolean" : spec.type;

      if (type.equals("boolean")) {
         return new BooleanSetting(spec.key, spec.label, spec.defaultValue >= 0.5);
      } else if (type.equals("number")) {
         return new NumberSetting(spec.key, spec.label, spec.defaultValue, spec.minimum, spec.maximum, spec.step);
      } else if (type.equals("choice")) {
         String[] options = spec.options == null || spec.options.length == 0 ? new String[]{"default"} : spec.options;
         int index = (int)spec.defaultValue;
         return new ChoiceSetting(spec.key, spec.label, Math.max(0, Math.min(index, options.length - 1)), options);
      } else if (type.equals("range")) {
         return new RangeSetting(spec.key, spec.label, spec.defaultValue, spec.defaultValue, spec.minimum, spec.maximum);
      }

      return new StringListSetting(spec.key, spec.label, new ArrayList<String>());
   }

   public ScriptRuntime runtime() {
      return this.runtime;
   }

   @Override
   public int metadataCategory() {
      return this.guiCategory;
   }

   @Override
   public int descriptorFlags() {
      return 16;
   }

   @Override
   public String statusText() {
      return this.runtime.failed() ? "error" : null;
   }

   @Override
   public boolean listens(int type) {
      return this.runtime.manifest().listens(type);
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      this.runtime.event(context, event);
   }

   @Override
   public void onClientTick(ClientContext context) {
      this.runtime.tick(context);
   }

   @Override
   protected void onEnable(ClientContext context) {
      this.runtime.enable(context);
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.runtime.disable(context);
   }
}
