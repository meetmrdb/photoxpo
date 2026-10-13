#!/bin/bash
# Prints the text on the phone's current screen, one line each. Far cheaper than a screenshot for
# reading Home's headline, counts and warnings. Usage: scripts/home-state.sh [adb-serial]
set -eu
serial="${1:-emulator-5554}"
adb -s "$serial" shell uiautomator dump /sdcard/home-state.xml >/dev/null
adb -s "$serial" shell cat /sdcard/home-state.xml \
  | grep -o 'text="[^"]\+"' | sed 's/^text="//; s/"$//'
