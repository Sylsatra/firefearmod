package com.example.firefearmod.manager;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public record FearGroup(
        String groupId,
        double fleeSpeed,
        int searchRadius,
        List<MobDefinition> mobs,
        List<FearSourceDefinition> fearedBlocks,
        List<FearSourceDefinition> fearedItems,
        List<FearedEntityDefinition> fearedEntities
) implements IFearProfile {

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
        List<FearedEntityDefinition> fearedEntities = config.getOptional("feared_entities")
                .map(o -> (List<Config>) o)
                .map(list -> list.stream().map(FearedEntityDefinition::fromConfig).collect(Collectors.toList()))
                .orElse(List.of());
        return new FearGroup(groupId, fleeSpeed, searchRadius, mobs, fearedBlocks, fearedItems, fearedEntities);
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
    
    @Nullable
    public FearSourceDefinition findFearedBlock(BlockState blockState, @Nullable BlockEntity blockEntity) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        if (blockId == null) return null;
        for (FearSourceDefinition def : fearedBlocks) {
            if (def.matches(blockId, blockState, blockEntity, null)) { return def; }
        }
        return null;
    }

    @Nullable
    public FearSourceDefinition findFearedItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return null;
        for (FearSourceDefinition def : fearedItems) {
            if (def.matches(itemId, null, null, stack)) { return def; }
        }
        return null;
    }

    public boolean hasFearedEntities() {
        if (!fearedEntities.isEmpty()) {
            return true;
        }
        for (FearSourceDefinition def : fearedBlocks) {
            if (def.canMatchEntity()) { return true; }
        }
        return false;
    }

    public boolean shouldOverrideHostility(BlockState blockState, @Nullable BlockEntity blockEntity) {
        FearSourceDefinition def = findFearedBlock(blockState, blockEntity);
        return def != null && def.fearOverride();
    }

    public boolean shouldOverrideHostility(ItemStack stack) {
        FearSourceDefinition def = findFearedItem(stack);
        return def != null && def.fearOverride();
    }

    public boolean isFearedEntity(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) return false;
        for (FearedEntityDefinition def : fearedEntities) {
            if (def.matches(entityId, entity)) { return true; }
        }
        for (FearSourceDefinition def : fearedBlocks) {
            if (def.matchesEntity(entityId, entity)) { return true; }
        }
        return false;
    }

    public boolean shouldOverrideHostility(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) return false;
        for (FearedEntityDefinition def : fearedEntities) {
            if (def.matches(entityId, entity)) {
                return def.overrideHostility();
            }
        }
        return false;
    }

    @Override
    public IFearProfile.VisibilityMode getEntityVisibilityMode(Entity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return IFearProfile.VisibilityMode.LOOK_BASED;
        }
        for (FearedEntityDefinition def : fearedEntities) {
            if (def.matches(entityId, entity)) {
                return def.visibilityMode();
            }
        }
        return IFearProfile.VisibilityMode.LOOK_BASED;
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

    public record FearSourceDefinition(ResourceLocation id, boolean isTag, @Nullable String customName, @Nullable Config states, @Nullable CompoundTag nbt, boolean fearOverride) {
        public static FearSourceDefinition fromConfig(Config config) {
            String raw = (String) config.get("id");
            boolean isTag = false;
            if (raw.startsWith("#")) {
                isTag = true;
                raw = raw.substring(1);
            }
            ResourceLocation id = new ResourceLocation(raw);
            String customName = config.getOptional("custom_name").map(String::valueOf).orElse(null);
            Config states = config.getOptional("states").map(o -> (Config) o).orElse(null);
            CompoundTag nbt = config.getOptional("nbt").map(nbtStr -> {
                try {
                    return TagParser.parseTag((String) nbtStr);
                } catch (Exception e) { throw new IllegalArgumentException("Invalid NBT: " + e.getMessage()); }
            }).orElse(null);
            boolean fearOverride = config.getOptional("fear_override").map(o -> (Boolean) o).orElse(false);
            return new FearSourceDefinition(id, isTag, customName, states, nbt, fearOverride);
        }
        
        public boolean matches(ResourceLocation targetId, @Nullable BlockState blockState, @Nullable BlockEntity blockEntity, @Nullable ItemStack itemStack) {
            if (blockState != null) {
                if (this.isTag) {
                    TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, this.id);
                    if (!blockState.is(tagKey)) return false;
                } else {
                    if (!this.id.equals(targetId)) return false;
                }
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
                if (this.isTag) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, this.id);
                    if (!itemStack.is(tagKey)) return false;
                } else {
                    if (!this.id.equals(targetId)) return false;
                }
                if (this.customName != null && (!itemStack.hasCustomHoverName() || !itemStack.getHoverName().getString().equals(this.customName))) return false;
                if (this.nbt != null) {
                    CompoundTag itemNbt = itemStack.getTag();
                    if (itemNbt == null || !NbtUtils.compareNbt(this.nbt, itemNbt, true)) return false;
                }
                return true;
            }
            return false;
        }

        public boolean matchesEntity(ResourceLocation targetId, Entity entity) {
            if (!canMatchEntity()) {
                return false;
            }
            if (this.isTag) {
                TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, this.id);
                if (!entity.getType().is(tagKey)) return false;
            } else {
                if (!this.id.equals(targetId)) return false;
            }
            if (this.customName != null) {
                if (entity instanceof Player player) {
                    String playerName = player.getScoreboardName();
                    String displayName = player.getName().getString();
                    if (!this.customName.equals(playerName) && !this.customName.equals(displayName)) {
                        return false;
                    }
                } else {
                    if (!entity.hasCustomName() || !entity.getCustomName().getString().equals(this.customName)) return false;
                }
            }
            if (this.nbt != null) {
                CompoundTag entityNbt = new CompoundTag();
                entity.saveWithoutId(entityNbt);
                if (!NbtUtils.compareNbt(this.nbt, entityNbt, true)) return false;
            }
            return true;
        }

        public boolean canMatchEntity() {
            if (this.isTag) {
                return true;
            }
            return ForgeRegistries.ENTITY_TYPES.containsKey(this.id);
        }
    }

    public record FearedEntityDefinition(FearSourceDefinition source, boolean overrideHostility, IFearProfile.VisibilityMode visibilityMode) {
        public static FearedEntityDefinition fromConfig(Config config) {
            FearSourceDefinition src = FearSourceDefinition.fromConfig(config);
            boolean override = config.getOptional("fear_override").map(o -> (Boolean) o).orElse(false);
            String modeStr = config.getOptional("visibility_mode").map(String::valueOf).orElse("LOOK_BASED");
            IFearProfile.VisibilityMode mode;
            try {
                mode = IFearProfile.VisibilityMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                mode = IFearProfile.VisibilityMode.LOOK_BASED;
            }
            return new FearedEntityDefinition(src, override, mode);
        }

        public boolean matches(ResourceLocation entityId, Entity entity) {
            return source.matchesEntity(entityId, entity);
        }
    }
}