#!/usr/bin/env sh
# Container entrypoint for the Spring Boot fat jar published by this repo.
# Looks for the jar in /app (when running in the prod container) and falls back
# to ./build/libs (when shelling into the image from a local checkout).
logmsg() {
    SCRIPTNAME=$(basename $0)
    echo "$SCRIPTNAME : $1"
}

export LOCALJARFILE=$(ls ./build/libs/*.jar 2>/dev/null | grep -v 'plain' | head -n1)
export DOCKERJARFILE=$(ls /app/*.jar 2>/dev/null | grep -v 'plain' | head -n1)
if [ -f "$DOCKERJARFILE" ]; then
    logmsg "Running docker java jarfile $DOCKERJARFILE"
    java -jar "$DOCKERJARFILE"
elif [ -f "$LOCALJARFILE" ]; then
    logmsg "Running local java jarfile $LOCALJARFILE"
    java -jar "$LOCALJARFILE"
else
    logmsg "ERROR - No jarfile found. Unable to start application"
    exit 1
fi
