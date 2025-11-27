package com.example.firefearmod.trauma;

import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.manager.IFearProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class TraumaProfile implements IFearProfile {
    private static final double DEFAULT_FLEE_SPEED = 1.2;
    private static final int DEFAULT_SEARCH_RADIUS = 8;

    private final Mob mob;
    private final List<TraumaGroup> groups;

    public TraumaProfile(Mob mob, List<TraumaGroup> groups) {
        this.mob = mob;
        this.groups = List.copyOf(groups);
    }

    @Override
    public double fleeSpeed() {
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        double result = DEFAULT_FLEE_SPEED;
        boolean found = false;
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            Double v = resolveFleeSpeed(group, stageIndex);
            if (v != null) {
                if (!found || v > result) {
                    result = v;
                    found = true;
                }
            }
        }
        return result;
    }

    @Override
    public int searchRadius() {
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        int result = DEFAULT_SEARCH_RADIUS;
        boolean found = false;
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            Integer v = resolveSearchRadius(group, stageIndex);
            if (v != null) {
                if (!found || v > result) {
                    result = v;
                    found = true;
                }
            }
        }
        return result;
    }

    @Nullable
    @Override
    public FearGroup.FearSourceDefinition findFearedBlock(BlockState blockState, @Nullable BlockEntity blockEntity) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        if (blockId == null) {
            return null;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearSourceDefinition def : stage.fearedBlocks()) {
                    if (def.matches(blockId, blockState, blockEntity, null)) {
                        return def;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    public FearGroup.FearSourceDefinition findFearedItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return null;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearSourceDefinition def : stage.fearedItems()) {
                    if (def.matches(itemId, null, null, stack)) {
                        return def;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasFearedEntities() {
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                if (!stage.fearedEntities().isEmpty()) {
                    return true;
                }
                for (FearGroup.FearSourceDefinition def : stage.fearedBlocks()) {
                    if (def.canMatchEntity()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public com.example.firefearmod.manager.IFearProfile.VisibilityMode getEntityVisibilityMode(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return com.example.firefearmod.manager.IFearProfile.VisibilityMode.LOOK_BASED;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearedEntityDefinition def : stage.fearedEntities()) {
                    if (def.matches(entityId, entity)) {
                        return def.visibilityMode();
                    }
                }
            }
        }
        return com.example.firefearmod.manager.IFearProfile.VisibilityMode.LOOK_BASED;
    }

    @Override
    public boolean isFearedEntity(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return false;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearedEntityDefinition def : stage.fearedEntities()) {
                    if (def.matches(entityId, entity)) {
                        return true;
                    }
                }
                for (FearGroup.FearSourceDefinition def : stage.fearedBlocks()) {
                    if (def.matchesEntity(entityId, entity)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean shouldOverrideHostility(BlockState blockState, @Nullable BlockEntity blockEntity) {
        FearGroup.FearSourceDefinition def = findFearedBlock(blockState, blockEntity);
        return def != null && def.fearOverride();
    }

    @Override
    public boolean shouldOverrideHostility(ItemStack stack) {
        FearGroup.FearSourceDefinition def = findFearedItem(stack);
        return def != null && def.fearOverride();
    }

    @Override
    public boolean shouldOverrideHostility(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return false;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : groups) {
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearedEntityDefinition def : stage.fearedEntities()) {
                    if (def.matches(entityId, entity)) {
                        return def.overrideHostility();
                    }
                }
            }
        }
        return false;
    }

    private static int getActiveStageIndex(TraumaGroup group, @Nullable ITraumaData data) {
        List<TraumaGroup.TraumaStage> stages = group.stages();
        if (stages.isEmpty()) {
            return -1;
        }
        int stage = 0;
        if (data != null) {
            stage = data.getStage(group.id());
        }
        if (stage < 0) {
            stage = 0;
        }
        int maxIndex = stages.size() - 1;
        if (stage > maxIndex) {
            stage = maxIndex;
        }
        return stage;
    }

    @Nullable
    private static Double resolveFleeSpeed(TraumaGroup group, int stageIndex) {
        List<TraumaGroup.TraumaStage> stages = group.stages();
        for (int i = stageIndex; i >= 0; i--) {
            Double v = stages.get(i).fleeSpeed();
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    @Nullable
    private static Integer resolveSearchRadius(TraumaGroup group, int stageIndex) {
        List<TraumaGroup.TraumaStage> stages = group.stages();
        for (int i = stageIndex; i >= 0; i--) {
            Integer v = stages.get(i).searchRadius();
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
