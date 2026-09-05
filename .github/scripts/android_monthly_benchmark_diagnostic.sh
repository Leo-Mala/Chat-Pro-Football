#!/usr/bin/env bash
set -euo pipefail

mkdir -p android-monthly-benchmark-artifacts

target_apk=$(find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print -quit)
test_apk=$(find app/build/outputs/apk/androidTest/debug -type f -name '*-androidTest.apk' -print -quit)
test -n "$target_apk" && test -n "$test_apk"

adb install -r "$target_apk"
adb install -r "$test_apk"
adb logcat -c

set +e
adb shell am instrument -w -r \
  -e class com.example.usecase.MonthlyCommitPerformanceAndroidBenchmarkTest \
  com.aistudio.brasfutretro.djuxzt.test/androidx.test.runner.AndroidJUnitRunner \
  | tee android-monthly-benchmark-artifacts/instrumentation.txt
instrument_status=${PIPESTATUS[0]}
set -e

adb logcat -d -v threadtime \
  | grep -E 'MonthlyCommitBenchmark|PERF_ANDROID_MONTHLY_' \
  | tee android-monthly-benchmark-artifacts/logcat.txt || true

echo "INSTRUMENT_STATUS=${instrument_status}" >> android-monthly-benchmark-artifacts/environment.txt

test "$instrument_status" -eq 0
grep -q '^INSTRUMENTATION_CODE: -1' android-monthly-benchmark-artifacts/instrumentation.txt
grep -Eq '^OK \([0-9]+ tests?\)$' android-monthly-benchmark-artifacts/instrumentation.txt
if grep -q '^FAILURES!!!' android-monthly-benchmark-artifacts/instrumentation.txt; then
  echo "JUnit instrumentation reported failures." >&2
  exit 1
fi
grep -q 'PERF_ANDROID_MONTHLY_COMMIT_STAGES' android-monthly-benchmark-artifacts/logcat.txt
