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

## Current Gaps

- Resource Pack assets were not added in this worktree because the verified pack source lives in a separate branch/worktree and should be integrated there deliberately.
- Explosion, piston, hopper, and redstone listeners are not implemented yet in this first pass.
- Docker runtime integration for auto-loading the locally built plugin JAR still needs a verified build artifact path and startup wiring.
- Build/test execution has not been completed yet because this workspace does not currently include a Gradle wrapper or local Gradle installation.
