# PATCHES

Changes made to upstream (yuliskov/SmartTube) code. Check this list when merging upstream.

| File | Function | Change | Reason |
|---|---|---|---|
| `common/.../prefs/BlockedChannelData.java` | `Channel.matches` (new), `equals`, TreeSet comparator, `addChannel`, `removeChannel`, `containsChannel`, `restoreOldData`, `clear` | Match by `channelId` first, fall back to name only when an id is missing. Comparator sorts by name, then id. `clear()` notifies listeners. | Upstream matched by name only: same-name channels collided, renamed channels slipped through. `equals` compared `channel` with `channel.channelId`. |
| `common/.../app/models/data/Video.java` | `findNextVideo`, `findFirstAllowed` (new), `isBlocked` (new) | If the autoplay next video belongs to a blocked channel, pick the first allowed suggestion instead (or none). | Upstream only checked the block list for the remote (cast) case, so autoplay played blocked channels. |

Stored data format is unchanged (`channelId,channelName` per entry), so existing block lists and backups still load.
