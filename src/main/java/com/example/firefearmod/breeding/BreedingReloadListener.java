package com.example.firefearmod.breeding;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BreedingReloadListener {
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new NaturalBreedingManager());
        event.addListener(new ForcedBreedingManager());
    }
}
