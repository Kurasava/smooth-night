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
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.EntityList;
import net.minecraft.world.GameRules;
import net.minecraft.world.tick.TickManager;
import net.minecraft.world.tick.WorldTickScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(value = ServerWorld.class, priority = 999)
public abstract class ServerWorldMixin {

    private SmoothNight INSTANCE = SmoothNight.INSTANCE;

    @Shadow
    public abstract void setTimeOfDay(long timeOfDay);

    @Shadow
    protected SleepManager sleepManager;

    @Shadow
    protected boolean inBlockTick;

    @Shadow
    protected List<ServerPlayerEntity> players;

    @Shadow
    protected abstract TickManager getTickManager();

    @Shadow
    protected abstract void wakeSleepingPlayers();

    @Shadow
    protected abstract void tickWeather();

    @Shadow
    protected abstract void tickTime();

    @Shadow
    public abstract void tickBlock(BlockPos pos, Block block);

    @Shadow
    public abstract void tickFluid(BlockPos pos, Fluid fluid);

    @Shadow
    protected WorldTickScheduler<Block> blockTickScheduler;

    @Shadow
    protected WorldTickScheduler<Fluid> fluidTickScheduler;

    @Shadow
    protected RaidManager raidManager;

    @Shadow
    protected abstract ServerChunkManager getChunkManager();

    @Shadow
    protected abstract void processSyncedBlockEvents();

    @Shadow
    protected abstract LongSet getForcedChunks();

    @Shadow
    protected abstract void resetIdleTimeout();

    @Shadow
    protected int idleTimeout;

    @Shadow
    protected EnderDragonFight enderDragonFight;

    @Shadow
    protected EntityList entityList;

    @Shadow
    protected abstract boolean shouldCancelSpawn(Entity entity);

    @Shadow
    protected ServerChunkManager chunkManager;

    @Shadow
    protected abstract void tickEntity(Entity entity);

    @Shadow
    protected ServerEntityManager<Entity> entityManager;

    @Overwrite
    public void tick(BooleanSupplier shouldKeepTicking) {
        ServerWorld world = (ServerWorld) (Object) this;
        Profiler profiler = world.getProfiler();
        this.inBlockTick = true;
        TickManager tickManager = this.getTickManager();
        boolean bl = tickManager.shouldTick();
        if (bl) {
            profiler.push("world border");
            world.getWorldBorder().tick();
            profiler.swap("weather");
            this.tickWeather();
        }

        int i = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (this.sleepManager.canSkipNight(i) && this.sleepManager.canResetTime(i, this.players)) {
            this.smoothNightSkip(world);
        }

        world.calculateAmbientDarkness();
        if (bl) {
            this.tickTime();
        }

        profiler.swap("tickPending");
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
                    if (this.shouldCancelSpawn(entity)) {
                        entity.discard();
                    } else if (!tickManager.shouldSkipTick(entity)) {
                        profiler.push("checkDespawn");
                        entity.checkDespawn();
                        profiler.pop();
                        if (this.chunkManager.chunkLoadingManager.getTicketManager().shouldTickEntities(entity.getChunkPos().toLong())) {
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
            WorldMixin world1 = (WorldMixin) (Object) this;
            world1.tickBlockEntities();
        }

        profiler.push("entityManagement");
        this.entityManager.tick();
        profiler.pop();
    }

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

    private void sendTimeUpdatePacket(long time, long timeOfDay) {
        for (ServerPlayerEntity player : this.players) {
            player.networkHandler.sendPacket(new WorldTimeUpdateS2CPacket(time, timeOfDay, true));
        }
    }
}