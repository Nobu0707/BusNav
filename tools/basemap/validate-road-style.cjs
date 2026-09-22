// Run inside the pinned TileServer container: node < validate-road-style.cjs
const fs = require('fs');
const spec = require('/usr/src/app/node_modules/@maplibre/maplibre-gl-style-spec');
(async () => {
  for (const region of ['kanto', 'chubu']) {
    const data = await (await fetch(`http://localhost:8080/data/${region}.json`)).json();
    const layers = new Map(data.vector_layers.map(layer => [layer.id, layer.fields]));
    for (const layer of ['transportation', 'transportation_name']) {
      for (const field of ['route_network', 'route_ref']) {
        if (!layers.get(layer)?.[field]) throw new Error(`${region}/${layer} missing ${field}`);
      }
    }
    for (const theme of ['', '-light']) {
      const style = JSON.parse(fs.readFileSync(`/data/styles/busnav-${region}${theme}.json`));
      const errors = spec.validateStyleMin(style);
      if (errors.length) throw new Error(JSON.stringify(errors));
      for (const layer of style.layers) {
        if (layer['source-layer'] && !layers.has(layer['source-layer']))
          throw new Error(`Unknown source layer ${layer['source-layer']}`);
      }
      console.log(`PASS ${region}${theme}: MapLibre style spec, source layers, route attributes`);
    }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
