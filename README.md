# BonfireFoodRottingPatch

[English](#english) | [简体中文](#简体中文)

BonfireFoodRottingPatch is Bonfire's maintained food-freshness patch branch.

BonfireFoodRottingPatch 是 Bonfire 维护中的食物腐烂系统补丁分支。

---

## English

BonfireFoodRottingPatch is the maintained Bonfire patch branch for food freshness, rotten-state synchronization, and lore compatibility.

### What It Does

- Tracks fresh and rotten item states through plugin-managed metadata.
- Keeps lore rendering aligned with the actual runtime state.
- Includes admin reload support through `/bfr`.
- Targets survival gameplay where freshness needs to remain readable and stable.

### Core Command

- `/bfr`

### Repository Layout

- `src/`: plugin source code
- `build.ps1`: local build helper
- `build/`: local build output, excluded from Git release tracking

### Build

```powershell
.\build.ps1
```

### License

This repository currently uses the `Bonfire Non-Commercial Source License 1.0`.
See [LICENSE](LICENSE) for the exact terms.

---

## 简体中文

BonfireFoodRottingPatch 是 Bonfire 持续维护中的食物新鲜度系统补丁分支，重点解决腐烂状态同步与 lore 兼容问题。

### 它的作用

- 通过插件管理的元数据追踪新鲜与腐烂状态。
- 让 lore 展示与实际运行时状态保持一致。
- 通过 `/bfr` 提供管理端重载能力。
- 面向需要保留食物新鲜度玩法的生存式业务场景。

### 主要命令

- `/bfr`

### 仓库结构

- `src/`：插件源码
- `build.ps1`：本地构建脚本
- `build/`：本地构建输出，不纳入发布源码

### 构建方式

```powershell
.\build.ps1
```

### 授权

本仓库当前采用 `Bonfire Non-Commercial Source License 1.0`。
具体条款见 [LICENSE](LICENSE)。
