package local.cheertok.probe;

import android.content.*;
import android.os.*;
import java.lang.reflect.*;

public class ShellProbe extends MainActivity {
  Intent launchIntent = new Intent();
  InputBridge bridge;
  NativeInputGuard nativeGuard;

  @Override
  void onGattConnected(android.bluetooth.BluetoothDevice device) {
    if (bridge instanceof UhidBridge && nativeGuard == null) {
      nativeGuard = new NativeInputGuard(this, device, handler);
      nativeGuard.start();
    }
  }

  @Override
  void onRaw(int instance, byte[] data) {
    if (bridge != null)
      try {
        out("INJECT " + bridge.accept(instance, data));
      } catch (Exception e) {
        out("INJECT_ERROR " + e);
      }
  }

  ShellProbe(Context context, String address) {
    attachBaseContext(context);
    if (address != null) launchIntent.putExtra("address", address);
  }

  @Override
  void out(String message) {
    if (message.startsWith("DATA ")
        || message.startsWith("INJECT ")
        || message.startsWith("READ ")
        || message.startsWith("DESC_READ ")
        || message.startsWith("SPECIAL_BONDED ")
        || message.startsWith("CONNECTING ")) return;
    System.out.println(System.currentTimeMillis() + " " + message);
    System.out.flush();
  }

  @Override
  public Intent getIntent() {
    return launchIntent;
  }

  public static void main(String[] args) throws Exception {
    Looper.prepareMainLooper();
    Class<?> activityThread = Class.forName("android.app.ActivityThread");
    Object thread = activityThread.getMethod("systemMain").invoke(null);
    Class<?> initializer = Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
    if (initializer.getMethod("getBluetoothServiceManager").invoke(null) == null) {
      Class<?> manager = Class.forName("android.os.BluetoothServiceManager");
      initializer
          .getMethod("setBluetoothServiceManager", manager)
          .invoke(null, manager.getConstructor().newInstance());
    }
    Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
    Context shell =
        system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
    android.app.Application application = new android.app.Application();
    Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
    attach.setAccessible(true);
    attach.invoke(application, shell);
    Field initial = activityThread.getDeclaredField("mInitialApplication");
    initial.setAccessible(true);
    initial.set(thread, application);
    ShellProbe probe =
        new ShellProbe(shell, args.length > 0 && !"auto".equals(args[0]) ? args[0] : null);
    if (args.length > 1 && "refresh".equals(args[1])) probe.launchIntent.putExtra("refresh", true);
    if (args.length > 1 && "bridge".equals(args[1])) {
      probe.bridge = new InputBridge();
      probe.launchIntent.putExtra("refresh", true);
    }
    if (args.length > 1 && "uhid".equals(args[1])) {
      probe.bridge = new UhidBridge();
      probe.launchIntent.putExtra("refresh", true);
    }
    probe.out("SHELL_PROBE_START");
    if (args.length > 1 && "restore".equals(args[1])) probe.restoreNative();
    else if (args.length > 1 && "reconnect".equals(args[1])) probe.reconnect(args[0]);
    else if (args.length > 1 && "rebond".equals(args[1])) probe.rebond(args[0]);
    else probe.connect();
    Looper.loop();
  }

  void restoreNative() {
    android.bluetooth.BluetoothAdapter adapter =
        getSystemService(android.bluetooth.BluetoothManager.class).getAdapter();
    android.bluetooth.BluetoothDevice found = null;
    for (android.bluetooth.BluetoothDevice d : adapter.getBondedDevices())
      if ("CheerTok Special".equals(d.getName())) {
        found = d;
        break;
      }
    if (found == null) {
      System.exit(0);
      return;
    }
    final android.bluetooth.BluetoothDevice target = found;
    adapter.getProfileProxy(
        this,
        new android.bluetooth.BluetoothProfile.ServiceListener() {
          public void onServiceDisconnected(int p) {}

          public void onServiceConnected(int p, android.bluetooth.BluetoothProfile proxy) {
            try {
              out(
                  "NATIVE_HID_RESTORE "
                      + proxy
                          .getClass()
                          .getMethod("connect", android.bluetooth.BluetoothDevice.class)
                          .invoke(proxy, target));
            } catch (Exception e) {
              out("NATIVE_HID_RESTORE_ERROR " + e);
            }
            handler.postDelayed(
                () -> {
                  adapter.closeProfileProxy(p, proxy);
                  System.exit(0);
                },
                2000);
          }
        },
        4);
    handler.postDelayed(() -> System.exit(0), 6000);
  }

  void rebond(String address) throws Exception {
    android.bluetooth.BluetoothDevice device =
        getSystemService(android.bluetooth.BluetoothManager.class)
            .getAdapter()
            .getRemoteDevice(address);
    out("REMOVE_SPECIAL_BOND " + device.getClass().getMethod("removeBond").invoke(device));
    handler.postDelayed(
        new Runnable() {
          int tries = 0;
          boolean requested = false;

          public void run() {
            int state = device.getBondState();
            out("BOND_STATE " + state);
            if (state == android.bluetooth.BluetoothDevice.BOND_NONE && !requested) {
              requested = true;
              out("CREATE_SPECIAL_BOND " + device.createBond());
            } else if (state == android.bluetooth.BluetoothDevice.BOND_BONDED && requested) {
              out("REBOND_COMPLETE");
              reconnect(address);
              return;
            }
            if (++tries < 30) handler.postDelayed(this, 1000);
            else {
              out("REBOND_NEEDS_USER");
              System.exit(2);
            }
          }
        },
        1000);
  }

  void reconnect(String address) {
    android.bluetooth.BluetoothManager manager =
        getSystemService(android.bluetooth.BluetoothManager.class);
    android.bluetooth.BluetoothAdapter adapter = manager.getAdapter();
    android.bluetooth.BluetoothDevice device = adapter.getRemoteDevice(address);
    adapter.getProfileProxy(
        this,
        new android.bluetooth.BluetoothProfile.ServiceListener() {
          public void onServiceDisconnected(int p) {}

          public void onServiceConnected(int p, android.bluetooth.BluetoothProfile proxy) {
            try {
              out(
                  "HID_DISCONNECT "
                      + proxy
                          .getClass()
                          .getMethod("disconnect", android.bluetooth.BluetoothDevice.class)
                          .invoke(proxy, device));
              handler.postDelayed(
                  () -> {
                    try {
                      out(
                          "HID_CONNECT "
                              + proxy
                                  .getClass()
                                  .getMethod("connect", android.bluetooth.BluetoothDevice.class)
                                  .invoke(proxy, device));
                    } catch (Exception e) {
                      out("HID_CONNECT_ERROR " + e);
                    }
                    handler.postDelayed(
                        () -> {
                          adapter.closeProfileProxy(p, proxy);
                          System.exit(0);
                        },
                        5000);
                  },
                  2000);
            } catch (Exception e) {
              out("HID_ERROR " + e);
              System.exit(1);
            }
          }
        },
        4);
  }
}
