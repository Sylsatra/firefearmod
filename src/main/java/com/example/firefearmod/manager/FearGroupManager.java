package com.example.firefearmod.manager;

import com.electronwill.nightconfig.core.Config;
import com.example.firefearmod.config.ConfigHolder;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "firefearmod")
public class FearGroupManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final List<FearGroup> FEAR_GROUPS = new ArrayList<>();

    public static void reload() {
        FEAR_GROUPS.clear();
        LOGGER.info("Loading Fear Groups from config...");
        List<? extends Config> groupConfigs = ConfigHolder.FEAR_GROUPS.get();
        for (Config groupConfig : groupConfigs) {
            try {
                FearGroup group = FearGroup.fromConfig(groupConfig);
                FEAR_GROUPS.add(group);
            } catch (Exception e) {
                String id = groupConfig.getOptional("group_id").map(String::valueOf).orElse("UNKNOWN");
                LOGGER.error("Failed to parse fear group '{}'. Reason: {}", id, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} Fear Groups.", FEAR_GROUPS.size());
    }

    public static Optional<FearGroup> getGroupForMob(Mob mob) {
        FearGroup bestMatch = null;
        int bestScore = -1;

        for (FearGroup group : FEAR_GROUPS) {
            int currentScore = group.getMatchScore(mob);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestMatch = group;
            }
        }
        return Optional.ofNullable(bestMatch);
    }
}