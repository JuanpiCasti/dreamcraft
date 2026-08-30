from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SHEET_THEMES = ROOT / "brand-data" / "prompts" / "menus3.png"
# Camino A (sync/ward): panel maestro en alta res del diseñador (1442x1091).
# Se re-encuadra al rectángulo del marco (interior útil y92..971 + ornamento
# ~45px arriba / ~42px abajo) y se re-escala a 176x133. El recorte vertical
# centra el interior sobre las 6 filas de slots (y26..132) y contiene el
# ornamento inferior para que no invada el label "Inventory".
WARD_PANEL = ROOT / "brand-data" / "prompts" / "ChatGPT Image 29 ago 2026, 03_16_33 a.m..png"
SYNC_CONBG = ROOT / "brand-data" / "prompts" / "Sync-ui-conbg.png"
MATRIZ_CONBG = ROOT / "brand-data" / "prompts" / "Matriz-ui-conbg.png"
NEXO_CONBG = ROOT / "brand-data" / "prompts" / "Nexo-ui-conbg.png.png"
if not NEXO_CONBG.exists():
    NEXO_CONBG = ROOT / "brand-data" / "prompts" / "Nexo-ui-conbg.png"

# Paneles del diseñador procesados con canal alfa: contorno del menú transparente en exteriores
SYNC_PANEL = ROOT / "brand-data" / "prompts" / "Sync-panel.png"
MATRIZ_PANEL = ROOT / "brand-data" / "prompts" / "Matriz-panel.png"
NEXO_PANEL = ROOT / "brand-data" / "prompts" / "Nexo-panel.png"
OUT = ROOT / "resource-packs" / "packs" / "dreamcraft" / "assets" / "dreamcraft" / "textures" / "font" / "gui"


def extract_panel_with_transparent_margins(src_path: Path, thresh: float = 60.0, offset_x: int = 15, offset_y: int = 6) -> Image.Image:
    """Extrae el contorno del menú desde el render 2048x1536 'con background',
    dejando transparentes (alpha=0) todos los márgenes y esquinas exteriores
    fuera del contorno del marco, y recortando al bounding box del marco útil.
    """
    im = Image.open(src_path).convert("RGB")
    arr = np.array(im)
    h, w, _ = arr.shape
    val = arr.max(axis=2)

    min_safe_x, max_safe_x = offset_x, w - offset_x
    min_safe_y, max_safe_y = offset_y, h - offset_y

    top_edge = np.full(w, h, dtype=int)
    for x in range(min_safe_x, max_safe_x):
        hits = np.where(val[min_safe_y:h // 2, x] >= thresh)[0]
        if len(hits):
            top_edge[x] = min_safe_y + hits[0]

    bot_edge = np.full(w, -1, dtype=int)
    for x in range(min_safe_x, max_safe_x):
        hits = np.where(val[h // 2:max_safe_y, x] >= thresh)[0]
        if len(hits):
            bot_edge[x] = h // 2 + hits[-1]

    left_edge = np.full(h, w, dtype=int)
    for y in range(min_safe_y, max_safe_y):
        hits = np.where(val[y, min_safe_x:w // 2] >= thresh)[0]
        if len(hits):
            left_edge[y] = min_safe_x + hits[0]

    right_edge = np.full(h, -1, dtype=int)
    for y in range(min_safe_y, max_safe_y):
        hits = np.where(val[y, w // 2:max_safe_x] >= thresh)[0]
        if len(hits):
            right_edge[y] = w // 2 + hits[-1]

    xs = np.arange(w)[np.newaxis, :]
    ys = np.arange(h)[:, np.newaxis]

    envelope = (ys >= top_edge[xs]) & (ys <= bot_edge[xs]) & (xs >= left_edge[ys]) & (xs <= right_edge[ys])
    margin_zone = (xs < 55) | (xs > w - 55) | (ys < 95) | (ys > h - 75)
    clean_mask = envelope & ~(margin_zone & (val < 45))

    rgba_arr = np.zeros((h, w, 4), dtype=np.uint8)
    rgba_arr[:, :, :3] = arr
    rgba_arr[:, :, 3] = np.where(clean_mask, 255, 0).astype(np.uint8)

    mask_ys, mask_xs = np.where(clean_mask)
    min_x, max_x = mask_xs.min(), mask_xs.max()
    min_y, max_y = mask_ys.min(), mask_ys.max()

    return Image.fromarray(rgba_arr, "RGBA").crop((min_x, min_y, max_x + 1, max_y + 1))


def assemble_designer_panel(panel_path: Path, target_w: int = 178, target_h: int = 141, top_h: int = 14, bot_h: int = 9) -> Image.Image:
    """Ensambla el panel del diseñador preservando la proporción natural del marco superior
    y elevando el contorno inferior para no chocar con 'inventory':
    - X: [0..8] barra lateral izquierda sólida (sellando la línea blanca vanilla con offset -9)
         [9..169] interior de slots centrado exactamente sobre las 9 columnas de Minecraft
         [170..target_w] barra lateral derecha
    - Y: [0..top_h] cabecera superior y título (proporción natural ~11x, no estirado)
         [top_h..target_h-bot_h] cuerpo interior para las 6 filas de slots de Minecraft
         [target_h-bot_h..target_h] barra inferior elevada para no chocar con 'inventory'
    """
    panel = Image.open(panel_path).convert("RGBA")
    pw, ph = panel.size
    arr = np.array(panel)
    val = arr[:, :, :3].max(axis=2)
    alpha = arr[:, :, 3]

    col_t = val[:300, pw // 3]
    drop_t = np.argmax(col_t) + np.where(col_t[np.argmax(col_t):] < 45)[0][0]

    col_b = val[ph - 300:, pw // 3]
    drop_b = ph - 300 + np.where(col_b[:np.argmax(col_b)] < 45)[0][-1]

    row_l = val[ph // 2, :300]
    drop_l = np.argmax(row_l) + np.where(row_l[np.argmax(row_l):] < 45)[0][0]

    row_r = val[ph // 2, pw - 300:]
    drop_r = pw - 300 + np.where(row_r[:np.argmax(row_r)] < 45)[0][-1]

    first_solid_x = np.where(alpha[ph // 2] > 200)[0][0]
    last_solid_x = np.where(alpha[ph // 2] > 200)[0][-1]

    out = Image.new("RGBA", (target_w, target_h), (0, 0, 0, 0))

    x_src = [first_solid_x, drop_l, drop_r, last_solid_x + 1]
    y_src = [0, drop_t, drop_b, ph]

    x_dst = [0, 9, 170, target_w]
    y_dst = [0, top_h, target_h - bot_h, target_h]

    for yi in range(3):
        for xi in range(3):
            sb = (x_src[xi], y_src[yi], x_src[xi + 1], y_src[yi + 1])
            dw = x_dst[xi + 1] - x_dst[xi]
            dh = y_dst[yi + 1] - y_dst[yi]

            chunk = panel.crop(sb)
            res = chunk.resize((dw, dh), Image.LANCZOS)
            out.paste(res, (x_dst[xi], y_dst[yi]))

    out_arr = np.array(out)
    # 1. Sellar borde lateral izquierdo en x=0,1 (sellando la línea blanca vanilla)
    for y in range(top_h, target_h - bot_h):
        solids = np.where(out_arr[y, :, 3] > 180)[0]
        if len(solids) and solids[0] > 0:
            fill_color = out_arr[y, solids[0]]
            out_arr[y, :solids[0]] = fill_color

    # 2. Sellar borde superior de la GUI vanilla en y=7..8 para eliminar la línea blanca superior
    interior_color = out_arr[top_h + 2, target_w // 2]
    for y in [7, 8]:
        for x in range(target_w):
            if out_arr[y, x, 3] < 180:
                ref_color = out_arr[top_h, x] if out_arr[top_h, x, 3] > 180 else interior_color
                out_arr[y, x] = ref_color

    return Image.fromarray(out_arr, "RGBA")


TARGET_W = 176
TARGET_W_BAKED = 178
PANEL_SPECS = {
    "ward": {"target_h": 141, "top_h": 14, "bot_h": 9},
    "city": {"target_h": 139, "top_h": 12, "bot_h": 8},
    "estate": {"target_h": 139, "top_h": 12, "bot_h": 8},
}
# ascent 21 + baseline 13 -> glifo top en y=-8; con height = 144 el
# borde inferior del glifo queda en y = 136 (por debajo de los slots de fila 5
# que terminan en y=125, borde 126), sellando el fondo sin tapar slots.
TARGET_HEIGHTS = {"9": 43, "18": 61, "27": 85, "36": 97, "45": 115, "54": 144}

# Marcos DIRECTOS de menus3.png (sin filtros HSV): un frame por tema.
#   matriz  = frame superior  (azul, gemas de diamante en las esquinas)
#   default = frame medio     (azul-violeta, esferas en las esquinas) -> Synt
#   nexo    = frame inferior  (violeta, chevrones en las esquinas)
#
# Los frames miden ~1770x250: no entran a 176px con un 9-slice plano (las
# esquinas ornamentales quedarian estiradas). Este ensamblador compone el
# panel por piezas: esquinas reducidas, ornato central superior/inferior
# reescalado, barras laterales limpias estiradas e interior con su glow.
FRAME_BBOXES = {
    "matriz": (40, 17, 1804, 267),
    "default": (38, 299, 1809, 547),
    "nexo": (38, 580, 1812, 829),
}
# params por frame: C=recorte de esquina, top_t=grosor borde horiz,
# side_t=grosor borde vert, band_src=alto de la franja superior/inferior
# (borde + colgado del ornato). Todo se comprime ~8x al ensamblar, igual
# que la proporcion del frame completo (1764 -> 176).
FRAME_BBOXES = {
    "matriz": (40, 17, 1804, 267),
    "default": (38, 299, 1809, 547),
    "nexo": (38, 580, 1812, 829),
}
FRAME_PARAMS = {
    "matriz": {"C": 64, "top_t": 28, "side_t": 15, "band_src": 84},
    "default": {"C": 110, "top_t": 24, "side_t": 14, "band_src": 96},
    "nexo": {"C": 84, "top_t": 21, "side_t": 8, "band_src": 72},
}
THEME_FRAME = {"": "default", "_matriz": "matriz", "_nexo": "nexo"}

# Menus horneados: panel + contenido pintado (iconos/barra/perfil/cristal).
# Tema por dominio: ward->Synt(acero), city->Matriz(azul), estate->Nexo(violeta).
BAKED_THEMES = {
    "bg_menu_ward_status": "default",
    "bg_menu_city_overview": "matriz",
    "bg_menu_estate_lobby": "nexo",
    "bg_menu_estate_instance": "nexo",
}
# El ancla cambia de ascent 24 (top -11) a ascent 21 (top -8): el contenido
# Menús horneados: panel del tema + visuales pintados según el layout ACTUAL
# de cada builder (los items no-catchers quedan como capturadores invisibles;
# sin este arte los botones no se ven). Formato: (tema, linea_fila, elementos)
# donde cada elemento es (kind, iconKey, slot) con kind "item" (16px) o
# "block" (2x2 de cuadrantes escalado a 36px).
TEX_ROOT = Path(__file__).resolve().parents[1] / "packs" / "dreamcraft" / "assets" / "dreamcraft" / "textures" / "item"

BLOCK_TEX = {
    "icon.upkeep": "menu/q/upkeep", "icon.ward.tier": "menu/q/fase",
    "icon.city.overview": "menu/q/matriz", "city.treasury": "menu/q/tesoro",
    "menu.invite": "menu/q/invite", "menu.roles": "menu/q/roles",
    "icon.back": "menu/q/salir", "icon.estate.overview": "menu/q/iniciar",
    "icon.estate.zone-tp": "menu/q/zone-tp", "icon.ward.active": "menu/q/ward",
    "icon.ward.inactive": "menu/q/inactive", "icon.estate.join": "menu/q/unirse",
}
ITEM_TEX = {
    "menu.profile": "menu/profile", "menu.close": "menu/close",
    "menu.kick": "menu/kick", "menu.roles": "menu/roles",
    "menu.back": "menu/back", "menu.line": "menu/line",
    "menu.members": "menu/members", "menu.confirm": "menu/confirm",
    "icon.ward.inactive": "ward/inactive", "icon.city.overview": "city/icon",
    "city.score": "city/score", "icon.back": "menu/back",
    "menu.profile.matriz": "menu/profile_matriz",
    "menu.profile.nexo": "menu/profile_nexo",
    "menu.profile.sync": "menu/profile_sync",
    "menu.roles.matriz": "menu/roles_matriz",
    "menu.roles.nexo": "menu/roles_nexo",
    "menu.roles.sync": "menu/roles_sync",
    "menu.invite.matriz": "menu/invite_matriz",
    "menu.invite.nexo": "menu/invite_nexo",
    "menu.invite.sync": "menu/invite_sync",
    "menu.join.matriz": "menu/join_matriz",
    "menu.join.nexo": "menu/join_nexo",
    "menu.gear.sync": "menu/gear_sync",
    "menu.gear.matriz": "menu/gear_matriz",
    "menu.gear.nexo": "menu/gear_nexo",
    "ward.permissions": "menu/permissions",
    "menu.permissions": "menu/permissions",
    "menu.back.nexo": "menu/back_nexo",
    "menu.leave.nexo": "menu/leave_nexo",
}

BAKE_LAYOUTS = {
    # v2: espeja EXACTAMENTE los anchors de los builders del plugin. 54 slots = 6 filas de 9.
    # ward_status (default/acero):
    #   R0-2: estado 3x3@3
    #   R1-2: upkeep 2x2@9, tier 2x2@16
    #   R3: separador
    #   R4 (Fila 5): cerrar(flecha izq)@36, perfil@37, permisos(papel)@38, transferir(personitas)@39,
    #                Matriz 2x2@40 (filas 4-5, slots 40/41/49/50), apagar(escudo apagado)@43
    "bg_menu_ward_status": ("default", [
        ("block3", "icon.ward.active", 3),
        ("block", "icon.upkeep", 9),
        ("block", "icon.ward.tier", 16),
        ("block", "icon.city.overview", 40),
        ("item", "menu.back", 36),
        ("item", "menu.profile.sync", 37),
        ("item", "ward.permissions", 38),
        ("item", "menu.roles.sync", 39),
        ("item", "icon.ward.inactive", 43),
    ], 3),
    # ward_inactive: mismo panel ward con el cristal 3x3 apagado
    "bg_menu_ward_inactive": ("default", [
        ("block3", "icon.ward.inactive", 3),
        ("block", "icon.upkeep", 9),
        ("block", "icon.ward.tier", 16),
        ("block", "icon.city.overview", 40),
        ("item", "menu.back", 36),
        ("item", "menu.profile.sync", 37),
        ("item", "ward.permissions", 38),
        ("item", "menu.roles.sync", 39),
        ("item", "icon.ward.inactive", 43),
    ], 3),
    # city_overview (matriz/azul):
    #   R0-2: resumen matriz 3x3@3
    #   R1-2: invitar 2x2@9, roles 2x2@16
    #   R3:   separador
    #   R3-5: tesoro 3x3@30 (slots 30..32, 39..41, 48..50)
    #   R4 (Fila 5): cerrar(flecha izq)@36, perfil@37, políticas(papel)@38, expulsar@42, transferir@43, eliminar(escudo apagado)@44
    "bg_menu_city_overview": ("matriz", [
        ("block3", "icon.city.overview", 3),
        ("block", "menu.invite", 9),
        ("block", "menu.roles", 16),
        ("block3", "city.treasury", 30),
        ("item", "menu.back", 36),
        ("item", "menu.profile.matriz", 37),
        ("item", "ward.permissions", 38),
        ("item", "menu.kick", 42),
        ("item", "menu.roles.matriz", 43),
        ("item", "icon.ward.inactive", 44),
    ], 3),
    # estate_lobby (nexo/violeta):
    #   R0: perfil@8
    #   R1-2: invitar 2x2@9, resumen 3x2@13 (slots 13..15, 22..24), unirse 2x2@16
    #   R3: separador
    #   R4 (Fila 5): cerrar(flecha violeta)@36, abandonar(puerta)@37, iniciar(dragón) 3x2@39 (slots 39..41, 48..50), transferir@43, disolver(escudo apagado)@44
    "bg_menu_estate_lobby": ("nexo", [
        ("item", "menu.profile.nexo", 8),
        ("block", "menu.invite", 9),
        ("block3x2", "icon.estate.overview", 13),
        ("block", "icon.estate.join", 16),
        ("item", "menu.back.nexo", 36),
        ("item", "menu.leave.nexo", 37),
        ("block3x2", "icon.estate.overview", 39),
        ("item", "menu.roles.nexo", 43),
        ("item", "icon.ward.inactive", 44),
    ], 3),
    # estate_instance (nexo/violeta):
    #   R0: _ _ _ _ _ _ _ P _    perfil@8
    #   R1-2: I I _ O O O _ b b  invitar@9, resumen 3x2@13, salir@16
    #   R3: ─────────────────    separador
    #   R4: _ _ _ _ v i c _ _    volver@39, cerrar-instancia@40, cerrar@41
    "bg_menu_estate_instance": ("nexo", [
        ("item", "menu.profile.nexo", 8),
        ("block", "menu.invite", 9),
        ("block3x2", "icon.estate.overview", 13),
        ("block", "icon.back", 16),
        ("item", "menu.back.nexo", 39),
        ("item", "icon.ward.inactive", 40),
        ("item", "menu.close", 41),
    ], 3),
}


def interior_median(im: Image.Image, band: int = 0) -> tuple:
    a = np.array(im.convert("RGBA"), dtype=np.float32)
    h, w = a.shape[:2]
    # cuerpo superior del interior (navy oscuro, sin el glow del pie)
    inner = a[band + 10 : h // 2, w // 3 : 2 * w // 3]
    op = inner[..., 3] > 200
    med = np.median(inner[op], axis=0)
    return tuple(int(c) for c in med[:3])


def feather_horizontal(im: Image.Image, ramp: int = 10) -> Image.Image:
    """Difumina los bordes izquierdo/derecho de una pieza (rampa alpha).

    Evita los cortes secos del ornato central contra la franja horizontal
    del marco: los últimos `ramp` px de cada lado se desvanecen a 0.
    """
    a = np.array(im.convert("RGBA"), dtype=np.float32)
    w = a.shape[1]
    ramp = min(ramp, w // 2)
    alpha_ramp = np.linspace(0.0, 1.0, ramp, dtype=np.float32)
    a[..., 3] *= np.concatenate([alpha_ramp, np.ones(w - 2 * ramp, dtype=np.float32), alpha_ramp[::-1]])
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def edge_color(marco: Image.Image) -> tuple:
    """Color mediano del anillo exterior (2px) del marco original.

    Se usa para trazar un borde de 1px nítido y uniforme sobre el panel
    ensamblado: el reescalado por piezas deja el perímetro con píxeles
    desparejos y este trazo unifica los cuatro lados.
    """
    a = np.array(marco.convert("RGBA"), dtype=np.float32)
    ring = np.concatenate([
        a[0:2].reshape(-1, 4), a[-2:].reshape(-1, 4),
        a[:, 0:2].reshape(-1, 4), a[:, -2:].reshape(-1, 4),
    ])
    op = ring[..., 3] > 200
    if not op.any():
        return (30, 34, 48)
    med = np.median(ring[op], axis=0)
    return tuple(int(c) for c in med[:3])


def assemble_panel(marco: Image.Image, w: int, h: int, p: dict) -> Image.Image:
    """Compone el panel w x h desde el frame grande por piezas.

    Las bandas se comprimen con la relacion vertical (mh -> h, ~0.53) para
    que el borde conserve el grosor del frame; las esquinas se ajustan al
    ancho disponible. Las piezas se realzan (+brillo) porque el reescalado
    LANCZOS apaga las lineas finas de 1-2px.
    """
    mw, mh = marco.size
    C = p["C"]
    top_t = p["top_t"]
    side_t = p["side_t"]
    band = p["band_src"]
    cw = 18
    ch = max(9, min(14, round(h * 0.105)))
    sw = max(3, round(side_t * 0.28))
    # SIN boost: el oscurecido que se ve in-game es el tinte del color de
    # titulo (#404040) aplicado al glifo; el fix es enviar el glifo con
    # color blanco (§f) desde el plugin. Boostear aqui sobrepasaria la
    # referencia una vez corregido el tinte.
    boost = 1.0

    def bright(im: Image.Image) -> Image.Image:
        if boost == 1.0:
            return im
        a = np.array(im.convert("RGBA"), dtype=np.float32)
        a[..., :3] = np.clip(a[..., :3] * boost, 0, 255)
        return Image.fromarray(a.astype(np.uint8), "RGBA")

    backing = interior_median(marco, p["band_src"])
    out = Image.new("RGBA", (w, h), backing + (255,))

    # interior directo del frame: base BOX (gradiente y glow sin haces) +
    # overlay NEAREST parcial (devuelve el grano de particulas del starfield).
    # El pie se inseta -6px para no arrastrar el borde inferior del frame
    # (que duplicaria una linea sobre el borde real del panel).
    iw, ih = w - 2 * sw, h - 2 * ch
    inner = marco.crop((side_t + 2, band, mw - side_t - 2, mh - band - 6))
    base = inner.resize((iw, ih), Image.BOX).convert("RGBA")
    grain = inner.resize((iw, ih), Image.NEAREST).convert("RGBA")
    grain.putalpha(115)
    base.alpha_composite(grain)
    out.paste(base, (sw, ch))

    # barras laterales limpias estiradas en vertical
    left = bright(marco.crop((0, C, side_t + 4, mh - C)).resize((sw, h - 2 * ch), Image.NEAREST))
    right = bright(marco.crop((mw - side_t - 4, C, mw, mh - C)).resize((sw, h - 2 * ch), Image.NEAREST))
    out.paste(left, (0, ch))
    out.paste(right, (w - sw, ch))

    # franjas superior/inferior SOLO con el grosor de la linea del borde
    # (top_t+6): sin el padding oscuro del frame que las volvia negras.
    line_h = top_t + 6
    top_mid = bright(marco.crop((C, 0, mw - C, line_h)).resize((w - 2 * cw, ch), Image.LANCZOS))
    bot_mid = bright(marco.crop((C, mh - line_h, mw - C, mh)).resize((w - 2 * cw, ch), Image.LANCZOS))
    out.paste(top_mid, (cw, 0))
    out.paste(bot_mid, (cw, h - ch))

    # ornato central superior/inferior (cuelga hacia el interior); su fondo
    # oscuro se funde con el navy del panel. Bordes difuminados en horizontal
    # para eliminar los cortes secos contra la franja del marco.
    cw_orn = 220
    orn_top = bright(marco.crop((mw // 2 - cw_orn // 2, 0, mw // 2 + cw_orn // 2, band)).resize((56, ch + 6), Image.LANCZOS))
    orn_bot = bright(marco.crop((mw // 2 - cw_orn // 2, mh - band, mw // 2 + cw_orn // 2, mh)).resize((56, ch + 6), Image.LANCZOS))
    out.alpha_composite(feather_horizontal(orn_top), ((w - 56) // 2, 0))
    out.alpha_composite(feather_horizontal(orn_bot), ((w - 56) // 2, h - ch - 6))

    # esquinas reducidas (encima de todo)
    out.paste(bright(marco.crop((0, 0, C, band)).resize((cw, ch), Image.LANCZOS)), (0, 0))
    out.paste(bright(marco.crop((mw - C, 0, mw, band)).resize((cw, ch), Image.LANCZOS)), (w - cw, 0))
    out.paste(bright(marco.crop((0, mh - band, C, mh)).resize((cw, ch), Image.LANCZOS)), (0, h - ch))
    out.paste(bright(marco.crop((mw - C, mh - band, mw, mh)).resize((cw, ch), Image.LANCZOS)), (w - cw, h - ch))

    # ── Líneas de borde ──
    # 1) Trazo exterior de 1px (color muestreado del marco): perímetro limpio
    #    y uniforme en los cuatro lados.
    # 2) Línea de acento continua de 2px (inset 1), color del marco aclarado:
    #    corre por encima de esquinas y franjas y unifica el ensamblado por
    #    piezas (elimina los escalones entre crops).
    # 3) Filo interior de 1px al inset del borde (sw/ch): separa nítidamente
    #    el marco del interior.
    ec = edge_color(marco)
    draw = ImageDraw.Draw(out)
    draw.rectangle([0, 0, w - 1, h - 1], outline=ec + (255,))
    accent = tuple(min(255, int(c * 1.9)) for c in ec)
    draw.rectangle([1, 1, w - 3, h - 3], outline=accent + (255,))
    draw.rectangle([2, 2, w - 3, h - 3], outline=accent + (255,))
    inner = tuple(min(255, int(c * 1.35)) for c in ec)
    draw.rectangle([sw - 1, ch - 1, w - sw, h - ch], outline=inner + (255,))
    return out


def glow_accent(im: Image.Image, band: int) -> tuple:
    """Color acento del glow: mediana del pie central del interior."""
    a = np.array(im.convert("RGBA"), dtype=np.float32)
    h, w = a.shape[:2]
    region = a[h - band - 14 : h - band, w // 2 - w // 6 : w // 2 + w // 6]
    med = np.median(region[..., :3].reshape(-1, 3), axis=0)
    return tuple(float(c) for c in med)


def paint_baked_layout(canvas: Image.Image, layout: list, line_row: int) -> Image.Image:
    """Pinta los visuales de los capturadores segun el layout del builder.

    Coordenadas de glifo (ascent 21 -> top en GUI y=-8): un slot (fila r,
    columna c) pone su arte 16x16 en (8+18c+1, 26+18r+1); un bloque 2x2 pinta
    el ensamblado de cuadrantes escalado a 36x36 en la esquina del bloque,
    igual que lo renderizarian los items con oversized_in_gui.
    """
    out = canvas.copy()

    def slot_xy(slot):
        r, c = divmod(slot, 9)
        return 9 + 18 * c, 26 + 18 * r

    def paint_item(icon_key, slot):
        rel = ITEM_TEX[icon_key]
        tex = Image.open(TEX_ROOT / (rel + ".png")).convert("RGBA")
        tex = tex.resize((16, 16), Image.NEAREST)
        x, y = slot_xy(slot)
        out.alpha_composite(tex, (x + 1, y + 1))

    def paint_block(base_key, slot):
        asm = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for suffix, (dx, dy) in (("_tl", (0, 0)), ("_tr", (1, 0)),
                                 ("_bl", (0, 1)), ("_br", (1, 1))):
            q = Image.open(TEX_ROOT / (BLOCK_TEX[base_key] + suffix + ".png")).convert("RGBA")
            asm.alpha_composite(q, (dx * 16, dy * 16))
        asm = asm.resize((36, 36), Image.NEAREST)
        x, y = slot_xy(slot)
        out.alpha_composite(asm, (x, y))

    def paint_block3(base_key, slot):
        # Prueba de centrado: icono central a 3x3 slots (54px) desde el 2x2.
        asm = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for suffix, (dx, dy) in (("_tl", (0, 0)), ("_tr", (1, 0)),
                                 ("_bl", (0, 1)), ("_br", (1, 1))):
            q = Image.open(TEX_ROOT / (BLOCK_TEX[base_key] + suffix + ".png")).convert("RGBA")
            asm.alpha_composite(q, (dx * 16, dy * 16))
        asm = asm.resize((54, 54), Image.NEAREST)
        x, y = slot_xy(slot)
        out.alpha_composite(asm, (x, y))

    def paint_block3x2(base_key, slot):
        # 3 slots horizontales (54px) x 2 slots verticales (36px)
        asm = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for suffix, (dx, dy) in (("_tl", (0, 0)), ("_tr", (1, 0)),
                                 ("_bl", (0, 1)), ("_br", (1, 1))):
            q = Image.open(TEX_ROOT / (BLOCK_TEX[base_key] + suffix + ".png")).convert("RGBA")
            asm.alpha_composite(q, (dx * 16, dy * 16))
        asm = asm.resize((54, 36), Image.NEAREST)
        x, y = slot_xy(slot)
        out.alpha_composite(asm, (x, y))

    if line_row is not None:
        for c in range(9):
            paint_item("menu.line", line_row * 9 + c)
    for kind, key, slot in layout:
        if kind == "block":
            paint_block(key, slot)
        elif kind == "block3":
            paint_block3(key, slot)
        elif kind == "block3x2":
            paint_block3x2(key, slot)
        else:
            paint_item(key, slot)
    return out


def main() -> None:
    sheet = Image.open(SHEET_THEMES).convert("RGBA")
    frames = {name: sheet.crop(bbox) for name, bbox in FRAME_BBOXES.items()}

    # Fondo del diseñador por tema: Sync=ward(acero), Matriz=city(azul),
    # Nexo=estate(violeta). Se extrae el contorno transparente del menú.
    panels_cfg = {
        "ward": (SYNC_CONBG, SYNC_PANEL, 60.0),
        "city": (MATRIZ_CONBG, MATRIZ_PANEL, 60.0),
        "estate": (NEXO_CONBG, NEXO_PANEL, 60.0),
    }
    source_panels = {}
    for theme, (src_conbg, dst_panel, thresh) in panels_cfg.items():
        if src_conbg.exists():
            panel = extract_panel_with_transparent_margins(src_conbg, thresh=thresh)
            panel.save(dst_panel)
            source_panels[theme] = dst_panel
            print(dst_panel.name, panel.size, "<-", src_conbg.name, "(contorno extraído con transparencia)")
        else:
            source_panels[theme] = dst_panel

    # 18 fondos de tema: para size 54 (usado en menús admin) se usa el panel del diseñador
    # limpio (178px con proporciones naturales y sellado de bordes).
    for suffix, frame_name in THEME_FRAME.items():
        marco = frames[frame_name]
        theme = "ward" if suffix == "" else ("city" if suffix == "_matriz" else "estate")
        for size, height in TARGET_HEIGHTS.items():
            if size == "54":
                src_path = source_panels[theme]
                spec = PANEL_SPECS[theme]
                canvas = assemble_designer_panel(src_path, TARGET_W_BAKED, spec["target_h"], spec["top_h"], spec["bot_h"])
            elif size == "27":
                src_path = source_panels[theme]
                spec = PANEL_SPECS[theme]
                canvas = assemble_designer_panel(src_path, TARGET_W_BAKED, height, spec["top_h"], spec["bot_h"])
            else:
                canvas = assemble_panel(marco, TARGET_W, height, FRAME_PARAMS[frame_name])
            path = OUT / f"bg{suffix}_{size}.png"
            canvas.save(path)
            print(path.name, canvas.size, "<-", f"designer {theme}" if size in ("27", "54") else f"frame {frame_name}")

    # 4+1 horneados: panel del tema + visuales pintados segun el layout actual
    # de cada builder (los items son capturadores invisibles).
    baked = {k: (v[0], v[1], v[2]) for k, v in BAKE_LAYOUTS.items()}
    for old_name, (frame_name, layout, line_row) in baked.items():
        if old_name.startswith("bg_menu_ward_"):
            theme = "ward"
        elif old_name.startswith("bg_menu_city_"):
            theme = "city"
        else:
            theme = "estate"
        src_path = source_panels[theme]
        spec = PANEL_SPECS[theme]
        canvas = assemble_designer_panel(src_path, TARGET_W_BAKED, spec["target_h"], spec["top_h"], spec["bot_h"])
        src = f"panel disenador ({src_path.stem}, proporcion natural {TARGET_W_BAKED}x{spec['target_h']})"
        canvas = paint_baked_layout(canvas, layout, line_row)
        canvas.save(OUT / f"{old_name}.png")
        print(old_name, canvas.size, "<-", src + ", layout horneado")


if __name__ == "__main__":
    main()
