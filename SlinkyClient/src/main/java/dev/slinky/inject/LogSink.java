package dev.slinky.inject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * File logging for the OneClient/Fabric (Ornithe calamus) runtime. The native bridge is not
 * available there, so running logs and crash dumps are written next to the user's Desktop:
 *
 *   <Desktop>/slinky-running.log   every Platform.log line, appended
 *   <Desktop>/slinky-crash.log     uncaught exceptions / failed boot, overwritten per crash
 *
 * Resolution order for the Desktop directory: user.home/Desktop, then OneDrive variants,
 * then user.home itself so logging never blocks startup.
 */
public final class LogSink {
   private static final Object LOCK = new Object();
   private static PrintWriter running;
   private static File directory;
   private static boolean enabled;

   private LogSink() {
   }

   public static void line(String message) {
      synchronized (LOCK) {
         if (!enabled) {
            enable();
         }
         if (running != null) {
            running.println(timestamp() + ' ' + message);
            running.flush();
         }
      }
   }

   public static void crash(String header, Throwable throwable) {
      synchronized (LOCK) {
         if (!enabled) {
            enable();
         }
         if (directory == null) {
            return;
         }
         try {
            File file = new File(directory, "slinky-crash.log");
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                  new FileOutputStream(file), StandardCharsets.UTF_8)));
            writer.println(timestamp() + " " + header);
            if (throwable != null) {
               throwable.printStackTrace(writer);
            }
            writer.flush();
            writer.close();
         } catch (Throwable ignored) {
            // never let crash logging break the crash path
         }
      }
   }

   private static void enable() {
      enabled = true;
      directory = resolveDirectory();
      if (directory == null) {
         return;
      }
      try {
         File file = new File(directory, "slinky-running.log");
         running = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
               new FileOutputStream(file, true), StandardCharsets.UTF_8)), false);
         running.println(timestamp() + " ---- OpenSlinky+ session started ----");
         running.flush();
      } catch (Throwable ignored) {
         running = null;
      }
   }

   private static File resolveDirectory() {
      String home = System.getProperty("user.home");
      if (home == null || home.isEmpty()) {
         return null;
      }
      String[] candidates = new String[] {
            "Desktop",
            "OneDrive" + File.separatorChar + "Desktop",
            "OneDrive" + File.separatorChar + "Desktop" + File.separatorChar + "Desktop"
      };
      for (String candidate : candidates) {
         File dir = new File(home, candidate);
         if (dir.isDirectory()) {
            return dir;
         }
      }
      return new File(home);
   }

   private static String timestamp() {
      return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
   }
}
