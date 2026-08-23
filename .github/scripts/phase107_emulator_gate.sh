#!/usr/bin/env bash
set -euo pipefail

mode="${1:?mode required}"
api_label="${2:?api label required}"
TARGET_PACKAGE="${TARGET_PACKAGE:?}"
TEST_PACKAGE="${TEST_PACKAGE:?}"
TEST_RUNNER="${TEST_RUNNER:?}"
artifact_dir="phase107-artifacts/${api_label}"
mkdir -p "$artifact_dir"

collect_evidence() {
  adb logcat -d > "$artifact_dir/logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$artifact_dir/activities.txt" 2>/dev/null || true
  adb shell getprop ro.build.version.sdk > "$artifact_dir/api-level.txt" 2>/dev/null || true
  adb shell pm list instrumentation > "$artifact_dir/instrumentation-list.txt" 2>/dev/null || true
}
trap collect_evidence EXIT

run_test() {
  local class_name="$1"
  local label="$2"
  local output
  output="$(adb shell am instrument -w -r -e class "$class_name" "$TEST_PACKAGE/$TEST_RUNNER")"
  printf '\n===== %s =====\n%s\n' "$label" "$output" | tee -a "$artifact_dir/instrumentation.txt"
  printf '%s\n' "$output" | grep -q '^OK ('
}

install_pair() {
  local variant="$1"
  local app_apk test_apk
  app_apk="$(find "app/build/outputs/apk/$variant" -maxdepth 1 -name '*.apk' -print -quit)"
  test_apk="$(find "app/build/outputs/apk/androidTest/$variant" -type f -name '*-androidTest.apk' -print -quit)"
  test -n "$app_apk"
  test -n "$test_apk"
  printf 'app_apk=%s\ntest_apk=%s\n' "$app_apk" "$test_apk" > "$artifact_dir/apks.txt"
  adb wait-for-device
  adb install -r "$app_apk"
  adb install -r "$test_apk"
}

certify_installed_startup() {
  local label="$1"
  local start_output pid
  start_output="$(adb shell am start -W -n "$TARGET_PACKAGE/com.example.MainActivity")"
  printf '%s\n' "$start_output" | tee "$artifact_dir/${label}-direct-start.txt"
  printf '%s\n' "$start_output" | grep -q 'Status: ok'

  pid="$(adb shell pidof "$TARGET_PACKAGE" | tr -d '\r')"
  test -n "$pid"
  printf '%s\n' "$pid" > "$artifact_dir/${label}-pid.txt"

  adb shell dumpsys activity activities > "$artifact_dir/${label}-activities.txt"
  grep -q 'com.example.MainActivity' "$artifact_dir/${label}-activities.txt"
}

stop_installed_app() {
  adb shell am force-stop "$TARGET_PACKAGE"
  sleep 2
  test -z "$(adb shell pidof "$TARGET_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
}

compile_debug_target_for_instrumentation() {
  # Debug cold startup is certified before this optimization. The full instrumentation process is
  # then AOT-compiled so ART verifier/JIT pauses from the very large Compose graph cannot be
  # misclassified as save-session deadlocks or input ANRs by ActivityScenario.
  adb shell cmd package compile -m speed -f "$TARGET_PACKAGE" \
    | tee "$artifact_dir/target-package-compile.txt"
  adb shell cmd package compile -m speed -f "$TEST_PACKAGE" \
    | tee "$artifact_dir/test-package-compile.txt"
}

verify_new_app_process() {
  local old_pid="$1"
  local label="$2"
  local start_output new_pid
  start_output="$(adb shell am start -W -n "$TARGET_PACKAGE/com.example.MainActivity")"
  printf '%s\n' "$start_output" > "$artifact_dir/${label}-direct-start.txt"
  printf '%s\n' "$start_output" | grep -q 'Status: ok'
  new_pid="$(adb shell pidof "$TARGET_PACKAGE" | tr -d '\r')"
  test -n "$new_pid"
  test "$new_pid" != "$old_pid"
  printf 'old_pid=%s\nnew_pid=%s\n' "$old_pid" "$new_pid" > "$artifact_dir/process-restart.txt"
}

case "$mode" in
  smoke)
    install_pair debug
    adb shell pm clear "$TARGET_PACKAGE"
    certify_installed_startup 'debug-cold-startup'
    stop_installed_app
    adb shell pm clear "$TARGET_PACKAGE"
    # MainActivity startup is certified above by ActivityManager itself. This instrumentation
    # assertion intentionally avoids ActivityScenario's legacy "main looper must become idle"
    # heuristic, which is not reliable for a continuously scheduled Compose frame clock on API 24.
    run_test 'com.example.Phase107StartupLifecycleInstrumentedTest#productionApplicationHiltAndRoomGraphResolveOnRealAndroid' 'installed-hilt-room-graph'
    ;;
  debug-full)
    install_pair debug
    adb shell pm clear "$TARGET_PACKAGE"
    certify_installed_startup 'debug-cold-startup'
    stop_installed_app
    compile_debug_target_for_instrumentation
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107StartupLifecycleInstrumentedTest' 'startup-lifecycle'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107ComposeNavigationInstrumentedTest#mainMenuSavesAndCriticalActionsExposeRealComposeSemantics' 'compose-semantics-navigation'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107ComposeNavigationInstrumentedTest#createsAndReopensARealCareerThroughTheInstalledUi' 'real-ui-new-save-reopen'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107PersistenceInstrumentedTest' 'file-backed-room-recovery-isolation'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107MigrationInstrumentedTest' 'room-migration-21-22'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107ProcessRestartSeedInstrumentedTest#seedCanonicalCareerWithoutMetadataForExternalProcessRestart' 'process-restart-seed-without-metadata'
    adb shell am start -W -n "$TARGET_PACKAGE/com.example.MainActivity" | tee "$artifact_dir/pre-kill-start.txt"
    old_pid="$(adb shell pidof "$TARGET_PACKAGE" | tr -d '\r')"
    test -n "$old_pid"
    printf '%s\n' "$old_pid" > "$artifact_dir/pre-kill-pid.txt"
    stop_installed_app
    run_test 'com.example.Phase107ProcessRestartUiInstrumentedTest#recoveredCareerSurvivesExternalForceStopAndOpensThroughUi' 'process-restart-recovery-ui'
    verify_new_app_process "$old_pid" 'post-recovery'
    ;;
  release)
    install_pair release
    adb shell pm clear "$TARGET_PACKAGE"
    certify_installed_startup 'direct-release-start'
    stop_installed_app
    run_test 'com.example.Phase107StartupLifecycleInstrumentedTest#productionApplicationHiltAndMainActivityStartOnRealAndroid' 'release-startup-hilt-room'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107PersistenceInstrumentedTest#fileBackedRoomPersistsReopensAndUsesCurrentSchema' 'release-room-save-reopen'
    adb shell pm clear "$TARGET_PACKAGE"
    run_test 'com.example.Phase107ProcessRestartSeedInstrumentedTest#seedCanonicalCareerWithoutMetadataForExternalProcessRestart' 'release-process-seed'
    adb shell am start -W -n "$TARGET_PACKAGE/com.example.MainActivity" > "$artifact_dir/pre-kill-start.txt"
    old_pid="$(adb shell pidof "$TARGET_PACKAGE" | tr -d '\r')"
    test -n "$old_pid"
    stop_installed_app
    run_test 'com.example.Phase107ProcessRestartUiInstrumentedTest#recoveredCareerSurvivesExternalForceStopAndOpensThroughUi' 'release-process-restart-recovery'
    verify_new_app_process "$old_pid" 'post-recovery-release'
    ;;
  *)
    echo "unknown mode: $mode" >&2
    exit 64
    ;;
esac
