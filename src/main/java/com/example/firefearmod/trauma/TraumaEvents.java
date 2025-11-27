package com.example.firefearmod.trauma;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.util.VisionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TraumaEvents {

    @SubscribeEvent
    public static void onMobHurt(LivingHurtEvent event) {
        if (!ConfigHolder.INTERGENERATIONAL_TRAUMA_ENABLED.get()) {
            return;
        }
        LivingEntity living = event.getEntity();
        if (!(living instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        ITraumaData data = TraumaCapability.get(mob).orElse(null);
        if (data == null) {
            return;
        }
        List<TraumaGroup> groups = TraumaGroupManager.getGroupsForMob(mob);
        if (groups.isEmpty()) {
            return;
        }

        DamageSource source = event.getSource();
        int maxStagesGlobal = ConfigHolder.MAX_TRAUMA_STAGES_PER_GROUP.get();
        for (TraumaGroup group : groups) {
            int currentStage = getActiveStageIndex(group, data);
            int nextStage = currentStage + 1;
            if (nextStage < 0 || nextStage >= group.stages().size()) {
                continue;
            }
            TraumaGroup.TraumaStage nextStageDef = group.stages().get(nextStage);
            if (shouldAdvanceFromDirectRequirements(group, nextStageDef, mob, source)) {
                data.setStage(group.id(), nextStage, maxStagesGlobal);
                applyWitnessLearning(mob, group, nextStage);
            }
        }
    }

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!ConfigHolder.INTERGENERATIONAL_TRAUMA_ENABLED.get()) {
            return;
        }
        Mob child = event.getChild();
        if (child.level().isClientSide) {
            return;
        }
        Entity parentAEntity = event.getParentA();
        Entity parentBEntity = event.getParentB();
        Mob parentA = parentAEntity instanceof Mob mA ? mA : null;
        Mob parentB = parentBEntity instanceof Mob mB ? mB : null;
        if (parentA == null && parentB == null) {
            return;
        }

        ITraumaData childData = TraumaCapability.get(child).orElse(null);
        ITraumaData dataA = parentA != null ? TraumaCapability.get(parentA).orElse(null) : null;
        ITraumaData dataB = parentB != null ? TraumaCapability.get(parentB).orElse(null) : null;
        if (childData == null || (dataA == null && dataB == null)) {
            return;
        }

        List<TraumaGroup> childGroups = TraumaGroupManager.getGroupsForMob(child);
        if (childGroups.isEmpty()) {
            return;
        }
        int maxStagesGlobal = ConfigHolder.MAX_TRAUMA_STAGES_PER_GROUP.get();
        for (TraumaGroup group : childGroups) {
            int stageA = dataA != null ? dataA.getStage(group.id()) : 0;
            int stageB = dataB != null ? dataB.getStage(group.id()) : 0;
            int inherited = Math.max(stageA, stageB);
            if (inherited > 0) {
                childData.setStage(group.id(), inherited, maxStagesGlobal);
            }
        }
        childData.clampStages(maxStagesGlobal);
    }

    private static int getActiveStageIndex(TraumaGroup group, ITraumaData data) {
        int stage = data.getStage(group.id());
        if (stage < 0) {
            stage = 0;
        }
        int maxIndex = group.stages().size() - 1;
        if (stage > maxIndex) {
            stage = maxIndex;
        }
        return stage;
    }

    private static boolean shouldAdvanceFromDirectRequirements(TraumaGroup group,
                                                                TraumaGroup.TraumaStage targetStage,
                                                                Mob victim,
                                                                DamageSource source) {
        for (TraumaGroup.TraumaRequirement req : targetStage.requirements()) {
            switch (req.type()) {
                case HURT_BY_ENTITY -> {
                    if (isHurtByEntityRequirementMet(req, source)) {
                        return true;
                    }
                }
                case HURT_BY_SOURCE -> {
                    if (isHurtBySourceRequirementMet(req, victim, source)) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static boolean isHurtByEntityRequirementMet(TraumaGroup.TraumaRequirement req, DamageSource source) {
        FearGroup.FearedEntityDefinition def = req.entityDefinition();
        if (def == null) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker == null) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType());
        if (id == null) {
            return false;
        }
        return def.matches(id, attacker);
    }

    private static boolean isHurtBySourceRequirementMet(TraumaGroup.TraumaRequirement req, Mob victim, DamageSource source) {
        FearGroup.FearSourceDefinition def = req.sourceDefinition();
        if (def == null) {
            return false;
        }
        // Try matching attacker-held item first
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) {
            if (matchesHeldItem(def, living.getMainHandItem()) || matchesHeldItem(def, living.getOffhandItem())) {
                return true;
            }
        }
        // Fallback: try matching block at and below victim position
        Level level = victim.level();
        BlockPos basePos = victim.blockPosition();
        return matchesBlockAt(def, level, basePos) || matchesBlockAt(def, level, basePos.below());
    }

    private static boolean matchesHeldItem(FearGroup.FearSourceDefinition def, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }
        return def.matches(itemId, null, null, stack);
    }

    private static boolean matchesBlockAt(FearGroup.FearSourceDefinition def, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) {
            return false;
        }
        return def.matches(blockId, state, be, null);
    }

    private static void applyWitnessLearning(Mob victim, TraumaGroup group, int targetStageIndex) {
        List<TraumaGroup.TraumaStage> stages = group.stages();
        if (targetStageIndex < 0 || targetStageIndex >= stages.size()) {
            return;
        }
        TraumaGroup.TraumaStage stage = stages.get(targetStageIndex);
        double radius = stage.witnessRadius() != null ? stage.witnessRadius() : group.defaultWitnessRadius();
        if (radius <= 0.0) {
            return;
        }
        Level level = victim.level();
        double vertical = radius;
        AABB box = victim.getBoundingBox().inflate(radius, vertical, radius);
        int maxStagesGlobal = ConfigHolder.MAX_TRAUMA_STAGES_PER_GROUP.get();
        List<Mob> watchers = level.getEntitiesOfClass(Mob.class, box, m -> m != victim);
        for (Mob watcher : watchers) {
            if (watcher.level().isClientSide) {
                continue;
            }
            if (!VisionHelper.canSeeEntity(watcher, victim, true)) {
                continue;
            }
            ITraumaData data = TraumaCapability.get(watcher).orElse(null);
            if (data == null) {
                continue;
            }
            // Only consider watchers that belong to the same trauma group
            boolean inGroup = TraumaGroupManager.getGroupsForMob(watcher).stream().anyMatch(g -> g.id().equals(group.id()));
            if (!inGroup) {
                continue;
            }
            int currentStage = data.getStage(group.id());
            if (currentStage == targetStageIndex - 1) {
                data.setStage(group.id(), targetStageIndex, maxStagesGlobal);
            }
        }
    }
}
