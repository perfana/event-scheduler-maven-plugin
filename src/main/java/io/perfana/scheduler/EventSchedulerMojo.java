/*
 * Copyright (C) 2020 Peter Paul Bakker - Perfana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.perfana.scheduler;

import io.perfana.eventscheduler.EventScheduler;
import io.perfana.eventscheduler.EventSchedulerBuilder;
import io.perfana.eventscheduler.api.*;
import io.perfana.eventscheduler.api.config.EventConfig;
import io.perfana.eventscheduler.api.config.EventSchedulerConfig;
import io.perfana.eventscheduler.api.config.TestContext;
import io.perfana.eventscheduler.exception.EventCheckFailureException;
import io.perfana.eventscheduler.exception.handler.AbortSchedulerException;
import io.perfana.eventscheduler.exception.handler.KillSwitchException;
import net.jcip.annotations.GuardedBy;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Fires events according to the schedule.
 * Send start session and stop session and waits for given duration.
 * In case of failure or kill of process, sends abort session.
 */
@Mojo( name = "test", defaultPhase = LifecyclePhase.PROCESS_RESOURCES )
public class EventSchedulerMojo extends AbstractMojo {

    private final Object eventSchedulerLock = new Object();

    // TODO workaround to communicate from event to plugin
    public static volatile boolean START_WAITING = false;

    @GuardedBy("eventSchedulerLock")
    private EventScheduler eventScheduler;

    // volatile because possibly multiple threads are involved
    private volatile SchedulerExceptionType schedulerExceptionType = SchedulerExceptionType.NONE;

    @GuardedBy("eventSchedulerLock")
    @Parameter(required = true)
    EventSchedulerConfig eventSchedulerConfig;

    @Parameter
    private volatile Long slackDurationSeconds = 0L;

    @Override
    public void execute() {
        getLog().info("Execute event-scheduler-maven-plugin");

        synchronized (eventSchedulerLock) {
            if (eventSchedulerConfig != null && !eventSchedulerConfig.isSchedulerEnabled()) {
                getLog().info("EventScheduler is disabled.");
                return;
            }
        }

        boolean abortEventScheduler = false;

        // this class really needs to be on the classpath, otherwise: runtime exception, not found on classpath
        String factoryClassName = "io.perfana.scheduler.SchedulerSpyEventFactory";

        EventSchedulerConfig newConfig;

        synchronized (eventSchedulerLock) {
            List<EventConfig> eventConfigs = new ArrayList<>();
            eventConfigs.addAll(eventSchedulerConfig.getEventConfigs());

            eventConfigs.add(EventConfig.builder().name("schedulerTestStartSpyEvent").eventFactory(factoryClassName).build());

            newConfig = EventSchedulerConfig.builder()
                    .schedulerEnabled(eventSchedulerConfig.isSchedulerEnabled())
                    .debugEnabled(eventSchedulerConfig.isDebugEnabled())
                    .continueOnEventCheckFailure(eventSchedulerConfig.isContinueOnEventCheckFailure())
                    .failOnError(eventSchedulerConfig.isFailOnError())
                    .keepAliveIntervalInSeconds(eventSchedulerConfig.getKeepAliveIntervalInSeconds())
                    .testConfig(eventSchedulerConfig.getTestConfig())
                    .eventConfigs(eventConfigs)
                    .scheduleScript(eventSchedulerConfig.getScheduleScript())
                    .build();

            eventScheduler = createEventScheduler(newConfig, getLog());
        }

        try {

            final SchedulerExceptionHandler schedulerExceptionHandler = new SchedulerExceptionHandler() {
                @Override
                public void kill(String message) {
                    getLog().info("Killing running process, message: " + message);
                    schedulerExceptionType = SchedulerExceptionType.KILL;
                    // the main thread will check for this exception type and kill
                }
                @Override
                public void abort(String message) {
                    getLog().info("Aborting running process, message: " + message);
                    schedulerExceptionType = SchedulerExceptionType.ABORT;
                    // the main thread will check for this exception type and abort
                }
                @Override
                public void stop(String message) {
                    getLog().info("Stop running process, message: " + message);
                    schedulerExceptionType = SchedulerExceptionType.STOP;
                    // the main thread will check for this exception type and stop the test run
                }
            };

            synchronized (eventSchedulerLock) {
                startScheduler(eventScheduler, schedulerExceptionHandler);
            }

            Duration duration;
            synchronized (eventSchedulerLock) {
                TestContext testContext = eventScheduler.getEventSchedulerContext().getTestContext();
                Duration rampupTime = testContext.getRampupTime();
                Duration constantLoad = testContext.getConstantLoadTime();
                duration = rampupTime.plus(constantLoad);
            }

            // will be set when waiting should start (when start test event has happened).
            long stopTimestampMillis = Long.MAX_VALUE;
            final long startTimestampMillis = System.currentTimeMillis();
            boolean stopTimeIsSet = false;

            boolean justKeepLoopin = true;
            while (System.currentTimeMillis() < stopTimestampMillis && justKeepLoopin) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Waiting for scheduler to finish..." + Duration.ofMillis(stopTimestampMillis - System.currentTimeMillis()) + " left.");
                }

                if (START_WAITING && !stopTimeIsSet) {
                    stopTimestampMillis = startTimestampMillis + duration.toMillis() + Duration.ofSeconds(slackDurationSeconds).toMillis();
                    stopTimeIsSet = true;
                    getLog().info("The event-scheduler-maven-plugin will now wait for " + duration + " for scheduler to finish (including " + slackDurationSeconds + " seconds of slack).");
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    getLog().warn("Sleep got interrupted: stop wait loop.");
                    justKeepLoopin = false;
                }
                if (schedulerExceptionType == SchedulerExceptionType.KILL) {
                    throw new KillSwitchException("Rethrow KillSwitchException from wait loop in event-scheduler-maven-plugin.");
                }
                if (schedulerExceptionType == SchedulerExceptionType.ABORT) {
                    throw new AbortSchedulerException("Rethrow AbortSchedulerException from wait loop in event-scheduler-maven-plugin.");
                }
                if (schedulerExceptionType == SchedulerExceptionType.STOP) {
                    getLog().info("Got stop test run request from all ContinueOnKeepAliveParticipants.");
                    synchronized (eventSchedulerLock) {
                        eventScheduler.stopSession();
                    }
                    justKeepLoopin = false;
                }
            }
            String stopMessage = !justKeepLoopin ? "Stop test run request received." : "Regular timeout reached.";
            Duration actualDuration = Duration.ofMillis(System.currentTimeMillis() - startTimestampMillis);
            getLog().info("The event-scheduler-maven-plugin has waited for " + actualDuration + ". " + stopMessage);

        } catch (Exception e) {
            if (e instanceof KillSwitchException) {
                getLog().info("KillSwitchException found, setting abortEventScheduler to true.");
                abortEventScheduler = true;
            } else {
                getLog().warn("Inside catch exception", e);
                if (newConfig.isFailOnError()) {
                    getLog().debug(">>> Fail on error is enabled (true), setting abortEventScheduler to true.");
                    abortEventScheduler = true;
                } else {
                    getLog().warn("There were some errors, but failOnError was set to false: build will not fail.");
                }
            }
        } finally {
            synchronized (eventSchedulerLock) {
                if (eventScheduler != null) {
                    synchronized (eventSchedulerLock) {
                        if (!eventScheduler.isSessionStopped()) {
                            if (abortEventScheduler) {
                                getLog().debug(">>> Abort is called in finally: abortEventScheduler is true");
                                eventScheduler.abortSession();
                            } else {
                                getLog().debug(">>> Stop session (because isSessionStopped() is false and abortEventScheduler is false)");
                                eventScheduler.stopSession();
                            }
                        }
                    }
                }
            }
        }

        synchronized (eventSchedulerLock) {
            if (eventScheduler != null) {
                try {
                    getLog().debug(">>> Call check results");
                    // results are always checked, also in case of abort or killswitch
                    eventScheduler.checkResults();
                } catch (EventCheckFailureException e) {
                    getLog().debug(">>> EventCheckFailureException: " + e.getMessage());
                    if (!newConfig.isContinueOnEventCheckFailure()) {
                        throw  e;
                    }
                    else {
                        getLog().warn("EventCheck failures found, but continue on event check failure is true:" + e.getMessage());
                    }
                }
            }
        }
    }

    private void startScheduler(EventScheduler eventScheduler, SchedulerExceptionHandler schedulerExceptionHandler) {
        synchronized (eventSchedulerLock) {
            eventScheduler.addKillSwitch(schedulerExceptionHandler);
            eventScheduler.startSession();
            addShutdownHookForEventScheduler(eventScheduler);
        }
    }

    private void addShutdownHookForEventScheduler(EventScheduler eventScheduler) {

        final CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runnable shutdowner = () -> {
            try {
                synchronized (eventSchedulerLock) {
                    if (!eventScheduler.isSessionStopped()) {
                        eventScheduler.abortSession();
                    } else {
                        getLog().info("Shutdown Hook: event scheduler session already stopped.");
                    }
                }
            } finally {
                shutdownLatch.countDown();
            }

            // wait until the abort in the shutdown hook is finished
            try {
                getLog().info("Waiting for shutdown hook to finish...");
                shutdownLatch.await();
                getLog().info("Shutdown hook is finished.");
            } catch (InterruptedException e) {
                getLog().warn("Interrupt while waiting for shutdown hook to finish.");
                Thread.currentThread().interrupt();
            }

        };
        Thread eventSchedulerShutdownThread = new Thread(shutdowner, "eventSchedulerShutdownThread");
        Runtime.getRuntime().addShutdownHook(eventSchedulerShutdownThread);
    }

    private static EventScheduler createEventScheduler(EventSchedulerConfig eventSchedulerConfig, Log log) {

        EventLogger logger = new EventLogger() {
            @Override
            public void info(String message) {
                log.info(message);
            }

            @Override
            public void warn(String message) {
                log.warn(message);
            }

            @Override
            public void error(String message) {
                log.error(message);
            }

            @Override
            public void error(String message, Throwable throwable) {
                log.error(message, throwable);
            }

            @Override
            public void debug(final String message) {
                if (isDebugEnabled()) log.debug(message);
            }

            @Override
            public boolean isDebugEnabled() {
                return eventSchedulerConfig.isDebugEnabled();
            }

        };

        return EventSchedulerBuilder.of(eventSchedulerConfig, logger);
    }

}
