package dev.slinky.script;

import dev.slinky.inject.Mappings;
import dev.slinky.inject.Platform;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScriptGame {
   private static final String MINECRAFT = "net/minecraft/client/Minecraft";
   private static final String ENTITY = "net/minecraft/entity/Entity";
   private static final String LIVING = "net/minecraft/entity/EntityLivingBase";
   private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
   private static final String WORLD = "net/minecraft/world/World";

   private volatile ClassLoader loader;

   private ClassLoader loader() {
      ClassLoader current = this.loader;
      if (current == null) {
         try {
            current = Platform.getGameClassLoader();
         } catch (Throwable e) {
            current = ScriptGame.class.getClassLoader();
         }

         this.loader = current;
      }

      return current;
   }

   public Map<String, Object> player() {
      Map<String, Object> result = new HashMap<>();
      result.put("inWorld", false);

      try {
         Object player = playerEntity();
         if (player == null) {
            return result;
         }

         result.put("inWorld", true);
         result.put("x", doubleField(player, ENTITY, "posX"));
         result.put("y", doubleField(player, ENTITY, "posY"));
         result.put("z", doubleField(player, ENTITY, "posZ"));
         result.put("yaw", floatField(player, ENTITY, "rotationYaw"));
         result.put("pitch", floatField(player, ENTITY, "rotationPitch"));
         result.put("onGround", booleanField(player, ENTITY, "onGround"));
         result.put("sneaking", booleanField(player, ENTITY, "isSneaking"));
         result.put("sprinting", booleanField(player, LIVING, "isSprinting"));
         result.put("health", floatMethod(player, LIVING, "getHealth", "()F"));
         result.put("maxHealth", floatMethod(player, LIVING, "getMaxHealth", "()F"));
         result.put("food", intMethod(player, PLAYER, "getFoodLevel", "()I"));
         result.put("name", stringMethod(player, ENTITY, "getName", "()Ljava/lang/String;"));
         result.put("id", intMethod(player, ENTITY, "getEntityId", "()I"));
      } catch (Throwable e) {
         result.put("error", describe(e));
      }

      return result;
   }

   public List<Map<String, Object>> entities() {
      List<Map<String, Object>> result = new ArrayList<>();

      try {
         Object player = playerEntity();
         Object world = worldEntity();
         if (world == null) {
            return result;
         }

         Object loaded = field(world, WORLD, "loadedEntityList");
         if (!(loaded instanceof List)) {
            return result;
         }

         double x = 0.0;
         double y = 0.0;
         double z = 0.0;
         if (player != null) {
            x = doubleField(player, ENTITY, "posX");
            y = doubleField(player, ENTITY, "posY");
            z = doubleField(player, ENTITY, "posZ");
         }

         for (Object entity : (List<?>)loaded) {
            if (entity == null || entity == player) {
               continue;
            }

            Map<String, Object> row = new HashMap<>();
            double ex = doubleField(entity, ENTITY, "posX");
            double ey = doubleField(entity, ENTITY, "posY");
            double ez = doubleField(entity, ENTITY, "posZ");
            String named = Mappings.unmapClass(entity.getClass().getName().replace('.', '/'));
            row.put("id", intMethod(entity, ENTITY, "getEntityId", "()I"));
            row.put("name", stringMethod(entity, ENTITY, "getName", "()Ljava/lang/String;"));
            row.put("type", named.substring(named.lastIndexOf('/') + 1));
            row.put("player", named.contains("EntityPlayer"));
            row.put("x", ex);
            row.put("y", ey);
            row.put("z", ez);
            row.put("dx", ex - x);
            row.put("dy", ey - y);
            row.put("dz", ez - z);
            row.put("distance", Math.sqrt((ex - x) * (ex - x) + (ey - y) * (ey - y) + (ez - z) * (ez - z)));

            if (named.contains("EntityLivingBase")) {
               float health = floatMethod(entity, LIVING, "getHealth", "()F");
               row.put("health", health);
               row.put("dead", health <= 0.0F);
            }

            result.add(row);
         }
      } catch (Throwable e) {
         result.add(Collections.singletonMap("error", describe(e)));
      }

      return result;
   }

   public Map<String, Object> target() {
      Map<String, Object> best = null;
      double bestScore = Double.MAX_VALUE;

      for (Map<String, Object> row : entities()) {
         if (row.containsKey("error")) {
            continue;
         }

         Object dead = row.get("dead");
         if (dead instanceof Boolean && (Boolean)dead) {
            continue;
         }

         double distance = row.get("distance") instanceof Number ? ((Number)row.get("distance")).doubleValue() : Double.MAX_VALUE;
         boolean player = Boolean.TRUE.equals(row.get("player"));
         double score = player ? distance : distance + 1000.0;
         if (score < bestScore) {
            bestScore = score;
            best = row;
         }
      }

      return best;
   }

   public double[] angles(double x, double y, double z) {
      try {
         Object player = playerEntity();
         if (player == null) {
            return null;
         }

         double px = doubleField(player, ENTITY, "posX");
         double py = doubleField(player, ENTITY, "posY");
         double ez = doubleField(player, ENTITY, "posZ");
         double dx = x - px;
         double dy = y - (py + 1.62);
         double dz = z - ez;
         double horizontal = Math.sqrt(dx * dx + dz * dz);
         double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
         double pitch = -Math.toDegrees(Math.atan2(dy, horizontal));
         return new double[]{yaw, pitch};
      } catch (Throwable e) {
         return null;
      }
   }

   public Object playerEntity() {
      try {
         Object minecraft = minecraft();
         return minecraft == null ? null : field(minecraft, MINECRAFT, "thePlayer");
      } catch (Throwable e) {
         return null;
      }
   }

   public Object worldEntity() {
      try {
         Object minecraft = minecraft();
         return minecraft == null ? null : field(minecraft, MINECRAFT, "theWorld");
      } catch (Throwable e) {
         return null;
      }
   }

   public java.io.File dataDirectory() {
      try {
         Object minecraft = minecraft();
         if (minecraft == null) {
            return null;
         }

         Object value = field(minecraft, MINECRAFT, "mcDataDir");
         return value instanceof java.io.File ? (java.io.File)value : null;
      } catch (Throwable e) {
         return null;
      }
   }

   public Map<String, Object> blockAt(int x, int y, int z) {
      Map<String, Object> result = new HashMap<>();

      try {
         Object world = worldEntity();
         if (world == null) {
            result.put("air", true);
            return result;
         }

         Class<?> positionType = namedClass("net/minecraft/util/BlockPos");
         Object position = positionType.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
         Object state = method(world, WORLD, "getBlockState", "(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;", position);
         Object block = method(state, "net/minecraft/block/state/IBlockState", "getBlock", "()Lnet/minecraft/block/Block;");
         int id = ((Number)staticMethod("net/minecraft/block/Block", "getIdFromBlock", "(Lnet/minecraft/block/Block;)I", block)).intValue();
         String name = String.valueOf(method(block, "net/minecraft/block/Block", "getUnlocalizedName", "()Ljava/lang/String;"));
         result.put("id", id);
         result.put("name", name);
         result.put("air", id == 0);
         result.put("x", x);
         result.put("y", y);
         result.put("z", z);
      } catch (Throwable e) {
         result.put("error", describe(e));
      }

      return result;
   }

   public Map<String, Object> heldItem() {
      Map<String, Object> result = new HashMap<>();

      try {
         Object player = playerEntity();
         if (player == null) {
            return result;
         }

         Object stack = method(player, PLAYER, "getCurrentEquippedItem", "()Lnet/minecraft/item/ItemStack;");
         if (stack == null) {
            result.put("empty", true);
            return result;
         }

         Object item = method(stack, "net/minecraft/item/ItemStack", "getItem", "()Lnet/minecraft/item/Item;");
         int id = ((Number)staticMethod("net/minecraft/item/Item", "getIdFromItem", "(Lnet/minecraft/item/Item;)I", item)).intValue();
         result.put("id", id);
         result.put("name", String.valueOf(method(stack, "net/minecraft/item/ItemStack", "getDisplayName", "()Ljava/lang/String;")));
         result.put("empty", false);
      } catch (Throwable e) {
         result.put("error", describe(e));
      }

      return result;
   }

   public Map<String, Object> hotbar(int slot) {
      Map<String, Object> result = new HashMap<>();

      try {
         Object player = playerEntity();
         if (player == null) {
            return result;
         }

         Object inventory = method(player, PLAYER, "getInventory", "()Lnet/minecraft/entity/player/InventoryPlayer;");
         Object stack = method(inventory, "net/minecraft/entity/player/InventoryPlayer", "getStackInSlot", "(I)Lnet/minecraft/item/ItemStack;", slot);
         if (stack == null) {
            result.put("empty", true);
            return result;
         }

         Object item = method(stack, "net/minecraft/item/ItemStack", "getItem", "()Lnet/minecraft/item/Item;");
         int id = ((Number)staticMethod("net/minecraft/item/Item", "getIdFromItem", "(Lnet/minecraft/item/Item;)I", item)).intValue();
         result.put("id", id);
         result.put("name", String.valueOf(method(stack, "net/minecraft/item/ItemStack", "getDisplayName", "()Ljava/lang/String;")));
         result.put("slot", slot);
         result.put("empty", false);
      } catch (Throwable e) {
         result.put("error", describe(e));
      }

      return result;
   }

   public boolean swing() {
      try {
         Object player = playerEntity();
         if (player == null) {
            return false;
         }

         method(player, PLAYER, "swingItem", "()V");
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean attack(String targetName) {
      try {
         Object player = playerEntity();
         Object target = targetName == null ? null : entityByName(targetName);
         if (player == null || target == null) {
            return false;
         }

         method(player, PLAYER, "attackTargetEntityWithCurrentItem", "(Lnet/minecraft/entity/Entity;)V", target);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean say(String message) {
      try {
         Object player = playerEntity();
         if (player == null) {
            return false;
         }

         method(player, PLAYER, "sendChatMessage", "(Ljava/lang/String;)V", message);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public Object entityByName(String name) {
      try {
         Object world = worldEntity();
         if (world == null) {
            return null;
         }

         Object loaded = field(world, WORLD, "loadedEntityList");
         if (!(loaded instanceof List)) {
            return null;
         }

         for (Object entity : (List<?>)loaded) {
            if (entity == null) {
               continue;
            }

            Object label = method(entity, ENTITY, "getName", "()Ljava/lang/String;");
            if (label != null && String.valueOf(label).equalsIgnoreCase(name)) {
               return entity;
            }
         }
      } catch (Throwable e) {
         return null;
      }

      return null;
   }

   public int relationshipFlags(String entityName) {
      try {
         Object entity = entityByName(entityName);
         if (entity == null) {
            return 0;
         }

         Object value = staticMethod("dev/slinky/client/target/RelationshipService", "flags", "(Lnet/minecraft/entity/Entity;)I", entity);
         return value instanceof Number ? ((Number)value).intValue() : 0;
      } catch (Throwable e) {
         return 0;
      }
   }

   public boolean setFriend(String entityName, boolean friend) {
      try {
         Object entity = entityByName(entityName);
         if (entity == null) {
            return false;
         }

         staticMethod("dev/slinky/client/target/RelationshipService", "set", "(Lnet/minecraft/entity/Entity;IZ)V", entity, 1, friend);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   private Object staticMethod(String owner, String name, String descriptor, Object... args) throws Exception {
      Class<?> type = namedClass(owner);
      Method method = findMethod(type, Mappings.mapMethod(owner, name, descriptor), args.length);
      return method.invoke(null, args);
   }

   private Object minecraft() throws Exception {
      Class<?> type = namedClass(MINECRAFT);
      String name = Mappings.mapMethod(MINECRAFT, "getMinecraft", "()Lnet/minecraft/client/Minecraft;");
      Method method = findMethod(type, name, 0);
      return method.invoke(null);
   }

   private Class<?> namedClass(String internalName) throws ClassNotFoundException {
      return Class.forName(Mappings.mapClass(internalName).replace('/', '.'), false, this.loader());
   }

   private Object field(Object target, String owner, String name) throws Exception {
      Field field = findField(target.getClass(), Mappings.mapField(owner, name));
      return field.get(target);
   }

   private double doubleField(Object target, String owner, String name) throws Exception {
      Field field = findField(target.getClass(), Mappings.mapField(owner, name));
      return field.getDouble(target);
   }

   private float floatField(Object target, String owner, String name) throws Exception {
      Field field = findField(target.getClass(), Mappings.mapField(owner, name));
      return field.getFloat(target);
   }

   private boolean booleanField(Object target, String owner, String name) throws Exception {
      Field field = findField(target.getClass(), Mappings.mapField(owner, name));
      return field.getBoolean(target);
   }

   private float floatMethod(Object target, String owner, String name, String descriptor) throws Exception {
      Object value = method(target, owner, name, descriptor);
      return value instanceof Number ? ((Number)value).floatValue() : 0.0F;
   }

   private int intMethod(Object target, String owner, String name, String descriptor) throws Exception {
      Object value = method(target, owner, name, descriptor);
      return value instanceof Number ? ((Number)value).intValue() : 0;
   }

   private String stringMethod(Object target, String owner, String name, String descriptor) throws Exception {
      Object value = method(target, owner, name, descriptor);
      return value == null ? "" : String.valueOf(value);
   }

   private Object method(Object target, String owner, String name, String descriptor, Object... args) throws Exception {
      String runtimeName = Mappings.mapMethod(owner, name, descriptor);
      Method method = findMethod(target.getClass(), runtimeName, arity(descriptor));
      return method.invoke(target, args);
   }

   private static int arity(String descriptor) {
      int count = 0;
      int index = descriptor.indexOf('(');

      while (index >= 0 && index + 1 < descriptor.length() && descriptor.charAt(index + 1) != ')') {
         char c = descriptor.charAt(++index);
         if (c == 'L') {
            while (index < descriptor.length() && descriptor.charAt(index) != ';') {
               index++;
            }
         } else if (c == '[') {
            continue;
         }

         count++;
      }

      return count;
   }

   private static Field findField(Class<?> type, String runtimeName) {
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
         try {
            Field field = current.getDeclaredField(runtimeName);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException ignored) {
         }
      }

      throw new IllegalStateException("missing field " + runtimeName + " on " + type.getName());
   }

   private static Method findMethod(Class<?> type, String runtimeName, int arity) {
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
         for (Method method : current.getDeclaredMethods()) {
            if (method.getName().equals(runtimeName) && method.getParameterTypes().length == arity) {
               method.setAccessible(true);
               return method;
            }
         }
      }

      throw new IllegalStateException("missing method " + runtimeName + " on " + type.getName());
   }

   private static String describe(Throwable error) {
      String message = error.getMessage();
      return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
   }
}
