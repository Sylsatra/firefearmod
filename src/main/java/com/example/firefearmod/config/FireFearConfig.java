package com.example.firefearmod.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.core.registries.BuiltInRegistries; 
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

        if (!data.contains("fearfire.entities")) {
            data.set("fearfire.entities", Arrays.asList("minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned", "minecraft:endermite", "minecraft:evoker", "minecraft:guardian", "minecraft:hoglin", "minecraft:husk", "minecraft:magma_cube", "minecraft:phantom", "minecraft:piglin", "minecraft:piglin_brute", "minecraft:pillager", "minecraft:ravager", "minecraft:shulker", "minecraft:silverfish", "minecraft:skeleton", "minecraft:slime", "minecraft:spider", "minecraft:stray", "minecraft:vex", "minecraft:vindicator", "minecraft:witch", "minecraft:wither_skeleton", "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager", "scguns:cog_knight", "scguns:cog_minion", "scguns:blunderer", "scguns:hive", "scguns:dissident", "scguns:hornlin", "scguns:redcoat", "scguns:cog_knight", "scguns:sky_carrier", "scguns:supply_scamp", "scguns:swarm", "scguns:zombified_hornlin", "spore:braiomil", "spore:braurei", "spore:brot", "spore:brute", "spore:busser", "spore:inf_construct", "spore:delusioner", "spore:gastgaber", "spore:gazenbreacher", "spore:griefer", "spore:hevoker", "spore:hidenburg", "spore:howitzer", "spore:howler", "spore:hvindicator", "spore:illusion", "spore:inf_drownded", "spore:inf_evoker", "spore:inf_hazmat", "spore:husk", "spore:inf_pillager", "spore:inf_player", "spore:inf_villager", "spore:inf_vindicator", "spore:inf_wanderer", "spore:inf_witch", "spore:inf_human", "spore:inquisitor", "spore:jagd", "spore:knight", "spore:lacerator", "spore:leaper", "spore:mound", "spore:nuclea", "spore:ogre", "spore:plagued", "spore:proto", "spore:reconstructor", "spore:scamper", "spore:scavenger", "spore:scent", "spore:sieger", "spore:specter", "spore:spitter", "spore:stalker", "spore:thorn", "spore:umarmed", "spore:usurper", "spore:verva", "spore:vigil", "spore:volatile", "spore:wendigo", "minecraft:bat", "minecraft:cat", "minecraft:chicken", "minecraft:cod", "minecraft:cow", "minecraft:dolphin", "minecraft:donkey", "minecraft:horse", "minecraft:mooshroom", "minecraft:mule", "minecraft:ocelot", "minecraft:parrot", "minecraft:pig", "minecraft:rabbit", "minecraft:salmon", "minecraft:sheep", "minecraft:squid", "minecraft:tropical_fish", "minecraft:turtle", "minecraft:glow_squid", "minecraft:pufferfish", "minecraft:axolotl", "minecraft:fox", "minecraft:goat", "minecraft:llama", "minecraft:panda", "minecraft:polar_bear"));
        }
        data.setComment("fearfire.entities",
                "A list of entity IDs (e.g. 'minecraft:pig') that will have the Fear AI."
              + "\nOnly these entities will run away from blocks/items set below."
        );

        if (!data.contains("fearfire.blocks")) {
            data.set("fearfire.blocks", Arrays.asList(
                "minecraft:fire",
                "minecraft:campfire",
                "minecraft:soul_fire",
                "minecraft:soul_campfire",
                "minecraft:lava",
                "minecraft:end_crystal",
                "minecraft:magma_block"
            ));
        }
        data.setComment("fearfire.blocks",
            "Any block in this list will scare the entity if found within the search radius."
        );

        if (!data.contains("fearfire.items")) {
            data.set("fearfire.items", Arrays.asList("minecraft:end_crystal"));
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
            "Only check for fear blocks if there's at least one player within this radius (X,Z ±, Y ±2). "
          + "If no player is near, we skip block checks to save CPU."
        );

        data.save();

        FLEEING_ENTITIES = new ArrayList<>(data.get("fearfire.entities"));

        List<String> blocksList = data.get("fearfire.blocks");
        Set<Block> tmpBlocks = new HashSet<>();
        for (String idStr : blocksList) {
            Block b = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(idStr)).orElse(null);
            if (b != null) tmpBlocks.add(b);
        }
        BLOCKS_TO_FEAR = tmpBlocks;

        List<String> itemsList = data.get("fearfire.items");
        Set<Item> tmpItems = new HashSet<>();
        for (String idStr : itemsList) {
            Item i = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(idStr)).orElse(null);
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