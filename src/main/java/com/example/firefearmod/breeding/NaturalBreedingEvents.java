package com.example.firefearmod.breeding;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.trauma.ITraumaData;
import com.example.firefearmod.trauma.TraumaCapability;
import com.example.firefearmod.trauma.TraumaGroup;
import com.example.firefearmod.trauma.TraumaGroupManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NaturalBreedingEvents {
    private static final String BREEDING_TAG = "firefearmod_breeding";
    private static final String NATURAL_PREFIX = "natural_cd_";

    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        List<NaturalBreedingRule> rules = NaturalBreedingManager.getRulesForMob(mob);
        if (rules.isEmpty()) {
            return;
        }
        Level level = mob.level();
        CompoundTag root = mob.getPersistentData();
        CompoundTag breeding = root.getCompound(BREEDING_TAG);
        boolean hadBreedingTag = root.contains(BREEDING_TAG, Tag.TAG_COMPOUND);

        for (NaturalBreedingRule rule : rules) {
            if (!rule.enabled()) {
                continue;
            }
            String key = NATURAL_PREFIX + rule.id();
            int cd = breeding.getInt(key);
            if (cd > 0) {
                breeding.putInt(key, cd - 1);
                continue;
            }
            if (mob.getRandom().nextDouble() >= rule.chancePerAttempt()) {
                continue;
            }

            if (mob instanceof AgeableMob ageable && ageable.isBaby()) {
                continue;
            }

            double radius = rule.partnerRadius();
            AABB box = mob.getBoundingBox().inflate(radius, radius * 0.5, radius);
            List<Mob> candidates = level.getEntitiesOfClass(Mob.class, box, m -> m != mob && m.getType() == mob.getType());
            if (candidates.isEmpty()) {
                continue;
            }
            Mob partner = candidates.get(0);
            if (partner instanceof AgeableMob ageablePartner && ageablePartner.isBaby()) {
                continue;
            }

            // Check cooldown on partner as well
            CompoundTag partnerRoot = partner.getPersistentData();
            CompoundTag partnerBreeding = partnerRoot.getCompound(BREEDING_TAG);
            String partnerKey = NATURAL_PREFIX + rule.id();
            if (partnerBreeding.getInt(partnerKey) > 0) {
                continue;
            }

            // Check nearby children count
            ResourceLocation childId = rule.child();
            EntityType<?> childType = ForgeRegistries.ENTITY_TYPES.getValue(childId);
            if (childType == null) {
                continue;
            }
            BlockPos centerPos = mob.blockPosition().offset(0, 0, 0);
            AABB childBox = new AABB(centerPos).inflate(radius, radius * 0.5, radius);
            int children = level.getEntitiesOfClass(Mob.class, childBox, m -> m.getType() == childType).size();
            if (children >= rule.maxNearbyChildren()) {
                continue;
            }

            // Spawn child as baby when possible
            EntityType<?> type = childType;
            if (!(type.create(level) instanceof Mob child)) {
                continue;
            }
            if (child instanceof AgeableMob ageableChild) {
                ageableChild.setBaby(true);
            } else if (child instanceof Zombie zombieChild) {
                zombieChild.setBaby(true);
            }
            double x = (mob.getX() + partner.getX()) * 0.5;
            double y = (mob.getY() + partner.getY()) * 0.5;
            double z = (mob.getZ() + partner.getZ()) * 0.5;
            child.moveTo(x, y, z, 0.0f, 0.0f);
            level.addFreshEntity(child);

            // Trauma inheritance
            ITraumaData childData = TraumaCapability.get(child).orElse(null);
            ITraumaData dataA = TraumaCapability.get(mob).orElse(null);
            ITraumaData dataB = TraumaCapability.get(partner).orElse(null);
            if (childData != null && (dataA != null || dataB != null)) {
                int maxStagesGlobal = ConfigHolder.MAX_TRAUMA_STAGES_PER_GROUP.get();
                List<TraumaGroup> childGroups = TraumaGroupManager.getGroupsForMob(child);
                for (TraumaGroup g : childGroups) {
                    int stageA = dataA != null ? dataA.getStage(g.id()) : 0;
                    int stageB = dataB != null ? dataB.getStage(g.id()) : 0;
                    int inherited = Math.max(stageA, stageB);
                    if (inherited > 0) {
                        childData.setStage(g.id(), inherited, maxStagesGlobal);
                    }
                }
                childData.clampStages(maxStagesGlobal);
            }

            // Apply cooldown to both parents
            breeding.putInt(key, rule.cooldownTicks());
            partnerBreeding.putInt(partnerKey, rule.cooldownTicks());
            if (!partnerRoot.contains(BREEDING_TAG, Tag.TAG_COMPOUND)) {
                partnerRoot.put(BREEDING_TAG, partnerBreeding);
            }
            break; // Only one breeding attempt per tick per mob
        }

        if (!hadBreedingTag || !root.contains(BREEDING_TAG, Tag.TAG_COMPOUND)) {
            root.put(BREEDING_TAG, breeding);
        }
    }
}
