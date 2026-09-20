#!/usr/bin/env python3
"""Public station-area roads only; no personal position data is used or recorded."""
import json
import math
import sys
import time
import urllib.request


def request(base, path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base.rstrip("/") + path, data=data,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as response:
        assert response.status == 200
        return json.load(response)


def decode(shape):
    pos, lat, lon = 0, 0, 0
    points = []
    while pos < len(shape):
        values = []
        for _ in range(2):
            value, shift = 0, 0
            while True:
                byte = ord(shape[pos]) - 63
                pos += 1
                assert 0 <= byte <= 63 and shift <= 30
                value |= (byte & 31) << shift
                shift += 5
                if byte < 32:
                    break
            values.append(~(value >> 1) if value & 1 else value >> 1)
        lat += values[0]
        lon += values[1]
        assert -90e6 <= lat <= 90e6 and -180e6 <= lon <= 180e6
        points.append((lat / 1e6, lon / 1e6))
    assert len(points) >= 2
    return points


def check(base):
    status = request(base, "/status")
    assert status["version"].startswith("3.9.0"), status
    print("PASS status", status["version"])
    tokyo = (35.6812, 139.7671)
    routes = [
        ("Tokyo-Saitama", tokyo, (35.9062, 139.6237)),
        ("Tokyo-Kanagawa", tokyo, (35.4662, 139.6227)),
        ("Tokyo-Shizuoka", tokyo, (34.9717, 138.3888)),
        ("Tokyo-Yamanashi", tokyo, (35.6672, 138.5686)),
        ("Chubu-local", (35.1707, 136.8814), (35.4233, 137.0614)),
    ]
    long_distance = False
    for name, start, end in routes:
        begin = time.monotonic()
        result = request(base, "/route", {
            "locations": [{"lat": p[0], "lon": p[1]} for p in (start, end)],
            "costing": "truck", "directions_type": "maneuvers", "units": "kilometers",
            "costing_options": {"truck": {"height": 3.5, "width": 2.5, "length": 12, "weight": 16}},
        })
        trip = result["trip"]
        assert trip["status"] == 0 and trip["legs"]
        summary = trip["summary"]
        assert math.isfinite(summary["length"]) and summary["length"] > 0 and summary["time"] > 0
        for leg in trip["legs"]:
            points = decode(leg["shape"])
            assert leg["maneuvers"]
            for maneuver in leg["maneuvers"]:
                assert 0 <= maneuver["begin_shape_index"] <= maneuver["end_shape_index"] < len(points)
        long_distance |= summary["length"] > 100
        print("PASS", name, round(summary["length"], 1), "km", round(time.monotonic() - begin, 2), "s")
    assert long_distance, "Expected at least one route longer than 100 km"


if __name__ == "__main__":
    check(sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8002")
