package com.example.firefearmod.ai;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.util.VisionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.List;

public class FireFearGoal extends Goal {
    private final Mob mob;
    private final FearGroup fearGroup;
    private Vec3 dangerPos;
    private int scanCooldown = 0;
    private Vec3 fleeTarget;
    private int repathCooldown = 0;
    private int lostSightTicks = 0;
    private Vec3 fleeDirection = Vec3.ZERO;
    private Vec3 dangerClusterCenter = null;

    private static final int REPATH_INTERVAL_TICKS = 12;
    private static final int MAX_LOST_SIGHT_TICKS = 20;

    public FireFearGoal(Mob mob, FearGroup fearGroup) {
        this.mob = mob;
        this.fearGroup = fearGroup;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = ConfigHolder.SCAN_COOLDOWN_TICKS.get();
        dangerPos = findNearestThreat();
        return dangerPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (dangerPos == null) return false;
        double stopFleeDistSq = (fearGroup.searchRadius() + 2) * (fearGroup.searchRadius() + 2);
        if (mob.position().distanceToSqr(dangerPos) >= stopFleeDistSq) {
            return false;
        }
        return lostSightTicks <= MAX_LOST_SIGHT_TICKS;
    }

    @Override
    public void start() {
        repathCooldown = 0;
        fleeTarget = null;
        lostSightTicks = 0;
        updateFleePath(true);
    }

    @Override
    public void stop() {
        dangerPos = null;
        fleeTarget = null;
        repathCooldown = 0;
        lostSightTicks = 0;
        fleeDirection = Vec3.ZERO;
        dangerClusterCenter = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (dangerPos == null) return;
        if (!VisionHelper.canSeePosition(mob, dangerPos, true)) {
            Vec3 refreshed = findNearestThreat();
            if (refreshed != null) {
                dangerPos = refreshed;
                lostSightTicks = 0;
            } else {
                lostSightTicks++;
                if (lostSightTicks > MAX_LOST_SIGHT_TICKS) {
                    stop();
                    return;
                }
            }
        } else {
            lostSightTicks = 0;
        }

        if (repathCooldown > 0) {
            repathCooldown--;
        }
        updateFleePath(false);
    }

    private Vec3 getFleePos() {
        Vec3 baseDirection = fleeDirection;
        if (baseDirection.lengthSqr() < 1.0e-4 && dangerPos != null) {
            baseDirection = mob.position().subtract(dangerPos).normalize();
        }
        if (baseDirection.lengthSqr() < 1.0e-4) {
            baseDirection = new Vec3(mob.getRandom().nextDouble() - 0.5, 0, mob.getRandom().nextDouble() - 0.5).normalize();
        }

        double fleeDistance = Math.max(6.0, fearGroup.searchRadius());
        Vec3 desired = mob.position().add(baseDirection.scale(fleeDistance));
        return new Vec3(desired.x, mob.getY(), desired.z);
    }

    private void updateFleePath(boolean force) {
        if (dangerPos == null) {
            return;
        }
        Vec3 desiredTarget = getFleePos();
        if (force || fleeTarget == null || fleeTarget.distanceToSqr(desiredTarget) > 1.0) {
            fleeTarget = desiredTarget;
            moveToFleeTarget(desiredTarget);
            repathCooldown = REPATH_INTERVAL_TICKS;
            return;
        }

        PathNavigation navigation = mob.getNavigation();
        if (navigation.isDone() || repathCooldown <= 0) {
            moveToFleeTarget(fleeTarget);
            repathCooldown = REPATH_INTERVAL_TICKS;
        }
    }

    private void moveToFleeTarget(Vec3 target) {
        PathNavigation navigation = mob.getNavigation();
        Path path = navigation.createPath(BlockPos.containing(target), 0);
        if (path != null) {
            navigation.moveTo(path, fearGroup.fleeSpeed());
        } else {
            navigation.moveTo(target.x, target.y, target.z, fearGroup.fleeSpeed());
        }
    }

    private Vec3 findNearestThreat() {
        Level level = mob.level();
        double closestDistSqr = Double.MAX_VALUE;
        Vec3 closestThreat = null;
        Vec3 clusterSum = Vec3.ZERO;
        int threatCount = 0;

        if (canCheckBlocksNow() && !shouldSkipBlockCheckBecauseFireTick()) {
            BlockPos mobPos = mob.blockPosition();
            int radius = fearGroup.searchRadius();
            for (BlockPos checkPos : BlockPos.betweenClosed(mobPos.offset(-radius, -radius / 2, -radius), mobPos.offset(radius, radius / 2, radius))) {
                BlockState blockState = level.getBlockState(checkPos);
                if (!blockState.isAir()) {
                    BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(checkPos) : null;
                    if (fearGroup.isFearedBlock(blockState, blockEntity)) {
                        Vec3 threatPos = Vec3.atCenterOf(checkPos);
                        if (VisionHelper.canSeePosition(mob, threatPos, true)) {
                            double distSqr = mob.position().distanceToSqr(threatPos);
                            if (distSqr < closestDistSqr) {
                                closestDistSqr = distSqr;
                                closestThreat = threatPos;
                            }
                            clusterSum = clusterSum.add(threatPos);
                            threatCount++;
                        }
                    }
                }
            }
        }

        if (fearGroup.hasFearedEntities()) {
            int radius = fearGroup.searchRadius();
            double vertical = Math.max(3.0, radius * 0.75);
            AABB entityBox = mob.getBoundingBox().inflate(radius, vertical, radius);
            List<Entity> entities = level.getEntities(mob, entityBox, entity -> entity != mob && !(entity instanceof Player));
            for (Entity entity : entities) {
                if (!fearGroup.isFearedEntity(entity)) {
                    continue;
                }
                Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
                if (!VisionHelper.canSeeEntity(mob, entity, true)) {
                    continue;
                }
                double distSqr = mob.position().distanceToSqr(center);
                if (distSqr < closestDistSqr) {
                    closestDistSqr = distSqr;
                    closestThreat = center;
                }
                clusterSum = clusterSum.add(center);
                threatCount++;
            }
        }

        AABB playerBox = mob.getBoundingBox().inflate(ConfigHolder.PLAYER_CHECK_RADIUS.get(), ConfigHolder.PLAYER_CHECK_VERTICAL.get(), ConfigHolder.PLAYER_CHECK_RADIUS.get());
        List<Player> players = level.getEntitiesOfClass(Player.class, playerBox);
        for (Player player : players) {
            if (isPlayerHoldingFearedItem(player) && VisionHelper.canSeeEntity(mob, player, true)) {
                double distSqr = mob.distanceToSqr(player);
                if (distSqr < closestDistSqr) {
                    closestDistSqr = distSqr;
                    closestThreat = player.getEyePosition();
                }
                Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
                clusterSum = clusterSum.add(center);
                threatCount++;
            }
        }

        if (threatCount > 0) {
            dangerClusterCenter = clusterSum.scale(1.0 / threatCount);
            Vec3 away = mob.position().subtract(dangerClusterCenter);
            if (away.lengthSqr() > 1.0e-4) {
                fleeDirection = away.normalize();
            } else if (closestThreat != null) {
                fleeDirection = mob.position().subtract(closestThreat).normalize();
            } else {
                fleeDirection = Vec3.ZERO;
            }
        } else {
            dangerClusterCenter = null;
            fleeDirection = Vec3.ZERO;
        }
        return closestThreat;
    }

    private boolean isPlayerHoldingFearedItem(Player player) {
        return fearGroup.isFearedItem(player.getMainHandItem()) || fearGroup.isFearedItem(player.getOffhandItem());
    }

    private boolean canCheckBlocksNow() {
        AABB checkArea = mob.getBoundingBox().inflate(ConfigHolder.BLOCK_CHECK_PLAYER_RADIUS.get());
        return !mob.level().getEntitiesOfClass(Player.class, checkArea).isEmpty();
    }

    private boolean shouldSkipBlockCheckBecauseFireTick() {
        if (!ConfigHolder.SKIP_BLOCK_CHECK_IF_FIRE_TICK_OFF.get()) {
            return false;
        }
        return !mob.level().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
    }
}