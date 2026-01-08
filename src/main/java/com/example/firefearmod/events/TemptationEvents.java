package com.example.firefearmod.events;

import com.example.firefearmod.manager.IFearProfile;
import com.example.firefearmod.trauma.TraumaCapability;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TemptationEvents {

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null) {
            return;
        }

        List<com.example.firefearmod.trauma.TraumaGroup> groups = com.example.firefearmod.trauma.TraumaGroupManager.getGroupsForMob(mob);
        if (groups.isEmpty()) {
            return;
        }
        IFearProfile profile = new com.example.firefearmod.trauma.TraumaProfile(mob, groups);

        boolean shouldSuppress = profile.isTemptedBy(newTarget) || profile.shouldOverrideHostility(newTarget);
        
        if (!shouldSuppress) {
             shouldSuppress = profile.isTemptedBy(newTarget.getMainHandItem()) || profile.isTemptedBy(newTarget.getOffhandItem()) ||
                              profile.shouldOverrideHostility(newTarget.getMainHandItem()) || profile.shouldOverrideHostility(newTarget.getOffhandItem());
        }

        if (shouldSuppress) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        
        if (mob.tickCount % 10 != 0) {
            return;
        }

        List<com.example.firefearmod.trauma.TraumaGroup> groups = com.example.firefearmod.trauma.TraumaGroupManager.getGroupsForMob(mob);
        if (groups.isEmpty()) {
            return;
        }
        IFearProfile profile = new com.example.firefearmod.trauma.TraumaProfile(mob, groups);

        boolean shouldSuppress = profile.isTemptedBy(target) || profile.shouldOverrideHostility(target);
        if (!shouldSuppress) {
            shouldSuppress = profile.isTemptedBy(target.getMainHandItem()) || profile.isTemptedBy(target.getOffhandItem()) ||
                             profile.shouldOverrideHostility(target.getMainHandItem()) || profile.shouldOverrideHostility(target.getOffhandItem());
        }

        if (shouldSuppress) {
             mob.setTarget(null);
             mob.setAggressive(false);
        }
    }
}
