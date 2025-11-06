package com.example.firefearmod.ai;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.manager.FearGroupManager;
import com.example.firefearmod.manager.LightFear;
import com.example.firefearmod.util.VisionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import net.minecraft.util.RandomSource;

public class LightFearGoal extends Goal {
    private final Mob mob;
    private final FearGroup group;
    private final LightFear lightFear;

    private BlockPos targetPos;
    private int scanCooldown;
    private long lastRepathGameTime;
    private int lastTargetBrightness;
    private Vec3 violationPos;

    public LightFearGoal(Mob mob, FearGroup group) {
        this.mob = mob;
        this.group = group;
        this.lightFear = FearGroupManager.getLightFearForGroup(group);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!lightFear.enabled()) return false;
        Level level = mob.level();
        if (scanCooldown > 0) { scanCooldown--; return false; }
        violationPos = findVisibleViolation();
        if (violationPos == null) {
            return false;
        }
        targetPos = findSafeTarget();
        scanCooldown = ConfigHolder.LIGHT_CHECK_COOLDOWN_TICKS.get();
        lastRepathGameTime = level.getGameTime();
        if (targetPos != null) {
            lastTargetBrightness = lightFear.sampleBrightness(level, targetPos);
        }
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!lightFear.enabled()) return false;
        if (targetPos == null) return false;
        Level level = mob.level();
        boolean safeHere = lightFear.isSafe(level, mob.blockPosition());
        if (violationPos != null && !VisionHelper.canSeePosition(mob, violationPos, true)) {
            violationPos = findVisibleViolation();
            if (violationPos == null) {
                return false;
            }
        }
        if (!safeHere) return true;
        double dist2 = Vec3.atCenterOf(targetPos).distanceToSqr(mob.position());
        return dist2 > 1.44; 
    }

    @Override
    public void start() {
        moveToTarget();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;
        PathNavigation nav = mob.getNavigation();
        long now = mob.level().getGameTime();
        if (violationPos == null || !VisionHelper.canSeePosition(mob, violationPos, true)) {
            violationPos = findVisibleViolation();
            if (violationPos == null) {
                stop();
                return;
            }
        }
        if (now - lastRepathGameTime >= ConfigHolder.LIGHT_CHECK_COOLDOWN_TICKS.get()) {
            if (nav.isDone() || !lightFear.isSafe(mob.level(), targetPos)) {
                BlockPos newTarget = findSafeTarget();
                if (newTarget != null && !newTarget.equals(targetPos)) {
                    targetPos = newTarget;
                    moveToTarget();
                    lastTargetBrightness = lightFear.sampleBrightness(mob.level(), targetPos);
                }
                lastRepathGameTime = now;
            }
        }
    }

    @Override
    public void stop() {
        targetPos = null;
        violationPos = null;
        mob.getNavigation().stop();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    private void moveToTarget() {
        if (targetPos == null) return;
        PathNavigation nav = mob.getNavigation();
        Path path = nav.createPath(targetPos, 0);
        if (path != null) {
            nav.moveTo(path, group.fleeSpeed());
        } else {
            Vec3 c = Vec3.atCenterOf(targetPos);
            nav.moveTo(c.x, c.y, c.z, group.fleeSpeed());
        }
    }

    private BlockPos findSafeTarget() {
        Level level = mob.level();
        RandomSource rng = mob.getRandom();
        BlockPos origin = mob.blockPosition();
        int radius = Math.max(2, group.searchRadius());
        int curB = lightFear.sampleBrightness(level, origin);
        int minGain = Math.max(1, lightFear.hysteresis() + 1);

        BlockPos bestSafe = null;
        int bestSafeB = (lightFear.mode() == LightFear.Mode.BELOW) ? -1 : 16;
        double bestSafeD2 = Double.MAX_VALUE;

        BlockPos bestFallback = null;
        int bestFallbackB = curB;
        double bestFallbackD2 = Double.MAX_VALUE;

        final int attempts = Math.min(80, 24 + radius * 2);
        final int vRange = Math.min(2, Math.max(1, radius / 4));
        for (int i = 0; i < attempts; i++) {
            int dx = rng.nextInt(radius * 2 + 1) - radius;
            int dz = rng.nextInt(radius * 2 + 1) - radius;
            int dy = rng.nextInt(vRange * 2 + 1) - vRange;
            BlockPos sample = origin.offset(dx, dy, dz);
            int b = lightFear.sampleBrightness(level, sample);
            Path path = mob.getNavigation().createPath(sample, 0);
            if (path == null) continue;
            double d2 = sample.distSqr(origin);
            if (d2 < 4.0) continue;

            boolean isSafe = lightFear.isSafe(level, sample);
            if (isSafe) {
                if (lightFear.mode() == LightFear.Mode.BELOW) {
                    if (b > bestSafeB || (b == bestSafeB && d2 < bestSafeD2)) {
                        bestSafeB = b;
                        bestSafeD2 = d2;
                        bestSafe = sample;
                    }
                } else {
                    if (b < bestSafeB || (b == bestSafeB && d2 < bestSafeD2)) {
                        bestSafeB = b;
                        bestSafeD2 = d2;
                        bestSafe = sample;
                    }
                }
            } else {
                if (lightFear.mode() == LightFear.Mode.BELOW) {
                    if (b >= curB + minGain && (b > bestFallbackB || (b == bestFallbackB && d2 < bestFallbackD2))) {
                        bestFallbackB = b;
                        bestFallbackD2 = d2;
                        bestFallback = sample;
                    }
                } else {
                    if (b <= curB - minGain && (b < bestFallbackB || (b == bestFallbackB && d2 < bestFallbackD2))) {
                        bestFallbackB = b;
                        bestFallbackD2 = d2;
                        bestFallback = sample;
                    }
                }
            }
        }
        return bestSafe != null ? bestSafe : bestFallback;
    }

    private Vec3 findVisibleViolation() {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        int radius = Math.max(2, group.searchRadius());
        int vRange = Math.max(1, radius / 2);
        double bestDist = Double.MAX_VALUE;
        Vec3 best = null;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -vRange, -radius), origin.offset(radius, vRange, radius))) {
            if (!lightFear.isViolation(level, pos)) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(pos);
            if (!VisionHelper.canSeePosition(mob, center, true)) {
                continue;
            }
            double dist = center.distanceToSqr(mob.position());
            if (dist < bestDist) {
                bestDist = dist;
                best = center;
            }
        }

        return best;
    }
}
