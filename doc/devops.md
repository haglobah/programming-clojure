<!-- -*- eval: (custom-set-variables '(markdown-toc-user-toc-structure-manipulation-fn 'cdr) '(markdown-toc-user-toc-structure-manipulation-fn (lambda (toc-structure) (--map (-let (((level . label) it)) (cons (- level 1) label)) (cdr toc-structure))))) -*- -->

# Devops

Devops documentation for Panakeia.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Overview](#overview)
- [Docker composition](#docker-composition)
    - [Startup](#startup)
    - [Docker build](#docker-build)
        - [Build test system on Apple Silicon Macs](#build-test-system-on-apple-silicon-macs)
- [Proxy configuration](#proxy-configuration)
    - [Proxy configuration of docker daemon](#proxy-configuration-of-docker-daemon)
    - [Proxy configuration of user environment](#proxy-configuration-of-user-environment)
    - [Providing a proxy server](#providing-a-proxy-server)
    - [Check internet connection](#check-internet-connection)
- [User account](#user-account)
- [tmux session](#tmux-session)
- [Git repository](#git-repository)
    - [Branches](#branches)
        - [Useful git settings](#useful-git-settings)
        - [GitFlow](#gitflow)
- [Deploy changes](#deploy-changes)
- [Reverse proxy setup](#reverse-proxy-setup)
- [Elasticsearch cluster](#elasticsearch-cluster)
    - [Riemann Elasticsearch endpoint](#riemann-elasticsearch-endpoint)
    - [Log files](#log-files)
    - [Index management](#index-management)
    - [Bootstrap elasticsearch indices - kibana index patterns - grafana datasources](#bootstrap-elasticsearch-indices---kibana-index-patterns---grafana-datasources)
        - [Deployment of new source](#deployment-of-new-source)
        - [Bootstrap elasticsearch index management with lifecycle policies](#bootstrap-elasticsearch-index-management-with-lifecycle-policies)
        - [Bootstrap kibana index patterns](#bootstrap-kibana-index-patterns)
        - [Bootstrap grafana datasources](#bootstrap-grafana-datasources)
- [Query Elasticsearch with Kibana](#query-elasticsearch-with-kibana)
- [Grafana](#grafana)
    - [Backup and Restore](#backup-and-restore)
    - [Copy Dashboards](#copy-dashboards)
    - [Adjust Library-Panels](#adjust-library-panels)
- [Monitor system health](#monitor-system-health)
    - [Incomplete logs](#incomplete-logs)
- [Lift load from production system](#lift-load-from-production-system)
    - [Reduce subjects that the Tibrv bridge listens on](#reduce-subjects-that-the-tibrv-bridge-listens-on)
    - [Reduce logstash logs](#reduce-logstash-logs)
- [Solve Docker and host system problems](#solve-docker-and-host-system-problems)
    - [Not all containers are running](#not-all-containers-are-running)
    - [Tibrv-Bridge lost network connectivity to Riemann](#tibrv-bridge-lost-network-connectivity-to-riemann)
    - [Problems with ressources on the host system](#problems-with-ressources-on-the-host-system)
        - [Disk usage](#disk-usage)
        - [High load due to rogue container](#high-load-due-to-rogue-container)
    - [Obsolete system peculiarities, no longer active, just for reference](#obsolete-system-peculiarities-no-longer-active-just-for-reference)
        - [[obsolete] Experiment with transmitting in events in smaller batches](#obsolete-experiment-with-transmitting-in-events-in-smaller-batches)
- [Move production system to test system](#move-production-system-to-test-system)
- [Connect to Riemann's REPL](#connect-to-riemanns-repl)
- [Profile Riemann's JVM process](#profile-riemanns-jvm-process)
    - [async-profiler](#async-profiler)
    - [jvmtop](#jvmtop)
- [Contacts](#contacts)

<!-- markdown-toc end -->

## Overview

Panakeia consists of about 30 running docker containers that interact in
separate docker compositions and are deployed on three different servers:
`rt-panakeia` is the main production server, `rt-panakeia-web` hosts mainly the
web services, and `rt-panakeia-switch` hosts the central Logstash servers and a
Riemann instance that dispatches events to both the production and the test
system (to be able to have realistic load on the test system).  Panakeia's
backend is an Elasticsearch cluster that consists of three servers
`rt-elastix[0-2]`.

Additionally, there is a test server `rt-panakeia-test` that hosts the test
deployment with an Elasticsearch test cluster that consists of two servers
`rt-elastixtest[0-1]`.

Here is an graphical overview:
https://rt-panakeia.rt.de.bosch.com/system-layout.html

## Docker composition

The docker composition is split up into various `docker-compose.yml`
configuration files to reflect the various composition sets that run on the
different severs.  There is a `docker-compose-*.sh` shell script for each
composition set.

- `docker-compose.yml` contains the core services that run on `rt-panakeia`, the
  production server.  The associated shell script is `docker-compose.sh`.
- `docker-compose-web.yml` contains the web services that run on
  `rt-panakeia-web`.  The associated shell script is `docker-compose-web.sh`.
- `docker-compose-switch.yml` contains the services that run on
  `rt-panakeia-switch`.  The associated shell script is
  `docker-compose-switch.sh`.
- `docker-compose-test.yml` contains the overrides for the test system that run
  on `rt-panakeia-test`.  The associated shell script is
  `docker-compose-test.sh`, which stacks the compositions of
  `docker-compose.yml`, `docker-compose-web.yml`, and `docker-compose-test.yml`.

(Implementation detail: There are configuration files like
`docker-compose-common.yml` and `docker-compose-prometheus.yml` that abstract
over commonalities between the different setups.  The shell scripts use them
accordingly.)

You can check what containers belong to which composition with

    ./docker-compose<*>.sh config --services

Each composition comes with services that provide the actual functionality and
many helper services for monitoring the system.

The `docker-compose-*.sh` shell scripts build a `docker-compose` command line
and pass any additional arguments directly to `docker-compose`.  So all the
`docker-compose` functionality is available, for example:

- `./docker-compose[*].sh up -d` to start not running or changed containers
- `./docker-compose[*].sh logs -ft` to show logs
- `./docker-compose[*].sh down` to stop composition
- `./docker-compose[*].sh build` to build and tag containers
- ...

The composition tries to abstract as much same code, ressources, and settings
between production and test setup as possible.  Most configuration files are
mountend instead of copied into images that otherwise are identical on
production and test systems.  Configuration files that override production
settings contain `test` in their name.

### Startup

The servers are configured to start the docker composition during boot.
Therefore, the servers have systemd service scripts deployed to
`/etc/systemd/system/panakeia[*].service` according to their role.

You can enable the service that start panakeia on boot on the production system
with

    sudo systemctl enable panakeia

You can start it manually with

    sudo systemctl start panakeia

And query its status with

    sudo systemctl status panakeia

To disable the service use

    sudo systemctl disable panakeia

If you make changes to the `/etc/systemd/system/panakeia[*].service` script, you
need to reload them with

    sudo systemctl daemon-reload

### Docker build

Some images in the compositions depend on custom "base" images that need to be
built first and exists in the local docker registry before the images of the
compositions can be built.  These base images can be built with
`docker-build-base.sh`, which is done automatically if you use the special build
scripts for each composition:

- `docker-build.sh` on `rt-panakeia`
- `docker-build-web.sh` on `rt-panakeia-web`
- `docker-build-switch.sh` on `rt-panakeia-switch`
- `docker-build-test.sh` on `rt-panakeia-test`

Building needs a working internet connection, see next section.

#### Build test system on Apple Silicon Macs

You have to set the platform to `linux/amd64` specifically and use the `buildx`
system.  You can configure `docker-compose` via environment variables to behave
correctly:

```
COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=0 DOCKER_DEFAULT_PLATFORM=linux/amd64 ./docker-build-test.sh
```

## Proxy configuration

Building the compositions needs a working internet connection, for both the
docker service daemon as well as the user's shell environment.  On Bosch's
servers that means it needs a working proxy setup.  Setting the docker daemon's
and user's proxy setting to `localhost:3128` and making sure that a proxy
connection is setup on `localhost` proved to be the most reliable solution.

### Proxy configuration of docker daemon

The proxy setting of the docker daemon need to be configured in
`/etc/systemd/system/docker.service.d/http-proxy.conf`:

    Environment="HTTP_PROXY=http://localhost:3128/" "HTTPS_PROXY=http://localhost:3128/" "NO_PROXY=localhost,127.0.0.1,rb-dtr.de.bosch.com,.bosch.com,bosch.com"

After changing these settings, the changes need to be published to `systemd` first:

    systemctl daemon-reload

and the docker daemon needs to be restarted

    service docker restart

which also restarts all running docker containers, which leads to undesirable
downtime.  Thus keeping the proxy configuration pointed to localhost avoids the
need for these restarts, since all proxy-related debugging and setup happens on
the localhost outside the docker daemon's configuration.

### Proxy configuration of user environment

Panakeia's source code repository contains a `env.sh` script that sets up all
needed environment variables and gets sourced by default in `.bashrc` under the
`docker` user on the panakeia servers.  `env.sh` also sets all proxy-related
environment variables:

```
export PROXY_HOST=`hostname -i` # the host itself, make sure that a proxy is available on port 3128
export http_proxy=http://${PROXY_HOST}:3128
export https_proxy=http://${PROXY_HOST}:3128
export no_proxy="localhost,127.0.0.1,.bosch.com,.de.bosch.com,.rt.de.bosch.com"
```

The `PROXY_HOST` environment variable needs to be set to the host's IP address.
It will not work to set the `PROXY_HOST` environment variable to `localhost`
because these environment variables get passed into the running docker
containers where `localhost` points to the running container itself and not the
host system.  Therefore, you need to make sure that `PROXY_HOST` is set to the
IP address of the host system and that the proxy connection setup there listens
not only on localhost's interface but on `0.0.0.0`.  See next section for more
details.

### Providing a proxy server

There are two ways of providing a proxy server:

- Run a proxy server on the host directly.  This can be done with `cntlm` in
  gateway mode `-g` so that it listens on `0.0.0.0`:

        /usr/sbin/cntlm -g -I -u mc66rt@de -l 3128 proxy.rt.de.bosch.com:8080

  `cntlm` prompts for `mc66rt`'s password.

- Open an SSH tunnel to a proxy server that runs on another machine:

        ssh -L 0.0.0.0:3128:localhost:3128 fl33rt@rt0vm00091

    This is my preferred way to running proxies: On `rt0vm00091` under user `fl33rt`
    there is a `tmux` session running that hosts the `cntlm` process.  Use

        tmux a -t 0

    on `rt0vm00091` to connect to that session.  You have to do this for example
    after the proxy server requires re-authentication because of an account's
    password change or the like, which happens seemingly randomly from time to
    time.  That is why I prefer to maintain only one "real" `cntlm` proxy
    connection and use SSH tunnels from all other servers to this one
    connection, since I only have to fix one connection if the proxy server
    decides to end the communication.  Detach from the `tmux` session with `C-p
    d`, do not exit the shell process!

### Check internet connection

If `curl google.com` returns something like

```
<HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
<TITLE>301 Moved</TITLE></HEAD><BODY>
<H1>301 Moved</H1>
The document has moved
<A HREF="http://www.google.com/">here</A>.
</BODY></HTML>
```

everythings works as it should.

## User account

The panakeia servers `rt-panakeia`, `rt-panakeia-web`, `rt-panakeia-switch`, and
`rt-panakeia-test` all have the same docker installation and have a system user
called `docker` configured.

To connect to these hosts, you need to obtain a one-time passwort via
`https://rb-pam.bosch.com` and login via SSH as the user that
`https://rb-pam.bosch.com` provides (usually `rbadmin_app1` or `rbadmin_app2`):

    ssh rbadmin_app1@<rt-panakeia*>

`https://rb-pam.bosch.com` does not know about the DNS alias names though, you have to
search the server you want to connect to by its actual DNS name:

- `rt-panakeia` is `rt-105l.de.bosch.com`
- `rt-panakeia-web` is `rt0vm00015.de.bosch.com`
- `rt-panakeia-switch` is `rt-106l.de.bosch.com`
- `rt-panakeia-test` is `rt-107l.de.bosch.com`

There is a convenient shortcut by using the `rb-psmp` SSH gateway to connect to
hosts with obtaining the one-time passwort transparently, for example to connect
to `rt-105l`:

    ssh <bosch-user-account>@rbadmin_app1@rt-105l.de.bosch.com@rb-psmp.bosch.com

There is also an SSH gateway that takes care of the `rb-pam` lookup.  For
example, you can connect to the production system with this shortcut:

```
ssh mc66rt@rbadmin_app1@rt-042l.de.bosch.com@rb-psmp.bosch.com
```

When you are connected, you need to switch to the `docker` user:

    sudo -s
    su - docker

As a convention, there is a `tmux` session running under the `docker` user on
each Panakeia server.  See next section.

[//]: # (FIXME: ssh-key forwarding)

## tmux session

On each Panakeia server, there is a `tmux` session running under the `docker`
user.  The `tmux` session provides a convenient work environment by
reconnecting to it without the need to opening command prompts, switching users,
and changing directories.  The `tmux` session hosts the proxy connection and has
several other open windows, one that is usually used to deploy changes, and one
open root shell.  All windows are named properly.  Here are the most important
`tmux` keyboard shortcuts:

- `C-p d` detach from `tmux` session and leave it running in the background.
  Use this to exit the `tmux` session, to not exit the shell sessions in the
  windows!
- `C-p [0-9]` switch to window number [0-9]
- `C-p p` switch to previous window
- `C-p c` create new window
- `C-p A` rename current window
- `C-p ?` show help

When reconnecting to the `tmux` session fails, something went wrong (maybe the
server was rebooted).  Then you need to setup a new `tmux` session, connect
the proxy and restart the docker containers.  I usually do this and rename the
`tmux` windows accordingly (keyboard shortcuts in angle brackets):

    tmux<RET>
    <C-p A>proxy<RET>
    ssh -L 0.0.0.0:3128:localhost:3128 fl33rt@rt0vm00091<RET>
    [password]<RET>
    <C-p c>
    <C-p A>[name of panakeia system]<RET>
    ./docker-compose[*].sh up -d

## Git repository

There are two git remotes.  One git remote is hosted at Bosch and is used to
update the Panakeia servers at Bosch and to coordinate development with Bosch
colleagues.  The Bosch remote can be reached from within the Bosch network via

    ssh://git@rt-bitbucket.rt.de.bosch.com:7999/pan/panakeia.git

or via

    ssh://git@localhost:7999/pan/panakeia.git

through Active Group's Bosch VPN setup.  Markus Brügner manages access to the repository.

The second git remote is hosted at Active Group.  It is used for continuous
integration and for development at Active Group with colleagues that do not have
a Bosch account:

    ssh://git@joshua.active-group.de:1022/ag/panakeia

It is possible to setup a GIT repository with two remotes so that pushing,
pulling, and merging the two remotes is easy.  A `.git/config` can be configured
like this:

```
# Bosch Bitbucket via Cisco AnyConnect
# [remote "bosch-direct"]
#    url = ssh://git@rt-bitbucket.rt.de.bosch.com:7999/pan/panakeia.git
#    fetch = +refs/heads/*:refs/remotes/bosch/*
# Bosch Bitbucket via bosch-vpn
[remote "bosch"]
    url = ssh://git@localhost:7999/pan/panakeia.git
    fetch = +refs/heads/*:refs/remotes/bosch/*
[remote "ag"]
    url = ssh://git@joshua.active-group.de:1022/ag/panakeia
    fetch = +refs/heads/*:refs/remotes/origin/*
```

### Branches

The Panakeia servers have checkouts deployed. That is, it runs the software
directly from checked out git branches. On the production systems `rt-panakeia`
and `rt-panakeia-web` runs the `stable` branch. On the test system
`rt-panakeia-test` runs the `staging` branch. The `staging` branch also runs on
`rt-panakeia-switch`, which technically speaking is part of the production
systems since it also feeds the production system, but needs sometimes be
deployed with newly developed features.

Features are developed on feature branches. By merging them into `staging` they
are deployed and tested on `rt-panakeia-test`. Finished features are merged from
its feature branch into the `main` branch. The `main` branch is used as a base
for production deployment by merging its state into `stable`.

For bugfixes or time-critical improvements to production, you can branch bugfix
branches from `stable`. To test these branches, merge them into `staging` and
deploy them to the test system. Finished bugfix branches are merged into
`stable` and deployed to production. Then merge `stable` to `main` and `main` to
`staging`. Afterwards delete your bugfix branch and close the issue.

Since all features end up in `staging` to try them out, even the ones that are
only half-baked or cancelled or discarded, `staging` gets never merged back
to any other branch. And there should also be no direct commits on `staging`,
since these changes would never make it back to `main` or `stable`. Toplevel,
`staging` should only consist of merges from other branches.

#### Useful git settings

To avoid faulty merges, we recommend to install some git hooks.

Here is a merge hook that will prevent `staging` from getting merged to any
other branch (see the comment in the code for usage and installation
instructions):

```
#!/usr/bin/env ruby

# This git hook will prevent merging specific branches except from merging into themselves.
# Put this file in your local repo, in the .git/hooks folder
# and make sure it is executable.
# The name of the file *must* be "prepare-commit-msg" for Git to pick it up.

FORBIDDEN_BRANCHES = ["staging"]

def merge?
  ARGV[1] == "merge"
end

def current_branch
  return `git symbolic-ref --short HEAD`
end

def merge_msg
  @msg ||= `cat .git/MERGE_MSG`
end

def from_branch
  @is_merge_commit = merge_msg.match(/Merge branch '(.*?)'/)
  if @is_merge_commit
    @from_branch = merge_msg.match(/Merge branch '(.*?)'/)[1]
  else
    return true
  end
end

def from_forbidden_branch?
  FORBIDDEN_BRANCHES.include?(from_branch)
end

if merge? && from_forbidden_branch? && (current_branch != from_branch)
  out = `git reset --merge`
  puts
  puts " STOP THE PRESSES!"
  puts " You are trying to merge #{from_branch} into branch #{current_branch}."
  puts " Surely you don't mean that?"
  puts
  puts " run the following command now to discard your working tree changes:"
  puts
  puts " git reset --merge"
  puts
  exit 1
end
```

This hook only catches real merges, so fast-forward merges are not caught.
Therefore, you might want to consider turning off fast-forward merges globally:

    git config --global merge.ff false
    git config --global pull.ff true

And another useful hook that warns when branching from staging, which is also
very bad:

```
#!/bin/sh

# This git hook will prevent branching from specific branches.
# Put this file in your local repo, in the .git/hooks folder
# and make sure it is executable.
# The name of the file *must* be "post-checkout" for Git to pick it up.

FORBIDDEN_BRANCH=staging

getBranchName()
{
    echo $(git rev-parse --abbrev-ref HEAD)
}

getMergedBranches()
{
    echo $(git branch --merged)
}

if [ "$(getBranchName)" != "${FORBIDDEN_BRANCH}" ]; then
    if [[ $(getMergedBranches) == *"${FORBIDDEN_BRANCH}"* ]]; then
        echo "ATTENTION: Don't create branches from the ${FORBIDDEN_BRANCH} branch!"
        echo "Please delete this branch and start over from a different branch:"
        echo "  git checkout @{-1} && git branch -d ${getBranchName}"
        exit 1
    fi
fi
```

#### GitFlow

We are using the [GitFlow
workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
with just two branch names changed: `main` -> `stable` and `develop` -> `main`,
the rest is identical.  The last section from the above link reads with our
branch names substituted:

The overall flow of Gitflow is:

- A `main` branch is created from `stable`
- A `release` branch is created from `main`
- Feature branches are created from `main`
- When a feature is complete it is merged into the `main` branch
- When the release branch is done it is merged into `main` and `stable`
- If an issue in `stable` is detected a `hotfix` branch is created from `stable`
- Once the `hotfix` is complete it is merged to both `main` and `stable`


We currently do not use `release` branches since we currently do not need any
"release engineering", we thus merge `main` directly into `stable`.  But we can
easily adopt that when it is needed.

Using this GitFlow `main` and `stable` stays synchronized. However, sometimes we
do not release immediately from `main` into `stable`. In such a case it can make
sense to create a feature branch from `stable` such that the feature can be
merged into `stable` skipping `main`.

To test a feature for completion we merge the feature branch into `staging`.

We name our `feature` branches according to this scheme:
`feature/AG-<redmine-issue-number>-<short-name>` where `<redmine-issue-number>`
is the issue number from [Redmine](projects.active-group.de/) and `<short-name>`
is a short descriptive name of the topic of the branch.

## Deploy changes

Panakeia is run directly from the source git repositories.  Connect to the
`tmux` session on the system you want to update and issue

    git pull && ./docker-compose*.sh up -d --build

where you must pick the `./docker-compose*.sh` that matches the system you are
on, see [Docker composition](#docker-composition).

Some changes do not change the build context of a Docker image und thus do not
have Docker trigger a rebuild of the image and a restart of the container.  But
the changes might be in files that the Docker composition mounts into the
container.  Such changes might get unnoticed by the Docker composition and the
above command might not restart such a container with the updated files (one
example is `docker-monitor`).  You have to force restart these containers, for
example:

    ./docker-compose*.sh up -d --force-recreate docker-monitor

## Reverse proxy setup

The various web services that run from many containers on different host are
conveniently and transparantly hidden behind a proxy server that makes it easier
for users and keeps the urls pretty and human readable and handles SSL/TLS
encryption.

Every production service of panakeia can be reached starting from

- https://rt-panakeia.rt.de.bosch.com

the proxy setup under `docker-haproxy/haproxy.cfg` redirects either to
`rt-panakeia` itself or to `rt-panakeia-web`, that hosts most of the productive
web services.  Another proxy runs on `rt-panakeia-web`.

Every test service of panakeia can be reached starting from

- https://rt-panakeia-test.rt.de.bosch.com

the proxy setup under `docker-haproxy/haproxy-test.cfg` takes care of the
redirects within the test system.

Here is an overview of URLs to containers that eventually serve the request:

| URL             | container production system    | container test system           |
|-----------------|--------------------------------|---------------------------------|
| `grafana/`      | `grafana` on `rt-panakeia-web` | `grafana` on `rt-panakeia-test` |
| `kibana/`       | `kibana` on `rt-panakeia-web`  | `kibana` on `rt-panakeia-test`  |
| `machaon/`      | `machaon` on `rt-panakeia`     | `machaon` on `rt-panakeia-test` |
| everything else | `riemann` on `rt-panakeia`     | `riemann` on `rt-panakeia-test` |

## Elasticsearch cluster

The Elasticsearch production cluster that consists of three servers
`rt-elastix[0-2]`.

The Elasticsearch test cluster that consists of two servers
`rt-elastixtest[0-1]`.

To connect to these servers, you need to obtain a one-time passwort via
`https://rb-pam.bosch.com` and login via SSH as the user that
`https://rb-pam.bosch.com` provides (usually `rbadmin_app1` or `rbadmin_app2`),
see [detailed description above](#user-account).  `https://rb-pam.bosch.com` does not
know about the DNS alias names though, you have to search the server you want to
connect to by its actual DNS name:

- `rt-elastix0` is `rt-010l.de.bosch.com` (also `rt-elastix-ingest.rt.de.bosch.com`)
- `rt-elastix1` is `rt-011l.de.bosch.com`
- `rt-elastix2` is `rt-153l.de.bosch.com`
- `rt-elastixtest0` is `rt0vm926.de.bosch.com`
- `rt-elastixtest1` is `rt0vm927.de.bosch.com`


### Riemann Elasticsearch endpoint

To modify the riemann Elasticsearch endpoint the ingest node can be set with curl:

Testsystem:

```
curl -X GET -H "x-api-key: YOUR-API-KEY-HERE" https://rt-panakeia-test.rt.de.bosch.com/riemann/override-elasticsearch-url
curl -X PUT -H "x-api-key: YOUR-API-KEY-HERE" --data-urlencode "data=http://rt-elastixtest-ingest.rt.de.bosch.com:9200" https://rt-panakeia-test.rt.de.bosch.com/riemann/override-elasticsearch-url
```

You can find the api-key here: panakeia/riemann/docker/test-config.edn


Prodsystem:

```
curl -X GET -H "x-api-key: YOUR-API-KEY-HERE" https://rt-panakeia.rt.de.bosch.com/riemann/override-elasticsearch-url
curl -X PUT -H "x-api-key: YOUR-API-KEY-HERE" --data-urlencode "data=http://rt-elastix-ingest.rt.de.bosch.com:9200" https://rt-panakeia.rt.de.bosch.com/riemann/override-elasticsearch-url
```

You can find the api-key here: panakeia/riemann/docker/config.edn


### Log files

You can find the logs on the Elasticsearch servers at
`/apps/elasticsearch/logs/elastix_server.json` or rather
`/apps/elasticsearch/logs/elastixtest_server.json`.

### Index management

You can use Kibana's web frontend to monitor cluster health and manage indices
for the production system:

- https://rt-panakeia.rt.de.bosch.com/kibana/app/monitoring#
- https://rt-panakeia.rt.de.bosch.com/kibana/app/management/data/index_management/indices

and the test system:

- https://rt-panakeia-test.rt.de.bosch.com/kibana/app/monitoring#
- https://rt-panakeia-test.rt.de.bosch.com/kibana/app/management/data/index_management/indices

### Bootstrap elasticsearch indices - kibana index patterns - grafana datasources

To boostrap elasticsearch indices, kibana index patterns and grafana datasources
for a source, the script `bootstrap-elasticsearch-kibana-grafana.sh` can be
used.

To add a new source, open the script and add your source to the `metrics_list`
at the top. You can call the script with the flag `--help` for getting help or
`production` for running on production. Running the script without a flag or
with any other flag, will default to the test-system. Make sure to run the
script on the project's root directory, otherwise getting the grafana-user and
password will fail.

For each source in the `metrics_list` the script will run
`bootstrap-elasticsearch-ilm.sh`, `bootstrap-kibana-index-pattern.sh` and
`bootstrap-grafana-datasource.sh`, using some predefined values, like the (test)
host and port for elastix, kibana and grafana and predefined name-schemes like:

| script        | scheme                          | example                 |
|---------------|---------------------------------|-------------------------|
| elasticsearch | <name in list>                  | aergia                  |
| kibana        | <name in list>-*                | aergia-*                |
| grafana       | elasticsearch-<name in list>-v1 | elasticsearch-aergia-v1 |

Additionally, adjustments in the `metrics_list` affect the queried datasources
of a library-panel, that is part of the `Panakeia Core: Overview` dashboard (via
the `docker-grafana-scripts/adjust/plain.sh` script).

#### Deployment of new source

1. Add or adjust riemann filter (`assoc :es-index <es-index-name>`)
2. Add `<es-index-name>` to `metrics_list` in `bootstrap-elasticsearch-kibana-grafana.sh`.
3. Pull changes on (test-)system.
4. Run `./bootstrap-elasticsearch-kibana-grafana.sh`-script.
5. Run usual docker-compose-commands (e.g. up and build) to make the changes of the filter available.

#### Bootstrap elasticsearch index management with lifecycle policies

The Elasticsearch clusters use "Index Lifecycle Policies" to rotate and delete
old indices.  To have "Index Lifecycle Policies" work, you need to bootstrap the
indices that should behave according to a policy.  To bootstrap, use the script
`bootstrap-elasticsearch-ilm.sh` that accepts an index name as first and
`hostname:port` of the Elasticsearch server as second argument (default is
`localhost:9200`) and the lifecycle parameters, run without arguments for usage
hints.  The script creates lifecycle policies, creates index templates for the
given index with a lifecycle policy and index name aliases that specify the
actual index naming scheme.  Warning: If the index name aliases is already taken
by an index, the scripts deletes the index.

#### Bootstrap kibana index patterns

To bootstrap a kibana index pattern, the script
`bootstrap-kibana-index-pattern.sh` can be used. Use the flag `--help` or open
the file for details on using the script.

#### Bootstrap grafana datasources

To bootstrap a grafana datasource, the script `bootstrap-grafana-datasource.sh`
can be used. Use the flag `--help` or open the file for details on using the
script. The script focus is on datasources of the type `elasticsearch`.

Note: We also use grafana-config-files to provision grafana datasources. These
files can be found within the `docker-grafana`-folder. Currently, grafana
datasources of type `elasticsearch` are usually bootstrapped via the script.
Exceptions are more general grafana datasources of type `elasticsearch` which
can be found in their grafana-config-files within the `docker-grafana`-folder.
All other datasources are handled with their respective config-files.

## Query Elasticsearch with Kibana

You can use Kibana's web fronted to query the events in the Elasticsearch index.  Production Kibana is at

- https://rt-panakeia.rt.de.bosch.com/kibana/

test Kibana is at

- https://rt-panakeia-test.rt.de.bosch.com/kibana/

There are several index pattern defined, make sure you select `*` to include all
indices in your query.  Useful queries contain

- `panakeia-filter:"<panakeia filter name>"`, a list of filters can be found
  [here](index.html)

- `_index:docker*` show the logs of the running docker containers

## Grafana

### Backup and Restore

Every hour the cronjob of the docker container `grafana-backup-tool`
creates a grafana backup with the name `grafana-backup-<day>-<hour>.tar.gz` and
stores it at `docker-volumes/grafana-backups`. Details to the backup-tool can be
found here: https://github.com/ysde/grafana-backup-tool

On success, the tool writes to telegraph with the metric name
`grafana_backup_tool_backed_up`, which then can be found in prometheus.

Within the docker container there exists a rudimentary restore script
(`grafana-backup-restore.sh`). Use it with care, inform yourself of the
consequences and whether a more specific restore makes more sense in your case.

### Copy Dashboards

You can copy a grafana dashboard from one instance to another, copy alert-rules
associated with a specific grafana dashboard or copy library-panels associated
with a specific grafana dashboard. Note: If the dashboard, alert-rule or
library-panel to copy already exists, it will be overwritten without warning.
Details on the tool can be found here:
https://github.com/active-group/active-grafana

For copying from the test-grafana-instance to the production-grafana-instance,
or the other way round, run the following command and follow the instructions
there:

`docker-grafana-scripts/copy/plain.sh`

Additionally, as a shortcut, there exist a script for the `Panakeia Core:
Overview` dashboard. Run the following command and follow the instructions
there:

`docker-grafana-scripts/copy/panakeia-core-overview.sh`

### Adjust Library-Panels

In the very special case that you have a library-panel, where the queries only
differ in their datasource and the panel follows a specific structure, you can
add or remove datasources by running the following script and follow its
instructions there:

`docker-grafana-scripts/adjust/plain.sh`

An example usage, with a panel that has that specific structure, can be found in
`boostrap-elasticsearch-kibana-grafana.sh`.

Details on the tool can be found here:
https://github.com/active-group/active-grafana

## Monitor system health

The Grafana dashboard `Panakeia Core: Overview` shows the most important system
health indicators.  On the production system, it can be reached under

- https://rt-panakeia.rt.de.bosch.com/grafana/d/85ZGrbeWz/panakeia-core-overview?orgId=1

For the test systems, the system health overview is at

- https://rt-panakeia-test.rt.de.bosch.com/grafana/d/85ZGrbeWz/panakeia-core-overview?orgId=1

Login either with your Bosch user or with `grafanaAdmin`.

The left column shows basic Docker and host system status: Running containers,
network, CPU, and memory metrics.  In the panel `Running containers` at the
bottom of the dashboard you can see the container's running state over time.  If
not all Docker containers are running or problems with the ressources on the
host system, see section [Solve Docker and host system
problems](#solve-docker-and-host-system-problems) that contains some ideas that
might help solving the issues.

The panel `Panakeia: incoming queue size` basically shows if Riemann can keep up
with all the events it gets sent.  If the incoming queue size is above one
thousand or so, the systems cannot keep up.  If Riemann cannot keep up, the
panel `Tibrv queue length` is the first service that suffers from Riemann's
backpressure.  If queue length is above 2.000 elements, the system drags behind
real-time considerably.  The panels named `Bridge latency` shows how much
certain events are delayed from their occurance on the Tibrv bridge until they
are indexed by Elasticsearch.  There are alert threshold set for these kinds of
problems, Grafana mails these alerts to Markus Brügner.  Currently we see
temporary performance problems in production that can last up to two hours that
the system is able to recover from fully.

If the system is not able to recover from performance problems, the overall load
needs to be decreased, see section [Lift load from production
system](#lift-load-from-production-system).

If the `Tibrv queue length` increases continously, the reason can be that the
the `tibrv-bridge` container lost network connectivity to the `riemann`
container, see section [Tibrv-Bridge lost network connectivity to
Riemann](#tibrv-bridge-lost-network-connectivity-to-riemann).

### Incomplete logs

If the metrics in the Overview dashboards are incomplete, first check if the
`riemann` container is running properly since it is responsible for gernerating
and forwarding the metrics:

    ./docker-compose*.sh logs riemann

Narrow down where the metrics are missing, check if the panels with missing data
are backed by Prometheus or Elasticsearch datasources.  Then check the Prometheus
containers or Elasticsearch cluster for problems.  Note that some queries that
cover a long time span are expected to fail with Elasticsearch backed panels
since the result set is simply to large and aggregating it would be way to
expensive since Elasticsearch ist not made to perform that way.

## Lift load from production system

When the production system is not able to keep up with the load, you can reduce load
of unimportant filters to have the most important filters perform acceptably.
The panel `Panakeia accept event duration` on the Overview dashboard in Grafana
might help to decide where it could be worth to reduce load.  This panel shows
the time that the different filters take to process the events.  The filter that
takes the most process time relieves the system the most when it has fewer or
none events to process.  The [namespace documentation](index.html) of each
filter lists the input sources.  These ways to relieve load worked well in the
past:

### Reduce subjects that the Tibrv bridge listens on

On `rt-panakeia`, comment out subjects in `tibrv-bridge/docker/bridge.config`
and then restart the bridge:

    docker-compose up -d --build tibrv-bridge

For example, sometimes the subjects
`"BOSCH.RT.FAB2.Production.Equipment.TList.Event.StateChanged.*` might cause a
very high load on the production systems smart-alarms filter, the reason is
unclear up to now, check https://projects.active-group.de/issues/4908 .

```
[docker@rt-042l panakeia]$ git diff
diff --git a/tibrv-bridge/docker/bridge.config b/tibrv-bridge/docker/bridge.config
index 9681da8..92e4851 100644
--- a/tibrv-bridge/docker/bridge.config
+++ b/tibrv-bridge/docker/bridge.config
@@ -33,7 +33,7 @@
                                 ;; Carrier checked
                                 "BOSCH.RT.FAB2.Production.Equipment.EQC.Event.CARRIER_CHECKED.>" "eqc-carrier-checked"
                                 ;; task list errors
-                                "BOSCH.RT.FAB2.Production.Equipment.TList.Event.StateChanged.>" "task-list-errors"
+                                ;; FIXME load: "BOSCH.RT.FAB2.Production.Equipment.TList.Event.StateChanged.>" "task-list-errors"

                                 ;; tibrv advisory messages (only the really interesting ones -> system load!)
                                 "_RV.INFO.SYSTEM.HOST.STATUS.>" "tibrv-advisory-messages-info"
```

### Reduce logstash logs

On `rt-panakeia-switch` you can decide what logstash logs get routed to the test
and production system.  The following patch would prevent Dunamikos and
Intellimove logs from beeing forwared to the production system:

```
[docker@rt-041l panakeia]$ git diff
diff --git a/riemann-switch/riemann.config b/riemann-switch/riemann.config
index 31abc0e..d533c73 100644
--- a/riemann-switch/riemann.config
+++ b/riemann-switch/riemann.config
@@ -17,6 +17,8 @@
   (streams
     (sdo
       (async-queue! :panakeia-test {:queue-size 10000 :core-pool-size 4 :max-pool-size 1024}
-                    (batch 10000 1 (forward panakeia-test)))
-      (async-queue! :panakeia-prod {:queue-size 10000 :core-pool-size 4 :max-pool-size 1024}
-                    (batch 10000 1 (forward panakeia-prod))))))
+                    (batch 1000 1/10 (forward panakeia-test)))
+      (where (not (or (service "logstash-dunamikos")
+                        (service "logstash-intellimove"))
+             (async-queue! :panakeia-prod {:queue-size 10000 :core-pool-size 4 :max-pool-size 1024}
+                           (batch 1000 1/10 (forward panakeia-prod)))))))
```

## Solve Docker and host system problems

### Not all containers are running

If the panel `Docker Containers Up` in Grafana's `Panakeia Core: Overview`
dashboard does not show 100%, some Docker containers exited unexpectedly.  In
the panel `Running containers` at the bottom of the dashboard you can see the
container's running state over time.  Connect to the server that lacks running
containers and check the logs of the `docker-monitor` container to find out what
containers are not running:

    ./docker-compose*.sh logs docker-monitor

For example, this yields:

```
 Expected: 16     config-sync docker-monitor grafana grafana-image-renderer grafana-postgres haproxy prometheus kibana logspout logspout-logstash machaon oracle oracle-bridge riemann telegraf tibrv-bridge
 Actual:   15     config-sync docker-monitor grafana grafana-image-renderer grafana-postgres haproxy prometheus kibana logspout logspout-logstash machaon oracle-bridge riemann telegraf tibrv-bridge
 Missing:  1     oracle
```

Then check what happened to the missing containers, for example:

    ./docker-compose*.sh logs oracle

and save the logs to be able to debug the problem properly later.

Then restart all missing containers

    ./docker-compose*.sh up -d

and check the container's logs if they come up successfully.

### Tibrv-Bridge lost network connectivity to Riemann

If the `Tibrv queue length` increases continously, the reason can be that the
the `tibrv-bridge` container lost network connectivity to the `riemann`
container.  This is a known problem since spring 2023 with no reliable solution
yet.  A suitable workaround is to forcefully restart the `tibrv-bridge`
container once:

    ./docker-compose*.sh up -d --force-recreate tibrv-bridge

Sometimes it is caused by restarting the `riemann` container without also
restarting the `tibrv-bridge` container -- this can happen during development
and trying out things easily.  Although we have configured a dependency in the
Docker composotion between the two containers, Docker sometimes neglects that
dependency and simply does not restart the other container.  That mostly (but
not always) happens when only bulding and restarting the `riemann` container, so
a good rule of thumb is to never call the shell scripts that build and restart
the containers with a single `riemann` argument to just update the container in
isolation.  Better to always just call the scripts without any argument to let
Docker figure out what to update itself, even if that takes slightly longer.

### Problems with ressources on the host system

#### Disk usage

Always check if some filesystem partitions are full, especially the ones mounted
to `/`, `/net/...`, `/var/...`, and `/local/...`:

    df -h

If some partitition ran out of space, try to identify with

    du -hs ...

where all the space went and show good judgment in reclaiming space by deleting
garbage.

#### High load due to rogue container

Check

    docker stats

to see if one container causes high cpu load or high I/O usage.  It might give a
hint if a container has problems and is therefore for example emitting a very
large amount of error logs that put a lot of strain on the system.  Check the
logs with

    ./docker-compose*.sh logs -f

### Obsolete system peculiarities, no longer active, just for reference

#### [obsolete] Experiment with transmitting in events in smaller batches

- Working copy on production system `rt-panakeia` is currently dirty to evaluate
  tuned settings for Panakeia's Elasticsearch connector:

```
diff --git a/riemann/src/panakeia/riemann/elasticsearch.clj b/riemann/src/panakeia/riemann/elasticsearch.clj
index 162c868..c5786d8 100644
--- a/riemann/src/panakeia/riemann/elasticsearch.clj
+++ b/riemann/src/panakeia/riemann/elasticsearch.clj
@@ -13,7 +13,7 @@
   (elasticsearch/elasticsearch-filter
    (elasticsearch/make-elasticsearch-stream elasticsearch-url
                                             "panakeia"
-                                            {:batch-n 1000 :batch-dt 1/10 :es-index-suffix ""})
+                                            {:batch-n 100 :batch-dt 1/10 :es-index-suffix ""})
    (fn [ev]
      (-> ev
          (assoc :program "panakeia")
```

  This should not be harmful, but when in doubt roll it back and restart Riemann with

     ./docker-compose.sh up -d --build riemann

  and check the container's logs if it comes up successfully.

## Move production system to test system

```
# prepare building on NEW-PROD
git checkout stable
./docker-build.sh

# initial sync
mkdir docker-volumes-prod
sudo rsync -auv --progress --delete -e "ssh -xi /app/home/docker/.ssh/id_rsa" root@rt-105l:/app/panakeia/docker-volumes-prod/. /app/panakeia/docker-volumes-prod/.

# stop test system
git checkout staging
sudo systemctl disable panakeia-test
./docker-compose-test.sh rm -fs
mv docker-volumes docker-volumes-test

### stop prod on PREVIOUS-PROD and adjust DNS aliases

# sync again
sudo rsync -auv --progress --delete -e "ssh -xi /app/home/docker/.ssh/id_rsa" root@rt-105l:/app/panakeia/docker-volumes-prod/. /app/panakeia/docker-volumes-prod/.
mv docker-volumes-prod docker-volumes

# start prod system
git checkout stable
./docker-compose.sh up --build -d
# make sure that /etc/systemd/system/panakeia.service is there
sudo systemctl daemon-reload
sudo systemctl enable panakeia
```

Maybe you need to restart `rt-panakeia-switch` so that it picks up the DNS
changes. Or:

```
[root@rt-041l panakeia]# systemctl reload NetworkManager
[root@rt-041l panakeia]# host rt-panakeia.rt.de.bosch.com
rt-panakeia.rt.de.bosch.com is an alias for rt-043l.de.bosch.com.
rt-043l.de.bosch.com has address 10.38.217.141
```

## Connect to Riemann's REPL

For hard-core debugging:

```
./docker-compose-test.sh exec riemann /opt/panakeia/docker/lein repl :connect 127.0.0.1:5557
```

## Profile Riemann's JVM process

### async-profiler

https://github.com/jvm-profiling-tools/async-profiler

- Run profiler on Riemann:
```
$ ./docker-compose-test.sh exec riemann /async-profiler/profiler.sh 1 > profile-1.txt
```

- Accumulate percentage riemann spends in Elasticsearch outputter:
```
$ cat profile-1.txt | grep -e "---\|riemann.elasticsearch" | grep -B 1 -e "  \[" |  grep -e "---" | sed 's/.*(\(.*\)%).*/\1/g' | tr '\n' '+' | sed s/+$/\\n/g | bc
42.08
```

### jvmtop

https://github.com/patric-r/jvmtop

```
./docker-compose-test.sh exec riemann env JAVA_HOME=/usr/lib/jvm/java /jvmtop/jvmtop.sh --profile 1
```

## Contacts

- Markus Brügner
  Tel. +49 7121 35-32362 | Mobile +49 173 5139212 | Markus.Bruegner@de.bosch.com

- Nicolai Splettstoesser
  Tel. +49 7121 35-35084 | Mobile +49 174 3612256 | Nicolai.Splettstoesser@de.bosch.com

- Thomas Staiger
  Tel. +49 7121 35-34813 | Thomas.Staiger2@de.bosch.com
