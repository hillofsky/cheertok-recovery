package local.cheertok.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

public class ControlActivity extends Activity {
  TextView status;
  Switch toggle;
  IBinder service;
  boolean changing = false, desired = false, binding = false;
  final Handler ui = new Handler(Looper.getMainLooper());
  final Shizuku.OnBinderReceivedListener received =
      () ->
          ui.post(
              () -> {
                status.setText("Shizuku 연결됨");
                if (desired) enable();
              });
  final Shizuku.OnRequestPermissionResultListener permission =
      (r, g) ->
          ui.post(
              () -> {
                if (g == 0 && desired) enable();
                else {
                  desired = false;
                  setToggle(false);
                  status.setText("Shizuku 권한이 필요합니다");
                }
              });
  Shizuku.UserServiceArgs args;
  final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
          service = b;
          binding = false;
          call(desired ? 1 : 3);
          setToggle(desired);
        }

        public void onServiceDisconnected(ComponentName n) {
          service = null;
          binding = false;
          status.setText("Shizuku를 시작한 뒤 다시 켜주세요");
        }
      };

  String call(int code) {
    if (service == null) return "연결 대기";
    Parcel in = Parcel.obtain(), out = Parcel.obtain();
    try {
      in.writeInterfaceToken(RecoveryService.TOKEN);
      service.transact(code, in, out, 0);
      out.readException();
      String s = out.readString();
      status.setText(s);
      return s;
    } catch (Exception e) {
      status.setText("연결 오류: " + e.getMessage());
      return "오류";
    } finally {
      in.recycle();
      out.recycle();
    }
  }

  void setToggle(boolean v) {
    changing = true;
    toggle.setChecked(v);
    changing = false;
  }

  void enable() {
    if (!Shizuku.pingBinder()) {
      status.setText("먼저 Shizuku를 시작해주세요");
      setToggle(false);
      return;
    }
    if (Shizuku.checkSelfPermission() != 0) {
      Shizuku.requestPermission(5);
      return;
    }
    if (service != null) {
      call(1);
      setToggle(true);
      return;
    }
    if (!binding) {
      binding = true;
      Shizuku.bindUserService(args, connection);
    }
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(128);
    desired = getPreferences(0).getBoolean("enabled", false);
    args =
        new Shizuku.UserServiceArgs(new ComponentName(this, RecoveryService.class))
            .daemon(true)
            .tag("cheertok-recovery")
            .version(5)
            .processNameSuffix("recovery");
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(1);
    layout.setPadding(36, 48, 36, 36);
    TextView title = new TextView(this);
    title.setText("치어톡 스페셜 복구");
    title.setTextSize(25);
    layout.addView(title);
    status = new TextView(this);
    status.setText("Shizuku를 시작한 뒤 복구를 켜주세요");
    status.setTextSize(17);
    layout.addView(status);
    toggle = new Switch(this);
    toggle.setText("치어톡 복구");
    toggle.setPadding(0, 32, 0, 32);
    layout.addView(toggle);
    Button shizuku = new Button(this);
    shizuku.setText("Shizuku 열기");
    shizuku.setOnClickListener(
        v -> {
          Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
          if (i != null) startActivity(i);
          else status.setText("Shizuku 설치가 필요합니다");
        });
    layout.addView(shizuku);
    TextView help = new TextView(this);
    help.setText("치어톡을 스페셜 모드로 연결해주세요.\n재부팅 후 Shizuku를 시작하고 이 스위치를 켜세요.\n앱 화면을 닫아도 복구는 계속 실행됩니다.");
    help.setTextSize(16);
    layout.addView(help);
    setContentView(layout);
    toggle.setOnCheckedChangeListener(
        (button, checked) -> {
          if (changing) return;
          desired = checked;
          getPreferences(0).edit().putBoolean("enabled", checked).apply();
          if (checked) enable();
          else call(2);
        });
    Shizuku.addBinderReceivedListenerSticky(received);
    Shizuku.addRequestPermissionResultListener(permission);
    ui.postDelayed(
        new Runnable() {
          public void run() {
            if (service != null) call(3);
            ui.postDelayed(this, 2000);
          }
        },
        2000);
  }

  public void onDestroy() {
    ui.removeCallbacksAndMessages(null);
    Shizuku.removeBinderReceivedListener(received);
    Shizuku.removeRequestPermissionResultListener(permission);
    super.onDestroy();
  }
}
