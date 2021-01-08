/*
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

import nl.stokpop.eventscheduler.exception.EventSchedulerRuntimeException;

import java.io.File;

// seems this test needs jUnit 3 test* named methods to work with
// the MojoTest cases
public class EventSchedulerMojoTest extends BetterAbstractMojoTestCase {

    public void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp();
    }

    public void testExecute() throws Exception {

        File testPom = new File(getBasedir(), "/src/test/resources/event-scheduler-maven-plugin.xml");
        assertNotNull(testPom);

        EventSchedulerMojo mojo = (EventSchedulerMojo) lookupMojo("test", testPom);
        //EventSchedulerMojo mojo = (EventSchedulerMojo) lookupConfiguredMojo(testPom, "test");
        assertNotNull(mojo);

        mojo.execute();
    }

    public void testExecuteNoTestConfig() throws Exception {

        File testPom = new File(getBasedir(), "/src/test/resources/event-scheduler-maven-plugin-no-test-config.xml");
        assertNotNull(testPom);

        EventSchedulerMojo mojo = (EventSchedulerMojo) lookupMojo("test", testPom);
        //EventSchedulerMojo mojo = (EventSchedulerMojo) lookupConfiguredMojo(testPom, "test");
        assertNotNull(mojo);

        try {
            mojo.execute();
        } catch (EventSchedulerRuntimeException e) {
            // expected
            System.out.println("EventSchedulerRuntimeException: " + e.getMessage());
            return;
        }
        fail("expected EventSchedulerRuntimeException");
    }


}

