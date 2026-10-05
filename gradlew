#!/usr/bin/env sh

# Gradle startup script for POSIX
APP_BASE_NAME=$(basename "$0")
DIRNAME=$(dirname "$0")

# Use location of this script as standard working directory
APP_HOME=$(cd "$DIRNAME" && pwd -P)

JAVACMD="java"
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
fi

# Locate gradle-wrapper.jar or fallback to gradle action
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" "-classpath" "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
