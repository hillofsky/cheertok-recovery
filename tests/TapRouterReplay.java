package local.cheertok.probe;

import java.util.*;

public class TapRouterReplay {
  static long now;
  static Runnable pending;
  static List<String> actual;

  static void sample(long duration, String... expected) {
    now = 0;
    pending = null;
    actual = new ArrayList<>();
    TapRouter router =
        new TapRouter(
            () -> now,
            (r, delay) -> {
              pending = r;
              return () -> pending = null;
            },
            new TapRouter.Sink() {
              public void left(boolean down) {
                actual.add(down ? "DOWN" : "UP");
              }

              public void back() {
                actual.add("BACK");
              }
            });
    router.update(true);
    if (duration >= 12) {
      now = 12;
      pending.run();
    }
    now = duration;
    router.update(false);
    if (!actual.equals(Arrays.asList(expected)))
      throw new AssertionError(duration + "ms: " + actual);
    System.out.println("PASS recorded " + duration + "ms: " + actual);
  }

  public static void main(String[] args) {
    sample(1, "BACK");
    sample(0, "BACK");
    sample(96, "DOWN", "UP");
    sample(20, "DOWN", "UP");
    sample(107, "DOWN", "UP");
    sample(600, "DOWN", "UP");
  }
}
