package local.cheertok.probe;

public final class TapRouter {
  public interface Clock {
    long now();
  }

  public interface Cancel {
    void cancel();
  }

  public interface Scheduler {
    Cancel after(Runnable r, long delay);
  }

  public interface Sink {
    void left(boolean down);

    void back();
  }

  final Clock clock;
  final Scheduler scheduler;
  final Sink sink;
  boolean pressed, forwarded;
  long started;
  Cancel pending;

  public TapRouter(Clock clock, Scheduler scheduler, Sink sink) {
    this.clock = clock;
    this.scheduler = scheduler;
    this.sink = sink;
  }

  public synchronized void update(boolean down) {
    if (down == pressed) return;
    pressed = down;
    if (down) {
      started = clock.now();
      forwarded = false;
      pending =
          scheduler.after(
              () -> {
                synchronized (TapRouter.this) {
                  if (pressed && !forwarded) {
                    forwarded = true;
                    sink.left(true);
                  }
                }
              },
              12);
    } else {
      if (pending != null) pending.cancel();
      if (forwarded) sink.left(false);
      else if (clock.now() - started >= 12) {
        sink.left(true);
        sink.left(false);
      } else sink.back();
      forwarded = false;
    }
  }
}
