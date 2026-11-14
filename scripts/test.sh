#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/classes
java com.sun.tools.javac.Main -source 21 -target 21 -d build/classes src/main/java/dev/handoffguard/*.java src/test/java/dev/handoffguard/*.java
java -cp build/classes dev.handoffguard.EngineTest
java -cp build/classes dev.handoffguard.AcceptanceVerifierTest
java -cp build/classes dev.handoffguard.SalesforceVerifierRegressionTest
