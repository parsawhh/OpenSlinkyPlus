package dev.slinky.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Line delimited JSON control channel. The game listens on a TCP port so a tool on the same network can
 * list, read, upload, delete and toggle script modules while the game is running.
 *
 * Request:  {"cmd":"put","id":"my_module","source":"function onTick(api){}","token":"optional"}
 * Response: {"ok":true,"result":{...}}
 */
public final class ScriptServer {
   private final ScriptHost host;
   private final int port;
   private final String token;
   private final AtomicBoolean running = new AtomicBoolean(false);
   private ServerSocket socket;

   public ScriptServer(ScriptHost host, int port, String token) {
      this.host = host;
      this.port = port;
      this.token = token == null ? "" : token;
   }

   public void start() {
      if (!this.running.compareAndSet(false, true)) {
         return;
      }

      Thread acceptor = new Thread(new Runnable() {
         public void run() {
            ScriptServer.this.accept();
         }
      }, "slinky-script-server");
      acceptor.setDaemon(true);
      acceptor.start();
   }

   private void accept() {
      try {
         this.socket = new ServerSocket(this.port, 8, InetAddress.getByName("0.0.0.0"));
         Platform.log("script control port listening on 0.0.0.0:" + this.port);
         this.writeDescriptor();

         while (this.running.get()) {
            final Socket client;
            try {
               client = this.socket.accept();
            } catch (IOException e) {
               if (this.running.get()) {
                  Platform.log("script control accept failed: " + e);
               }

               continue;
            }

            Thread worker = new Thread(new Runnable() {
               public void run() {
                  ScriptServer.this.serve(client);
               }
            }, "slinky-script-client");
            worker.setDaemon(true);
            worker.start();
         }
      } catch (IOException e) {
         Platform.log("script control port failed: " + e);
      }
   }

   private void writeDescriptor() {
      try {
         JsonObject descriptor = new JsonObject();
         descriptor.addProperty("host", this.port);
         descriptor.addProperty("port", this.port);
         descriptor.addProperty("token", this.token);
         descriptor.addProperty("directory", this.host.directory().toString());
         descriptor.addProperty("status", this.host.statusDirectory().toString());
         Files.createDirectories(this.host.statusDirectory());
         Files.write(this.host.statusDirectory().resolve("host.json"), descriptor.toString().getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         Platform.log("script descriptor failed: " + e);
      }
   }

   private void serve(Socket client) {
      try (Socket socket = client;
           BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
           Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
         socket.setSoTimeout(30000);
         String line;
         while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
               continue;
            }

            JsonObject response = this.handle(line);
            writer.write(response.toString());
            writer.write("\n");
            writer.flush();
         }
      } catch (IOException e) {
         Platform.log("script control client failed: " + e);
      }
   }

   private JsonObject handle(String line) {
      JsonObject response = new JsonObject();
      try {
         JsonElement parsed = new JsonParser().parse(line);
         if (!parsed.isJsonObject()) {
            response.addProperty("ok", false);
            response.addProperty("error", "expected a JSON object");
            return response;
         }

         JsonObject request = parsed.getAsJsonObject();
         if (!this.token.isEmpty() && !this.token.equals(text(request, "token", ""))) {
            response.addProperty("ok", false);
            response.addProperty("error", "invalid token");
            return response;
         }

         String command = text(request, "cmd", "");
         JsonObject result = this.dispatch(command, request);
         response.addProperty("ok", true);
         response.add("result", result);
      } catch (Throwable e) {
         response.addProperty("ok", false);
         response.addProperty("error", e.getClass().getSimpleName() + ": " + e.getMessage());
      }

      return response;
   }

   private JsonObject dispatch(String command, JsonObject request) throws IOException {
      JsonObject result = new JsonObject();

      if (command.equals("ping")) {
         result.addProperty("pong", true);
         result.addProperty("namespace", Platform.getNamespace() == null ? "unknown" : Platform.getNamespace().id);
      } else if (command.equals("list")) {
         result.add("scripts", this.host.describe());
      } else if (command.equals("get")) {
         String id = text(request, "id", "");
         Path file = this.host.directory().resolve(id + ".js");
         if (!Files.isRegularFile(file)) {
            throw new IOException("unknown script " + id);
         }

         result.addProperty("id", id);
         result.addProperty("source", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
      } else if (command.equals("put")) {
         String id = text(request, "id", "");
         String source = text(request, "source", "");
         if (id.isEmpty() || !id.matches("[A-Za-z0-9_-]+")) {
            throw new IOException("invalid script id");
         }

         Files.createDirectories(this.host.directory());
         Path file = this.host.directory().resolve(id + ".js");
         Files.write(file, source.getBytes(StandardCharsets.UTF_8));
         this.host.submit(new Runnable() {
            public void run() {
               ScriptServer.this.host.reload(id);
            }
         });
         result.addProperty("id", id);
         result.addProperty("bytes", source.length());
      } else if (command.equals("delete")) {
         String id = text(request, "id", "");
         Path file = this.host.directory().resolve(id + ".js");
         Files.deleteIfExists(file);
         this.host.submit(new Runnable() {
            public void run() {
               ScriptServer.this.host.reload(id);
            }
         });
         result.addProperty("id", id);
         result.addProperty("deleted", true);
      } else if (command.equals("enable")) {
         String id = text(request, "id", "");
         boolean on = request.has("on") && request.get("on").getAsBoolean();
         this.host.submit(new Runnable() {
            public void run() {
               ScriptServer.this.host.setEnabled(id, on);
            }
         });
         result.addProperty("id", id);
         result.addProperty("enabled", on);
      } else if (command.equals("setting")) {
         String id = text(request, "id", "");
         String key = text(request, "key", "");
         JsonElement value = request.get("value");
         final String raw = value == null ? null : value.toString();
         this.host.submit(new Runnable() {
            public void run() {
               ScriptServer.this.host.setSetting(id, key, raw);
            }
         });
         result.addProperty("id", id);
         result.addProperty("key", key);
      } else if (command.equals("status")) {
         result.add("scripts", this.host.describe());
         result.addProperty("namespace", Platform.getNamespace() == null ? "unknown" : Platform.getNamespace().id);
         result.addProperty("directory", this.host.directory().toString());
      } else if (command.equals("logs")) {
         String id = text(request, "id", "");
         JsonArray lines = new JsonArray();
         for (String line : this.host.logs(id)) {
            lines.add(new com.google.gson.JsonPrimitive(line));
         }

         result.add("lines", lines);
      } else {
         throw new IOException("unknown command " + command);
      }

      return result;
   }

   private static String text(JsonObject row, String key, String fallback) {
      JsonElement value = row.get(key);
      if (value != null && value.isJsonPrimitive()) {
         return value.getAsString();
      }

      return fallback;
   }
}
