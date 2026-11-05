package local.cheertok.probe;

import android.os.*;
import android.view.*;
import java.lang.reflect.*;

public class InputBridge {
  final Object manager;
  final Method inject;
  float x = 540, y = 1170;
  int buttons = 0, consumer = 0;
  long downTime;

  public InputBridge() throws Exception {
    Class<?> type;
    try {
      type = Class.forName("android.hardware.input.InputManagerGlobal");
    } catch (ClassNotFoundException e) {
      type = Class.forName("android.hardware.input.InputManager");
    }
    manager = type.getMethod("getInstance").invoke(null);
    inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
  }

  boolean send(InputEvent event) throws Exception {
    return (Boolean) inject.invoke(manager, event, 0);
  }

  void key(int code, boolean down) throws Exception {
    long now = SystemClock.uptimeMillis();
    send(
        new KeyEvent(
            now,
            now,
            down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
            code,
            0,
            0,
            -1,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD));
  }

  void mouse(int action, int state, float vertical, float horizontal) throws Exception {
    long now = SystemClock.uptimeMillis();
    MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties();
    prop.id = 0;
    prop.toolType = MotionEvent.TOOL_TYPE_MOUSE;
    MotionEvent.PointerCoords coord = new MotionEvent.PointerCoords();
    coord.x = x;
    coord.y = y;
    coord.pressure = state == 0 ? 0 : 1;
    coord.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
    coord.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
    MotionEvent event =
        MotionEvent.obtain(
            downTime == 0 ? now : downTime,
            now,
            action,
            1,
            new MotionEvent.PointerProperties[] {prop},
            new MotionEvent.PointerCoords[] {coord},
            0,
            state,
            1,
            1,
            -1,
            0,
            InputDevice.SOURCE_MOUSE,
            0);
    boolean ok = send(event);
    event.recycle();
    if (!ok) throw new Exception("Input injection rejected");
  }

  static int signed12(int v) {
    return v >= 2048 ? v - 4096 : v;
  }

  public String accept(int instance, byte[] data) throws Exception {
    if (instance == 28 && data.length == 7) {
      int next = data[0] & 7;
      int wheel = data[1];
      int dx = signed12((data[2] & 255) | ((data[3] & 15) << 8));
      int dy = signed12(((data[3] & 255) >> 4) | ((data[4] & 255) << 4));
      int horizontal = data[5];
      x = Math.max(1, Math.min(1079, x + dx));
      y = Math.max(1, Math.min(2339, y + dy));
      if ((next & 1) != (buttons & 1)) {
        if ((next & 1) != 0) {
          downTime = SystemClock.uptimeMillis();
          mouse(MotionEvent.ACTION_DOWN, next, 0, 0);
        } else mouse(MotionEvent.ACTION_UP, 0, 0, 0);
      }
      if ((next & 2) != (buttons & 2)) key(KeyEvent.KEYCODE_BACK, (next & 2) != 0);
      if ((next & 4) != (buttons & 4)) key(KeyEvent.KEYCODE_BUTTON_3, (next & 4) != 0);
      buttons = next;
      if (dx != 0 || dy != 0)
        mouse(
            (next & 1) != 0 ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_HOVER_MOVE, next, 0, 0);
      if (wheel != 0 || horizontal != 0) mouse(MotionEvent.ACTION_SCROLL, next, wheel, horizontal);
      return "MOUSE dx="
          + dx
          + " dy="
          + dy
          + " wheel="
          + wheel
          + " horizontal="
          + horizontal
          + " buttons="
          + next;
    }
    if (instance == 32 && data.length == 2) {
      int next = (data[0] & 255) | ((data[1] & 255) << 8);
      int[] codes = {26, 3, 4, 82, 24, 25, 85, 0, 0, 0, 168, 169, 220, 221, 87, 88};
      for (int bit = 0; bit < 16; bit++) {
        if (codes[bit] != 0 && ((next ^ consumer) & (1 << bit)) != 0)
          key(codes[bit], (next & (1 << bit)) != 0);
      }
      consumer = next;
      return "CONSUMER bits=" + next;
    }
    return "UNMAPPED instance=" + instance + " bytes=" + data.length;
  }
}
