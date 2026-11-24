#!/bin/bash
# after_script: keep the job open until Travis has archived this job's log up
# to the test totals, so a harvester reading the log right after the build
# finishes sees the complete test output. Cannot change the build result.
LEG="$1"
URL="https://api.travis-ci.com/v3/job/${TRAVIS_JOB_ID}/log.txt"
CAP=1800
waited=0
while [ "$waited" -lt "$CAP" ]; do
  if curl -fsSL "$URL" 2>/dev/null | grep -Eq 'SUREFIRE TOTALS: tests=[0-9]'; then
    echo "log for $LEG archived through the test totals after ${waited}s"
    exit 0
  fi
  echo "waiting for $LEG log archive... (${waited}s)"
  sleep 20
  waited=$((waited + 20))
done
echo "gave up waiting for $LEG log archive after ${CAP}s"
exit 0
