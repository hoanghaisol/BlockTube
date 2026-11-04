# PATCHES

Changes made to upstream (yuliskov/SmartTube) code. Check this list when merging upstream.

| File | Function | Change | Reason |
|---|---|---|---|
| `common/.../prefs/BlockedChannelData.java` | `Channel.matches` (new), `equals`, TreeSet comparator, `addChannel`, `removeChannel`, `containsChannel`, `restoreOldData`, `clear` | Match by `channelId` first, fall back to name only when an id is missing. Comparator sorts by name, then id. `clear()` notifies listeners. | Upstream matched by name only: same-name channels collided, renamed channels slipped through. `equals` compared `channel` with `channel.channelId`. |
| `common/.../app/models/data/Video.java` | `findNextVideo`, `findFirstAllowed` (new), `isBlocked` (new) | If the autoplay next video belongs to a blocked channel, pick the first allowed suggestion instead (or none). | Upstream only checked the block list for the remote (cast) case, so autoplay played blocked channels. |
| `common/.../app/models/data/Video.java` | `isSameChannel` (new) | Compare channels by id, fall back to author name. | Shared by "remove all cards of a channel". |
| `common/.../presenters/dialogs/menu/VideoMenuPresenter.java` | `appendBlockChannelButton` | Block sends `ACTION_REMOVE_AUTHOR` instead of `ACTION_REMOVE`. Item shown when `channelId` exists even without author name. | Upstream removed only the selected card; other cards of the channel stayed until refresh. |
| `smarttubetv/.../tv/adapter/VideoGroupObjectAdapter.java` | `removeAuthor` | Match with `Video.isSameChannel` instead of author name. | Id-first, consistent with the block list. Also affects unsubscribe in Subscriptions. |
| `smarttubetv/.../tv/ui/browse/video/MultipleRowsFragment.java` | `updateInt` | Handle `ACTION_REMOVE_AUTHOR` across all rows. | Home/Channel rows ignored it and would fall through to the "add" branch. |
| `smarttubetv/.../tv/ui/search/tags/SearchTagsFragment.java` | `update` | Handle `ACTION_REMOVE_AUTHOR` across all result rows. | Same as above for Search. |
| `common/.../presenters/SearchPresenter.java`, `ChannelPresenter.java` | `onVideoItemLongClicked` | Pass a menu callback handling `ACTION_REMOVE_AUTHOR`. | Upstream passed no callback, so nothing was removed live. |
| `common/.../playback/controllers/PlayerUIController.java` | `onSuggestionItemLongClicked` | Handle `ACTION_REMOVE_AUTHOR`: remove the channel's cards from that suggestion row. | Player kept handling only `ACTION_REMOVE`. |

Stored data format is unchanged (`channelId,channelName` per entry), so existing block lists and backups still load.
