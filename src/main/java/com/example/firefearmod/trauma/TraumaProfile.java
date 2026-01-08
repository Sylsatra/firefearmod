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
    private final List<ResourceLocation> groupIds;

    public TraumaProfile(Mob mob, List<TraumaGroup> groups) {
        this.mob = mob;
        this.groupIds = groups.stream().map(TraumaGroup::id).toList();
    }
    
    private List<TraumaGroup> getGroups() {
        List<TraumaGroup> list = new java.util.ArrayList<>();
        for (ResourceLocation id : groupIds) {
            TraumaGroup g = TraumaGroupManager.getGroup(id);
            if (g != null) list.add(g);
        }
        return list;
    }

    @Override
    public double fleeSpeed() {
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        double result = DEFAULT_FLEE_SPEED;
        boolean found = false;
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
    public boolean isMutualVision(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) return false;
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) continue;
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) continue;
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearedEntityDefinition def : stage.fearedEntities()) {
                    if (def.matches(entityId, entity)) return def.source().mutualVision();
                }
                for (FearGroup.FearSourceDefinition def : stage.fearedBlocks()) {
                    if (def.matchesEntity(entityId, entity)) return def.mutualVision();
                }
            }
        }
        return false;
    }

    @Override
    public boolean isMutualVision(ItemStack stack) {
        FearGroup.FearSourceDefinition def = findFearedItem(stack);
        return def != null && def.mutualVision();
    }

    @Override
    public boolean isMutualVision(BlockState state, @Nullable BlockEntity be) {
        FearGroup.FearSourceDefinition def = findFearedBlock(state, be);
        return def != null && def.mutualVision();
    }

    @Override
    public boolean isFearedEntity(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return false;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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
    public boolean isTemptedBy(BlockState blockState, BlockEntity blockEntity) {
        if (com.example.firefearmod.integration.LureIntegration.isLuredByBlock(mob, blockState, blockEntity)) {
           return true;
        }
        FearGroup.FearSourceDefinition def = findFearedBlock(blockState, blockEntity);
        return def != null && def.temptation();
    }

    @Override
    public boolean shouldOverrideHostility(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        com.example.firefearmod.util.PerformanceCache cache = (data != null) ? data.getCache() : null;
        String key = null;
        
        if (cache != null) {
            key = stack.getDescriptionId() + (stack.hasTag() ? stack.getTag().toString() : "") + "_override";
            Boolean cached = cache.getFeared(key);
            if (cached != null) return cached;
        }

        FearGroup.FearSourceDefinition def = findFearedItem(stack);
        boolean result = def != null && def.fearOverride();
        
        if (cache != null && key != null) {
            cache.putFeared(key, result);
        }
        return result;
    }

    @Override
    public boolean isTemptedBy(ItemStack stack) {
        if (stack.isEmpty()) return false;

        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        com.example.firefearmod.util.PerformanceCache cache = (data != null) ? data.getCache() : null;
        String key = null;

        if (cache != null) {
            key = stack.getDescriptionId() + (stack.hasTag() ? stack.getTag().toString() : "") + "_tempt";
            Boolean cached = cache.getTempted(key);
            if (cached != null) return cached;
        }

        if (com.example.firefearmod.integration.LureIntegration.isLuredByItem(mob, stack)) {
            if (cache != null && key != null) cache.putTempted(key, true);
            return true;
        }
        if (mob instanceof net.minecraft.world.entity.animal.Animal animal && animal.isFood(stack)) {
            if (cache != null && key != null) cache.putTempted(key, true);
            return true;
        }
        FearGroup.FearSourceDefinition def = findFearedItem(stack);
        boolean result = def != null && def.temptation();
        
        if (cache != null && key != null) {
            cache.putTempted(key, result);
        }
        return result;
    }

    @Override
    public boolean shouldOverrideHostility(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return false;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
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

    @Override
    public boolean isTemptedBy(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living && 
            com.example.firefearmod.integration.LureIntegration.isLured(mob, living)) {
            return true;
        }
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return false;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        for (TraumaGroup group : getGroups()) {
            if (!areConditionsMet(group)) {
                continue;
            }
            int stageIndex = getActiveStageIndex(group, data);
            if (stageIndex < 0) {
                continue;
            }
            for (int i = 0; i <= stageIndex; i++) {
                TraumaGroup.TraumaStage stage = group.stages().get(i);
                for (FearGroup.FearedEntityDefinition def : stage.fearedEntities()) {
                    if (def.matches(entityId, entity)) {
                        if (def.source().temptation()) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean areConditionsMet(TraumaGroup group) {
        if (group.conditions() == null || group.conditions().isEmpty()) {
            return true;
        }
        for (TraumaGroup.TraumaCondition condition : group.conditions()) {
            switch (condition.type()) {
                case HEALTH_PERCENT -> {
                    float healthPct = mob.getHealth() / mob.getMaxHealth();
                    if (condition.min() != null && healthPct < condition.min()) return false;
                    if (condition.max() != null && healthPct > condition.max()) return false;
                }
                case IS_DAY -> {
                    boolean isDay = mob.level().isDay();
                    if (condition.boolValue() != null && isDay != condition.boolValue()) return false;
                }
                case IS_RAINING -> {
                    boolean isRaining = mob.level().isRaining();
                    if (condition.boolValue() != null && isRaining != condition.boolValue()) return false;
                }
                case Y_LEVEL -> {
                    double y = mob.getY();
                    if (condition.min() != null && y < condition.min()) return false;
                    if (condition.max() != null && y > condition.max()) return false;
                }
            }
        }
        return true;
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

    @Override
    public double getFleeSpeedFor(Entity entity) {
         ITraumaData data = TraumaCapability.get(mob).orElse(null);
         ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
         double result = -1.0;
         
         for (TraumaGroup group : getGroups()) {
             if (!areConditionsMet(group)) continue;
             int stageIndex = getActiveStageIndex(group, data);
             if (stageIndex < 0) continue;
             
             Double speed = null;
             if (entityId != null) {
                 for (int i = 0; i <= stageIndex; i++) {
                     TraumaGroup.TraumaStage stage = group.stages().get(i);
                     for (FearGroup.FearedEntityDefinition def : stage.fearedEntities()) {
                         if (def.matches(entityId, entity)) {
                             speed = resolveFleeSpeed(group, i);
                             break;
                         }
                     }
                     if (speed == null) {
                         for (FearGroup.FearSourceDefinition def : stage.fearedBlocks()) {
                             if (def.matchesEntity(entityId, entity)) {
                                 speed = resolveFleeSpeed(group, i);
                                 break;
                             }
                         }
                     }
                     if (speed != null) break;
                 }
             }
             
             if (speed != null && (result < 0 || speed > result)) {
                 result = speed;
             }
         }
         return result < 0 ? fleeSpeed() : result;
    }

    @Override
    public double getFleeSpeedFor(BlockState state) {
         ITraumaData data = TraumaCapability.get(mob).orElse(null);
         ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
         if (blockId == null) return fleeSpeed();
         double result = -1.0;
 
         for (TraumaGroup group : getGroups()) {
             if (!areConditionsMet(group)) continue;
             int stageIndex = getActiveStageIndex(group, data);
             if (stageIndex < 0) continue;
 
             Double speed = null;
             for (int i = 0; i <= stageIndex; i++) {
                 TraumaGroup.TraumaStage stage = group.stages().get(i);
                 for (FearGroup.FearSourceDefinition def : stage.fearedBlocks()) {
                      if (def.matches(blockId, state, null, null)) {
                          speed = resolveFleeSpeed(group, i);
                          break;
                      }
                 }
                 if (speed != null) break;
             }
              if (speed != null && (result < 0 || speed > result)) {
                 result = speed;
             }
         }
         return result < 0 ? fleeSpeed() : result;
    }

    @Override
    public double getFleeSpeedFor(ItemStack stack) {
          ITraumaData data = TraumaCapability.get(mob).orElse(null);
          if (stack.isEmpty()) return fleeSpeed();
          ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
          if (itemId == null) return fleeSpeed();
          double result = -1.0;
 
         for (TraumaGroup group : getGroups()) {
             if (!areConditionsMet(group)) continue;
             int stageIndex = getActiveStageIndex(group, data);
             if (stageIndex < 0) continue;
 
             Double speed = null;
             for (int i = 0; i <= stageIndex; i++) {
                 TraumaGroup.TraumaStage stage = group.stages().get(i);
                 for (FearGroup.FearSourceDefinition def : stage.fearedItems()) {
                      if (def.matches(itemId, null, null, stack)) {
                          speed = resolveFleeSpeed(group, i);
                          break;
                      }
                 }
                 if (speed != null) break;
             }
              if (speed != null && (result < 0 || speed > result)) {
                 result = speed;
             }
         }
         return result < 0 ? fleeSpeed() : result;
    }
}
