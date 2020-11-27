<!---
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
-->
 # event-scheduler-maven-plugin

Use plain event-scheduler from maven build to generate events during a load test.

 * Fires events according to the schedule.
 * Send start session and stop session and waits for given duration.
 * In case of failure or kill of process, sends abort session.

Run with: `mvn event-scheduler:test`

## Example 

Example configuration in maven `pom.xml` with `test-events-hello-world` events:

```xml 
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    
    <modelVersion>4.0.0</modelVersion>

    <groupId>nl.stokpop</groupId>
    <artifactId>events-test</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
 
        <encoding>UTF-8</encoding>

        <event-scheduler-maven-plugin.version>1.0.0</event-scheduler-maven-plugin.version>
        <test-events-hello-world.version>1.0.3</test-events-hello-world.version>

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
                    <groupId>nl.stokpop</groupId>
                    <artifactId>event-scheduler-maven-plugin</artifactId>
                    <version>${event-scheduler-maven-plugin.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>

        <plugins>
            <plugin>
                <groupId>nl.stokpop</groupId>
                <artifactId>event-scheduler-maven-plugin</artifactId>
                <configuration>

                    <eventDebugEnabled>true</eventDebugEnabled>
                    <eventSystemUnderTest>${systemUnderTest}</eventSystemUnderTest>
                    <eventVersion>${version}</eventVersion>
                    <eventWorkload>${workload}</eventWorkload>
                    <eventTestEnvironment>${testEnvironment}</eventTestEnvironment>
                    <eventTestRunId>${testRunId}</eventTestRunId>
                    <eventBuildResultsUrl>${buildResultsUrl}</eventBuildResultsUrl>
                    <eventRampupTimeInSeconds>${rampupTimeInSeconds}</eventRampupTimeInSeconds>
                    <eventConstantLoadTimeInSeconds>${constantLoadTimeInSeconds}</eventConstantLoadTimeInSeconds>
                    <eventAnnotations>${annotations}</eventAnnotations>

                    <eventVariables>
                        <property>
                            <name>$service</name>
                            <value>afterburner</value>
                        </property>
                    </eventVariables>
                    <eventTags>${tags}</eventTags>
                    <eventScheduleScript>
                        ${eventScheduleScript}
                    </eventScheduleScript>
                    <events>
                        <!-- here you can define events, with own properties per event -->
                        <StokpopHelloEvent1>
                            <eventFactory>nl.stokpop.helloworld.event.StokpopHelloEventFactory</eventFactory>
                            <myRestServer>https://my-rest-api</myRestServer>
                            <myCredentials>${ENV.SECRET}</myCredentials>
                        </StokpopHelloEvent1>
                    </events>
                </configuration>
                <dependencies>
                   <dependency>
                        <groupId>nl.stokpop</groupId>
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
➜  mvn event-scheduler:test
[INFO] Scanning for projects...
[INFO]
[INFO] -----------------------< nl.stokpop:events-test >-----------------------
[INFO] Building events-test 1.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- event-scheduler-maven-plugin:1.0.0-SNAPSHOT:test (default-cli) @ events-test ---
[INFO] Execute event-scheduler-maven-plugin
[StokpopHelloEvent] Class loaded
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Default constructor called.
                            <myRestServer>https://my-rest-api</myRestServer>
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Number of processors: 8      cores
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Max memory:           4096   MB
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Total memory:         256    MB
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Free memory:          245    MB
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Message: Default Hello Message
[WARNING] [StokpopHelloEvent1] [StokpopHelloEvent] unknown property found: 'myCredentials' with value: ''. Choose from: [helloInitialSleepSeconds, helloMessage, myRestServer]
[INFO] start test session
[INFO] broadcast before test event
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Hello before test [Afterburner-1.0-shortTest-cloud]
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Event properties: EventProperties{properties={myRestServer=https://my-rest-api, myCredentials=, eventFactory=nl.stokpop.helloworld.event.StokpopHelloEventFactory}}
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Sleep for 2 seconds
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Wakeup after 2 seconds
[INFO] calling keep alive every PT30S
[INFO] create new thread: Keep-Alive-Thread
[INFO] no custom schedule events found
[INFO] Sleep for PT40S
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Hello keep alive for test [Afterburner-1.0-shortTest-cloud]
                    </events>
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Hello keep alive for test [Afterburner-1.0-shortTest-cloud]
[INFO] stop test session.
[INFO] shutdown Executor threads
[INFO] broadcast after test event
[INFO] [StokpopHelloEvent1] [StokpopHelloEvent] Hello after test [Afterburner-1.0-shortTest-cloud]
[INFO] all broadcasts for stop test session are done
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  42.269 s
[INFO] Finished at: 2020-11-08T11:18:40+01:00
[INFO] ------------------------------------------------------------------------
```

## See also
* https://github.com/stokpop/event-scheduler
* https://github.com/stokpop/test-events-wiremock
* https://github.com/stokpop/test-events-hello-world
* https://github.com/stokpop/events-gatling-maven-plugin
* https://github.com/stokpop/events-jmeter-maven-plugin
