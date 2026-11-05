# Ancient Debris Machine (Fabric)

This is a server-side Java mod for Minecraft Java 1.21.11. It runs a 3-wide × 3-high tunnel miner from commands. It mines every block in each face (not just Ancient Debris), which lets it handle netherrack, basalt, gravel, lava, and other blocks in the path. Drops are collected into the command player's inventory; if that inventory is full, leftovers go into a chest one block behind and below the advancing face. Ancient Debris is collected like any other dropped block.

The machine advances one block every 10 server ticks after successfully clearing its current face. It halts on repeated fully blocked faces, after 256 blocks, if its owner leaves the server, or if the owner changes dimension. It uses the normal block drops and harvest rules of the player who started it. It has no rendered moving entity, so there is no blinking/flickering machine animation.

## Build

Install a JDK 21 and Gradle (or add the Gradle wrapper from a Fabric template project). From this folder, run:

```powershell
gradle build
```

The installable JAR will be in `build/libs/ancient-debris-machine-1.0.0.jar` (the shorter filename without `-sources` if Gradle produces both). The first build downloads Minecraft and Fabric dependencies.

## Install

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put the built JAR and Fabric API for 1.21.11 in the instance's `mods` folder.
3. Launch the Fabric profile. For multiplayer, install the mod on the server; the operator running commands must have permission level 2 or higher.

## Use

Stand at the center of the tunnel's starting face, at the intended height, in the Nether. Run one of:

```text
/debris start north
/debris start south
/debris start east
/debris start west
```

The miner starts one block ahead of your feet and clears a 3×3 face each step. Place a chest one block behind and one block below the current face to receive items when your inventory fills; otherwise items are sent to your inventory. To check it, run `/debris status`. Stop it with `/debris stop`.

Use in a disposable test world first. The command destroys terrain and does not understand claims or protection plugins beyond stopping when the game refuses to break a block. It can expose lava and does not place blocks, bridge gaps, or make the area safe for a player. Stay clear of the tunnel and provide suitable fire/lava protection.
