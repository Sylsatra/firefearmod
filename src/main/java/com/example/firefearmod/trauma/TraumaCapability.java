package com.example.firefearmod.trauma;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = "firefearmod", bus = Mod.EventBusSubscriber.Bus.MOD)
public class TraumaCapability {
    public static final ResourceLocation KEY = new ResourceLocation("firefearmod", "trauma");

    public static final Capability<ITraumaData> TRAUMA_CAP = CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ITraumaData.class);
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final TraumaData backend = new TraumaData();
        private final LazyOptional<ITraumaData> optional = LazyOptional.of(() -> backend);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == TRAUMA_CAP ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return backend.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            backend.deserializeNBT(nbt);
        }
    }

    public static LazyOptional<ITraumaData> get(Mob mob) {
        return mob.getCapability(TRAUMA_CAP);
    }
}
