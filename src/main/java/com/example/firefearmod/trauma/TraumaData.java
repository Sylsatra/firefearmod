package com.example.firefearmod.trauma;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TraumaData implements ITraumaData {
    private static final String STAGES_KEY = "stages";

    private final Map<ResourceLocation, Integer> stages = new HashMap<>();
    private final com.example.firefearmod.util.PerformanceCache cache = new com.example.firefearmod.util.PerformanceCache();
    private int tickCounter = 0;
    
    private int cachedDataVersion = -1;
    private java.util.List<ResourceLocation> cachedGroups = null;

    @Override
    public void tick() {
        tickCounter++;
        int ttl = com.example.firefearmod.config.ConfigHolder.CACHE_TTL_TICKS.get();
        if (tickCounter >= ttl) {
            tickCounter = 0;
            cache.clear();
        }
    }

    @Override
    public com.example.firefearmod.util.PerformanceCache getCache() {
        return cache;
    }

    @Override
    public int getStage(ResourceLocation groupId) {
        return stages.getOrDefault(groupId, 0);
    }

    @Override
    public void setStage(ResourceLocation groupId, int stage, int maxStages) {
        if (maxStages <= 0) {
            maxStages = 1;
        }
        int clamped = Math.max(0, Math.min(stage, maxStages - 1));
        if (clamped == 0) {
            stages.remove(groupId);
        } else {
            stages.put(groupId, clamped);
        }
    }

    @Override
    public Map<ResourceLocation, Integer> getAllStages() {
        return Collections.unmodifiableMap(stages);
    }

    @Override
    public void clampStages(int maxStages) {
        if (maxStages <= 0) {
            stages.clear();
            return;
        }
        int cap = maxStages - 1;
        stages.replaceAll((id, value) -> Math.max(0, Math.min(value, cap)));
        stages.entrySet().removeIf(e -> e.getValue() <= 0);
    }

    @Override
    public int getCachedDataVersion() {
        return cachedDataVersion;
    }

    @Override
    public void setCachedDataVersion(int version) {
        this.cachedDataVersion = version;
    }

    @Override
    public java.util.List<ResourceLocation> getCachedGroups() {
        return cachedGroups;
    }

    @Override
    public void setCachedGroups(java.util.List<ResourceLocation> groups) {
        this.cachedGroups = groups;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag stagesTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : stages.entrySet()) {
            stagesTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put(STAGES_KEY, stagesTag);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        stages.clear();
        if (tag.contains(STAGES_KEY)) {
            CompoundTag stagesTag = tag.getCompound(STAGES_KEY);
            for (String key : stagesTag.getAllKeys()) {
                int value = stagesTag.getInt(key);
                if (value > 0) {
                    stages.put(new ResourceLocation(key), value);
                }
            }
        }
    }
}
