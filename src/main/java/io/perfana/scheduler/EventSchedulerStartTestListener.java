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

import io.perfana.eventscheduler.api.EventAdapter;
import io.perfana.eventscheduler.api.EventLogger;
import io.perfana.eventscheduler.api.config.EventContext;
import io.perfana.eventscheduler.api.config.TestContext;
import io.perfana.eventscheduler.api.message.EventMessageBus;

public class EventSchedulerStartTestListener extends EventAdapter<EventContext> {

    public EventSchedulerStartTestListener(EventContext context, TestContext testContext, EventMessageBus messageBus, EventLogger logger) {
        super(context, testContext, messageBus, logger);
    }

    @Override
    public void startTest() {
        super.startTest();
        logger.info("Scheduler plugin detected start test: start the wait time now.");
        EventSchedulerMojo.START_WAITING = true;
    }
}

