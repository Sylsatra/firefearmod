package com.example.firefearmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

public final class VisionHelper {
    private static final String SOUND_ATTRACT_MOD_ID = "soundattract";
    private static final String QUANTIFIED_MOD_ID = "quantified";
    private static final boolean SOUND_ATTRACT_LOADED = ModList.get().isLoaded(SOUND_ATTRACT_MOD_ID);
    private static final boolean QUANTIFIED_LOADED = ModList.get().isLoaded(QUANTIFIED_MOD_ID);
    private static final double FALLBACK_FOV_DEGREES = 120.0;

    private VisionHelper() {
    }

    public static boolean canSeeEntity(Mob looker, Entity target, boolean checkObstructions) {
        if (looker == null || target == null) {
            return false;
        }
        if (SOUND_ATTRACT_LOADED) {
            try {
                Boolean result = SoundAttractBridge.isEntityInFov(looker, target, checkObstructions);
                if (result != null) {
                    return result;
                }
            } catch (Throwable ignored) {
                return fallbackEntityCheck(looker, target, checkObstructions);
            }
        }
        return fallbackEntityCheck(looker, target, checkObstructions);
    }

    public static boolean canSeePosition(Mob looker, Vec3 target, boolean checkObstructions) {
        if (looker == null || target == null) {
            return false;
        }
        if (SOUND_ATTRACT_LOADED) {
            try {
                Boolean result = SoundAttractBridge.isPositionInFov(looker, target, checkObstructions);
                if (result != null) {
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }
        if (checkObstructions && !hasLineOfSight(looker, target)) {
            return false;
        }
        return isWithinFallbackFov(looker, target);
    }

    public static boolean hasLineOfSight(Mob looker, Vec3 target) {
        if (looker == null || target == null) {
            return false;
        }
        
        if (QUANTIFIED_LOADED) {
             try {
                 return QuantifiedBridge.getCachedLineOfSight(looker, target);
             } catch (Throwable ignored) {
             }
        }

        return computeLineOfSight(looker, target);
    }

    private static boolean computeLineOfSight(Mob looker, Vec3 target) {
        Vec3 eyePos = looker.getEyePosition();
        Vec3 direction = target.subtract(eyePos);
        double distance = direction.length();
        if (distance < 1.0E-4) {
            return true;
        }
        Vec3 adjustedTarget = target;
        if (distance > 0.05) {
            double offsetMagnitude = Math.min(0.51, distance - 0.05);
            if (offsetMagnitude > 0.0) {
                Vec3 offset = direction.scale(1.0 / distance).scale(offsetMagnitude);
                adjustedTarget = target.subtract(offset);
            }
        }
        Level level = looker.level();
        ClipContext context = new ClipContext(eyePos, adjustedTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker);
        BlockHitResult hit = level.clip(context);
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        BlockPos targetPos = BlockPos.containing(target);
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(targetPos);
    }

    public static boolean hasLineOfSight(Mob looker, Entity target) {
        return hasLineOfSight(looker, target.getEyePosition());
    }

    private static boolean fallbackEntityCheck(Mob looker, Entity target, boolean checkObstructions) {
        if (checkObstructions && !hasLineOfSight(looker, target)) {
            return false;
        }
        return isWithinFallbackFov(looker, target.getEyePosition());
    }

    private static boolean isWithinFallbackFov(Mob looker, Vec3 target) {
        Vec3 lookVector = looker.getLookAngle().normalize();
        Vec3 toTargetVector = target.subtract(looker.getEyePosition()).normalize();

        if (Double.isNaN(toTargetVector.x) || Double.isNaN(toTargetVector.y) || Double.isNaN(toTargetVector.z)) {
            return false;
        }

        Vec3 lookHorizontal = new Vec3(lookVector.x, 0, lookVector.z);
        Vec3 targetHorizontal = new Vec3(toTargetVector.x, 0, toTargetVector.z);
        if (lookHorizontal.lengthSqr() < 1.0E-6 || targetHorizontal.lengthSqr() < 1.0E-6) {
            return true;
        }
        double dotHorizontal = lookHorizontal.normalize().dot(targetHorizontal.normalize());
        double clampedHorizontal = Math.max(-1.0, Math.min(1.0, dotHorizontal));
        double angleHorizontal = Math.toDegrees(Math.acos(clampedHorizontal));
        if (angleHorizontal > FALLBACK_FOV_DEGREES * 0.5) {
            return false;
        }

        double lookPitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, lookVector.y))));
        double targetPitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTargetVector.y))));
        double verticalDiff = Math.abs(targetPitch - lookPitch);
        return verticalDiff <= FALLBACK_FOV_DEGREES * 0.5;
    }

    private static final class SoundAttractBridge {
        private static final Class<?> FOV_EVENTS_CLASS;
        private static final java.lang.reflect.Method IS_TARGET_IN_FOV;

        static {
            Class<?> clazz;
            java.lang.reflect.Method method;
            try {
                clazz = Class.forName("com.example.soundattract.event.FovEvents");
                method = clazz.getMethod("isTargetInFov", Mob.class, Entity.class, boolean.class);
            } catch (Throwable throwable) {
                clazz = null;
                method = null;
            }
            FOV_EVENTS_CLASS = clazz;
            IS_TARGET_IN_FOV = method;
        }

        private static Boolean isEntityInFov(Mob looker, Entity target, boolean checkObstructions) throws ReflectiveOperationException {
            if (FOV_EVENTS_CLASS == null || IS_TARGET_IN_FOV == null) {
                return null;
            }
            Object result = IS_TARGET_IN_FOV.invoke(null, looker, target, checkObstructions);
            return result instanceof Boolean bool ? bool : null;
        }

        private static Boolean isPositionInFov(Mob looker, Vec3 target, boolean checkObstructions) throws ReflectiveOperationException {
            if (FOV_EVENTS_CLASS == null || IS_TARGET_IN_FOV == null) {
                return null;
            }
            Level level = looker.level();
            ArmorStand probe = EntityType.ARMOR_STAND.create(level);
            if (probe == null) {
                return null;
            }
            probe.setPos(target.x, target.y, target.z);
            try {
                Object result = IS_TARGET_IN_FOV.invoke(null, looker, probe, checkObstructions);
                return result instanceof Boolean bool ? bool : null;
            } finally {
                probe.discard();
            }
        }
    }
    
    private static final class QuantifiedBridge {
        private static final Class<?> API_CLASS;
        private static final java.lang.reflect.Method GET_CACHED_METHOD;

        static {
            Class<?> clazz;
            java.lang.reflect.Method method;
            try {
                clazz = Class.forName("org.admany.quantified.api.QuantifiedAPI");
                method = clazz.getMethod("getCached", String.class, String.class, java.util.function.Supplier.class);
            } catch (Throwable t) {
                clazz = null;
                method = null;
            }
            API_CLASS = clazz;
            GET_CACHED_METHOD = method;
        }

        private static boolean getCachedLineOfSight(Mob looker, Vec3 target) throws ReflectiveOperationException {
            if (API_CLASS == null || GET_CACHED_METHOD == null) {
                return computeLineOfSight(looker, target);
            }
             
            Vec3 start = looker.getEyePosition();
            String dim = looker.level().dimension().location().toString();
            String key = new StringBuilder(96)
                    .append(dim).append('|')
                    .append(q(start.x)).append(',').append(q(start.y)).append(',').append(q(start.z)).append('|')
                    .append(q(target.x)).append(',').append(q(target.y)).append(',').append(q(target.z))
                    .toString();

            java.util.function.Supplier<Boolean> loader = () -> computeLineOfSight(looker, target);
            
            Object result = GET_CACHED_METHOD.invoke(null, "firefear_los", key, loader);
            return result instanceof Boolean bool ? bool : computeLineOfSight(looker, target);
        }

        private static int q(double v) {
            return (int) Math.round(v * 4.0);
        }
    }
}
