package com.example.firefearmod;

import com.example.firefearmod.config.FireFearConfig;
import net.minecraftforge.fml.common.Mod;

@Mod("firefearmod")
public class FireFearMod {
    public FireFearMod() {
        FireFearConfig.loadConfig();

    }
}
