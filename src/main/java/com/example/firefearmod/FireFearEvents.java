package com.example.firefearmod;

import com.example.firefearmod.ai.FireFearGoal;
import com.example.firefearmod.config.FireFearConfig; 
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = "firefearmod")
public class FireFearEvents {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof Mob mob) {

            ResourceLocation entityId = mob.getType().getRegistryName();
            if (entityId == null) {
                return;
            }


            String entityIdStr = entityId.toString();

            if (!FireFearConfig.FLEEING_ENTITIES.contains(entityIdStr)) {
                return; 
            }

            mob.goalSelector.addGoal(2, new FireFearGoal(mob, 1.2, 8));
        }
    }
}
