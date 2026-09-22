#!/usr/bin/env python3
"""Idempotently apply the BusNav Japanese-road presentation to the two OMT templates."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
PALETTES = {'busnav-light.json': ('#397BB8', '#D16D61', '#50956C'),
            'busnav.json': ('#629AD0', '#D7857D', '#71AD86')}


def format_json(value, indent=0):
    pad = ' ' * indent
    if isinstance(value, dict):
        return '{\n' + ',\n'.join(' ' * (indent + 2) + json.dumps(k) + ': ' + format_json(v, indent + 2)
                                 for k, v in value.items()) + '\n' + pad + '}'
    if isinstance(value, list) and any(isinstance(v, dict) for v in value):
        return '[\n' + ',\n'.join(' ' * (indent + 2) + format_json(v, indent + 2) for v in value) + '\n' + pad + ']'
    return json.dumps(value, ensure_ascii=False)


def update(path, palette):
    style = json.loads(path.read_text(encoding='utf-8'))
    style['metadata']['busnav:road-schema'] = 1
    layers = [x for x in style['layers'] if not x['id'].startswith('route-shield-')]
    for layer in layers:
        if (layer['id'].startswith('roads-') and not layer['id'].endswith('-casing')) or layer['id'] in ('road-tunnels', 'road-bridges'):
            old = layer['paint']['line-color']
            if isinstance(old, list) and old[:2] == ['match', ['get', 'route_network']]:
                old = old[-1]
            # Unknown motorway/major roads retain a neutral width hierarchy, never a category color.
            if layer['id'] == 'roads-motorway':
                old = '#d3c7ae' if 'light' in path.name else '#777c82'
            layer['paint']['line-color'] = ['match', ['get', 'route_network'],
                'expressway', palette[0], 'national', palette[1], 'prefectural', palette[2], old]
        if layer['id'] == 'road-labels':
            layer['filter'] = ['in', ['get', 'class'], ['literal', ['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor', 'service']]]
            layer['layout']['text-field'] = ['coalesce', ['get', 'name:ja'], ['get', 'name'], '']
            layer['layout']['text-allow-overlap'] = False
            layer['layout']['text-ignore-placement'] = False
    layers = [x for x in layers if x['id'] != 'motorway-refs']
    shields = []
    for rank, (kind, zoom, spacing) in enumerate([('expressway', 7, 420), ('national', 8, 550), ('prefectural', 13, 680)]):
        icon = 'jp-' + kind
        if kind == 'expressway':
            icon = ['case', ['>', ['length', ['get', 'route_ref']], 3], 'jp-expressway-wide', 'jp-expressway']
        filters = ['all', ['==', ['geometry-type'], 'LineString'],
                   ['==', ['get', 'route_network'], kind], ['has', 'route_ref']]
        if kind == 'national':
            # Only single/double-digit national routes at overview zooms; all at z10+.
            filters.append(['any', ['>=', ['zoom'], 10], ['<=', ['length', ['get', 'route_ref']], 2]])
        shields.append({'id': 'route-shield-' + kind, 'type': 'symbol', 'source': 'openmaptiles',
            'source-layer': 'transportation_name', 'minzoom': zoom, 'filter': filters,
            'layout': {'symbol-placement': 'line', 'symbol-spacing': spacing, 'symbol-sort-key': rank,
                'icon-image': icon, 'icon-size': 1, 'icon-anchor': 'center',
                'icon-rotation-alignment': 'viewport', 'icon-pitch-alignment': 'viewport',
                'icon-allow-overlap': False, 'icon-ignore-placement': False, 'icon-optional': False,
                'icon-padding': 8, 'text-field': ['get', 'route_ref'],
                'text-font': ['Klokantech Noto Sans CJK Regular'], 'text-size': 13,
                'text-anchor': 'center', 'text-justify': 'center',
                'text-offset': [0, -0.12] if kind == 'national' else [0, 0],
                'text-rotation-alignment': 'viewport', 'text-pitch-alignment': 'viewport',
                'text-allow-overlap': False, 'text-ignore-placement': False, 'text-optional': False,
                'text-padding': 8}, 'paint': {'text-color': '#ffffff'}})
    insertion = next(i for i, x in enumerate(layers) if x['id'] == 'road-labels') + 1
    layers[insertion:insertion] = shields
    style['layers'] = layers
    path.write_text(format_json(style) + '\n', encoding='utf-8')


if __name__ == '__main__':
    for filename, palette in PALETTES.items():
        update(ROOT / 'style' / filename, palette)
