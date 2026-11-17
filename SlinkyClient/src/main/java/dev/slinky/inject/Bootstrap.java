package dev.slinky.inject;

import com.google.common.util.concurrent.ListenableFuture;
import dev.slinky.SlinkyMod;
import dev.slinky.client.event.ClientEventBridge;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.inject.transform.SlinkyPatches;
import dev.slinky.inject.transform.TransformerEngine;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IThreadListener;

public final class Bootstrap {
   private static final String INJECTED_PROPERTY = "slinky.injected";

   private Bootstrap() {
   }

   public static void start(String namespace, String payloadPath) throws Throwable {
      start(namespace, payloadPath, true);
   }

   public static void start(String namespace, String payloadPath, boolean natives) throws Throwable {
      synchronized (Bootstrap.class) {
         Field managerField = GameHooks.class.getDeclaredField("modules");
         managerField.setAccessible(true);
         Object previousManager = managerField.get(null);
         if (previousManager != null) {
            System.setProperty("slinky.patches.installed", "true");
            Field shutdownField = previousManager.getClass().getDeclaredField("shutdown");
            shutdownField.setAccessible(true);
            if (!((AtomicBoolean)shutdownField.get(previousManager)).get()) {
               Platform.log("OpenSlinky+ is already running in this process; ignoring second injection");
               return;
            }
            ClientEventBridge.install(null);
            GameHooks.install(null);
            Platform.log("Previous OpenSlinky+ session was unloaded; starting a new session");
         }
         System.clearProperty("slinky.injected");

         try {
            System.setProperty("slinky.injected", "true");
            Platform.initialize(Platform.Namespace.of(namespace), payloadPath, natives);

            try (InputStream mappings = Platform.openResource("/dev/slinky/inject/mappings.txt")) {
               if (mappings == null && Platform.getNamespace() != Platform.Namespace.NAMED) {
                  throw new IllegalStateException("Payload for " + namespace + " is missing " + "/dev/slinky/inject/mappings.txt");
               }

               Mappings.load(mappings);
            }

            Platform.log("Runtime: " + Platform.describe() + ", " + Mappings.size() + " mappings, payload " + payloadPath);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
               throw new IllegalStateException("Minecraft has not been created yet");
            }
            ListenableFuture<Object> task = ((IThreadListener) mc).addScheduledTask(new Runnable() {
               @Override
               public void run() {
                  Bootstrap.initializeOnClientThread();
               }
            });
            task.get(2L, TimeUnit.MINUTES);
         } catch (Throwable failure) {
            System.clearProperty("slinky.injected");
            LogSink.crash("OpenSlinky+ failed to boot (" + namespace + ")", failure);
            throw failure;
         }
      }
   }

   /**
    * Entry point used by the OneClient/Fabric mod. The java agent has already published its
    * Instrumentation; this waits (off the game thread) until Minecraft exists and then boots the
    * payload, which is this same mod jar.
    */
   public static void launchFabric() {
      installFabricInstrumentation();
      final String payloadPath = locatePayloadJar();
      Thread worker = new Thread(new Runnable() {
         @Override
         public void run() {
            Platform.log("OpenSlinky+ Fabric entrypoint waiting for Minecraft to be created");
            long deadline = System.currentTimeMillis() + 180000L;
            Minecraft minecraft = null;

            while (System.currentTimeMillis() < deadline) {
               try {
                  minecraft = Minecraft.getMinecraft();
               } catch (Throwable ignored) {
                  minecraft = null;
               }

               if (minecraft != null) {
                  break;
               }

               try {
                  Thread.sleep(200L);
               } catch (InterruptedException interrupted) {
                  return;
               }
            }

            if (minecraft == null) {
               LogSink.crash("OpenSlinky+ timed out waiting for Minecraft", new IllegalStateException("Minecraft.getMinecraft() stayed null for 3 minutes"));
               return;
            }

            try {
               start("calamus", payloadPath, false);
            } catch (Throwable failure) {
               LogSink.crash("OpenSlinky+ Fabric bootstrap failed", failure);
               Platform.log("OpenSlinky+ Fabric bootstrap failed", failure);
            }
         }
      }, "OpenSlinky+ OneClient");
      worker.setDaemon(true);
      worker.start();
   }

   private static void installFabricInstrumentation() {
      try {
         Class<?> holder = Class.forName("dev.slinky.inject.agent.InstrumentationHolder", true, Bootstrap.class.getClassLoader());
         Object instrumentation = holder.getMethod("get").invoke(null);
         if (instrumentation instanceof java.lang.instrument.Instrumentation) {
            Platform.installInstrumentation((java.lang.instrument.Instrumentation) instrumentation);
            Platform.log("OpenSlinky+ using the java agent for class redefinition");
         } else {
            Platform.log("OpenSlinky+ java agent is not attached; start the game with -javaagent:slinky-oneclient-agent.jar");
         }
      } catch (Throwable missingAgent) {
         Platform.log("OpenSlinky+ java agent is not attached; start the game with -javaagent:slinky-oneclient-agent.jar");
      }
   }

   private static String locatePayloadJar() {
      try {
         java.security.CodeSource source = Bootstrap.class.getProtectionDomain().getCodeSource();
         if (source != null && source.getLocation() != null) {
            java.io.File file = new java.io.File(source.getLocation().toURI());
            if (file.isFile()) {
               return file.getAbsolutePath();
            }
         }
      } catch (Throwable ignored) {
      }

      return Platform.getPayloadPath();
   }

   private static void initializeOnClientThread() {
      Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
         @Override
         public void uncaughtException(Thread thread, Throwable throwable) {
            LogSink.crash("OpenSlinky+ uncaught exception on " + thread.getName(), throwable);
            Platform.log("Uncaught exception on " + thread.getName(), throwable);
         }
      });
      SlinkyMod mod = new SlinkyMod();

      try {
         mod.preInitialize();
         mod.initialize();
         if (System.getProperty("slinky.patches.installed") == null) {
            TransformerEngine transformer = new TransformerEngine();
            transformer.applyAll(SlinkyPatches.create(Platform.isForge()));
            if (transformer.getFailed() != 0) {
               throw new IllegalStateException("Failed to install " + transformer.getFailed() + " OpenSlinky+ class patches");
            }
            System.setProperty("slinky.patches.installed", "true");
         } else {
            Platform.log("Reusing the installed OpenSlinky+ game hooks");
         }
         Platform.log("OpenSlinky+ initialized with " + mod.modules().all().size() + " modules");
      } catch (Throwable var2) {
         LogSink.crash("OpenSlinky+ failed to initialize on the client thread", var2);
         try {
            if (mod.modules() != null) {
               mod.modules().shutdown();
            }
         } catch (Throwable cleanupFailure) {
            var2.addSuppressed(cleanupFailure);
         } finally {
            System.clearProperty("slinky.injected");
         }
         Platform.log("OpenSlinky+ initialization failed", var2);
         throw var2 instanceof RuntimeException ? (RuntimeException)var2 : new RuntimeException(var2);
      }
   }
}
