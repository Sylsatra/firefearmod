package com.example.firefearmod;

import com.example.firefearmod.config.FireFearConfig;
import net.minecraftforge.fml.common.Mod;

@Mod("firefearmod")
public class FireFearMod {
    public FireFearMod() {
        // Load config once at startup
        FireFearConfig.loadConfig();

        // Other initialization
    }
}
