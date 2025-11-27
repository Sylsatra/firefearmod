package com.example.firefearmod.breeding;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.trauma.ITraumaData;
import com.example.firefearmod.trauma.TraumaCapability;
import com.example.firefearmod.trauma.TraumaGroup;
import com.example.firefearmod.trauma.TraumaGroupManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForcedBreedingEvents {
    private static final String BREEDING_TAG = "firefearmod_breeding";
    private static final String FORCED_PREFIX = "forced_cd_";

    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (!ConfigHolder.INTERGENERATIONAL_TRAUMA_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }

        // Rules are selected based only on mob type; player's held item is ignored
        List<ForcedBreedingRule> rules = ForcedBreedingManager.getRulesFor(mob, ItemStack.EMPTY);
        if (rules.isEmpty()) {
            return;
        }
        Level level = mob.level();
        CompoundTag root = mob.getPersistentData();
        CompoundTag breeding = root.getCompound(BREEDING_TAG);
        boolean hadBreedingTag = root.contains(BREEDING_TAG, Tag.TAG_COMPOUND);

        for (ForcedBreedingRule rule : rules) {
            if (!rule.enabled()) {
                continue;
            }
            String key = FORCED_PREFIX + rule.id();
            int cd = breeding.getInt(key);
            if (cd > 0) {
                continue;
            }

            double radius = rule.partnerRadius();
            AABB box = mob.getBoundingBox().inflate(radius, radius * 0.5, radius);
            List<Mob> candidates = level.getEntitiesOfClass(Mob.class, box, m -> m != mob && m.getType() == mob.getType());
            if (candidates.isEmpty()) {
                continue;
            }
            Mob partner = candidates.get(0);

            // Enforce requiredHeldItem condition if present: at least one parent must be holding it
            ResourceLocation requiredHeld = rule.requiredHeldItem();
            if (requiredHeld != null) {
                boolean ok = false;
                ResourceLocation aMainId = ForgeRegistries.ITEMS.getKey(mob.getMainHandItem().getItem());
                ResourceLocation bMainId = ForgeRegistries.ITEMS.getKey(partner.getMainHandItem().getItem());
                if (aMainId != null && aMainId.equals(requiredHeld)) {
                    ok = true;
                }
                if (!ok && bMainId != null && bMainId.equals(requiredHeld)) {
                    ok = true;
                }
                if (!ok) {
                    continue;
                }
            }

            // Check partner cooldown
            CompoundTag partnerRoot = partner.getPersistentData();
            CompoundTag partnerBreeding = partnerRoot.getCompound(BREEDING_TAG);
            String partnerKey = FORCED_PREFIX + rule.id();
            if (partnerBreeding.getInt(partnerKey) > 0) {
                continue;
            }

            // Check nearby children
            ResourceLocation childId = rule.child();
            EntityType<?> childType = ForgeRegistries.ENTITY_TYPES.getValue(childId);
            if (childType == null) {
                continue;
            }
            AABB childBox = mob.getBoundingBox().inflate(radius, radius * 0.5, radius);
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

            // Apply cooldowns
            breeding.putInt(key, rule.cooldownTicks());
            partnerBreeding.putInt(partnerKey, rule.cooldownTicks());
            if (!partnerRoot.contains(BREEDING_TAG, Tag.TAG_COMPOUND)) {
                partnerRoot.put(BREEDING_TAG, partnerBreeding);
            }
            break;
        }

        if (!hadBreedingTag || !root.contains(BREEDING_TAG, Tag.TAG_COMPOUND)) {
            root.put(BREEDING_TAG, breeding);
        }
    }
}
