# Guidance fixture provenance

Captured 2026-09-20 from WSL localhost:8002, status version 3.9.0-a3a5631c4, existing Chubu dataset. These are developer-selected synthetic routes, not GPS tracks or user journeys. No credentials or device identifiers are included. Full shape and indices are preserved.

Both requests use directions_type=maneuvers, shape_format=polyline6, units=kilometers, costing=truck, costing_options.truck height=3.8, width=2.5, length=12, weight=16. HTTP 200 / trip.status=0.

- maneuvers-local-3.9.0.json: (35.161,136.882) to (35.170,136.910), 8 maneuvers; left/right and U-turn.
- maneuvers-highway-3.9.0.json: (35.161,136.882) to (35.171,138.675), additionally use_highways=1; 16 maneuvers, 4 with signs; ramp-right, exit-left and keep-left/right.
- maneuvers-signs-synthetic.json: explicitly synthetic three-point shape, all four sign lists, consecutive_count, duplicate sign, unknown field and optional narrative strings. The live maneuvers-only responses omit narrative instruction strings by design.
- route-88km-valhalla-3.9.0.json: prior Phase004.1 shape-only regression retained unchanged.
