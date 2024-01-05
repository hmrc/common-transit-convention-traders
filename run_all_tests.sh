#!/usr/bin/env bash
sbt clean scalafmtCheckAll coverage test it/test coverageOff coverageReport
