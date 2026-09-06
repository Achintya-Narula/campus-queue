#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
classes_dir="$project_dir/build/test-classes"
source_list="$project_dir/build/java-sources.txt"

mkdir -p "$classes_dir"
find "$project_dir/src/main/java" "$project_dir/src/test/java" -name '*.java' -print | sort > "$source_list"

if command -v javac >/dev/null 2>&1; then
  javac --add-modules jdk.httpserver -d "$classes_dir" @"$source_list"
  java_command=(java)
else
  jdk_root="${JDK_ROOT:-/usr/lib/jvm/java-17-openjdk-amd64}"
  export LD_LIBRARY_PATH="$jdk_root/lib:$jdk_root/lib/server"
  "$jdk_root/bin/java" -m jdk.compiler/com.sun.tools.javac.Main \
    --add-modules jdk.httpserver -d "$classes_dir" @"$source_list"
  java_command=("$jdk_root/bin/java")
fi

"${java_command[@]}" --add-modules jdk.httpserver -cp "$classes_dir" dev.achu.campusqueue.JsonTest
"${java_command[@]}" --add-modules jdk.httpserver -cp "$classes_dir" dev.achu.campusqueue.CampusQueueServiceTest
"${java_command[@]}" --add-modules jdk.httpserver -cp "$classes_dir" dev.achu.campusqueue.CampusQueueHttpServerTest
