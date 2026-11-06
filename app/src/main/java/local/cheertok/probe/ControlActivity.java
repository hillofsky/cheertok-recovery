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
      if (code == 1) {
        android.content.SharedPreferences prefs = getPreferences(0);
        in.writeInt(prefs.getInt("flags", RecoveryOptions.DEFAULT_FLAGS));
        in.writeInt(prefs.getInt("travel", RecoveryOptions.DEFAULT_PERCENT));
        in.writeInt(prefs.getInt("inertia", RecoveryOptions.DEFAULT_PERCENT));
      }
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
            .version(21)
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
    Button settings = new Button(this);
    settings.setText("복구 옵션");
    settings.setOnClickListener(v -> showOptions());
    layout.addView(settings);
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

  private void showOptions() {
    SharedPreferences prefs = getPreferences(0);
    int savedFlags = prefs.getInt("flags", RecoveryOptions.DEFAULT_FLAGS);
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(1);
    int padding = (int) (20 * getResources().getDisplayMetrics().density);
    fields.setPadding(padding, 0, padding, padding);
    String[] labels = {
      "빠른 세로 스크롤을 터치로 처리", "빠른 가로 스크롤을 터치로 처리",
      "가로 스크롤 방향 반전", "최근 앱에서만 가로 터치 방향 추가 반전",
      "홈 → 뒤로가기 멀티태스킹 조합"
    };
    int[] flags = {
      RecoveryOptions.VERTICAL_TOUCH, RecoveryOptions.HORIZONTAL_TOUCH,
      RecoveryOptions.REVERSE_HORIZONTAL, RecoveryOptions.REVERSE_IN_RECENTS,
      RecoveryOptions.HOME_SHORTCUT
    };
    Switch[] switches = new Switch[labels.length];
    for (int i = 0; i < labels.length; i++) {
      switches[i] = new Switch(this);
      switches[i].setText(labels[i]);
      switches[i].setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
      switches[i].setChecked((savedFlags & flags[i]) != 0);
      fields.addView(switches[i]);
    }
    SeekBar travel = percentSlider(fields, "터치 이동량", 50, 200, prefs.getInt("travel", 100));
    SeekBar inertia = percentSlider(fields, "추가 관성", 0, 150, prefs.getInt("inertia", 100));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(fields);
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("복구 옵션")
        .setView(scroll)
        .setNegativeButton("취소", null)
        .setNeutralButton("기본값", null)
        .setPositiveButton("저장 및 적용", (d, which) -> {
          int value = 0;
          for (int i = 0; i < flags.length; i++) {
            if (switches[i].isChecked()) value |= flags[i];
          }
          prefs.edit().putInt("flags", value).putInt("travel", 50 + travel.getProgress())
              .putInt("inertia", inertia.getProgress()).apply();
          if (desired) enable();
        })
        .show();
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
      for (Switch option : switches) option.setChecked(true);
      travel.setProgress(50);
      inertia.setProgress(100);
    });
  }

  private SeekBar percentSlider(LinearLayout fields, String label, int min, int max, int value) {
    TextView text = new TextView(this);
    text.setText(label + " " + value + "%");
    fields.addView(text);
    SeekBar bar = new SeekBar(this);
    bar.setContentDescription(label);
    bar.setMax(max - min);
    bar.setProgress(value - min);
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar view, int progress, boolean fromUser) {
        text.setText(label + " " + (min + progress) + "%");
      }
      public void onStartTrackingTouch(SeekBar view) {}
      public void onStopTrackingTouch(SeekBar view) {}
    });
    fields.addView(bar);
    return bar;
  }

  public void onDestroy() {
    ui.removeCallbacksAndMessages(null);
    Shizuku.removeBinderReceivedListener(received);
    Shizuku.removeRequestPermissionResultListener(permission);
    super.onDestroy();
  }
}
