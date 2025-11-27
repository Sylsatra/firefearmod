package com.example.firefearmod;

import com.example.firefearmod.ai.FireFearGoal;
import com.example.firefearmod.ai.LightFearGoal;
import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.manager.FearGroupManager;
import com.example.firefearmod.trauma.TraumaGroup;
import com.example.firefearmod.trauma.TraumaGroupManager;
import com.example.firefearmod.trauma.TraumaProfile;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "firefearmod")
public class FireFearEvents {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) {
            if (ConfigHolder.INTERGENERATIONAL_TRAUMA_ENABLED.get()) {
                List<TraumaGroup> traumaGroups = TraumaGroupManager.getGroupsForMob(mob);
                if (!traumaGroups.isEmpty()) {
                    TraumaProfile profile = new TraumaProfile(mob, traumaGroups);
                    mob.goalSelector.addGoal(0, new FireFearGoal(mob, profile));
                }
            } else {
                Optional<FearGroup> groupOpt = FearGroupManager.getGroupForMob(mob);
                groupOpt.ifPresent(group -> {
                    if (FearGroupManager.isLightFearEnabledForGroup(group)) {
                        mob.goalSelector.addGoal(4, new LightFearGoal(mob, group));
                    }
                    mob.goalSelector.addGoal(0, new FireFearGoal(mob, group));
                });
            }
        }
    }
}