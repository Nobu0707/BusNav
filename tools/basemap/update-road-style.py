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


def detail_layers(light):
    common = {'visibility': 'none', 'symbol-placement': 'point',
        'icon-rotation-alignment': 'viewport', 'icon-pitch-alignment': 'viewport',
        'text-rotation-alignment': 'viewport', 'text-pitch-alignment': 'viewport',
        'icon-allow-overlap': False, 'text-allow-overlap': False,
        'icon-ignore-placement': False, 'text-ignore-placement': False,
        'icon-optional': False, 'text-optional': False, 'icon-padding': 2, 'text-padding': 3}
    label = {'text-field': ['get', 'name'], 'text-font': ['Klokantech Noto Sans CJK Regular'],
        'text-size': 11, 'text-max-width': 16, 'icon-text-fit': 'both', 'icon-text-fit-padding': [3, 5, 3, 5]}
    result = []
    for name, rank in [('major', 0), ('normal', 1)]:
        result.append({'id': 'intersection-' + name, 'type': 'symbol', 'source': 'openmaptiles',
            'source-layer': 'busnav_named_intersections', 'minzoom': 13 if rank == 0 else 14,
            'filter': ['==', ['get', 'rank'], rank],
            'layout': dict(common, **label, **{'icon-image': 'jp-intersection-light' if light else 'jp-intersection-dark',
                'symbol-sort-key': ['get', 'rank'], 'text-offset': [0, 1.3]}),
            'paint': {'text-color': '#243740' if light else '#E4EEF3'}})
    for group, types, zoom, icon in [
        ('junction', ['junction'], 12, 'jp-facility-junction'),
        ('access', ['interchange', 'entrance', 'exit'], 13, 'jp-facility-access'),
        ('toll', ['toll_gate', 'mainline_toll_gate'], 14, 'jp-facility-toll')]:
        base = {'type': 'symbol', 'source': 'openmaptiles', 'source-layer': 'busnav_expressway_facilities',
            'minzoom': zoom, 'filter': ['in', ['get', 'facility_type'], ['literal', types]]}
        result.append(dict(base, id='facility-' + group + '-icon', layout=dict(common, **{'icon-image': icon})))
        result.append(dict(base, id='facility-' + group + '-label',
            filter=['all', base['filter'], ['has', 'name']],
            layout=dict(common, **label, **{'icon-image': 'jp-facility-label',
                'text-anchor': 'left', 'text-offset': [2.6, 0]}), paint={'text-color': '#FFFFFF'}))
    return result


def update(path, palette):
    style = json.loads(path.read_text(encoding='utf-8'))
    style['metadata']['busnav:road-schema'] = 2
    layers = [x for x in style['layers'] if not x['id'].startswith(('route-shield-', 'facility-', 'intersection-', 'busnav-')) and x['id'] != 'motorway-junctions']
    for layer in layers:
        if (layer['id'].startswith('roads-') and not layer['id'].endswith('-casing')) or layer['id'] in ('road-tunnels', 'road-bridges'):
            old = layer['paint']['line-color']
            if isinstance(old, list) and old[:2] == ['match', ['get', 'route_network']]:
                old = old[-1]
            # Unknown motorway/major roads retain a neutral width hierarchy, never a category color.
            if layer['id'] == 'roads-motorway':
                old = '#d3c7ae' if 'light' in path.name else '#777c82'
            colors = palette
            if layer['id'] in ('road-tunnels', 'road-bridges'):
                # A distinct tint keeps the structural stroke visible over the classified fill.
                colors = ('#84AED1', '#E3ADA5', '#91BEA2') if 'light' in path.name else ('#99BDDF', '#E3B0AA', '#A3C9B0')
            layer['paint']['line-color'] = ['match', ['get', 'route_network'],
                ['expressway', 'urban_expressway'], colors[0], 'national', colors[1], 'prefectural', colors[2], old]
        if layer['id'] == 'road-labels':
            layer['filter'] = ['in', ['get', 'class'], ['literal', ['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor', 'service']]]
            layer['layout']['text-field'] = ['coalesce', ['get', 'name:ja'], ['get', 'name'], '']
            layer['layout']['text-allow-overlap'] = False
            layer['layout']['text-ignore-placement'] = False
    layers = [x for x in layers if x['id'] != 'motorway-refs']
    shields = []
    for rank, (kind, zoom, spacing) in enumerate([('expressway', 7, 420), ('urban_expressway', 12, 420), ('national', 8, 550), ('prefectural', 13, 680)]):
        icon = 'jp-' + kind
        if kind == 'expressway':
            icon = ['case', ['>', ['length', ['get', 'route_ref']], 3], 'jp-expressway-wide', 'jp-expressway']
        if kind == 'urban_expressway':
            icon = ['case', ['all', ['==', ['get', 'route_source_network'], '首都高速道路'],
                ['in', ['get', 'route_ref'], ['literal', ['C1', 'C2']]]], 'jp-urban-ring', 'jp-urban']
        filters = ['all', ['==', ['geometry-type'], 'LineString'],
                   ['==', ['get', 'route_network'], kind], ['has', 'route_ref']]
        if kind == 'national':
            # Only single/double-digit national routes at overview zooms; all at z10+.
            filters.append(['any', ['>=', ['zoom'], 10], ['<=', ['length', ['get', 'route_ref']], 2]])
        shields.append({'id': 'route-shield-' + kind, 'type': 'symbol', 'source': 'openmaptiles',
            'source-layer': 'transportation_name', 'minzoom': zoom, 'filter': filters,
            'layout': {'visibility': 'none', 'symbol-placement': 'line', 'symbol-spacing': spacing, 'symbol-sort-key': rank,
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
    # Invisible line sentinel allows runtime route insertion below every route shield.
    anchor = {'id': 'busnav-shield-anchor', 'type': 'line', 'source': 'openmaptiles',
        'source-layer': 'transportation', 'layout': {'visibility': 'none'}}
    details = detail_layers('light' in path.name)
    layers[insertion:insertion] = [anchor] + details[:2] + shields + details[2:]
    style['layers'] = layers
    path.write_text(format_json(style) + '\n', encoding='utf-8')


if __name__ == '__main__':
    for filename, palette in PALETTES.items():
        update(ROOT / 'style' / filename, palette)
