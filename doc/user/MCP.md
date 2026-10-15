# Model Context Protocol API

OTP can expose an optional MCP endpoint for language-model clients. Enable the `McpApi`
feature before using the endpoint:

```json
{
  "feature": {
    "McpApi": true
  }
}
```

The endpoint is `/otp/mcp` when OTP is running with its default server base path.
The initial implementation exposes two read-only tools and a stop-index resource:

- `find_nearest_stop` finds transit stops near a latitude/longitude using a bounded straight-line
  radius. This works when the graph has no street network.
- `route` plans transit journeys between feed-scoped stop IDs, stop names, or coordinates. It accepts
  ordered intermediate stops of those same types as pass-through points.
- `resources/list` advertises `otp://stops.json` when `stops.json` is present in OTP's base directory,
  and `resources/read` returns the compact root-stop index.

The stop index is a JSON array of `[name, id, lat, lon]` tuples. Route stop fields accept either a
feed-scoped stop ID prefixed with `@`, a name search, or a coordinate object containing `latitude`
and `longitude`. Name searches are normalized to ASCII lowercase. For names with at least four
normalized characters, OTP considers stops with the same first four characters and selects the one
with the smallest Levenshtein distance between the remaining suffixes. Names shorter than four
characters must match exactly after normalization. If there is no matching prefix (or no exact
short-name match), the request returns no stop match. Coordinates resolve to the closest indexed
stop within 10,000 meters. Explicit stop IDs can still be routed when the index resource is
unavailable; name searches and coordinates require the resource.

The route tool requires:

- `origin`: `@`-prefixed feed-scoped stop ID, name search, or coordinate object
- `destination`: `@`-prefixed feed-scoped stop ID, name search, or coordinate object
- `intermediateStops`: optional ordered list of `@`-prefixed stop IDs, names, or coordinate objects
- `time`: ISO-8601 timestamp
- `timeDirection`: `DEPART_AT` or `ARRIVE_BY`
- `searchWindowSeconds`: bounded search window
- `maxItineraries`: bounded result count
- `locale`: optional language tag

The MCP endpoint is disabled by default. For local Streamable HTTP clients, the implementation
accepts localhost origins and requires clients to advertise both `application/json` and
`text/event-stream` in the `Accept` header. Non-local deployments must put the endpoint behind the
deployment's authentication and origin-control layer before exposing it to untrusted clients.

The first version returns a bounded structured result. It does not expose opaque GraphQL paging
cursors or arbitrary GraphQL execution. Additional GraphQL-backed MCP tools should be added with
small, explicit schemas rather than a generic query tool.
