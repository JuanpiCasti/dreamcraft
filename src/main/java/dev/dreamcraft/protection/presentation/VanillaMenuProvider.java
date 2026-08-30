package dev.dreamcraft.protection.presentation;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Vanilla Bukkit inventory implementation of {@link MenuProvider}.
 *
 * <p>Renders {@link MenuDefinition} as a standard Bukkit inventory using Paper 1.21's
 * Adventure-based APIs. Icon keys are resolved to vanilla {@link Material} values via
 * a configurable icon map; unknown keys fall back to PAPER.
 *
 * <p>When Oraxen or a resource pack is active, override {@link #resolveIcon(String)}
 * to apply custom model data — no other code changes required.
 *
 * <p>Action callbacks are dispatched to the registered {@link #setActionHandler handler}.
 * The service layer registers the handler at startup.
 */
public class VanillaMenuProvider implements MenuProvider, Listener {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    /** Default icon resolution map: iconKey → Material name. */
    private static final Map<String, String> DEFAULT_ICON_MAP = Map.ofEntries(
            Map.entry("icon.ward.active",    "SHIELD"),
            Map.entry("ward.icon",           "SHIELD"),
            Map.entry("icon.ward.inactive",  "CRACKED_STONE_BRICKS"),
            Map.entry("ward.inactive",       "CRACKED_STONE_BRICKS"),
            Map.entry("icon.ward.orphan",    "BARRIER"),
            Map.entry("ward.orphan",         "BARRIER"),
            Map.entry("icon.ward.tier",      "NETHER_STAR"),
            Map.entry("ward.tier",           "NETHER_STAR"),
            Map.entry("ward.permissions",    "REPEATER"),
            Map.entry("ward.disband",        "TNT"),
            Map.entry("ward.transfer",       "GOLDEN_HELMET"),
            // Toggle vanilla del filtro «solo sospechosos» (admin): REDSTONE_TORCH
            // encendida = filtro activo, TORCH apagada = lista completa.
            Map.entry("icon.toggle.on",      "REDSTONE_TORCH"),
            Map.entry("icon.toggle.off",     "TORCH"),
            Map.entry("icon.nucleus",        "BEACON"),
            Map.entry("nucleus.icon",        "BEACON"),
            Map.entry("icon.upkeep",         "CHEST"),
            Map.entry("ward.upkeep",         "CHEST"),
            Map.entry("icon.members",        "PLAYER_HEAD"),
            Map.entry("city.score",          "EXPERIENCE_BOTTLE"),
            Map.entry("city.treasury",       "GOLD_BLOCK"),
            Map.entry("city.policy",         "LECTERN"),
            Map.entry("city.transfer",       "GOLDEN_HELMET"),
            Map.entry("city.delete",         "TNT"),
            Map.entry("icon.city.overview",  "BEACON"),
            Map.entry("city.overview",       "BEACON"),
            Map.entry("city.icon",           "BEACON"),
            Map.entry("icon.city.admin",     "COMMAND_BLOCK"),
            Map.entry("estate.icon",         "BOOK"),
            Map.entry("icon.estate.overview","BOOK"),
            Map.entry("estate.overview",     "BOOK"),
            Map.entry("estate.instance",     "END_PORTAL_FRAME"),
            Map.entry("estate.adventure",    "ENDER_EYE"),
            Map.entry("estate.dragon",       "ENDER_EYE"),
            Map.entry("icon.estate.zone-tp", "NETHER_STAR"),
            Map.entry("estate.zone-tp",      "NETHER_STAR"),
            Map.entry("estate.join",         "ENDER_PEARL"),
            Map.entry("estate.leave",        "IRON_DOOR"),
            Map.entry("estate.transfer",     "GOLDEN_HELMET"),
            Map.entry("estate.disband",      "TNT"),
            Map.entry("estate.dissolve",     "TNT"),
            Map.entry("icon.deposit",        "LIME_STAINED_GLASS_PANE"),
            Map.entry("menu.deposit",        "LIME_STAINED_GLASS_PANE"),
            Map.entry("icon.filler",         "GRAY_STAINED_GLASS_PANE"),
            Map.entry("menu.filler",         "GRAY_STAINED_GLASS_PANE"),
            Map.entry("icon.back",           "ARROW"),
            Map.entry("menu.back",           "ARROW"),
            Map.entry("menu.back.nexo",      "ARROW"),
            Map.entry("menu.close",          "BARRIER"),
            Map.entry("menu.invite",         "EMERALD"),
            Map.entry("menu.kick",           "SHEARS"),
            Map.entry("menu.roles",          "BOOK"),
            Map.entry("menu.permissions",    "REPEATER"),
            Map.entry("menu.transfer",       "GOLDEN_HELMET"),
            Map.entry("menu.disband",        "TNT"),
            Map.entry("menu.leave.nexo",     "IRON_DOOR"),
            Map.entry("menu.profile",        "PLAYER_HEAD"),
            Map.entry("menu.members",        "PLAYER_HEAD"),
            Map.entry("menu.confirm",        "EMERALD_BLOCK"),
            Map.entry("menu.catcher",        "PAPER"),
            // ── 2×2 quarter tiles (pack CMDs 41501-41540): each block shows one
            //    big button split across four slots; same action on all four.
            Map.entry("icon.upkeep.tl",        "CHEST"),
            Map.entry("icon.upkeep.tr",        "CHEST"),
            Map.entry("icon.upkeep.bl",        "CHEST"),
            Map.entry("icon.upkeep.br",        "CHEST"),
            Map.entry("icon.ward.tier.tl",     "NETHER_STAR"),
            Map.entry("icon.ward.tier.tr",     "NETHER_STAR"),
            Map.entry("icon.ward.tier.bl",     "NETHER_STAR"),
            Map.entry("icon.ward.tier.br",     "NETHER_STAR"),
            Map.entry("icon.city.overview.tl", "BEACON"),
            Map.entry("icon.city.overview.tr", "BEACON"),
            Map.entry("icon.city.overview.bl", "BEACON"),
            Map.entry("icon.city.overview.br", "BEACON"),
            Map.entry("city.treasury.tl",      "GOLD_BLOCK"),
            Map.entry("city.treasury.tr",      "GOLD_BLOCK"),
            Map.entry("city.treasury.bl",      "GOLD_BLOCK"),
            Map.entry("city.treasury.br",      "GOLD_BLOCK"),
            Map.entry("menu.invite.tl",        "EMERALD"),
            Map.entry("menu.invite.tr",        "EMERALD"),
            Map.entry("menu.invite.bl",        "EMERALD"),
            Map.entry("menu.invite.br",        "EMERALD"),
            Map.entry("menu.roles.tl",         "BOOK"),
            Map.entry("menu.roles.tr",         "BOOK"),
            Map.entry("menu.roles.bl",         "BOOK"),
            Map.entry("menu.roles.br",         "BOOK"),
            Map.entry("icon.estate.overview.tl", "BOOK"),
            Map.entry("icon.estate.overview.tr", "BOOK"),
            Map.entry("icon.estate.overview.bl", "BOOK"),
            Map.entry("icon.estate.overview.br", "BOOK"),
            Map.entry("icon.back.tl",          "ARROW"),
            Map.entry("icon.back.tr",          "ARROW"),
            Map.entry("icon.back.bl",          "ARROW"),
            Map.entry("icon.back.br",          "ARROW"),
            Map.entry("icon.ward.active.tl",   "SHIELD"),
            Map.entry("icon.ward.active.tr",   "SHIELD"),
            Map.entry("icon.ward.active.bl",   "SHIELD"),
            Map.entry("icon.ward.active.br",   "SHIELD"),
            Map.entry("icon.ward.inactive.tl", "CRACKED_STONE_BRICKS"),
            Map.entry("icon.ward.inactive.tr", "CRACKED_STONE_BRICKS"),
            Map.entry("icon.ward.inactive.bl", "CRACKED_STONE_BRICKS"),
            Map.entry("icon.ward.inactive.br", "CRACKED_STONE_BRICKS"),
            Map.entry("icon.estate.zone-tp.tl", "NETHER_STAR"),
            Map.entry("icon.estate.zone-tp.tr", "NETHER_STAR"),
            Map.entry("icon.estate.zone-tp.bl", "NETHER_STAR"),
            Map.entry("icon.estate.zone-tp.br", "NETHER_STAR"),
            Map.entry("menu.line",           "GRAY_STAINED_GLASS_PANE")
    );

    /**
     * Prefix of dynamic player-head icon keys: {@code icon.player.<uuid>} is
     * rendered as a PLAYER_HEAD whose SkullMeta owner is that player.
     */
    public static final String PLAYER_HEAD_PREFIX = "icon.player.";

    /** Menu iconKey → contract key when the shapes differ (presentation-assets.yml). */
    private static final Map<String, String> CONTRACT_KEY_ALIASES = Map.ofEntries(
            Map.entry("icon.ward.active",     "ward.icon"),
            Map.entry("icon.upkeep",          "ward.upkeep"),
            Map.entry("icon.members",         "city.members"),
            Map.entry("icon.city.overview",   "city.icon"),
            Map.entry("icon.estate.overview", "estate.icon"),
            Map.entry("icon.deposit",         "menu.deposit"),
            Map.entry("icon.filler",          "menu.filler"),
            Map.entry("icon.back",            "menu.back")
    );

    private final Map<String, String> iconMap;
    /** Optional asset contract (presentation-assets.yml): CMD per viewer + fallbacks. */
    private volatile dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry assets;
    /** Optional per-player resource pack state (menus.provider: auto). */
    private volatile dev.dreamcraft.protection.presentation.resourcepack.PackState packState;
    /** Glyph background titles from the resource pack (menus.custom-title). */
    private volatile boolean customTitles = true;
    private final Map<UUID, String> openMenus           = new ConcurrentHashMap<>();
    private final Map<UUID, MenuDefinition> openDefs    = new ConcurrentHashMap<>();
    private final Map<UUID, MenuContext> openContexts   = new ConcurrentHashMap<>();

    private BiConsumer<MenuContext, MenuAction> actionHandler = (ctx, act) -> {};

    /**
     * Handler for deposit slots. Receives the offered material/amount and a
     * {@code consume} action that removes the items from the cursor — invoke it
     * only after the deposit is accepted so rejected offers never destroy items.
     */
    public interface DepositHandler {
        void onDeposit(MenuContext ctx, Material material, int amount, Runnable consume);
    }

    private DepositHandler depositHandler = (ctx, mat, amt, consume) -> {};

    public VanillaMenuProvider() {
        this.iconMap = new HashMap<>(DEFAULT_ICON_MAP);
    }

    public VanillaMenuProvider(Map<String, String> extraIconMap) {
        this.iconMap = new HashMap<>(DEFAULT_ICON_MAP);
        this.iconMap.putAll(extraIconMap);
    }

    public void setActionHandler(BiConsumer<MenuContext, MenuAction> handler) {
        this.actionHandler = Objects.requireNonNull(handler);
    }

    public void setDepositHandler(DepositHandler handler) {
        this.depositHandler = Objects.requireNonNull(handler);
    }

    /**
     * Installs the asset contract (presentation-assets.yml). When present,
     * icons gain CustomModelData for players with the resource pack and fall
     * back to configured vanilla materials otherwise.
     */
    public void setAssetRegistry(dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry registry) {
        this.assets = registry;
    }

    /** Installs the per-player pack state used by {@code menus.provider: auto|rp}. */
    public void setPackTracker(dev.dreamcraft.protection.presentation.resourcepack.PackState state) {
        this.packState = state;
    }

    public dev.dreamcraft.protection.presentation.resourcepack.PackState getPackTracker() {
        return packState;
    }

    /** Enables/disables glyph background titles (config {@code menus.custom-title}). */
    public void setCustomTitles(boolean enabled) {
        this.customTitles = enabled;
    }

    /** Composes title component (HD background glyph if pack loaded, or plain text). */
    public Component composeTitle(MenuDefinition definition, UUID viewerId) {
        return customTitles
                ? dev.dreamcraft.protection.presentation.resourcepack.MenuTitleComposer
                        .compose(assets, packState, definition, viewerId)
                : LEGACY.deserialize(definition.title());
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public void open(MenuDefinition definition, MenuContext context) {
        Player player = Bukkit.getPlayer(context.viewerId());
        if (player == null) return;

        // With the pack loaded the title is an HD background glyph covering the
        // vanilla container; otherwise (or with custom-title: false) plain text.
        Component titleComp = composeTitle(definition, context.viewerId());
        MenuHolder holder = new MenuHolder(definition.menuId());
        org.bukkit.Bukkit.getLogger().info("[DreamCraft][MenuDebug] menu=" + definition.menuId()
                + " size=" + definition.size()
                + " hasPack=" + viewerHasPack(context.viewerId())
                + " customTitles=" + customTitles
                + " assetsAvail=" + (assets != null && assets.isAvailable()));
        Inventory inv = Bukkit.createInventory(holder, definition.size(), titleComp);
        holder.attach(inv);
        populate(inv, definition, context.viewerId());

        openMenus.put(context.viewerId(), definition.menuId());
        openDefs.put(context.viewerId(), definition);
        openContexts.put(context.viewerId(), context);
        player.openInventory(inv);
    }

    @Override
    public void refresh(String menuId, MenuContext context) {
        Player player = Bukkit.getPlayer(context.viewerId());
        if (player == null) return;
        if (!menuId.equals(openMenus.get(context.viewerId()))) return;
        MenuDefinition def = openDefs.get(context.viewerId());
        if (def == null) return;
        openContexts.put(context.viewerId(), context);
        populate(player.getOpenInventory().getTopInventory(), def, context.viewerId());
    }

    @Override
    public void close(UUID viewerId) {
        Player player = Bukkit.getPlayer(viewerId);
        if (player != null) player.closeInventory();
        openMenus.remove(viewerId);
        openDefs.remove(viewerId);
        openContexts.remove(viewerId);
    }

    @Override
    public boolean supports(String menuId) { return true; }

    @Override
    public String providerName() { return "vanilla"; }

    // ── Bukkit events ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only DreamCraft menus (identified by holder, never by visible title)
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;

        int size = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();
        boolean inTop = rawSlot >= 0 && rawSlot < size;

        if (event.getClick() == ClickType.DOUBLE_CLICK) { event.setCancelled(true); return; }
        if (!inTop && event.getClick().isShiftClick())   { event.setCancelled(true); return; }
        if (!inTop) return;

        event.setCancelled(true);

        UUID viewerId = player.getUniqueId();
        MenuDefinition def = openDefs.get(viewerId);
        MenuContext ctx    = openContexts.get(viewerId);
        if (def == null || ctx == null) return;

        MenuItem item = def.itemAt(rawSlot);
        if (item == null) return;

        if (item.acceptsDeposit()) {
            handleDeposit(event, player, item, ctx);
            return;
        }
        item.getAction().ifPresent(action -> actionHandler.accept(ctx, action));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        int size = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < size) { event.setCancelled(true); return; }
        }
    }

    // ── Deposit handling ──────────────────────────────────────────────────────

    private void handleDeposit(InventoryClickEvent event, Player player, MenuItem item, MenuContext ctx) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        depositHandler.onDeposit(ctx, cursor.getType(), cursor.getAmount(),
                () -> event.getView().setCursor(null));
    }

    // ── Render helpers ────────────────────────────────────────────────────────

    private void populate(Inventory inv, MenuDefinition definition, UUID viewerId) {
        // With the pack loaded the background glyph shows through the empty
        // slots; vanilla viewers keep the classic glass-pane filler.
        ItemStack filler = viewerHasPack(viewerId)
                ? new ItemStack(Material.AIR)
                : buildFiller();
        for (int i = 0; i < definition.size(); i++) inv.setItem(i, filler);
        for (MenuItem item : definition.items()) {
            if (item.slot() >= 0 && item.slot() < definition.size()) {
                inv.setItem(item.slot(), buildItemStack(item, definition.menuId(), viewerId));
            }
        }
    }

    private boolean viewerHasPack(UUID viewerId) {
        var registry = assets;
        var state = packState;
        return registry != null && registry.isAvailable()
                && state != null && state.has(viewerId);
    }

    private ItemStack buildItemStack(MenuItem item, String menuId, UUID viewerId) {
        var state = packState;
        boolean viewerHasPack = state != null && state.has(viewerId);

        String effectiveKey = (!viewerHasPack && item.fallbackKey() != null)
                ? item.fallbackKey()
                : item.iconKey();

        Material mat = null;
        if (assets != null) {
            mat = assets.resolveMaterial(effectiveKey, viewerHasPack);
            if (mat == null && effectiveKey != null && effectiveKey.startsWith("icon.")) {
                String contractKey = CONTRACT_KEY_ALIASES.getOrDefault(effectiveKey,
                        effectiveKey.substring("icon.".length()));
                mat = assets.resolveMaterial(contractKey, viewerHasPack);
            }
        }
        if (mat == null) {
            mat = resolveIcon(effectiveKey);
        }

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(itemName(item, viewerId));
        if (!item.lore().isEmpty()) {
            List<Component> loreLine = item.lore().stream()
                    .<Component>map(LEGACY::deserialize)
                    .toList();
            meta.lore(loreLine);
        }
        applySkullOwner(effectiveKey, meta);
        applyAssets(effectiveKey, stack, meta, viewerId);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Display name of a menu item as plain legacy text. The gothic TTF is
     * valid (glyf, cmap fmt 4) but its pixel glyphs are illegible at item-name
     * size (user-facing "00000") — decorative use only, never item names.
     * Lore and chat are never affected.
     */
    private Component itemName(MenuItem item, UUID viewerId) {
        return LEGACY.deserialize(item.displayName());
    }

    /**
     * Applies the asset contract for this icon: CustomModelData when the viewer
     * loaded the pack, configured vanilla fallback otherwise. Respects the
     * provider mode — {@code vanilla} never applies CMD.
     */
    private void applyAssets(String iconKey, ItemStack stack, ItemMeta meta, UUID viewerId) {
        var registry = assets;
        if (registry == null) return;
        var state = packState;
        boolean viewerHasPack = state != null && state.has(viewerId);
        registry.applyTo(iconKey, stack, meta, viewerHasPack);
        // Contract keys are semantic ("ward.icon") while menu items use the
        // "icon.<domain>.<state>" shape: when the full key has no contract
        // entry, retry with its aliased contract key (or the stripped key)
        // so CMD/fallbacks still resolve.
        if (iconKey != null && iconKey.startsWith("icon.") && registry.icon(iconKey).isEmpty()) {
            String contractKey = CONTRACT_KEY_ALIASES.getOrDefault(iconKey,
                    iconKey.substring("icon.".length()));
            registry.applyTo(contractKey, stack, meta, viewerHasPack);
        }
    }

    private ItemStack buildFiller() {
        Material mat = resolveIcon("icon.filler");
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.space());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Resolves an icon key to a Bukkit Material.
     * Override in subclasses to apply custom model data or Oraxen items.
     */
    protected Material resolveIcon(String iconKey) {
        if (iconKey != null && iconKey.startsWith(PLAYER_HEAD_PREFIX)) {
            Material head = Material.matchMaterial("PLAYER_HEAD");
            if (head != null) return head;
        }
        String materialName = iconMap.getOrDefault(iconKey, "PAPER");
        Material mat = Material.matchMaterial(materialName);
        return mat != null ? mat : Material.PAPER;
    }

    /**
     * Renders {@code icon.player.<uuid>} items as heads owned by that player
     * (SkullMeta owner). Used by the online-player picker menus.
     */
    private void applySkullOwner(String iconKey, ItemMeta meta) {
        if (!(meta instanceof org.bukkit.inventory.meta.SkullMeta skull)) return;
        if (iconKey == null || !iconKey.startsWith(PLAYER_HEAD_PREFIX)) return;
        try {
            UUID ownerId = UUID.fromString(iconKey.substring(PLAYER_HEAD_PREFIX.length()));
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId));
        } catch (IllegalArgumentException ignored) {
            // Malformed uuid in the key: keep the generic head
        }
    }
}
