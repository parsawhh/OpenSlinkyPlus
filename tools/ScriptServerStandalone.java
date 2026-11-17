package dev.slinky.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runs the script control channel without Minecraft so the protocol can be exercised offline.
 *
 *   java -cp ... dev.slinky.script.ScriptServerStandalone <scripts-dir> <port>
 */
public final class ScriptServerStandalone {
   public static void main(String[] args) throws Exception {
      Path root = Paths.get(args.length > 0 ? args[0] : "scripts");
      int port = args.length > 1 ? Integer.parseInt(args[1]) : 47900;
      Files.createDirectories(root);
      ScriptHost host = new ScriptHost(null, root);
      ScriptServer server = new ScriptServer(host, port, "");
      server.start();
      System.out.println("control channel ready on port " + port + " serving " + root.toAbsolutePath());
      Thread.sleep(3600000L);
   }
}
