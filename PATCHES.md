# PATCHES

Changes made to upstream (yuliskov/SmartTube) code. Check this list when merging upstream.

| File | Function | Change | Reason |
|---|---|---|---|
| `common/.../prefs/BlockedChannelData.java` | `Channel.matches` (new), `equals`, TreeSet comparator, `addChannel`, `removeChannel`, `containsChannel`, `restoreOldData`, `clear` | Match by `channelId` first, fall back to name only when an id is missing. Comparator sorts by name, then id. `clear()` notifies listeners. | Upstream matched by name only: same-name channels collided, renamed channels slipped through. `equals` compared `channel` with `channel.channelId`. |
| `common/.../app/models/data/Video.java` | `findNextVideo`, `findFirstAllowed` (new), `isBlocked` (new) | If the autoplay next video belongs to a blocked channel, pick the first allowed suggestion instead (or none). | Upstream only checked the block list for the remote (cast) case, so autoplay played blocked channels. |
| `common/.../app/models/data/Video.java` | `isSameChannel` (new) | Compare channels by id, fall back to author name. | Shared by "remove all cards of a channel". |
| `common/.../presenters/dialogs/menu/VideoMenuPresenter.java` | `appendBlockChannelButton` | Block sends `ACTION_REMOVE_AUTHOR` instead of `ACTION_REMOVE`. Item shown when `channelId` exists even without author name. | Upstream removed only the selected card; other cards of the channel stayed until refresh. |
| `common/.../presenters/dialogs/menu/VideoMenuPresenter.java` | `appendBlockChannelButton` | Unblock asks for the existing settings password (`GeneralData.getSettingsPassword`) while the block list is locked. Block needs no password. | PIN protection for the block list without a new password store. |
| `common/.../prefs/BlockedChannelData.java` | `isLocked`, `unlock` (new) | Locked when a settings password is set; a correct password unlocks for 5 minutes (memory only). | One password entry covers viewing and unblocking. |
| `common/.../presenters/BrowsePresenter.java` | `updateSection` | Blocked channels section shows `BlockedChannelsLockedError` (password button) instead of the list while locked. | Hide the block list from others. |
| `common/.../app/models/errors/BlockedChannelsLockedError.java` | new file | Error screen action: ask password, unlock, refresh section. | Same pattern as `SignInError` / `PasswordError`. |
| `smarttubetv/.../tv/adapter/VideoGroupObjectAdapter.java` | `removeAuthor` | Match with `Video.isSameChannel` instead of author name. | Id-first, consistent with the block list. Also affects unsubscribe in Subscriptions. |
| `smarttubetv/.../tv/ui/browse/video/MultipleRowsFragment.java` | `updateInt` | Handle `ACTION_REMOVE_AUTHOR` across all rows. | Home/Channel rows ignored it and would fall through to the "add" branch. |
| `smarttubetv/.../tv/ui/search/tags/SearchTagsFragment.java` | `update` | Handle `ACTION_REMOVE_AUTHOR` across all result rows. | Same as above for Search. |
| `common/.../presenters/SearchPresenter.java`, `ChannelPresenter.java` | `onVideoItemLongClicked` | Pass a menu callback handling `ACTION_REMOVE_AUTHOR`. | Upstream passed no callback, so nothing was removed live. |
| `common/.../playback/controllers/PlayerUIController.java` | `onSuggestionItemLongClicked` | Handle `ACTION_REMOVE_AUTHOR`: remove the channel's cards from that suggestion row. | Player kept handling only `ACTION_REMOVE`. |
| `common/.../utils/Utils.java` | `passwordMatch`, `SUPER_PASSWD` (removed) | No universal password; only the real password matches. | Upstream accepted a public master password for every password prompt, which defeats parental protection. Forgotten password → clear app data and restore a backup. |

Stored data format is unchanged (`channelId,channelName` per entry), so existing block lists and backups still load.

## Fork identity (stable flavor only)

| File | Change | Reason |
|---|---|---|
| `smarttubetv/build.gradle` | `ststable`: applicationId `io.github.hoanghaisol.blocktube`, versionCode `upstream * 100 + filterPatch`, versionName `<upstream>-filter.<patch>`. APK file prefix `BlockTube`. | Install side by side with SmartTube; updater compares versionCode only, so it must always grow. Bump `filterPatch` per release; reset to 0 is safe only when upstream versionCode grows. |
| `smarttubetv/src/ststable/res/values/strings.xml` | `app_name`, `browse_title` = BlockTube | No "YouTube" in the name. |
| `leanbackassistant/src/ststable/res/values/strings.xml`, `.../res/xml/searchable.xml` | Search authority = applicationId | Must match the provider authority (`${applicationId}`), otherwise install conflicts with SmartTube. |
| `common/src/ststable/res/values/update_urls.xml` | `https://github.com/hoanghaisol/BlockTube/releases/download/latest/blocktube_stable.json` | Updater reads this repo's releases. |
| `smarttubetv/google-services.json` | Removed | Upstream's Firebase project; has no client for our package and build fails. Crashlytics plugin is skipped when the file is absent. |
| `.gitignore` | `*.jks`, `*.keystore`, `keystore.properties`, `.env` | Never commit signing secrets. |

## Release tooling (new files only)

| File | Purpose |
|---|---|
| `.github/workflows/release.yml` | Manual release: build signed stable APKs, publish `v<versionName>` with SHA-256, update `blocktube_stable.json` in release `latest`. |
| `scripts/make_update_manifest.py` | Build/merge the updater manifest; refuses a versionCode that is not newer. |
| `.github/README.md` | Fork README (GitHub shows it instead of the upstream root README). |

## Similar channels suggester (Phase 2)

New module `:channelfilter` (no upstream code). Upstream touch points:

| File | Change | Reason |
|---|---|---|
| `settings.gradle` | include `:channelfilter` | New module. |
| `common/build.gradle` | `api project(':channelfilter')` | Bridge lives in `common`. |
| `common/.../models/data/VideoGroup.java` | `add()`: call `ChannelFilterBridge.onVideoSeen(video)` after the block filter | Collect channels seen while browsing (cheap, in-memory LRU). |
| `common/.../misc/ChannelFilterBridge.java` | New file | Only upstream-side class that talks to the module: RSS titles, block list, playback state; listens to `BlockedChannelData`. |
| `common/src/main/res/values{,-vi}/strings_blocktube.xml` | New files | BlockTube strings kept out of upstream `strings.xml`. |

## Block from search results

| File | Function | Change | Reason |
|---|---|---|---|
| `common/.../presenters/dialogs/menu/VideoMenuPresenter.java` | `appendBlockChannelButton` | When the video has no channel id (search results), block by name immediately, then resolve the id via `loadMetadata` and re-add the entry with the id. | Search result videos carry no channel id, so blocks from Search were name-only (and invisible to the suggester). |

## Content filter tile, keywords, PIN (feedback round 1)

| File | Function | Change | Reason |
|---|---|---|---|
| `common/.../misc/AppDataSourceManager.java` | `getSettingItems` | First tile "Content filter" → `ContentFilterSettingsPresenter` | One place for blocked channels, keywords, AI, PIN, update check. |
| `common/.../presenters/settings/ContentFilterSettingsPresenter.java` | New file | Blocked channels (unblock), search/video keywords, AI switch + auto-block switch + review + "Blocked by AI" (unblock), PIN set/change/remove, check for updates. | |
| `common/.../prefs/KeywordFilterData.java` | New file | Profile-scoped search and video keyword lists (part of the backup). | |
| `channelfilter/.../keyword/KeywordMatcher.java` | New file | Case- and diacritics-insensitive, whole-word/phrase match. | Typing accents on a TV is hard. |
| `common/.../models/data/VideoGroup.java` | `add()`, `isKeywordBlocked` (new) | Drop videos whose title has a blocked keyword. | Video keyword filter everywhere. |
| `common/.../models/data/Video.java` | `isBlocked` | Autoplay next also skips keyword-blocked titles. | |
| `common/.../presenters/SearchPresenter.java` | `loadSearchResult(String)` | Blocked keyword in the query → clear results, "No results". | Search keyword filter. |
| `common/.../models/search/MediaServiceSearchTagProvider.java` | `search`, `filterBlocked` (new) | Drop search suggestions containing a blocked keyword. | |
| `common/.../utils/PinHelper.java` | New file | 4-digit PIN check/set, one correct entry unlocks for 5 minutes; legacy text passwords still accepted. | |
| `common/.../utils/SimpleEditDialog.java` | `showPin` (new) | Number pad, masked, max 4 digits. | |
| `smarttubetv/.../ui/browse/settings/SettingsGridFragment.java` | `ItemViewClickedListener` | `PinHelper.check` instead of the text password dialog. | PIN protects all Settings. |
| `common/.../presenters/settings/GeneralSettingsPresenter.java` | `showPasswordDialog` | Set the settings password via `PinHelper.showSetPin`. | |
| `common/.../presenters/dialogs/menu/VideoMenuPresenter.java` | `appendBlockChannelButton` | Unblock uses `PinHelper.check`. | |
| `common/.../prefs/BlockedChannelData.java` | `isLocked` | Delegates to `PinHelper` (shared unlock window). | |
| `common/.../models/errors/BlockedChannelsLockedError.java` | `onAction` | `PinHelper.check`. | |
| `common/.../utils/Utils.java` | `KNOWN_PACKAGES` | Add `io.github.hoanghaisol.blocktube`. | Full About screen (with update settings) and GDrive restore across installs. |

AI auto-block: on by default. Only channels the user blocked are used as examples, so auto-blocks never chain. Unblocking an auto-blocked channel (anywhere) dismisses it for good.
