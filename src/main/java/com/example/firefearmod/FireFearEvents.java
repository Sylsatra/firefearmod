package com.example.firefearmod;

import com.example.firefearmod.ai.FireFearGoal;
import com.example.firefearmod.config.FireFearConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "firefearmod")
public class FireFearEvents {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) { 
        if (event.getEntity() instanceof Mob mob) {

            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            
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