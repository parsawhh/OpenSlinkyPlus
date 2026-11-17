package dev.slinky.script;

public final class ScriptEvents {
   public static final int TICK = 0;
   public static final int RENDER_TICK = 1;
   public static final int PACKET_INBOUND = 2;
   public static final int PACKET_OUTBOUND = 3;
   public static final int CANCEL_DISABLE = 4;
   public static final int LIFECYCLE = 5;
   public static final int RENDER_WORLD = 6;
   public static final int RENDER_ORTHO = 7;
   public static final int RAY_TRACE = 8;
   public static final int GUI_CANVAS = 9;
   public static final int GUI_DRAW = 10;
   public static final int GUI_ACTION = 11;
   public static final int GUI_ERROR = 12;
   public static final int ROTATION = 13;
   public static final int PRE_INTERACT = 14;
   public static final int POST_INTERACT = 15;
   public static final int PRE_ENTITIES = 16;
   public static final int BEGIN_MOVEMENT = 17;
   public static final int POST_ENTITIES = 18;
   public static final int FRIENDS = 19;
   public static final int BLOCK_EVENT = 20;
   public static final int PRE_MOVE = 21;
   public static final int POST_MOVE = 22;
   public static final int PACKET_SEND = 23;
   public static final int BINDING = 24;
   public static final int CONTROLLER = 25;
   public static final int RESET = 26;
   public static final int COUNT = 27;

   private static final String[] NAMES = {
      "tick", "render_tick", "packet_inbound", "packet_outbound", "cancel_disable", "lifecycle",
      "render_world", "render_ortho", "ray_trace", "gui_canvas", "gui_draw", "gui_action",
      "gui_error", "rotation", "pre_interact", "post_interact", "pre_entities", "begin_movement",
      "post_entities", "friends", "block_event", "pre_move", "post_move", "packet_send",
      "binding", "controller", "reset"
   };

   private ScriptEvents() {
   }

   public static String name(int type) {
      if (type >= 0 && type < NAMES.length) {
         return NAMES[type];
      }

      return "event_" + type;
   }

   public static int byName(String name) {
      for (int i = 0; i < NAMES.length; i++) {
         if (NAMES[i].equalsIgnoreCase(name)) {
            return i;
         }
      }

      return -1;
   }
}
