package dev.slinky.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Offline test for the script control channel. Starts the TCP server without Minecraft and speaks the
 * line delimited JSON protocol against it.
 */
public final class ScriptServerSmokeTest {
   private static int failures;

   public static void main(String[] args) throws Exception {
      Path root = Paths.get(System.getProperty("java.io.tmpdir"), "slinky-server-test");
      Files.createDirectories(root);

      ScriptHost host = new ScriptHost(null, root);
      ScriptServer server = new ScriptServer(host, 47951, "");
      server.start();
      Thread.sleep(600);

      JsonObject pong = call("{\"cmd\":\"ping\"}");
      check("ping ok", true, pong.get("ok").getAsBoolean());
      check("ping payload", true, pong.getAsJsonObject("result").has("pong"));

      JsonObject put = call("{\"cmd\":\"put\",\"id\":\"demo\",\"source\":\"function onTick(api){}\"}");
      check("put ok", true, put.get("ok").getAsBoolean());
      check("put wrote file", true, Files.isRegularFile(root.resolve("demo.js")));

      JsonObject get = call("{\"cmd\":\"get\",\"id\":\"demo\"}");
      check("get ok", true, get.get("ok").getAsBoolean());
      check("get source", "function onTick(api){}", get.getAsJsonObject("result").get("source").getAsString());

      JsonObject list = call("{\"cmd\":\"list\"}");
      check("list ok", true, list.get("ok").getAsBoolean());
      check("list shape", true, list.getAsJsonObject("result").get("scripts").isJsonArray());

      JsonObject bad = call("{\"cmd\":\"nope\"}");
      check("unknown command rejected", false, bad.get("ok").getAsBoolean());

      JsonObject missing = call("{\"cmd\":\"get\",\"id\":\"missing\"}");
      check("missing script rejected", false, missing.get("ok").getAsBoolean());

      if (failures == 0) {
         System.out.println("script control channel test: OK");
      } else {
         System.out.println("script control channel test: " + failures + " FAILURES");
         System.exit(1);
      }
   }

   private static JsonObject call(String request) throws Exception {
      try (Socket socket = new Socket()) {
         socket.connect(new InetSocketAddress("127.0.0.1", 47951), 5000);
         socket.setSoTimeout(5000);
         Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
         writer.write(request);
         writer.write("\n");
         writer.flush();
         BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         String line = reader.readLine();
         return new JsonParser().parse(line).getAsJsonObject();
      }
   }

   private static void check(String label, Object expected, Object actual) {
      boolean ok = expected == null ? actual == null : expected.equals(actual);
      if (!ok) {
         failures++;
      }

      System.out.println((ok ? "  ok   " : "  FAIL ") + label + " expected=" + expected + " actual=" + actual);
   }
}
