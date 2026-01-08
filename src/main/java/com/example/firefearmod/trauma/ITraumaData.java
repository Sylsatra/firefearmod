package com.example.firefearmod.trauma;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public interface ITraumaData {
    int getStage(ResourceLocation groupId);

    void setStage(ResourceLocation groupId, int stage, int maxStages);

    Map<ResourceLocation, Integer> getAllStages();

    void clampStages(int maxStages);

    void tick();

    com.example.firefearmod.util.PerformanceCache getCache();
}
