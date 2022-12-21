/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.event.handler.persistence;

import static org.opencastproject.db.Queries.namedQuery;

import org.opencastproject.db.DBSession;
import org.opencastproject.db.DBSessionFactory;
import org.opencastproject.security.api.SecurityService;

import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;

/**
 * Implements {@link UpdateHandlerDatabase}.
 */
@Component(
        immediate = true,
        service = UpdateHandlerDatabase.class,
        property = {
                "service.description=Update Handler Persistence"
        }
)
public class UpdateHandlerDatabaseImpl implements UpdateHandlerDatabase {

  /** JPA persistence unit name */
  public static final String PERSISTENCE_UNIT = "org.opencastproject.event.handler.persistence";

  /** Logging utilities */
  private static final Logger logger = LoggerFactory.getLogger(UpdateHandlerDatabaseImpl.class);

  /** Factory used to create {@link EntityManager}s for transactions */
  protected EntityManagerFactory emf;

  protected DBSessionFactory dbSessionFactory;

  protected DBSession db;

  /** The security service */
  protected SecurityService securityService;

  /** OSGi DI */
  @Reference(target = "(osgi.unit.name=org.opencastproject.event.handler.persistence)")
  public void setEntityManagerFactory(EntityManagerFactory emf) {
    this.emf = emf;
  }

  @Reference
  public void setDBSessionFactory(DBSessionFactory dbSessionFactory) {
    this.dbSessionFactory = dbSessionFactory;
  }

  /**
   * Creates {@link EntityManagerFactory} using persistence provider and properties passed via OSGi.
   *
   * @param cc
   * @throws UpdateHandlerDatabaseException
   */
  @Activate
  public void activate(ComponentContext cc) throws UpdateHandlerDatabaseException {
    logger.info("Activating persistence manager for event handler service");
    db = dbSessionFactory.createSession(emf);
    this.populateSeriesData();
  }

  private void populateSeriesData() throws UpdateHandlerDatabaseException {
    try {
      db.execTxChecked(em -> {
        UpdateHandlerConcurrencyEntity entity = new UpdateHandlerConcurrencyEntity();
        entity.setId("ConductingSeries");
        entity.setLocked(false);
        em.merge(entity);

      });
    } catch (Exception e) {
      logger.error("Could not init entity: {}", e.getMessage());
      throw new UpdateHandlerDatabaseException(e);
    }
  }

  /**
   * OSGi callback to set the security service.
   *
   * @param securityService
   *          the securityService to set
   */
  @Reference
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  @Override
  public boolean getSeriesUpdateHandlerLocked() throws UpdateHandlerDatabaseException {
    try {
      return db.exec(namedQuery.find(
              "updateHandlerConcurrency.getConductingSeries",
              UpdateHandlerConcurrencyEntity.class,
              Pair.of("seriesId", "ConductingSeries")
      )).getLocked();
    } catch (Exception e) {
      logger.error("Could not find number of mediapackages", e);
      throw new UpdateHandlerDatabaseException(e);
    }
  }

  @Override
  public void setSeriesUpdateHandlerLocked(boolean locked) throws UpdateHandlerDatabaseException {
    try {
      db.execTxChecked(em -> {
        UpdateHandlerConcurrencyEntity entity = new UpdateHandlerConcurrencyEntity();
        entity.setId("ConductingSeries");
        entity.setLocked(locked);
        em.merge(entity);

      });
    } catch (Exception e) {
      logger.error("Could not init entity: {}", e.getMessage());
      throw new UpdateHandlerDatabaseException(e);
    }
  }
}
