# Overview

Panakeia collects log events from several sources, processes and analyzes them
by running them through filters to correlate them and emits results to several
targets.  It also comes with a web application called
[Machaon](#web-application-machaon).

## Event analyzer

Panakeia uses a Riemann stream processor running on port 5555 and provides a
domain-specific language for writing Riemann filters to analyze events.  Each
namespace implements a specific filter.

## Input sources

Panakeia collects log events from several input sources:

### Input source: TIBCO bus

A docker container that runs the _TIBCO Bridge_ can be configured to listen on
TIBCO busses for certain subjects and submit them to Panakeia's Riemann stream
processor.

### Input source: Intellimove logs

A docker container called `intellimove` runs a Logstash instance that processes
the Intellimove logs that are hosted on network shares and mounted inside the
container.

### Input source: SLQ Database

A docker container called `sql-bridge` can be configured to query SQL
databases (currently Oracle and MSSQL) and submit the results to Riemann.

### Input source: Scout logs

A docker container called `scout` runs a Logstash instance on port 5044 that
processes the logs that the Scout servers send with Filebeat to
`http://rt-scout-logstash.rt.de.bosch.com:5044`.

### Input source: FDC passthrough logs

A docker container called `fdc-pt` runs a Logstash instance on port 5045 that
processes the logs that the FDC-PT machines send with Filebeat to
`rt-panakeia-fdcptlogs-logstash.rt.de.bosch.com:5045`.

### Input source: FDC PCE logs

A docker container called `fdc-pce` runs a Logstash instance on port 5046 that
processes the logs that the FDC-PCE machines send with Filebeat to
`rt-panakeia-fdcpcelogs-logstash.rt.de.bosch.com:5046`.

### Input source: Amundsen/Dunamikos logs

A docker container called `dunamikos-logstash` runs a Logstash instance on port
5043 that processes the logs that the Systema servers send with Filebeat to
`rt-panakeia-dunamikos-logstash.rt.de.bosch.com:5043`.

Additionally, the Dunamikos daemon sends its logs as JSON via TCP to the same
Logstash instance on another port 4660.  Logstash tags these logs with `daemon`.

### Input source: ETMINP/Pulse logs

A docker container called `etminp` runs a Logstash instance on port 5047 that
processes the logs that the Systema EI servers send with Filebeat to
`rt-panakeia-etminplogs-logstash.rt.de.bosch.com:5047`.

### Input source: Litho logs

A docker container called `lithologs-logstash` runs a Logstash instance on port
5051 that processes the logs that the litho servers send with Filebeat.
Currently it processes ASML Error logs that it sends to
`rt-panakeia-equipmentlogs-litho-logstash.rt.de.bosch.com:5051`.

### Input source: Backend logs via Lumberjack

A docker container called `backend-lumberjack-logstash` runs a Logstash instance
on port 5042 that processes the logs that the Backend servers send with
Logstash's Lumberjack protocol to
`rt-panakeia-logstash-backend.rt.de.bosch.com:5042`.

### Input source: Backend logs via Filebeat

A docker container called `backend-filebeat-logstash` runs a Logstash instance
on port 5035 that processes the logs that the Backend servers send via Beats
protocol to `rt-panakeia-logstash-backend.rt.de.bosch.com:5035`.

### Input source: Backend logs from network shares

A docker container called `backend-netmount-logstash` runs a Logstash instance
that processes the x2parquet logs that are hosted on network shares and mounted
inside the container.

### Input source: Original FactoryLink logs via Filebeat

A docker container called `factorylink-logstash` runs a Logstash instance on
port 5036 that processes the logs that the original FactoryLink servers send via
Beats protocol to `rt-panakeia-original-factorylink.rt.de.bosch.com:5036`.

### Input source: Particle data cubic

A docker container called `particle-data-cubic-logstash` runs a Logstash
instance on port 5036 that processes requests from a Python script that collects
particle data and send it via HTTP with JSON payload to
`rt-panakeia-particle-data-cubic.rt.de.bosch.com:5041`.

## Output destinations

### Output destination: Elasticsearch with Kibana frontend

Most results are written to an Elasticsearch cluster that can be examined with
[Kibana frontend](/kibana/).

### Output destination: Prometheus with Grafana frontend

Metrics are scraped by an Prometheus time series database that can be viewed
with [Grafana frontend](/grafana/).

### Output destination: TIBCO bus

Certain results are published on the TIBCO bus to be picked up by Systema
gateways.  Some filters currently publish to PULSE Gateway, SMS Gateway, and
Email Gateway.

### Output destination: Oracle Database

Some results are inserted into an Oracle database table.

## Web application Machaon

Machaon is the [web application](/machaon/) running on port 3333 that can
influcence (i.e. configure) and display certain aspects of the system:

- [Smart alarms configurator](/machaon/smart-alarms-configurator/) to configure
  alarm notifications
  

- [Alarms monitor](/machaon/alarms-monitor/) to monitor currently active alarms

## On-line documentation

[On-line documentation](/) with description of the overall system and the
currently implemented filters is available on port 8000.

## test-only serivces

### Signatures

- http://rt-panakeia-test.rt.de.bosch.com:9500
  
### Oracle test database

- rt-panakeia-test.rt.de.bosch.com:1521
