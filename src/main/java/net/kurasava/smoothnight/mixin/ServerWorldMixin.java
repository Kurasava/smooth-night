package net.kurasava.smoothnight.mixin;

import net.kurasava.smoothnight.SmoothNight;
import net.kurasava.smoothnight.config.ModConfig;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.world.rule.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Final
    @Shadow
    List<ServerPlayerEntity> players;

    @Shadow
    public abstract void setTimeOfDay(long timeOfDay);

    @Shadow
    protected abstract void wakeSleepingPlayers();

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/SleepManager;canSkipNight(I)Z"
            ),
            cancellable = true
    )
    private void onTickTimeSkip(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        int i = world.getGameRules().getValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (this.sleepManager.canSkipNight(i) && this.sleepManager.canResetTime(i, world.getPlayers())) {
            this.smoothNightSkip(world);
            ci.cancel();
        }
    }


    @Unique
    private void smoothNightSkip(ServerWorld world) {
        boolean doDayLightCycle = world.getGameRules().getValue(GameRules.ADVANCE_TIME);

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
                if (world.getGameRules().getValue(GameRules.ADVANCE_WEATHER)) {
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
