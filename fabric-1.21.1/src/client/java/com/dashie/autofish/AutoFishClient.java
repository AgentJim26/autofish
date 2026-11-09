package com.dashie.autofish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Cast once manually -> auto reel on bite -> auto recast.
 * Cancel by switching off the rod or reeling early (right-click before a bite).
 */
public class AutoFishClient implements ClientModInitializer {
    private boolean autoFishing = false;
    private boolean recastScheduled = false;
    private boolean justAutoReeled = false;
    private int recastTimer = 0;
    private int actionCooldown = 0;

    private TrackedData<Boolean> caughtFishKey = null;
    private boolean keyLookupFailed = false;
    private double prevHookY = Double.NaN;

    private static final int RECAST_DELAY_TICKS = 20; // 1s after reel before recast
    private static final int ACTION_COOLDOWN_TICKS = 6; // let fishHook field sync

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick(client));
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        ClientPlayerEntity player = client.player;

        if (actionCooldown > 0) actionCooldown--;

        boolean holdingRod = isHoldingRod(player);
        var hook = player.fishHook;

        if (!autoFishing) {
            // Manual cast detected: bobber in water and we're holding a rod.
            if (hook != null && holdingRod) {
                autoFishing = true;
                recastScheduled = false;
                justAutoReeled = false;
                player.sendMessage(Text.literal("§b[AutoFish] §aON — reel early or switch item to stop"), true);
            }
            return;
        }

        // --- auto mode on ---
        if (!holdingRod) {
            stop(player, "§b[AutoFish] §cOFF (rod put away)");
            return;
        }

        if (hook != null) {
            // Bobber out. Reel the moment the server flags a bite.
            if (actionCooldown <= 0 && isBite(hook)) {
                useRod(client, player);
                justAutoReeled = true;
                recastScheduled = true;
                recastTimer = RECAST_DELAY_TICKS;
                actionCooldown = ACTION_COOLDOWN_TICKS;
            }
            return;
        }

        // hook == null here (bobber gone)
        prevHookY = Double.NaN;
        if (justAutoReeled) {
            // This null is the result of our own reel; keep the pending recast.
            justAutoReeled = false;
            return;
        }

        if (recastScheduled) {
            if (actionCooldown > 0) return;
            if (recastTimer > 0) {
                recastTimer--;
                return;
            }
            // Time to cast again.
            if (isHoldingRod(player)) {
                useRod(client, player);
                recastScheduled = false;
                actionCooldown = ACTION_COOLDOWN_TICKS;
            } else {
                stop(player, "§b[AutoFish] §cOFF (rod put away)");
            }
            return;
        }

        // Hook vanished with no recast pending and it wasn't our reel:
        // player reeled early / bobber despawned / rod broke -> cancel.
        stop(player, "§b[AutoFish] §cOFF (cancelled)");
    }

    /**
     * 1.21.1 Yarn has no public bite getter, and the CAUGHT_FISH tracked-data
     * key is private. Find it once via reflection by picking the static
     * TrackedData field whose current value is a Boolean, then read it.
     * Falls back to bobber-dip detection if reflection fails.
     */
    @SuppressWarnings("unchecked")
    private boolean isBite(FishingBobberEntity hook) {
        double y = hook.getY();
        boolean dipped = !Double.isNaN(prevHookY) && (prevHookY - y) > 0.2;
        prevHookY = y;

        if (!keyLookupFailed) {
            try {
                if (caughtFishKey == null) {
                    for (Field f : FishingBobberEntity.class.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())
                                && TrackedData.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            TrackedData<?> key = (TrackedData<?>) f.get(null);
                            Object val;
                            try {
                                val = hook.getDataTracker().get(key);
                            } catch (Exception e) {
                                continue;
                            }
                            if (val instanceof Boolean) {
                                caughtFishKey = (TrackedData<Boolean>) key;
                                break;
                            }
                        }
                    }
                    if (caughtFishKey == null) {
                        keyLookupFailed = true;
                    }
                }
                if (caughtFishKey != null) {
                    prevHookY = hook.getY();
                    return Boolean.TRUE.equals(hook.getDataTracker().get(caughtFishKey));
                }
            } catch (Exception e) {
                keyLookupFailed = true;
            }
        }
        // Fallback: the server yanks the bobber down hard on a bite.
        return dipped;
    }

    private boolean isHoldingRod(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.FISHING_ROD)
                || player.getOffHandStack().isOf(Items.FISHING_ROD);
    }

    private void useRod(MinecraftClient client, ClientPlayerEntity player) {
        Hand hand = player.getMainHandStack().isOf(Items.FISHING_ROD) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        client.interactionManager.interactItem(player, hand);
        // Swing so it looks/behaves like a normal right-click.
        player.swingHand(hand);
    }

    private void stop(ClientPlayerEntity player, String msg) {
        autoFishing = false;
        recastScheduled = false;
        justAutoReeled = false;
        recastTimer = 0;
        player.sendMessage(Text.literal(msg), true);
    }
}
