package com.example.firefearmod.manager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public interface IFearProfile {
    enum VisibilityMode {
        LOOK_BASED,
        ALWAYS
    }

    double fleeSpeed();

    int searchRadius();

    @Nullable
    FearGroup.FearSourceDefinition findFearedBlock(BlockState blockState, @Nullable BlockEntity blockEntity);

    @Nullable
    FearGroup.FearSourceDefinition findFearedItem(ItemStack stack);

    boolean hasFearedEntities();

    boolean isFearedEntity(Entity entity);

    boolean shouldOverrideHostility(BlockState blockState, @Nullable BlockEntity blockEntity);

    boolean shouldOverrideHostility(ItemStack stack);

    boolean shouldOverrideHostility(Entity entity);

    default boolean isTemptedBy(Entity entity) { return false; }
    default boolean isTemptedBy(ItemStack stack) { return false; }
    default boolean isTemptedBy(BlockState state, BlockEntity be) { return false; }

    default double getFleeSpeedFor(Entity entity) { return fleeSpeed(); }
    default double getFleeSpeedFor(BlockState state) { return fleeSpeed(); }
    default double getFleeSpeedFor(ItemStack stack) { return fleeSpeed(); }

    VisibilityMode getEntityVisibilityMode(Entity entity);
    boolean isMutualVision(Entity entity);
    boolean isMutualVision(ItemStack stack);
    boolean isMutualVision(BlockState state, @Nullable BlockEntity be);
}
