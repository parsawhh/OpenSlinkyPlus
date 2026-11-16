package dev.slinky.script;

import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;

/**
 * Makes the java maps that the api returns behave like plain script objects, so a script can read
 * player.health instead of player.get("health"). The api itself stays free of engine types.
 */
public final class ScriptValues {
   private ScriptValues() {
   }

   public static WrapFactory wrapFactory() {
      return new Values();
   }

   private static final class Values extends WrapFactory {
      public Object wrap(Context context, Scriptable scope, Object object, Class<?> staticType) {
         if (object instanceof Map) {
            NativeObject result = new NativeObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)object).entrySet()) {
               ScriptableObject.putProperty(
                  result,
                  String.valueOf(entry.getKey()),
                  wrap(context, scope, entry.getValue(), Object.class)
               );
            }

            return result;
         }

         if (object instanceof Boolean || object instanceof Number || object instanceof String) {
            return object;
         }

         return super.wrap(context, scope, object, staticType);
      }
   }
}
