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

- `Ward` is the only protection aggregate: owner, WG region id, base score → tier → radius, upkeep balance, per-ward permissions, optional city membership.
- `WardService` owns the ward lifecycle (create/rename/score/tier recalculation/upkeep deduction/transfer/delete) on top of `YamlWardRepository`.
- `WardDissolutionService` is the single dissolution contract shared by all four removal routes (`/ward delete`, `/protection dissolve`, menu disband, physical block break): WG region removal + repository delete + save + physical core block removed (guarded on the configured ward material). When the OWNER dissolves their own ward the tagged founder item comes back (inventory, or dropped at feet); an admin tearing down someone else's ward gets nothing. The break route cancels the vanilla event first, so no generic untagged beacon ever drops — no orphan beacons, no free generic cores.
- **Anti-duplication refund guard** (`WardDissolutionService.shouldRefund`): the founder item is returned only when the owner dissolves their OWN ward AND the physical core was actually removed (`coreRemoved == true`) — the item IS the block, so a refund with the block absent/gone would mint a duplicate core. Truth table: `(owner, coreRemoved)` → refund only for `(true, true)`. Core removal itself is double-guarded: the chunk must be loaded and the block must still hold the configured `ward.material`. Callers surface a missed refund as "(sin bloque físico: nada devuelto)" appended to the confirmation (command, menu dispatcher and break listener share the phrasing).
- Core acquisition paths: one free claim per player UUID (`/ward reclamar`, persisted in `nucleus-claims.yml` via `NucleusClaimStore`, full inventory → drop at feet), admin `/ward give`, or crafting — `ward.recipe` in config.yml defines a configurable shaped recipe (default thematic: 8 diamonds + nether star) whose result is the tagged `createWardItem()`; `enabled: false` keeps it unregistered. There is no per-player ward cap: the acquisition cost is the brake.
- `/protection` and `/ward` share the exact same mechanics: both delegate to the Ward domain through `WardMenuFacade` and the shared `MenuActionDispatcher`. The legacy claim system has been fully removed (ProtectionMenu, ClaimManager, claim listeners, UpkeepTickTask); old `claims.yml` files are simply ignored.
- The ward menu is a vanilla chest UI (`VanillaMenuProvider`) with an item-deposit slot: players insert accepted materials (config `ward.upkeep-materials`, e.g. diamond/emerald/gold/iron/coal with per-material unit values) credited by `WardUpkeepService`.
- `WardUpkeepTickTask` drains the balance every interval; when funds run out the balance zeroes and the owner is warned once per interval (region suspension is future work).
- `WardBlockGateListener` gates "advanced" blocks (enchanting table, brewing stand, beacon…) behind Ward tiers (`ward.tier-gated-blocks`). **Surcharge model**: placing a gated block below the required rank is NOT cancelled — it is allowed, increments the ward's `belowTierBlocks`, and every upkeep interval then costs `tier.upkeep-per-interval + ward.below-tier-surcharge-units × belowTierBlocks` (default 2). It is a recurring per-interval charge, never a one-off placement fee. Breaking a still-gated block while the ward is under-ranked decrements the counter (documented approximation: it does not track who placed each block); once the tier requirement is met, blocks stop carrying surcharge. The founder item is exempt and admins (`dreamcraft.ward.admin`) bypass the gate. The counter is also **seeded at founding**: every founding route (`/ward create` and placing the ward item) runs an initial world scan that counts pre-existing below-tier gated blocks into the counter — unloaded chunks are exempt until the next re-scan. Tier transitions funnel through `WardService.addBaseScore`, the single choke point for command, menu and admin edits: an ascent wipes the counter to 0 (the higher phase now covers those blocks; the owner sees a «Fase alineada…» notice) and a descent fires an authoritative re-scan that REPLACES the counter with the current count; intra-tier changes touch nothing.
- Orphan detection lives in `domain/service/WardHealth`, a pure classifier over two observed states: `CoreState` = `PRESENT | MISSING | CHUNK_UNLOADED` and `RegionState` = `PRESENT | MISSING | WG_INACTIVE`. A ward is **orphan** only when a component is verified MISSING (either side); `CHUNK_UNLOADED` (core chunk not loaded during the check) and `WG_INACTIVE` (WorldGuard unavailable) are unknown/degraded states and never count as absence. Region existence is queried through the `WorldGuardAdapter.regionExists(Ward)` port method — an in-memory registry lookup that never loads chunks or worlds synchronously.
- `WardRegionListener` shows an action bar with the ward name and center coordinates while inside a ward.
- `CityLevelService` computes city levels purely from annexed wards, member count and wealth (sum of annexed wards' base scores) — never from direct deposits: `city-levels.levels` in `config.yml`.
- City names can be changed with `/ward rename <nombre>` / `/protection rename <nombre>` (owner or admin, unique, ≤32 chars); epic default generated names are preserved until renamed.

## Configurable Command & Presentation Layer (current)

- **CommandSpec framework** (`command/SubcommandSpec` + `CommandRegistry`): every
  subcommand is declared once; dispatch, tab completion and admin gating read the
  same table — the old switch/tab-list duplication is gone.
- Per-server customization without recompiling:
  - root-command renaming via Bukkit's native `commands.yml`
    (`plugin-configs/DreamCraftProtection/commands.example.yml`);
  - subcommand aliases / enable-flags from `config.yml` (`commands.*.subcommands`);
  - shared texts, prefixes, help blocks and all menu feedback externalized to
    `messages.yml` (`message/Messages`, single locale es, legacy codes + `{placeholders}`,
    server override → embedded default → code fallback).
- **Resource pack port** (`presentation/resourcepack/`):
  - `ResourcePackProvider` port with a dependency-free `PresentationAssetRegistry`
    implementation (vanilla materials + CustomModelData);
  - `presentation-assets.yml` is the formal contract between this plugin and the
    future resource-pack worktree (`docs/presentation-assets.md`) — semantic keys
    (`ward.icon`, sounds, fonts, symbols, particles) map to CMD values;
  - per-player fallback: `menus.provider: auto|vanilla|rp` in config.yml decides
    whether CustomModelData applies only when the viewer loaded the pack
    (`PackStatusTracker`), always, or never — MD golden rule §23 holds.

## Estate Adventure Instances (End / Trial Chamber)

- `Estate` gained an adventure `type` (`STANDARD`, `END`, `TRIAL_CHAMBER`) and an optional gated **area** (world + center XYZ + radius, persisted in `estates.yml` with legacy defaults).
- Area resolution is a pure domain query (`EstateService.findAreaAt`): circular containment, smallest area wins on overlap. No Bukkit dependency.
- **WorldGuard interaction**: each estate area gets a WG region (`dc_estate_<id12>`, full height, priority 10) owned by the estate owner with all members synced — the portal/structure zone is grief-protected and membership-gated. Synced on invite/join/leave/transfer/disband.
- **EssentialsX interaction**: when no estate area anchor exists, instance exits fall back to the EssentialsX spawn.
- **Private End instances** (`service/EndInstanceService`): END-type estates get their own End dimension created lazily via Bukkit `WorldCreator` (`dc_end_<id8>`) — no Multiverse needed. The shared `world_the_end` stays untouched for everyone else.
  - Entry builds a vanilla-style obsidian platform at (100, 48, 0) and spawns a fresh Ender Dragon if none is alive.
  - Arriving players are told which estate members are already "on the other side"; after a grace period the overworld portal frames are stripped of eyes so the next group must rebuild it (members only).
  - When the last member leaves (exit portal, death respawn, quit), the world is unloaded without saving and its folder deleted after a configurable delay: the map returns to its pre-boss state and the next group faces a freshly spawned dragon.
  - Sessions survive relogs (players rejoin mid-fight); orphaned non-members are teleported out on join.
- **Gating** (`listener/EstatePortalListener`): inserting Eyes of Ender into frames inside an END/TRIAL_CHAMBER estate area requires membership of at least one estate covering the point; portal travel redirects each member to their own estate's private instance (preferring an already-active world) and cancels non-members. TRIAL_CHAMBER areas additionally gate VAULT/TRIAL_SPAWNER interactions to members.
- **Party-per-leader flow**: stepping into a zone (or `/estate discover <tipo>`) without belonging to any of its estates auto-creates a personal party estate — the discoverer becomes its leader, inherits the zone's gated area (and gets a mirrored WG region), and invites their group via `/estate invite`. Multiple parties share one physical zone while each fights in its own private End world in parallel.
- Config lives under `estate-instances` in config.yml (`config/EndInstanceConfig`): enabled flag, world prefix, default area radius, frame scan radius, reset delays, first-enter portal reset toggle.
- Admin flow: `/estate admin create <id> end [radio|auto]` — `auto` anchors the
  area at the real vanilla structure (Registry.STRUCTURE lookup, stronghold /
  trial chambers, 512-block search, r=48 default); move it later with
  `/estate admin area <id> [radio]`; force a reset with `/estate admin reset <id>`.
- **Vertical stealth band** (`estate-instances.band-below/band-above`, defaults
  16/48): the WG area region spans anchorY-band…anchorY+band instead of full
  world height, and zone discovery applies the same band — surface players
  never learn a stronghold/chamber exists below. Legacy zones anchored at Y=0
  must be re-anchored once via `/estate admin area`.
- Instance worlds are runtime-only (never in bukkit.yml); stale folders are wiped before recreation and unloaded (no save) on plugin disable.

## Current Gaps

- Resource Pack assets: the visual contract is now defined (`presentation-assets.yml`,
  `docs/presentation-assets.md`) but the actual pack still lives in a separate
  worktree and must be integrated there deliberately; CMD ranges are provisional.
- Oraxen / DeluxeMenus adapters remain future work (port + detection ready, no
  compile dependency — neither is installed in the current docker stack).
- Deep per-handler command detail lines (e.g. `/ward info` field lines) are still
  inline Spanish; the shared layer (prefixes, help, errors, menu feedback) is fully
  catalog-driven and the `Messages.tr` fallback makes incremental migration trivial.
- Docker runtime integration for auto-loading the locally built plugin JAR still needs a verified build artifact path and startup wiring.
- Ward upkeep debt currently warns but does not suspend/dissolve the WG region yet.
- Pre-existing Wards created before any given update remain valid: they live in `wards.yml` and load on boot; no migration needed.
