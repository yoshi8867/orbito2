import random


def move(state_str):
    """
    Input : "[cell0,cell1,...,cell15]/white_remaining/black_remaining/my_color"
            cell values: "" (empty), "w" (white), "b" (black)
    Output: "src>dst/place"  or  "skip/place"
    """
    parts = state_str.split('/')
    cells = parts[0][1:-1].split(',')   # strip brackets, split 16 cells
    my_color = parts[3].strip()
    opp_color = 'b' if my_color == 'w' else 'w'

    empty_cells = [i for i, c in enumerate(cells) if c == '']
    opp_cells   = [i for i, c in enumerate(cells) if c == opp_color]

    # --- optional move: randomly pick an opponent piece that has an adjacent empty cell ---
    opt_src, opt_dst = None, None
    random.shuffle(opp_cells)
    for src in opp_cells:
        r, c = src // 4, src % 4
        candidates = []
        for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nr, nc = r + dr, c + dc
            if 0 <= nr < 4 and 0 <= nc < 4:
                dst = nr * 4 + nc
                if cells[dst] == '':
                    candidates.append(dst)
        if candidates:
            opt_src = src
            opt_dst = random.choice(candidates)
            break

    # --- placement: available cells after the optional move ---
    if opt_src is not None:
        updated = cells[:]
        updated[opt_dst] = updated[opt_src]
        updated[opt_src] = ''
        place_cells = [i for i, c in enumerate(updated) if c == '']
    else:
        place_cells = empty_cells[:]

    if not place_cells:
        place_cells = [0]

    place = random.choice(place_cells)

    if opt_src is not None:
        return f"{opt_src}>{opt_dst}/{place}"
    else:
        return f"skip/{place}"
