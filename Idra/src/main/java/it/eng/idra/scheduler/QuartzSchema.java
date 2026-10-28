package it.eng.idra.scheduler;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates the Quartz job store tables that are missing, before the scheduler starts.
 *
 * <p>The tables used to come only from the MySQL bootstrap script, which the MySQL image runs only
 * on an empty data directory: with a reused or half-initialised data directory Hibernate still
 * created the Idra tables, but the scheduler failed with "Table 'qrtz_triggers' doesn't exist".
 * Existing tables and their rows are never touched.
 */
public final class QuartzSchema {

  private static final Logger logger = LogManager.getLogger(QuartzSchema.class);

  private static final String KEYS = "SCHED_NAME VARCHAR(120) NOT NULL, "
      + "TRIGGER_NAME VARCHAR(200) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, ";
  private static final String TRIGGER_PK = "PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP), "
      + "FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) "
      + "REFERENCES %1$sTRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)";

  /** Standard Quartz 2.3 MySQL InnoDB tables (without prefix), in foreign key order. */
  private static final Map<String, String> TABLES = new LinkedHashMap<>();

  static {
    TABLES.put("JOB_DETAILS", "SCHED_NAME VARCHAR(120) NOT NULL, JOB_NAME VARCHAR(200) NOT NULL, "
        + "JOB_GROUP VARCHAR(200) NOT NULL, DESCRIPTION VARCHAR(250) NULL, "
        + "JOB_CLASS_NAME VARCHAR(250) NOT NULL, IS_DURABLE VARCHAR(1) NOT NULL, "
        + "IS_NONCONCURRENT VARCHAR(1) NOT NULL, IS_UPDATE_DATA VARCHAR(1) NOT NULL, "
        + "REQUESTS_RECOVERY VARCHAR(1) NOT NULL, JOB_DATA BLOB NULL, "
        + "PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)");
    TABLES.put("TRIGGERS", KEYS + "JOB_NAME VARCHAR(200) NOT NULL, JOB_GROUP VARCHAR(200) NOT NULL, "
        + "DESCRIPTION VARCHAR(250) NULL, NEXT_FIRE_TIME BIGINT NULL, PREV_FIRE_TIME BIGINT NULL, "
        + "PRIORITY INTEGER NULL, TRIGGER_STATE VARCHAR(16) NOT NULL, "
        + "TRIGGER_TYPE VARCHAR(8) NOT NULL, START_TIME BIGINT NOT NULL, END_TIME BIGINT NULL, "
        + "CALENDAR_NAME VARCHAR(200) NULL, MISFIRE_INSTR SMALLINT NULL, JOB_DATA BLOB NULL, "
        + "PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP), "
        + "FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP) "
        + "REFERENCES %1$sJOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP)");
    TABLES.put("SIMPLE_TRIGGERS", KEYS + "REPEAT_COUNT BIGINT NOT NULL, "
        + "REPEAT_INTERVAL BIGINT NOT NULL, TIMES_TRIGGERED BIGINT NOT NULL, " + TRIGGER_PK);
    TABLES.put("CRON_TRIGGERS", KEYS + "CRON_EXPRESSION VARCHAR(200) NOT NULL, "
        + "TIME_ZONE_ID VARCHAR(80), " + TRIGGER_PK);
    TABLES.put("SIMPROP_TRIGGERS", KEYS + "STR_PROP_1 VARCHAR(512) NULL, "
        + "STR_PROP_2 VARCHAR(512) NULL, STR_PROP_3 VARCHAR(512) NULL, INT_PROP_1 INT NULL, "
        + "INT_PROP_2 INT NULL, LONG_PROP_1 BIGINT NULL, LONG_PROP_2 BIGINT NULL, "
        + "DEC_PROP_1 NUMERIC(13,4) NULL, DEC_PROP_2 NUMERIC(13,4) NULL, "
        + "BOOL_PROP_1 VARCHAR(1) NULL, BOOL_PROP_2 VARCHAR(1) NULL, " + TRIGGER_PK);
    TABLES.put("BLOB_TRIGGERS", KEYS + "BLOB_DATA BLOB NULL, " + TRIGGER_PK);
    TABLES.put("CALENDARS", "SCHED_NAME VARCHAR(120) NOT NULL, CALENDAR_NAME VARCHAR(200) NOT NULL, "
        + "CALENDAR BLOB NOT NULL, PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)");
    TABLES.put("PAUSED_TRIGGER_GRPS", "SCHED_NAME VARCHAR(120) NOT NULL, "
        + "TRIGGER_GROUP VARCHAR(200) NOT NULL, PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)");
    TABLES.put("FIRED_TRIGGERS", "SCHED_NAME VARCHAR(120) NOT NULL, ENTRY_ID VARCHAR(95) NOT NULL, "
        + "TRIGGER_NAME VARCHAR(200) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, "
        + "INSTANCE_NAME VARCHAR(200) NOT NULL, FIRED_TIME BIGINT NOT NULL, "
        + "SCHED_TIME BIGINT NOT NULL, PRIORITY INTEGER NOT NULL, STATE VARCHAR(16) NOT NULL, "
        + "JOB_NAME VARCHAR(200) NULL, JOB_GROUP VARCHAR(200) NULL, "
        + "IS_NONCONCURRENT VARCHAR(1) NULL, REQUESTS_RECOVERY VARCHAR(1) NULL, "
        + "PRIMARY KEY (SCHED_NAME,ENTRY_ID)");
    TABLES.put("SCHEDULER_STATE", "SCHED_NAME VARCHAR(120) NOT NULL, "
        + "INSTANCE_NAME VARCHAR(200) NOT NULL, LAST_CHECKIN_TIME BIGINT NOT NULL, "
        + "CHECKIN_INTERVAL BIGINT NOT NULL, PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)");
    TABLES.put("LOCKS", "SCHED_NAME VARCHAR(120) NOT NULL, LOCK_NAME VARCHAR(40) NOT NULL, "
        + "PRIMARY KEY (SCHED_NAME,LOCK_NAME)");
  }

  private QuartzSchema() {
  }

  /**
   * Creates the missing job store tables described by the Quartz properties (JDBC job store with
   * the myDS data source). Other configurations are left to Quartz.
   *
   * @return the names of the tables created
   */
  public static Set<String> ensure(Properties quartz) throws SQLException {
    String url = quartz.getProperty("org.quartz.dataSource.myDS.URL");
    if (url == null || !url.startsWith("jdbc:mysql:")) {
      return new TreeSet<>();
    }
    String prefix = quartz.getProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
    // Inside Tomcat DriverManager does not see the web application's driver: load it directly.
    Driver driver;
    try {
      driver = (Driver) Class.forName(quartz.getProperty("org.quartz.dataSource.myDS.driver",
          "com.mysql.cj.jdbc.Driver"), true, QuartzSchema.class.getClassLoader())
          .getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new SQLException("JDBC driver not available: " + e.getMessage(), e);
    }
    Properties credentials = new Properties();
    credentials.setProperty("user", quartz.getProperty("org.quartz.dataSource.myDS.user", ""));
    credentials.setProperty("password",
        quartz.getProperty("org.quartz.dataSource.myDS.password", ""));
    try (Connection connection = driver.connect(url, credentials)) {
      return ensure(connection, prefix, " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }
  }

  /** Creates the missing tables on the given connection; returns the names created. */
  static Set<String> ensure(Connection connection, String prefix, String tableOptions)
      throws SQLException {
    Set<String> existing = existingTables(connection);
    Set<String> created = new TreeSet<>();
    try (Statement statement = connection.createStatement()) {
      for (Map.Entry<String, String> table : TABLES.entrySet()) {
        String name = prefix + table.getKey();
        if (existing.contains(name.toUpperCase(Locale.ROOT))) {
          continue;
        }
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + name + " ("
            + String.format(table.getValue(), prefix) + ")" + tableOptions);
        created.add(name);
      }
    }
    if (!created.isEmpty()) {
      logger.warn("Quartz job store tables were missing and have been created: " + created);
    }
    return created;
  }

  private static Set<String> existingTables(Connection connection) throws SQLException {
    Set<String> names = new TreeSet<>();
    DatabaseMetaData metadata = connection.getMetaData();
    try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%",
        new String[] {"TABLE"})) {
      while (tables.next()) {
        names.add(tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
      }
    }
    return names;
  }
}
