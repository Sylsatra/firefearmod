package com.example.firefearmod.ai;

import com.example.firefearmod.config.FireFearConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class FireFearGoal extends Goal {
    private final Mob mob;
    private final double speedModifier;
    private final int searchRadius;

    private Vec3 dangerPos;

    private static final double STOP_FLEE_DIST_SQ = 10 * 10;

    private int scanCooldown = 0;

    public FireFearGoal(Mob mob, double speedModifier, int searchRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        dangerPos = findNearestThreat();
        return dangerPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (dangerPos == null) return false;
        double distSqr = mob.blockPosition().distToCenterSqr(dangerPos);
        return distSqr < STOP_FLEE_DIST_SQ;
    }

    @Override
    public void stop() {
        dangerPos = null;
        scanCooldown = 0; 
    }

    @Override
    public void tick() {
        if (scanCooldown > 0) {
            scanCooldown--;
        } else {
            scanCooldown = FireFearConfig.SCAN_COOLDOWN_TICKS;
            dangerPos = findNearestThreat();
        }

        if (dangerPos == null) return;

        Vec3 mobPos = mob.position();
        Vec3 fleeDir = mobPos.subtract(dangerPos).normalize();
        Vec3 fleeTarget = mobPos.add(fleeDir.scale(5.0));
        mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, speedModifier);
    }


    private Vec3 findNearestThreat() {
        Level level = mob.level;
        double closestDistSqr = Double.MAX_VALUE;
        Vec3 closestThreat = null;

        boolean canCheckBlocks = canCheckBlocksNow();

        if (canCheckBlocks && !shouldSkipBlockCheckBecauseFireTick()) {
            BlockPos mobPos = mob.blockPosition();
            for (int x = -searchRadius; x <= searchRadius; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -searchRadius; z <= searchRadius; z++) {
                        BlockPos checkPos = mobPos.offset(x, y, z);
                        if (isFearedBlock(level, checkPos)) {
                            double distSqr = mobPos.distSqr(checkPos);
                            if (distSqr < closestDistSqr) {
                                closestDistSqr = distSqr;
                                closestThreat = Vec3.atCenterOf(checkPos);
                            }
                        }
                    }
                }
            }
        }

        double px = mob.getX();
        double py = mob.getY();
        double pz = mob.getZ();
        int horiz = FireFearConfig.PLAYER_CHECK_RADIUS;
        int vert = FireFearConfig.PLAYER_CHECK_VERTICAL;

        AABB playerBox = new AABB(px - horiz, py - vert, pz - horiz,
                                  px + horiz, py + vert, pz + horiz);

        List<Player> players = level.getEntitiesOfClass(Player.class, playerBox);
        for (Player pl : players) {
            if (isPlayerHoldingFearedItem(pl)) {
                double distSqr = mob.distanceToSqr(pl);
                if (distSqr < closestDistSqr) {
                    closestDistSqr = distSqr;
                    closestThreat = pl.position();
                }
            }
        }

        return closestThreat;
    }


    private boolean canCheckBlocksNow() {
        Level level = mob.level;

        double mx = mob.getX();
        double my = mob.getY();
        double mz = mob.getZ();

        int blockCheckRadius = FireFearConfig.BLOCK_CHECK_PLAYER_RADIUS;
        AABB nearBox = new AABB(
            mx - blockCheckRadius, my - 2, mz - blockCheckRadius,
            mx + blockCheckRadius, my + 2, mz + blockCheckRadius
        );

        List<Player> nearPlayers = level.getEntitiesOfClass(Player.class, nearBox);
        return !nearPlayers.isEmpty();
    }

    private boolean shouldSkipBlockCheckBecauseFireTick() {
        if (!FireFearConfig.SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF) {
            return false;
        }
        return !mob.level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
    }

    private boolean isFearedBlock(Level level, BlockPos pos) {
        Block b = level.getBlockState(pos).getBlock();
        return FireFearConfig.BLOCKS_TO_FEAR.contains(b);
    }

    private boolean isPlayerHoldingFearedItem(Player player) {
        Item main = player.getMainHandItem().getItem();
        if (FireFearConfig.ITEMS_TO_FEAR.contains(main)) return true;
        Item off = player.getOffhandItem().getItem();
        return FireFearConfig.ITEMS_TO_FEAR.contains(off);
    }
}
