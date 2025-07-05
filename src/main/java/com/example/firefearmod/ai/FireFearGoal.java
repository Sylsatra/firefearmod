package com.example.firefearmod.ai;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity; // FIX: Added missing import
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class FireFearGoal extends Goal {
    private final Mob mob;
    private final FearGroup fearGroup;
    private Vec3 dangerPos;
    private int scanCooldown = 0;

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
        return mob.position().distanceToSqr(dangerPos) < stopFleeDistSq && hasLineOfSight(dangerPos);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        dangerPos = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (dangerPos == null) return;
        PathNavigation navigation = mob.getNavigation();
        Vec3 fleePos = getFleePos();
        navigation.moveTo(fleePos.x, fleePos.y, fleePos.z, fearGroup.fleeSpeed());
    }

    private Vec3 getFleePos() {
        return new Vec3(
                mob.getX() + (mob.getX() - dangerPos.x()),
                mob.getY(),
                mob.getZ() + (mob.getZ() - dangerPos.z())
        );
    }

    private Vec3 findNearestThreat() {
        Level level = mob.level();
        double closestDistSqr = Double.MAX_VALUE;
        Vec3 closestThreat = null;

        if (canCheckBlocksNow() && !shouldSkipBlockCheckBecauseFireTick()) {
            BlockPos mobPos = mob.blockPosition();
            int radius = fearGroup.searchRadius();
            for (BlockPos checkPos : BlockPos.betweenClosed(mobPos.offset(-radius, -radius / 2, -radius), mobPos.offset(radius, radius / 2, radius))) {
                BlockState blockState = level.getBlockState(checkPos);
                if (!blockState.isAir()) {
                    // FIX: This entire block is now correct.
                    BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(checkPos) : null;
                    if (fearGroup.isFearedBlock(blockState, blockEntity)) {
                        Vec3 threatPos = Vec3.atCenterOf(checkPos);
                        if (hasLineOfSight(threatPos)) {
                            double distSqr = mob.position().distanceToSqr(threatPos);
                            if (distSqr < closestDistSqr) {
                                closestDistSqr = distSqr;
                                closestThreat = threatPos;
                            }
                        }
                    }
                }
            }
        }

        AABB playerBox = mob.getBoundingBox().inflate(ConfigHolder.PLAYER_CHECK_RADIUS.get(), ConfigHolder.PLAYER_CHECK_VERTICAL.get(), ConfigHolder.PLAYER_CHECK_RADIUS.get());
        List<Player> players = level.getEntitiesOfClass(Player.class, playerBox);
        for (Player player : players) {
            if (isPlayerHoldingFearedItem(player) && hasLineOfSight(player.position())) {
                double distSqr = mob.distanceToSqr(player);
                if (distSqr < closestDistSqr) {
                    closestDistSqr = distSqr;
                    closestThreat = player.position();
                }
            }
        }
        return closestThreat;
    }

    private boolean isPlayerHoldingFearedItem(Player player) {
        return fearGroup.isFearedItem(player.getMainHandItem()) || fearGroup.isFearedItem(player.getOffhandItem());
    }

    private boolean hasLineOfSight(Vec3 target) {
        Vec3 eyePos = mob.getEyePosition();
        ClipContext context = new ClipContext(eyePos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob);
        return mob.level().clip(context).getType() == BlockHitResult.Type.MISS;
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