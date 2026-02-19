package com.example.firefearmod.ai;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.IFearProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class LightFearGoal extends Goal {
    protected final Mob mob;
    protected final IFearProfile fearProfile;
    protected int cooldownTicks = 0;
    protected Vec3 targetPos;

    public LightFearGoal(Mob mob, IFearProfile fearProfile) {
        this.mob = mob;
        this.fearProfile = fearProfile;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return false;
        }

        if (this.mob == null || this.mob.isDeadOrDying()) {
            return false;
        }

        if (!this.fearProfile.hasLightFear()) {
            this.cooldownTicks = 100; 
            return false;
        }

        Level level = this.mob.level();
        if (level == null) return false;

        if (this.fearProfile.isPositionSafeFromLight(level, this.mob.blockPosition())) {
            this.cooldownTicks = ConfigHolder.LIGHT_CHECK_COOLDOWN_TICKS.get();
            return false;
        }

        this.targetPos = this.findSafeTarget();
        if (this.targetPos == null) {
            this.cooldownTicks = ConfigHolder.LIGHT_CHECK_COOLDOWN_TICKS.get();
            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob == null || this.mob.getNavigation().isDone() || this.targetPos == null) {
            return false;
        }
        Level level = this.mob.level();
        if (level == null) return false;
        
        return !this.fearProfile.isPositionSafeFromLight(level, this.mob.blockPosition());
    }

    @Override
    public void start() {
        if (this.targetPos != null && this.mob != null) {
            PathNavigation nav = this.mob.getNavigation();
            if (nav != null) {
                nav.moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, this.fearProfile.fleeSpeed());
            }
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.cooldownTicks = ConfigHolder.LIGHT_CHECK_COOLDOWN_TICKS.get();
    }

    @Override
    public void tick() {
        if (this.mob == null || this.targetPos == null) return;
        
        if (this.mob.tickCount % 20 == 0) {
            Level level = this.mob.level();
            BlockPos targetBlockPos = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
            if (level != null && !this.fearProfile.isPositionSafeFromLight(level, targetBlockPos)) {
                 this.stop();
            }
        }
    }

    @Nullable
    protected Vec3 findSafeTarget() {
        if (this.mob == null) return null;
        Level level = this.mob.level();
        if (level == null) return null;

        if (this.targetPos != null) {
            BlockPos currentTargetBlock = BlockPos.containing(this.targetPos);
            if (this.fearProfile.isPositionSafeFromLight(level, currentTargetBlock) &&
                this.mob.position().distanceToSqr(this.targetPos) > 2.0) {
                 return this.targetPos;
            }
        }

        RandomSource random = this.mob.getRandom();
        BlockPos origin = this.mob.blockPosition();
        int radius = this.fearProfile.searchRadius();
        
        Vec3 forward = this.mob.getLookAngle();
        Vec3 bestPos = null;
        double bestScore = -Double.MAX_VALUE;

        for (int i = 0; i < 20; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt(5) - 2;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos candidate = origin.offset(dx, dy, dz);

            if (this.fearProfile.isPositionSafeFromLight(level, candidate) && 
                level.getBlockState(candidate).isAir() && 
                level.getBlockState(candidate.above()).isAir()) {
                
                Vec3 candidateVec = Vec3.atCenterOf(candidate);
                Vec3 directionToCandidate = candidateVec.subtract(this.mob.position()).normalize();
                
                double dot = forward.dot(directionToCandidate);
                double distSqr = this.mob.position().distanceToSqr(candidateVec);
                
                double score = dot * 2.0 + Math.min(distSqr, 100.0) * 0.05;
                
                if (score > bestScore) {
                    bestScore = score;
                    bestPos = candidateVec;
                }
            }
        }
        
        return bestPos; 
    }
}
