package dev.slinky.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.module.ModuleCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ScriptManifest {
   private final String id;
   private final String name;
   private final String description;
   private final ModuleCategory category;
   private final int guiCategory;
   private final List<ScriptSetting> settings;
   private final int[] listens;

   private ScriptManifest(String id, String name, String description, ModuleCategory category, int guiCategory, List<ScriptSetting> settings, int[] listens) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.category = category;
      this.guiCategory = guiCategory;
      this.settings = Collections.unmodifiableList(settings);
      this.listens = listens;
   }

   public String id() {
      return this.id;
   }

   public String name() {
      return this.name;
   }

   public String description() {
      return this.description;
   }

   public ModuleCategory category() {
      return this.category;
   }

   public int guiCategory() {
      return this.guiCategory;
   }

   public List<ScriptSetting> settings() {
      return this.settings;
   }

   public int[] listens() {
      return this.listens;
   }

   public boolean listens(int type) {
      for (int candidate : this.listens) {
         if (candidate == type) {
            return true;
         }
      }

      return false;
   }

   public ScriptManifest withIdentity(String id, String name) {
      return new ScriptManifest(id, name, this.description, this.category, this.guiCategory, this.settings, this.listens);
   }

   public static ScriptManifest parse(String source, String fallbackId) throws ScriptException {
      String block = extract(source);
      JsonObject root = new JsonObject();

      if (block != null) {
         try {
            JsonElement parsed = new JsonParser().parse(block);
            if (parsed.isJsonObject()) {
               root = parsed.getAsJsonObject();
            }
         } catch (RuntimeException e) {
            throw new ScriptException("invalid manifest: " + e.getMessage(), e);
         }
      }

      String id = text(root, "id", fallbackId);
      if (id == null || id.trim().isEmpty()) {
         throw new ScriptException("manifest id must not be empty");
      }

      String name = text(root, "name", id);
      String description = text(root, "description", "");
      ModuleCategory category = category(text(root, "category", "utility"));
      List<ScriptSetting> settings = settings(root);
      int[] listens = listens(root);
      return new ScriptManifest(id.trim(), name.trim(), description, category, guiCategory(category), settings, listens);
   }

   private static ModuleCategory category(String value) {
      String text = value.trim().toLowerCase(Locale.ROOT);

      if (text.equals("combat") || text.equals("0")) {
         return ModuleCategory.COMBAT;
      } else if (text.equals("movement") || text.equals("move") || text.equals("1")) {
         return ModuleCategory.MOVEMENT;
      } else if (text.equals("render") || text.equals("visual") || text.equals("2")) {
         return ModuleCategory.RENDER;
      } else if (text.equals("blocks") || text.equals("block") || text.equals("world") || text.equals("3")) {
         return ModuleCategory.BLOCKS;
      }

      return ModuleCategory.UTILITY;
   }

   private static int guiCategory(ModuleCategory category) {
      switch (category) {
         case COMBAT:
            return 0;
         case MOVEMENT:
            return 1;
         case RENDER:
            return 2;
         case BLOCKS:
            return 3;
         default:
            return 4;
      }
   }

   private static List<ScriptSetting> settings(JsonObject root) {
      List<ScriptSetting> result = new ArrayList<>();
      JsonElement rows = root.get("settings");
      if (rows == null || !rows.isJsonArray()) {
         return result;
      }

      for (JsonElement element : rows.getAsJsonArray()) {
         if (!element.isJsonObject()) {
            continue;
         }

         JsonObject row = element.getAsJsonObject();
         String key = text(row, "key", null);
         if (key == null || key.trim().isEmpty()) {
            continue;
         }

         String type = text(row, "type", "boolean").trim().toLowerCase(Locale.ROOT);
         String label = text(row, "label", key);
         double minimum = number(row, "min", 0.0);
         double maximum = number(row, "max", 100.0);
         if (maximum < minimum) {
            double swap = minimum;
            minimum = maximum;
            maximum = swap;
         }

         double defaultValue = number(row, "default", type.equals("boolean") ? 0.0 : minimum);
         if (defaultValue < minimum) {
            defaultValue = minimum;
         }

         if (defaultValue > maximum) {
            defaultValue = maximum;
         }

         double step = number(row, "step", type.equals("number") ? 0.0 : 1.0);
         String[] options = new String[0];
         JsonElement choices = row.get("options");
         if (choices != null && choices.isJsonArray()) {
            List<String> labels = new ArrayList<>();

            for (JsonElement choice : choices.getAsJsonArray()) {
               if (choice.isJsonPrimitive()) {
                  labels.add(choice.getAsString());
               } else if (choice.isJsonObject()) {
                  labels.add(text(choice.getAsJsonObject(), "label", choice.getAsJsonObject().get("value").getAsString()));
               }
            }

            options = labels.toArray(new String[0]);
         }

         result.add(new ScriptSetting(key.trim(), label, type, defaultValue, minimum, maximum, step, options));
      }

      return result;
   }

   private static int[] listens(JsonObject root) {
      List<Integer> values = new ArrayList<>();
      JsonElement rows = root.get("listens");
      if (rows != null && rows.isJsonArray()) {
         for (JsonElement element : rows.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
               int type = element.getAsInt();
               if (type >= 0 && type < ScriptEvents.COUNT) {
                  values.add(type);
               }
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
               int type = ScriptEvents.byName(element.getAsString());
               if (type >= 0) {
                  values.add(type);
               }
            }
         }
      }

      int[] result = new int[values.size()];
      for (int i = 0; i < result.length; i++) {
         result[i] = values.get(i);
      }

      return result;
   }

   private static String text(JsonObject row, String key, String fallback) {
      JsonElement value = row.get(key);
      if (value != null && value.isJsonPrimitive()) {
         return value.getAsString();
      }

      return fallback;
   }

   private static double number(JsonObject row, String key, double fallback) {
      JsonElement value = row.get(key);
      if (value != null && value.isJsonPrimitive()) {
         com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
         if (primitive.isNumber()) {
            return primitive.getAsDouble();
         }

         if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? 1.0 : 0.0;
         }
      }

      return fallback;
   }

   private static String extract(String source) {
      String[] lines = source.split("\n", -1);
      StringBuilder buffer = new StringBuilder();
      boolean started = false;
      int depth = 0;

      for (String line : lines) {
         String trimmed = line.trim();
         if (!started) {
            if (trimmed.startsWith("// @slinky")) {
               started = true;
               String rest = trimmed.substring("// @slinky".length()).trim();
               buffer.append(rest);
               depth = depth(rest);
               if (depth <= 0 && buffer.toString().trim().length() > 0) {
                  break;
               }
            }

            continue;
         }

         buffer.append('\n').append(stripComment(trimmed));
         depth += depth(trimmed);
         if (depth <= 0 && buffer.toString().trim().length() > 0) {
            break;
         }
      }

      if (!started || buffer.toString().trim().isEmpty()) {
         return null;
      }

      return buffer.toString();
   }

   private static String stripComment(String line) {
      if (line.startsWith("//")) {
         return line.substring(2).trim();
      }

      if (line.startsWith("/*")) {
         return line.substring(2).trim();
      }

      return line;
   }

   private static int depth(String text) {
      int depth = 0;
      boolean quoted = false;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
            quoted = !quoted;
         } else if (!quoted && c == '{') {
            depth++;
         } else if (!quoted && c == '}') {
            depth--;
         }
      }

      return depth;
   }
}
