import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * RemapPayload - remaps the MCP-named payload jar/classes to a runtime namespace using the
 * dev/slinky/inject/mappings.txt line format:
 *
 *   CL  <namedClass>  <runtimeClass>
 *   FD  <namedOwner>  <namedField>  <runtimeField>
 *   MD  <namedOwner>  <namedMethod> <namedDescriptor> <runtimeMethod>
 *
 * Used for
 *   - slinky-calamus.jar: the whole named payload jar -> OneClient/Ornithe calamus runtime
 *     (class names stay MCP-style, members become f_ / m_ names; calamus.txt carries no CL lines), and
 *   - the inject-package class variants inside slinky-srg.jar / slinky-official.jar.
 *
 * dev/slinky/shaded (the bundled Rhino) is copied verbatim; dev/slinky/inject/mappings.txt is
 * replaced with the target namespace's file.
 *
 * usage: java -cp <asm jars> RemapPayload.java <in.jar> <mappings.txt> <out.jar>
 */
public final class RemapPayload {

   public static void main(String[] args) throws Exception {
      Path in = Paths.get(args[0]);
      Path mappings = Paths.get(args[1]);
      Path out = Paths.get(args[2]);

      Map<String, String> classes = new HashMap<>();
      Map<String, String> classesReverse = new HashMap<>();
      Map<String, Map<String, String>> fields = new HashMap<>();
      Map<String, Map<String, Map<String, String>>> methods = new HashMap<>();
      for (String line : Files.readAllLines(mappings, StandardCharsets.UTF_8)) {
         if (line.isEmpty()) continue;
         String[] c = line.split("\t");
         if (c[0].equals("CL")) {
            classes.put(c[1], c[2]);
            classesReverse.put(c[2], c[1]);
         } else if (c[0].equals("FD")) {
            fields.computeIfAbsent(c[1], k -> new HashMap<>()).put(c[2], c[3]);
         } else if (c[0].equals("MD")) {
            methods.computeIfAbsent(c[1], k -> new HashMap<>())
                  .computeIfAbsent(c[2], k -> new HashMap<>())
                  .put(c[3], c[4]);
         }
      }

      Remapper mapper = new Remapper() {
         private String unmap(String runtimeName) {
            String named = classesReverse.get(runtimeName);
            if (named != null) return named;
            int inner = runtimeName.lastIndexOf('$');
            if (inner > 0) {
               String outerNamed = classesReverse.get(runtimeName.substring(0, inner));
               if (outerNamed != null) return outerNamed + runtimeName.substring(inner);
            }
            return runtimeName;
         }

         private String unmapDescriptor(String runtimeDescriptor) {
            if (classesReverse.isEmpty()) return runtimeDescriptor;
            StringBuilder builder = new StringBuilder();
            int index = 0;
            while (index < runtimeDescriptor.length()) {
               char ch = runtimeDescriptor.charAt(index);
               if (ch == 'L') {
                  int end = runtimeDescriptor.indexOf(';', index);
                  builder.append('L').append(unmap(runtimeDescriptor.substring(index + 1, end))).append(';');
                  index = end + 1;
               } else {
                  builder.append(ch);
                  index++;
               }
            }
            return builder.toString();
         }

         @Override
         public String map(String internalName) {
            String mapped = classes.get(internalName);
            if (mapped != null) return mapped;
            int inner = internalName.lastIndexOf('$');
            if (inner > 0) {
               String outer = classes.get(internalName.substring(0, inner));
               if (outer != null) return outer + internalName.substring(inner);
            }
            return internalName;
         }

         @Override
         public String mapFieldName(String owner, String name, String descriptor) {
            Map<String, String> byOwner = fields.get(unmap(owner));
            String mapped = byOwner == null ? null : byOwner.get(name);
            return mapped != null ? mapped : name;
         }

         @Override
         public String mapMethodName(String owner, String name, String descriptor) {
            Map<String, Map<String, String>> byOwner = methods.get(unmap(owner));
            if (byOwner != null) {
               Map<String, String> byDesc = byOwner.get(name);
               String mapped = byDesc == null ? null : byDesc.get(unmapDescriptor(descriptor));
               if (mapped != null) return mapped;
            }
            return name;
         }
      };

      byte[] mappingsBytes = Files.readAllBytes(mappings);
      Set<String> seen = new HashSet<>();
      try (JarInputStream in0 = new JarInputStream(Files.newInputStream(in));
           JarOutputStream out0 = new JarOutputStream(Files.newOutputStream(out))) {
         JarEntry entry;
         while ((entry = in0.getNextJarEntry()) != null) {
            String name = entry.getName();
            if (!seen.add(name)) continue;
            if (name.endsWith("/") || name.endsWith(".class") && name.startsWith("dev/slinky/")
                  && !name.startsWith("dev/slinky/shaded/")) {
               if (name.endsWith("/")) continue;
               byte[] bytes = readAll(in0);
               ClassReader reader = new ClassReader(bytes);
               ClassWriter writer = new ClassWriter(0);
               reader.accept(new ClassRemapper(writer, mapper), 0);
               write(out0, name, writer.toByteArray());
            } else if (name.equals("dev/slinky/inject/mappings.txt")) {
               write(out0, name, mappingsBytes);
            } else {
               write(out0, name, readAll(in0));
            }
         }
         // the named jar carries no mappings.txt (identity); every remapped payload needs one
         if (!seen.contains("dev/slinky/inject/mappings.txt")) {
            write(out0, "dev/slinky/inject/mappings.txt", mappingsBytes);
         }
      }
      System.out.println(out + ": " + seen.size() + " entries");
   }

   private static byte[] readAll(InputStream stream) throws Exception {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[16384];
      int n;
      while ((n = stream.read(chunk)) != -1) buffer.write(chunk, 0, n);
      return buffer.toByteArray();
   }

   private static void write(JarOutputStream jar, String name, byte[] bytes) throws Exception {
      jar.putNextEntry(new JarEntry(name));
      jar.write(bytes);
      jar.closeEntry();
   }
}
