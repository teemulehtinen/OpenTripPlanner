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
The initial implementation exposes two read-only tools:

- `find_nearest_stop` finds transit stops near a latitude/longitude using a bounded straight-line
  radius. This works when the graph has no street network.
- `route` plans transit journeys between feed-scoped stop IDs. It accepts ordered intermediate stop
  IDs as pass-through points.

A coordinate is not accepted as a route origin, destination, or intermediate point. Use
`find_nearest_stop` first, then pass the selected stop ID to `route`. This is intentional: routing
between stop IDs is supported on graphs without streets, while coordinate linking and coordinate
visit-via routing require street-network support.

The route tool requires:

- `origin`: feed-scoped stop ID, for example `agency:stop-a`
- `destination`: feed-scoped stop ID
- `intermediateStops`: optional ordered list of feed-scoped stop IDs
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
