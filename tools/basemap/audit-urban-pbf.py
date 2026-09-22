#!/usr/bin/env python3
"""Read existing OSM PBF with stdlib only; emit explicit route/point tag evidence."""
import importlib.util
import json
import struct
import sys
import zlib
sys.dont_write_bytecode = True
from collections import Counter
from pathlib import Path

spec = importlib.util.spec_from_file_location('mvt', Path(__file__).with_name('audit-road-properties.py'))
mvt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mvt)

def packed(data):
    pos = 0
    while pos < len(data):
        value, pos = mvt.varint(data, pos)
        yield value

def audit(path):
    counts = Counter()
    samples = {}
    with open(path, 'rb') as stream:
        while prefix := stream.read(4):
            header = dict(mvt.fields(stream.read(struct.unpack('>I', prefix)[0])))
            blob = dict(mvt.fields(stream.read(header[3])))
            if header[1] != b'OSMData':
                continue
            block = list(mvt.fields(blob.get(1) or zlib.decompress(blob[3])))
            strings = [v.decode() for k, v in mvt.fields(next(v for k, v in block if k == 1))]
            for k, group in block:
                if k != 2:
                    continue
                for kind, entity in mvt.fields(group):
                    data = dict(mvt.fields(entity))
                    if kind == 2:
                        tags = {}
                        values = iter(packed(data.get(10, b'')))
                        for key in values:
                            if key == 0:
                                collect('node', tags, counts, samples)
                                tags = {}
                            else:
                                tags[strings[key]] = strings[next(values)]
                    elif kind in (1, 3, 4):
                        tags = {strings[a]: strings[b] for a, b in zip(packed(data.get(2, b'')), packed(data.get(3, b'')))}
                        collect({1: 'node', 3: 'way', 4: 'relation'}[kind], tags, counts, samples)
    print(json.dumps({'file': str(path), 'counts': dict(counts), 'samples': samples}, ensure_ascii=False, indent=2))

def collect(kind, tags, counts, samples):
    category = None
    if kind == 'relation' and tags.get('route') == 'road':
        network = tags.get('network', '')
        operator = tags.get('operator', '')
        if any(x in network + operator for x in ('首都', '名古屋', '阪神', '都市高速')):
            category = 'urban-relation'
    elif kind == 'node':
        if tags.get('highway') == 'motorway_junction': category = 'motorway_junction'
        elif tags.get('barrier') == 'toll_booth': category = 'toll_booth'
        elif tags.get('name') and (tags.get('highway') == 'traffic_signals' or tags.get('junction') == 'yes'):
            category = 'named-intersection'
    if category:
        counts[category] += 1
        values = samples.setdefault(category, [])
        if len(values) < (300 if category == 'urban-relation' else 100):
            values.append(tags)

if __name__ == '__main__':
    audit(sys.argv[1])
