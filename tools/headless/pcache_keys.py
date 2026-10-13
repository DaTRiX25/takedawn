#!/usr/bin/env python3
"""pcache_keys.py <key list> [<key list>]

Reads a backend's key list (metal_pipelines.bin, vk_pipelines.bin: the
toolkit's src/d3d/nv2a_pcache.h) and prints how many keys of each kind it
holds. With two lists: the keys only one of them has, by kind and by the
record's own 64-bit check (the keys themselves are the title's data and are
not printed), and exit status 1 when the two sets differ.

For comparing what two shader pre-builds recorded (docs/shader-prebuild.md).
"""
import struct
import sys

KIND = {0: 'vertex', 1: 'fragment', 2: 'pipeline'}


def read(path):
    b = open(path, 'rb').read()
    if len(b) < 16 or b[:8] != b'XRPCACHE':
        raise SystemExit(f'{path}: not a key list')
    fmt, n = struct.unpack_from('<II', b, 8)
    version = b[16:16 + n].decode('ascii', 'replace')
    off, keys = 16 + n, {}
    while off + 16 <= len(b):
        kind, ln, h = struct.unpack_from('<IIQ', b, off)
        if off + 16 + ln > len(b):
            break
        keys[(kind, h)] = ln
        off += 16 + ln
    return version, keys


def counts(keys):
    c = {}
    for kind, _ in keys:
        c[kind] = c.get(kind, 0) + 1
    return ', '.join(f'{c.get(k, 0)} {KIND.get(k, k)}' for k in sorted(set(c) | set(KIND)))


def main(argv):
    if len(argv) not in (1, 2):
        print(__doc__)
        return 2
    lists = [read(p) for p in argv]
    for p, (version, keys) in zip(argv, lists):
        print(f'{p}: {counts(keys)} [{version}]')
    if len(lists) == 1:
        return 0
    a, b = lists[0][1], lists[1][1]
    if lists[0][0] != lists[1][0]:
        print('the two lists are of different key formats')
        return 1
    only_a, only_b = sorted(set(a) - set(b)), sorted(set(b) - set(a))
    for name, only, src in (('first', only_a, a), ('second', only_b, b)):
        for kind, h in only:
            print(f'only in the {name}: {KIND.get(kind, kind)} {h:016x} ({src[(kind, h)]} bytes)')
    print(f'{len(set(a) & set(b))} keys in both, {len(only_a)} only in the first, '
          f'{len(only_b)} only in the second')
    return 1 if only_a or only_b else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
