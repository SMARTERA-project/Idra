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

import it.eng.idra.beans.dcat.SkosConcept;
import it.eng.idra.beans.dcat.SkosPrefLabel;
import it.eng.idra.management.FederationCore;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared helper used by all open-data connectors to turn a flat list of
 * "theme entries" (a mix of URIs and free-text labels) into typed SKOS concepts.
 *
 * <p>Until 2026-05, each connector duplicated the same loop and hardcoded the
 * {@code resourceUri} to an empty string — i.e. the authority URI of every theme
 * was thrown away and only the English label was kept. This factory preserves
 * the URI when the entry already is one, while still running the existing label
 * mapping ({@link FederationCore#getEnglishDcatTheme(String)}) on the URI's last
 * segment so DCAT-AP authority codes (ENVI, ECON, TRAN, …) keep their friendly
 * label.
 */
public final class SkosConceptFactory {

  private static final Logger logger = LogManager.getLogger(SkosConceptFactory.class);

  private SkosConceptFactory() {
  }

  /**
   * Build a typed list of SKOS concepts from a list of textual entries.
   *
   * @param <T>         concrete subclass of {@link SkosConcept} (e.g. {@code SkosConceptTheme})
   * @param propertyUri the DCAT property URI the concepts are attached to (e.g. {@code dcat:theme})
   * @param entries     raw concept entries — each may be either a URI or a free-text label
   * @param type        the {@link SkosConcept} subclass to instantiate
   * @param nodeId      the federated ODMS node id
   * @return a list of concept instances, never {@code null}; blank entries are skipped
   */
  public static <T extends SkosConcept> List<T> build(String propertyUri, List<String> entries,
      Class<T> type, String nodeId) {
    List<T> result = new ArrayList<T>();
    if (entries == null || entries.isEmpty()) {
      return result;
    }

    for (String entry : entries) {
      if (StringUtils.isBlank(entry)) {
        continue;
      }
      String trimmed = entry.trim();
      String resourceUri = "";
      String label;
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        resourceUri = trimmed;
        int slash = trimmed.lastIndexOf('/');
        String segment = slash >= 0 && slash + 1 < trimmed.length()
            ? trimmed.substring(slash + 1) : trimmed;
        label = FederationCore.getEnglishDcatTheme(segment);
      } else {
        label = FederationCore.getEnglishDcatTheme(trimmed);
      }
      try {
        result.add(type.getDeclaredConstructor(SkosConcept.class).newInstance(new SkosConcept(
            propertyUri, resourceUri,
            Arrays.asList(new SkosPrefLabel("", label, nodeId)), nodeId)));
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
          | InvocationTargetException | NoSuchMethodException | SecurityException e) {
        logger.error(e.getMessage(), e);
      }
    }
    return result;
  }
}
