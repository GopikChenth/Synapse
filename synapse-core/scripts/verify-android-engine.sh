#!/usr/bin/env bash
# Fails if an Android engine binary would not run on a phone, or is stale.
# Usage: verify-android-engine.sh <jniLibs dir>
set -euo pipefail
dir="${1:?usage: verify-android-engine.sh <jniLibs dir>}"
fail=0; before=0
err() { echo "::error::$*"; fail=1; }

for abi in arm64-v8a armeabi-v7a x86_64; do
  before=$fail; fail=0
  f="$dir/$abi/libsynapse.so"
  [ -f "$f" ] || { err "$f is missing"; [ "$before" -eq 1 ] && fail=1; continue; }
  hdr=$(readelf -hW "$f")
  prog=$(readelf -lW "$f")

  # Android runs the engine as a program (ProcessBuilder), so it must be a PIE
  # executable that uses Android's loader, not a shared library or a glibc binary.
  grep -q 'Type:.*DYN' <<<"$hdr" || err "$abi: not a position-independent executable"
  grep -q 'program interpreter: /system/bin/linker' <<<"$prog" || err "$abi: does not use Android's loader (/system/bin/linker*); the app cannot start it"

  case "$abi" in
    arm64-v8a)   want='AArch64' ;;
    armeabi-v7a) want='ARM' ;;
    x86_64)      want='X86-64' ;;
  esac
  grep -q "Machine:.*$want" <<<"$hdr" || err "$abi: wrong CPU type ($(grep Machine <<<"$hdr" | xargs))"

  # 64-bit Android devices may use 16 KB pages (Android 15+); a binary whose
  # segments are aligned for 4 KB pages is refused at exec time.
  if [ "$abi" != armeabi-v7a ]; then
    while read -r align; do
      if [ $((align)) -lt 16384 ]; then
        err "$abi: LOAD segment aligned to $align (< 16 KB); it will not start on 16 KB-page phones"
        break
      fi
    done < <(awk '$1=="LOAD"{print $NF}' <<<"$prog")
  fi

  # Guards against packaging an engine built from an old copy of the source.
  grep -qaF 'synapse-quic (mTLS)' "$f" || err "$abi: not built from the current engine source (mutual-TLS peer code missing)"

  [ "$fail" -eq 0 ] && echo "$abi: OK ($(stat -c %s "$f") bytes)"
  [ "$before" -eq 1 ] && fail=1
done
exit "$fail"
