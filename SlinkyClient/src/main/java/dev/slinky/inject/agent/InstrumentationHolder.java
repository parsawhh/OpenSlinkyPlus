package dev.slinky.inject.agent;

import java.lang.instrument.Instrumentation;

/**
 * Shared with the Fabric mod through parent-first class loading: the java agent (system class
 * loader) installs the instrumentation object here and the mod entrypoint (Knot class loader)
 * reads it back.
 */
public final class InstrumentationHolder {
   private static volatile Instrumentation instrumentation;

   private InstrumentationHolder() {
   }

   public static void install(Instrumentation value) {
      instrumentation = value;
   }

   public static Instrumentation get() {
      return instrumentation;
   }
}
