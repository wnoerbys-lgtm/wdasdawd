package dev.codex.debrismachine;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.arguments.EntityArgument.getPlayer;

public final class DebrisMachineMod implements ModInitializer {
    private static final Map<UUID, Machine> MACHINES = new HashMap<>();
    private static final int TICKS_PER_METER = 10;
    private static final int MAX_RANGE = 256;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("debris")
                .then(Commands.literal("start")
                    .then(Commands.argument("direction", StringArgumentType.word())
                        .executes(context -> start(context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "direction")))))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource().getPlayerOrException())))
                .then(Commands.literal("status").executes(context -> status(context.getSource().getPlayerOrException())))
        ));
        ServerTickEvents.END_SERVER_TICK.register(DebrisMachineMod::tick);
    }

    private static int start(ServerPlayer player, String rawDirection) {
        Direction direction = switch (rawDirection.toLowerCase()) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> null;
        };
        if (direction == null) {
            player.sendSystemMessage(Component.literal("Use /debris start north|south|east|west"));
            return 0;
        }
        MACHINES.put(player.getUUID(), new Machine(player.getUUID(), player.serverLevel(),
            player.blockPosition(), direction));
        player.sendSystemMessage(Component.literal("Debris miner started facing " + direction.getName() + ". It clears a 3x3 tunnel; use /debris stop to halt."));
        return 1;
    }

    private static int stop(ServerPlayer player) {
        boolean removed = MACHINES.remove(player.getUUID()) != null;
        player.sendSystemMessage(Component.literal(removed ? "Debris miner stopped." : "You do not have a running miner."));
        return removed ? 1 : 0;
    }

    private static int status(ServerPlayer player) {
        Machine machine = MACHINES.get(player.getUUID());
        player.sendSystemMessage(Component.literal(machine == null ? "No miner is running." :
            "Miner at " + machine.front + ", facing " + machine.direction.getName() + ", distance " + machine.distance + " blocks."));
        return machine == null ? 0 : 1;
    }

    private static void tick(MinecraftServer server) {
        Iterator<Machine> iterator = MACHINES.values().iterator();
        while (iterator.hasNext()) {
            Machine machine = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(machine.owner);
            if (owner == null || !owner.isAlive() || owner.serverLevel() != machine.level) {
                iterator.remove();
                continue;
            }
            machine.ticks++;
            collectNearby(machine, owner);
            if (machine.ticks % TICKS_PER_METER != 0) continue;
            if (machine.distance >= MAX_RANGE) {
                owner.sendSystemMessage(Component.literal("Miner reached its safety limit of " + MAX_RANGE + " blocks and stopped."));
                iterator.remove();
                continue;
            }

            int broken = clearLayer(machine, owner);
            collectNearby(machine, owner);
            if (broken == 0) {
                machine.stalled++;
                if (machine.stalled >= 3) {
                    owner.sendSystemMessage(Component.literal("Miner stopped: the 3x3 face could not be cleared (unbreakable blocks or protection)."));
                    iterator.remove();
                }
                continue;
            }
            machine.stalled = 0;
            machine.front = machine.front.relative(machine.direction);
            machine.distance++;
        }
    }

    private static int clearLayer(Machine machine, ServerPlayer owner) {
        int broken = 0;
        Direction right = machine.direction.getClockWise();
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                BlockPos pos = machine.front.offset(right.getStepX() * x, y, right.getStepZ() * x);
                if (!machine.level.hasChunkAt(pos)) continue;
                BlockState state = machine.level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(machine.level, pos) < 0) continue;
                if (machine.level.destroyBlock(pos, true, owner)) broken++;
            }
        }
        return broken;
    }

    private static void collectNearby(Machine machine, ServerPlayer owner) {
        BlockPos center = machine.front;
        for (ItemEntity item : machine.level.getEntitiesOfClass(ItemEntity.class,
            new net.minecraft.world.phys.AABB(center).inflate(5.0))) {
            if (!item.isAlive() || item.hasPickUpDelay()) continue;
            ItemStack remaining = item.getItem().copy();
            owner.getInventory().add(remaining);
            if (!remaining.isEmpty()) {
                if (machine.level.getBlockEntity(machine.chestPos) instanceof Container container) {
                    remaining = insert(container, remaining);
                }
            }
            if (remaining.isEmpty()) item.discard();
            else item.setItem(remaining);
        }
    }

    private static ItemStack insert(Container container, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < container.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (!container.canPlaceItem(i, rest)) continue;
            if (slot.isEmpty()) {
                int moved = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), container.getMaxStackSize()));
                ItemStack placed = rest.copy();
                placed.setCount(moved);
                container.setItem(i, placed);
                rest.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(slot, rest)) {
                int room = Math.min(slot.getMaxStackSize(), container.getMaxStackSize()) - slot.getCount();
                int moved = Math.min(Math.max(0, room), rest.getCount());
                if (moved > 0) {
                    slot.grow(moved);
                    rest.shrink(moved);
                }
            }
        }
        container.setChanged();
        return rest;
    }

    private static final class Machine {
        private final UUID owner;
        private final ServerLevel level;
        private final Direction direction;
        private final BlockPos chestPos;
        private BlockPos front;
        private int ticks;
        private int distance;
        private int stalled;

        private Machine(UUID owner, ServerLevel level, BlockPos start, Direction direction) {
            this.owner = owner;
            this.level = level;
            this.front = start.relative(direction);
            this.direction = direction;
            this.chestPos = start.relative(direction.getOpposite()).below();
        }
    }
}
