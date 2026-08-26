# DreamCraft Agent Development Workflow

This document defines the safe development boundary for DreamCraft agent work.
DreamCraft is the Minecraft server infrastructure repository; DeepSeek Harness
must operate on isolated Git worktrees, never directly on the primary checkout.

## Repository Layout

Expected workspace layout:

```text
F:\Dreamcraft\
  dreamcraft-main\              primary DreamCraft checkout
  dreamcraft-worktrees\         isolated DreamCraft task worktrees
  deepseek-harness-master\      DeepSeek Harness checkout
```

The primary checkout remains the review and integration point. Agent changes are
made in task worktrees under `dreamcraft-worktrees\`.

## Git Strategy

- Main branch detected from `origin/HEAD`: `main`.
- Local primary checkout branch at inspection time: `master`.
- Resource pack preparation branch: `resource-packs`.
- Task branches should use `agent/<task-name>` unless a named integration branch
  is explicitly requested.

Recommended task worktree command:

```powershell
git worktree add F:\Dreamcraft\dreamcraft-worktrees\<task-name> -b agent/<task-name> origin/main
```

Before creating a worktree:

1. Run `git status --short --branch`.
2. Run `git branch --all --verbose`.
3. Run `git worktree list`.
4. Confirm the destination path and branch do not already exist.

Never use `git reset --hard`, `git clean -fd`, `git clean -fdx`, force push, or
automatic push as part of agent work.

## Development Server Isolation

The current `docker-compose.yml` uses:

- `mc` service from `itzg/minecraft-server:stable-java25`.
- `config-sync` service to copy `plugin-configs/` into `data/plugins/`.
- Bind mount `./data:/data`.
- Ports `25565:25565` and `19132:19132/udp`.

Because `./data` is relative to the checkout/worktree, a task worktree gets its
own server state when `docker compose up` is run from that worktree. Do not run
server tests from the primary checkout when testing agent changes.

Production safety rule: server data, worlds, player data, plugin databases, and
logs must stay outside Git and must not be removed by automation.

## Agent Workflow

Every agent task follows this order:

1. Inspect repository and current Git state.
2. Plan the change from observed files.
3. Create or select an isolated worktree.
4. Implement only inside that worktree.
5. Build and validate.
6. Start the development server only from the worktree when needed.
7. Inspect logs and generated diff.
8. Present results for human review.
9. Wait for explicit Git approval before commit, merge, or push.

Functional approval is separate from Git approval. A statement that something
works is not permission to commit, merge, or push.

## DeepSeek Harness Integration

DeepSeek Harness is plugin-first and Cordis-based. Its current architecture uses
profiles, bundles, and `cordis.patch.yml` layers rather than ad hoc external
tool wiring.

For DreamCraft, prefer this order:

1. Built-in Harness filesystem, shell, sandbox, approval, and tool facilities.
2. A Harness profile or patch that pins workspace root to a DreamCraft worktree.
3. A small out-of-tree DreamCraft Harness plugin if custom tools are required.
4. Harness core modifications only if an official extension point is insufficient.

If a DreamCraft-specific Harness plugin becomes necessary, keep it outside both
DreamCraft and DeepSeek Harness repositories, for example:

```text
F:\Dreamcraft\dreamcraft-dsh-plugin\
```

Do not create that plugin until a real tool requirement justifies it.

## Approval Boundary

Agent output should end with enough information for review:

```text
Implementation complete.

Build: PASS/FAIL/NOT RUN
Resource Pack validation: PASS/FAIL/NOT RUN
Server startup: PASS/FAIL/NOT RUN
Tests: PASS/FAIL/NOT RUN

Changed files:
...

No commit/push performed.
Waiting for human approval.
```

Forbidden without explicit human approval:

- `git push`
- `git push --force`
- `git reset --hard`
- `git clean -fd`
- `git clean -fdx`
- `docker system prune`
- `docker volume prune`
- `docker compose down -v`
- deleting worlds, volumes, credentials, or production data
- changing Git remotes or credentials
