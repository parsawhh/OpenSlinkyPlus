package dev.slinky.script;

import dev.slinky.inject.Mappings;
import dev.slinky.inject.Platform;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
      Map<String, Object> result = new HashMap<String, Object>();
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
         result.put("sprinting", booleanField(player, ENTITY, "isSprinting"));
         result.put("vx", doubleField(player, ENTITY, "motionX"));
         result.put("vy", doubleField(player, ENTITY, "motionY"));
         result.put("vz", doubleField(player, ENTITY, "motionZ"));
         result.put("hurtTime", intField(player, ENTITY, "hurtTime"));
         result.put("fallDistance", floatField(player, ENTITY, "fallDistance"));
         result.put("minX", doubleField(player, ENTITY, "minX"));
         result.put("minY", doubleField(player, ENTITY, "minY"));
         result.put("minZ", doubleField(player, ENTITY, "minZ"));
         result.put("maxX", doubleField(player, ENTITY, "maxX"));
         result.put("maxY", doubleField(player, ENTITY, "maxY"));
         result.put("maxZ", doubleField(player, ENTITY, "maxZ"));
         result.put("health", floatMethod(player, LIVING, "getHealth", "()F"));
         result.put("maxHealth", floatMethod(player, LIVING, "getMaxHealth", "()F"));
         result.put("food", intMethod(player, PLAYER, "getFoodLevel", "()I"));
         result.put("name", stringMethod(player, ENTITY, "getName", "()Ljava/lang/String;"));
         result.put("id", intMethod(player, ENTITY, "getEntityId", "()I"));
         result.put("ticksExisted", intField(player, ENTITY, "ticksExisted"));
      } catch (Throwable e) {
         result.put("error", describe(e));
      }

      return result;
   }

   public List<Map<String, Object>> entities() {
      List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

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

         double x = 0.0D;
         double y = 0.0D;
         double z = 0.0D;
         if (player != null) {
            x = doubleField(player, ENTITY, "posX");
            y = doubleField(player, ENTITY, "posY");
            z = doubleField(player, ENTITY, "posZ");
         }

         for (Object entity : (List<?>)loaded) {
            if (entity == null || entity == player) {
               continue;
            }

            Map<String, Object> row = new HashMap<String, Object>();
            double ex = doubleField(entity, ENTITY, "posX");
            double ey = doubleField(entity, ENTITY, "posY");
            double ez = doubleField(entity, ENTITY, "posZ");
            String named = Mappings.unmapClass(entity.getClass().getName().replace('.', '/'));
            row.put("id", intMethod(entity, ENTITY, "getEntityId", "()I"));
            row.put("name", stringMethod(entity, ENTITY, "getName", "()Ljava/lang/String;"));
            row.put("type", named.substring(named.lastIndexOf('/') + 1));
            row.put("class", named.replace('/', '.'));
            row.put("player", named.contains("EntityPlayer"));
            row.put("x", ex);
            row.put("y", ey);
            row.put("z", ez);
            row.put("dx", ex - x);
            row.put("dy", ey - y);
            row.put("dz", ez - z);
            row.put("distance", Math.sqrt((ex - x) * (ex - x) + (ey - y) * (ey - y) + (ez - z) * (ez - z)));
            row.put("vx", doubleField(entity, ENTITY, "motionX"));
            row.put("vy", doubleField(entity, ENTITY, "motionY"));
            row.put("vz", doubleField(entity, ENTITY, "motionZ"));
            row.put("hurtTime", intField(entity, ENTITY, "hurtTime"));
            row.put("ticksExisted", intField(entity, ENTITY, "ticksExisted"));

            try {
               float health = floatMethod(entity, LIVING, "getHealth", "()F");
               row.put("health", health);
               row.put("dead", health <= 0.0F);
            } catch (Throwable ignored) {
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
         if (dead instanceof Boolean && ((Boolean)dead).booleanValue()) {
            continue;
         }

         double distance = row.get("distance") instanceof Number ? ((Number)row.get("distance")).doubleValue() : Double.MAX_VALUE;
         boolean player = Boolean.TRUE.equals(row.get("player"));
         double score = player ? distance : distance + 1000.0D;
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
         double pz = doubleField(player, ENTITY, "posZ");
         double dx = x - px;
         double dy = y - (py + 1.62D);
         double dz = z - pz;
         double horizontal = Math.sqrt(dx * dx + dz * dz);
         double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D;
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

   public File dataDirectory() {
      try {
         Object minecraft = minecraft();
         if (minecraft == null) {
            return null;
         }

         Object value = field(minecraft, MINECRAFT, "mcDataDir");
         return value instanceof File ? (File)value : null;
      } catch (Throwable e) {
         return null;
      }
   }

   public Map<String, Object> blockAt(int x, int y, int z) {
      Map<String, Object> result = new HashMap<String, Object>();

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
         int meta = 0;
         try {
            meta = ((Number)method(block, "net/minecraft/block/Block", "getMetaFromState", "(Lnet/minecraft/block/state/IBlockState;)I", state)).intValue();
         } catch (Throwable ignored) {
         }
         result.put("id", id);
         result.put("meta", meta);
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
      Map<String, Object> result = new HashMap<String, Object>();

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
         String itemClass = Mappings.unmapClass(item.getClass().getName().replace('.', '/'));
         result.put("id", id);
         result.put("name", String.valueOf(method(stack, "net/minecraft/item/ItemStack", "getDisplayName", "()Ljava/lang/String;")));
         result.put("class", itemClass.substring(itemClass.lastIndexOf('/') + 1));
         result.put("empty", false);
      } catch (Throwable e) {
         result.put("error", describe(e));
      }

      return result;
   }

   public Map<String, Object> hotbar(int slot) {
      Map<String, Object> result = new HashMap<String, Object>();

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
         String itemClass = Mappings.unmapClass(item.getClass().getName().replace('.', '/'));
         result.put("id", id);
         result.put("name", String.valueOf(method(stack, "net/minecraft/item/ItemStack", "getDisplayName", "()Ljava/lang/String;")));
         result.put("class", itemClass.substring(itemClass.lastIndexOf('/') + 1));
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

   public boolean placeBlock(int x, int y, int z, String face) {
      try {
         Object player = playerEntity();
         Object world = worldEntity();
         Object minecraft = minecraft();
         if (player == null || world == null || minecraft == null) {
            return false;
         }

         Object stack = method(player, PLAYER, "getCurrentEquippedItem", "()Lnet/minecraft/item/ItemStack;");
         Object controller = field(minecraft, MINECRAFT, "playerController");
         if (stack == null || controller == null) {
            return false;
         }

         String direction = face.toUpperCase(Locale.ROOT);
         int supportX = x;
         int supportY = y;
         int supportZ = z;
         double hitX = x + 0.5D;
         double hitY = y + 0.5D;
         double hitZ = z + 0.5D;
         if (direction.equals("UP")) {
            supportY = y - 1; hitY = y;
         } else if (direction.equals("DOWN")) {
            supportY = y + 1; hitY = y + 1.0D;
         } else if (direction.equals("NORTH")) {
            supportZ = z + 1; hitZ = z + 1.0D;
         } else if (direction.equals("SOUTH")) {
            supportZ = z - 1; hitZ = z;
         } else if (direction.equals("EAST")) {
            supportX = x - 1; hitX = x;
         } else if (direction.equals("WEST")) {
            supportX = x + 1; hitX = x + 1.0D;
         }

         Class<?> positionType = namedClass("net/minecraft/util/BlockPos");
         Object position = positionType.getConstructor(int.class, int.class, int.class).newInstance(supportX, supportY, supportZ);
         Class<?> facingType = namedClass("net/minecraft/util/EnumFacing");
         Object facing = facingType.getField(direction).get(null);
         Class<?> vectorType = namedClass("net/minecraft/util/Vec3");
         Object vector = vectorType.getConstructor(double.class, double.class, double.class).newInstance(hitX, hitY, hitZ);
         method(controller, "net/minecraft/client/multiplayer/PlayerControllerMP", "onPlayerRightClick",
            "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/Vec3;)Z",
            player, world, stack, position, facing, vector);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public int heldSlot() {
      try {
         Object player = playerEntity();
         if (player == null) {
            return -1;
         }

         Object inventory = method(player, PLAYER, "getInventory", "()Lnet/minecraft/entity/player/InventoryPlayer;");
         Field field = findField(inventory.getClass(), Mappings.mapField("net/minecraft/entity/player/InventoryPlayer", "currentItem"));
         return field.getInt(inventory);
      } catch (Throwable e) {
         return -1;
      }
   }

   public boolean selectSlot(int slot) {
      try {
         Object player = playerEntity();
         if (player == null) {
            return false;
         }

         Object inventory = method(player, PLAYER, "getInventory", "()Lnet/minecraft/entity/player/InventoryPlayer;");
         Field field = findField(inventory.getClass(), Mappings.mapField("net/minecraft/entity/player/InventoryPlayer", "currentItem"));
         field.setInt(inventory, slot);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean useItem() {
      try {
         Object minecraft = minecraft();
         if (minecraft == null) {
            return false;
         }

         method(minecraft, MINECRAFT, "rightClickMouse", "()V");
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean holdingBlock() {
      try {
         Object player = playerEntity();
         if (player == null) {
            return false;
         }

         Object stack = method(player, PLAYER, "getCurrentEquippedItem", "()Lnet/minecraft/item/ItemStack;");
         if (stack == null) {
            return false;
         }

         Object item = method(stack, "net/minecraft/item/ItemStack", "getItem", "()Lnet/minecraft/item/Item;");
         String named = Mappings.unmapClass(item.getClass().getName().replace('.', '/'));
         return named.contains("ItemBlock");
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean keyDown(String binding) {
      try {
         Object key = keyBinding(binding);
         if (key == null) return false;
         Object state = method(key, "net/minecraft/client/settings/KeyBinding", "isKeyDown", "()Z");
         return state instanceof Boolean && ((Boolean)state).booleanValue();
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean setKeyDown(String binding, boolean down) {
      try {
         Object key = keyBinding(binding);
         if (key == null) return false;
         Field pressed = findField(key.getClass(), Mappings.mapField("net/minecraft/client/settings/KeyBinding", "pressed"));
         pressed.setBoolean(key, down);
         // also update keybind state map for correctness
         try {
            int code = ((Number)method(key, "net/minecraft/client/settings/KeyBinding", "getKeyCode", "()I")).intValue();
            Class<?> kb = key.getClass();
            Method setState = findMethod(kb, Mappings.mapMethod("net/minecraft/client/settings/KeyBinding", "setKeyBindState", "(IZ)V"), 2);
            if (Modifier.isStatic(setState.getModifiers())) {
               setState.invoke(null, code, down);
            }
         } catch (Throwable ignored) {
         }
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   private Object keyBinding(String binding) {
      try {
         Object minecraft = minecraft();
         if (minecraft == null) return null;
         Object settings = method(minecraft, MINECRAFT, "gameSettings", "()Lnet/minecraft/client/settings/GameSettings;");
         if (settings == null) return null;
         String field = "keyBind" + binding;
         Field key = findField(settings.getClass(), Mappings.mapField("net/minecraft/client/settings/GameSettings", field));
         return key.get(settings);
      } catch (Throwable e) {
         return null;
      }
   }

   public boolean attack(String targetName) {
      try {
         Object target = targetName == null ? null : entityByName(targetName);
         return this.attackEntity(target);
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean attackEntity(Object target) {
      try {
         Object player = playerEntity();
         if (player == null || target == null) {
            return false;
         }

         method(player, PLAYER, "attackTargetEntityWithCurrentItem", "(Lnet/minecraft/entity/Entity;)V", target);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean message(String text) {
      try {
         Object player = playerEntity();
         if (player == null) {
            return false;
         }

         Class<?> component = Class.forName(Mappings.mapClass("net/minecraft/util/ChatComponentText").replace('/', '.'), false, this.loader());
         Object built = component.getConstructor(String.class).newInstance(text);
         Method add = findMethod(player.getClass(), Mappings.mapMethod(PLAYER, "addChatMessage", "(Lnet/minecraft/util/IChatComponent;)V"), 1);
         add.invoke(player, built);
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

   public ScriptRef entityById(int id) {
      try {
         Object world = worldEntity();
         if (world == null) return null;
         Object loaded = field(world, WORLD, "loadedEntityList");
         if (!(loaded instanceof List)) return null;
         for (Object entity : (List<?>)loaded) {
            if (entity == null) continue;
            int eid = ((Number)method(entity, ENTITY, "getEntityId", "()I")).intValue();
            if (eid == id) return ScriptRef.of(entity);
         }
      } catch (Throwable ignored) {
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

   // --- raw refs ---
   public ScriptRef mcRef() { try { return ScriptRef.of(minecraft()); } catch (Throwable e) { return null; } }
   public ScriptRef playerRef() { return ScriptRef.of(playerEntity()); }
   public ScriptRef worldRef() { return ScriptRef.of(worldEntity()); }
   public ScriptRef entityRef(String name) { return ScriptRef.of(entityByName(name)); }

   // --- packets ---
   public boolean sendPacket(Object packet) {
      if (packet == null) return false;
      Object raw = packet instanceof ScriptRef ? ((ScriptRef)packet).raw() : packet;
      try {
         Object mc = minecraft();
         if (mc == null) return false;
         Object handler = method(mc, MINECRAFT, "getNetHandler", "()Lnet/minecraft/client/network/NetHandlerPlayClient;");
         if (handler == null) return false;
         Method add = findMethod(handler.getClass(), Mappings.mapMethod("net/minecraft/client/network/NetHandlerPlayClient", "addToSendQueue", "(Lnet/minecraft/network/Packet;)V"), 1);
         add.invoke(handler, raw);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public ScriptRef newPacket(String className) { return newPacket(className, new Object[0]); }
   public ScriptRef newPacket(String className, Object[] args) {
      try {
         String internal = className.replace('.', '/');
         Class<?> type = namedClass(internal);
         Object[] a = args == null ? new Object[0] : args;
         // try constructors by arity
         for (java.lang.reflect.Constructor<?> ctor : type.getConstructors()) {
            if (ctor.getParameterTypes().length != a.length) continue;
            try {
               Object[] coerced = coerceArgs(ctor.getParameterTypes(), a);
               ctor.setAccessible(true);
               return ScriptRef.of(ctor.newInstance(coerced));
            } catch (Throwable ignored) {
            }
         }
         // try declared
         for (java.lang.reflect.Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.getParameterTypes().length != a.length) continue;
            try {
               Object[] coerced = coerceArgs(ctor.getParameterTypes(), a);
               ctor.setAccessible(true);
               return ScriptRef.of(ctor.newInstance(coerced));
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   public boolean sendSprintPacket(boolean start) {
      try {
         Object player = playerEntity();
         if (player == null) return false;
         // try enum action first (1.8.9)
         try {
            Class<?> actionType = namedClass("net/minecraft/network/play/client/C0BPacketEntityAction$Action");
            Object action = null;
            for (Object c : actionType.getEnumConstants()) {
               String n = String.valueOf(c);
               if (start && n.contains("START_SPRINTING")) { action = c; break; }
               if (!start && n.contains("STOP_SPRINTING")) { action = c; break; }
            }
            if (action != null) {
               Class<?> pkt = namedClass("net/minecraft/network/play/client/C0BPacketEntityAction");
               // ctor (Entity, Action) or (Entity, EntityPlayer$EnumAction) in some mappings
               for (java.lang.reflect.Constructor<?> ctor : pkt.getDeclaredConstructors()) {
                  if (ctor.getParameterTypes().length == 2) {
                     try {
                        ctor.setAccessible(true);
                        Object p = ctor.newInstance(player, action);
                        return sendPacket(p);
                     } catch (Throwable ignored) {}
                  }
               }
            }
         } catch (Throwable ignored) {
         }
         // fallback: set sprinting state (server syncs)
         return setSprint(start);
      } catch (Throwable e) {
         return false;
      }
   }

   public boolean setSprint(boolean sprinting) {
      try {
         Object player = playerEntity();
         if (player == null) return false;
         method(player, ENTITY, "setSprinting", "(Z)V", sprinting);
         return true;
      } catch (Throwable e) {
         // try field
         try {
            Object player = playerEntity();
            if (player == null) return false;
            Field f = findField(player.getClass(), Mappings.mapField(ENTITY, "isSprinting"));
            f.setBoolean(player, sprinting);
            return true;
         } catch (Throwable ignored) {
         }
         return false;
      }
   }

   public boolean isSprinting() {
      try {
         Object player = playerEntity();
         if (player == null) return false;
         Object v = method(player, ENTITY, "isSprinting", "()Z");
         return v instanceof Boolean && ((Boolean)v).booleanValue();
      } catch (Throwable e) {
         return false;
      }
   }

   public double distanceToEntity(Object target) {
      try {
         Object player = playerEntity();
         if (player == null || target == null) return Double.MAX_VALUE;
         Object raw = target instanceof ScriptRef ? ((ScriptRef)target).raw() : target;
         double px = doubleField(player, ENTITY, "posX");
         double py = doubleField(player, ENTITY, "posY");
         double pz = doubleField(player, ENTITY, "posZ");
         double ex = doubleField(raw, ENTITY, "posX");
         double ey = doubleField(raw, ENTITY, "posY");
         double ez = doubleField(raw, ENTITY, "posZ");
         double dx = px - ex, dy = py - ey, dz = pz - ez;
         return Math.sqrt(dx*dx + dy*dy + dz*dz);
      } catch (Throwable e) {
         return Double.MAX_VALUE;
      }
   }

   // --- files ---
   public String dataDirPath() {
      File dir = dataDirectory();
      return dir == null ? null : dir.getAbsolutePath();
   }

   private File safeFile(String path) {
      if (path == null) return null;
      File base = dataDirectory();
      if (base == null) return null;
      try {
         File target = path.startsWith("/") ? new File(path) : new File(base, path);
         String canonBase = base.getCanonicalPath();
         String canonTarget = target.getCanonicalPath();
         if (!canonTarget.startsWith(canonBase)) {
            // allow scripts subdir outside? no, restrict to data dir
            return null;
         }
         return target;
      } catch (Throwable e) {
         return null;
      }
   }

   public String readFile(String path) {
      try {
         File f = safeFile(path);
         if (f == null || !f.isFile()) return null;
         byte[] data = Files.readAllBytes(f.toPath());
         return new String(data, StandardCharsets.UTF_8);
      } catch (Throwable e) {
         return null;
      }
   }

   public boolean writeFile(String path, String content) {
      try {
         File f = safeFile(path);
         if (f == null) return false;
         File parent = f.getParentFile();
         if (parent != null) parent.mkdirs();
         Files.write(f.toPath(), (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public List<String> listFiles(String path) {
      List<String> result = new ArrayList<String>();
      try {
         File f = safeFile(path == null || path.isEmpty() ? "." : path);
         if (f == null || !f.isDirectory()) return result;
         File[] files = f.listFiles();
         if (files == null) return result;
         for (File child : files) {
            result.add(child.getName() + (child.isDirectory() ? "/" : ""));
         }
      } catch (Throwable ignored) {
      }
      return result;
   }

   // --- reflection internals ---
   private Object staticMethod(String owner, String name, String descriptor, Object... args) throws Exception {
      Class<?> type = namedClass(owner);
      Method method = findMethod(type, Mappings.mapMethod(owner, name, descriptor), args.length);
      Object[] coerced = coerceArgs(method.getParameterTypes(), args);
      return method.invoke(null, coerced);
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

   private int intField(Object target, String owner, String name) throws Exception {
      Field field = findField(target.getClass(), Mappings.mapField(owner, name));
      return field.getInt(target);
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
      Object[] coerced = coerceArgs(method.getParameterTypes(), args);
      return method.invoke(target, coerced);
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

   private static Object[] coerceArgs(Class<?>[] types, Object[] args) {
      Object[] out = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
         Object a = i < args.length ? args[i] : null;
         if (a instanceof ScriptRef) a = ((ScriptRef)a).raw();
         out[i] = coerce(a, types[i]);
      }
      return out;
   }

   private static Object coerce(Object value, Class<?> type) {
      if (value == null) return null;
      if (type.isInstance(value)) return value;
      if (type == String.class) return String.valueOf(value);
      if (type == boolean.class || type == Boolean.class) {
         if (value instanceof Boolean) return value;
         if (value instanceof Number) return ((Number)value).intValue() != 0;
         return Boolean.valueOf(String.valueOf(value));
      }
      if (type == int.class || type == Integer.class) {
         if (value instanceof Number) return ((Number)value).intValue();
         return Integer.valueOf((int)Double.parseDouble(String.valueOf(value)));
      }
      if (type == float.class || type == Float.class) {
         if (value instanceof Number) return ((Number)value).floatValue();
         return Float.valueOf((float)Double.parseDouble(String.valueOf(value)));
      }
      if (type == double.class || type == Double.class) {
         if (value instanceof Number) return ((Number)value).doubleValue();
         return Double.valueOf(Double.parseDouble(String.valueOf(value)));
      }
      if (type == long.class || type == Long.class) {
         if (value instanceof Number) return ((Number)value).longValue();
         return Long.valueOf((long)Double.parseDouble(String.valueOf(value)));
      }
      return value;
   }

   private static String describe(Throwable error) {
      String message = error.getMessage();
      return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
   }
}
