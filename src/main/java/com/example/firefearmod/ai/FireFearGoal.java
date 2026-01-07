package com.example.firefearmod.ai;

import com.example.firefearmod.config.ConfigHolder;
import com.example.firefearmod.manager.FearGroup;
import com.example.firefearmod.manager.FearGroup.FearSourceDefinition;
import com.example.firefearmod.manager.IFearProfile;
import com.example.firefearmod.util.VisionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;

import java.util.EnumSet;
import java.util.List;

public class FireFearGoal extends Goal {
    private final Mob mob;
    private final IFearProfile fearGroup;
    private Vec3 dangerPos;
    private int scanCooldown = 0;
    private Vec3 fleeTarget;
    private int repathCooldown = 0;
    private int lostSightTicks = 0;
    private Vec3 fleeDirection = Vec3.ZERO;
    private Vec3 dangerClusterCenter = null;
    private boolean overrideHostilityActive = false;
    private boolean targetingSuppressed = false;
    private IFearProfile.VisibilityMode dangerVisibilityMode = IFearProfile.VisibilityMode.LOOK_BASED;
    private double activeFleeSpeed = 1.2;
    private Entity dangerEntity;

    private static final int REPATH_INTERVAL_TICKS = 12;
    private static final int MAX_LOST_SIGHT_TICKS = 40;
    private static final double OVERRIDE_PRIORITY_BONUS = 16.0;
    private static final double DIRECTION_RETENTION = 0.7;

    public FireFearGoal(Mob mob, IFearProfile fearGroup) {
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
        
        // Immediate check for entity validity (prevents Creative/Spectator/Lure lag)
        if (dangerEntity != null) {
            if (!dangerEntity.isAlive()) return false;
            if (dangerEntity instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
            if (fearGroup.shouldOverrideHostility(dangerEntity)) return false;
            if (!fearGroup.isFearedEntity(dangerEntity)) {
                // Check if they are still holding a fear source
                if (dangerEntity instanceof Player p && getPlayerHeldFearSource(p) == null) return false;
                else if (!(dangerEntity instanceof Player)) return false;
            }
        }

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
        if (overrideHostilityActive) {
            suppressHostileTarget();
        }
        updateFleePath(true);
    }

    @Override
    public void stop() {
        dangerPos = null;
        dangerEntity = null;
        fleeTarget = null;
        repathCooldown = 0;
        lostSightTicks = 0;
        fleeDirection = Vec3.ZERO;
        dangerClusterCenter = null;
        overrideHostilityActive = false;
        dangerVisibilityMode = IFearProfile.VisibilityMode.LOOK_BASED;
        restoreTargetControl();
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (dangerPos == null) return;

        // Reactive scan for state changes (Lures/Creative)
        if (mob.tickCount % 5 == 0) {
            Vec3 refreshed = findNearestThreat();
            if (refreshed != null) {
                dangerPos = refreshed;
            } else {
                // If threat is gone completely (skipped in findNearestThreat), stop immediately
                if (dangerEntity != null && (dangerEntity.isRemoved() || (dangerEntity instanceof Player p && (p.isCreative() || p.isSpectator())) || fearGroup.shouldOverrideHostility(dangerEntity))) {
                    stop();
                    return;
                }
            }
        }

        if (dangerVisibilityMode == IFearProfile.VisibilityMode.ALWAYS) {
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
        }

        if (overrideHostilityActive) {
            suppressHostileTarget();
        } else {
            restoreTargetControl();
        }

        if (repathCooldown > 0) {
            repathCooldown--;
        }
        updateFleePath(false);
    }

    @Override
    public boolean isInterruptable() {
        return false;
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
        if (mob instanceof PathfinderMob pathfinderMob) {
            if (dangerClusterCenter != null) {
                Vec3 away = DefaultRandomPos.getPosAway(pathfinderMob, (int) Math.ceil(fleeDistance), 4, dangerClusterCenter);
                if (away != null) {
                    return away;
                }
            } else if (dangerPos != null) {
                Vec3 away = DefaultRandomPos.getPosAway(pathfinderMob, (int) Math.ceil(fleeDistance), 4, dangerPos);
                if (away != null) {
                    return away;
                }
            }
        }
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
            navigation.moveTo(path, this.activeFleeSpeed);
        } else {
            navigation.moveTo(target.x, target.y, target.z, this.activeFleeSpeed);
        }
    }

    private Vec3 findNearestThreat() {
        Level level = mob.level();
        double bestPriority = Double.MAX_VALUE;
        Vec3 closestThreatPos = null;
        Entity closestThreatEntity = null;
        Vec3 clusterSum = Vec3.ZERO;
        int threatCount = 0;
        IFearProfile.VisibilityMode bestMode = IFearProfile.VisibilityMode.LOOK_BASED;
        double speedAccumulator = 0;
        int speedCount = 0;
        boolean anyOverrideActive = false;

        int radius = fearGroup.searchRadius();

        if (canCheckBlocksNow() && !shouldSkipBlockCheckBecauseFireTick()) {
            BlockPos mobPos = mob.blockPosition();
            for (BlockPos checkPos : BlockPos.betweenClosed(mobPos.offset(-radius, -radius / 2, -radius), mobPos.offset(radius, radius / 2, radius))) {
                BlockState blockState = level.getBlockState(checkPos);
                if (!blockState.isAir()) {
                    BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(checkPos) : null;
                    
                    boolean isOverride = fearGroup.shouldOverrideHostility(blockState, blockEntity);
                    FearSourceDefinition blockDef = fearGroup.findFearedBlock(blockState, blockEntity);
                    
                    if (isOverride) {
                        anyOverrideActive = true;
                        continue;
                    }
                    
                    if (blockDef != null) {
                        Vec3 threatPos = Vec3.atCenterOf(checkPos);
                        if (VisionHelper.canSeePosition(mob, threatPos, true)) {
                            double distSqr = mob.position().distanceToSqr(threatPos);
                            if (distSqr < bestPriority) {
                                bestPriority = distSqr;
                                closestThreatPos = threatPos;
                                closestThreatEntity = null;
                            }
                            clusterSum = clusterSum.add(threatPos);
                            threatCount++;
                            speedAccumulator += fearGroup.getFleeSpeedFor(blockState);
                            speedCount++;
                        }
                    }
                }
            }
        }

        if (fearGroup.hasFearedEntities()) {
            double vertical = Math.max(3.0, radius * 0.75);
            AABB entityBox = mob.getBoundingBox().inflate(radius, vertical, radius);
            List<Entity> entities = level.getEntities(mob, entityBox, entity -> entity != mob);
            for (Entity entity : entities) {
                if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                
                boolean isOverride = fearGroup.shouldOverrideHostility(entity);
                if (isOverride) {
                    anyOverrideActive = true;
                    continue;
                }

                if (!fearGroup.isFearedEntity(entity)) {
                    continue;
                }
                IFearProfile.VisibilityMode mode = fearGroup.getEntityVisibilityMode(entity);
                boolean visible;
                if (mode == IFearProfile.VisibilityMode.ALWAYS) {
                    visible = VisionHelper.canSeeEntity(mob, entity, true);
                } else {
                    visible = isMutuallyVisible(entity);
                }
                if (!visible) {
                    continue;
                }
                Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
                double distSqr = mob.position().distanceToSqr(center);
                if (distSqr < bestPriority) {
                    bestPriority = distSqr;
                    closestThreatPos = center;
                    closestThreatEntity = entity;
                    bestMode = mode;
                }
                clusterSum = clusterSum.add(center);
                threatCount++;
                speedAccumulator += fearGroup.getFleeSpeedFor(entity);
                speedCount++;
            }
        }

        AABB playerBox = mob.getBoundingBox().inflate(radius, radius * 0.75, radius);
        List<Player> players = level.getEntitiesOfClass(Player.class, playerBox);
        for (Player player : players) {
            if (player.isCreative() || player.isSpectator()) continue;

            boolean isOverride = fearGroup.shouldOverrideHostility(player);
            if (isOverride) {
                anyOverrideActive = true;
                continue;
            }

            boolean isFeared = fearGroup.isFearedEntity(player);
            FearSourceDefinition heldFear = isFeared ? null : getPlayerHeldFearSource(player);

            if (!isFeared && heldFear == null) {
                continue;
            }

            IFearProfile.VisibilityMode mode = isFeared ? fearGroup.getEntityVisibilityMode(player) : IFearProfile.VisibilityMode.LOOK_BASED;
            boolean visible;
            if (mode == IFearProfile.VisibilityMode.ALWAYS) {
                visible = VisionHelper.canSeeEntity(mob, player, true);
            } else {
                visible = isMutuallyVisible(player);
            }

            if (visible) {
                 if (heldFear != null && heldFear.fearOverride()) {
                     anyOverrideActive = true;
                     continue; 
                 }
                 
                 double distSqr = mob.distanceToSqr(player);
                 if (distSqr < bestPriority) {
                     bestPriority = distSqr;
                     closestThreatPos = player.getEyePosition();
                     closestThreatEntity = player;
                     bestMode = mode;
                 }
                 Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
                 clusterSum = clusterSum.add(center);
                 threatCount++;
                 
                 if (isFeared) {
                     speedAccumulator += fearGroup.getFleeSpeedFor(player);
                 } else if (heldFear != null) {
                     ItemStack stack = player.getMainHandItem();
                     if (fearGroup.findFearedItem(stack) == null) stack = player.getOffhandItem();
                     speedAccumulator += fearGroup.getFleeSpeedFor(stack);
                 } else {
                     speedAccumulator += fearGroup.fleeSpeed();
                 }
                 speedCount++;
            }
        }

        if (threatCount > 0) {
            dangerClusterCenter = clusterSum.scale(1.0 / threatCount);
            this.activeFleeSpeed = speedCount > 0 ? (speedAccumulator / speedCount) : fearGroup.fleeSpeed();
            
            Vec3 away = mob.position().subtract(dangerClusterCenter);
            Vec3 newDir = Vec3.ZERO;
            if (away.lengthSqr() > 1.0e-4) {
                newDir = away.normalize();
            } else if (closestThreatPos != null) {
                Vec3 candidate = mob.position().subtract(closestThreatPos).normalize();
                if (candidate.lengthSqr() > 1.0e-4) {
                    newDir = candidate;
                }
            }
            if (newDir.lengthSqr() > 1.0e-4) {
                if (fleeDirection.lengthSqr() > 1.0e-4) {
                    Vec3 blended = fleeDirection.scale(DIRECTION_RETENTION).add(newDir.scale(1.0 - DIRECTION_RETENTION));
                    double len = blended.length();
                    fleeDirection = len > 1.0e-4 ? blended.scale(1.0 / len) : newDir;
                } else {
                    fleeDirection = newDir;
                }
            }
        } else {
            dangerClusterCenter = null;
            fleeDirection = Vec3.ZERO;
        }
        
        this.overrideHostilityActive = anyOverrideActive;
        this.dangerVisibilityMode = bestMode;
        this.dangerEntity = closestThreatEntity;
        return closestThreatPos;
    }

    private boolean isMutuallyVisible(Entity entity) {
        if (!VisionHelper.canSeeEntity(mob, entity, true)) {
            return false;
        }
        if (entity instanceof Mob otherMob) {
            return VisionHelper.canSeeEntity(otherMob, mob, true);
        }
        if (entity instanceof Player player) {
            Vec3 playerEye = player.getEyePosition();
            Vec3 view = player.getViewVector(1.0F);
            Vec3 toMob = mob.getEyePosition().subtract(playerEye);
            double length = toMob.length();
            if (length < 1.0E-6) {
                return true;
            }
            Vec3 toMobNorm = toMob.scale(1.0 / length);
            if (Double.isNaN(toMobNorm.x) || Double.isNaN(toMobNorm.y) || Double.isNaN(toMobNorm.z)) {
                return false;
            }
            Vec3 viewHorizontal = new Vec3(view.x, 0.0, view.z);
            Vec3 targetHorizontal = new Vec3(toMobNorm.x, 0.0, toMobNorm.z);
            if (viewHorizontal.lengthSqr() < 1.0E-6 || targetHorizontal.lengthSqr() < 1.0E-6) {
                return true;
            }
            double dotHorizontal = viewHorizontal.normalize().dot(targetHorizontal.normalize());
            double clampedHorizontal = Math.max(-1.0, Math.min(1.0, dotHorizontal));
            double angleHorizontal = Math.toDegrees(Math.acos(clampedHorizontal));
            if (angleHorizontal > 60.0) {
                return false;
            }
            double lookPitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, view.y))));
            double targetPitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toMobNorm.y))));
            double verticalDiff = Math.abs(targetPitch - lookPitch);
            return verticalDiff <= 60.0;
        }
        return true;
    }

    @Nullable
    private FearSourceDefinition getPlayerHeldFearSource(Player player) {
        FearSourceDefinition main = fearGroup.findFearedItem(player.getMainHandItem());
        if (main != null) {
            return main;
        }
        return fearGroup.findFearedItem(player.getOffhandItem());
    }

    private void suppressHostileTarget() {
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
        mob.setLastHurtByMob(null);
        mob.setAggressive(false);
        if (!targetingSuppressed) {
            GoalSelector selector = mob.targetSelector;
            if (selector != null) {
                selector.disableControlFlag(Goal.Flag.TARGET);
                targetingSuppressed = true;
            }
        }
    }

    private void restoreTargetControl() {
        if (!targetingSuppressed) {
            return;
        }
        GoalSelector selector = mob.targetSelector;
        if (selector != null) {
            selector.enableControlFlag(Goal.Flag.TARGET);
        }
        targetingSuppressed = false;
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