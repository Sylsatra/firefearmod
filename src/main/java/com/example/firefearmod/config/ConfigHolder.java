package com.example.firefearmod.config;

import com.electronwill.nightconfig.core.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConfigHolder {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> CONFIG_SCHEMA_VERSION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Config>> FEAR_GROUPS;
    public static final ForgeConfigSpec.BooleanValue SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF;
    public static final ForgeConfigSpec.IntValue SCAN_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PLAYER_CHECK_RADIUS;
    public static final ForgeConfigSpec.IntValue PLAYER_CHECK_VERTICAL;
    public static final ForgeConfigSpec.IntValue BLOCK_CHECK_PLAYER_RADIUS;
    public static final ForgeConfigSpec.IntValue LIGHT_CHECK_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue INTERGENERATIONAL_TRAUMA_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_TRAUMA_STAGES_PER_GROUP;
    public static final ForgeConfigSpec.BooleanValue CHECK_ALL_MOB_HELD_ITEMS;
    public static final ForgeConfigSpec.IntValue MOB_HELD_ITEM_CHECK_RADIUS;
    public static final ForgeConfigSpec.IntValue CACHE_TTL_TICKS;

    private static String targetSchemaVersion = "";

    static {
        CONFIG_SCHEMA_VERSION = BUILDER
                .comment("Tracks the mod version that last migrated this config. Do not edit manually unless you know what you are doing.")
                .define("config_schema_version", "");
        BUILDER.push("Fear Groups");
        FEAR_GROUPS = BUILDER
                .comment("""
                        A list of fear groups. Each group defines a set of mobs and what they fear.
                        Each group has:
                         - group_id (string, required): A unique name for the group.
                         - flee_speed (double, optional, default: 1.2): How fast mobs in this group flee.
                         - search_radius (int, optional, default: 8): How far mobs in this group search for threats.
                         - mobs (list, required): A list of mobs in this group. Can specify 'id' and 'nbt'.
                         - feared_blocks (list, required): Blocks this group fears. Can specify 'id', 'states', 'nbt' (for block entities), and 'fear_override' (boolean).
                         - feared_items (list, required): Items this group fears. Can specify 'id', 'nbt', and 'fear_override' (boolean).
                         - feared_entities (list, optional): Entities this group fears. Supports 'id', 'nbt', 'custom_name', and 'fear_override' (boolean, default: false) to override hostility when visible.
                         - light_fear (object, optional): Per-group light level avoidance config. When omitted or disabled, light fear is off.
                           Fields:
                             - enabled (boolean, default: false)
                             - mode (string, default: "ABOVE"): "ABOVE" to avoid bright areas where brightness >= threshold, or "BELOW" to avoid where brightness <= threshold
                             - threshold (int, default: 11): Light threshold in [0, 15]
                             - layer (string, default: "COMBINED"): "COMBINED" brightness (works indoors/outdoors), or "BLOCK"/"SKY"
                             - hysteresis (int, optional, default: 1): Margin to prevent jitter when entering/leaving boundary
                        """)
                .defineList("fear_groups", new ArrayList<>(), obj -> obj instanceof Config);
        BUILDER.pop();
        BUILDER.push("Optimizations");
        SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF = BUILDER.define("skipBlockCheckIfFireTickOff", true);
        SCAN_COOLDOWN_TICKS = BUILDER.defineInRange("scanCooldownTicks", 15, 1, 200);
        PLAYER_CHECK_RADIUS = BUILDER.defineInRange("playerCheckRadius", 8, 1, 64);
        PLAYER_CHECK_VERTICAL = BUILDER.defineInRange("playerCheckVertical", 4, 1, 64);
        BLOCK_CHECK_PLAYER_RADIUS = BUILDER.defineInRange("blockCheckPlayerRadius", 16, 1, 64);
        LIGHT_CHECK_COOLDOWN_TICKS = BUILDER.defineInRange("lightCheckCooldownTicks", 20, 1, 200);
        CHECK_ALL_MOB_HELD_ITEMS = BUILDER
                .comment("If true, check held items of ALL mobs (expensive). If false, only check Players' held items.")
                .define("checkAllMobHeldItems", false);
        MOB_HELD_ITEM_CHECK_RADIUS = BUILDER
                .comment("Only check held items of mobs within this radius of any player (performance optimization).")
                .defineInRange("mobHeldItemCheckRadius", 32, 8, 128);
        CACHE_TTL_TICKS = BUILDER
                .comment("How often to clear the per-mob fear/temptation cache (in ticks). Lower = more responsive, higher = better performance.")
                .defineInRange("cacheTTLTicks", 20, 5, 100);
        BUILDER.pop();
        BUILDER.push("IntergenerationalTrauma");
        INTERGENERATIONAL_TRAUMA_ENABLED = BUILDER.define("intergenerationalTraumaEnabled", false);
        MAX_TRAUMA_STAGES_PER_GROUP = BUILDER.defineInRange("maxTraumaStagesPerGroup", 3, 1, 32);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static void setDefaults() {
        if (FEAR_GROUPS.get().isEmpty()) {
            List<Config> defaultGroups = new ArrayList<>();
            defaultGroups.add(createDefaultUndeadGroup());
            defaultGroups.add(createDefaultArthropodGroup());
            defaultGroups.add(createDefaultTeachingGroup());
            defaultGroups.add(createDefaultVillagerLowLightGroup());
            defaultGroups.add(createDefaultPassiveGroup());
            defaultGroups.add(createDefaultSporeGroup());
            FEAR_GROUPS.set(defaultGroups);
            FEAR_GROUPS.save();
        }
    }

    private static Config createDefaultUndeadGroup() {
        Config group = Config.inMemory();
        group.set("group_id", "hostile_fire_fear");
        group.set("flee_speed", 1.15);
        group.set("search_radius", 10);
        group.set("mobs", List.of(
            createMobDef("minecraft:zombie"),
            createMobDef("minecraft:skeleton"),
            createMobDef("minecraft:husk"),
            createMobDef("minecraft:stray"),
            createMobDef("minecraft:zombie_villager"),
            createMobDef("minecraft:phantom"),
            createMobDef("minecraft:drowned"),
            createMobDef("minecraft:piglin"),
            createMobDef("minecraft:piglin_brute"),
            createMobDef("minecraft:vindicator"),
            createMobDef("minecraft:evoker"),
            createMobDef("minecraft:illusioner"),
            createMobDef("minecraft:ravager"),
            createMobDef("minecraft:pillager"),
            createMobDef("minecraft:witch"),
            createMobDef("minecraft:vex"),
            createMobDef("minecraft:warden"),
            createMobDef("minecraft:wither_skeleton"),
            createMobDef("minecraft:slime"),
            createMobDef("minecraft:creeper")
        ));
        group.set("feared_blocks", List.of(
            createFearSourceDef("minecraft:fire"),
            createFearSourceDef("minecraft:soul_fire"),
            createFearSourceDef("minecraft:lava"),
            createFearSourceDef("minecraft:magma_block"),
            createFearSourceDef("minecraft:tnt"),
            createFearSourceDef("minecraft:end_crystal"),
            createFearSourceDef("minecraft:tnt_minecart"),
            createFearSourceDefWithStates("minecraft:campfire", Map.of("lit", "true")),
            createFearSourceDefWithStates("minecraft:soul_campfire", Map.of("lit", "true"))
        ));
        group.set("feared_items", List.of(
            createFearSourceDef("minecraft:flint_and_steel")
        ));
        return group;
    }

    private static Config createDefaultVillagerLowLightGroup() {
        Config group = Config.inMemory();
        group.set("group_id", "villager_low_light_fear");
        group.set("flee_speed", 1.05);
        group.set("search_radius", 12);
        group.set("mobs", List.of(
            createMobDef("minecraft:villager")
        ));
        group.set("feared_blocks", List.of());
        group.set("feared_items", List.of());

        Config lf = group.createSubConfig();
        lf.set("enabled", true);
        lf.set("mode", "BELOW");
        lf.set("threshold", 6);     
        lf.set("layer", "COMBINED");
        lf.set("hysteresis", 2);
        group.set("light_fear", lf);
        return group;
    }

    private static Config createDefaultArthropodGroup() {
        Config group = Config.inMemory();
        group.set("group_id", "arthropod_custom_fear");
        group.set("flee_speed", 1.2);
        group.set("search_radius", 8);
        group.set("mobs", List.of(
            createMobDef("minecraft:spider"),
            createMobDef("minecraft:cave_spider"),
            createMobDef("minecraft:silverfish"),
            createMobDefWithNbt("minecraft:endermite", "{PlayerSpawned:1b}")
        ));
        group.set("feared_blocks", List.of(
            createFearSourceDef("minecraft:magma_block")
        ));
        group.set("feared_items", List.of(
            createFearSourceDefWithNbt("minecraft:stone_sword", "{Enchantments:[{id:\"minecraft:bane_of_arthropods\"}]}")
        ));
        return group;
    }
    private static Config createDefaultTeachingGroup() {
        Config group = Config.inMemory();
        group.set("group_id", "teaching_example_zombies");
        group.set("flee_speed", 2.0);
        group.set("search_radius", 16);

        group.set("mobs", List.of(

            createMobDefWithNbt("minecraft:zombie", "{Silent:1b}"),

            createMobDefWithCustomName("minecraft:drowned", "Patches")
        ));

        group.set("feared_blocks", List.of(
            createFearSourceDef("minecraft:beacon"),

            createFearSourceDefWithStates("minecraft:conduit", Map.of("waterlogged", "false")),

            createFearSourceDefWithNbt("minecraft:spawner", "{SpawnData:{entity:{id:\"minecraft:skeleton\"}}}")
        ));

        group.set("feared_items", List.of(
        createFearSourceDef("minecraft:golden_apple"),

            createFearSourceDefWithNbt("minecraft:netherite_sword", "{Enchantments:[{id:\"minecraft:smite\"}]}"),

            createFearSourceDefWithCustomName("minecraft:paper", "Exorcism Scroll")
        ));

        group.set("feared_entities", List.of(
            createFearedEntityDefWithOverride("minecraft:wolf"),
            createFearedEntityDefWithNbt("minecraft:endermite", "{PlayerSpawned:1b}", false),
            createFearedEntityDef("#minecraft:skeletons"),
            createProfessorFearEntity()
        ));

        return group;
    }

        private static Config createDefaultPassiveGroup() {
        Config group = Config.inMemory();
        group.set("group_id", "scaredy_cat");
        group.set("flee_speed", 1.25);
        group.set("search_radius", 12);
        group.set("mobs", List.of(
            createMobDef("minecraft:cat"),
            createMobDef("minecraft:chicken"),
            createMobDef("minecraft:cod"),
            createMobDef("minecraft:cow"),
            createMobDef("minecraft:dolphin"),
            createMobDef("minecraft:donkey"),
            createMobDef("minecraft:horse"),
            createMobDef("minecraft:mooshroom"),
            createMobDef("minecraft:mule"),
            createMobDef("minecraft:ocelot"),
            createMobDef("minecraft:parrot"),
            createMobDef("minecraft:pig"),
            createMobDef("minecraft:rabbit"),
            createMobDef("minecraft:salmon"),
            createMobDef("minecraft:sheep"),
            createMobDef("minecraft:squid"),
            createMobDef("minecraft:tropical_fish"),
            createMobDef("minecraft:turtle"),
            createMobDef("minecraft:glow_squid"),
            createMobDef("minecraft:pufferfish"),
            createMobDef("minecraft:axolotl"),
            createMobDef("minecraft:fox"),
            createMobDef("minecraft:goat"),
            createMobDef("minecraft:llama"),
            createMobDef("minecraft:panda"),
            createMobDef("minecraft:polar_bear"),
            createMobDef("minecraft:bat"),
            createMobDef("minecraft:bee"),
            createMobDef("minecraft:villager"),
            createMobDef("minecraft:wandering_trader"),
            createMobDef("minecraft:trader_llama"),
            createMobDef("minecraft:iron_golem"),
            createMobDef("minecraft:snow_golem"),
            createMobDef("minecraft:allay")
        ));
        group.set("feared_blocks", List.of(
            createFearSourceDef("minecraft:fire"),
            createFearSourceDef("minecraft:soul_fire"),
            createFearSourceDef("minecraft:lava"),
            createFearSourceDefWithStates("minecraft:campfire", Map.of("lit", "true")),
            createFearSourceDefWithStates("minecraft:soul_campfire", Map.of("lit", "true"))
        ));
        group.set("feared_items", List.of(
            createFearSourceDef("minecraft:flint_and_steel"),
            createFearSourceDef("minecraft:wooden_sword"),
            createFearSourceDef("minecraft:wooden_axe"),
            createFearSourceDef("minecraft:stone_sword"),
            createFearSourceDef("minecraft:stone_axe"),
            createFearSourceDef("minecraft:iron_sword"),
            createFearSourceDef("minecraft:iron_axe"),
            createFearSourceDef("minecraft:golden_sword"),
            createFearSourceDef("minecraft:golden_axe"),
            createFearSourceDef("minecraft:diamond_sword"),
            createFearSourceDef("minecraft:diamond_axe"),
            createFearSourceDef("minecraft:netherite_sword"),
            createFearSourceDef("minecraft:netherite_axe"),
            createFearSourceDef("minecraft:trident"),
            createFearSourceDef("minecraft:bow"),
            createFearSourceDef("minecraft:crossbow")
        ));
        return group;
    }

        private static Config createDefaultSporeGroup() {
        Config group = Config.inMemory();
        group.set("group_id", "Spore_fire_fear");
        group.set("flee_speed", 1.2);
        group.set("search_radius", 12);
        group.set("mobs", List.of(
            createMobDef("spore:braiomil"),
            createMobDef("spore:braurei"),
            createMobDef("spore:brot"),
            createMobDef("spore:brute"),
            createMobDef("spore:busser"),
            createMobDef("spore:inf_construct"),
            createMobDef("spore:delusioner"),
            createMobDef("spore:gastgaber"),
            createMobDef("spore:gazenbreacher"),
            createMobDef("spore:griefer"),
            createMobDef("spore:hevoker"),
            createMobDef("spore:hidenburg"),
            createMobDef("spore:howitzer"),
            createMobDef("spore:howler"),
            createMobDef("spore:hvindicator"),
            createMobDef("spore:inf_drownded"),
            createMobDef("spore:inf_evoker"),
            createMobDef("spore:inf_hazmat"),
            createMobDef("spore:husk"),
            createMobDef("spore:inf_pillager"),
            createMobDef("spore:inf_player"),
            createMobDef("spore:inf_villager"),
            createMobDef("spore:inf_vindicator"),
            createMobDef("spore:inf_wanderer"),
            createMobDef("spore:inf_witch"),
            createMobDef("spore:inf_human"),
            createMobDef("spore:jagd"),
            createMobDef("spore:knight"),
            createMobDef("spore:lacerator"),
            createMobDef("spore:inquisitor"),
            createMobDef("spore:leaper"),
            createMobDef("spore:mound"),
            createMobDef("spore:nuclea"),
            createMobDef("spore:ogre"),
            createMobDef("spore:plagued"),
            createMobDef("spore:proto"),
            createMobDef("spore:reconstructor"),
            createMobDef("spore:scamper"),
            createMobDef("spore:scavenger"),
            createMobDef("spore:scent"),
            createMobDef("spore:sieger"),
            createMobDef("spore:specter"),
            createMobDef("spore:spitter"),
            createMobDef("spore:stalker"),
            createMobDef("spore:thorn"),
            createMobDef("spore:umarmed"),
            createMobDef("spore:usurper"),
            createMobDef("spore:verva"),
            createMobDef("spore:vigil"),
            createMobDef("spore:volatile"),
            createMobDef("spore:wendigo")
        ));
        group.set("feared_blocks", List.of(
            createFearSourceDef("minecraft:fire"),
            createFearSourceDef("minecraft:soul_fire"),
            createFearSourceDef("minecraft:lava"),
            createFearSourceDef("minecraft:magma_block"),
            createFearSourceDef("minecraft:tnt"),
            createFearSourceDef("minecraft:end_crystal"),
            createFearSourceDef("minecraft:tnt_minecart"),
            createFearSourceDefWithStates("minecraft:campfire", Map.of("lit", "true")),
            createFearSourceDefWithStates("minecraft:soul_campfire", Map.of("lit", "true"))
        ));
        group.set("feared_items", List.of(
            createFearSourceDef("minecraft:flint_and_steel")
        ));
        return group;
    }

    private static Config createMobDef(String id) {
        Config table = Config.inMemory();
        table.set("id", id);
        return table;
    }

    private static Config createMobDefWithNbt(String id, String nbt) {
        Config table = createMobDef(id);
        table.set("nbt", nbt);
        return table;
    }

    private static Config createFearSourceDef(String id) {
        Config table = Config.inMemory();
        table.set("id", id);
        return table;
    }

    private static Config createFearSourceDefWithNbt(String id, String nbt) {
        Config table = createFearSourceDef(id);
        table.set("nbt", nbt);
        return table;
    }

    private static Config createFearSourceDefWithStates(String id, Map<String, String> states) {
        Config table = Config.inMemory();
        table.set("id", id);
        Config statesTable = table.createSubConfig();
        states.forEach(statesTable::set);
        table.set("states", statesTable);
        return table;
    }

    private static Config createFearSourceDefWithCustomName(String id, String name) {
        Config table = createFearSourceDef(id);
        table.set("custom_name", name);
        return table;
    }

    private static Config createMobDefWithCustomName(String id, String name) {
        Config table = createMobDef(id);
        table.set("custom_name", name);
        return table;
    }

    private static Config createFearedEntityDef(String id) {
        Config table = Config.inMemory();
        table.set("id", id);
        return table;
    }

    private static Config createFearedEntityDefWithOverride(String id) {
        Config table = createFearedEntityDef(id);
        table.set("fear_override", true);
        return table;
    }

    private static Config createFearedEntityDefWithCustomName(String id, String name, boolean fearOverride) {
        Config table = createFearedEntityDef(id);
        table.set("custom_name", name);
        if (fearOverride) {
            table.set("fear_override", true);
        }
        return table;
    }

    private static Config createFearedEntityDefWithNbt(String id, String nbt, boolean fearOverride) {
        Config table = createFearedEntityDef(id);
        table.set("nbt", nbt);
        if (fearOverride) {
            table.set("fear_override", true);
        }
        return table;
    }

    private static Config createProfessorFearEntity() {
        Config table = createFearedEntityDefWithCustomName("minecraft:player", "Professor Fear", true);
        table.set("nbt", "{Tags:[\"fear_trainer\"]}");
        return table;
    }

    public static void setTargetSchemaVersion(String version) {
        targetSchemaVersion = Objects.requireNonNullElse(version, "");
    }

    public static void onConfigReload(ModConfig config) {
        if (config.getSpec() != SPEC) {
            return;
        }
        setDefaults();
        migrateIfNeeded();
    }

    private static void migrateIfNeeded() {
        String desiredVersion = targetSchemaVersion == null ? "" : targetSchemaVersion;
        String storedVersion = CONFIG_SCHEMA_VERSION.get();
        if (Objects.equals(storedVersion, desiredVersion)) {
            return;
        }

        boolean updatedTeachingGroup = updateTeachingExampleGroup();

        CONFIG_SCHEMA_VERSION.set(desiredVersion);
        CONFIG_SCHEMA_VERSION.save();
        if (updatedTeachingGroup) {
            FEAR_GROUPS.save();
        }
    }

    private static boolean updateTeachingExampleGroup() {
        boolean changed = false;
        List<? extends Config> groups = FEAR_GROUPS.get();
        for (Config group : groups) {
            String id = Objects.toString(group.get("group_id"), "");
            if (!"teaching_example_zombies".equals(id)) {
                continue;
            }
            group.set("feared_entities", List.of(
                    createFearedEntityDefWithOverride("minecraft:wolf"),
                    createFearedEntityDefWithNbt("minecraft:endermite", "{PlayerSpawned:1b}", false),
                    createFearedEntityDef("#minecraft:skeletons"),
                    createProfessorFearEntity()
            ));
            changed = true;
            break;
        }
        return changed;
    }
}