# Release notes Perfana Event Scheduler Maven Plugin

## 3.0.0 - April 2023

* uses event-scheduler 4.0.0: no more TestConfig at EventConfig level
  * in pom.xml: make sure to use event-scheduler plugins that also use event-scheduler 4.0.0+
  * in pom.xml: move TestConfig from EventConfig to EventSchedulerConfig level if needed 
* uses java 11 instead of java 8

## 3.0.2 - November 2023

* uses event-scheduler 4.0.1: improved default values for check results
