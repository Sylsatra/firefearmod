package com.example.firefearmod;

import com.example.firefearmod.ai.FireFearGoal;
import com.example.firefearmod.ai.LightFearGoal;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.manager.FearGroupManager;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = "firefearmod")
public class FireFearEvents {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) {
            
            Optional<FearGroup> groupOpt = FearGroupManager.getGroupForMob(mob);

            groupOpt.ifPresent(group -> {
                if (FearGroupManager.isLightFearEnabledForGroup(group)) {
                    mob.goalSelector.addGoal(5, new LightFearGoal(mob, group));
                }
                mob.goalSelector.addGoal(4, new FireFearGoal(mob, group));
            });
        }
    }
}