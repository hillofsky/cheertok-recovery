package local.cheertok.probe;

import android.view.KeyEvent;
import java.io.*;

public class UhidBridge extends InputBridge {
  final Process hid;
  final Writer writer;
  volatile int physicalButtons = 0;
  volatile boolean tapLeft = false;
  private final ScrollPacer scrollPacer = new ScrollPacer();
  private final android.view.Display display;
  private final RecoveryOptions options;
  private long touchDownAt = -1;
  private float touchX, touchY;
  // S26 Ultra active screen height (from the diagonal and aspect ratio), versus
  // the CheerTok Air body length as a proxy for its unpublished pad travel.
  private static final float FAST_TRAVEL_GAIN = (float) Math.sqrt(159f / 69.5f);
  private static final float FAST_RELEASE_GAIN = (float) Math.sqrt(FAST_TRAVEL_GAIN);
  private int displayWidth, displayHeight;
  private boolean horizontalScroll, lastFlingHorizontal;
  private boolean reverseHorizontalTouch;
  private float wheelIntervalMs;
  private java.util.concurrent.ScheduledFuture<?> glide;
  private long glideGeneration;
  private long lastWheelAt = -1;
  private int scrollDirection, pendingUnits;
  private boolean wheelLocked;
  private long lastFlingAt = -1;
  private int touchUnits;
  final java.util.concurrent.ScheduledExecutorService tapTimer =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
  private static final long HOME_COMBO_MS = 450;
  private static final long BACK_DOUBLE_MS = 350;
  private java.util.concurrent.ScheduledFuture<?> homeAction;
  private int homeGeneration;
  private boolean awaitingSecondBack;

  private synchronized void queueHome() {
    if (!options.enabled(RecoveryOptions.HOME_SHORTCUT)) {
      sendHomeAction(KeyEvent.KEYCODE_HOME);
      return;
    }
    if (awaitingSecondBack) sendHomeAction(KeyEvent.KEYCODE_APP_SWITCH);
    scheduleHomeAction(false);
  }

  private synchronized void routeBack() {
    if (homeAction == null) {
      sendHomeAction(KeyEvent.KEYCODE_BACK);
    } else if (awaitingSecondBack) {
      homeAction.cancel(false);
      homeAction = null;
      homeGeneration++;
      awaitingSecondBack = false;
      switchToPreviousTask();
    } else {
      scheduleHomeAction(true);
    }
  }

  private void scheduleHomeAction(boolean recents) {
    if (homeAction != null) homeAction.cancel(false);
    awaitingSecondBack = recents;
    int generation = ++homeGeneration;
    homeAction = tapTimer.schedule(() -> {
      synchronized (this) {
        if (generation != homeGeneration) return;
        homeAction = null;
        awaitingSecondBack = false;
        sendHomeAction(recents ? KeyEvent.KEYCODE_APP_SWITCH : KeyEvent.KEYCODE_HOME);
      }
    }, recents ? BACK_DOUBLE_MS : HOME_COMBO_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  private void sendHomeAction(int code) {
    try {
      key(code, true);
      key(code, false);
    } catch (Exception e) {
      System.out.println("HOME_ERROR " + e);
    }
  }

  private void switchToPreviousTask() {
    try {
      Class<?> manager = Class.forName("android.app.ActivityTaskManager");
      Object service = manager.getMethod("getService").invoke(null);
      Class<?> api = Class.forName("android.app.IActivityTaskManager");
      int user = (Integer) android.app.ActivityManager.class.getMethod("getCurrentUser").invoke(null);
      Object slice = api.getMethod("getRecentTasks", int.class, int.class, int.class)
          .invoke(service, 12, 1, user);
      java.util.List<?> tasks = (java.util.List<?>) slice.getClass().getMethod("getList").invoke(slice);
      boolean foundCurrent = false;
      for (Object item : tasks) {
        android.app.ActivityManager.RecentTaskInfo task =
            (android.app.ActivityManager.RecentTaskInfo) item;
        Object configuration = task.getClass().getField("configuration").get(task);
        Object window = configuration.getClass().getField("windowConfiguration").get(configuration);
        int type = (Integer) window.getClass().getMethod("getActivityType").invoke(window);
        if (type != 1 || task.persistentId < 0) continue; // Standard app task.
        if (!foundCurrent) {
          foundCurrent = true;
          continue;
        }
        api.getMethod("startActivityFromRecents", int.class, android.os.Bundle.class)
            .invoke(service, task.persistentId, null);
        return;
      }
    } catch (Exception e) {
      System.out.println("HOME_SWITCH_ERROR " + e);
    }
  }

  final TapRouter taps =
      new TapRouter(
          android.os.SystemClock::uptimeMillis,
          (r, delay) -> {
            java.util.concurrent.Future<?> f =
                tapTimer.schedule(r, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            return () -> f.cancel(false);
          },
          new TapRouter.Sink() {
            public void left(boolean down) {
              tapLeft = down;
              try {
                byte[] packet = new byte[7];
                packet[0] = (byte) ((tapLeft || (physicalButtons & 4) != 0) ? 1 : 0);
                report(3, packet);
                System.out.println("TAP_LEFT " + down);
              } catch (Exception e) {
                System.out.println("TAP_ERROR " + e);
              }
            }

            public void back() {
              routeBack();
            }
          });
  static final String MAP =
      "05010902a10185030901a10005091901290315002501750195038102750595018101050109381581257f750895018106750c95020930093126ff071601f88106050c1581257f750895020a38020a2f028106c0c0050c0901a101850209300a23020a2402094009e909ea09cd0a2f021500250175019508810209a009820a2d020a2e02096f097009b509b695088102c0";

  UhidBridge(android.content.Context context, RecoveryOptions options) throws Exception {
    super();
    this.options = options;
    display =
        context
            .getSystemService(android.hardware.display.DisplayManager.class)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY);
    hid = new ProcessBuilder("/system/bin/hid", "-").redirectErrorStream(true).start();
    writer = new OutputStreamWriter(hid.getOutputStream(), "UTF-8");
    new Thread(
            () -> {
              try (BufferedReader r =
                  new BufferedReader(new InputStreamReader(hid.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) System.out.println("UHID " + line);
              } catch (Exception e) {
                System.out.println("UHID_LOG " + e);
              }
            },
            "hid-output")
        .start();
    byte[] map = new byte[MAP.length() / 2];
    for (int i = 0; i < map.length; i++)
      map[i] = (byte) Integer.parseInt(MAP.substring(i * 2, i * 2 + 2), 16);
    write(
        "{\"id\":55,\"command\":\"register\",\"name\":\"CheerTok Special"
            + " Recovery\",\"vid\":9354,\"pid\":33361,\"bus\":\"usb\",\"descriptor\":"
            + array(map)
            + "}");
  }

  static String array(byte[] bytes) {
    StringBuilder s = new StringBuilder("[");
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) s.append(',');
      s.append(bytes[i] & 255);
    }
    return s.append(']').toString();
  }

  synchronized void write(String json) throws Exception {
    writer.write(json + "\n");
    writer.flush();
  }

  void report(int id, byte[] payload) throws Exception {
    byte[] report = new byte[payload.length + 1];
    report[0] = (byte) id;
    System.arraycopy(payload, 0, report, 1, payload.length);
    write("{\"id\":55,\"command\":\"report\",\"report\":" + array(report) + "}");
  }

  @Override
  public String accept(int instance, byte[] data) throws Exception {
    if (instance == 28 && data.length == 7) {
      int next = data[0] & 7;
      boolean buttonChanged = next != physicalButtons;
      boolean pointerMoved = data[2] != 0 || data[3] != 0 || data[4] != 0;
      if (buttonChanged || pointerMoved) stopGlide();
      if ((next & 2) == 0 && (physicalButtons & 2) != 0) queueHome();
      physicalButtons = next;
      taps.update((next & 1) != 0);
      byte[] mouse = data.clone();
      mouse[0] = (byte) ((tapLeft || (next & 4) != 0) ? 1 : 0);
      int vertical = mouse[1];
      int horizontal = options.enabled(RecoveryOptions.REVERSE_HORIZONTAL) ? -mouse[5] : mouse[5];
      mouse[1] = mouse[5] = 0;
      report(3, mouse);
      if (vertical != 0 || horizontal != 0) {
        long now = android.os.SystemClock.uptimeMillis();
        long delay = scrollPacer.reserve(now);
        if (next == 0 && !buttonChanged && !pointerMoved && ((vertical == 0) != (horizontal == 0))
            && options.enabled(vertical != 0 ? RecoveryOptions.VERTICAL_TOUCH
                                              : RecoveryOptions.HORIZONTAL_TOUCH)) {
          routeScroll(now, vertical != 0 ? vertical : horizontal, horizontal != 0);
        } else {
          stopGlide();
          tapTimer.schedule(
              () -> sendScroll(vertical, horizontal),
              delay,
              java.util.concurrent.TimeUnit.MILLISECONDS);
        }
      }
      return "UHID_MOUSE buttons=" + next + " wheel=" + data[1] + " horizontal=" + data[5];
    }
    if (instance == 32 && data.length == 2) {
      stopGlide();
      report(2, data);
      return "UHID_CONSUMER " + hex(data);
    }
    return "IGNORE instance=" + instance;
  }

  private synchronized void stopGlide() {
    cancelScrollTimer();
    flushWheel();
    wheelLocked = false;
    lastWheelAt = -1;
    endTouch(android.view.MotionEvent.ACTION_CANCEL);
  }

  private void cancelScrollTimer() {
    glideGeneration++;
    if (glide != null) glide.cancel(false);
    glide = null;
  }

  private void flushWheel() {
    while (pendingUnits > 0) {
      sendAxisWheel(scrollDirection);
      pendingUnits--;
    }
  }

  private void sendAxisWheel(int units) {
    sendScroll(horizontalScroll ? 0 : units, horizontalScroll ? units : 0);
  }

  private synchronized void routeScroll(long now, int unitsInput, boolean horizontal) {
    int direction = Integer.signum(unitsInput);
    // A wheel tick may not interrupt an app's fling, but a new touch can.
    boolean interrupting = (touchDownAt >= 0 && direction != scrollDirection)
        || (lastFlingAt >= 0 && now - lastFlingAt <= 600 && horizontal == lastFlingHorizontal);
    if (lastWheelAt < 0 || direction != scrollDirection || horizontal != horizontalScroll || now - lastWheelAt > 75) {
      stopGlide();
      scrollDirection = direction;
      horizontalScroll = horizontal;
      wheelIntervalMs = 15;
    } else if (now > lastWheelAt) {
      wheelIntervalMs = 0.7f * wheelIntervalMs + 0.3f * (now - lastWheelAt);
    }
    lastWheelAt = now;
    if (wheelLocked || display == null) {
      sendAxisWheel(unitsInput);
      return;
    }

    if (touchDownAt < 0) {
      pendingUnits += Math.abs(unitsInput);
      if (!interrupting && pendingUnits < 4 && Math.abs(unitsInput) < 2) {
        if (glide == null) {
          long generation = glideGeneration;
          glide =
              tapTimer.schedule(
                  () -> {
                    synchronized (this) {
                      if (generation != glideGeneration) return;
                      glide = null;
                      wheelLocked = true;
                      flushWheel();
                    }
                  },
                  direction > 0 ? 48 : 50,
                  java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return;
      }
    }

    cancelScrollTimer();
    float speedScale = Math.max(0.65f, Math.min(1.4f, 15f / wheelIntervalMs));
    if (touchDownAt < 0) reverseHorizontalTouch = horizontal
        && options.enabled(RecoveryOptions.REVERSE_IN_RECENTS) && isRecentsFocused();
    int touchDirection = reverseHorizontalTouch ? -direction : direction;
    float releaseSpeed = touchDirection * (horizontal ? 2.67f : direction > 0 ? 2.88f : 2.46f)
        * speedScale * FAST_RELEASE_GAIN * options.inertia / 100f;
    try {
      int units = Math.abs(unitsInput);
      if (touchDownAt < 0) {
        android.graphics.Point size = new android.graphics.Point();
        display.getRealSize(size);
        if (size.x <= 0 || size.y <= 0) {
          flushWheel();
          wheelLocked = true;
          return;
        }
        displayWidth = size.x;
        displayHeight = size.y;
        // Positive wheel input produces a downward finger swipe.
        boolean fingerDown = direction > 0;
        boolean portrait = size.y > size.x;
        touchX = size.x * (portrait ? (fingerDown ? 0.711f : 0.719f) : 0.5f);
        touchY = size.y * (portrait ? (fingerDown ? 0.418f : 0.726f)
                                   : (fingerDown ? 0.23f : 0.77f));
        if (horizontal) {
          touchX = size.x * (touchDirection > 0 ? 0.35f : 0.65f);
          touchY = size.y * 0.55f;
        }
        touchDownAt = android.os.SystemClock.uptimeMillis();
        emitTouch(android.view.MotionEvent.ACTION_DOWN);
        lastFlingAt = -1;
        touchUnits = 0;
        units = pendingUnits;
        pendingUnits = 0;
      }
      // Leave part of the measured travel for the timed release motion.
      float travel = (direction > 0 ? 0.007f : 0.008f) * units * FAST_TRAVEL_GAIN * options.travel / 100f;
      float position = horizontal ? touchX / displayWidth : touchY / displayHeight;
      float room = touchDirection > 0 ? 0.90f - position : position - 0.10f;
      touchUnits += units;
      float reserve = touchUnits >= 2 ? Math.abs(releaseSpeed) * 0.036f : 0;
      moveTouch(touchDirection * Math.min(travel, Math.max(0f, room - reserve)));
    } catch (Exception e) {
      stopGlide();
      wheelLocked = true;
      System.out.println("INJECT_ERROR " + e);
      return;
    }

    scheduleRelease(glideGeneration, releaseSpeed, touchUnits >= 2 && options.inertia > 0 ? 3 : 0, 38);
  }

  private void scheduleRelease(long generation, float speed, int remaining, long delayMs) {
    glide =
        tapTimer.schedule(
            () -> {
              synchronized (this) {
                if (generation != glideGeneration) return;
                if (remaining == 0) {
                  endTouch(android.view.MotionEvent.ACTION_UP);
                  if (touchUnits >= 2 && options.inertia > 0) {
                    lastFlingAt = android.os.SystemClock.uptimeMillis();
                    lastFlingHorizontal = horizontalScroll;
                  }
                  glide = null;
                  lastWheelAt = -1;
                  return;
                }
                try {
                  moveTouch(speed * 0.012f);
                  scheduleRelease(generation, speed, remaining - 1, remaining == 1 ? 8 : 12);
                } catch (Exception e) {
                  stopGlide();
                  System.out.println("INJECT_ERROR " + e);
                }
              }
            },
            delayMs,
            java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  private boolean isRecentsFocused() {
    try {
      Class<?> manager = Class.forName("android.app.ActivityTaskManager");
      Object service = manager.getMethod("getService").invoke(null);
      Class<?> api = Class.forName("android.app.IActivityTaskManager");
      Object task = api.getMethod("getFocusedRootTaskInfo").invoke(service);
      if (task == null) return false;
      Object configuration = task.getClass().getField("configuration").get(task);
      Object window = configuration.getClass().getField("windowConfiguration").get(configuration);
      return (Integer) window.getClass().getMethod("getActivityType").invoke(window) == 3;
    } catch (Exception e) {
      System.out.println("RECENTS_LOOKUP_ERROR " + e);
      return false;
    }
  }

  private void moveTouch(float screenFraction) throws Exception {
    if (horizontalScroll) {
      touchX = Math.max(displayWidth * 0.10f,
          Math.min(displayWidth * 0.90f, touchX + displayWidth * screenFraction));
    } else {
      touchY = Math.max(displayHeight * 0.10f,
          Math.min(displayHeight * 0.90f, touchY + displayHeight * screenFraction));
    }
    emitTouch(android.view.MotionEvent.ACTION_MOVE);
  }

  private void endTouch(int action) {
    if (touchDownAt < 0) return;
    try {
      emitTouch(action);
    } catch (Exception e) {
      System.out.println("INJECT_ERROR " + e);
    } finally {
      touchDownAt = -1;
    }
  }

  private void emitTouch(int action) throws Exception {
    android.view.MotionEvent event =
        android.view.MotionEvent.obtain(
            touchDownAt, android.os.SystemClock.uptimeMillis(), action, touchX, touchY, 0);
    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
    try {
      if (!send(event)) throw new IllegalStateException("Touch injection rejected");
    } finally {
      event.recycle();
    }
  }

  private synchronized void sendScroll(int vertical, int horizontal) {
    byte[] packet = new byte[7];
    packet[0] = (byte) ((tapLeft || (physicalButtons & 4) != 0) ? 1 : 0);
    packet[1] = (byte) vertical;
    packet[5] = (byte) horizontal;
    try {
      report(3, packet);
    } catch (Exception e) {
      System.out.println("INJECT_ERROR " + e);
    }
  }

  static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format("%02x", b & 255));
    return s.toString();
  }
}
