# Fear of Fire Mod - Keep Mobs on the Run! 🔥

Make selected mobs flee from dangerous blocks and fearsome items! With FearFire, you can configure which entities will avoid specific blocks (like fire) or run from players holding certain items (like torches).

Showcase:
https://www.youtube-nocookie.com/embed/d8wBxqgOEog

# Default Configuration: firefearmod.toml
v1.0.0: Cow and Pig will now run away from the player holding a torch. And they will avoid places with fire, campfire, soul fire, and soul campfire.
v1.0.1: Most mobs will now run away from the player holding a flint and steel. And they avoid places with fire, campfire, soul fire, and soul campfire like the previous version.

When updating, please make a backup and remove the old configuration!

# 🛠 How to Configure:

<details>
<summary>Spoiler</summary>

1️⃣ Locate the config file: config/firefearmod.toml
2️⃣ Open it with a text editor.
3️⃣ Edit the lists under [fearfire] to add/remove:

entities → Mobs that will flee (e.g., "minecraft:zombie").
blocks → Blocks that scare mobs (e.g., "minecraft:magma_block").
items → Items that scare mobs when held (e.g., "minecraft:flint_and_steel").
4️⃣ Adjust performance settings under [fearfire.optimizations] to fine-tune block checks, cooldowns, and player detection radius.

Save your changes and enjoy a world where mobs fear the fire! 🔥👀💨


Example Config from my modpack Walking Among The Dinosaur: 

[fearfire]
#A list of entity IDs (e.g. 'minecraft:pig') that will have the Fear AI.
#Only these entities will run away from blocks/items set below.
entities = ["fossil:brachiosaurus", "fossil:gallimimus", "fossil:parasaurolophus", "fossil:dryosaurus", "fossil:pachyrhinosaurus", "fossil:quagga", "fossil:triceratops", "fossil:pachycephalosaurus", "fossil:psittacosaurus", "fossil:protoceratops", "fossil:dodo", "fossil:ankylosaurus", "fossil:stegosaurus", "fossil:mammoth", "fossil:megaloceros", "fossil:elasmotherium", "fossil:platybelodon"]
#Any block in this list will scare the entity if found within the search radius.
blocks = ["minecraft:fire", "minecraft:campfire", "minecraft:glowstone"]
#Any item in this list will scare the entity if a nearby player is holding it (main or off hand).
items = ["minecraft:torch", "minecraft:glowstone"]

[fearfire.optimizations]
#If true, skip block checks if the gamerule 'doFireTick' is off.
skipBlockCheckIfFireTickOff = true
#Number of ticks between scanning for threats. 20 = 1 second.
scanCooldownTicks = 120
#Horizontal radius to find players holding feared items.
playerCheckRadius = 8
#Vertical (Y) range to check for item-holding players.
playerCheckVerticalRange = 2
#Only check for fear blocks if there's at least one player within this radius (X,Z ±, Y ±2). If no player is near, we skip block checks to save CPU.
blockCheckPlayerRadius = 32

</details>
