package net.minecraft.client;

import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.util.IThreadListener;

public class Minecraft implements IThreadListener {

   public static Minecraft getMinecraft() {
      return null;
   }

   public ListenableFuture<Object> addScheduledTask(Runnable task) {
      return null;
   }
}
