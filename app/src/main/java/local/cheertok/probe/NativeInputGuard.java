package local.cheertok.probe;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.view.InputDevice;

public final class NativeInputGuard {
  final Context context;
  final BluetoothDevice device;
  final Handler handler;
  BluetoothProfile profile;
  boolean closed = false;

  public NativeInputGuard(Context context, BluetoothDevice device, Handler handler) {
    this.context = context;
    this.device = device;
    this.handler = handler;
  }

  public void start() {
    BluetoothAdapter adapter = context.getSystemService(BluetoothManager.class).getAdapter();
    adapter.getProfileProxy(
        context,
        new BluetoothProfile.ServiceListener() {
          public void onServiceDisconnected(int p) {
            profile = null;
          }

          public void onServiceConnected(int p, BluetoothProfile value) {
            profile = value;
            check();
          }
        },
        4);
  }

  void check() {
    if (closed) return;
    boolean nativePresent = false;
    for (int id : InputDevice.getDeviceIds()) {
      InputDevice input = InputDevice.getDevice(id);
      if (input != null
          && input.getVendorId() == 0x248a
          && input.getProductId() == 0x8251
          && input.getName().startsWith("CheerTok Special ")
          && !input.getName().contains("Recovery")) {
        nativePresent = true;
        break;
      }
    }
    if (nativePresent && profile != null) {
      try {
        Object ok =
            profile
                .getClass()
                .getMethod("disconnect", BluetoothDevice.class)
                .invoke(profile, device);
        System.out.println("NATIVE_HID_SUPPRESS " + ok);
      } catch (Exception e) {
        System.out.println("NATIVE_HID_SUPPRESS_ERROR " + e);
      }
    }
    handler.postDelayed(this::check, 2000);
  }
}
