package com.example.firefearmod.manager;

import com.electronwill.nightconfig.core.Config;
import com.example.firefearmod.config.ConfigHolder;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "firefearmod")
public class FearGroupManager {

    private static final Logger LOGGER = LogManager.getLogger();
    
    private static volatile List<FearGroup> fearGroups = Collections.emptyList();
    private static volatile Map<String, LightFear> lightFears = Collections.emptyMap();

    public static void reload() {
        LOGGER.info("Loading Fear Groups from config...");
        List<FearGroup> newGroups = new ArrayList<>();
        Map<String, LightFear> newLightFears = new HashMap<>();
        
        List<? extends Config> groupConfigs = ConfigHolder.FEAR_GROUPS.get();
        for (Config groupConfig : groupConfigs) {
            try {
                FearGroup group = FearGroup.fromConfig(groupConfig);
                newGroups.add(group);
                Config lfCfg = (Config) groupConfig.getOptional("light_fear").orElse(null);
                LightFear lf = LightFear.fromConfig(lfCfg);
                newLightFears.put(group.groupId(), lf);
            } catch (Exception e) {
                String id = groupConfig.getOptional("group_id").map(String::valueOf).orElse("UNKNOWN");
                LOGGER.error("Failed to parse fear group '{}'. Reason: {}", id, e.getMessage());
            }
        }
        
        fearGroups = Collections.unmodifiableList(newGroups);
        lightFears = Collections.unmodifiableMap(newLightFears);
        
        LOGGER.info("Loaded {} Fear Groups.", fearGroups.size());
    }

    public static Optional<FearGroup> getGroupForMob(Mob mob) {
        FearGroup bestMatch = null;
        int bestScore = -1;
        
        List<FearGroup> currentGroups = fearGroups;

        for (FearGroup group : currentGroups) {
            int currentScore = group.getMatchScore(mob);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestMatch = group;
            }
        }
        return Optional.ofNullable(bestMatch);
    }

    public static LightFear getLightFearForGroup(FearGroup group) {
        return lightFears.getOrDefault(group.groupId(), LightFear.disabled());
    }

    public static boolean isLightFearEnabledForGroup(FearGroup group) {
        return getLightFearForGroup(group).enabled();
    }

    public static Optional<LightFear> getEnabledLightFear(FearGroup group) {
        LightFear lf = getLightFearForGroup(group);
        return lf.enabled() ? Optional.of(lf) : Optional.empty();
    }
}