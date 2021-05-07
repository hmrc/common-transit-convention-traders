#!/usr/bin/env bash
sbt clean coverage test it:test coverageOff coverageReport
