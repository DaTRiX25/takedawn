#!/usr/bin/env bash
# Cross-build burnout3.exe (Windows x86-64, SDL2 + Vulkan) from macOS or Linux
# with llvm-mingw. Run tools/win/fetch-deps.sh once first.
#
#   tools/win/build-mingw.sh [extra cmake -D options]
#
# Environment: BO3_WINDEPS (deps prefix, default ~/.cache/bo3-windeps),
# XBOXRECOMP_DIR (toolkit checkout, default ../xboxrecomp-win next to the repo
# or ../xboxrecomp), BUILD_DIR (default build-mingw), JOBS.
# Output: bin/burnout3.exe, and SDL2.dll beside it.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
D="${BO3_WINDEPS:-$HOME/.cache/bo3-windeps}"
TK="${XBOXRECOMP_DIR:-}"
if [ -z "$TK" ]; then
  for c in "$HERE/../xboxrecomp-win" "$HERE/../xboxrecomp"; do [ -d "$c" ] && TK="$c" && break; done
fi
[ -d "$TK" ] || { echo "toolkit checkout not found; set XBOXRECOMP_DIR" >&2; exit 1; }
[ -x "$D/llvm-mingw/bin/x86_64-w64-mingw32-clang" ] || "$HERE/tools/win/fetch-deps.sh"
B="${BUILD_DIR:-$HERE/build-mingw}"

cmake -S "$HERE" -B "$B" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$HERE/cmake/llvm-mingw-x86_64.cmake" \
  -DLLVM_MINGW_ROOT="$D/llvm-mingw" \
  -DXBOXRECOMP_DIR="$TK" \
  -DCMAKE_PREFIX_PATH="$D/SDL2/x86_64-w64-mingw32;$D/glslang" \
  -DVulkan_INCLUDE_DIR="$D/vulkan/include" \
  -DVulkan_LIBRARY="$D/vulkan/lib/libvulkan-1.dll.a" \
  "$@"
cmake --build "$B" --target burnout3 -j"${JOBS:-$(sysctl -n hw.ncpu 2>/dev/null || nproc)}"
cp -f "$D/SDL2/x86_64-w64-mingw32/bin/SDL2.dll" "$HERE/bin/"
ls -l "$HERE/bin/burnout3.exe"
