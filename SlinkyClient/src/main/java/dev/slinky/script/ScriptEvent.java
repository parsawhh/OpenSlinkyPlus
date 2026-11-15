package dev.slinky.script;

import dev.slinky.client.event.SemanticEvent;

public final class ScriptEvent {
   private final SemanticEvent event;

   public ScriptEvent(SemanticEvent event) {
      this.event = event;
   }

   public int getType() {
      return this.event.type;
   }

   public String getName() {
      return ScriptEvents.name(this.event.type);
   }

   public boolean isCancelled() {
      return this.event.cancelled;
   }

   public void cancel() {
      this.event.cancelled = true;
   }

   public boolean isActive() {
      return this.event.active;
   }

   public boolean isEnabled() {
      return this.event.enabled;
   }

   public boolean isJump() {
      return this.event.jump;
   }

   public boolean isSneaking() {
      return this.event.sneaking;
   }

   public boolean isOverrideRotation() {
      return this.event.overrideRotation;
   }

   public boolean isRemapMovement() {
      return this.event.remapMovement;
   }

   public boolean isReducing() {
      return this.event.reducing;
   }

   public float getYaw() {
      return this.event.yaw;
   }

   public float getPitch() {
      return this.event.pitch;
   }

   public double getDistance() {
      return this.event.distance;
   }

   public double getExpand() {
      return this.event.expand;
   }

   public float getForward() {
      return this.event.forward;
   }

   public float getSideways() {
      return this.event.sideways;
   }

   public float getFactor() {
      return this.event.factor;
   }

   public float getScale() {
      return this.event.scale;
   }

   public double getElapsedSeconds() {
      return this.event.elapsedSeconds;
   }

   public int getDispatchMode() {
      return this.event.dispatchMode;
   }
}
