Kie-benchmarks repository [![PR Check Status Badge](https://github.com/kiegroup/kie-benchmarks/actions/workflows/pull_request.yml/badge.svg)](https://github.com/kiegroup/kie-benchmarks/actions/workflows/pull_request.yml)
=========================
This repository contains benchmarks from various areas of Kie group projects.  

For more information about Drools benchmarks, see [Drools benchmarks README](https://github.com/kiegroup/kie-benchmarks/tree/master/drools-benchmarks).


Proposed CEP Benchmarks
--------------
This repository includes three new Complex Event Processing (CEP) benchmark suites developed for performance evaluation of the Drools rule engine:

| Benchmark | Directory | Description |
|---|---|---|
| **Wikimedia** | `drools-benchmarks-parent/drools-benchmarks-cep-wikimedia` | Benchmarks real-time Wikipedia edit events, testing content-moderation rules with temporal sliding windows. |
| **Binance** | `drools-benchmarks-parent/drools-benchmarks-binance-cep` | Benchmarks high-frequency crypto trade events from Binance, evaluating price-anomaly and volume-spike detection rules. |
| **OpenSky** | `drools-benchmarks-parent/drools-benchmarks-cep-opensky` | Benchmarks ADS-B air traffic events from the OpenSky Network, testing geospatial proximity and airspace-violation rules. |


Developing Drools and jBPM
==========================

**If you want to build or contribute to a droolsjbpm project, [read this document](https://github.com/droolsjbpm/droolsjbpm-build-bootstrap/blob/master/README.md).**

**It will save you and us a lot of time by setting up your development environment correctly.**
It solves all known pitfalls that can disrupt your development.
It also describes all guidelines, tips and tricks.
If you want your pull requests (or patches) to be merged into master, please respect those guidelines.
