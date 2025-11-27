package com.example.firefearmod.breeding;

import net.minecraft.resources.ResourceLocation;

public record NaturalBreedingRule(
        ResourceLocation id,
        boolean enabled,
        ResourceLocation mob,
        ResourceLocation child,
        double partnerRadius,
        int cooldownTicks,
        double chancePerAttempt,
        int maxNearbyChildren
) {
}
