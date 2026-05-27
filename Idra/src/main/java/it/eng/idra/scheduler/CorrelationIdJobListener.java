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

package it.eng.idra.scheduler;

import it.eng.idra.exception.CorrelationIdFilter;
import java.util.UUID;
import org.apache.logging.log4j.ThreadContext;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.MDC;

/**
 * Puts a fresh correlationId into SLF4J MDC for the duration of each Quartz job
 * execution, so log lines emitted by the connector layer carry the same id as
 * the rest of the request/sync pipeline. Mirrors {@link CorrelationIdFilter} for
 * the HTTP path.
 */
public class CorrelationIdJobListener implements JobListener {

  public static final String NAME = "CorrelationIdJobListener";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    String id = "job-" + UUID.randomUUID();
    context.put(CorrelationIdFilter.MDC_KEY, id);
    // Write to both: SLF4J MDC (for code that reads MDC) and log4j2 ThreadContext
    // (which is what the %X{} pattern actually reads from in this build — the
    // slf4j-api 1.7 / log4j-slf4j-impl 2.17 bridge isn't propagating in practice).
    MDC.put(CorrelationIdFilter.MDC_KEY, id);
    ThreadContext.put(CorrelationIdFilter.MDC_KEY, id);
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    // No execution -> nothing to clean.
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    MDC.remove(CorrelationIdFilter.MDC_KEY);
    ThreadContext.remove(CorrelationIdFilter.MDC_KEY);
  }
}
