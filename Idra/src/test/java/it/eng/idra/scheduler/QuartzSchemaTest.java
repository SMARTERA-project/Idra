package it.eng.idra.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import org.junit.jupiter.api.Test;

// A reused MySQL data directory skips the bootstrap script: Idra must create the Quartz tables itself.
class QuartzSchemaTest {

  private static Connection database(String name) throws Exception {
    return DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
  }

  @Test void createsEveryTableOnAnEmptyDatabase() throws Exception {
    try (Connection c = database("quartzEmpty")) {
      Set<String> created = QuartzSchema.ensure(c, "QRTZ_", "");
      assertEquals(11, created.size());
      assertTrue(created.contains("QRTZ_TRIGGERS"));
      assertTrue(QuartzSchema.ensure(c, "QRTZ_", "").isEmpty(), "a second start creates nothing");
    }
  }

  @Test void keepsExistingTablesAndRows() throws Exception {
    try (Connection c = database("quartzPartial"); Statement s = c.createStatement()) {
      s.executeUpdate("CREATE TABLE qrtz_locks (SCHED_NAME VARCHAR(120) NOT NULL, "
          + "LOCK_NAME VARCHAR(40) NOT NULL, PRIMARY KEY (SCHED_NAME,LOCK_NAME))");
      s.executeUpdate("INSERT INTO qrtz_locks VALUES ('ODFScheduler','TRIGGER_ACCESS')");
      Set<String> created = QuartzSchema.ensure(c, "QRTZ_", "");
      assertEquals(10, created.size());
      assertFalse(created.contains("QRTZ_LOCKS"));
      try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM QRTZ_LOCKS")) {
        r.next();
        assertEquals(1, r.getInt(1));
      }
      s.executeQuery("SELECT * FROM QRTZ_TRIGGERS").close();
    }
  }
}
