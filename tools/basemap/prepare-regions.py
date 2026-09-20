#!/usr/bin/env python3
"""Validate MBTiles and derive regional styles from the single tracked template."""
import json
import sqlite3
import sys
from pathlib import Path


def validate(path):
    with sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True) as db:
        assert db.execute("PRAGMA quick_check").fetchone()[0] == "ok", "Corrupt MBTiles"
        metadata = dict(db.execute("SELECT name, value FROM metadata"))
        assert metadata["format"] == "pbf"
        assert 0 <= int(metadata["minzoom"]) <= int(metadata["maxzoom"]) <= 22
        assert len(metadata["bounds"].split(",")) == 4
        layers = json.loads(metadata["json"])["vector_layers"]
        assert {"transportation", "transportation_name", "building", "water"} <= {x["id"] for x in layers}
        assert db.execute("SELECT count(*) FROM tiles").fetchone()[0] > 0
        print(json.dumps({"file": path.name, "bytes": path.stat().st_size,
                          "bounds": metadata["bounds"], "minzoom": metadata["minzoom"],
                          "maxzoom": metadata["maxzoom"], "format": metadata["format"],
                          "layer_count": len(layers)}))


if __name__ == "__main__":
    if sys.argv[1] == "--validate":
        validate(Path(sys.argv[2]))
    else:
        data = Path(sys.argv[1])
        output = data / "styles"
        output.mkdir(exist_ok=True)
        for region in ("kanto", "chubu"):
            validate(data / (region + ".mbtiles"))
            for suffix in ("", "-light"):
                template = Path(__file__).parent / "style" / ("busnav" + suffix + ".json")
                style = json.loads(template.read_text(encoding="utf-8"))
                style["name"] = "BusNav " + region.title() + (" Light" if suffix else " Dark")
                style["sources"]["openmaptiles"]["url"] = "mbtiles://{" + region + "}"
                if region == "kanto":
                    style["center"] = [139.75, 35.75]
                text = json.dumps(style, ensure_ascii=False, indent=2) + "\n"
                (output / ("busnav-" + region + suffix + ".json")).write_text(text, encoding="utf-8")
                if region == "chubu":
                    (output / ("busnav" + suffix + ".json")).write_text(text, encoding="utf-8")
