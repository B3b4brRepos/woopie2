# Woopie Sword (Fabric, MC 26.3)

Standalone sword item (`/give @s woopie:woopie_sword`) with the Woopie first-person animations from the TACZ Annoying Villagers pack.

Build on GitHub: push this folder, the workflow in `.github/workflows/build.yml` builds the jar (Java 25, Gradle 9.3, Loom `net.fabricmc.fabric-loom`).
Only Fabric Loader is required (no Fabric API).

Controls: left click = melee_bayonet 1-4 combo, right click = melee_push, sneak + left click = inspect.

Tuning (SwordRenderer.java): VIEW_ANCHOR / SCALE / OFF_* move the model on screen; MODEL_ROT_SIGN / ANIM_ROT_SIGN / ANIM_POS_SIGN fix limb/sword sign problems.
