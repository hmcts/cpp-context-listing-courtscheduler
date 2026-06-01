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
# $JAVA_OPTS is supplied by the deployment (Helm values -> container env). It is
# intentionally unquoted so each space-separated flag becomes its own argument.
# exec replaces the shell so the JVM is PID 1 and receives SIGTERM directly for
# graceful shutdown.
if [ -f "$DOCKERJARFILE" ]; then
    logmsg "Running docker java jarfile $DOCKERJARFILE"
    exec java $JAVA_OPTS -jar "$DOCKERJARFILE"
elif [ -f "$LOCALJARFILE" ]; then
    logmsg "Running local java jarfile $LOCALJARFILE"
    exec java $JAVA_OPTS -jar "$LOCALJARFILE"
else
    logmsg "ERROR - No jarfile found. Unable to start application"
    exit 1
fi
