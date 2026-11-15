import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/**
 * Relocates the bundled Rhino runtime from org/mozilla/javascript into dev/slinky/shaded/rhino so the
 * native injector can define it (it only defines classes under dev/slinky/) and so the payload stays
 * namespace neutral. Also rewrites the payload classes that reference Rhino.
 *
 * Every Rhino class is mapped by its exact name, so no generic prefix rewriting is needed.
 *
 * usage: java Relocate.java <rhino.jar> <classes-dir> <output-dir>
 */
public final class Relocate {
   private static final String FROM = "org/mozilla/javascript";
   private static final String TO = "dev/slinky/shaded/rhino";

   public static void main(String[] args) throws Exception {
      if (args.length < 3) {
         System.err.println("usage: Relocate <rhino.jar> <classes-dir> <output-dir>");
         System.exit(2);
      }

      Path rhino = Paths.get(args[0]);
      Path classes = Paths.get(args[1]);
      Path output = Paths.get(args[2]);
      Map<String, String> mapping = new HashMap<>();

      int relocated = 0;
      int copied = 0;

      try (ZipFile zip = new ZipFile(rhino.toFile())) {
         Enumeration<? extends ZipEntry> entries = zip.entries();
         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory()) {
               continue;
            }

            if (name.endsWith(".class")) {
               String internal = name.substring(0, name.length() - 6);
               mapping.put(internal, TO + "/" + internal.substring(FROM.length() + 1));
            }
         }

         entries = zip.entries();
         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory()) {
               continue;
            }

            byte[] data;
            try (InputStream in = zip.getInputStream(entry)) {
               data = in.readAllBytes();
            }

            if (name.endsWith(".class")) {
               byte[] moved = relocate(data, mapping);
               Path target = output.resolve(TO + "/" + name.substring(FROM.length() + 1));
               Files.createDirectories(target.getParent());
               Files.write(target, moved);
               relocated++;
            } else if (name.startsWith(FROM + "/")) {
               Path target = output.resolve(TO + "/" + name.substring(FROM.length() + 1));
               Files.createDirectories(target.getParent());
               Files.write(target, data);
               copied++;
            }
         }
      }

      int[] counter = new int[1];
      if (Files.isDirectory(classes)) {
         Path root = classes;
         Files.walk(root).filter(path -> path.toString().endsWith(".class")).forEach(path -> {
            try {
               byte[] moved = relocate(Files.readAllBytes(path), mapping);
               Path target = output.resolve(root.relativize(path).toString());
               Files.createDirectories(target.getParent());
               Files.write(target, moved);
               counter[0]++;
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      }

      System.out.println("relocated " + relocated + " rhino classes, copied " + copied + " resources, rewrote " + counter[0] + " payload classes");
   }

   private static byte[] relocate(byte[] data, Map<String, String> mapping) {
      ClassReader reader = new ClassReader(data);
      ClassWriter writer = new ClassWriter(0);
      ClassVisitor visitor = new ClassRemapper(writer, new SimpleRemapper(mapping));
      reader.accept(new LiteralRemapper(visitor), 0);
      return writer.toByteArray();
   }

   /**
    * Rhino loads a few helper classes by name (VMBridge), so plain string literals that mention the old
    * package have to be rewritten as well.
    */
   private static final class LiteralRemapper extends ClassVisitor {
      LiteralRemapper(ClassVisitor delegate) {
         super(Opcodes.ASM9, delegate);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
         MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
         return new MethodVisitor(Opcodes.ASM9, delegate) {
            @Override
            public void visitLdcInsn(Object value) {
               if (value instanceof String) {
                  String text = (String)value;
                  if (text.startsWith("org.mozilla.javascript")) {
                     super.visitLdcInsn("dev.slinky.shaded.rhino" + text.substring("org.mozilla.javascript".length()));
                     return;
                  }

                  if (text.startsWith("org/mozilla/javascript")) {
                     super.visitLdcInsn("dev/slinky/shaded/rhino" + text.substring("org/mozilla/javascript".length()));
                     return;
                  }
               }

               super.visitLdcInsn(value);
            }
         };
      }
   }
}
