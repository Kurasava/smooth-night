package net.kurasava.smoothnight.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.kurasava.smoothnight.SmoothNight;
import net.kurasava.smoothnight.config.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.EntityList;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.tick.TickManager;
import net.minecraft.world.tick.WorldTickScheduler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(value = ServerWorld.class, priority = 999)
public abstract class ServerWorldMixin {

    @Unique
    private SmoothNight INSTANCE = SmoothNight.INSTANCE;

    @Final
    @Shadow
    private SleepManager sleepManager;

    @Shadow
    private boolean inBlockTick;

    @Final
    @Shadow
    List<ServerPlayerEntity> players;

    @Final
    @Shadow
    private WorldTickScheduler<Block> blockTickScheduler;

    @Final
    @Shadow
    private WorldTickScheduler<Fluid> fluidTickScheduler;

    @Final
    @Shadow
    protected RaidManager raidManager;

    @Shadow
    private int idleTimeout;

    @Shadow
    private EnderDragonFight enderDragonFight;

    @Final
    @Shadow
    EntityList entityList;

    @Final
    @Shadow
    private ServerChunkManager chunkManager;

    @Final
    @Shadow
    private ServerEntityManager<Entity> entityManager;

    @Shadow
    public abstract void setTimeOfDay(long timeOfDay);

    @Shadow
    public abstract TickManager getTickManager();

    @Shadow
    protected abstract void wakeSleepingPlayers();

    @Shadow
    protected abstract void tickWeather();

    @Shadow
    protected abstract void tickTime();

    @Shadow
    protected abstract void tickBlock(BlockPos pos, Block block);

    @Shadow
    protected abstract void tickFluid(BlockPos pos, Fluid fluid);

    @Shadow
    public abstract ServerChunkManager getChunkManager();

    @Shadow
    protected abstract void processSyncedBlockEvents();

    @Shadow
    public abstract LongSet getForcedChunks();

    @Shadow
    public abstract void resetIdleTimeout();

    @Shadow
    public abstract void tickEntity(Entity entity);

    @Overwrite
    public void tick(BooleanSupplier shouldKeepTicking) {
        ServerWorld world = (ServerWorld) (Object) this;
        Profiler profiler = Profilers.get();
        this.inBlockTick = true;
        TickManager tickManager = this.getTickManager();
        boolean bl = tickManager.shouldTick();
        if (bl) {
            profiler.push("world border");
            world.getWorldBorder().tick();
            profiler.swap("weather");
            this.tickWeather();
            profiler.pop();
        }

        int i = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (this.sleepManager.canSkipNight(i) && this.sleepManager.canResetTime(i, this.players)) {
            this.smoothNightSkip(world);
        }

        world.calculateAmbientDarkness();
        if (bl) {
            this.tickTime();
        }

        profiler.push("tickPending");
        if (!world.isDebugWorld() && bl) {
            long l = world.getTime();
            profiler.push("blockTicks");
            this.blockTickScheduler.tick(l, 65536, this::tickBlock);
            profiler.swap("fluidTicks");
            this.fluidTickScheduler.tick(l, 65536, this::tickFluid);
            profiler.pop();
        }

        profiler.swap("raid");
        if (bl) {
            this.raidManager.tick();
        }

        profiler.swap("chunkSource");
        this.getChunkManager().tick(shouldKeepTicking, true);
        profiler.swap("blockEvents");
        if (bl) {
            this.processSyncedBlockEvents();
        }

        this.inBlockTick = false;
        profiler.pop();
        boolean bl2 = !this.players.isEmpty() || !this.getForcedChunks().isEmpty();
        if (bl2) {
            this.resetIdleTimeout();
        }

        if (bl2 || this.idleTimeout++ < 300) {
            profiler.push("entities");
            if (this.enderDragonFight != null && bl) {
                profiler.push("dragonFight");
                this.enderDragonFight.tick();
                profiler.pop();
            }

            this.entityList.forEach((entity) -> {
                if (!entity.isRemoved()) {
                    if (!tickManager.shouldSkipTick(entity)) {
                        profiler.push("checkDespawn");
                        entity.checkDespawn();
                        profiler.pop();
                        if (entity instanceof ServerPlayerEntity || this.chunkManager.chunkLoadingManager.getTicketManager().shouldTickEntities(entity.getChunkPos().toLong())) {
                            Entity entity2 = entity.getVehicle();
                            if (entity2 != null) {
                                if (!entity2.isRemoved() && entity2.hasPassenger(entity)) {
                                    return;
                                }

                                entity.stopRiding();
                            }

                            profiler.push("tick");
                            world.tickEntity(this::tickEntity, entity);
                            profiler.pop();
                        }
                    }
                }
            });
            profiler.pop();
            ((WorldInvoker) world).tickBlockEntities();
        }

        profiler.push("entityManagement");
        this.entityManager.tick();
        profiler.pop();
    }


    @Unique
    private void smoothNightSkip(ServerWorld world) {
        boolean doDayLightCycle = world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);

        if (doDayLightCycle) {
            long currentTime = world.getTimeOfDay();
            long l = currentTime + 24000L;
            long targetTime = l - l % 24000L;
            long maxStep = ModConfig.maxStep;
            long step = (long) (maxStep * INSTANCE.config.modifier);
            if (targetTime - currentTime > step) {
                this.setTimeOfDay(currentTime + step);
                this.sendTimeUpdatePacket(world.getTime(), currentTime);
            } else {
                this.setTimeOfDay(targetTime);
                this.wakeSleepingPlayers();
                if (world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
                    if ((world.isRaining() && INSTANCE.config.doSkipWeather) || world.isThundering()) {
                        world.resetWeather();
                    }
                }
            }
        }
    }

    @Unique
    private void sendTimeUpdatePacket(long time, long timeOfDay) {
        for (ServerPlayerEntity player : this.players) {
            player.networkHandler.sendPacket(new WorldTimeUpdateS2CPacket(time, timeOfDay, true));
        }
    }
}

@Mixin(World.class)
interface WorldInvoker {
    @Invoker("tickBlockEntities")
    void tickBlockEntities();
}
