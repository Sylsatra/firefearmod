package com.example.firefearmod.trauma;

import com.example.firefearmod.manager.FearGroup;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public record TraumaGroup(ResourceLocation id,
                          List<FearGroup.MobDefinition> mobs,
                          List<FearGroup.MobDefinition> blacklist,
                          double defaultWitnessRadius,
                          List<TraumaGroup.TraumaStage> stages,
                          List<TraumaCondition> conditions) {

    public enum RequirementType {
        HURT_BY_ENTITY,
        HURT_BY_SOURCE,
        WITNESS_HURT_BY_ENTITY,
        WITNESS_HURT_BY_SOURCE
    }

    public enum ConditionType {
        HEALTH_PERCENT,
        IS_DAY,
        IS_RAINING,
        Y_LEVEL
    }

    public record TraumaCondition(ConditionType type, @Nullable Double min, @Nullable Double max, @Nullable Boolean boolValue) {
    }

    public record TraumaRequirement(RequirementType type,
                                    @Nullable FearGroup.FearedEntityDefinition entityDefinition,
                                    @Nullable FearGroup.FearSourceDefinition sourceDefinition) {
    }

    public record TraumaStage(int index,
                              @Nullable Double fleeSpeed,
                              @Nullable Integer searchRadius,
                              @Nullable Double witnessRadius,
                              List<FearGroup.FearSourceDefinition> fearedBlocks,
                              List<FearGroup.FearSourceDefinition> fearedItems,
                              List<FearGroup.FearedEntityDefinition> fearedEntities,
                              List<TraumaRequirement> requirements) {
    }
}
