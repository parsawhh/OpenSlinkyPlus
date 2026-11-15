package dev.slinky.script;

public class ScriptException extends Exception {
   public ScriptException(String message) {
      super(message);
   }

   public ScriptException(String message, Throwable cause) {
      super(message, cause);
   }
}
