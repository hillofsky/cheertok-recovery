package local.cheertok.probe;

import android.app.*;
import android.bluetooth.*;
import android.os.*;
import android.util.Log;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  final Handler handler = new Handler(Looper.getMainLooper());
  final ArrayDeque<Runnable> queue = new ArrayDeque<>();
  BluetoothGatt gatt;
  TextView status;
  File logfile;
  boolean busy = false;
  int seq = 0;

  void out(String message) {
    String line = System.currentTimeMillis() + " " + message;
    Log.i("CheerProbe", line);
    try (FileWriter w = new FileWriter(logfile, true)) {
      w.write(line + "\n");
    } catch (Exception e) {
    }
    handler.post(() -> status.setText(line));
  }

  static String hex(byte[] bytes) {
    if (bytes == null) return "null";
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format("%02x", b & 255));
    return s.toString();
  }

  void onRaw(int instance, byte[] data) {}

  void onGattConnected(BluetoothDevice device) {}

  public void onCreate(Bundle b) {
    super.onCreate(b);
    logfile = new File(getFilesDir(), "probe.txt");
    status = new TextView(this);
    status.setTextSize(18);
    setContentView(status);
    out("START");
    if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != 0) {
      requestPermissions(
          new String[] {
            "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"
          },
          1);
    } else connect();
  }

  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == 0) connect();
    else out("PERMISSION_DENIED");
  }

  void connect() {
    try {
      BluetoothManager bm = getSystemService(BluetoothManager.class);
      BluetoothDevice chosen = null;
      for (BluetoothDevice d : bm.getAdapter().getBondedDevices()) {
        if ("CheerTok Special".equals(d.getName())) {
          out("SPECIAL_BONDED " + d.getAddress());
          if (chosen == null) chosen = d;
          for (BluetoothDevice c : bm.getConnectedDevices(BluetoothProfile.GATT)) {
            if (c.equals(d)) chosen = d;
          }
        }
      }
      String address = getIntent().getStringExtra("address");
      if (address != null) chosen = bm.getAdapter().getRemoteDevice(address);
      if (chosen == null) {
        out("NO_SPECIAL_BOND");
        return;
      }
      out("CONNECTING " + chosen.getAddress());
      gatt = chosen.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE);
    } catch (Exception e) {
      out("CONNECT_ERROR " + e);
    }
  }

  void next() {
    handler.post(
        () -> {
          if (busy) return;
          Runnable r = queue.poll();
          if (r == null) {
            out("READY_FOR_BUTTONS");
            return;
          }
          busy = true;
          int ticket = ++seq;
          handler.postDelayed(
              () -> {
                if (busy && ticket == seq) {
                  out("OP_TIMEOUT");
                  busy = false;
                  next();
                }
              },
              5000);
          r.run();
        });
  }

  void done() {
    handler.post(
        () -> {
          busy = false;
          ++seq;
          next();
        });
  }

  final BluetoothGattCallback callback =
      new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt g, int s, int n) {
          out("STATE status=" + s + " state=" + n);
          if (n == BluetoothProfile.STATE_CONNECTED) {
            onGattConnected(g.getDevice());
            if (getIntent().getBooleanExtra("refresh", false)) {
              try {
                out("CACHE_REFRESH " + g.getClass().getMethod("refresh").invoke(g));
              } catch (Exception e) {
                out("REFRESH_ERROR " + e);
              }
              handler.postDelayed(() -> out("DISCOVER " + g.discoverServices()), 2000);
            } else {
              out("DISCOVER " + g.discoverServices());
            }
          }
        }

        public void onServicesDiscovered(BluetoothGatt g, int s) {
          out("SERVICES status=" + s);
          for (BluetoothGattService service : g.getServices()) {
            out("SERVICE " + service.getUuid());
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
              out(
                  "CHAR "
                      + c.getUuid()
                      + " instance="
                      + c.getInstanceId()
                      + " props="
                      + c.getProperties());
              for (BluetoothGattDescriptor d : c.getDescriptors()) {
                out("DESC " + d.getUuid());
                queue.add(
                    () -> {
                      try {
                        boolean ok = g.readDescriptor(d);
                        out("DESC_READ_START " + d.getUuid() + " accepted=" + ok);
                        if (!ok) done();
                      } catch (Exception e) {
                        out("DESC_READ_ERROR " + e);
                        done();
                      }
                    });
              }
              if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0)
                queue.add(
                    () -> {
                      try {
                        out("READ_START " + c.getUuid() + " accepted=" + g.readCharacteristic(c));
                      } catch (Exception e) {
                        out("READ_ERROR " + c.getUuid() + " " + e);
                        done();
                      }
                    });
              if ((c.getProperties()
                      & (BluetoothGattCharacteristic.PROPERTY_NOTIFY
                          | BluetoothGattCharacteristic.PROPERTY_INDICATE))
                  != 0) {
                BluetoothGattDescriptor ccc =
                    c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                queue.add(
                    () -> {
                      try {
                        boolean ok = g.setCharacteristicNotification(c, true);
                        out("NOTIFY_LOCAL " + c.getUuid() + " accepted=" + ok);
                        if (ccc == null) {
                          done();
                          return;
                        }
                        ccc.setValue(
                            (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        if (!g.writeDescriptor(ccc)) {
                          out("CCC_REJECTED " + c.getUuid());
                          done();
                        }
                      } catch (Exception e) {
                        out("NOTIFY_ERROR " + c.getUuid() + " " + e);
                        done();
                      }
                    });
              }
            }
          }
          next();
        }

        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int s) {
          out(
              "READ "
                  + c.getUuid()
                  + " instance="
                  + c.getInstanceId()
                  + " status="
                  + s
                  + " data="
                  + hex(c.getValue()));
          done();
        }

        public void onDescriptorRead(BluetoothGatt g, BluetoothGattDescriptor d, int s) {
          out(
              "DESC_READ char="
                  + d.getCharacteristic().getUuid()
                  + " instance="
                  + d.getCharacteristic().getInstanceId()
                  + " uuid="
                  + d.getUuid()
                  + " status="
                  + s
                  + " data="
                  + hex(d.getValue()));
          done();
        }

        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int s) {
          out("CCC " + d.getCharacteristic().getUuid() + " status=" + s);
          done();
        }

        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
          out(
              "DATA "
                  + c.getUuid()
                  + " instance="
                  + c.getInstanceId()
                  + " data="
                  + hex(c.getValue()));
          onRaw(c.getInstanceId(), c.getValue());
        }
      };

  public void onDestroy() {
    if (gatt != null) {
      gatt.disconnect();
      gatt.close();
    }
    super.onDestroy();
  }
}
