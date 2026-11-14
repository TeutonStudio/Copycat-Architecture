#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
version=2.2.20
cache="$PWD/.tools/kotlin-$version"
out="$PWD/src/generated"
mkdir -p "$cache" "$out"
fetch() {
    local group=$1 name=$2 version=$3 extension=$4 file partial
    file="$cache/$name-$version.$extension"
    if [[ ! -s "$file" ]]; then
        partial="$(mktemp "$cache/.download.XXXXXX")"
        curl --fail --silent --show-error --retry 2 --location \
            "https://repo.maven.apache.org/maven2/$group/$name/$version/$name-$version.$extension" -o "$partial" || { rm -f "$partial"; return 1; }
        mv "$partial" "$file"
    fi
}
pids=()
fetch org/jetbrains/kotlin kotlin-compiler-embeddable "$version" jar & pids+=("$!")
fetch org/jetbrains/kotlin kotlin-stdlib "$version" jar & pids+=("$!")
fetch org/jetbrains/kotlin kotlin-script-runtime "$version" jar & pids+=("$!")
fetch org/jetbrains/kotlin kotlin-daemon-embeddable "$version" jar & pids+=("$!")
fetch org/jetbrains/kotlin kotlin-reflect 1.6.10 jar & pids+=("$!")
fetch org/jetbrains/kotlinx kotlinx-coroutines-core-jvm 1.8.0 jar & pids+=("$!")
fetch org/jetbrains/kotlin kotlin-stdlib-js "$version" klib & pids+=("$!")
for pid in "${pids[@]}"; do wait "$pid"; done

domain=../src/main/kotlin/de/teutonstudio/copycatarchitect/domain
node prepare-engine.mjs
node generate-codec.mjs
sources=()
for file in "$domain"/*.kt; do
    case "$(basename "$file")" in GraphModels.kt|GraphPlatform.kt) continue ;; esac
    sources+=("$file")
done
sources+=(../src/main/kotlin/de/teutonstudio/copycatarchitect/client/{GraphDiagramModels,KilaGroupProjection,GroupGraphInterfaces}.kt)
sources+=(kotlin/*.kt "$out/Catalog.kt" "$out/Codec.kt")
stdlib="$cache/kotlin-stdlib-js-$version.klib"
target="$out/engine.mjs"
if [[ -f "$target" ]]; then
    rebuild=false
    for file in "${sources[@]}" "$0" "$stdlib"; do
        if [[ "$file" -nt "$target" ]]; then rebuild=true; break; fi
    done
    if [[ "$rebuild" == false ]]; then exit 0; fi
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
compiler=(java -Xmx768m -cp "$cache/*" org.jetbrains.kotlin.cli.js.K2JSCompiler)
"${compiler[@]}" -Xir-produce-klib-file -libraries "$stdlib" \
    -ir-output-dir "$work" -ir-output-name domain "${sources[@]}"
"${compiler[@]}" -Xir-produce-js -Xir-dce -module-kind es -target es2015 -main noCall \
    -libraries "$stdlib" -Xinclude="$work/domain.klib" -ir-output-dir "$work/js" -ir-output-name engine
partial="$(mktemp "$out/.engine.XXXXXX")"
cp "$work/js/engine.mjs" "$partial"
mv "$partial" "$target"
