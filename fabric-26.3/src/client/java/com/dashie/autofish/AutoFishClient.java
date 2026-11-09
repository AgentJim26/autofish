package com.dashie.autofish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Cast once manually -> auto reel on bite -> auto recast.
 * Cancel any time by switching off the rod or reeling early (right-click before a bite).
 */
public class AutoFishClient implements ClientModInitializer {
    private boolean autoFishing = false;
    private boolean recastScheduled = false;
    private boolean justAutoReeled = false;
    private int recastTimer = 0;
    private int actionCooldown = 0;

    private EntityDataAccessor<Boolean> bitingKey = null;
    private boolean keyLookupFailed = false;
    private double prevHookY = Double.NaN;

    private static final int RECAST_DELAY_TICKS = 20; // 1s after reel before recast
    private static final int ACTION_COOLDOWN_TICKS = 6; // let the hook field sync

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;
        LocalPlayer player = client.player;

        if (actionCooldown > 0) actionCooldown--;

        boolean holdingRod = isHoldingRod(player);
        FishingHook hook = player.fishing;

        if (!autoFishing) {
            // Manual cast detected: hook out and we're holding a rod.
            if (hook != null && holdingRod) {
                autoFishing = true;
                recastScheduled = false;
                justAutoReeled = false;
                player.sendOverlayMessage(Component.literal("§b[AutoFish] §aON — reel early or switch item to stop"));
            }
            return;
        }

        // --- auto mode on ---
        if (!holdingRod) {
            stop(player, "§b[AutoFish] §cOFF (rod put away)");
            return;
        }

        if (hook != null) {
            // Hook out. Reel the moment the server flags a bite.
            if (actionCooldown <= 0 && isBite(hook)) {
                useRod(client, player);
                justAutoReeled = true;
                recastScheduled = true;
                recastTimer = RECAST_DELAY_TICKS;
                actionCooldown = ACTION_COOLDOWN_TICKS;
            }
            return;
        }

        // hook == null here (gone)
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
     * The bite flag (DATA_BITING) is synced to the client but the
     * accessor is private. Find it once via reflection by picking the
     * static EntityDataAccessor field whose current value is a Boolean.
     * Falls back to hook-dip motion detection if reflection fails.
     */
    @SuppressWarnings("unchecked")
    private boolean isBite(FishingHook hook) {
        double y = hook.getY();
        boolean dipped = !Double.isNaN(prevHookY) && (prevHookY - y) > 0.2;
        prevHookY = y;

        if (!keyLookupFailed) {
            try {
                if (bitingKey == null) {
                    SynchedEntityData data = hook.getEntityData();
                    for (Field f : FishingHook.class.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())
                                && EntityDataAccessor.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            EntityDataAccessor<?> key = (EntityDataAccessor<?>) f.get(null);
                            Object val;
                            try {
                                val = data.get(key);
                            } catch (Exception e) {
                                continue;
                            }
                            if (val instanceof Boolean) {
                                bitingKey = (EntityDataAccessor<Boolean>) key;
                                break;
                            }
                        }
                    }
                    if (bitingKey == null) {
                        keyLookupFailed = true;
                    }
                }
                if (bitingKey != null) {
                    prevHookY = hook.getY();
                    return Boolean.TRUE.equals(hook.getEntityData().get(bitingKey));
                }
            } catch (Exception e) {
                keyLookupFailed = true;
            }
        }
        // Fallback: the server yanks the hook down hard on a bite.
        return dipped;
    }

    private boolean isHoldingRod(LocalPlayer player) {
        return player.getMainHandItem().is(Items.FISHING_ROD)
                || player.getOffhandItem().is(Items.FISHING_ROD);
    }

    private void useRod(Minecraft client, LocalPlayer player) {
        InteractionHand hand = player.getMainHandItem().is(Items.FISHING_ROD)
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        client.gameMode.useItem(player, hand);
        // Cosmetic swing so it looks like a normal right-click.
        player.swing(hand, SwingAnimation.DEFAULT, false);
    }

    private void stop(LocalPlayer player, String msg) {
        autoFishing = false;
        recastScheduled = false;
        justAutoReeled = false;
        recastTimer = 0;
        player.sendOverlayMessage(Component.literal(msg));
    }
}
