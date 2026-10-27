/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2021 Engineering Ingegneria Informatica S.p.A.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/

package it.eng.idra.beans.dcat;

// TODO: Auto-generated Javadoc
/**
 * The Enum DctFrequency.
 */
public enum DctFrequency {

  /** The annual. */
  ANNUAL,
  /** The annual 2. */
  ANNUAL_2,
  /** The annual 3. */
  ANNUAL_3,
  /** The biennial. */
  BIENNIAL,

  /** The bimonthly. */
  BIMONTHLY,
  /** The biweekly. */
  BIWEEKLY,
  /** The cont. */
  CONT,
  /** The daily. */
  DAILY,
  /** The daily 2. */
  DAILY_2,

  /** The irreg. */
  IRREG,
  /** The monthly. */
  MONTHLY,
  /** The monthly 2. */
  MONTHLY_2,
  /** The monthly 3. */
  MONTHLY_3,

  /** The never. */
  NEVER,
  /** The op datpro. */
  OP_DATPRO,
  /** The quarterly. */
  QUARTERLY,
  /** The triennial. */
  TRIENNIAL,

  /** The unknown. */
  UNKNOWN,
  /** The update cont. */
  UPDATE_CONT,
  /** The weekly 2. */
  WEEKLY_2,

  /** The weekly 3. */
  WEEKLY_3,
  /** The decennial. */
  DECENNIAL,
  /** The hourly. */
  HOURLY,
  /** The quadrennial. */
  QUADRENNIAL,

  /** The quinquennial. */
  QUINQUENNIAL,

  // EU frequency codes present in the pinned DCAT-AP 3.0.1 vocabulary and missing above: valid source values
  // (and the GeoDCAT-AP mapping of ISO 19115 codes) were otherwise exported as UNKNOWN.
  WEEKLY,
  WEEKLY_5,
  AS_NEEDED,
  NOT_PLANNED,
  OTHER,
  BIDECENNIAL,
  BIHOURLY,
  SEXENNIAL,
  TRIDECENNIAL,
  TRIHOURLY;

  /** EU codes that are not Java identifiers. */
  private static final java.util.Set<String> NUMERIC_CODES =
      java.util.Set.of("1MIN", "5MIN", "10MIN", "15MIN", "30MIN", "12HRS");

  /** True for a code of the EU frequency vocabulary (local name, without the authority base IRI). */
  public static boolean isCode(String code) {
    if (code == null) return false;
    if (NUMERIC_CODES.contains(code)) return true;
    try {
      valueOf(code);
      return true;
    } catch (IllegalArgumentException unknown) {
      return false;
    }
  }

  /** Frequency names written as words by the sources (OpenDataSoft: French and English). */
  private static final java.util.Map<String, String> LABELS = java.util.Map.ofEntries(
      java.util.Map.entry("annuelle", "ANNUAL"), java.util.Map.entry("annual", "ANNUAL"),
      java.util.Map.entry("mensuelle", "MONTHLY"), java.util.Map.entry("monthly", "MONTHLY"),
      java.util.Map.entry("quotidienne", "DAILY"), java.util.Map.entry("daily", "DAILY"),
      java.util.Map.entry("hebdomadaire", "WEEKLY"), java.util.Map.entry("weekly", "WEEKLY"),
      java.util.Map.entry("trimestrielle", "QUARTERLY"), java.util.Map.entry("quarterly", "QUARTERLY"),
      java.util.Map.entry("semestrielle", "ANNUAL_2"), java.util.Map.entry("irregular", "IRREG"),
      java.util.Map.entry("irrégulière", "IRREG"), java.util.Map.entry("continuous", "UPDATE_CONT"),
      java.util.Map.entry("en continu", "UPDATE_CONT"), java.util.Map.entry("pas de mise à jour", "NEVER"),
      java.util.Map.entry("never", "NEVER"));

  /** EU code of a frequency name with an exact equivalent; null otherwise (the text is kept). */
  public static String fromLabel(String label) {
    return label == null ? null : LABELS.get(label.trim().toLowerCase(java.util.Locale.ROOT));
  }

  /**
   * GeoDCAT-AP mapping of the ISO 19115 MD_MaintenanceFrequencyCode list to the EU frequency vocabulary;
   * null for a code without an equivalent (it is then not exported as a frequency).
   */
  public static String fromIso19115(String isoCode) {
    if (isoCode == null) return null;
    switch (isoCode.trim()) {
      case "continual": return UPDATE_CONT.name();
      case "daily": return DAILY.name();
      case "weekly": return WEEKLY.name();
      case "fortnightly": return BIWEEKLY.name();
      case "monthly": return MONTHLY.name();
      case "quarterly": return QUARTERLY.name();
      case "biannually": return ANNUAL_2.name();
      case "annually": return ANNUAL.name();
      case "asNeeded": return AS_NEEDED.name();
      case "irregular": return IRREG.name();
      case "notPlanned": return NOT_PLANNED.name();
      case "unknown": return UNKNOWN.name();
      default: return null;
    }
  }
}
