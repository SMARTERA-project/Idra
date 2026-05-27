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

package it.eng.idra.api;

import it.eng.idra.authentication.AuthenticationManager;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * The Class ApplicationConfig.
 */
@ApplicationPath("/api/v1")
public class ApplicationConfig extends Application {

  private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

  /*
   * (non-Javadoc)
   * 
   * @see javax.ws.rs.core.Application#getClasses()
   */
  @Override
  public Set<Class<?>> getClasses() {

    Set<Class<?>> resources = new HashSet<Class<?>>();
    resources.add(it.eng.idra.api.ClientApi.class);
    resources.add(it.eng.idra.api.StatisticsApi.class);
    resources.add(it.eng.idra.api.AdministrationApi.class);
    resources.add(it.eng.idra.api.FederationApiMockup.class);
    resources.add(org.glassfish.jersey.media.multipart.MultiPartFeature.class);
    resources.add(it.eng.idra.api.CorsResponseFilter.class);
    resources.add(it.eng.idra.api.CorsPreflightFilter.class);
    resources.add(it.eng.idra.authentication.filters.PermissionFilter.class);
    resources.add(it.eng.idra.exception.CorrelationIdFilter.class);
    resources.add(it.eng.idra.exception.GlobalExceptionMapper.class);
    try {
      resources.add(AuthenticationManager.getActiveAuthenticationManager().getFilterClass());
    } catch (ClassNotFoundException e) {
      logger.error("Authentication filter class not found, auth disabled", e);
    }

    return resources;
  }

}
