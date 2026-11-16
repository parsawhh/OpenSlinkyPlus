package dev.slinky.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public final class ScriptEngines {
   private ScriptEngines() {
   }

   public static ScriptEngine create() throws ScriptException {
      try {
         Context context = Context.enter();
         context.setOptimizationLevel(-1);
         context.setLanguageVersion(Context.VERSION_1_8);
         context.setWrapFactory(ScriptValues.wrapFactory());
         Scriptable scope = context.initStandardObjects();
         return new Rhino(context, scope);
      } catch (Throwable e) {
         throw new ScriptException("Rhino engine unavailable: " + e, e);
      }
   }

   private static final class Rhino implements ScriptEngine {
      private final Context context;
      private final Scriptable scope;

      Rhino(Context context, Scriptable scope) {
         this.context = context;
         this.scope = scope;
      }

      public void evaluate(String name, String source) throws ScriptException {
         try {
            this.context.evaluateString(this.scope, source, name, 1, null);
         } catch (RhinoException e) {
            throw new ScriptException(name + ": " + e.details(), e);
         }
      }

      public Object call(String function, Object... args) throws ScriptException {
         try {
            Function target = function(function);
            if (target == null) {
               throw new ScriptException("missing function " + function);
            }

            return target.call(this.context, this.scope, this.scope, args);
         } catch (RhinoException e) {
            throw new ScriptException(e.details(), e);
         }
      }

      private Function function(String name) {
         Object value = ScriptableObject.getProperty(this.scope, name);
         return value instanceof Function ? (Function)value : null;
      }

      public void bind(String name, Object value) {
         ScriptableObject.putProperty(this.scope, name, Context.javaToJS(value, this.scope));
      }

      public boolean hasFunction(String function) {
         return function(function) != null;
      }

      public void close() {
      }
   }
}
