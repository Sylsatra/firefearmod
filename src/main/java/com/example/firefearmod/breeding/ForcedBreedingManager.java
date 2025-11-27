package com.example.firefearmod.breeding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForcedBreedingManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "forced_breeding";

    private static final Map<ResourceLocation, List<ForcedBreedingRule>> RULES_BY_MOB = new HashMap<>();
    private static final Map<ResourceLocation, List<ForcedBreedingRule>> RULES_BY_ITEM = new HashMap<>();

    public ForcedBreedingManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        RULES_BY_MOB.clear();
        RULES_BY_ITEM.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), "forced_breeding_rule");
                boolean enabled = obj.has("enabled") ? GsonHelper.getAsBoolean(obj, "enabled") : true;
                String mobStr = GsonHelper.getAsString(obj, "mob");
                String childStr = obj.has("child") ? GsonHelper.getAsString(obj, "child") : mobStr;

                // Optional player-held item (kept for backwards compatibility, but ignored by rule lookup)
                String itemStr = obj.has("item") ? GsonHelper.getAsString(obj, "item") : null;
                // New: optional required held item on parents
                String requiredHeldStr = obj.has("require_held_item") ? GsonHelper.getAsString(obj, "require_held_item") : null;

                ResourceLocation mobId = new ResourceLocation(mobStr);
                ResourceLocation childId = new ResourceLocation(childStr);
                ResourceLocation itemId = itemStr != null ? new ResourceLocation(itemStr) : null;
                ResourceLocation requiredHeldItem = requiredHeldStr != null ? new ResourceLocation(requiredHeldStr) : null;

                double partnerRadius = obj.has("partner_radius") ? GsonHelper.getAsDouble(obj, "partner_radius") : 8.0D;
                int cooldownTicks = obj.has("cooldown_ticks") ? GsonHelper.getAsInt(obj, "cooldown_ticks") : 6000;
                int maxNearbyChildren = obj.has("max_nearby_children") ? GsonHelper.getAsInt(obj, "max_nearby_children") : 6;
                boolean consumeItem = obj.has("consume_item") && GsonHelper.getAsBoolean(obj, "consume_item");

                ForcedBreedingRule rule = new ForcedBreedingRule(fileId, enabled, itemId, mobId, childId, partnerRadius, cooldownTicks, maxNearbyChildren, consumeItem, requiredHeldItem);
                RULES_BY_MOB.computeIfAbsent(mobId, k -> new ArrayList<>()).add(rule);
                if (itemId != null) {
                    RULES_BY_ITEM.computeIfAbsent(itemId, k -> new ArrayList<>()).add(rule);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse forced breeding rule JSON {}: {}", fileId, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} forced breeding mob entries.", RULES_BY_MOB.size());
    }

    public static Collection<ForcedBreedingRule> getAllRules() {
        List<ForcedBreedingRule> all = new ArrayList<>();
        for (List<ForcedBreedingRule> list : RULES_BY_MOB.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableCollection(all);
    }

    public static List<ForcedBreedingRule> getRulesFor(Mob mob, ItemStack stack) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) {
            return List.of();
        }
        List<ForcedBreedingRule> byMob = RULES_BY_MOB.get(mobId);
        if (byMob == null || byMob.isEmpty()) {
            return List.of();
        }
        List<ForcedBreedingRule> result = new ArrayList<>();
        for (ForcedBreedingRule rule : byMob) {
            if (!rule.enabled()) {
                continue;
            }
            // We intentionally ignore the player's held item; forced breeding is controlled by parent-held items only.
            result.add(rule);
        }
        return result;
    }
}
