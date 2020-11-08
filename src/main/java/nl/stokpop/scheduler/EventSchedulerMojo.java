/**
 * Copyright (C) 2020 Peter Paul Bakker - Stokpop Software Services
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
package nl.stokpop.scheduler;

import nl.stokpop.eventscheduler.EventScheduler;
import nl.stokpop.eventscheduler.EventSchedulerBuilder;
import nl.stokpop.eventscheduler.api.*;
import nl.stokpop.eventscheduler.exception.EventCheckFailureException;
import nl.stokpop.eventscheduler.exception.handler.KillSwitchException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Fires events according to the schedule.
 * Send start session and stop session and waits for given duration.
 * In case of failure or kill of process, sends abort session.
 */
@Mojo(name = "test",
    defaultPhase = LifecyclePhase.INTEGRATION_TEST,
    requiresDependencyResolution = ResolutionScope.TEST)
public class EventSchedulerMojo extends AbstractMojo {

    private final Object eventSchedulerLock = new Object();
    private EventScheduler eventScheduler;

    /**
     * fail in case of errors, default is true
     */
    @Parameter(defaultValue = "true")
    private boolean failOnError;

    /**
     * continue if there are assertion failures, default is true
     */
    @Parameter(defaultValue = "true")
    private boolean continueOnAssertionFailure;
    
    /**
     * enable or disable calls to EventScheduler, default is enabled
     */
    @Parameter(defaultValue = "true")
    private boolean eventSchedulerEnabled;

    /**
     * list of custom event definitions
     */
    @Parameter
    private Map<String, Properties> events;

    /**
     * schedule script with events, one event per line, such as: PT1M|scale-down|replicas=2
     */
    @Parameter
    private String eventScheduleScript;

    /**
     * name of system under test.
     */
    @Parameter(defaultValue = "UNKNOWN_SYSTEM_UNDER_TEST")
    private String eventSystemUnderTest;

    /**
     * work load for this test, for instance load or stress
     */
    @Parameter(defaultValue = "UNKNOWN_WORKLOAD")
    private String eventWorkload;

    /**
     * environment for this test.
     */
    @Parameter(defaultValue = "UNKNOWN_TEST_ENVIRONMENT")
    private String eventTestEnvironment;

    /**
     * name of product that is being tested.
     */
    @Parameter(defaultValue = "ANONYMOUS_PRODUCT")
    private String eventProductName;

    /**
     * name of performance dashboard for this test.
     */
    @Parameter(defaultValue = "ANONYMOUS_DASHBOARD")
    private String eventDashboardName;

    /**
     * test run id.
     */
    @Parameter(defaultValue = "ANONYMOUS_TEST_ID")
    private String eventTestRunId;

    /**
     * build results url is where the build results of this load test can be found.
     */
    @Parameter
    private String eventBuildResultsUrl;

    /**
     * the version number of the system under test.
     */
    @Parameter(defaultValue = "1.0.0-SNAPSHOT")
    private String eventVersion;

    /**
     * test rampup time in seconds.
     */
    @Parameter(defaultValue = "30")
    private String eventRampupTimeInSeconds;

    /**
     * test constant load time in seconds.
     */
    @Parameter(defaultValue = "570")
    private String eventConstantLoadTimeInSeconds;

    /**
     * test run annotations passed via environment variable
     */
    @Parameter
    private String eventAnnotations;

    /**
     * test run variables passed via environment variable
     */
    @Parameter
    private Properties eventVariables;

    /**
     * test run comma separated tags via environment variable
     */
    @Parameter
    private List<String> eventTags;

    /**
     * enable debug logging for events.
     * Note: "maven -X" debug should also be active.
     */
    @Parameter
    private boolean eventDebugEnabled;

    /**
     * how often is keep alive event fired. Default is 30 seconds.
     */
    @Parameter(defaultValue = "30")
    private Integer eventKeepAliveIntervalInSeconds;

    // volatile because possibly multiple threads are involved
    private volatile SchedulerExceptionType schedulerExceptionType = SchedulerExceptionType.NONE;

    @Override
    public void execute() {
        getLog().info("Execute event-scheduler-maven-plugin");

        if (!eventSchedulerEnabled) {
            getLog().info("EventScheduler is disabled.");
            return;
        }

        boolean abortEventScheduler = false;

        eventScheduler = createEventScheduler();

        try {

            final SchedulerExceptionHandler schedulerExceptionHandler = new SchedulerExceptionHandler() {
                @Override
                public void kill(String message) {
                    getLog().info("Killing running process, message: " + message);
                    schedulerExceptionType = SchedulerExceptionType.KILL;
                }
                @Override
                public void abort(String message) {
                    getLog().info("Killing running process, message: " + message);
                    schedulerExceptionType = SchedulerExceptionType.ABORT;
                }
            };

            startScheduler(eventScheduler, schedulerExceptionHandler);

            int rampupInSeconds = Integer.parseInt(eventRampupTimeInSeconds);
            int constantLoadInSeconds = Integer.parseInt(eventConstantLoadTimeInSeconds);
            int durationInSeconds = rampupInSeconds + constantLoadInSeconds;

            Duration duration = Duration.ofSeconds(durationInSeconds);

            getLog().info("event-scheduler-maven-plugin will now wait for " + duration);
            Thread.sleep(duration.toMillis());

            eventScheduler.stopSession();

        } catch (Exception e) {
            getLog().warn("Inside catch exception: " + e);
            // AbortSchedulerException should just fall through and be handled like other exceptions
            // For KillSwitchException, go on with check results and assertions instead
            if (!(e instanceof KillSwitchException)) {
                if (failOnError) {
                    getLog().debug(">>> Fail on error is enabled (true), setting abortEventScheduler to true.");
                    abortEventScheduler = true;
                } else {
                    getLog().warn("There were some errors, but failOnError was set to false: build will not fail.");
                }
            }
            else {
                getLog().warn("KillSwitchException found.");
            }
        } finally {
            if (eventScheduler != null) {
                synchronized (eventSchedulerLock) {
                    if (abortEventScheduler && !eventScheduler.isSessionStopped()) {
                        getLog().debug(">>> Abort is called in finally: abortEventScheduler is true");
                        // implicit stop session
                        eventScheduler.abortSession();
                    } else {
                        getLog().debug(">>> No abort called: " +
                            "abort event scheduler is " + abortEventScheduler + ", stop is already called is " + eventScheduler.isSessionStopped());
                    }
                }
            }
        }

        if ( eventScheduler != null && !eventScheduler.isSessionStopped() ) {
            getLog().debug(">>> Stop session (because not isSessionStopped())");
            eventScheduler.stopSession();
            try {
                getLog().debug(">>> Call check results");
                eventScheduler.checkResults();
            } catch (EventCheckFailureException e) {
                getLog().debug(">>> EventCheckFailureException: " + e.getMessage());
                if (!continueOnAssertionFailure) {
                    throw  e;
                }
                else {
                    getLog().warn("EventCheck failures found, but continue on assert failure is enabled:" + e.getMessage());
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
            }

        };
        Thread eventSchedulerShutdownThread = new Thread(shutdowner, "eventSchedulerShutdownThread");
        Runtime.getRuntime().addShutdownHook(eventSchedulerShutdownThread);
    }

    private EventScheduler createEventScheduler() {

        EventLogger logger = new EventLogger() {
            @Override
            public void info(String message) {
                getLog().info(message);
            }

            @Override
            public void warn(String message) {
                getLog().warn(message);
            }

            @Override
            public void error(String message) {
                getLog().error(message);
            }

            @Override
            public void error(String message, Throwable throwable) {
                getLog().error(message, throwable);
            }

            @Override
            public void debug(final String message) {
                if (isDebugEnabled()) getLog().debug(message);
            }

            @Override
            public boolean isDebugEnabled() {
                return eventDebugEnabled;
            }

        };

        // there might be null values for empty <tag></tag>
        List<String> filteredEventTags = eventTags.stream().filter(Objects::nonNull).collect(Collectors.toList());

        TestContext testContext = new TestContextBuilder()
            .setTestRunId(eventTestRunId)
            .setSystemUnderTest(eventSystemUnderTest)
            .setVersion(eventVersion)
            .setWorkload(eventWorkload)
            .setTestEnvironment(eventTestEnvironment)
            .setCIBuildResultsUrl(eventBuildResultsUrl)
            .setRampupTimeInSeconds(eventRampupTimeInSeconds)
            .setConstantLoadTimeInSeconds(eventConstantLoadTimeInSeconds)
            .setAnnotations(eventAnnotations)
            .setTags(filteredEventTags)
            .setVariables(eventVariables)
            .build();

        EventSchedulerSettings settings = new EventSchedulerSettingsBuilder()
            .setKeepAliveInterval(Duration.ofSeconds(eventKeepAliveIntervalInSeconds))
            .build();

        EventSchedulerBuilder eventSchedulerBuilder = new EventSchedulerBuilder()
            .setEventSchedulerSettings(settings)
            .setTestContext(testContext)
            .setAssertResultsEnabled(eventSchedulerEnabled)
            .setCustomEvents(eventScheduleScript)
            .setLogger(logger);

        if (events != null) {
            events.forEach(eventSchedulerBuilder::addEvent);
        }

        return eventSchedulerBuilder.build();
    }
}
