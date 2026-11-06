package local.cheertok.probe;

final class RecoveryOptions {
  static final int VERTICAL_TOUCH = 1;
  static final int HORIZONTAL_TOUCH = 2;
  static final int REVERSE_HORIZONTAL = 4;
  static final int REVERSE_IN_RECENTS = 8;
  static final int HOME_SHORTCUT = 16;
  static final int DEFAULT_FLAGS = 31;
  static final int DEFAULT_PERCENT = 100;

  final int flags, travel, inertia;

  RecoveryOptions(int flags, int travel, int inertia) {
    this.flags = flags & DEFAULT_FLAGS;
    this.travel = Math.max(50, Math.min(200, travel));
    this.inertia = Math.max(0, Math.min(150, inertia));
  }

  boolean enabled(int flag) {
    return (flags & flag) != 0;
  }
}
