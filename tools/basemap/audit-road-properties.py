#!/usr/bin/env python3
"""Read-only sample of MVT road attributes (no third-party dependencies)."""
import gzip
import json
import math
import sqlite3
import struct
import sys
from pathlib import Path


def varint(data, pos):
    value = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
        shift += 7


def fields(data):
    pos = 0
    while pos < len(data):
        tag, pos = varint(data, pos)
        wire = tag & 7
        if wire == 0:
            value, pos = varint(data, pos)
        elif wire == 2:
            length, pos = varint(data, pos)
            value = data[pos:pos + length]
            pos += length
        elif wire in (1, 5):
            length = 8 if wire == 1 else 4
            value = data[pos:pos + length]
            pos += length
        else:
            raise ValueError(wire)
        yield tag >> 3, value


def decode(data):
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    result = {}
    for tag, layer in fields(data):
        if tag != 3:
            continue
        items = list(fields(layer))
        name = next(v.decode() for k, v in items if k == 1)
        keys = [v.decode() for k, v in items if k == 3]
        values = []
        for k, v in items:
            if k == 4:
                kind, value = next(fields(v))
                if kind == 1:
                    value = value.decode()
                elif kind == 6:
                    value = (value >> 1) ^ -(value & 1)
                elif kind in (2, 3):
                    value = struct.unpack('<f' if kind == 2 else '<d', value)[0]
                values.append(value)
        features = []
        for k, v in items:
            if k != 2:
                continue
            props = {}
            for attr, packed in fields(v):
                if attr == 2:
                    pos = 0
                    while pos < len(packed):
                        key, pos = varint(packed, pos)
                        val, pos = varint(packed, pos)
                        props[keys[key]] = values[val]
            features.append(props)
        result[name] = features
    return result


def audit(path):
    db = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)
    metadata = dict(db.execute('select name,value from metadata'))
    print(json.dumps({'file': path.name, 'version': metadata.get('version'),
                      'layers': [x for x in json.loads(metadata['json'])['vector_layers']
                                 if x['id'].startswith(('transportation', 'busnav_'))]}, ensure_ascii=False))
    sites = [('shibuya-246', 35.658, 139.701), ('tomei', 35.625, 139.615),
             ('hachioji', 35.660, 139.310), ('ken-o', 35.650, 139.250),
             ('miyakezaka', 35.678, 139.742), ('tanimachi', 35.668, 139.741),
             ('takebashi', 35.692, 139.754), ('hakozaki', 35.681, 139.787),
             ('ohashi', 35.651, 139.689), ('bayshore', 35.632, 139.791),
             ('kanagawa', 35.469, 139.629), ('kawaguchi', 35.821, 139.734)]
    if path.stem == 'chubu':
        sites = [('nagoya', 35.17, 136.90), ('gifu', 35.42, 136.76),
                 ('shizuoka', 34.97, 138.39), ('kofu', 35.66, 138.57)]
    for site, lat, lon in sites:
        z = 14
        x = int((lon + 180) / 360 * 2**z)
        y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * 2**z)
        row = db.execute('select tile_data from tiles where zoom_level=? and tile_column=? and tile_row=?',
                         (z, x, 2**z - 1 - y)).fetchone()
        if not row:
            continue
        for layer, features in decode(row[0]).items():
            if not layer.startswith(('transportation', 'busnav_')):
                continue
            unique = {json.dumps({k: v for k, v in p.items() if k in ('class', 'subclass', 'ref', 'network', 'network_type', 'route_network', 'route_ref', 'route_1_network', 'route_1_ref', 'route_2_network', 'route_2_ref', 'route_source_network', 'route_operator', 'facility_type', 'name', 'rank', 'signalized', 'major_way_count', 'operator', 'toll')}, ensure_ascii=False, sort_keys=True) for p in features
                      if layer.startswith('busnav_') or p.get('class') in ('motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor', 'service')}
            print(json.dumps({'site': site, 'tile': [z, x, y], 'layer': layer,
                              'properties': [json.loads(p) for p in sorted(unique)][:65]}, ensure_ascii=False))


if __name__ == '__main__':
    audit(Path(sys.argv[1]))
