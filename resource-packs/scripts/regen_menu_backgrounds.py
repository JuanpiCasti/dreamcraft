from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SHEET_THEMES = ROOT / "brand-data" / "prompts" / "menus3.png"
OUT = ROOT / "resource-packs" / "packs" / "dreamcraft" / "assets" / "dreamcraft" / "textures" / "font" / "gui"

TARGET_W = 176
# ascent 21 + baseline 13 -> glifo top en y=-8; con height = 25 + 18*filas el
# borde inferior del glifo queda en y = 17 + 18*filas (fin exacto del grid de
# slots), 2px antes del label "Inventory" en y = 19 + 18*filas.
TARGET_HEIGHTS = {"9": 43, "18": 61, "27": 79, "36": 97, "45": 115, "54": 133}

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
    "icon.ward.inactive": "menu/q/inactive",
}
ITEM_TEX = {
    "menu.profile": "menu/profile", "menu.close": "menu/close",
    "menu.kick": "menu/kick", "menu.roles": "menu/roles",
    "menu.back": "menu/back", "menu.line": "menu/line",
    "menu.members": "menu/members", "menu.confirm": "menu/confirm",
    "icon.ward.inactive": "ward/inactive", "icon.city.overview": "city/icon",
    "city.score": "city/score", "icon.back": "menu/back",
}

BAKE_LAYOUTS = {
    # v2: botones distribuidos por todo el panel (filas 1-2 + 4-5); el bloque
    # de estado/resumen deja de estar aislado y las acciones suben al primer
    # plano. Debe espejar EXACTAMENTE los anchors de los builders del plugin.
    "bg_menu_ward_status": ("default", [
        ("item", "menu.profile", 4),
        ("block", "icon.upkeep", 10),
        ("block", "icon.ward.tier", 16),
        ("item", "menu.roles", 37), ("item", "menu.roles", 46),
        ("block", "icon.city.overview", 40),
        ("item", "icon.ward.inactive", 43), ("item", "menu.close", 52),
    ], 3),
    "bg_menu_city_overview": ("matriz", [
        ("item", "menu.profile", 8),
        ("block", "menu.invite", 10),
        ("block", "icon.city.overview", 13),
        ("block", "city.treasury", 16),
        ("item", "icon.city.overview", 37), ("item", "menu.roles", 43),
        ("block", "menu.roles", 40),
        ("item", "menu.kick", 46), ("item", "icon.ward.inactive", 52),
        ("item", "menu.back", 45), ("item", "menu.close", 53),
    ], 3),
    "bg_menu_estate_lobby": ("nexo", [
        ("item", "menu.profile", 8),
        ("block", "menu.invite", 10),
        ("block", "icon.estate.overview", 13),
        ("block", "menu.invite", 16),
        ("item", "icon.back", 37), ("item", "menu.roles", 43),
        ("block", "icon.estate.overview", 40),
        ("item", "menu.back", 46), ("item", "icon.ward.inactive", 52),
        ("item", "menu.close", 53),
    ], 3),
    "bg_menu_estate_instance": ("nexo", [
        ("item", "menu.profile", 8),
        ("block", "menu.invite", 10),
        ("block", "icon.estate.overview", 13),
        ("block", "icon.back", 16),
        ("item", "menu.back", 39), ("item", "icon.ward.inactive", 40),
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
        return 8 + 18 * c, 26 + 18 * r

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

    for kind, key, slot in layout:
        if kind == "block":
            paint_block(key, slot)
        else:
            paint_item(key, slot)
    if line_row is not None:
        for c in range(9):
            paint_item("menu.line", line_row * 9 + c)
    return out


def main() -> None:
    sheet = Image.open(SHEET_THEMES).convert("RGBA")
    frames = {name: sheet.crop(bbox) for name, bbox in FRAME_BBOXES.items()}

    # 18 fondos de tema: frame directo por tema, sin filtros de color.
    for suffix, frame_name in THEME_FRAME.items():
        marco = frames[frame_name]
        for size, height in TARGET_HEIGHTS.items():
            canvas = assemble_panel(marco, TARGET_W, height, FRAME_PARAMS[frame_name])
            path = OUT / f"bg{suffix}_{size}.png"
            canvas.save(path)
            print(path.name, canvas.size, "<-", f"frame {frame_name}")

    # 4 horneados: panel del tema + visuales pintados segun el layout actual
    # de cada builder (los items no-13/14/22/23 son capturadores invisibles).
    baked = {k: (v[0], v[1], v[2]) for k, v in BAKE_LAYOUTS.items()}
    for old_name, (frame_name, layout, line_row) in baked.items():
        canvas = assemble_panel(
            frames[frame_name], TARGET_W, TARGET_HEIGHTS["54"], FRAME_PARAMS[frame_name]
        )
        canvas = paint_baked_layout(canvas, layout, line_row)
        canvas.save(OUT / f"{old_name}.png")
        print(old_name, canvas.size, "<-", f"frame {frame_name}, layout horneado")


if __name__ == "__main__":
    main()
