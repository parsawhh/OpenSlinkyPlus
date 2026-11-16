package dev.slinky.script;

import dev.slinky.client.ClientContext;
import dev.slinky.client.event.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderMath;
import java.util.ArrayList;
import java.util.List;

/**
 * MC-free draw helper for script modules. Builds a DrawPlan and submits it to RenderAccess (which lives in
 * the payload and performs the GL work in the correct namespace).
 */
public final class ScriptDraw {
   private final ScriptRuntime runtime;
   private final DrawPlan plan = new DrawPlan();

   ScriptDraw(ScriptRuntime runtime) {
      this.runtime = runtime;
   }

   DrawPlan plan() {
      return this.plan;
   }

   public ScriptDraw color(float r, float g, float b, float a) {
      this.plan.color(r, g, b, a);
      return this;
   }

   public ScriptDraw lineWidth(float width) {
      this.plan.lineWidth(width);
      return this;
   }

   public ScriptDraw line(float x1, float y1, float x2, float y2) {
      this.plan.line(x1, y1, x2, y2);
      return this;
   }

   public ScriptDraw rect(float x, float y, float w, float h, float radius) {
      this.plan.rect(x, y, w, h, radius);
      return this;
   }

   public ScriptDraw rect(float x, float y, float w, float h) {
      this.plan.rect(x, y, w, h, 0.0F);
      return this;
   }

   public ScriptDraw strokeRect(float x, float y, float w, float h, float radius) {
      this.plan.roundedStroke(x, y, w, h, radius);
      return this;
   }

   public ScriptDraw circle(float x, float y, float radius) {
      // approximate with DrawPlan.circleStroke? circleStroke exists as outline; for fill use rounded rect
      this.plan.rect(x - radius, y - radius, radius * 2.0F, radius * 2.0F, radius);
      return this;
   }

   public ScriptDraw circleStroke(float x, float y, float radius) {
      this.plan.circleStroke(x, y, radius);
      return this;
   }

   public ScriptDraw text(String value, float x, float y, int color, boolean shadow, float size) {
      this.plan.text(value == null ? "" : value, x, y, color, shadow, size);
      return this;
   }

   public ScriptDraw text(String value, float x, float y, int color) {
      return this.text(value, x, y, color, false, 9.0F);
   }

   public ScriptDraw push() {
      this.plan.push();
      return this;
   }

   public ScriptDraw pop() {
      this.plan.pop();
      return this;
   }

   public ScriptDraw translate(float x, float y, float z) {
      this.plan.translate(x, y, z);
      return this;
   }

   public ScriptDraw scale(float x, float y, float z) {
      this.plan.scale(x, y, z);
      return this;
   }

   public ScriptDraw rotate(float angle, float x, float y, float z) {
      this.plan.rotate(angle, x, y, z);
      return this;
   }

   public ScriptDraw clip(float x, float y, float w, float h) {
      this.plan.clip(x, y, w, h);
      return this;
   }

   public ScriptDraw unclip() {
      this.plan.unclip();
      return this;
   }

   public ScriptDraw enable(int capability) {
      this.plan.enable(capability);
      return this;
   }

   public ScriptDraw disable(int capability) {
      this.plan.disable(capability);
      return this;
   }

   public ScriptDraw vertices(int mode, int dimensions, Object coords) {
      float[] v = toFloatArray(coords);
      if (v != null) {
         this.plan.vertices(mode, dimensions, v);
      }
      return this;
   }

   public double[] project(double x, double y, double z) {
      ClientContext c = this.runtime.context();
      SemanticEvent e = this.runtime.currentEvent();
      if (c == null || e == null) {
         return null;
      }

      RenderAccess.Frame frame = RenderAccess.frame(c, e);
      if (frame == null) {
         return null;
      }

      RenderMath.Projection p = frame.project(new double[]{x - frame.camera[0], y - frame.camera[1], z - frame.camera[2]});
      if (p == null) {
         return null;
      }

      return new double[]{p.x, p.y, p.reciprocal};
   }

   public ScriptDraw box(double x, double y, double z, double w, double h, double d, int color, float width) {
      // project 8 corners and draw edges
      double[] c = new double[]{x - w / 2.0, y, z - d / 2.0, x + w / 2.0, y, z - d / 2.0, x + w / 2.0, y, z + d / 2.0, x - w / 2.0, y, z + d / 2.0,
         x - w / 2.0, y + h, z - d / 2.0, x + w / 2.0, y + h, z - d / 2.0, x + w / 2.0, y + h, z + d / 2.0, x - w / 2.0, y + h, z + d / 2.0};
      double[][] p = new double[8][];
      boolean ok = true;
      for (int i = 0; i < 8; i++) {
         p[i] = this.project(c[i * 3], c[i * 3 + 1], c[i * 3 + 2]);
         if (p[i] == null) {
            ok = false;
         }
      }

      if (!ok) {
         return this;
      }

      float a = (color >> 24 & 255) / 255.0F;
      float r = (color >> 16 & 255) / 255.0F;
      float g = (color >> 8 & 255) / 255.0F;
      float b = (color & 255) / 255.0F;
      if (a == 0.0F) {
         a = 1.0F;
      }

      this.plan.color(r, g, b, a).lineWidth(width);
      int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
      for (int[] e : edges) {
         this.plan.line((float)p[e[0]][0], (float)p[e[0]][1], (float)p[e[1]][0], (float)p[e[1]][1]);
      }

      return this;
   }

   public void submit() {
      ClientContext c = this.runtime.context();
      SemanticEvent e = this.runtime.currentEvent();
      if (c != null && e != null) {
         RenderAccess.submit(e, this.plan);
      }
   }

   private static float[] toFloatArray(Object coords) {
      if (coords == null) {
         return null;
      }

      if (coords instanceof float[]) {
         return (float[])coords;
      }

      if (coords instanceof double[]) {
         double[] d = (double[])coords;
         float[] f = new float[d.length];
         for (int i = 0; i < d.length; i++) {
            f[i] = (float)d[i];
         }
         return f;
      }

      if (coords instanceof org.mozilla.javascript.NativeArray) {
         org.mozilla.javascript.NativeArray a = (org.mozilla.javascript.NativeArray)coords;
         float[] f = new float[(int)a.getLength()];
         for (int i = 0; i < f.length; i++) {
            Object v = a.get(i, a);
            f[i] = v instanceof Number ? ((Number)v).floatValue() : 0.0F;
         }
         return f;
      }

      if (coords instanceof List) {
         List<?> l = (List<?>)coords;
         float[] f = new float[l.size()];
         for (int i = 0; i < f.length; i++) {
            Object v = l.get(i);
            f[i] = v instanceof Number ? ((Number)v).floatValue() : 0.0F;
         }
         return f;
      }

      return null;
   }
}
