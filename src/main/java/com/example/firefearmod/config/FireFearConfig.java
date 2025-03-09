package com.example.firefearmod.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.*;

public class FireFearConfig {

    private static final String CONFIG_FILE_NAME = "firefearmod.toml";

    public static List<String> FLEEING_ENTITIES = new ArrayList<>();
    public static Set<Block> BLOCKS_TO_FEAR = new HashSet<>();
    public static Set<Item> ITEMS_TO_FEAR = new HashSet<>();

    public static boolean SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF = true;
    public static int SCAN_COOLDOWN_TICKS = 20;
    public static int PLAYER_CHECK_RADIUS = 4;
    public static int PLAYER_CHECK_VERTICAL = 2;
    public static int BLOCK_CHECK_PLAYER_RADIUS = 16;

    public static void loadConfig() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        CommentedFileConfig data = CommentedFileConfig.builder(configPath)
            .autosave()
            .autoreload()
            .build();
        data.load();

        // Manually fill the list of mobs that are scared of fire.
        // Excluded (not listed): creeper, blaze, zombified_piglin, magma_cube, ender_dragon, wither, guardian, elder_guardian,
        // all fish (cod, salmon, tropical_fish, pufferfish), dolphin, turtle, villager, vindicator.
        if (!data.contains("fearfire.entities")) {
            data.set("fearfire.entities", Arrays.asList(
                "minecraft:bat",
                "minecraft:bee",
                "minecraft:cat",
                "minecraft:cow",
                "minecraft:chicken",
                "minecraft:donkey",
                "minecraft:fox",
                "minecraft:horse",
                "minecraft:llama",
                "minecraft:mooshroom",
                "minecraft:mule",
                "minecraft:parrot",
                "minecraft:pig",
                "minecraft:rabbit",
                "minecraft:zombie",
                "minecraft:skeleton",
                "minecraft:cave_spider",
                "minecraft:spider",
                "minecraft:witch",
                "minecraft:slime",
                "minecraft:zombie_villager",
                "minecraft:enderman",
                "minecraft:ghast",
                "minecraft:ravager",
                "minecraft:evoker",
                "minecraft:pillager",
                "minecraft:illusioner",
                "minecraft:wither_skeleton",
                "minecraft:snow_golem",
                "minecraft:polar_bear",
                "minecraft:endermite",
                "minecraft:squid",
                "minecraft:glow_squid"
            ));
        }
        data.setComment("fearfire.entities",
                "A list of entity IDs (e.g. 'minecraft:pig') that will have the Fear AI.\n" +
                "By default, every mob is scared of fire except: creeper, blaze, zombified_piglin, magma_cube, " +
                "ender_dragon, wither, guardian, elder_guardian, all fish, dolphin, turtle, villager, and vindicator."
        );

        if (!data.contains("fearfire.blocks")) {
            data.set("fearfire.blocks", Arrays.asList(
                "minecraft:fire",
                "minecraft:campfire",
                "minecraft:soul_fire",
                "minecraft:soul_campfire"
            ));
        }
        data.setComment("fearfire.blocks",
            "Any block in this list will scare the entity if found within the search radius."
        );

        if (!data.contains("fearfire.items")) {
            data.set("fearfire.items", Arrays.asList("minecraft:flint_and_steel"));
        }
        data.setComment("fearfire.items",
            "Any item in this list will scare the entity if a nearby player is holding it (main or off hand)."
        );

        if (!data.contains("fearfire.optimizations.skipBlockCheckIfFireTickOff")) {
            data.set("fearfire.optimizations.skipBlockCheckIfFireTickOff", true);
        }
        data.setComment("fearfire.optimizations.skipBlockCheckIfFireTickOff",
            "If true, skip block checks if the gamerule 'doFireTick' is off."
        );

        if (!data.contains("fearfire.optimizations.scanCooldownTicks")) {
            data.set("fearfire.optimizations.scanCooldownTicks", 20);
        }
        data.setComment("fearfire.optimizations.scanCooldownTicks",
            "Number of ticks between scanning for threats. 20 = 1 second."
        );

        if (!data.contains("fearfire.optimizations.playerCheckRadius")) {
            data.set("fearfire.optimizations.playerCheckRadius", 4);
        }
        data.setComment("fearfire.optimizations.playerCheckRadius",
            "Horizontal radius to find players holding feared items."
        );

        if (!data.contains("fearfire.optimizations.playerCheckVerticalRange")) {
            data.set("fearfire.optimizations.playerCheckVerticalRange", 2);
        }
        data.setComment("fearfire.optimizations.playerCheckVerticalRange",
            "Vertical (Y) range to check for item-holding players."
        );

        if (!data.contains("fearfire.optimizations.blockCheckPlayerRadius")) {
            data.set("fearfire.optimizations.blockCheckPlayerRadius", 16);
        }
        data.setComment("fearfire.optimizations.blockCheckPlayerRadius",
            "Only check for fear blocks if there's at least one player within this radius (X,Z ±, Y ±2). " +
            "If no player is near, we skip block checks to save CPU."
        );

        data.save();

        FLEEING_ENTITIES = new ArrayList<>(data.get("fearfire.entities"));

        List<String> blocksList = data.get("fearfire.blocks");
        Set<Block> tmpBlocks = new HashSet<>();
        for (String idStr : blocksList) {
            Block b = Registry.BLOCK.getOptional(new ResourceLocation(idStr)).orElse(null);
            if (b != null) tmpBlocks.add(b);
        }
        BLOCKS_TO_FEAR = tmpBlocks;

        List<String> itemsList = data.get("fearfire.items");
        Set<Item> tmpItems = new HashSet<>();
        for (String idStr : itemsList) {
            Item i = Registry.ITEM.getOptional(new ResourceLocation(idStr)).orElse(null);
            if (i != null) tmpItems.add(i);
        }
        ITEMS_TO_FEAR = tmpItems;

        SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF = data.get("fearfire.optimizations.skipBlockCheckIfFireTickOff");
        SCAN_COOLDOWN_TICKS = data.get("fearfire.optimizations.scanCooldownTicks");
        PLAYER_CHECK_RADIUS = data.get("fearfire.optimizations.playerCheckRadius");
        PLAYER_CHECK_VERTICAL = data.get("fearfire.optimizations.playerCheckVerticalRange");
        BLOCK_CHECK_PLAYER_RADIUS = data.get("fearfire.optimizations.blockCheckPlayerRadius");

        data.close();

        System.out.println("[FireFearConfig] Entities: " + FLEEING_ENTITIES);
        System.out.println("[FireFearConfig] Blocks to fear: " + blocksList);
        System.out.println("[FireFearConfig] Items to fear: " + itemsList);
        System.out.println("[FireFearConfig] skipBlockCheckIfFireTickOff? " + SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF);
        System.out.println("[FireFearConfig] scanCooldownTicks = " + SCAN_COOLDOWN_TICKS);
        System.out.println("[FireFearConfig] playerCheckRadius = " + PLAYER_CHECK_RADIUS);
        System.out.println("[FireFearConfig] playerCheckVertical = " + PLAYER_CHECK_VERTICAL);
        System.out.println("[FireFearConfig] blockCheckPlayerRadius = " + BLOCK_CHECK_PLAYER_RADIUS);
    }
}
