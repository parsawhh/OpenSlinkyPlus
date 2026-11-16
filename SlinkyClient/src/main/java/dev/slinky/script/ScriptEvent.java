package dev.slinky.script;

import dev.slinky.client.event.SemanticEvent;
import dev.slinky.inject.Mappings;
import java.lang.reflect.Field;

public final class ScriptEvent {
   private final SemanticEvent event;

   public ScriptEvent(SemanticEvent event) {
      this.event = event;
   }

   SemanticEvent rawEvent() {
      return this.event;
   }

   public int getType() { return this.event.type; }
   public String getName() { return ScriptEvents.name(this.event.type); }
   public boolean isCancelled() { return this.event.cancelled; }
   public void cancel() { this.event.cancelled = true; }
   public void setCancelled(boolean value) { this.event.cancelled = value; }
   public boolean isActive() { return this.event.active; }
   public void setActive(boolean value) { this.event.active = value; }
   public boolean isEnabled() { return this.event.enabled; }
   public void setEnabled(boolean value) { this.event.enabled = value; }
   public boolean isJump() { return this.event.jump; }
   public void setJump(boolean value) { this.event.jump = value; }
   public boolean isSneaking() { return this.event.sneaking; }
   public boolean isOverrideRotation() { return this.event.overrideRotation; }
   public void setOverrideRotation(boolean value) { this.event.overrideRotation = value; }
   public boolean isRemapMovement() { return this.event.remapMovement; }
   public void setRemapMovement(boolean value) { this.event.remapMovement = value; }
   public boolean isReducing() { return this.event.reducing; }
   public float getYaw() { return this.event.yaw; }
   public void setYaw(float value) { this.event.yaw = value; }
   public float getPitch() { return this.event.pitch; }
   public void setPitch(float value) { this.event.pitch = value; }
   public double getDistance() { return this.event.distance; }
   public void setDistance(double value) { this.event.distance = value; }
   public double getExpand() { return this.event.expand; }
   public void setExpand(double value) { this.event.expand = value; }
   public float getForward() { return this.event.forward; }
   public void setForward(float value) { this.event.forward = value; }
   public float getSideways() { return this.event.sideways; }
   public void setSideways(float value) { this.event.sideways = value; }
   public float getFactor() { return this.event.factor; }
   public void setFactor(float value) { this.event.factor = value; }
   public float getScale() { return this.event.scale; }
   public void setScale(float value) { this.event.scale = value; }
   public double getElapsedSeconds() { return this.event.elapsedSeconds; }
   public int getDispatchMode() { return this.event.dispatchMode; }
   public void setDispatchMode(int mode) { this.event.dispatchMode = mode; }
   public void setQueryOverride(boolean value) { this.event.queryOverride = value; }
   public boolean isQueryOverride() { return this.event.queryOverride; }

   public Object getPacket() {
      try { return SemanticEvent.class.getField("packet").get(this.event); }
      catch (Throwable e) { return null; }
   }

   public ScriptRef packetRef() { return ScriptRef.of(this.getPacket()); }
   public String getPacketName() { return this.packetClass(); }

   public String packetClass() {
      Object packet = this.getPacket();
      if (packet == null) return null;
      return Mappings.unmapClass(packet.getClass().getName().replace('.', '/')).replace('/', '.');
   }

   public void setPacket(Object packet) {
      Object raw = packet instanceof ScriptRef ? ((ScriptRef)packet).raw() : packet;
      try { SemanticEvent.class.getField("packet").set(this.event, raw); }
      catch (Throwable e) { }
   }

   public Object getEntity() {
      try { return SemanticEvent.class.getField("entity").get(this.event); }
      catch (Throwable e) { return null; }
   }

   public ScriptRef entityRef() { return ScriptRef.of(this.getEntity()); }

   public Object getReceiver() {
      try { return SemanticEvent.class.getField("receiver").get(this.event); }
      catch (Throwable e) { return null; }
   }

   public double getCameraX() { return this.event.cameraX; }
   public double getCameraY() { return this.event.cameraY; }
   public double getCameraZ() { return this.event.cameraZ; }
   public float getRenderWidth() { return this.event.renderWidth; }
   public float getRenderHeight() { return this.event.renderHeight; }
}
