# event-scheduler-maven-plugin

Use plain event-scheduler from maven build to generate events during a load test.

 * Fires events according to the schedule.
 * Send start session and stop session and waits for given duration.
 * In case of failure or kill of process, sends abort session.

Run with: `mvn event-scheduler:test`

# Properties

* `debugEnabled` (default: `false`) - Enable debug logging with `true`.
* `schedulerEnabled` (default: `true`) - Disable the scheduler with `false`.
* `failOnError` (default: `true`) - Fail the build on exceptions thrown during waiting for the result.
* `continueOnEventCheckFailure` (default: `false`) - Continue the build then result checks are not successful.
* `scheduleScript` (default: empty) - Schedule events with as described in [custom-events](https://github.com/perfana/event-scheduler#custom-events).

## Example 

Example configuration in maven `pom.xml` with `test-events-hello-world` events:

```xml 
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>io.perfana</groupId>
    <artifactId>events-test</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>

        <encoding>UTF-8</encoding>

        <event-scheduler-maven-plugin.version>1.1.0</event-scheduler-maven-plugin.version>
        <test-events-hello-world.version>1.1.0</test-events-hello-world.version>

        <buildResultsUrl>${BUILD_URL}</buildResultsUrl>

        <!-- Default load settings -->
        <rampupTimeInSeconds>60</rampupTimeInSeconds>
        <constantLoadTimeInSeconds>900</constantLoadTimeInSeconds>

        <systemUnderTest>Afterburner</systemUnderTest>
        <version>1.0</version>
        <testEnvironment>cloud</testEnvironment>

    </properties>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>io.perfana</groupId>
                    <artifactId>event-scheduler-maven-plugin</artifactId>
                    <version>${event-scheduler-maven-plugin.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>

        <plugins>
            <plugin>
                <groupId>io.perfana</groupId>
                <artifactId>event-scheduler-maven-plugin</artifactId>
                <configuration>
                    <eventSchedulerConfig>
                        <debugEnabled>true</debugEnabled>
                        <schedulerEnabled>true</schedulerEnabled>
                        <failOnError>true</failOnError>
                        <continueOnEventCheckFailure>false</continueOnEventCheckFailure>
                        <testConfig>
                            <systemUnderTest>${systemUnderTest}</systemUnderTest>
                            <version>${version}</version>
                            <workload>${workload}</workload>
                            <testEnvironment>${testEnvironment}</testEnvironment>
                            <testRunId>${testRunId}</testRunId>
                            <buildResultsUrl>${buildResultsUrl}</buildResultsUrl>
                            <rampupTimeInSeconds>${rampupTimeInSeconds}</rampupTimeInSeconds>
                            <constantLoadTimeInSeconds>${constantLoadTimeInSeconds}</constantLoadTimeInSeconds>
                            <annotations>${annotations}</annotations>
                            <tags>${tags}</tags>
                        </testConfig>
                        <scheduleScript>
                            ${eventScheduleScript}
                        </scheduleScript>
                        <eventConfigs>
                            <eventConfig implementation="io.perfana.helloworld.event.HelloWorldEventConfig">
                                <name>HelloEvent1</name>
                                <myRestService>https://my-rest-api</myRestService>
                                <myCredentials>${ENV.SECRET}</myCredentials>
                            </eventConfig>
                        </eventConfigs>
                    </eventSchedulerConfig>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>io.perfana</groupId>
                        <artifactId>test-events-hello-world</artifactId>
                        <version>${test-events-hello-world.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>short-test</id>
            <activation> <activeByDefault>true</activeByDefault> </activation>
            <properties>
                <workload>shortTest</workload>
                <rampupTimeInSeconds>20</rampupTimeInSeconds>
                <constantLoadTimeInSeconds>20</constantLoadTimeInSeconds>
                <testRunId>${systemUnderTest}-${version}-${workload}-${testEnvironment}</testRunId>
                <tags>short-test</tags>
                <eventScheduleScript>
                    PT10S|hello-world|name=pp
                    PT20S|hello-world2|duration=2s
                </eventScheduleScript>
            </properties>
        </profile>
    </profiles>
</project>
```

This will output:

```
➜ mvn -f src/test/resources/example-pom.xml event-scheduler:test
[INFO] Scanning for projects...
[INFO]
[INFO] -----------------------< io.perfana:events-test >-----------------------
[INFO] Building events-test 1.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- event-scheduler-maven-plugin:1.1.0-SNAPSHOT:test (default-cli) @ events-test ---
[INFO] Execute event-scheduler-maven-plugin
[HelloWorldEvent] Class loaded
[INFO] [HelloEvent1] [HelloWorldEvent] Default constructor called.
[INFO] [HelloEvent1] [HelloWorldEvent] Number of processors: 8      cores
[INFO] [HelloEvent1] [HelloWorldEvent] Max memory:           4096   MB
[INFO] [HelloEvent1] [HelloWorldEvent] Total memory:         256    MB
[INFO] [HelloEvent1] [HelloWorldEvent] Free memory:          246    MB
[INFO] [HelloEvent1] [HelloWorldEvent] Message: Default Hello Message
[INFO] start test session
[INFO] broadcast before test event
[INFO] [HelloEvent1] [HelloWorldEvent] Hello before test [Afterburner-1.0-shortTest-cloud]
[INFO] [HelloEvent1] [HelloWorldEvent] Sleep for 2 seconds
[INFO] [HelloEvent1] [HelloWorldEvent] Wakeup after 2 seconds
[INFO] calling keep alive every PT30S
[INFO] create new thread: Keep-Alive-Thread
[INFO] === custom events schedule ===
==> ScheduleEvent (hello-world-PT10S)                  [fire-at=PT10S    settings=name=pp                                           ]
==> ScheduleEvent (hello-world2-PT20S)                 [fire-at=PT20S    settings=duration=2s                                       ]
[INFO] [HelloEvent1] [HelloWorldEvent] Hello keep alive for test [Afterburner-1.0-shortTest-cloud]
[INFO] create new thread: Custom-Event-Thread-1
[INFO] create new thread: Custom-Event-Thread-2
[INFO] event-scheduler-maven-plugin will now wait for PT40S for scheduler to finish.
[INFO] broadcast hello-world custom event
[INFO] [HelloEvent1] [HelloWorldEvent] Custom hello world called:name=pp
[INFO] broadcast hello-world2 custom event
[INFO] [HelloEvent1] [HelloWorldEvent] WARNING: ignoring unknown event [hello-world2]
[INFO] [HelloEvent1] [HelloWorldEvent] Hello keep alive for test [Afterburner-1.0-shortTest-cloud]
[INFO] stop test session.
[INFO] shutdown Executor threads
[INFO] broadcast after test event
[INFO] [HelloEvent1] [HelloWorldEvent] Hello after test [Afterburner-1.0-shortTest-cloud]
[INFO] all broadcasts for stop test session are done
[INFO] check results called
[INFO] broadcast check test
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  42.354 s
[INFO] Finished at: 2021-01-08T11:34:05+01:00
[INFO] ------------------------------------------------------------------------
```

# test

The `EventSchedulerMojoTest` might fail in an ide with the following error: `org.codehaus.plexus.component.repository.exception.ComponentLookupException: java.util.NoSuchElementException`.
Try to do a `mvn test` from the command line, if that succeeds, it also works from ide.

## See also
* https://github.com/perfana/event-scheduler
* https://github.com/perfana/test-events-wiremock
* https://github.com/perfana/test-events-hello-world
* https://github.com/perfana/events-gatling-maven-plugin
* https://github.com/perfana/events-jmeter-maven-plugin
