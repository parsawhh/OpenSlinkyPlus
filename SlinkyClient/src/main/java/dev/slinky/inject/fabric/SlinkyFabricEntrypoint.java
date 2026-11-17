package dev.slinky.inject.fabric;

import dev.slinky.inject.Bootstrap;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint for OneClient 1.8.9 (Fabric loader + Ornithe calamus names).
 *
 * The jar also has to be attached as a java agent (see SlinkyAgent) so that class redefinition is
 * available; that agent publishes Instrumentation and this entrypoint boots the payload on a
 * worker thread once Minecraft exists.
 */
public final class SlinkyFabricEntrypoint implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      Bootstrap.launchFabric();
   }
}
