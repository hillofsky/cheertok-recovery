package local.cheertok.probe;

final class ScrollPacer {
  private static final long FRAME_MS = 16;
  private static final long MAX_DELAY_MS = 48;
  private long nextFrame;

  synchronized long reserve(long now) {
    long at = Math.min(Math.max(now, nextFrame), now + MAX_DELAY_MS);
    nextFrame = at + FRAME_MS;
    return at - now;
  }
}
