# DreamCraft Protection Architecture

## Discovery

- `dreamcraft-main` is currently an infrastructure repository, not a custom plugin source tree.
- The active runtime is Docker-based with `itzg/minecraft-server`, `TYPE: PAPER`, `config-sync`, and versioned plugin configs under `plugin-configs/`.
- Existing reusable server-side systems are operational plugins, not shared Java code:
  `WorldGuard`, `LuckPerms`, `CoreProtect`, `EssentialsX`, `Chunky`, `ViaVersion`, `ViaBackwards`, `Geyser`, `PacketEvents`.
- The only existing Resource Pack structure found during discovery lives in the separate `resource-packs` worktree, so the protection plugin keeps its core fully independent from pack assets.
- No existing DreamCraft command/menu/persistence abstraction was present in the repo at discovery time, so this implementation introduces a new Paper plugin module instead of extending non-existent local code.

## Implemented Shape

- `ProtectionClaim` is the core aggregate and stores ownership, bounds, tier, upkeep timings, members, wardrobe location, and incremental block stats.
- `ClaimIndex` is the central spatial index keyed by world and grid cells, avoiding global claim scans for point lookups and overlap checks.
- `ProtectionChecker` is the single authorization entry point for build, break, interact, transfer, and management checks.
- `ClaimManager` owns claim lifecycle, membership changes, ownership transfer, incremental block tracking, and persistence coordination.
- `UpkeepCalculator` and `UpkeepManager` keep upkeep logic separate from Bukkit listeners.
- `ClaimRepository` persists dynamic plugin state into the plugin data folder, leaving `plugin-configs/` as versioned configuration only.
- `WardrobeItems` provides a stable logical item identity using persistent item data, with optional `CustomModelData` for resource-pack presentation.
- `ProtectionMenu` is a vanilla inventory UI and remains functional without any Resource Pack.

## Ward Domain (current system)

- `Ward` is the new protection aggregate: owner, WG region id, base score → tier → radius, upkeep balance, per-ward permissions, optional city membership.
- `WardService` owns the ward lifecycle (create/rename/score/tier recalculation/upkeep deduction/transfer/delete) on top of `YamlWardRepository`.
- `/protection` and `/ward` share the exact same mechanics: both delegate to the Ward domain through `WardMenuFacade` and the shared `MenuActionDispatcher`. The legacy claim system still runs to protect pre-existing claims but is no longer reachable from commands.
- The ward menu is a vanilla chest UI (`VanillaMenuProvider`) with an item-deposit slot: players insert accepted materials (config `ward.upkeep-materials`, e.g. diamond/emerald/gold/iron/coal with per-material unit values) credited by `WardUpkeepService`.
- `WardUpkeepTickTask` drains the balance every interval; when funds run out the balance zeroes and the owner is warned once per interval (region suspension is future work).
- `WardBlockGateListener` blocks placing high-value blocks (enchanting table, brewing stand, beacon…) until the ward reaches the configured minimum tier (`ward.tier-gated-blocks`).
- `WardRegionListener` shows an action bar with the ward name and center coordinates while inside a ward.
- `CityLevelService` computes city levels purely from annexed wards, member count and wealth (sum of annexed wards' base scores) — never from direct deposits: `city-levels.levels` in `config.yml`.
- Ward names can be changed with `/ward rename <nombre>` / `/protection rename <nombre>` (owner or admin, unique, ≤32 chars); epic default generated names are preserved until renamed.

## Current Gaps

- Resource Pack assets were not added in this worktree because the verified pack source lives in a separate branch/worktree and should be integrated there deliberately.
- Docker runtime integration for auto-loading the locally built plugin JAR still needs a verified build artifact path and startup wiring.
- Ward upkeep debt currently warns but does not suspend/dissolve the WG region yet.
- Legacy claim listeners remain active alongside the Ward system until existing claims are migrated.
