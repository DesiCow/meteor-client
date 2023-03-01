/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class BucketAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWater = settings.createGroup("Water");
    private final SettingGroup sgLava = settings.createGroup("Lava");

    // General

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("The maximum distance the entity can be to target it.")
        .min(0.0)
        .defaultValue(5.0)
        .build()
    );

    private final Setting<Boolean> targetBabies = sgGeneral.add(new BoolSetting.Builder()
        .name("target-babies")
        .description("If checked, babies will also be targeted.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> tickInterval = sgGeneral.add(new IntSetting.Builder()
        .name("tick-interval")
        .description("Minimum delay in ticks for targeting entities.")
        .defaultValue(10)
        .sliderRange(1, 20)
        .build()
    );

    // Water

    private final Setting<Boolean> waterEnabled = sgWater.add(new BoolSetting.Builder()
        .name("water")
        .description("Whether or not to enable water-bucket aura.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Object2BooleanMap<EntityType<?>>> waterEntities = sgWater.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to target.")
        .defaultValue(EntityType.ENDERMAN)
        .visible(waterEnabled::get)
        .build()
    );

    private final Setting<Boolean> waterRecollect = sgWater.add(new BoolSetting.Builder()
        .name("recollect")
        .description("Automatically re-collects placed water in empty bucket.")
        .defaultValue(false)
        .visible(waterEnabled::get)
        .build()
    );

    private final Setting<Boolean> waterOnlyOnFire = sgWater.add(new BoolSetting.Builder()
        .name("only-on-fire")
        .description("Only target entities on fire.")
        .defaultValue(false)
        .visible(waterEnabled::get)
        .build()
    );

    // Lava

    private final Setting<Boolean> lavaEnabled = sgLava.add(new BoolSetting.Builder()
        .name("lava")
        .description("Whether or not to enable lava-bucket aura.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Object2BooleanMap<EntityType<?>>> lavaEntities = sgLava.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to target.")
        .defaultValue(
            EntityType.CHICKEN,
            EntityType.COW,
            EntityType.PIG,
            EntityType.PLAYER,
            EntityType.RABBIT,
            EntityType.SHEEP
        )
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<Boolean> lavaRecollect = sgLava.add(new BoolSetting.Builder()
        .name("recollect")
        .description("Automatically re-collects placed lava in empty bucket.")
        .defaultValue(true)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<Boolean> lavaIgnoreOnFire = sgLava.add(new BoolSetting.Builder()
        .name("ignore-on-fire")
        .description("Ignores targeting entities already on fire.")
        .defaultValue(true)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<Boolean> lavaIgnoreSelf = sgLava.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Ignores yourself when placing lava.")
        .defaultValue(true)
        .visible(() -> lavaEnabled.get() && lavaEntities.get().getBoolean(EntityType.PLAYER))
        .build()
    );

    private final Setting<Boolean> lavaIgnoreFriends = sgLava.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Ignores your friends when placing lava.")
        .defaultValue(true)
        .visible(() -> lavaEnabled.get() && lavaEntities.get().getBoolean(EntityType.PLAYER))
        .build()
    );

    private List<BlockPos> lastWaterPos;
    private List<BlockPos> lastLavaPos;

    private int ticks;

    public BucketAura() {
        super(Categories.Combat, "bucket-aura", "Automatically places and collects water/lava buckets.");
    }

    @Override
    public void onDeactivate() {
        lastWaterPos.clear();
        lastLavaPos.clear();
        ticks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Increment timer
        ticks++;

        // Place
        for (Entity entity : mc.world.getEntities()) {
            if(ticks < tickInterval.get()) break;
            if (entity == mc.cameraEntity) continue;
            if(!PlayerUtils.isWithin(entity, distance.get()) || !PlayerUtils.canSeeFeet(entity)) continue;
            if (!targetBabies.get() && entity instanceof LivingEntity living && living.isBaby()) continue;

            if (filterWater(entity) &&
                interact(InvUtils.findInHotbar(Items.WATER_BUCKET), entity.getBlockPos())) lastWaterPos.add(entity.getBlockPos());

            if (filterLava(entity) &&
                interact(InvUtils.findInHotbar(Items.LAVA_BUCKET), entity.getBlockPos())) lastLavaPos.add(entity.getBlockPos());
        }

        // Reset timer
        if (ticks >= tickInterval.get()) ticks = 0;

        // Recollect
        recollect();
    }

    private void recollect() {
        // Recollect Water
        if (waterRecollect.get()) {
            lastWaterPos.forEach(pos -> {
                if (mc.world.getBlockState(pos).getBlock() == Blocks.WATER &&
                    interact(InvUtils.findInHotbar(Items.BUCKET), pos)) lastWaterPos.remove(pos);

            });
        }

        // Recollect Lava
        if (lavaRecollect.get()) {
            lastLavaPos.forEach(pos -> {
                if (mc.world.getBlockState(pos).getBlock() == Blocks.LAVA &&
                    interact(InvUtils.findInHotbar(Items.BUCKET), pos)) lastLavaPos.remove(pos);

            });
        }
    }

    private boolean filterWater(Entity entity) {
        if (!waterEnabled.get()) return false;
        if (!waterEntities.get().getBoolean(entity.getType())) return false;
        if (waterOnlyOnFire.get() && !entity.isOnFire()) return false;
        if (mc.world.getBlockState(entity.getBlockPos()).getBlock() == Blocks.WATER) return false;

        return true;
    }

    private boolean filterLava(Entity entity) {
        if (!lavaEnabled.get()) return false;
        if (!lavaEntities.get().getBoolean(entity.getType())) return false;
        if (lavaIgnoreOnFire.get() && entity.isOnFire()) return false;
        if (lavaIgnoreSelf.get() && entity == mc.player) return false;
        if (lavaIgnoreFriends.get() && entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        if (mc.world.getBlockState(entity.getBlockPos()).getBlock() == Blocks.LAVA) return false;

        return true;
    }

    private boolean interact(FindItemResult itemResult, BlockPos targetPos) {
        if (!InvUtils.swap(itemResult.slot(), true)) return false;

        Rotations.rotate(Rotations.getYaw(Vec3d.ofBottomCenter(targetPos)), Rotations.getPitch(Vec3d.ofBottomCenter(targetPos)), 10,
            () -> mc.interactionManager.interactItem(mc.player, itemResult.getHand()));

        InvUtils.swapBack();
        return true;
    }
}
