package com.puddingkc;

import de.tr7zw.nbtapi.NBTItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BfrUtils {

    private static final Pattern HEX_INLINE = Pattern.compile("(?i)&#([0-9A-F]{6})");

    private final BfrMain plugin;
    private final NamespacedKey creationTimeKey;
    private final NamespacedKey stateKey;
    private final NamespacedKey bundlePauseSinceKey;

    public BfrUtils(BfrMain plugin) {
        this.plugin = plugin;
        this.creationTimeKey = new NamespacedKey(plugin, "creation_time");
        this.stateKey = new NamespacedKey(plugin, "state");
        this.bundlePauseSinceKey = new NamespacedKey(plugin, "bundle_pause_since");
    }

    public void checkInventory(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            pauseFoodInsideProtectedBundles(stack);
            checkItem(stack);
            inventory.setItem(slot, stack);
        }
        if (plugin.mergeStacksEnabled) {
            compactFoodStacks(inventory);
        }
    }

    public void checkItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        Material material = stack.getType();
        Integer maxMinutes = plugin.food.get(material);
        if (maxMinutes == null || maxMinutes <= 0) {
            return;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }

        NBTItem nbt = new NBTItem(stack);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        long creationTime = resolveCreationTime(now, pdc, nbt);
        creationTime = applyStoredBundlePause(now, creationTime, pdc);

        long elapsed = Math.max(0L, now - creationTime);
        long maxMillis = maxMinutes * 60L * 1000L;
        int state = resolveState(elapsed, maxMillis);

        if (plugin.writeStateLore) {
            String rawLore = switch (state) {
                case 2 -> plugin.loreRotten;
                case 1 -> plugin.loreSpoiled;
                default -> plugin.loreFresh;
            };
            applyStateLore(meta, rawLore);
        } else {
            removeStateLore(meta);
        }

        pdc.set(stateKey, PersistentDataType.INTEGER, state);
        stack.setItemMeta(meta);
    }

    private void pauseFoodInsideProtectedBundles(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !plugin.isProtectedBundle(stack.getType())) {
            return;
        }
        pauseFoodInsideProtectedBundles(stack, System.currentTimeMillis(), 0);
    }

    private boolean pauseFoodInsideProtectedBundles(ItemStack stack, long now, int depth) {
        if (stack == null || stack.getType().isAir() || !plugin.isProtectedBundle(stack.getType())) {
            return false;
        }
        if (depth > 4) {
            return false;
        }

        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return false;
        }

        List<ItemStack> original = bundleMeta.getItems();
        if (original == null || original.isEmpty()) {
            return false;
        }

        List<ItemStack> updated = new ArrayList<>(original.size());
        boolean changed = false;

        for (ItemStack content : original) {
            if (content == null || content.getType().isAir()) {
                updated.add(content);
                continue;
            }

            ItemStack copy = content.clone();
            boolean entryChanged = markPausedIfManagedFood(copy, now);
            if (plugin.isProtectedBundle(copy.getType())) {
                entryChanged = pauseFoodInsideProtectedBundles(copy, now, depth + 1) || entryChanged;
            }
            changed = changed || entryChanged;
            updated.add(copy);
        }

        if (!changed) {
            return false;
        }

        bundleMeta.setItems(updated);
        stack.setItemMeta(bundleMeta);
        return true;
    }

    private boolean markPausedIfManagedFood(ItemStack stack, long now) {
        if (!isManagedFood(stack)) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(bundlePauseSinceKey, PersistentDataType.LONG)) {
            return false;
        }

        NBTItem nbt = new NBTItem(stack);
        resolveCreationTime(now, pdc, nbt);
        pdc.set(bundlePauseSinceKey, PersistentDataType.LONG, now);
        stack.setItemMeta(meta);
        return true;
    }

    public int getState(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return -1;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(stateKey, PersistentDataType.INTEGER)) {
                Integer state = pdc.get(stateKey, PersistentDataType.INTEGER);
                if (state != null) {
                    return state;
                }
            }
        }

        NBTItem nbt = new NBTItem(stack);
        if (nbt.hasTag("state")) {
            Integer state = nbt.getInteger("state");
            return state != null ? state : -1;
        }
        return -1;
    }

    private long resolveCreationTime(long now, PersistentDataContainer pdc, NBTItem nbt) {
        if (pdc.has(creationTimeKey, PersistentDataType.LONG)) {
            Long stored = pdc.get(creationTimeKey, PersistentDataType.LONG);
            long resolved = stored != null ? stored : now;
            long normalized = normalizeCreationTime(resolved);
            if (normalized != resolved) {
                pdc.set(creationTimeKey, PersistentDataType.LONG, normalized);
            }
            return normalized;
        }

        long creationTime = now;
        if (nbt.hasTag("creation_time")) {
            Long old = nbt.getLong("creation_time");
            if (old != null) {
                creationTime = old;
            }
        }
        creationTime = normalizeCreationTime(creationTime);
        pdc.set(creationTimeKey, PersistentDataType.LONG, creationTime);
        return creationTime;
    }

    private long applyStoredBundlePause(long now, long creationTime, PersistentDataContainer pdc) {
        if (!pdc.has(bundlePauseSinceKey, PersistentDataType.LONG)) {
            return creationTime;
        }

        Long pausedSince = pdc.get(bundlePauseSinceKey, PersistentDataType.LONG);
        pdc.remove(bundlePauseSinceKey);
        if (pausedSince == null) {
            return creationTime;
        }

        long anchoredSince = Math.min(now, pausedSince);
        long pausedMillis = Math.max(0L, now - anchoredSince);
        if (pausedMillis <= 0L) {
            return creationTime;
        }

        long adjusted = normalizeCreationTime(creationTime + pausedMillis);
        pdc.set(creationTimeKey, PersistentDataType.LONG, adjusted);
        return adjusted;
    }

    private long normalizeCreationTime(long timestamp) {
        if (plugin.creationTimeBucketSeconds <= 0) {
            return timestamp;
        }
        long bucketMillis = plugin.creationTimeBucketSeconds * 1000L;
        if (bucketMillis <= 1L) {
            return timestamp;
        }
        return (timestamp / bucketMillis) * bucketMillis;
    }

    private void compactFoodStacks(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean anyChanged = false;
        long now = System.currentTimeMillis();

        for (int i = 0; i < contents.length; i++) {
            ItemStack base = contents[i];
            if (!isManagedFood(base)) {
                continue;
            }

            int baseMax = Math.max(1, Math.min(base.getMaxStackSize(), base.getType().getMaxStackSize()));
            if (base.getAmount() >= baseMax) {
                continue;
            }

            int baseState = getState(base);
            if (baseState < 0) {
                checkItem(base);
                baseState = getState(base);
            }
            if (baseState < 0) {
                continue;
            }

            long oldestTime = getCreationTime(base, now);
            boolean mergedThisSlot = false;

            for (int j = i + 1; j < contents.length && base.getAmount() < baseMax; j++) {
                ItemStack other = contents[j];
                if (!isManagedFood(other)) {
                    continue;
                }

                int otherState = getState(other);
                if (otherState != baseState) {
                    continue;
                }
                if (!canMergeFoodStacks(base, other)) {
                    continue;
                }

                int move = Math.min(baseMax - base.getAmount(), other.getAmount());
                if (move <= 0) {
                    continue;
                }

                oldestTime = Math.min(oldestTime, getCreationTime(other, now));
                base.setAmount(base.getAmount() + move);
                other.setAmount(other.getAmount() - move);

                if (other.getAmount() <= 0) {
                    contents[j] = null;
                } else {
                    contents[j] = other;
                }

                mergedThisSlot = true;
                anyChanged = true;
            }

            if (mergedThisSlot) {
                setCreationTime(base, oldestTime);
                checkItem(base);
                contents[i] = base;
            }
        }

        if (!anyChanged) {
            return;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            inventory.setItem(slot, contents[slot]);
        }
    }

    private boolean canMergeFoodStacks(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getType() != second.getType()) {
            return false;
        }
        ItemStack normalizedFirst = normalizeForMergeComparison(first);
        ItemStack normalizedSecond = normalizeForMergeComparison(second);
        return normalizedFirst.isSimilar(normalizedSecond);
    }

    private ItemStack normalizeForMergeComparison(ItemStack source) {
        ItemStack clone = source.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            removeStateLore(meta);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.remove(creationTimeKey);
            pdc.remove(stateKey);
            pdc.remove(bundlePauseSinceKey);
            clone.setItemMeta(meta);
        }

        NBTItem nbt = new NBTItem(clone);
        boolean changed = false;
        if (nbt.hasTag("creation_time")) {
            nbt.removeKey("creation_time");
            changed = true;
        }
        if (nbt.hasTag("state")) {
            nbt.removeKey("state");
            changed = true;
        }
        return changed ? nbt.getItem() : clone;
    }

    private boolean isManagedFood(ItemStack stack) {
        return stack != null
                && !stack.getType().isAir()
                && plugin.food.containsKey(stack.getType());
    }

    private long getCreationTime(ItemStack stack, long fallbackNow) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(creationTimeKey, PersistentDataType.LONG)) {
                Long stored = pdc.get(creationTimeKey, PersistentDataType.LONG);
                if (stored != null) {
                    return normalizeCreationTime(stored);
                }
            }
        }

        NBTItem nbt = new NBTItem(stack);
        if (nbt.hasTag("creation_time")) {
            Long old = nbt.getLong("creation_time");
            if (old != null) {
                return normalizeCreationTime(old);
            }
        }
        return normalizeCreationTime(fallbackNow);
    }

    private void setCreationTime(ItemStack stack, long creationTime) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(creationTimeKey, PersistentDataType.LONG, normalizeCreationTime(creationTime));
        stack.setItemMeta(meta);
    }

    private static int resolveState(long elapsed, long maxMillis) {
        if (elapsed >= maxMillis) {
            return 2;
        }
        if (elapsed >= maxMillis / 2L) {
            return 1;
        }
        return 0;
    }

    private void applyStateLore(ItemMeta meta, String rawLore) {
        String rendered = colorize(rawLore);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        List<String> knownRendered = knownRenderedStateLines();

        Set<String> knownNormalized = new LinkedHashSet<>();
        for (String line : knownRendered) {
            String normalized = normalizeForMatch(line);
            if (!normalized.isEmpty()) {
                knownNormalized.add(normalized);
            }
        }

        String statePrefix = resolveStatePrefix(knownNormalized);
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            String normalized = normalizeForMatch(line);
            if (!isStateLine(normalized, knownNormalized, statePrefix)) {
                continue;
            }
            if (!replaced) {
                lore.set(i, rendered);
                replaced = true;
            } else {
                lore.remove(i);
                i--;
            }
        }

        if (!replaced) {
            lore.add(rendered);
        }
        meta.setLore(lore.isEmpty() ? null : lore);
    }

    private void removeStateLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return;
        }

        List<String> lore = new ArrayList<>(meta.getLore());
        List<String> knownRendered = knownRenderedStateLines();

        Set<String> knownNormalized = new LinkedHashSet<>();
        for (String line : knownRendered) {
            String normalized = normalizeForMatch(line);
            if (!normalized.isEmpty()) {
                knownNormalized.add(normalized);
            }
        }
        String statePrefix = resolveStatePrefix(knownNormalized);

        boolean changed = false;
        for (int i = 0; i < lore.size(); i++) {
            String normalized = normalizeForMatch(lore.get(i));
            if (!isStateLine(normalized, knownNormalized, statePrefix)) {
                continue;
            }
            lore.remove(i);
            i--;
            changed = true;
        }

        if (changed) {
            meta.setLore(lore.isEmpty() ? null : lore);
        }
    }

    private List<String> knownRenderedStateLines() {
        List<String> list = new ArrayList<>(3);
        addIfNotNull(list, plugin.loreFresh);
        addIfNotNull(list, plugin.loreSpoiled);
        addIfNotNull(list, plugin.loreRotten);
        return list;
    }

    private static void addIfNotNull(List<String> list, String value) {
        if (value != null) {
            list.add(colorize(value));
        }
    }

    private static boolean isStateLine(String normalized, Set<String> knownNormalized, String prefix) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if (knownNormalized.contains(normalized)) {
            return true;
        }
        if (prefix != null && !prefix.isBlank() && normalized.startsWith(prefix)) {
            return true;
        }
        return normalized.startsWith("food state:")
                || normalized.startsWith("food state：")
                || normalized.startsWith("食物状态:")
                || normalized.startsWith("食物状态：");
    }

    private static String resolveStatePrefix(Set<String> knownNormalized) {
        for (String line : knownNormalized) {
            int idx = Math.max(line.indexOf(':'), line.indexOf('：'));
            if (idx > 0) {
                return line.substring(0, idx + 1);
            }
        }
        return "";
    }

    private static String colorize(String input) {
        if (input == null) {
            return null;
        }
        String expanded = expandInlineHex(input);
        return ChatColor.translateAlternateColorCodes('&', expanded);
    }

    private static String expandInlineHex(String input) {
        Matcher matcher = HEX_INLINE.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toUpperCase(Locale.ROOT);
            String replacement = "&x"
                    + "&" + hex.charAt(0)
                    + "&" + hex.charAt(1)
                    + "&" + hex.charAt(2)
                    + "&" + hex.charAt(3)
                    + "&" + hex.charAt(4)
                    + "&" + hex.charAt(5);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(value);
        if (stripped == null) {
            stripped = value;
        }
        return stripped.trim().toLowerCase(Locale.ROOT);
    }
}
