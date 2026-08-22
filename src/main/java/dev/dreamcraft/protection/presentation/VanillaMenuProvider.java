package dev.dreamcraft.protection.presentation;

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

    /** Magic separator encoded in the inventory title so click events can identify menus. */
    private static final String TITLE_MAGIC = "\u00A78[DC:";
    private static final String TITLE_SUFFIX = "]";

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    /** Default icon resolution map: iconKey → Material name. */
    private static final Map<String, String> DEFAULT_ICON_MAP = Map.of(
            "icon.ward.active",    "SHIELD",
            "icon.ward.inactive",  "CRACKED_STONE_BRICKS",
            "icon.upkeep",         "CHEST",
            "icon.members",        "PLAYER_HEAD",
            "icon.city.overview",  "BEACON",
            "icon.estate.overview","BOOK",
            "icon.deposit",        "LIME_STAINED_GLASS_PANE",
            "icon.filler",         "GRAY_STAINED_GLASS_PANE",
            "icon.back",           "ARROW"
    );

    private final Map<String, String> iconMap;
    /** Optional asset contract (presentation-assets.yml): CMD per viewer + fallbacks. */
    private volatile dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry assets;
    /** Optional per-player resource pack state (menus.provider: auto). */
    private volatile dev.dreamcraft.protection.presentation.resourcepack.PackState packState;
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

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public void open(MenuDefinition definition, MenuContext context) {
        Player player = Bukkit.getPlayer(context.viewerId());
        if (player == null) return;

        // Embed menu ID in the title via a hidden suffix for event identification
        String rawTitle = definition.title() + TITLE_MAGIC + definition.menuId() + TITLE_SUFFIX;
        Component titleComp = LEGACY.deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(null, definition.size(), titleComp);
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
        // Retrieve title via Adventure API — avoids deprecated InventoryView#getTitle()
        String title = LEGACY.serialize(event.getView().title());
        if (!isDCMenu(title)) return;

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
        String title = LEGACY.serialize(event.getView().title());
        if (!isDCMenu(title)) return;
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
        ItemStack filler = buildFiller();
        for (int i = 0; i < definition.size(); i++) inv.setItem(i, filler);
        for (MenuItem item : definition.items()) {
            if (item.slot() >= 0 && item.slot() < definition.size()) {
                inv.setItem(item.slot(), buildItemStack(item, viewerId));
            }
        }
    }

    private ItemStack buildItemStack(MenuItem item, UUID viewerId) {
        Material mat = resolveIcon(item.iconKey());
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(LEGACY.deserialize(item.displayName()));
        if (!item.lore().isEmpty()) {
            List<Component> loreLine = item.lore().stream()
                    .<Component>map(LEGACY::deserialize)
                    .toList();
            meta.lore(loreLine);
        }
        applyAssets(item.iconKey(), stack, meta, viewerId);
        stack.setItemMeta(meta);
        return stack;
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
        String materialName = iconMap.getOrDefault(iconKey, "PAPER");
        Material mat = Material.matchMaterial(materialName);
        return mat != null ? mat : Material.PAPER;
    }

    private boolean isDCMenu(String title) {
        return title != null && title.contains(TITLE_MAGIC);
    }
}
