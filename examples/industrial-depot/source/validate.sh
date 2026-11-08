#!/usr/bin/env bash
set -euo pipefail
package_dir="$(cd "$(dirname "$0")/.." && pwd)"
project_dir="$(cd "$package_dir/../.." && pwd)"
if [[ ! -f "$project_dir/build/classes/kotlin/main/de/teutonstudio/copycatarchitect/domain/BuildingGraphCompiler.class" ]]; then
  (cd "$project_dir" && ./gradlew compileKotlin --offline)
fi
class_dir="$package_dir/.validation-classes"
mkdir -p "$class_dir"
classpath="$project_dir/build/classes/kotlin/main"
for spec in 'org.jetbrains.kotlin/kotlin-stdlib/2.2.20' 'com.google.code.gson/gson/2.10.1'; do
  jar="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/$spec" -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit)"
  if [[ -z "$jar" ]]; then echo "Missing cached dependency: $spec" >&2; exit 1; fi
  classpath="$classpath:$jar"
done
javac -cp "$classpath" -d "$class_dir" "$package_dir/source/ValidateBuilding.java"
java -Xmx6g -cp "$class_dir:$classpath" ValidateBuilding "$package_dir/library.json"
