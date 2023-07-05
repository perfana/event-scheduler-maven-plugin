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
import io.perfana.eventscheduler.api.EventLogger;
import io.perfana.eventscheduler.api.SchedulerExceptionHandler;
import io.perfana.eventscheduler.api.SchedulerExceptionType;
import io.perfana.eventscheduler.api.config.EventSchedulerConfig;
import io.perfana.eventscheduler.api.config.TestContext;
import io.perfana.eventscheduler.exception.EventCheckFailureException;
import io.perfana.eventscheduler.exception.handler.AbortSchedulerException;
import io.perfana.eventscheduler.exception.handler.KillSwitchException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.time.Duration;

/**
 * Fires events according to the schedule.
 * Send start session and stop session and waits for given duration.
 * In case of failure or kill of process, sends abort session.
 */
@Mojo( name = "test", defaultPhase = LifecyclePhase.PROCESS_RESOURCES )
public class EventSchedulerMojo extends AbstractMojo {

    private final Object eventSchedulerLock = new Object();

    private EventScheduler eventScheduler;

    // volatile because possibly multiple threads are involved
    private volatile SchedulerExceptionType schedulerExceptionType = SchedulerExceptionType.NONE;

    @Parameter(required = true)
    EventSchedulerConfig eventSchedulerConfig;

    @Override
    public void execute() {
        getLog().info("Execute event-scheduler-maven-plugin");

        if (eventSchedulerConfig != null && !eventSchedulerConfig.isSchedulerEnabled()) {
            getLog().info("EventScheduler is disabled.");
            return;
        }

        boolean abortEventScheduler = false;

        eventScheduler = createEventScheduler(eventSchedulerConfig, getLog());

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

            startScheduler(eventScheduler, schedulerExceptionHandler);

            TestContext testContext = eventScheduler.getEventSchedulerContext().getTestContext();
            Duration rampupTime = testContext.getRampupTime();
            Duration constantLoad = testContext.getConstantLoadTime();
            Duration duration = rampupTime.plus(constantLoad);

            long stopTimestamp = System.currentTimeMillis() + duration.toMillis();
            getLog().info("The event-scheduler-maven-plugin will now wait for " + duration + " for scheduler to finish.");

            boolean justKeepLoopin = true;
            while (System.currentTimeMillis() < stopTimestamp && justKeepLoopin) {
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
                    eventScheduler.stopSession();
                    justKeepLoopin = false;
                }
            }
            getLog().info("The event-scheduler-maven-plugin has waited for " + duration + ". Scheduler will be stopped.");

        } catch (Exception e) {
            if (e instanceof KillSwitchException) {
                getLog().info("KillSwitchException found, setting abortEventScheduler to true.");
                abortEventScheduler = true;
            } else {
                getLog().warn("Inside catch exception", e);
                if (eventSchedulerConfig.isFailOnError()) {
                    getLog().debug(">>> Fail on error is enabled (true), setting abortEventScheduler to true.");
                    abortEventScheduler = true;
                } else {
                    getLog().warn("There were some errors, but failOnError was set to false: build will not fail.");
                }
            }
        } finally {
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

        if (eventScheduler != null) {
            try {
                getLog().debug(">>> Call check results");
                // results are always checked, also in case of abort or killswitch
                eventScheduler.checkResults();
            } catch (EventCheckFailureException e) {
                getLog().debug(">>> EventCheckFailureException: " + e.getMessage());
                if (!eventSchedulerConfig.isContinueOnEventCheckFailure()) {
                    throw  e;
                }
                else {
                    getLog().warn("EventCheck failures found, but continue on event check failure is true:" + e.getMessage());
                }
            }
        }
    }

    private void startScheduler(EventScheduler eventScheduler, SchedulerExceptionHandler schedulerExceptionHandler) {
        eventScheduler.addKillSwitch(schedulerExceptionHandler);
        eventScheduler.startSession();
        addShutdownHookForEventScheduler(eventScheduler);
    }

    private void addShutdownHookForEventScheduler(EventScheduler eventScheduler) {
        final Thread main = Thread.currentThread();
        Runnable shutdowner = () -> {
            synchronized (eventScheduler) {
                if (!eventScheduler.isSessionStopped()) {
                    getLog().info("Shutdown Hook: abort event scheduler session!");
                    // implicit stop session
                    eventScheduler.abortSession();
                }
            }

            // try to hold on to main thread to let the abort event tasks finish properly
            try {
                main.join(4000);
            } catch (InterruptedException e) {
                getLog().warn("Interrupt while waiting for abort to finish.");
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
