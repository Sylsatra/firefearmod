package com.example.firefearmod.manager;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record FearGroup(
        String groupId,
        double fleeSpeed,
        int searchRadius,
        List<MobDefinition> mobs,
        List<FearSourceDefinition> fearedBlocks,
        List<FearSourceDefinition> fearedItems
) {

    public static FearGroup fromConfig(Config config) {
        String groupId = config.get("group_id");
        double fleeSpeed = config.getOptional("flee_speed").map(o -> ((Number) o).doubleValue()).orElse(1.2);
        int searchRadius = config.getOptional("search_radius").map(o -> ((Number) o).intValue()).orElse(8);
        List<MobDefinition> mobs = ((List<Config>) config.get("mobs")).stream()
                .map(MobDefinition::fromConfig).collect(Collectors.toList());
        List<FearSourceDefinition> fearedBlocks = ((List<Config>) config.get("feared_blocks")).stream()
                .map(FearSourceDefinition::fromConfig).collect(Collectors.toList());
        List<FearSourceDefinition> fearedItems = ((List<Config>) config.get("feared_items")).stream()
                .map(FearSourceDefinition::fromConfig).collect(Collectors.toList());
        return new FearGroup(groupId, fleeSpeed, searchRadius, mobs, fearedBlocks, fearedItems);
    }

    public int getMatchScore(Mob mob) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) return -1;
        int bestScore = -1;
        for (MobDefinition def : mobs) {
            int score = def.getMatchScore(mob, mobId);
            if (score > bestScore) {
                bestScore = score;
            }
        }
        return bestScore;
    }
    
    public boolean isFearedBlock(BlockState blockState, @Nullable BlockEntity blockEntity) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        if (blockId == null) return false;
        for (FearSourceDefinition def : fearedBlocks) {
            if (def.matches(blockId, blockState, blockEntity, null)) { return true; }
        }
        return false;
    }

    public boolean isFearedItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return false;
        for (FearSourceDefinition def : fearedItems) {
            if (def.matches(itemId, null, null, stack)) { return true; }
        }
        return false;
    }

    public record MobDefinition(ResourceLocation id, @Nullable String customName, @Nullable CompoundTag nbt) {
        public static MobDefinition fromConfig(Config config) {
            ResourceLocation id = new ResourceLocation((String)config.get("id"));
            String customName = config.getOptional("custom_name").map(String::valueOf).orElse(null);
            CompoundTag nbt = config.getOptional("nbt").map(nbtStr -> {
                try {
                    return TagParser.parseTag((String) nbtStr);
                } catch (Exception e) { throw new IllegalArgumentException("Invalid NBT: " + e.getMessage()); }
            }).orElse(null);
            return new MobDefinition(id, customName, nbt);
        }

        public int getMatchScore(Mob mob, ResourceLocation mobId) {
            if (!this.id.equals(mobId)) return -1;
            
            boolean nameMatch = (this.customName == null) || (mob.hasCustomName() && mob.getName().getString().equals(this.customName));
            boolean nbtMatch = (this.nbt == null) || (NbtUtils.compareNbt(this.nbt, mob.saveWithoutId(new CompoundTag()), true));

            if (!nameMatch || !nbtMatch) {
                return -1;
            }

            int score = 0;
            if (this.customName != null) score += 2;
            if (this.nbt != null) score += 1;
            return score;
        }
    }

    public record FearSourceDefinition(ResourceLocation id, @Nullable String customName, @Nullable Config states, @Nullable CompoundTag nbt) {
        public static FearSourceDefinition fromConfig(Config config) {
            ResourceLocation id = new ResourceLocation((String)config.get("id"));
            String customName = config.getOptional("custom_name").map(String::valueOf).orElse(null);
            Config states = config.getOptional("states").map(o -> (Config) o).orElse(null);
            CompoundTag nbt = config.getOptional("nbt").map(nbtStr -> {
                try {
                    return TagParser.parseTag((String) nbtStr);
                } catch (Exception e) { throw new IllegalArgumentException("Invalid NBT: " + e.getMessage()); }
            }).orElse(null);
            return new FearSourceDefinition(id, customName, states, nbt);
        }
        
        public boolean matches(ResourceLocation targetId, @Nullable BlockState blockState, @Nullable BlockEntity blockEntity, @Nullable ItemStack itemStack) {
            if (!this.id.equals(targetId)) return false;
            if (blockState != null) {
                if (states != null) {
                    for (Config.Entry entry : states.entrySet()) {
                        String key = entry.getKey();
                        if (key == null) { continue; }
                        Property<?> property = blockState.getBlock().getStateDefinition().getProperty(key);
                        if (property == null) return false;
                        Optional<?> value = property.getValue(entry.getValue().toString());
                        if (value.isEmpty() || !blockState.getValue(property).equals(value.get())) { return false; }
                    }
                }
                if (nbt != null) {
                    if (blockEntity == null) return false;
                    CompoundTag blockNbt = blockEntity.saveWithoutMetadata();
                    if (!NbtUtils.compareNbt(nbt, blockNbt, true)) { return false; }
                }
                return true;
            }
            if (itemStack != null) {
                if (this.customName != null && (!itemStack.hasCustomHoverName() || !itemStack.getHoverName().getString().equals(this.customName))) return false;
                if (this.nbt != null) {
                    CompoundTag itemNbt = itemStack.getTag();
                    if (itemNbt == null || !NbtUtils.compareNbt(this.nbt, itemNbt, true)) return false;
                }
                return true;
            }
            return false;
        }
    }
}