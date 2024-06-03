# System layout

## `rt-panakeia`

### Services

- `riemann`
- `machaon`
- `tibrv-bridge`
- `sql-bridge`
- `prometheus`
- `prometheus-alertmanager`

### Helpers

- `config-sync`
- `docker-monitor`
- `logspout`
- `logspout-logstash`
- `haproxy`
- `telegraf`

## `rt-panakeia-web`

### Services

- `grafana`
- `kibana`

### Helpers

- `grafana-image-renderer`
- `grafana-postgres`
- `grafana-backup-tool`
- `haproxy`
- `telegraf`
- `docker-monitor`

## `rt-panakeia-switch`

### Riemann

- `riemann-switch`

### Logstash

- `backend-logstash`
- `dunamikos-logstash`
- `etminp`
- `factorylink-logstash`
- `fdc-pce`
- `fdc-pt`
- `intellimove`
- `lithologs-logstash`
- `scout`

### Helpers

- `docker-monitor`
- `telegraf`

## `rt-panakeia-test`

### Services

- `riemann`
- `machaon`
- `tibrv-bridge`
- `sql-bridge`
- `lithologs-logstash`
- `kibana`
- `grafana`

### Test only

- `oracle`

### Helpers

- `grafana-image-renderer`
- `grafana-postgres`
- `grafana-backup-tool`
- `telegraf`
- `logspout`
- `config-sync`
- `docker-monitor`
- `postgres`
- `prometheus`
- `prometheus-alertmanager`
- `haproxy`
- `logspout-logstash`
