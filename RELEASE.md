# Release notes Perfana Event Scheduler Maven Plugin


## 3.0.6 - March 2025

* improved test run stop logging, added wait debugging

## 3.0.5 - January 2025

* uses event-scheduler 4.0.5: improved executor handling on shutdown and logging
* improved shutdown hook handling

## 3.0.4 - April 2024

* added spy event so the waiting time only starts are testStart event - to avoid having to compensate for long before test activities, such as starting a test remotely
* uses event-scheduler 4.0.4: ability to create new TestContexts with overrides using `withX` methods

## 3.0.3 - January 2024

* uses event-scheduler 4.0.3: improved "disabled event" handling

## 3.0.2 - November 2023

* uses event-scheduler 4.0.1: improved default values for check results

## 3.0.0 - April 2023

* uses event-scheduler 4.0.0: no more TestConfig at EventConfig level
  * in pom.xml: make sure to use event-scheduler plugins that also use event-scheduler 4.0.0+
  * in pom.xml: move TestConfig from EventConfig to EventSchedulerConfig level if needed 
* uses java 11 instead of java 8






