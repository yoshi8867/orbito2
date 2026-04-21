import random

_ROTATION = [4, 0, 1, 2, 8, 9, 5, 3, 12, 10, 6, 7, 13, 14, 15, 11]

_LINES = [
    [0, 1, 2, 3], [4, 5, 6, 7], [8, 9, 10, 11], [12, 13, 14, 15],
    [0, 4, 8, 12], [1, 5, 9, 13], [2, 6, 10, 14], [3, 7, 11, 15],
    [0, 5, 10, 15], [3, 6, 9, 12]
]


def _rotate(cells):
    out = [''] * 16
    for src in range(16):
        out[_ROTATION[src]] = cells[src]
    return out


def _has_win(cells, color):
    return any(all(cells[i] == color for i in line) for line in _LINES)


def _threat_count(cells, color):
    count = 0
    for line in _LINES:
        vals = [cells[i] for i in line]
        if vals.count(color) == 3 and vals.count('') == 1:
            count += 1
    return count


def _score(cells, my, opp):
    if _has_win(cells, my):
        return 100000
    if _has_win(cells, opp):
        return -100000
    s = _threat_count(cells, my) * 100 - _threat_count(cells, opp) * 150
    for i in (5, 6, 9, 10):
        if cells[i] == my:
            s += 5
        elif cells[i] == opp:
            s -= 3
    return s


def move(state_str):
    """
    Input : "[cell0,cell1,...,cell15]/white_remaining/black_remaining/my_color"
            cell values: "" (empty), "w" (white), "b" (black)
    Output: "src>dst/place"  or  "skip/place"
    """
    parts = state_str.split('/')
    cells = parts[0][1:-1].split(',')
    my_color = parts[3].strip()
    opp_color = 'b' if my_color == 'w' else 'w'

    empty_cells = [i for i, c in enumerate(cells) if c == '']
    opp_cells   = [i for i, c in enumerate(cells) if c == opp_color]

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


def smart_move(state_str):
    """1-ply lookahead: picks the (optional_move, placement) that maximises
    the board score after rotation."""
    parts = state_str.split('/')
    cells = parts[0][1:-1].split(',')
    my_color = parts[3].strip()
    opp_color = 'b' if my_color == 'w' else 'w'

    empty_cells = [i for i, c in enumerate(cells) if c == '']
    opp_cells   = [i for i, c in enumerate(cells) if c == opp_color]

    best_score = None
    best_opt   = None
    best_place = None

    def evaluate(board_after_opt, opt):
        nonlocal best_score, best_opt, best_place
        place_cells = [i for i, c in enumerate(board_after_opt) if c == '']
        if not place_cells:
            return
        for place in place_cells:
            candidate = board_after_opt[:]
            candidate[place] = my_color
            after_rot = _rotate(candidate)
            score = _score(after_rot, my_color, opp_color)
            if best_score is None or score > best_score:
                best_score = score
                best_opt   = opt
                best_place = place

    # skip optional move
    evaluate(cells, None)

    # try moving each opponent piece
    for src in opp_cells:
        r, c = src // 4, src % 4
        for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nr, nc = r + dr, c + dc
            if 0 <= nr < 4 and 0 <= nc < 4:
                dst = nr * 4 + nc
                if cells[dst] == '':
                    after_opt = cells[:]
                    after_opt[dst] = after_opt[src]
                    after_opt[src] = ''
                    evaluate(after_opt, (src, dst))

    if best_place is None:
        return move(state_str)

    if best_opt is None:
        return f"skip/{best_place}"
    else:
        return f"{best_opt[0]}>{best_opt[1]}/{best_place}"
