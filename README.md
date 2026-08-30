# Kiwi Lawncher
A sophisticated Swordigo Mod Loader that makes installing and developing mods so much more easier!

## Links
- [Discord Server](https://discord.gg/t5cMNQRK9E)
- [Lawncher Data](https://github.com/raijinswordigo/requests)

# Installation
The Lawncher requires vanilla Swordigo from Play Store to be installed in order to work. You can download the Lawncher from the releases tab by clicking [here](https://github.com/raijinswordigo/lawncher/releases).

# Internals
The Lawncher's internal functioning is divided into a few steps below:
## Extraction of Libraries
The Lawncher queries `com.touchfoo.swordigo` and extracts `libswordigo.so` and `libopenal-soft.so` from the Swordigo APK queried by the Lawncher.
## Extraction of Music
Music is also extracted via the same method as the extraction of libraries. This is done to put music in a custom directory that the Lawncher can access.
## Hooking
`libswordigo.so` is hooked using GlossHook to provide APIs and Modding Functionality.
## Modded Assets and Music
The mods are designed in such a way that the game supports mods as instances. Mod folders usually contain `/resources/` for resources such as scenes and scene libraries, `/music/` for music files such as 1_hero2.mp3. The Lawncher checks the mod's Assets and Music then falls back to Vanilla's Assets and Music.
## APIs
`ProgramState::RegisterProgramLibrary` is hooked to add extra APIs that the mods might need. This is done to give the mods additional capabilities beyond Vanilla's functions e.g. FS API, Mini API, and OverlayController API.

# Forking & Contributions
This project is public so people can view the source code and learn how it works—not as an invitation to fork, modify, or redistribute it under any name. If you would like to see changes or features added, please open an Issue or Pull Request instead.

Read the [LICENSE](LICENSE) for full legal terms.

# Disclaimer
Swordigo belongs to its respective rights holders. Lawncher is an independent project, not affiliated with or endorsed by Swordigo's developers or publishers, and does not distribute Swordigo's game assets or source code. You need your own legally obtained copy of the game to use it.