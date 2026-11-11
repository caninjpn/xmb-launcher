"""
Cut each icon out of the sheet onto a transparent background.

Alpha comes from luminance (white paper -> transparent, black ink -> opaque), which
keeps the original antialiasing instead of hard-keying it away. RGB is left untouched,
so the pixels are the source pixels; the launcher tints at draw time, and tinting
replaces RGB while keeping alpha, so nothing is lost either way.

Two post-passes on the boxes from find_icons.py:
  * split boxes tall enough to be two stacked icons at their widest internal gap
  * drop the "PLAYSTATION 3 icons" wordmark, which is just ink like anything else

Usage: python cut_icons.py <source.png> <boxes.json> <out_dir>
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PAD = 2              # transparent margin so filtering never clips an edge
SPLIT_MIN_H = 58     # only boxes this tall are candidates for being two icons
SPLIT_GAP = 4        # an empty band at least this tall marks the seam
SPLIT_MIN_PART = 18
TITLE_Y = 850        # the wordmark sits below this and nothing else does


def split_tall(mask, box):
    """Recursively split a box at its widest internal all-empty row band."""
    x0, y0, x1, y1 = box
    if (y1 - y0) < SPLIT_MIN_H:
        return [box]

    rows = mask[y0:y1, x0:x1].any(axis=1)
    bands, start = [], None
    for i, filled in enumerate(rows):
        if not filled and start is None:
            start = i
        elif filled and start is not None:
            bands.append((start, i))
            start = None
    bands = [b for b in bands if b[1] - b[0] >= SPLIT_GAP]
    if not bands:
        return [box]

    s, e = max(bands, key=lambda b: b[1] - b[0])
    cut = y0 + (s + e) // 2
    if (cut - y0) < SPLIT_MIN_PART or (y1 - cut) < SPLIT_MIN_PART:
        return [box]
    return split_tall(mask, [x0, y0, x1, cut]) + split_tall(mask, [x0, cut, x1, y1])


def tighten(mask, box):
    """Shrink a box back onto its ink after a split."""
    x0, y0, x1, y1 = box
    sub = mask[y0:y1, x0:x1]
    if not sub.any():
        return None
    ys = np.flatnonzero(sub.any(axis=1))
    xs = np.flatnonzero(sub.any(axis=0))
    return [x0 + int(xs[0]), y0 + int(ys[0]), x0 + int(xs[-1]) + 1, y0 + int(ys[-1]) + 1]


def main():
    src, boxes_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    out_dir.mkdir(parents=True, exist_ok=True)

    img = Image.open(src).convert("RGB")
    arr = np.asarray(img)
    mask = arr.min(axis=2) < 230
    lum = arr.astype(np.float32).mean(axis=2)
    alpha_full = np.clip(255.0 - lum, 0, 255).astype(np.uint8)

    boxes = json.loads(boxes_path.read_text())
    boxes = [b for b in boxes if b[1] < TITLE_Y]

    final = []
    for b in boxes:
        for part in split_tall(mask, b):
            t = tighten(mask, part)
            if t and (t[2] - t[0]) >= 7 and (t[3] - t[1]) >= 7:
                final.append(t)
    final.sort(key=lambda b: (round(b[0] / 60), b[1]))

    sheet_cols, cell = 10, 96
    rows = (len(final) + sheet_cols - 1) // sheet_cols
    sheet = Image.new("RGBA", (sheet_cols * cell, rows * cell), (30, 30, 34, 255))

    manifest = []
    for i, (x0, y0, x1, y1) in enumerate(final):
        w, h = x1 - x0, y1 - y0
        out = Image.new("RGBA", (w + PAD * 2, h + PAD * 2), (0, 0, 0, 0))
        rgb = Image.fromarray(arr[y0:y1, x0:x1])
        a = Image.fromarray(alpha_full[y0:y1, x0:x1])
        rgb.putalpha(a)
        out.paste(rgb, (PAD, PAD))
        out.save(out_dir / f"icon_{i:02d}.png")
        manifest.append({"index": i, "x": x0, "y": y0, "w": w, "h": h})

        # contact sheet, white so dark glyphs are visible against the dark cell
        thumb = out.copy()
        thumb.thumbnail((cell - 20, cell - 20), Image.LANCZOS)
        white = Image.new("RGBA", thumb.size, (255, 255, 255, 255))
        white.putalpha(thumb.getchannel("A"))
        cx = (i % sheet_cols) * cell + (cell - thumb.width) // 2
        cy = (i // sheet_cols) * cell + (cell - thumb.height) // 2
        sheet.paste(white, (cx, cy), white)

    sheet.save(out_dir / "contact_sheet.png")
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print(f"cut {len(final)} icons -> {out_dir}")
    for m in manifest:
        print(f"{m['index']:3d}  {m['w']:3d}x{m['h']:3d}  @({m['x']},{m['y']})")


if __name__ == "__main__":
    main()
