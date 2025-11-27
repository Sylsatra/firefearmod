package com.example.firefearmod.trauma;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TraumaData implements ITraumaData {
    private static final String STAGES_KEY = "stages";

    private final Map<ResourceLocation, Integer> stages = new HashMap<>();

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
