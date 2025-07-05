package com.example.firefearmod;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroupManager;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("firefearmod")
public class FireFearMod {
    public FireFearMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHolder.SPEC, "firefearmod-common.toml");
    }

    public void onConfigLoad(final ModConfigEvent event) {
        ConfigHolder.setDefaults();
        FearGroupManager.reload();
    }
}