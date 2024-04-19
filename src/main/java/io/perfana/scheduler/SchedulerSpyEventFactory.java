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

import io.perfana.eventscheduler.api.Event;
import io.perfana.eventscheduler.api.EventFactory;
import io.perfana.eventscheduler.api.EventLogger;
import io.perfana.eventscheduler.api.config.EventContext;
import io.perfana.eventscheduler.api.config.TestContext;
import io.perfana.eventscheduler.api.message.EventMessageBus;

public class SchedulerSpyEventFactory implements EventFactory<EventContext> {
    private final String name;

    public SchedulerSpyEventFactory() {
        this("SchedulerSpyEventFactory default constructor instance");
    }

    SchedulerSpyEventFactory(String name) {
        this.name = name;
    }

    public Event create(EventContext context, TestContext testContext, EventMessageBus messageBus, EventLogger logger) {
        return new EventSchedulerStartTestListener(context, testContext, messageBus, logger);
    }

    public String toString() {
        return "SchedulerSpyEventFactory (" + this.name + ")";
    }
}