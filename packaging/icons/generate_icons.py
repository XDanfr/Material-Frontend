"""Generate M3 Expressive emulation-frontend icon concepts as SVG.

Shape paths: matraic/m3e packages/web/src/shape/ShapePolygon.ts (380x380 space, centre 190).
Colours: M3 baseline tonal roles (light + dark).
"""
import json, os, sys

SHAPES = json.load(open(sys.argv[1]))
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)

# M3 baseline roles
P, ON_P, PC, ON_PC = "#6750A4", "#FFFFFF", "#EADDFF", "#4F378B"
S, SC, ON_SC = "#625B71", "#E8DEF8", "#4A4458"
T, TC, ON_TC = "#7D5260", "#FFD8E4", "#633B48"
D_SURF, D_P, D_PC, D_ON_P, D_T, D_TC = "#1D1B20", "#D0BCFF", "#4F378B", "#381E72", "#EFB8C8", "#633B48"

R = 112  # icon tile corner radius (design decision, ~squircle look at 512)


def shape(name, cx, cy, size, fill, rot=0, extra=""):
    """Place a library shape so its 320-unit design box spans `size` px."""
    k = size / 320
    return (f'<path d="{SHAPES[name]}" fill="{fill}" {extra}'
            f'transform="translate({cx} {cy}) rotate({rot}) scale({k:.4f}) translate(-190 -190)"/>')


def icon(name, bg, body):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">\n'
           f'<title>{name}</title>\n'
           f'<defs><clipPath id="tile"><rect width="512" height="512" rx="{R}"/></clipPath></defs>\n'
           f'<g clip-path="url(#tile)">\n<rect width="512" height="512" fill="{bg}"/>\n'
           + "\n".join(body) + "\n</g>\n</svg>\n")
    open(os.path.join(OUT, f"{name}.svg"), "w").write(svg)


def rrect(x, y, w, h, r, fill, extra=""):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" {extra}/>'


# 1 — Face Buttons: four different library shapes as a controller's face buttons.
icon("01-face-buttons", PC, [
    shape("soft-burst", 256, 256, 400, "#DCCBFB", rot=8),
    shape("sunny", 256, 146, 128, T, rot=10),
    shape("4-sided-cookie", 366, 256, 124, P, rot=45),
    shape("flower", 256, 366, 128, ON_PC, rot=0),
    shape("gem", 146, 256, 124, S, rot=-90),
])

# 2 — D-pad: plus with a cookie hub over a soft burst (round/square tension).
icon("02-dpad-burst", P, [
    shape("very-sunny", 256, 256, 440, "#7A63B8", rot=0),
    rrect(196, 96, 120, 320, 36, PC),
    rrect(96, 196, 320, 120, 36, PC),
    shape("4-sided-cookie", 256, 256, 96, T, rot=45),
])

# 3 — Controller set inside a 12-sided cookie.
ctrl = [
    shape("12-sided-cookie", 256, 256, 420, T, rot=0),
    # body + grips
    rrect(118, 190, 276, 132, 66, TC),
    shape("circle", 162, 316, 104, TC),
    shape("circle", 350, 316, 104, TC),
    # d-pad
    rrect(152, 230, 26, 68, 8, T), rrect(131, 251, 68, 26, 8, T),
    # buttons (shape mix)
    shape("4-sided-cookie", 342, 238, 36, P, rot=45),
    shape("circle", 374, 270, 30, ON_TC),
    shape("circle", 310, 270, 30, ON_TC),
    shape("4-sided-cookie", 342, 300, 36, P, rot=45),
]
icon("03-controller-cookie", TC, ctrl)

# 4 — Cartridge with asymmetric (inner-corner) shape, label = nested radius.
cart = ("M152 92 H336 Q380 92 380 136 V340 L340 420 H176 Q132 420 132 376 V112 Q132 92 152 92 Z")
icon("04-cartridge", SC, [
    f'<path d="{cart}" fill="{P}"/>',
    rrect(176, 112, 32, 12, 6, "#8D79C7"), rrect(240, 112, 32, 12, 6, "#8D79C7"), rrect(304, 112, 32, 12, 6, "#8D79C7"),
    rrect(160, 148, 192, 176, 28, PC),  # outer 44-ish, padding 16 -> 28
    shape("flower", 256, 236, 132, T, rot=0),
    *[rrect(176 + i * 28, 372, 14, 30, 7, "#8D79C7") for i in range(5)],
])

# 5 — 2.5D play stack (dark): layered shapes with offset for depth.
icon("05-play-stack-dark", D_SURF, [
    shape("very-sunny", 256, 256, 420, "#2B2930", rot=14),
    shape("9-sided-cookie", 270, 272, 300, D_T, rot=0),
    shape("9-sided-cookie", 250, 248, 300, D_P, rot=0),
    shape("triangle", 266, 248, 150, D_ON_P, rot=90),
])

# 6 — Pixel: the M3E pixel shapes as a retro/emulation nod.
icon("06-pixel-play", PC, [
    shape("pixel-circle", 256, 256, 360, P),
    shape("pixel-triangle", 270, 256, 150, PC),
])

# 7 — Morph row: square -> cookie -> circle reads as a "loading/booting" shape morph,
# sitting in a cartridge-slot pill.
icon("07-morph-slot", "#F7F2FA", [
    rrect(48, 180, 416, 152, 76, PC),
    shape("square", 132, 256, 92, S, rot=0),
    shape("6-sided-cookie", 256, 256, 104, P, rot=0),
    shape("triangle", 386, 256, 104, T, rot=90),
])

print("ok")

# 4b — Cartridge with the label window punched out as a hole (tile shows through), flower sitting on top.
icon("04b-cartridge-cutout", SC, [
    '<mask id="cut" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="512">'
    '<rect width="512" height="512" fill="#fff"/>'
    + rrect(160, 148, 192, 176, 28, "#000") + '</mask>',
    f'<path d="{cart}" fill="{P}" mask="url(#cut)"/>',
    rrect(176, 112, 32, 12, 6, "#8D79C7"), rrect(240, 112, 32, 12, 6, "#8D79C7"), rrect(304, 112, 32, 12, 6, "#8D79C7"),
    shape("flower", 256, 236, 132, T),
    *[rrect(176 + i * 28, 372, 14, 30, 7, "#8D79C7") for i in range(5)],
])

# 4c / 4d — Layer-friendly versions (e.g. for Liquid Glass): ridges and pins are holes
# in the cartridge body, with solid colour blocks on a lower layer showing through them.
RIDGES = [(176 + i * 64, 112, 32, 12, 6) for i in range(3)]
PINS = [(176 + i * 28, 372, 14, 30, 7) for i in range(5)]
ACCENT = "#8D79C7"


def layered_cartridge(name, label_hole):
    holes = "".join(rrect(*r, "#000") for r in RIDGES + PINS)
    if label_hole:
        holes += rrect(160, 148, 192, 176, 28, "#000")
    mask_id = f"holes-{name}"
    body = [
        '<g id="layer-blocks">',
        rrect(166, 104, 180, 28, 12, ACCENT),   # under ridges
        rrect(166, 362, 146, 50, 14, ACCENT),   # under pins
        '</g>',
        f'<mask id="{mask_id}" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="512">'
        f'<rect width="512" height="512" fill="#fff"/>{holes}</mask>',
        f'<g id="layer-body"><path d="{cart}" fill="{P}" mask="url(#{mask_id})"/></g>',
    ]
    if not label_hole:
        body.append(f'<g id="layer-label">{rrect(160, 148, 192, 176, 28, PC)}</g>')
    body.append(f'<g id="layer-flower">{shape("flower", 256, 236, 132, T)}</g>')
    icon(name, SC, body)


layered_cartridge("04c-cartridge-layered", label_hole=False)
layered_cartridge("04d-cartridge-cutout-layered", label_hole=True)
