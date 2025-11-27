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
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NaturalBreedingManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "natural_breeding";

    private static final Map<ResourceLocation, List<NaturalBreedingRule>> RULES_BY_MOB = new HashMap<>();

    public NaturalBreedingManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        RULES_BY_MOB.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), "natural_breeding_rule");
                boolean enabled = obj.has("enabled") ? GsonHelper.getAsBoolean(obj, "enabled") : true;
                String mobStr = GsonHelper.getAsString(obj, "mob");
                String childStr = obj.has("child") ? GsonHelper.getAsString(obj, "child") : mobStr;

                ResourceLocation mobId = new ResourceLocation(mobStr);
                ResourceLocation childId = new ResourceLocation(childStr);

                double partnerRadius = obj.has("partner_radius") ? GsonHelper.getAsDouble(obj, "partner_radius") : 8.0D;
                int cooldownTicks = obj.has("cooldown_ticks") ? GsonHelper.getAsInt(obj, "cooldown_ticks") : 12000;
                double chancePerAttempt = obj.has("chance_per_attempt") ? GsonHelper.getAsDouble(obj, "chance_per_attempt") : 0.05D;
                int maxNearbyChildren = obj.has("max_nearby_children") ? GsonHelper.getAsInt(obj, "max_nearby_children") : 8;

                NaturalBreedingRule rule = new NaturalBreedingRule(fileId, enabled, mobId, childId, partnerRadius, cooldownTicks, chancePerAttempt, maxNearbyChildren);
                RULES_BY_MOB.computeIfAbsent(mobId, k -> new ArrayList<>()).add(rule);
            } catch (Exception e) {
                LOGGER.error("Failed to parse natural breeding rule JSON {}: {}", fileId, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} natural breeding mob entries.", RULES_BY_MOB.size());
    }

    public static Collection<NaturalBreedingRule> getAllRules() {
        List<NaturalBreedingRule> all = new ArrayList<>();
        for (List<NaturalBreedingRule> list : RULES_BY_MOB.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableCollection(all);
    }

    public static List<NaturalBreedingRule> getRulesForMob(Mob mob) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) {
            return List.of();
        }
        List<NaturalBreedingRule> list = RULES_BY_MOB.get(mobId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list;
    }
}
