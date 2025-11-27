package com.example.firefearmod.trauma;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TraumaReloadListener {
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TraumaGroupManager());
    }
}
