"""
Locate the individual icons on the PS3 icon sheet.

Many icons are made of several disconnected blobs (a wrench beside an object, a
'+' badge next to a face), so plain connected components would cut them in half.
This labels components first, then merges any whose bounding boxes sit within
MERGE_PX of each other, which re-assembles the multi-part glyphs.

Usage: python find_icons.py <source.png> <out_dir>
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

INK_MAX = 230      # a pixel counts as ink when its darkest channel is below this
MERGE_PX = 10      # bounding boxes closer than this belong to the same icon
MIN_AREA = 60      # drop specks
MIN_SIDE = 7


def components(mask: np.ndarray):
    """Row-run union-find labelling. Far faster than per-pixel BFS in Python."""
    h, w = mask.shape
    parent: dict[int, int] = {}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)

    runs_by_row = []
    next_label = 0
    for y in range(h):
        row = mask[y]
        if not row.any():
            runs_by_row.append([])
            continue
        # run starts/ends via edge detection on the boolean row
        padded = np.concatenate(([False], row, [False]))
        edges = np.diff(padded.astype(np.int8))
        starts = np.flatnonzero(edges == 1)
        ends = np.flatnonzero(edges == -1)
        runs = []
        for s, e in zip(starts, ends):
            parent[next_label] = next_label
            runs.append((int(s), int(e), next_label))
            next_label += 1
        # join to overlapping runs on the previous row
        for s, e, lab in runs:
            for ps, pe, plab in runs_by_row[-1] if runs_by_row else []:
                if s < pe and ps < e:
                    union(lab, plab)
        runs_by_row.append(runs)

    boxes: dict[int, list[int]] = {}
    for y, runs in enumerate(runs_by_row):
        for s, e, lab in runs:
            r = find(lab)
            b = boxes.get(r)
            if b is None:
                boxes[r] = [s, y, e, y + 1]
            else:
                b[0] = min(b[0], s)
                b[1] = min(b[1], y)
                b[2] = max(b[2], e)
                b[3] = max(b[3], y + 1)
    return list(boxes.values())


def merge(boxes, gap):
    """Repeatedly union boxes whose expanded rectangles intersect."""
    boxes = [list(b) for b in boxes]
    changed = True
    while changed:
        changed = False
        out = []
        for b in boxes:
            for o in out:
                if (b[0] - gap < o[2] and o[0] - gap < b[2]
                        and b[1] - gap < o[3] and o[1] - gap < b[3]):
                    o[0] = min(o[0], b[0]); o[1] = min(o[1], b[1])
                    o[2] = max(o[2], b[2]); o[3] = max(o[3], b[3])
                    changed = True
                    break
            else:
                out.append(b)
        boxes = out
    return boxes


def main():
    src = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)

    img = Image.open(src).convert("RGB")
    arr = np.asarray(img)
    mask = arr.min(axis=2) < INK_MAX

    raw = components(mask)
    merged = merge(raw, MERGE_PX)
    merged = [
        b for b in merged
        if (b[2] - b[0]) * (b[3] - b[1]) >= MIN_AREA
        and (b[2] - b[0]) >= MIN_SIDE and (b[3] - b[1]) >= MIN_SIDE
    ]
    # reading order: columns first, then down
    merged.sort(key=lambda b: (round(b[0] / 60), b[1]))

    preview = img.convert("RGB")
    d = ImageDraw.Draw(preview)
    for i, (x0, y0, x1, y1) in enumerate(merged):
        d.rectangle([x0 - 2, y0 - 2, x1 + 1, y1 + 1], outline=(220, 0, 0), width=2)
        d.text((x0 - 2, max(0, y0 - 13)), str(i), fill=(0, 90, 220))
    preview.save(out_dir / "preview.png")

    (out_dir / "boxes.json").write_text(json.dumps(merged))
    print(f"raw components : {len(raw)}")
    print(f"merged icons   : {len(merged)}")
    for i, (x0, y0, x1, y1) in enumerate(merged):
        print(f"{i:3d}  x={x0:4d} y={y0:4d}  {x1 - x0:3d}x{y1 - y0:3d}")


if __name__ == "__main__":
    main()
