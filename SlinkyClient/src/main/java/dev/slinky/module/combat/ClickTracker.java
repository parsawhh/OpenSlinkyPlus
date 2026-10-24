package dev.slinky.module.combat;

import org.lwjgl.input.Mouse;

/**
 * Attack-button state that survives auto-click macros (for example a mouse "fire key" clicking at 20 cps).
 *
 * Reading Mouse.isButtonDown once per game tick misses most clicks of such a macro (each press lasts only a
 * few milliseconds), so aim assist kept resetting its "mouse pressed" gate. The button is therefore sampled on
 * every frame and also counts as held for a short window after it was last seen down. Attacks cancelled or
 * fake-swung by Hit Select are reported through noteClick() and count as clicks too.
 */
public final class ClickTracker {
   /** The button still counts as held this long (nanoseconds) after it was last seen pressed. */
   static final long WINDOW_NANOS = 180_000_000L;
   private static final long[] last = new long[2];
   private static final boolean[] seen = new boolean[2];

   private ClickTracker() {
   }

   /** Call as often as possible (every frame / event). */
   public static void sample() {
      sampleAt(System.nanoTime());
   }

   /** A click happened that the game did not see as a normal attack (Hit Select cancel / fake swing). */
   public static void noteClick() {
      noteAt(System.nanoTime());
   }

   /** Replacement for Mouse.isButtonDown(button) in the aim assist "mouse pressed" gate. */
   public static boolean held(int button) {
      return heldAt(button, System.nanoTime());
   }

   static void sampleAt(long now) {
      if (Mouse.isCreated()) {
         for (int b = 0; b < 2; b++) {
            if (Mouse.isButtonDown(b)) {
               mark(b, now);
            }
         }
      }
   }

   static void noteAt(long now) {
      mark(0, now);
      mark(1, now);
   }

   static boolean heldAt(int button, long now) {
      int b = button == 1 ? 1 : 0;
      if (Mouse.isCreated() && Mouse.isButtonDown(b)) {
         mark(b, now);
         return true;
      }

      return seen[b] && now - last[b] < WINDOW_NANOS;
   }

   private static void mark(int b, long now) {
      last[b] = now;
      seen[b] = true;
   }
}
