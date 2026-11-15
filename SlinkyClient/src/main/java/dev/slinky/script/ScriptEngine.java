package dev.slinky.script;

public interface ScriptEngine {
   void evaluate(String name, String source) throws ScriptException;

   Object call(String function, Object... args) throws ScriptException;

   void bind(String name, Object value);

   boolean hasFunction(String function);

   void close();
}
