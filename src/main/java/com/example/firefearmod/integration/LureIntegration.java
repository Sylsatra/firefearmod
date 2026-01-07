package com.example.firefearmod.integration;

import com.example.luremod.manager.LureGroup;
import com.example.luremod.manager.LureGroupManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

public class LureIntegration {
    private static final String LURE_MOD_ID = "luremod";
    private static final boolean LOADED = ModList.get().isLoaded(LURE_MOD_ID);

    public static boolean isLured(Mob mob, LivingEntity target) {
        if (!LOADED || mob == null || target == null) {
            return false;
        }
        try {
            return Bridges.checkEntity(mob, target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isLuredByItem(Mob mob, ItemStack stack) {
        if (!LOADED || mob == null || stack.isEmpty()) {
            return false;
        }
        try {
            return Bridges.checkItem(mob, stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isLuredByBlock(Mob mob, BlockState state, BlockEntity intent) {
        if (!LOADED || mob == null || state == null) {
            return false;
        }
        try {
            return Bridges.checkBlock(mob, state, intent);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static class Bridges {
        static boolean checkEntity(Mob mob, LivingEntity target) {
            Optional<LureGroup> groupOpt = LureGroupManager.getGroupForMob(mob);
            if (groupOpt.isEmpty()) {
                return false;
            }
            LureGroup group = groupOpt.get();
            if (group.isLuredItem(target.getMainHandItem())) return true;
            if (group.isLuredItem(target.getOffhandItem())) return true;
            return false;
        }

        static boolean checkItem(Mob mob, ItemStack stack) {
            Optional<LureGroup> groupOpt = LureGroupManager.getGroupForMob(mob);
            return groupOpt.map(lureGroup -> lureGroup.isLuredItem(stack)).orElse(false);
        }

        static boolean checkBlock(Mob mob, BlockState state, BlockEntity be) {
            Optional<LureGroup> groupOpt = LureGroupManager.getGroupForMob(mob);
            return groupOpt.map(lureGroup -> lureGroup.isLuredBlock(state, be)).orElse(false);
        }
    }
}
