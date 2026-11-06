package local.cheertok.probe;

import android.content.Context;
import android.os.*;
import java.io.*;

public class RecoveryService extends Binder {
  static final String TOKEN = "local.cheertok.recovery";
  final String apk;
  java.lang.Process worker;
  RecoveryOptions activeOptions;
  String status = "꺼짐";

  public RecoveryService(Context context) {
    apk = context.getApplicationInfo().sourceDir;
    attachInterface(null, TOKEN);
  }

  synchronized String start(RecoveryOptions options) {
    if (worker != null && worker.isAlive()) {
      if (activeOptions.flags == options.flags && activeOptions.travel == options.travel
          && activeOptions.inertia == options.inertia) return status;
      worker.destroy();
      try {
        if (!worker.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
          worker.destroyForcibly();
          if (!worker.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            return "이전 복구를 종료하지 못했습니다. 다시 시도해주세요.";
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return "설정 적용이 중단됐습니다";
      }
    }
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
              "/system/bin/app_process", "/", "local.cheertok.probe.ShellProbe", "auto", "uhid",
              Integer.toString(options.flags), Integer.toString(options.travel),
              Integer.toString(options.inertia));
      pb.environment().put("CLASSPATH", apk);
      pb.redirectErrorStream(true);
      worker = pb.start();
      activeOptions = options;
      status = "연결 중…";
      java.lang.Process current = worker;
      new Thread(
              () -> {
                try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(current.getInputStream()))) {
                  String line;
                  while ((line = r.readLine()) != null) {
                    android.util.Log.i("CheerRecovery", line);
                    synchronized (RecoveryService.this) {
                      if (current != worker) break;
                      if (line.contains("READY_FOR_BUTTONS")) status = "실행 중";
                      else if (line.contains("CONNECT_ERROR")
                          || line.contains("NO_SPECIAL_BOND")
                          || line.contains("INJECT_ERROR")
                          || line.contains("TAP_ERROR")) status = "오류: " + line;
                    }
                  }
                } catch (Exception e) {
                  synchronized (RecoveryService.this) {
                    if (current == worker) status = "오류: " + e.getMessage();
                  }
                }
                synchronized (RecoveryService.this) {
                  if (current == worker && !current.isAlive()) status = "중지됨 — 다시 켜주세요";
                }
              },
              "recovery-status")
          .start();
      return status;
    } catch (Exception e) {
      status = "시작 실패: " + e.getMessage();
      return status;
    }
  }

  synchronized String stop() {
    if (worker != null) {
      worker.destroy();
      worker = null;
      try {
        ProcessBuilder restore =
            new ProcessBuilder(
                "/system/bin/app_process",
                "/",
                "local.cheertok.probe.ShellProbe",
                "auto",
                "restore");
        restore.environment().put("CLASSPATH", apk);
        restore.redirectOutput(new File("/data/local/tmp/cheertok-restore.log"));
        restore.redirectErrorStream(true);
        restore.start();
      } catch (Exception e) {
        android.util.Log.w("CheerRecovery", "Native HID restore failed", e);
      }
    }
    status = "꺼짐";
    return status;
  }

  @Override
  protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
      throws RemoteException {
    if (code == INTERFACE_TRANSACTION) {
      reply.writeString(TOKEN);
      return true;
    }
    if (code == 16777115) {
      stop();
      System.exit(0);
      return true;
    }
    data.enforceInterface(TOKEN);
    String result;
    if (code == 1) result = start(new RecoveryOptions(data.readInt(), data.readInt(), data.readInt()));
    else if (code == 2) result = stop();
    else if (code == 3) {
      synchronized (this) {
        result = status;
      }
    } else return super.onTransact(code, data, reply, flags);
    reply.writeNoException();
    reply.writeString(result);
    return true;
  }
}
