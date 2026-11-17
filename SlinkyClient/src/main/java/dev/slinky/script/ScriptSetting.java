package dev.slinky.script;

public final class ScriptSetting {
   public final String key;
   public final String label;
   public final String type;
   public final double defaultValue;
   public final double minimum;
   public final double maximum;
   public final double step;
   public final String[] options;

   public ScriptSetting(String key, String label, String type, double defaultValue, double minimum, double maximum, double step, String[] options) {
      this.key = key;
      this.label = label;
      this.type = type;
      this.defaultValue = defaultValue;
      this.minimum = minimum;
      this.maximum = maximum;
      this.step = step;
      this.options = options;
   }
}
