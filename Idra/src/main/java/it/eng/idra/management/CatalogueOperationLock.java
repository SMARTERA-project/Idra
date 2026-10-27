package it.eng.idra.management;

import java.util.concurrent.locks.ReentrantLock;

/** Coordinates a catalogue's scheduler work and administrative lifecycle in this JVM. */
public final class CatalogueOperationLock implements AutoCloseable {
  // Bounded stripes keep arbitrary missing catalogue IDs from growing a lock registry.
  private static final ReentrantLock[] LOCKS = new ReentrantLock[1024];
  static {
    for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock(true);
  }
  private final ReentrantLock lock;
  private CatalogueOperationLock(ReentrantLock lock) { this.lock = lock; }

  public static CatalogueOperationLock acquire(int catalogueId) throws InterruptedException {
    ReentrantLock lock = LOCKS[Math.floorMod(catalogueId, LOCKS.length)];
    lock.lockInterruptibly();
    return new CatalogueOperationLock(lock);
  }

  @Override public void close() { lock.unlock(); }
}
