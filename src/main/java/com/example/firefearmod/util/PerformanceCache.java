package com.example.firefearmod.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public class PerformanceCache {
    
    private final Map<Object, Boolean> fearCache = new HashMap<>();
    private final Map<Object, Boolean> temptationCache = new HashMap<>();

    public void clear() {
        fearCache.clear();
        temptationCache.clear();
    }

    public Boolean getFeared(Object key) {
        return fearCache.get(key);
    }

    public void putFeared(Object key, boolean result) {
        fearCache.put(key, result);
    }
    
    public Boolean getTempted(Object key) {
        return temptationCache.get(key);
    }

    public void putTempted(Object key, boolean result) {
        temptationCache.put(key, result);
    }
}
