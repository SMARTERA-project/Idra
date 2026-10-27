package it.eng.idra.cache;

import it.eng.idra.scheduler.job.OdmsSynchJob;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import static org.junit.jupiter.api.Assertions.*;

class QuartzCatalogueConcurrencyTest {
  static CountDownLatch firstStarted, otherStarted, releaseFirst, completed;
  static AtomicInteger activeSame, maximumSame;

  @DisallowConcurrentExecution
  public static class FixtureJob implements Job {
    public void execute(JobExecutionContext context) throws JobExecutionException {
      boolean same = context.getJobDetail().getKey().getName().equals("118");
      try {
        if (same) {
          int active=activeSame.incrementAndGet(); maximumSame.accumulateAndGet(active,Math::max);
          firstStarted.countDown();
          if (!releaseFirst.await(10,TimeUnit.SECONDS)) throw new JobExecutionException("Timeout");
        } else otherStarted.countDown();
      } catch (InterruptedException e) { Thread.currentThread().interrupt();throw new JobExecutionException(e); }
      finally {if(same)activeSame.decrementAndGet();completed.countDown();}
    }
  }

  @Test void sameCatalogueTriggersSerializeWhileAnotherCatalogueRuns() throws Exception {
    assertTrue(OdmsSynchJob.class.isAnnotationPresent(DisallowConcurrentExecution.class));
    firstStarted=new CountDownLatch(1);otherStarted=new CountDownLatch(1);
    releaseFirst=new CountDownLatch(1);completed=new CountDownLatch(3);
    activeSame=new AtomicInteger();maximumSame=new AtomicInteger();
    Properties config=new Properties();
    config.setProperty("org.quartz.scheduler.instanceName","plan45-quartz-test");
    config.setProperty("org.quartz.threadPool.threadCount","3");
    config.setProperty("org.quartz.jobStore.class","org.quartz.simpl.RAMJobStore");
    Scheduler scheduler=new StdSchedulerFactory(config).getScheduler();
    try {
      for(String id:new String[]{"118","96"}) scheduler.addJob(JobBuilder.newJob(FixtureJob.class)
          .withIdentity(id,"jobs").storeDurably().build(),false);
      scheduler.start();scheduler.triggerJob(new JobKey("118","jobs"));
      assertTrue(firstStarted.await(5,TimeUnit.SECONDS));
      scheduler.triggerJob(new JobKey("118","jobs"));
      scheduler.triggerJob(new JobKey("96","jobs"));
      assertTrue(otherStarted.await(5,TimeUnit.SECONDS),"Different catalogue runs while 118 is blocked");
      releaseFirst.countDown();assertTrue(completed.await(10,TimeUnit.SECONDS));
      assertEquals(1,maximumSame.get(),"Both triggers share the existing per-catalogue JobKey");
    } finally {releaseFirst.countDown();scheduler.shutdown(true);}
  }
}
