#!/bin/sh
# Builds the Android app. All tools live in ../.tools so nothing lands in the home folder.
set -e
cd "$(dirname "$0")"
TOOLS="$(cd ../.tools && pwd)"
export JAVA_HOME="$TOOLS/jdk"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_USER_HOME="$TOOLS/android-user"
./gradlew :androidApp:assembleDebug "$@"
cp androidApp/build/outputs/apk/debug/androidApp-debug.apk ../WG-Hub.apk
echo "App: $(cd .. && pwd)/WG-Hub.apk"
