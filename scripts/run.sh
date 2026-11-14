#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
while [ "$#" -gt 0 ]; do
  case "$1" in
    --port) PORT="$2"; export PORT; shift 2 ;;
    --host) HOST="$2"; export HOST; shift 2 ;;
    --strictPort) shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
mkdir -p build/classes
java com.sun.tools.javac.Main -source 21 -target 21 -d build/classes src/main/java/dev/handoffguard/*.java
exec java -cp build/classes dev.handoffguard.Server
