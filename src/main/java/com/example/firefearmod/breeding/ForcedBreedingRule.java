package com.example.firefearmod.breeding;

import net.minecraft.resources.ResourceLocation;

public record ForcedBreedingRule(
        ResourceLocation id,
        boolean enabled,
        ResourceLocation item,
        ResourceLocation mob,
        ResourceLocation child,
        double partnerRadius,
        int cooldownTicks,
        int maxNearbyChildren,
        boolean consumeItem,
        ResourceLocation requiredHeldItem
) {
}
