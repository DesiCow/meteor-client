/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.commands.commands;

import com.google.common.collect.Streams;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.systems.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.util.stream.Stream;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class VClipCommand extends Command {
    public VClipCommand() {
        super("vclip", "Lets you clip through blocks vertically.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("blocks", DoubleArgumentType.doubleArg()).executes(context -> {

            double blocks = context.getArgument("blocks", Double.class);

            // Implementation of "PaperClip" aka "TPX" aka "VaultClip" into vclip
            // Allows you to teleport up to 200 blocks in one go (as you can send 20 move packets per tick)
            // Paper allows you to teleport 10 blocks for each move packet you send in that tick
            // Video explanation by LiveOverflow: https://www.youtube.com/watch?v=3HSnDsfkJT8
            int packetsRequired = (int) Math.ceil(blocks / 10);
            if (mc.player.hasVehicle()) {
                // Vehicle version
                // For each 10 blocks, send a vehicle move packet with no delta
                for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                    mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(mc.player.getVehicle()));
                }
                // Now send the final vehicle move packet
                mc.player.getVehicle().setPosition(mc.player.getVehicle().getX(), mc.player.getVehicle().getY() + blocks, mc.player.getVehicle().getZ());
                mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(mc.player.getVehicle()));
            } else {
                // No vehicle version
                // For each 10 blocks, send a player move packet with no delta
                for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                }
                // Now send the final player move packet
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + blocks, mc.player.getZ(), true));
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + blocks, mc.player.getZ());
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("up").executes(context -> {
            Box adjustedBox = mc.player.getBoundingBox().stretch(0, 200, 0);

            Stream<Box> boxes = Streams.stream(mc.world.getBlockCollisions(mc.player, adjustedBox))
                .map(VoxelShape::getBoundingBox);

            boxes.filter(box -> mc.world.getStatesInBox(box.offset(0, 1, 0).stretch(0, 1, 0))
                // Find landing pos
                .noneMatch(state -> state.getBlock().collidable))
                .mapToDouble(box -> box.minY).min()
                .ifPresent(y -> teleport(new BlockPos(mc.player.getX(), y, mc.player.getZ())));

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("down").executes(context -> {
            Box adjustedBox = mc.player.getBoundingBox().offset(0, -mc.player.getHeight(), 0)
                .stretch(0, Math.max(-200, mc.world.getBottomY()), 0);

            Stream<Box> collisions = Streams.stream(mc.world.getBlockCollisions(mc.player, adjustedBox))
                .map(VoxelShape::getBoundingBox);

            collisions.filter(box -> mc.world.getStatesInBox(box.offset(0, 1, 0).stretch(0, 1, 0))
                // Find landing pos
                .noneMatch(state -> state.getBlock().collidable))
                .mapToDouble(box -> box.minY).max()
                .ifPresent(y -> teleport(new BlockPos(mc.player.getX(), y, mc.player.getZ())));

            return SINGLE_SUCCESS;
        }));
    }

    // Implementation of "PaperClip" aka "TPX" aka "VaultClip" into vclip
    // Allows you to teleport up to 200 blocks in one go (as you can send 20 move packets per tick)
    // Paper allows you to teleport 10 blocks for each move packet you send in that tick
    // Video explanation by LiveOverflow: https://www.youtube.com/watch?v=3HSnDsfkJT8
    private void teleport(BlockPos pos) {
        int packetsRequired = pos.subtract(mc.player.getBlockPos()).getY() / 10;
        if (mc.player.hasVehicle()) {
            // Vehicle version
            // For each 10 blocks, send a vehicle move packet with no delta
            for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(mc.player.getVehicle()));
            }
            // Now send the final vehicle move packet
            mc.player.getVehicle().setPosition(pos.getX(), pos.getY(), pos.getZ());
            mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(mc.player.getVehicle()));
        } else {
            // No vehicle version
            // For each 10 blocks, send a player move packet with no delta
            for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            }
            // Now send the final player move packet
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.getX(), pos.getY(), pos.getZ(),true));
            mc.player.setPosition(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
