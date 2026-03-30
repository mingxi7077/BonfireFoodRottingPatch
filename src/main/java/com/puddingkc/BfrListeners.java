package com.puddingkc;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BfrListeners implements Listener {

    private final BfrMain plugin;
    private final BfrUtils utils;
    private final Random random = new Random();

    public BfrListeners(BfrMain plugin, BfrUtils utils) {
        this.plugin = plugin;
        this.utils = utils;
    }

    private static boolean isBypassConsumeEffect(Player player) {
        return player.hasPermission("bonfire.food.bypass");
    }

    private boolean shouldSkipTracking(Player player) {
        return !plugin.shouldTrackPlayer(player);
    }

    private void schedulePlayerCheck(Player player, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            utils.checkInventory(player.getInventory());
            player.updateInventory();
        }, delayTicks);
    }

    private void scheduleInventoryCheck(Inventory inventory, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> utils.checkInventory(inventory), delayTicks);
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (isBypassConsumeEffect(player) || shouldSkipTracking(player)) {
            return;
        }

        int state = utils.getState(event.getItem());
        if (state == 1) {
            if (random.nextDouble() <= 0.4D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 1));
            }
            return;
        }

        if (state > 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
            if (random.nextDouble() <= 0.5D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 3));
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 3));
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || shouldSkipTracking(player)) {
            return;
        }
        Inventory opened = event.getInventory();
        if (opened != null && opened != player.getInventory() && opened.getHolder() instanceof InventoryHolder) {
            scheduleInventoryCheck(opened, 1L);
        }
        schedulePlayerCheck(player, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || shouldSkipTracking(player)) {
            return;
        }
        Inventory closed = event.getInventory();
        if (closed != null && closed != player.getInventory() && closed.getHolder() instanceof InventoryHolder) {
            scheduleInventoryCheck(closed, 1L);
        }
        schedulePlayerCheck(player, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (shouldSkipTracking(player)) {
            return;
        }
        if (event.getCurrentItem() != null) {
            utils.checkItem(event.getCurrentItem());
        }
        if (event.getCursor() != null) {
            utils.checkItem(event.getCursor());
        }
        schedulePlayerCheck(player, 1L);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (shouldSkipTracking(player)) {
            return;
        }
        schedulePlayerCheck(player, 1L);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (shouldSkipTracking(player)) {
            return;
        }
        schedulePlayerCheck(player, 1L);
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (shouldSkipTracking(player)) {
            return;
        }
        schedulePlayerCheck(player, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (shouldSkipTracking(player)) {
            return;
        }
        schedulePlayerCheck(player, 20L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Material type = clicked.getType();

        if (clicked.getState() instanceof Chest chest) {
            scheduleInventoryCheck(chest.getInventory(), 1L);
            return;
        }
        if (clicked.getState() instanceof Barrel barrel) {
            scheduleInventoryCheck(barrel.getInventory(), 1L);
            return;
        }
        if (clicked.getState() instanceof ShulkerBox shulkerBox) {
            scheduleInventoryCheck(shulkerBox.getInventory(), 1L);
            return;
        }
        if (type == Material.ENDER_CHEST && !shouldSkipTracking(event.getPlayer())) {
            scheduleInventoryCheck(event.getPlayer().getEnderChest(), 1L);
        }
    }
}
