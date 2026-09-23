# Remote Japan Android integration (Phase 010.6A)

Developer Connections (debug builds) offers Local Emulator, Local LAN, Remote Test,
and Custom. Select a profile, then **保存** to apply it. Selection alone is a draft.
During active FREE or PRESCRIBED navigation, profile selection, URL/region editing,
save, and reset are disabled. End navigation before changing connections.

## Remote Test

`RemoteTestEndpoints` is the canonical endpoint definition in the Android app:

| Setting | Value |
| --- | --- |
| Routing | `https://routing-busnav.nobu0707.net` |
| Map | `https://maps-busnav.nobu0707.net` |
| Region | `BasemapRegion.JAPAN` |
| Light | `https://maps-busnav.nobu0707.net/styles/busnav-light/style.json` |
| Dark | `https://maps-busnav.nobu0707.net/styles/busnav/style.json` |
| Server TileJSON (reference only) | `https://maps-busnav.nobu0707.net/data/japan.json` |

REMOTE_TEST locks both URLs to HTTPS and the region to JAPAN. The repository rejects
edited remote URLs/regions. All four active settings are persisted in one DataStore
transaction. Routing reads the saved URL on its next request; the map observes the
same settings snapshot and reloads when the effective style configuration changes.

JAPAN means the single nationwide `japan.mbtiles`, not a collection of regional
switches. Panning between Hokkaido, Tohoku, Kanto, Chubu, Kansai, Chugoku, Shikoku,
Kyushu and Okinawa never selects a different source/style. No KANTO/CHUBU fallback
is used for remote failures. A failed initial style load can show the existing
embedded fallback with an explicit unavailable banner; a subsequent source failure
marks the map unavailable without replacing the source or reloading the style.
Android loads the server style JSON directly; it does not reconstruct a style from
TileJSON. The Japan connection check requests the canonical dark style.

## Existing local settings and migration

- Local Emulator retains `http://10.0.2.2:8002` and `http://10.0.2.2:8080` and saved
  local region behavior. When no local region exists, it uses KANTO.
- Local LAN restores its saved endpoints/region; on the first use it can inherit
  existing Custom/legacy values. Enter and save LAN URLs if none have been saved.
- Custom restores saved manual endpoints. Non-remote snapshots survive a remote
  round trip and app restart. Reset clears the selected profile and its snapshots.
- Missing or unknown `selectedConnectionEnvironment` decodes as CUSTOM, preserving
  existing routing/map URLs and region. Merely reading a legacy record does not
  rewrite it or opt it into REMOTE_TEST.
- `kanto`/`chubu` remain valid persisted IDs. Unknown region IDs retain the existing
  KANTO fallback. An explicitly remote persisted record always resolves to the
  canonical JAPAN snapshot, even if its other stored fields are inconsistent.
- Local region style paths stay `/styles/busnav-kanto/style.json` and
  `/styles/busnav-chubu/style.json` with their `-light` counterparts.

Debug/release build defaults and release DataStore isolation are unchanged. This
phase does not promote Remote Test into the production default. Release continues
to use its fixed build configuration and `usesCleartextTraffic="false"`; local
debug HTTP remains permitted. There is no certificate pinning or custom IPv6 socket.

## Presentation and retained state

Normal presentation is LIGHT; active navigation at night or in a tunnel is DARK.
Both Japan styles pass through the existing MapController style-loaded callback.
It registers all JapaneseRoadShields runtime images on every load: national/urban
expressway, national/prefectural route shields, facility access/junction/toll icons,
stretchable green facility label backgrounds and intersection backgrounds.

The server layers `busnav_expressway_facilities` and `busnav_named_intersections`
remain usable for IC, entrances/exits, JCT, toll gates and named intersections.
Road colors, visible-span shield visibility and runtime rendering logic are unchanged.
The same callback restores route, detour, route-plan, traffic and current-location
overlays, then layer order (route lines below shields, traffic above shields).
The MapController and its retained overlay/camera state are not recreated by a
profile or theme change. FREE/PRESCRIBED, Detour/Rejoin, HEADING_UP/NORTH_UP, compass,
ring+arrow marker, matching/deviation and guidance logic are unchanged.

## IPv6 and verification scope

The supplied server deployment is IPv6-only (AAAA, no A record). An IPv4-only client
network cannot reach it. Ordinary Android DNS/HTTPS handles connectivity. Failed
remote connection checks and the unavailable map banner include:
「現在のリモートテストサーバーはIPv6接続が必要です」.
This is a deployment limitation, not a diagnosis that a particular failure is DNS
or IPv6 related. No network-type detection is attempted.

Server facts supplied for this phase: Valhalla `3.9.0-a3a5631c4`, nationwide bounds
`122.5607,20.08228,154.4709,45.8154`, zoom 0–14, valid public HTTPS certificate,
backend 8002/8080 bound to localhost. Server-side nationwide and cross-region smoke
results were supplied by the user; they are not Android validation results.

Required host checks: `test`, `lint`, `assembleDebug`, `assembleRelease`,
`assembleDebugAndroidTest`. The last task compiles instrumentation sources only.

- Emulator: NOT RUN (user requested)
- Physical Android: NOT RUN (user requested)
- `connectedDebugAndroidTest`, managed device tests and adb: NOT RUN (user requested)

Windows DNS/HTTPS smoke is optional and environment-dependent. Actual remote map
pixels, runtime images and on-device overlay restoration require a later authorized
Android validation session on an IPv6-capable network. Production promotion should
separately decide release defaults, access/availability policy and IPv4 reachability,
then validate real devices. Phase 010.5C Visible Map Cursor is the next handoff.
