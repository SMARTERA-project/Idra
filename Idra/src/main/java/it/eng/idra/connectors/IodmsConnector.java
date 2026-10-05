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

package it.eng.idra.connectors;

import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.beans.odms.OdmsSynchronizationResult;
import java.util.HashMap;
import java.util.List;

// TODO: Auto-generated Javadoc
/**
 * The Interface IodmsConnector.
 */
public interface IodmsConnector {

  /**
   * Find datasets.
   *
   * @param searchParameters the search parameters
   * @return the list
   * @throws Exception the exception
   */
  public List<DcatDataset> findDatasets(HashMap<String, Object> searchParameters) throws Exception;

  /**
   * Count search datasets.
   *
   * @param searchParameters the search parameters
   * @return the int
   * @throws Exception the exception
   */
  public int countSearchDatasets(HashMap<String, Object> searchParameters) throws Exception;

  /**
   * Count datasets.
   *
   * <p>Returns the real number of datasets ({@code 0} for an empty catalogue) or
   * {@code -1} when it cannot be known without downloading the whole catalogue.
   *
   * @return the int
   * @throws Exception the exception
   */
  public int countDatasets() throws Exception;

  /**
   * Checks whether the catalogue is reachable, without downloading its datasets where the
   * connector allows it. Throws (or returns OFFLINE) when it is not.
   *
   * <p>Default: legacy rule based on {@link #countDatasets()} ({@code 0} means
   * offline, {@code -1} unknown but online). Connectors for which an empty catalogue is
   * legitimate, or whose count is expensive, override it.
   *
   * @return the catalogue state
   * @throws Exception when the catalogue cannot be reached
   */
  default it.eng.idra.beans.odms.OdmsCatalogueState checkState() throws Exception {
    return countDatasets() != 0 ? it.eng.idra.beans.odms.OdmsCatalogueState.ONLINE : it.eng.idra.beans.odms.OdmsCatalogueState.OFFLINE;
  }

  /**
   * Dataset to dcat.
   *
   * @param dataset the dataset
   * @param node    the node
   * @return the dcat dataset
   * @throws Exception the exception
   */
  DcatDataset datasetToDcat(Object dataset, OdmsCatalogue node) throws Exception;

  /**
   * Gets the dataset.
   *
   * @param datasetId the dataset id
   * @return the dataset
   * @throws Exception the exception
   */
  public DcatDataset getDataset(String datasetId) throws Exception;

  /**
   * Gets the all datasets.
   *
   * @return the all datasets
   * @throws Exception the exception
   */
  public List<DcatDataset> getAllDatasets() throws Exception;

  /**
   * Gets the changed datasets.
   *
   * @param oldDatasets  the old datasets
   * @param startingDate the starting date
   * @return the changed datasets
   * @throws Exception the exception
   */
  public OdmsSynchronizationResult getChangedDatasets(List<DcatDataset> oldDatasets,
      String startingDate) throws Exception;
}
