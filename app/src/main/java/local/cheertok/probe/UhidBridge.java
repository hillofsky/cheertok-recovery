package local.cheertok.probe;

import android.view.KeyEvent;
import java.io.*;

public class UhidBridge extends InputBridge {
  final Process hid;
  final Writer writer;
  volatile int physicalButtons = 0;
  volatile boolean tapLeft = false;
  final java.util.concurrent.ScheduledExecutorService tapTimer =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
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
              try {
                key(KeyEvent.KEYCODE_BACK, true);
                key(KeyEvent.KEYCODE_BACK, false);
                System.out.println("PHYSICAL_BACK");
              } catch (Exception e) {
                System.out.println("TAP_ERROR " + e);
              }
            }
          });
  static final String MAP =
      "05010902a10185030901a10005091901290315002501750195038102750595018101050109381581257f750895018106750c95020930093126ff071601f88106050c1581257f750895020a38020a2f028106c0c0050c0901a101850209300a23020a2402094009e909ea09cd0a2f021500250175019508810209a009820a2d020a2e02096f097009b509b695088102c0";

  public UhidBridge() throws Exception {
    super();
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
      if ((next & 2) != (physicalButtons & 2)) key(KeyEvent.KEYCODE_HOME, (next & 2) != 0);
      physicalButtons = next;
      taps.update((next & 1) != 0);
      byte[] mouse = data.clone();
      mouse[0] = (byte) ((tapLeft || (next & 4) != 0) ? 1 : 0);
      report(3, mouse);
      return "UHID_MOUSE buttons=" + next + " wheel=" + data[1] + " horizontal=" + data[5];
    }
    if (instance == 32 && data.length == 2) {
      report(2, data);
      return "UHID_CONSUMER " + hex(data);
    }
    return "IGNORE instance=" + instance;
  }

  static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format("%02x", b & 255));
    return s.toString();
  }
}
