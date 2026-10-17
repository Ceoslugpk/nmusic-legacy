#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app_dir="$(cd "$script_dir/.." && pwd)"
repo_dir="$(cd "$app_dir/.." && pwd)"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
gradle_cache="${GRADLE_USER_HOME:-$HOME/.gradle}"
android_jar="$sdk_root/platforms/android-35/android.jar"
extractor_jar="$app_dir/app/libs/newpipe-extractor-0.26.1.jar"

if [[ -z "$sdk_root" || ! -f "$android_jar" ]]; then
    echo "Set ANDROID_HOME to an SDK containing platforms/android-35." >&2
    exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

python3 - "$repo_dir" "$app_dir" "$gradle_cache" "$android_jar" "$extractor_jar" "$tmp_dir" <<'PY'
from pathlib import Path
import subprocess
import sys
import zipfile

repo, app, cache, android_jar, extractor_jar, tmp = map(Path, sys.argv[1:])
source_root = app / "third_party/NewPipeExtractor/java"
nano_root = app / "third_party/nanojson/java"
patched_sources = [p for p in source_root.rglob("*.java")
                   if "org.schabi.newpipe.extractor.LegacyCollections." in p.read_text()]
helper = source_root / "org/schabi/newpipe/extractor/LegacyCollections.java"
nano_sources = list(nano_root.rglob("*.java"))
classpath = [str(extractor_jar), str(android_jar)]
classpath.extend(str(p) for p in cache.rglob("*.jar"))
classes = tmp / "classes"
classes.mkdir(parents=True)
cmd = ["java", "--module", "jdk.compiler/com.sun.tools.javac.Main",
       "-source", "17", "-target", "17", "-sourcepath", "",
       "-classpath", ":".join(classpath), "-d", str(classes), str(helper)]
cmd.extend(str(p) for p in nano_sources)
cmd.extend(str(p) for p in patched_sources)
subprocess.run(cmd, cwd=app, check=True)

patches = {p.relative_to(classes).as_posix(): p.read_bytes()
           for p in classes.rglob("*.class")
           if p.relative_to(classes).as_posix().startswith("org/schabi/newpipe/extractor/")}
replacement = extractor_jar.with_name(extractor_jar.name + ".api18.tmp")
with zipfile.ZipFile(extractor_jar) as original, zipfile.ZipFile(replacement, "w", zipfile.ZIP_DEFLATED) as output:
    for entry in original.infolist():
        if entry.filename not in patches:
            output.writestr(entry, original.read(entry.filename))
    for name, data in patches.items():
        output.writestr(name, data)
replacement.replace(extractor_jar)
print("Patched", len(patches), "NewPipe classes for Android API 18")
PY
