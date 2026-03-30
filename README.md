# BonfireFoodRottingPatch

![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Platform](https://img.shields.io/badge/platform-Paper%201.21.x-brightgreen)
![Dependency](https://img.shields.io/badge/dependency-NBTAPI-blueviolet)
![Status](https://img.shields.io/badge/status-patched-success)

BonfireFoodRottingPatch is the maintained patch branch of Bonfire's food rotting system, focused on stable state sync and lore compatibility.

## Highlights

- Tracks fresh and rotten item states through plugin-managed metadata.
- Keeps lore rendering aligned with the runtime item state.
- Includes admin reload support through `/bfr`.
- Targets Bonfire survival gameplay where item freshness matters.

## Build

```powershell
.\build.ps1
```

## Repository Scope

- Source and config only.
- Build outputs and copied server jars are excluded from Git.

## License

GPL-3.0
