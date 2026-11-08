#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$script_dir/../../.." && pwd)
gradle_cache_dir="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
stdlib_jar=$(find "$gradle_cache_dir/org.jetbrains.kotlin/kotlin-stdlib/2.2.20" -name 'kotlin-stdlib-2.2.20.jar' -print -quit)
gson_jar=$(find "$gradle_cache_dir/com.google.code.gson/gson/2.10.1" -name 'gson-2.10.1.jar' -print -quit)
classes_dir="$repo_dir/build/classes/kotlin/main"
if [[ ! -f "$classes_dir/de/teutonstudio/copycatarchitect/domain/BuildingGraphCompiler.class" ]]; then
  echo 'Compile the mod classes first: ./gradlew compileKotlin --offline' >&2
  exit 1
fi
build_dir=$(mktemp -d /tmp/quartier-compiler-XXXXXX)
trap 'rm -rf -- "$build_dir"' EXIT
classpath="$classes_dir:$stdlib_jar:$gson_jar"
javac -cp "$classpath" -d "$build_dir" "$script_dir/CompileQuartier.java"
java -Xmx3g -cp "$build_dir:$classpath" CompileQuartier "$script_dir/.."
