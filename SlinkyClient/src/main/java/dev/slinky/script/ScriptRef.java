package dev.slinky.script;

import dev.slinky.inject.Mappings;
import dev.slinky.inject.Platform;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MC-free reflective wrapper so scripts can access raw game objects ("raw bridge" / reflect). Uses Mappings
 * to translate named class/field/method identifiers at runtime for named/srg/official payloads.
 */
public final class ScriptRef {
   private final Object target;

   private ScriptRef(Object target) {
      this.target = target;
   }

   public static ScriptRef of(Object target) {
      return target == null ? null : new ScriptRef(target);
   }

   public Object raw() {
      return this.target;
   }

   public boolean isNull() {
      return this.target == null;
   }

   public String className() {
      if (this.target == null) {
         return "null";
      }

      return Mappings.unmapClass(this.target.getClass().getName().replace('.', '/')).replace('/', '.');
   }

   public Object get(String field) {
      if (this.target == null || field == null) {
         return null;
      }

      try {
         Field f = findField(this.target.getClass(), field);
         Object value = f.get(Modifier.isStatic(f.getModifiers()) ? null : this.target);
         return adapt(value);
      } catch (Throwable e) {
         return null;
      }
   }

   public boolean set(String field, Object value) {
      if (this.target == null || field == null) {
         return false;
      }

      try {
         Field f = findField(this.target.getClass(), field);
         f.set(Modifier.isStatic(f.getModifiers()) ? null : this.target, coerce(value, f.getType()));
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public Object call(String method, Object... args) {
      if (this.target == null || method == null) {
         return null;
      }

      try {
         Object[] a = args == null ? new Object[0] : args;
         Method m = findMethod(this.target.getClass(), method, a.length);
         Object[] coerced = coerceArgs(m.getParameterTypes(), a);
         Object result = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : this.target, coerced);
         return adapt(result);
      } catch (Throwable e) {
         return null;
      }
   }

   public List<String> fields() {
      List<String> names = new ArrayList<String>();
      if (this.target == null) {
         return names;
      }

      for (Class<?> c = this.target.getClass(); c != null; c = c.getSuperclass()) {
         for (Field f : c.getDeclaredFields()) {
            names.add(f.getName());
         }
      }

      return names;
   }

   public boolean has(String name) {
      if (this.target == null || name == null) {
         return false;
      }

      try {
         findField(this.target.getClass(), name);
         return true;
      } catch (Throwable e) {
         return findMethodQuiet(this.target.getClass(), name) != null;
      }
   }

   public String toString() {
      return this.target == null ? "null" : String.valueOf(this.target);
   }

   private static Field findField(Class<?> type, String name) {
      String namedOwner = Mappings.unmapClass(type.getName().replace('.', '/'));
      String mapped = Mappings.mapField(namedOwner, name);

      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         Field f = tryField(c, mapped);
         if (f != null) {
            return f;
         }

         if (!mapped.equals(name)) {
            f = tryField(c, name);
            if (f != null) {
               return f;
            }
         }
      }

      // also try walking named supertypes via Mappings (field may be declared on MC super in named form)
      // best-effort: try the given name on all supers again (already done). give up.
      throw new IllegalStateException("missing field " + name);
   }

   private static Field tryField(Class<?> c, String runtimeName) {
      try {
         Field f = c.getDeclaredField(runtimeName);
         f.setAccessible(true);
         return f;
      } catch (NoSuchFieldException e) {
         return null;
      }
   }

   private static Method findMethod(Class<?> type, String name, int arity) {
      Method m = findMethodQuiet(type, name, arity);
      if (m != null) {
         return m;
      }

      throw new IllegalStateException("missing method " + name + "/" + arity);
   }

   private static Method findMethodQuiet(Class<?> type, String name) {
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
               m.setAccessible(true);
               return m;
            }
         }
      }

      return null;
   }

   private static Method findMethodQuiet(Class<?> type, String name, int arity) {
      String namedOwner = Mappings.unmapClass(type.getName().replace('.', '/'));
      // try common descriptor-less lookup: map by name on named owner across hierarchy
      // We don't know descriptor, so try exact runtime name match by arity; also try mapped name candidates.
      // Strategy: attempt mapping with a best-effort parameter descriptor is hard without types; instead scan.
      // But we can try Mappings.mapMethod with no descriptor by scanning declared methods and comparing unmapped.
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         Method best = null;
         for (Method m : c.getDeclaredMethods()) {
            if (m.getParameterTypes().length != arity) {
               continue;
            }

            if (m.getName().equals(name)) {
               m.setAccessible(true);
               return m;
            }

            // try mapped name: build named descriptor guess not possible; but compare by attempting mapMethod on owner chain
            // fall back: match by mapped method name for each known mapping? too expensive. accept name match.
            // also try: m.getName() equals Mappings.mapMethod(namedOwner,name,descriptor?) unknown.
            // As a pragmatic approach: check if mapping the declared runtime name back gives our named name? no reverse map.
            best = null;
         }
      }

      // second pass: try mapped runtime name by asking Mappings with guessed signatures is impossible.
      // alternative: try Mappings.mapMethod for each declared method by constructing a descriptor from runtime params (mapped to named)
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         for (Method m : c.getDeclaredMethods()) {
            if (m.getParameterTypes().length != arity) {
               continue;
            }

            try {
               String runtime = m.getName();
               String mappedCandidate = Mappings.mapMethod(namedOwner, name, namedDescriptorOf(m));
               if (mappedCandidate.equals(runtime)) {
                  m.setAccessible(true);
                  return m;
               }

               String mappedByParams = Mappings.mapMethodByParameters(namedOwner, name, namedParametersOf(m));
               if (mappedByParams.equals(runtime)) {
                  m.setAccessible(true);
                  return m;
               }
            } catch (Throwable ignored) {
            }
         }
      }

      return null;
   }

   private static String namedDescriptorOf(Method m) {
      StringBuilder b = new StringBuilder("(");
      for (Class<?> p : m.getParameterTypes()) {
         b.append(toDescriptor(Mappings.unmapClass(p.getName().replace('.', '/'))));
      }
      b.append(')').append(toDescriptor(Mappings.unmapClass(m.getReturnType().getName().replace('.', '/'))));
      return b.toString();
   }

   private static String namedParametersOf(Method m) {
      StringBuilder b = new StringBuilder("(");
      for (Class<?> p : m.getParameterTypes()) {
         b.append(toDescriptor(Mappings.unmapClass(p.getName().replace('.', '/'))));
      }
      b.append(')');
      return b.toString();
   }

   private static String toDescriptor(String internalOrName) {
      if (internalOrName == null) {
         return "Ljava/lang/Object;";
      }

      String n = internalOrName.replace('.', '/');
      if (n.equals("void")) return "V";
      if (n.equals("boolean")) return "Z";
      if (n.equals("byte")) return "B";
      if (n.equals("char")) return "C";
      if (n.equals("short")) return "S";
      if (n.equals("int")) return "I";
      if (n.equals("long")) return "J";
      if (n.equals("float")) return "F";
      if (n.equals("double")) return "D";
      if (n.startsWith("[")) return n;
      return "L" + n + ";";
   }

   private static Object adapt(Object value) {
      if (value == null) {
         return null;
      }

      if (value instanceof Boolean || value instanceof Number || value instanceof String) {
         return value;
      }

      // keep maps/lists as-is (ScriptValues will wrap maps); wrap arbitrary objects
      if (value instanceof Map || value instanceof List) {
         return value;
      }

      return new ScriptRef(value);
   }

   private static Object[] coerceArgs(Class<?>[] types, Object[] args) {
      Object[] out = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
         Object a = i < args.length ? args[i] : null;
         out[i] = coerce(a, types[i]);
      }

      return out;
   }

   private static Object coerce(Object value, Class<?> type) {
      if (value instanceof ScriptRef) {
         value = ((ScriptRef)value).raw();
      }

      if (value == null) {
         if (type.isPrimitive()) {
            if (type == boolean.class) return Boolean.FALSE;
            if (type == int.class) return Integer.valueOf(0);
            if (type == long.class) return Long.valueOf(0L);
            if (type == float.class) return Float.valueOf(0.0F);
            if (type == double.class) return Double.valueOf(0.0D);
            if (type == byte.class) return Byte.valueOf((byte)0);
            if (type == short.class) return Short.valueOf((short)0);
            if (type == char.class) return Character.valueOf('\0');
         }

         return null;
      }

      if (type == String.class) {
         return String.valueOf(value);
      }

      if (type == boolean.class || type == Boolean.class) {
         if (value instanceof Boolean) return value;
         if (value instanceof Number) return ((Number)value).intValue() != 0;
         return Boolean.valueOf(String.valueOf(value));
      }

      if (type == int.class || type == Integer.class) {
         if (value instanceof Number) return ((Number)value).intValue();
         return Integer.valueOf((int)Double.parseDouble(String.valueOf(value)));
      }

      if (type == long.class || type == Long.class) {
         if (value instanceof Number) return ((Number)value).longValue();
         return Long.valueOf((long)Double.parseDouble(String.valueOf(value)));
      }

      if (type == float.class || type == Float.class) {
         if (value instanceof Number) return ((Number)value).floatValue();
         return Float.valueOf((float)Double.parseDouble(String.valueOf(value)));
      }

      if (type == double.class || type == Double.class) {
         if (value instanceof Number) return ((Number)value).doubleValue();
         return Double.valueOf(Double.parseDouble(String.valueOf(value)));
      }

      if (type == byte.class || type == Byte.class) {
         if (value instanceof Number) return ((Number)value).byteValue();
         return Byte.valueOf((byte)Integer.parseInt(String.valueOf(value)));
      }

      if (type == short.class || type == Short.class) {
         if (value instanceof Number) return ((Number)value).shortValue();
         return Short.valueOf((short)Integer.parseInt(String.valueOf(value)));
      }

      if (type.isInstance(value)) {
         return value;
      }

      return value;
   }
}
