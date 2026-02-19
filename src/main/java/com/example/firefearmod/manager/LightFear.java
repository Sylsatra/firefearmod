package com.example.firefearmod.manager;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public record LightFear(boolean enabled, Mode mode, int threshold, Layer layer, int hysteresis) {
    public enum Mode { ABOVE, BELOW }
    public enum Layer { COMBINED, BLOCK, SKY }

    public static LightFear fromConfig(Config cfg) {
        if (cfg == null) return disabled();
        boolean enabled = cfg.getOptional("enabled").map(o -> (Boolean) o).orElse(false);
        String modeStr = cfg.getOptional("mode").map(String::valueOf).orElse("ABOVE");
        String layerStr = cfg.getOptional("layer").map(String::valueOf).orElse("COMBINED");
        int threshold = clamp(cfg.getOptional("threshold").map(o -> ((Number) o).intValue()).orElse(11), 0, 15);
        int hysteresis = clamp(cfg.getOptional("hysteresis").map(o -> ((Number) o).intValue()).orElse(1), 0, 15);
        Mode mode = modeStr.equalsIgnoreCase("BELOW") ? Mode.BELOW : Mode.ABOVE;
        Layer layer = switch (layerStr.toUpperCase()) {
            case "BLOCK" -> Layer.BLOCK;
            case "SKY" -> Layer.SKY;
            default -> Layer.COMBINED;
        };
        return new LightFear(enabled, mode, threshold, layer, hysteresis);
    }

    public static LightFear disabled() { return new LightFear(false, Mode.ABOVE, 11, Layer.COMBINED, 1); }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public int sampleBrightness(Level level, BlockPos pos) {
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken();
        if (skyLight < 0) skyLight = 0;

        return switch (layer) {
            case BLOCK -> blockLight;
            case SKY -> skyLight;
            case COMBINED -> Math.max(blockLight, skyLight);
        };
    }

    public boolean isViolation(Level level, BlockPos pos) {
        int b = sampleBrightness(level, pos);
        return switch (mode) {
            case ABOVE -> b >= threshold;
            case BELOW -> b <= threshold;
        };
    }

    public boolean isSafe(Level level, BlockPos pos) {
        int b = sampleBrightness(level, pos);
        return switch (mode) {
            case ABOVE -> b <= Math.max(0, threshold - hysteresis);
            case BELOW -> b >= Math.min(15, threshold + hysteresis);
        };
    }
}
