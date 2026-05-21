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

/**
 * Discriminates DCAT-AP v2 vs v3 catalogs. Orthogonal to {@link DcatApProfile}
 * (DCATAP / DCATAP_IT). Always populated automatically by {@code DcatVersionDetector}
 * by inspecting v3-only predicates in the federated metadata — never selected by the
 * end user when registering a catalog.
 */
public enum DcatApVersion {

  DCAT_AP_2,
  DCAT_AP_3;

}
