package com.example.firefearmod.trauma;

import com.electronwill.nightconfig.core.Config;
import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.manager.IFearProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TraumaGroupManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "trauma_groups";

    private static final Map<ResourceLocation, TraumaGroup> GROUPS = new HashMap<>();

    public TraumaGroupManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        GROUPS.clear();
        int maxStages = ConfigHolder.MAX_TRAUMA_STAGES_PER_GROUP.get();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), "trauma_group");
                String groupIdStr = obj.has("group_id") ? GsonHelper.getAsString(obj, "group_id") : fileId.toString();
                ResourceLocation groupId = new ResourceLocation(groupIdStr);
                double defaultWitnessRadius = obj.has("default_witness_radius") ? GsonHelper.getAsDouble(obj, "default_witness_radius") : 16.0D;

                List<FearGroup.MobDefinition> mobs = parseMobs(obj, groupId);
                List<TraumaGroup.TraumaStage> stages = parseStages(obj, groupId, maxStages);
                List<TraumaGroup.TraumaCondition> conditions = parseConditions(obj, groupId);

                if (stages.isEmpty()) {
                    LOGGER.warn("Trauma group '{}' has no stages, skipping", groupId);
                    continue;
                }

                TraumaGroup group = new TraumaGroup(groupId, mobs, defaultWitnessRadius, stages, conditions);
                GROUPS.put(groupId, group);
            } catch (Exception e) {
                LOGGER.error("Failed to parse trauma group JSON {}: {}", fileId, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} trauma groups.", GROUPS.size());
    }

    private static List<FearGroup.MobDefinition> parseMobs(JsonObject root, ResourceLocation groupId) {
        List<FearGroup.MobDefinition> result = new ArrayList<>();
        if (!root.has("mobs")) {
            LOGGER.warn("Trauma group '{}' is missing 'mobs' array", groupId);
            return result;
        }
        JsonArray array = GsonHelper.getAsJsonArray(root, "mobs");
        for (JsonElement element : array) {
            JsonObject obj = GsonHelper.convertToJsonObject(element, "mob");
            String idStr = GsonHelper.getAsString(obj, "id");
            ResourceLocation id = new ResourceLocation(idStr);
            String customName = obj.has("custom_name") ? GsonHelper.getAsString(obj, "custom_name") : null;
            CompoundTag nbt = null;
            if (obj.has("nbt")) {
                String nbtStr = GsonHelper.getAsString(obj, "nbt");
                try {
                    nbt = TagParser.parseTag(nbtStr);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse NBT for mob definition in trauma group '{}': {}", groupId, e.getMessage());
                }
            }
            result.add(new FearGroup.MobDefinition(id, customName, nbt));
        }
        return result;
    }

    private static List<TraumaGroup.TraumaStage> parseStages(JsonObject root, ResourceLocation groupId, int maxStages) {
        List<TraumaGroup.TraumaStage> result = new ArrayList<>();
        if (!root.has("stages")) {
            LOGGER.warn("Trauma group '{}' is missing 'stages' array", groupId);
            return result;
        }
        JsonArray array = GsonHelper.getAsJsonArray(root, "stages");
        int limit = array.size();
        if (maxStages > 0 && maxStages < limit) {
            limit = maxStages;
        }
        for (int i = 0; i < limit; i++) {
            JsonElement element = array.get(i);
            JsonObject obj = GsonHelper.convertToJsonObject(element, "stage");
            Double fleeSpeed = obj.has("flee_speed") ? GsonHelper.getAsDouble(obj, "flee_speed") : null;
            Integer searchRadius = obj.has("search_radius") ? GsonHelper.getAsInt(obj, "search_radius") : null;
            Double witnessRadius = obj.has("witness_radius") ? GsonHelper.getAsDouble(obj, "witness_radius") : null;
            List<FearGroup.FearSourceDefinition> fearedBlocks = new ArrayList<>();
            List<FearGroup.FearSourceDefinition> fearedItems = new ArrayList<>();
            List<FearGroup.FearedEntityDefinition> fearedEntities = new ArrayList<>();
            List<TraumaGroup.TraumaRequirement> requirements = new ArrayList<>();

            parseFears(obj, groupId, i, fearedBlocks, fearedItems, fearedEntities);
            parseRequirements(obj, groupId, i, requirements);

            result.add(new TraumaGroup.TraumaStage(i, fleeSpeed, searchRadius, witnessRadius,
                    fearedBlocks, fearedItems, fearedEntities, requirements));
        }
        if (limit < array.size()) {
            LOGGER.warn("Trauma group '{}' has {} stages but only the first {} are used due to maxTraumaStagesPerGroup", groupId, array.size(), limit);
        }
        return result;
    }

    private static void parseFears(JsonObject stageObj, ResourceLocation groupId, int stageIndex,
                                   List<FearGroup.FearSourceDefinition> fearedBlocks,
                                   List<FearGroup.FearSourceDefinition> fearedItems,
                                   List<FearGroup.FearedEntityDefinition> fearedEntities) {
        if (!stageObj.has("fears")) {
            return;
        }
        JsonArray array = GsonHelper.getAsJsonArray(stageObj, "fears");
        for (JsonElement element : array) {
            JsonObject obj = GsonHelper.convertToJsonObject(element, "fear");
            String type = obj.has("type") ? GsonHelper.getAsString(obj, "type") : "block";
            String normalized = type.toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "block" -> {
                    FearGroup.FearSourceDefinition def = parseFearSourceDefinition(obj, groupId, stageIndex, "block");
                    if (def != null) {
                        fearedBlocks.add(def);
                    }
                }
                case "item" -> {
                    FearGroup.FearSourceDefinition def = parseFearSourceDefinition(obj, groupId, stageIndex, "item");
                    if (def != null) {
                        fearedItems.add(def);
                    }
                }
                case "entity" -> {
                    FearGroup.FearedEntityDefinition def = parseFearedEntityDefinition(obj, groupId, stageIndex);
                    if (def != null) {
                        fearedEntities.add(def);
                    }
                }
                default -> LOGGER.warn("Unknown fear type '{}' in trauma group '{}' stage {}", type, groupId, stageIndex);
            }
        }
    }

    @Nullable
    private static FearGroup.FearSourceDefinition parseFearSourceDefinition(JsonObject obj,
                                                                            ResourceLocation groupId,
                                                                            int stageIndex,
                                                                            String context) {
        String rawId = GsonHelper.getAsString(obj, "id");
        boolean isTag = false;
        if (rawId.startsWith("#")) {
            isTag = true;
            rawId = rawId.substring(1);
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(rawId);
        } catch (Exception e) {
            LOGGER.error("Invalid {} id '{}' in trauma group '{}' stage {}: {}", context, rawId, groupId, stageIndex, e.getMessage());
            return null;
        }

        String customName = obj.has("custom_name") ? GsonHelper.getAsString(obj, "custom_name") : null;
        boolean fearOverride = obj.has("fear_override") && GsonHelper.getAsBoolean(obj, "fear_override");
        boolean temptation = (obj.has("temptation") && GsonHelper.getAsBoolean(obj, "temptation")) ||
                             (obj.has("is_tempted_by") && GsonHelper.getAsBoolean(obj, "is_tempted_by"));
        boolean mutualVision = obj.has("mutual_vision") && GsonHelper.getAsBoolean(obj, "mutual_vision");

        Config statesCfg = null;
        if (obj.has("states")) {
            JsonObject statesObj = GsonHelper.getAsJsonObject(obj, "states");
            statesCfg = Config.inMemory();
            for (Map.Entry<String, JsonElement> entry : statesObj.entrySet()) {
                statesCfg.set(entry.getKey(), entry.getValue().getAsString());
            }
        }

        CompoundTag nbt = null;
        if (obj.has("nbt")) {
            String nbtStr = GsonHelper.getAsString(obj, "nbt");
            try {
                nbt = TagParser.parseTag(nbtStr);
            } catch (Exception e) {
                LOGGER.error("Failed to parse NBT for {} fear in trauma group '{}' stage {}: {}", context, groupId, stageIndex, e.getMessage());
            }
        }

        return new FearGroup.FearSourceDefinition(id, isTag, customName, statesCfg, nbt, fearOverride, temptation, mutualVision);
    }

    @Nullable
    private static FearGroup.FearedEntityDefinition parseFearedEntityDefinition(JsonObject obj,
                                                                                ResourceLocation groupId,
                                                                                int stageIndex) {
        String rawId = GsonHelper.getAsString(obj, "id");
        boolean isTag = false;
        if (rawId.startsWith("#")) {
            isTag = true;
            rawId = rawId.substring(1);
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(rawId);
        } catch (Exception e) {
            LOGGER.error("Invalid entity id '{}' in trauma group '{}' stage {}: {}", rawId, groupId, stageIndex, e.getMessage());
            return null;
        }

        String customName = obj.has("custom_name") ? GsonHelper.getAsString(obj, "custom_name") : null;
        boolean fearOverride = obj.has("fear_override") && GsonHelper.getAsBoolean(obj, "fear_override");
        CompoundTag nbt = null;
        if (obj.has("nbt")) {
            String nbtStr = GsonHelper.getAsString(obj, "nbt");
            try {
                nbt = TagParser.parseTag(nbtStr);
            } catch (Exception e) {
                LOGGER.error("Failed to parse NBT for entity fear in trauma group '{}' stage {}: {}", groupId, stageIndex, e.getMessage());
            }
        }

        String modeStr = obj.has("visibility_mode") ? GsonHelper.getAsString(obj, "visibility_mode") : "LOOK_BASED";
        IFearProfile.VisibilityMode mode;
        try {
            mode = IFearProfile.VisibilityMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = IFearProfile.VisibilityMode.LOOK_BASED;
        }

        FearGroup.FearSourceDefinition src = new FearGroup.FearSourceDefinition(id, isTag, customName, null, nbt, false, false, false);
        return new FearGroup.FearedEntityDefinition(src, fearOverride, mode);
    }

    private static void parseRequirements(JsonObject stageObj, ResourceLocation groupId, int stageIndex,
                                          List<TraumaGroup.TraumaRequirement> out) {
        if (!stageObj.has("requirements")) {
            return;
        }
        JsonArray array = GsonHelper.getAsJsonArray(stageObj, "requirements");
        for (JsonElement element : array) {
            JsonObject obj = GsonHelper.convertToJsonObject(element, "requirement");
            String typeStr = obj.has("type") ? GsonHelper.getAsString(obj, "type") : "";
            TraumaGroup.RequirementType type;
            switch (typeStr.toLowerCase(Locale.ROOT)) {
                case "hurt_by_entity" -> type = TraumaGroup.RequirementType.HURT_BY_ENTITY;
                case "hurt_by_source" -> type = TraumaGroup.RequirementType.HURT_BY_SOURCE;
                case "witness_hurt_by_entity" -> type = TraumaGroup.RequirementType.WITNESS_HURT_BY_ENTITY;
                case "witness_hurt_by_source" -> type = TraumaGroup.RequirementType.WITNESS_HURT_BY_SOURCE;
                default -> {
                    LOGGER.warn("Unknown requirement type '{}' in trauma group '{}' stage {}", typeStr, groupId, stageIndex);
                    continue;
                }
            }

            switch (type) {
                case HURT_BY_ENTITY, WITNESS_HURT_BY_ENTITY -> {
                    if (!obj.has("entity")) {
                        LOGGER.warn("Requirement of type '{}' in trauma group '{}' stage {} is missing 'entity' object", typeStr, groupId, stageIndex);
                        continue;
                    }
                    JsonObject entityObj = GsonHelper.getAsJsonObject(obj, "entity");
                    FearGroup.FearedEntityDefinition entityDef = parseFearedEntityDefinition(entityObj, groupId, stageIndex);
                    if (entityDef != null) {
                        out.add(new TraumaGroup.TraumaRequirement(type, entityDef, null));
                    }
                }
                case HURT_BY_SOURCE, WITNESS_HURT_BY_SOURCE -> {
                    if (!obj.has("source")) {
                        LOGGER.warn("Requirement of type '{}' in trauma group '{}' stage {} is missing 'source' object", typeStr, groupId, stageIndex);
                        continue;
                    }
                    JsonObject sourceRoot = GsonHelper.getAsJsonObject(obj, "source");
                    FearGroup.FearSourceDefinition sourceDef = null;
                    if (sourceRoot.has("item")) {
                        JsonObject itemObj = GsonHelper.getAsJsonObject(sourceRoot, "item");
                        sourceDef = parseFearSourceDefinition(itemObj, groupId, stageIndex, "item requirement");
                    } else if (sourceRoot.has("block")) {
                        JsonObject blockObj = GsonHelper.getAsJsonObject(sourceRoot, "block");
                        sourceDef = parseFearSourceDefinition(blockObj, groupId, stageIndex, "block requirement");
                    } else {
                        LOGGER.warn("Requirement of type '{}' in trauma group '{}' stage {} has 'source' without 'item' or 'block'", typeStr, groupId, stageIndex);
                    }
                    if (sourceDef != null) {
                        out.add(new TraumaGroup.TraumaRequirement(type, null, sourceDef));
                    }
                }
            }
        }
    }

    private static List<TraumaGroup.TraumaCondition> parseConditions(JsonObject root, ResourceLocation groupId) {
        List<TraumaGroup.TraumaCondition> result = new ArrayList<>();
        if (!root.has("conditions")) {
            return result;
        }
        JsonArray array = GsonHelper.getAsJsonArray(root, "conditions");
        for (JsonElement element : array) {
            JsonObject obj = GsonHelper.convertToJsonObject(element, "condition");
            String typeStr = GsonHelper.getAsString(obj, "type", "");
            TraumaGroup.ConditionType type;
            try {
                type = TraumaGroup.ConditionType.valueOf(typeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown condition type '{}' in trauma group '{}'", typeStr, groupId);
                continue;
            }

            Double min = obj.has("min") ? GsonHelper.getAsDouble(obj, "min") : null;
            Double max = obj.has("max") ? GsonHelper.getAsDouble(obj, "max") : null;
            Boolean boolValue = obj.has("value") ? GsonHelper.getAsBoolean(obj, "value") : null;
            
            result.add(new TraumaGroup.TraumaCondition(type, min, max, boolValue));
        }
        return result;
    }

    public static Collection<TraumaGroup> getAllGroups() {
        return Collections.unmodifiableCollection(GROUPS.values());
    }

    public static TraumaGroup getGroup(ResourceLocation id) {
        return GROUPS.get(id);
    }

    public static List<TraumaGroup> getGroupsForMob(Mob mob) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) {
            return List.of();
        }
        List<TraumaGroup> result = new ArrayList<>();
        for (TraumaGroup group : GROUPS.values()) {
            int bestScore = -1;
            for (FearGroup.MobDefinition def : group.mobs()) {
                int score = def.getMatchScore(mob, mobId);
                if (score > bestScore) {
                    bestScore = score;
                }
            }
            if (bestScore >= 0) {
                result.add(group);
            }
        }
        return result;
    }

    public static boolean isMobInGroup(Mob mob, ResourceLocation groupId) {
        TraumaGroup group = GROUPS.get(groupId);
        if (group == null) {
            return false;
        }
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) {
            return false;
        }
        int bestScore = -1;
        for (FearGroup.MobDefinition def : group.mobs()) {
            int score = def.getMatchScore(mob, mobId);
            if (score > bestScore) {
                bestScore = score;
            }
        }
        return bestScore >= 0;
    }
}
