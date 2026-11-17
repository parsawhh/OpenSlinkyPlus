package dev.slinky.inject.agent;

import java.lang.instrument.Instrumentation;

/**
 * Premain/agentmain bridge used when OpenSlinky+ runs inside a Fabric (Knot) client such as
 * OneClient, where the native JVMTI bridge is not available.
 *
 * Launch with: -javaagent:slinky-oneclient-agent.jar
 *
 * The agent only publishes {@link Instrumentation}; the Fabric mod entrypoint waits for Minecraft
 * to exist and then boots the payload. Keeping the payload out of the agent jar is deliberate so
 * the game class loader (Knot), not the system loader, loads every dev/slinky class.
 */
public final class SlinkyAgent {
   private SlinkyAgent() {
   }

   public static void premain(String arguments, Instrumentation instrumentation) {
      attach(instrumentation);
   }

   public static void agentmain(String arguments, Instrumentation instrumentation) {
      attach(instrumentation);
   }

   private static void attach(Instrumentation instrumentation) {
      InstrumentationHolder.install(instrumentation);
      System.out.println("[OpenSlinky+] java agent attached (class redefinition available)");
   }
}
