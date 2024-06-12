/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gobblin.service.modules.orchestration;

import java.util.Properties;

import org.junit.Assert;
import org.quartz.JobDataMap;
import org.testng.annotations.Test;

import org.apache.gobblin.configuration.ConfigurationKeys;
import org.apache.gobblin.service.modules.scheduler.GobblinServiceJobScheduler;


public class FlowLaunchHandlerTest {
  long flowExecutionId = 123000L;
  long eventToRevisit = 641000L;
  long minimumLingerDurationMillis = 2000L;
  String cronExpression = FlowLaunchHandler.createCronFromDelayPeriod(minimumLingerDurationMillis);
  String cronExpressionSuffix = truncateFirstTwoFieldsOfCronExpression(cronExpression);
  int schedulerBackOffMillis = 10;
  DagActionStore.DagAction dagAction = new DagActionStore.DagAction("flowName", "flowGroup",
      flowExecutionId, "jobName", DagActionStore.DagActionType.LAUNCH);
  LeaseAttemptStatus.LeasedToAnotherStatus leasedToAnotherStatus =
      new LeaseAttemptStatus.LeasedToAnotherStatus(dagAction, eventToRevisit, minimumLingerDurationMillis);

  /**
   * Remove first two fields from cron expression representing seconds and minutes to return truncated cron expression
   * that can be used to compare to the expression generated by the original cronExpression plus a small random number
   * in milliseconds
   * @param cronExpression
   * @return
   */
  private static String truncateFirstTwoFieldsOfCronExpression(String cronExpression) {
    String cronWithoutSeconds = cronExpression.substring(cronExpression.indexOf(" ") + 1);
    String cronWithoutMinutes = cronWithoutSeconds.substring(cronWithoutSeconds.indexOf(" ") + 1);
    return cronWithoutMinutes;
  }

  /**
   * Provides an input with all three values (cronExpression, reminderTimestamp, originalEventTime) set in the map
   * Properties and checks that they are updated properly
   */
  @Test
  public void testUpdatePropsInJobDataMap() {
    JobDataMap oldJobDataMap = new JobDataMap();
    Properties originalProperties = new Properties();
    originalProperties.setProperty(ConfigurationKeys.JOB_SCHEDULE_KEY, "0 0 0 ? * * 2050");
    originalProperties.setProperty(ConfigurationKeys.SCHEDULER_EXPECTED_REMINDER_TIME_MILLIS_KEY, "0");
    originalProperties.setProperty(ConfigurationKeys.SCHEDULER_PRESERVED_CONSENSUS_EVENT_TIME_MILLIS_KEY, "1");
    oldJobDataMap.put(GobblinServiceJobScheduler.PROPERTIES_KEY, originalProperties);

    JobDataMap newJobDataMap = FlowLaunchHandler.updatePropsInJobDataMap(oldJobDataMap, leasedToAnotherStatus,
        schedulerBackOffMillis);
    Properties newProperties = (Properties) newJobDataMap.get(GobblinServiceJobScheduler.PROPERTIES_KEY);
    Assert.assertTrue(newProperties.getProperty(ConfigurationKeys.JOB_SCHEDULE_KEY).endsWith(cronExpressionSuffix));
    Assert.assertNotEquals("0",
        newProperties.getProperty(ConfigurationKeys.SCHEDULER_EXPECTED_REMINDER_TIME_MILLIS_KEY));
    Assert.assertEquals(String.valueOf(leasedToAnotherStatus.getEventTimeMillis()),
        newProperties.getProperty(ConfigurationKeys.SCHEDULER_PRESERVED_CONSENSUS_EVENT_TIME_MILLIS_KEY));
    Assert.assertTrue(Boolean.parseBoolean(newProperties.getProperty(ConfigurationKeys.FLOW_IS_REMINDER_EVENT_KEY)));
  }

  /**
   * Provides input with an empty Properties object and checks that the three values in question are set.
   */
  @Test
  public void testSetPropsInJobDataMap() {
    JobDataMap oldJobDataMap = new JobDataMap();
    Properties originalProperties = new Properties();
    oldJobDataMap.put(GobblinServiceJobScheduler.PROPERTIES_KEY, originalProperties);

    JobDataMap newJobDataMap = FlowLaunchHandler.updatePropsInJobDataMap(oldJobDataMap, leasedToAnotherStatus,
        schedulerBackOffMillis);
    Properties newProperties = (Properties) newJobDataMap.get(GobblinServiceJobScheduler.PROPERTIES_KEY);
    Assert.assertTrue(newProperties.getProperty(ConfigurationKeys.JOB_SCHEDULE_KEY).endsWith(cronExpressionSuffix));
    Assert.assertTrue(newProperties.containsKey(ConfigurationKeys.SCHEDULER_EXPECTED_REMINDER_TIME_MILLIS_KEY));
    Assert.assertEquals(String.valueOf(leasedToAnotherStatus.getEventTimeMillis()),
        newProperties.getProperty(ConfigurationKeys.SCHEDULER_PRESERVED_CONSENSUS_EVENT_TIME_MILLIS_KEY));
    Assert.assertTrue(Boolean.parseBoolean(newProperties.getProperty(ConfigurationKeys.FLOW_IS_REMINDER_EVENT_KEY)));
  }

  /**
   * Tests `createSuffixForJobTrigger` helper function to ensure the suffix is constructed as we expect
   */
  @Test
  public void testCreateSuffixForJobTrigger() {
    String suffix = FlowLaunchHandler.createSuffixForJobTrigger(leasedToAnotherStatus);
    Assert.assertTrue(suffix.equals("reminder_for_" + eventToRevisit));
  }
}
