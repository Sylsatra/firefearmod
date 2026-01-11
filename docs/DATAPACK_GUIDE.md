# Fear Anything Datapack Guide

This guide provides a comprehensive reference for creating custom datapacks for the **Fear Anything** mod.

> [!IMPORTANT]
> **Applying Changes**: After modifying your datapack, you must use the `/reload` command in-game to apply the changes. Mobs will automatically refresh their fear profiles to use the new data without needing a world rejoin.

## 1. Directory Structure

To create a datapack, you need a folder with a `pack.mcmeta` file and a `data` folder.
Inside `data`, you will create folders for your namespace (e.g., `example_pack`).

### Folder Layout
- **Trauma Groups (Fear Profiles)**: `data/<namespace>/trauma_groups/`
- **Natural Breeding**: `data/<namespace>/natural_breeding/`
- **Forced Breeding**: `data/<namespace>/forced_breeding/`

*(Replace `<namespace>` with your unique ID, e.g., `my_custom_fear`)*

---

## 2. Trauma Groups (Fear Profiles)

Trauma groups define what certain mobs fear and under what conditions. Multiple groups can apply to the same mob.

### Root Object Properties
```json
{
  "group_id": "example:my_fear_group",
  "default_witness_radius": 16.0,
  "conditions": [ ... ],
  "mobs": [ ... ],
  "stages": [ ... ]
}
```

| Field | Description | Default |
| :--- | :--- | :--- |
| `group_id` | Unique ID for this group. | Filename |
| `default_witness_radius` | Radius within which a mob witnesses others being hurt (triggers requirements). | `16.0` |
| `conditions` | List of conditions that must be met for this entire group to be active. | `[]` |
| `mobs` | List of entity types this group applies to. | **Required** |
| `stages` | Detailed progression of fear. | **Required** |

### Mob Definitions
In the `"mobs"` array, you define which entities are affected.
```json
"mobs": [
  { "id": "minecraft:cow", "custom_name": "Daisy", "nbt": "{CustomTag:1b}" }
]
```
- **id**: Registration ID of the entity.
- **custom_name** (Optional): Only match mobs with this specific name.
- **nbt** (Optional): Match mobs using advanced NBT rules (see [Advanced NBT Matching](#advanced-nbt-matching)). Supports checking for owners, taming status, etc.

---

## 3. Trauma Stages

A group can have multiple stages. The mod supports up to 5 stages by default (this is a config setting).

### Stage Object Properties
```json
{
  "flee_speed": 1.4,
  "search_radius": 16,
  "witness_radius": 24,
  "fears": [ ... ],
  "requirements": [ ... ]
}
```

| Field | Description |
| :--- | :--- |
| `flee_speed` | Movement multiplier when fleeing. `1.0` is walking, `1.6` is sprinting panic. |
| `search_radius` | How far (in blocks) the mob scans for threats. |
| `witness_radius` | Specific witness radius for this stage (overrides global `default_witness_radius`). |
| `fears` | List of items, blocks, or entities that trigger fear. |
| `requirements` | List of events (like being hurt) that must happen for this stage to be active. |

---

## 4. Fear & Requirement matching

### Fear Sources (Items, Blocks, Entities)
Fears are defined in the `"fears"` list within a stage.
```json
{
  "type": "item",
  "id": "minecraft:iron_sword",
  "custom_name": "Excalibur",
  "nbt": "{Enchantments:[{id:\"minecraft:sharpness\",lvl:1s}]}",
  "fear_override": true,
  "mutual_vision": true
}
```

#### Shared Properties:
- **type**: `block`, `item`, or `entity`.
- **id**: Registration ID (e.g., `minecraft:fire`). Use `#name` for tags (e.g., `#minecraft:campfires`).
- **custom_name**: Match by display name.
- **nbt**: Match by specific NBT tags using [Advanced NBT Matching](#advanced-nbt-matching).
- **fear_override**: If `true`, the mob flees even if it is currently aggressive/attacking.
- **mutual_vision**: (Entities/Held Items only) Mob only flees if it sees you AND you are looking at it.

#### Block Specific:
- **states**: Match specific block properties.
  ```json
  "states": { "lit": "true", "signal_fire": "false" }
  ```

#### Entity Specific:
- **visibility_mode**: 
  - `LOOK_BASED`: Must have line-of-sight and be within FOV.
  - `ALWAYS`: Can sense the threat through walls/behind them.

### Trauma Requirements
Requirements define *what happens* to trigger fear in a stage.
```json
"requirements": [
  {
    "type": "hurt_by_entity",
    "entity": { "id": "minecraft:player" }
  },
  {
    "type": "witness_hurt_by_source",
    "source": { 
      "item": { "id": "minecraft:fire_charge" } 
    }
  }
]
```

#### Requirement Types:
- `hurt_by_entity`: When the mob is hurt by a specific entity.
- `hurt_by_source`: When the mob is hurt by a specific item or block.

---

## 5. Conditions

Conditions make a trauma group active or inactive based on the environment.
```json
"conditions": [
  { "type": "health_percent", "min": 0.0, "max": 0.5 },
  { "type": "is_day", "value": false },
  { "type": "is_raining", "value": true },
  { "type": "y_level", "min": 64 }
]
```

| Type | Param | Description |
| :--- | :--- | :--- |
| `health_percent` | `min`, `max` | Current health as a fraction (0.0 to 1.0). |
| `is_day` | `value` | `true` for day, `false` for night. |
| `is_raining` | `value` | `true` if raining or snowing. |
| `y_level` | `min`, `max` | World height range. |

---

## 6. Advanced NBT Matching

The `nbt` field in most objects can now be more than just a simple string. It supports a persistent path-based matching system.

### NBT Rule Structure
```json
"nbt": {
  "path": "ForgeData.my_mod.power_level",
  "op": ">",
  "value": "100"
}
```
Or multiple rules:
```json
"nbt": [
  { "path": "Owner", "op": "exists", "value": "true" },
  { "path": "Health", "op": ">", "value": "10" }
]
```

| Field | Description |
| :--- | :--- |
| `path` | Dot-separated path to the tag (e.g., `ForgeData.foo.bar`). |
| `op` | Operator: `==`, `!=`, `>`, `>=`, `<`, `<=`, `contains`, `regex`, `exists`. |
| `value` | The value to compare against. For `exists`, use `"true"` or `"false"`. |

### Examples
- **Tamed Wolf**: `{ "path": "Owner", "op": "exists", "value": "true" }`
- **Missing Tag**: `{ "path": "Invisible", "op": "exists", "value": "false" }`
- **Regex Match**: `{ "path": "CustomName", "op": "regex", "value": ".*Killer.*" }`

---
## 6. Temptation

If a mob is "tempted," it feels safe and will ignore threats it would otherwise flee from.

### Automatic Temptation:
1.  **Lure Mod**: Items defined as lures by the Lure mod.

### Manual Temptation:
If the mob fears the player, you can override the fear with an item as a temptation in a fear group using `"temptation": true` (or `"is_tempted_by": true`).
```json
{ "type": "item", "id": "minecraft:wheat", "temptation": true }
```
*Mobs seeing this item will follow it if Item & Block Attraction is installed.*

--- 

## 7. Breeding Datapacks

### Natural Breeding
Ambient breeding that happens between mobs without player input.
- **Folder**: `natural_breeding/`
```json
{
  "mob": "minecraft:cow",
  "child": "minecraft:cow",
  "partner_radius": 8.0,
  "cooldown_ticks": 12000,
  "chance_per_attempt": 0.05,
  "max_nearby_children": 8
}
```

### Forced Breeding
Automatic breeding triggered by items held by the parents.
- **Folder**: `forced_breeding/`
```json
{
  "mob": "minecraft:cow",
  "child": "minecraft:cow",
  "require_held_item": "minecraft:wheat",
  "consume_item": true,
  "partner_radius": 8.0,
  "max_nearby_children": 6
}
```
- **require_held_item**: Both parents must be holding this item.
- **consume_item**: If `true`, the item is removed from the mob after breeding.

---

## 8. Example jsons

### Cow fear of player
This json will make cow walk away from the player.
```json
{
    "group_id": "guide_test:cow_simple_player",
    "mobs": [
        {
            "id": "minecraft:cow"
        }
    ],
    "stages": [
        {
            "flee_speed": 1.0,
            "search_radius": 12,
            "fears": [
                {
                    "type": "entity",
                    "id": "minecraft:player",
                    "visibility_mode": "LOOK_BASED"
                }
            ]
        }
    ]
}
```
The cow will walk away at normal walking speed **"flee_speed"** from the player if the cow see the player **""visibility_mode": "LOOK_BASED""** within the **"search_radius"** of 12 blocks.

### Cow fear drowned when it is raining
This json will make the cow run away from the drowned when it is raining.
```json
{
    "group_id": "guide_test:cow_storm_drowned",
    "conditions": [
        {
            "type": "is_raining",
            "value": true
        }
    ],
    "mobs": [
        {
            "id": "minecraft:cow"
        }
    ],
    "stages": [
        {
            "flee_speed": 1.5,
            "search_radius": 20,
            "fears": [
                {
                    "type": "entity",
                    "id": "minecraft:drowned",
                    "visibility_mode": "ALWAYS"
                }
            ]
        }
    ]
}
```
The cow will run away from the drowned when it is raining **"type": "is_raining"** **"value": true** regardless of seeing the drowned or not **""visibility_mode": "ALWAYS""**

### Zombie fear any entity holding diamondsword when spotted.
This json will make the zombie fear of the diamond sword holder if it is not spotted by the entity holding the diamond sword.
```json
{
    "group_id": "guide_test:zombie_mutual_vision",
    "mobs": [
        {
            "id": "minecraft:zombie"
        }
    ],
    "stages": [
        {
            "flee_speed": 1.6,
            "search_radius": 16,
            "fears": [
                {
                    "type": "item",
                    "id": "minecraft:diamond_sword",
                    "fear_override": true,
                    "mutual_vision": true
                }
            ]
        }
    ]
}
```

The zombie will run at **"flee_speed"** if it is seen by the diamond sword holder in 16 blocks **"search_radius"** due to **"mutual_vision": true** and **"fear_override": true**. Without **"mutual_vision": true**, the zombie will run away even if the entity does not spot it. Without **"fear_override": true**, the zombie is able to attack. 