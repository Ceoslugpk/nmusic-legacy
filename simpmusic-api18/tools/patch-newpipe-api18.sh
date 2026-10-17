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
import struct

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

def patch_jsonarray_stream(class_file):
    data = bytearray(class_file.read_bytes())
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise RuntimeError('JsonArray.class is not a JVM class file')

    cp_count = struct.unpack_from('>H', data, 8)[0]
    pos = 10
    cp = [None] * cp_count
    index = 1
    while index < cp_count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, pos)[0]
            pos += 2
            cp[index] = ('utf8', bytes(data[pos:pos + size]).decode('utf8'))
            pos += size
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[index] = ('index', struct.unpack_from('>H', data, pos)[0])
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[index] = ('pair', *struct.unpack_from('>HH', data, pos))
            pos += 4
        elif tag == 15:
            pos += 3
        else:
            raise RuntimeError('Unknown constant-pool tag %s' % tag)
        index += 1

    def utf_index(value):
        for i, item in enumerate(cp):
            if item == ('utf8', value):
                return i
        encoded = value.encode('utf8')
        cp.append(('utf8', value))
        new_index = len(cp) - 1
        additions.extend(b'\x01' + struct.pack('>H', len(encoded)) + encoded)
        return new_index

    def class_index(name):
        name_index = utf_index(name)
        cp.append(('index', name_index))
        new_index = len(cp) - 1
        additions.extend(b'\x07' + struct.pack('>H', name_index))
        return new_index

    additions = bytearray()
    collection_class = class_index('java/util/Collection')
    helper_class = class_index('org/schabi/newpipe/extractor/LegacyCollections')
    stream_name = utf_index('stream')
    helper_desc = utf_index('(Ljava/util/Collection;)Ljava/util/stream/Stream;')
    nat_index = len(cp)
    cp.append(('pair', stream_name, helper_desc))
    additions.extend(b'\x0c' + struct.pack('>HH', stream_name, helper_desc))
    helper_method = len(cp)
    cp.append(('pair', helper_class, nat_index))
    additions.extend(b'\x0a' + struct.pack('>HH', helper_class, nat_index))
    method_name = utf_index('stream')
    method_desc = utf_index('()Lj$/util/stream/Stream;')
    code_name = utf_index('Code')

    # Locate methods_count after the class header and fields.
    cursor = pos + 6
    interfaces_count = struct.unpack_from('>H', data, cursor)[0]
    cursor += 2 + interfaces_count * 2
    fields_count = struct.unpack_from('>H', data, cursor)[0]
    cursor += 2
    for _ in range(fields_count):
        attributes_count = struct.unpack_from('>H', data, cursor + 6)[0]
        cursor += 8
        for _ in range(attributes_count):
            attribute_size = struct.unpack_from('>I', data, cursor + 2)[0]
            cursor += 6 + attribute_size
    methods_count_pos = cursor
    methods_count = struct.unpack_from('>H', data, cursor)[0]
    cursor += 2
    for _ in range(methods_count):
        name = struct.unpack_from('>H', data, cursor + 2)[0]
        desc = struct.unpack_from('>H', data, cursor + 4)[0]
        attributes_count = struct.unpack_from('>H', data, cursor + 6)[0]
        if cp[name] == ('utf8', 'stream') and cp[desc] == ('utf8', '()Lj$/util/stream/Stream;'):
            raise RuntimeError('JsonArray already contains the desugared stream bridge')
        cursor += 8
        for _ in range(attributes_count):
            attribute_size = struct.unpack_from('>I', data, cursor + 2)[0]
            cursor += 6 + attribute_size

    code = b'\x2a\xc0' + struct.pack('>H', collection_class) + b'\xb8' + struct.pack('>H', helper_method) + b'\xb0'
    code_info = struct.pack('>HHI', 1, 1, len(code)) + code + struct.pack('>HH', 0, 0)
    method_info = (struct.pack('>HHHH', 0x0001, method_name, method_desc, 1)
                   + struct.pack('>HI', code_name, len(code_info)) + code_info)
    patched = (data[:8] + struct.pack('>H', len(cp)) + data[10:pos]
               + additions + data[pos:methods_count_pos]
               + struct.pack('>H', methods_count + 1) + data[methods_count_pos + 2:cursor]
               + method_info + data[cursor:])
    class_file.write_bytes(patched)

json_array_class = classes / 'com/grack/nanojson/JsonArray.class'
patch_jsonarray_stream(json_array_class)

patches = {p.relative_to(classes).as_posix(): p.read_bytes()
           for p in classes.rglob("*.class")
           if p.relative_to(classes).as_posix().startswith(("org/schabi/newpipe/extractor/", "com/grack/nanojson/"))}
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
