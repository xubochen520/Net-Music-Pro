"""Generate the Cloudscore Disc Studio Blockbench and Minecraft assets."""

from __future__ import annotations

import base64
import io
import json
import math
import random
import uuid
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets" / "netmusicpro"
MODEL_NAME = "yunpu_burner"
TEX_SIZE = 512
SCALE = 4
random.seed(15)

PALETTE = {
    "wood": (69, 43, 31),
    "wood_dark": (42, 29, 27),
    "wood_light": (95, 58, 36),
    "copper": (169, 65, 29),
    "copper_dark": (116, 37, 21),
    "brass": (214, 150, 55),
    "brass_light": (251, 208, 105),
    "metal": (48, 42, 42),
    "metal_light": (74, 63, 58),
    "glass": (27, 61, 66),
    "screen": (17, 43, 48),
    "cyan": (99, 207, 210),
    "red": (193, 72, 53),
    "green": (103, 190, 122),
    "blue": (99, 139, 218),
    "black": (19, 18, 21),
}


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color)


def texture_patch(material, face, width, height):
    w = max(2, round(width * SCALE))
    h = max(2, round(height * SCALE))
    base = PALETTE.get(material, PALETTE["wood"])
    face_shade = {"up": 15, "down": -26, "north": 3, "south": -11, "east": -18, "west": -7}[face]
    im = Image.new("RGB", (w, h), shade(base, face_shade))
    d = ImageDraw.Draw(im)

    if material.startswith("wood"):
        for y in range(1, h - 1, 4):
            col = shade(base, face_shade + random.choice((-11, -5, 8, 13)))
            d.line((1, y, w - 2, y), fill=col)
            if w > 12:
                x = random.randrange(2, w - 3)
                d.line((x, y, min(w - 2, x + random.randrange(3, 10)), y), fill=shade(col, 12))
        d.rectangle((0, 0, w - 1, h - 1), outline=shade(base, face_shade - 14))
    elif material in ("copper", "copper_dark", "brass", "brass_light", "metal", "metal_light"):
        if w > 3 and h > 3:
            d.line((0, 0, w - 1, 0), fill=shade(base, face_shade + 27))
            d.line((0, 0, 0, h - 1), fill=shade(base, face_shade + 16))
            d.line((0, h - 1, w - 1, h - 1), fill=shade(base, face_shade - 24))
            d.line((w - 1, 0, w - 1, h - 1), fill=shade(base, face_shade - 20))
            if w > 18 and h > 8:
                for x in (3, w - 4):
                    d.point((x, 3), fill=shade(base, face_shade + 37))
    elif material == "screen" and face == "north":
        d.rectangle((0, 0, w - 1, h - 1), fill=(11, 28, 32), outline=(57, 119, 112), width=2)
        # Three linked services, signal bars, and a tiny music waveform.
        for i, color in enumerate(((205, 86, 64), (102, 186, 113), (108, 148, 221))):
            x = 6 + i * 13
            d.rectangle((x, 5, x + 7, 10), fill=color)
            d.rectangle((x + 2, 7, x + 5, 8), fill=(249, 226, 175))
        d.line((6, 16, w - 7, 16), fill=(61, 112, 107), width=1)
        points = [(6, h - 10), (12, h - 10), (15, h - 15), (18, h - 6), (22, h - 17), (26, h - 8), (31, h - 10), (w - 7, h - 10)]
        d.line(points, fill=PALETTE["cyan"], width=2)
        d.rectangle((w - 13, 5, w - 7, 10), outline=PALETTE["brass_light"])
    elif material == "platter" and face == "up":
        d.rectangle((0, 0, w - 1, h - 1), fill=(44, 34, 34), outline=(217, 142, 49), width=2)
        margin = max(3, min(w, h) // 10)
        d.ellipse((margin, margin, w - margin - 1, h - margin - 1), fill=(22, 21, 24), outline=(221, 157, 62), width=2)
        d.ellipse((margin + 5, margin + 5, w - margin - 6, h - margin - 6), outline=(82, 70, 61), width=1)
        cx, cy = w // 2, h // 2
        d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=(195, 76, 42), outline=(246, 194, 92))
        d.ellipse((cx - 1, cy - 1, cx + 1, cy + 1), fill=(29, 23, 23))
    elif material == "slot" and face == "north":
        d.rectangle((0, 0, w - 1, h - 1), fill=(38, 28, 28), outline=(205, 129, 47), width=2)
        d.rectangle((4, h // 2 - 2, w - 5, h // 2 + 1), fill=(7, 8, 10))
        d.line((6, h // 2 + 3, w - 7, h // 2 + 3), fill=(113, 61, 32))
    elif material == "vents" and face in ("east", "west", "south") and w >= 10:
        for y in range(4, h - 3, 6):
            d.rectangle((4, y, w - 5, y + 2), fill=(19, 18, 20))
            d.line((5, y + 3, w - 6, y + 3), fill=(123, 57, 31))
    elif material == "lights" and face == "north":
        for i, col in enumerate((PALETTE["red"], PALETTE["green"], PALETTE["blue"])):
            x = (i + 1) * w // 4
            d.rectangle((x - 2, h // 2 - 2, x + 2, h // 2 + 2), fill=col, outline=(248, 202, 116))
    return im


# Each piece is a native Blockbench cube. North is the front of the machine.
PARTS = []


def part(name, fr, to, material, *, front=None, top=None, sides=None):
    PARTS.append({"name": name, "from": fr, "to": to, "material": material,
                  "front": front, "top": top, "sides": sides})


# Original machine proportions: one block wide, two blocks tall.
part("底座 · 深铜踢脚", [0, 0, 0], [16, 2, 16], "copper_dark")
part("主机 · 深色木柜", [1, 2, 1], [15, 14, 15], "wood")
part("机柜左立柱", [0, 2, 0], [2, 14, 2], "wood_dark")
part("机柜右立柱", [14, 2, 0], [16, 14, 2], "wood_dark")
part("机柜左后柱", [0, 2, 14], [2, 14, 16], "wood_dark")
part("机柜右后柱", [14, 2, 14], [16, 14, 16], "wood_dark")
part("前面板 · 铜框", [2, 4, 0], [14, 13, 1], "copper")
part("唱片出口", [3, 8, -0.18], [13, 11, 0], "metal", front="slot")
part("底部控制板", [3, 4, -0.15], [13, 7, 0], "metal", front="lights")
part("台面 · 铜边", [0, 14, 0], [16, 16, 16], "copper")
part("台面 · 木质芯", [1, 16, 1], [15, 16.7, 15], "wood_light")

# Visible disc deck and the stylus arm above it.
part("刻录盘 · 金属底", [4, 16.7, 2], [12, 17.4, 10], "brass")
part("刻录盘 · 黑胶盘", [4.5, 17.4, 2.5], [11.5, 17.7, 9.5], "metal", top="platter")
part("刻录轴", [7.4, 17.7, 5.4], [8.6, 18.7, 6.6], "brass_light")
part("唱针座", [12, 16.7, 8], [14, 19.7, 10], "copper_dark")
part("唱针臂 · 纵", [12.7, 19.3, 5], [13.3, 20, 9], "brass")
part("唱针臂 · 横", [9, 19.3, 4.8], [13, 20, 5.4], "brass")
part("唱针头", [8.7, 18, 4.4], [9.6, 19.5, 5.6], "copper")

# A raised login console echoes the original burner's open upper frame.
part("后方木质支架", [1, 16, 12], [15, 29, 16], "wood_dark")
part("左侧铜框", [0, 16, 11], [2, 30, 16], "copper_dark")
part("右侧铜框", [14, 16, 11], [16, 30, 16], "copper_dark")
part("下横梁", [2, 18, 10.4], [14, 20, 13], "copper")
part("上横梁", [2, 28, 10.4], [14, 30, 13], "copper")
part("终端 · 黄铜边框", [2, 20, 10.3], [14, 28, 12.5], "brass")
part("终端 · 黑色面框", [2.7, 20.7, 10.12], [13.3, 27.3, 10.3], "metal")
part("多平台登录屏", [3.2, 21.2, 10.04], [12.8, 26.8, 10.12], "glass", front="screen")
part("屏幕下缘状态灯", [3.2, 20.4, 9.95], [12.8, 21.1, 10.1], "metal", front="lights")
part("顶盖 · 铜缘", [0, 30, 10], [16, 32, 16], "copper")
part("顶盖 · 木芯", [1, 30.4, 10.5], [15, 31.6, 15.5], "wood_light")

# Connection sockets and brass fasteners give side and rear views purpose.
part("左侧通风口", [-0.13, 5, 4], [0, 11, 12], "metal", sides="vents")
part("右侧通风口", [16, 5, 4], [16.13, 11, 12], "metal", sides="vents")
part("后方接口", [5, 5, 15.8], [11, 10, 16], "metal", sides="vents")
for x in (2.5, 12.5):
    part(f"前台铆钉{x}", [x, 14.3, -0.1], [x + 1, 15.3, 0], "brass_light")


def dimensions(p, face):
    x = p["to"][0] - p["from"][0]
    y = p["to"][1] - p["from"][1]
    z = p["to"][2] - p["from"][2]
    if face in ("north", "south"):
        return x, y
    if face in ("east", "west"):
        return z, y
    return x, z


def face_material(p, face):
    if face == "north" and p["front"]:
        return p["front"]
    if face == "up" and p["top"]:
        return p["top"]
    if face in ("east", "west", "south") and p["sides"]:
        return p["sides"]
    return p["material"]


atlas = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
cursor_x = cursor_y = row_height = 0


def put_patch(patch):
    global cursor_x, cursor_y, row_height
    if cursor_x + patch.width + 1 > TEX_SIZE:
        cursor_x = 0
        cursor_y += row_height + 1
        row_height = 0
    if cursor_y + patch.height + 1 > TEX_SIZE:
        raise ValueError("Texture atlas full")
    pos = (cursor_x, cursor_y)
    atlas.paste(patch, pos)
    cursor_x += patch.width + 1
    row_height = max(row_height, patch.height)
    return [pos[0], pos[1], pos[0] + patch.width, pos[1] + patch.height]


bb_elements = []
mc_elements = []
groups = {group: [] for group in ("机柜", "刻录机构", "登录终端", "接口与细节")}
for index, p in enumerate(PARTS):
    uid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"netmusicpro/yunpu_burner/{index}/{p['name']}"))
    bb_faces = {}
    mc_faces = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        w, h = dimensions(p, face)
        patch = texture_patch(face_material(p, face), face, w, h)
        uv = put_patch(patch)
        bb_faces[face] = {"uv": uv, "texture": 0}
        mc_faces[face] = {"uv": [round(v * 16 / TEX_SIZE, 5) for v in uv], "texture": "#0"}
    bb_elements.append({
        "name": p["name"], "type": "cube", "uuid": uid, "box_uv": False,
        "from": p["from"], "to": p["to"], "origin": [8, 16, 8],
        "color": index % 8, "faces": bb_faces,
    })
    mc_elements.append({"name": p["name"], "from": p["from"], "to": p["to"], "faces": mc_faces})
    group = "机柜" if index < 11 else "刻录机构" if index < 18 else "登录终端" if index < 29 else "接口与细节"
    groups[group].append(uid)


texture_path = ASSETS / "textures" / "block" / f"{MODEL_NAME}.png"
texture_path.parent.mkdir(parents=True, exist_ok=True)
atlas.save(texture_path)

buf = io.BytesIO()
atlas.save(buf, format="PNG")
bbmodel = {
    "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
    "name": "云谱刻录台 · Yunpu Disc Studio",
    "model_identifier": MODEL_NAME,
    "resolution": {"width": TEX_SIZE, "height": TEX_SIZE},
    "elements": bb_elements,
    "outliner": [{
        "name": name, "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"netmusicpro/{name}")),
        "origin": [8, 16, 8], "rotation": [0, 0, 0], "color": i,
        "children": children, "isOpen": True,
    } for i, (name, children) in enumerate(groups.items())],
    "textures": [{
        "name": texture_path.name, "id": "0", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "netmusicpro/yunpu_texture")),
        "relative_path": f"assets/netmusicpro/textures/block/{MODEL_NAME}.png",
        "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii"),
    }],
}
(ROOT / f"{MODEL_NAME}.bbmodel").write_text(json.dumps(bbmodel, ensure_ascii=False, indent=2), encoding="utf-8")

model_dir = ASSETS / "models" / "block"
model_dir.mkdir(parents=True, exist_ok=True)
mc_model = {
    "credit": "Original model by Net Music Pro; inspired by Net Music's copper and dark wood palette",
    "texture_size": [TEX_SIZE, TEX_SIZE],
    "textures": {"0": f"netmusicpro:block/{MODEL_NAME}", "particle": f"netmusicpro:block/{MODEL_NAME}"},
    "elements": mc_elements,
    "display": {"gui": {"rotation": [25, 45, 0], "translation": [0, -6, 0], "scale": [0.5, 0.5, 0.5]}},
}
(model_dir / f"{MODEL_NAME}.json").write_text(json.dumps(mc_model, ensure_ascii=False, indent=2), encoding="utf-8")
item_dir = ASSETS / "models" / "item"
item_dir.mkdir(parents=True, exist_ok=True)
(item_dir / f"{MODEL_NAME}.json").write_text(json.dumps({"parent": f"netmusicpro:block/{MODEL_NAME}"}, indent=2), encoding="utf-8")
state_dir = ASSETS / "blockstates"
state_dir.mkdir(parents=True, exist_ok=True)
states = {f"facing={direction}": {"model": f"netmusicpro:block/{MODEL_NAME}", "y": angle}
          for direction, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
(state_dir / f"{MODEL_NAME}.json").write_text(json.dumps({"variants": states}, indent=2), encoding="utf-8")


def project(point):
    x, y, z = point
    return (390 + (x + z - 16) * 14.5, 540 - y * 14 + (x - z) * 7)


def render_preview():
    # Isometric software render with a depth buffer. The actual asset is the bbmodel.
    canvas = Image.new("RGB", (800, 720), (27, 29, 33))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((202, 575, 640, 673), fill=(17, 18, 21))
    pixels = np.asarray(canvas).copy()
    depth_buffer = np.full((720, 800), np.inf, dtype=np.float32)
    atlas_rgb = np.asarray(atlas.convert("RGB"))
    for p, element in zip(PARTS, bb_elements):
        x0, y0, z0 = p["from"]
        x1, y1, z1 = p["to"]
        vertices = {
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
            "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
        }
        for face, points in vertices.items():
            uv = element["faces"][face]["uv"]
            projected = [project(v) for v in points]
            a, b, _, d = projected
            e1 = (b[0] - a[0], b[1] - a[1])
            e2 = (d[0] - a[0], d[1] - a[1])
            determinant = e1[0] * e2[1] - e1[1] * e2[0]
            if abs(determinant) < 0.01:
                continue
            xmin = max(0, int(min(pt[0] for pt in projected)))
            xmax = min(799, int(max(pt[0] for pt in projected)) + 1)
            ymin = max(0, int(min(pt[1] for pt in projected)))
            ymax = min(719, int(max(pt[1] for pt in projected)) + 1)
            if xmin >= xmax or ymin >= ymax:
                continue
            yy, xx = np.mgrid[ymin:ymax + 1, xmin:xmax + 1]
            dx, dy = xx + .5 - a[0], yy + .5 - a[1]
            u = (dx * e2[1] - dy * e2[0]) / determinant
            v = (dy * e1[0] - dx * e1[1]) / determinant
            inside = (u >= 0) & (u <= 1) & (v >= 0) & (v <= 1)
            p0, p1, _, p3 = points
            depth = (p0[2] - p0[0]) + u * ((p1[2] - p1[0]) - (p0[2] - p0[0])) + v * ((p3[2] - p3[0]) - (p0[2] - p0[0]))
            current_depth = depth_buffer[ymin:ymax + 1, xmin:xmax + 1]
            visible = inside & (depth < current_depth)
            if not np.any(visible):
                continue
            tu = np.clip((u * (uv[2] - uv[0] - 1)).astype(int), 0, uv[2] - uv[0] - 1)
            tv_ratio = 1 - v if face in ("north", "east") else v
            tv = np.clip((tv_ratio * (uv[3] - uv[1] - 1)).astype(int), 0, uv[3] - uv[1] - 1)
            sample = atlas_rgb[uv[1] + tv, uv[0] + tu]
            target = pixels[ymin:ymax + 1, xmin:xmax + 1]
            target[visible] = sample[visible]
            current_depth[visible] = depth[visible]
    canvas = Image.fromarray(pixels)
    draw = ImageDraw.Draw(canvas)
    title = "YUNPU DISC STUDIO"
    font = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 25) if Path("C:/Windows/Fonts/consolab.ttf").exists() else ImageFont.load_default()
    draw.text((42, 42), title, font=font, fill=(238, 205, 143))
    draw.text((43, 82), "Net Music Pro  /  16 x 16 x 32", fill=(154, 169, 169))
    canvas.save(ROOT / "yunpu_burner_preview.png")


render_preview()
print(f"Generated {len(PARTS)} cubes, {texture_path}, and Blockbench project")
