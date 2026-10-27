package it.eng.idra.cache;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A bounded retry of one DB-only unit, always with a new persistence context. */
public final class DatabaseTransaction {
  private static final Logger logger = LogManager.getLogger(DatabaseTransaction.class);
  private DatabaseTransaction() { }

  public static <T> T execute(EntityManagerFactory factory, Function<EntityManager, T> work) {
    int attempts = Integer.getInteger("idra.mysql.deadlock.maxAttempts", 3);
    if (attempts < 1 || attempts > 10) {
      throw new IllegalArgumentException("idra.mysql.deadlock.maxAttempts must be between 1 and 10");
    }
    for (int attempt = 1; ; attempt++) {
      EntityManager em = factory.createEntityManager();
      EntityTransaction transaction = em.getTransaction();
      RuntimeException failure = null;
      try {
        transaction.begin();
        T result = work.apply(em);
        transaction.commit();
        return result;
      } catch (RuntimeException e) {
        failure = e;
        try { if (transaction.isActive()) transaction.rollback(); }
        catch (RuntimeException rollbackFailure) { e.addSuppressed(rollbackFailure); }
      } finally {
        try { em.close(); }
        catch (RuntimeException closeFailure) {
          if (failure == null) throw closeFailure;
          failure.addSuppressed(closeFailure);
        }
      }
      if (!isMysqlDeadlock(failure) || attempt >= attempts) throw failure;
      logger.warn("MySQL deadlock: retry DB transaction, attempt {}/{}", attempt + 1, attempts);
      // Cascade delete/reinsert can hold gap locks across several child tables for hundreds
      // of milliseconds. Give the winning transaction time to finish before reopening.
      long delay = 500L * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(500);
      try { Thread.sleep(delay); }
      catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        failure.addSuppressed(interrupted);
        throw failure;
      }
    }
  }

  public static boolean isMysqlDeadlock(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    return isMysqlDeadlock(failure, seen);
  }

  private static boolean isMysqlDeadlock(Throwable failure, Set<Throwable> seen) {
    if (failure == null || !seen.add(failure)) return false;
    if (failure instanceof SQLException) {
      SQLException sql = (SQLException) failure;
      // MySQL 1213 was reproduced with Connector/J 9.2.0; 1205 is deliberately excluded.
      if (sql.getErrorCode() == 1213 && "40001".equals(sql.getSQLState())) return true;
      if (isMysqlDeadlock(sql.getNextException(), seen)) return true;
    }
    return isMysqlDeadlock(failure.getCause(), seen);
  }
}
