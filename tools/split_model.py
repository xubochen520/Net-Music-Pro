"""Split the 2-block-tall yunpu_burner model into lower/upper halves and write
all block/item/blockstate resources under the target namespace.

Run:  python tools/split_model.py
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_NS = "netmusicpro"
TARGET_NS = "yunpumusic"
MODEL = "yunpu_burner"
HALF = 16.0

src_assets = ROOT / "assets" / SRC_NS
out_assets = ROOT / "mod" / "src" / "main" / "resources" / "assets" / TARGET_NS


def load_model() -> dict:
    raw = json.loads((src_assets / "models" / "block" / f"{MODEL}.json").read_text(encoding="utf-8"))
    # Blockbench export wraps the model; keep only the Minecraft model fields.
    return {
        "credit": raw.get("credit", ""),
        "texture_size": raw.get("texture_size", [512, 512]),
        "textures": {k: v.replace(f"{SRC_NS}:", f"{TARGET_NS}:") for k, v in raw["textures"].items()},
        "elements": raw["elements"],
        "display": raw.get("display", {}),
    }


def split_elements(elements: list[dict]) -> tuple[list[dict], list[dict]]:
    lower, upper = [], []
    for element in elements:
        y_from, y_to = element["from"][1], element["to"][1]
        if y_to <= HALF:
            lower.append(element)
        elif y_from >= HALF:
            shifted = json.loads(json.dumps(element))
            shifted["from"][1] -= HALF
            shifted["to"][1] -= HALF
            upper.append(shifted)
        else:
            # Straddles the boundary: clip it into both halves.
            low = json.loads(json.dumps(element))
            low["to"][1] = HALF
            high = json.loads(json.dumps(element))
            high["from"][1] = HALF
            high["to"][1] -= HALF
            high["from"][1] -= HALF
            lower.append(low)
            upper.append(high)
    return lower, upper


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    model = load_model()
    lower, upper = split_elements(model["elements"])
    print(f"elements: {len(model['elements'])} -> lower {len(lower)}, upper {len(upper)}")

    display = model["display"]
    gui_display = {"gui": display["gui"]} if "gui" in display else {}

    write_json(out_assets / "models" / "block" / f"{MODEL}.json", {
        "credit": model["credit"],
        "textures": model["textures"],
        "elements": lower,
        "display": display,
    })
    write_json(out_assets / "models" / "block" / f"{MODEL}_top.json", {
        "credit": model["credit"],
        "textures": model["textures"],
        "elements": upper,
    })
    write_json(out_assets / "models" / "item" / f"{MODEL}.json", {
        "parent": f"{TARGET_NS}:block/{MODEL}",
        "display": gui_display,
    })

    for name, mid in ((MODEL, f"{TARGET_NS}:block/{MODEL}"),
                      (f"{MODEL}_top", f"{TARGET_NS}:block/{MODEL}_top")):
        variants = {}
        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            variants[f"facing={facing}"] = {"model": mid, "y": rotation}
        write_json(out_assets / "blockstates" / f"{name}.json", {"variants": variants})

    tex_src = src_assets / "textures" / "block" / f"{MODEL}.png"
    tex_dst = out_assets / "textures" / "block" / f"{MODEL}.png"
    tex_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(tex_src, tex_dst)
    print(f"texture copied -> {tex_dst.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
