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

package it.eng.idra.utils;

import it.eng.idra.beans.dcat.DcatApVersion;
import it.eng.idra.beans.dcat.DcatDataService;
import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.dcat.DcatDistribution;
import it.eng.idra.beans.dcat.DcatProperty;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Detects whether a catalog uses DCAT-AP v2 or v3 by inspecting the parsed datasets
 * for predicates introduced only in v3.
 *
 * <p>Used at the end of each sync to set/refresh {@code OdmsCatalogue.dcatVersion}.
 * The decision is <strong>monotonic toward v3</strong>: once a catalog has been
 * classified as v3 (because a v3-only predicate appeared at least once), it stays v3
 * even if a later sync no longer carries that predicate. This avoids flapping when
 * the source temporarily drops a v3 attribute.
 *
 * <p>v3 markers (any one is sufficient): {@code dcat:hvdCategory},
 * {@code dcatap:applicableLegislation}, {@code dcat:DatasetSeries} / {@code dcat:inSeries},
 * {@code dcat:qualifiedRelation} + {@code dcat:hadRole}, {@code dcat:temporalResolution},
 * {@code dcat:DataService} / {@code dcat:accessService}, {@code dcat:hasCurrentVersion}.
 */
public final class DcatVersionDetector {

  private DcatVersionDetector() {
  }

  /**
   * Detect the DCAT-AP version of a freshly synced catalog.
   *
   * @param datasets       datasets just loaded from the source
   * @param currentVersion previously persisted version (may be {@code null} on first sync)
   * @return {@link DcatApVersion#DCAT_AP_3} if either the current version is already v3
   *         or the dataset list contains at least one v3-only predicate; otherwise
   *         {@link DcatApVersion#DCAT_AP_2}.
   */
  public static DcatApVersion detect(List<DcatDataset> datasets, DcatApVersion currentVersion) {
    if (currentVersion == DcatApVersion.DCAT_AP_3) {
      // Already classified as v3 — keep it (monotonic).
      return DcatApVersion.DCAT_AP_3;
    }
    if (datasets == null || datasets.isEmpty()) {
      // Nothing to inspect — preserve the previous classification (defaults to v2 if null).
      return currentVersion != null ? currentVersion : DcatApVersion.DCAT_AP_2;
    }
    for (DcatDataset dataset : datasets) {
      if (dataset != null && hasV3Markers(dataset)) {
        return DcatApVersion.DCAT_AP_3;
      }
    }
    return DcatApVersion.DCAT_AP_2;
  }

  private static boolean hasV3Markers(DcatDataset dataset) {
    // dcat:hvdCategory
    if (anyNonBlank(dataset.getHVDCategory())) {
      return true;
    }
    // dcatap:applicableLegislation (dataset-level)
    if (anyNonBlank(dataset.getApplicableLegislation())) {
      return true;
    }
    // dcat:inSeries / DatasetSeries
    if (dataset.getInSeries() != null && !dataset.getInSeries().isEmpty()) {
      return true;
    }
    // dcat:qualifiedRelation
    if (dataset.getQualifiedRelation() != null && !dataset.getQualifiedRelation().isEmpty()) {
      return true;
    }
    // dcat:temporalResolution (modelled as a single DcatProperty)
    DcatProperty temporalResolution = dataset.getTemporalResolution();
    if (temporalResolution != null && StringUtils.isNotBlank(temporalResolution.getValue())) {
      return true;
    }
    // prov:wasGeneratedBy (introduced in DCAT-AP v3)
    if (anyNonBlank(dataset.getWasGeneratedBy())) {
      return true;
    }
    // dcat:DataService / dcat:accessService — checked at distribution level only
    // (DcatDataset has no direct collection of services in the current model).
    if (dataset.getDistributions() != null) {
      for (DcatDistribution distribution : dataset.getDistributions()) {
        if (distribution == null) {
          continue;
        }
        if (anyNonBlank(distribution.getApplicableLegislation())) {
          return true;
        }
        List<DcatDataService> services = distribution.getAccessService();
        if (services != null && !services.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean anyNonBlank(List<DcatProperty> properties) {
    if (properties == null || properties.isEmpty()) {
      return false;
    }
    for (DcatProperty p : properties) {
      if (p != null && StringUtils.isNotBlank(p.getValue())) {
        return true;
      }
    }
    return false;
  }
}
