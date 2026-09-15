extends Node2D

# DEEPCORE - Professional Incremental Mining Prototype
# Inspired by Keep on Mining!

const SCREEN_W := 1280.0
const SCREEN_H := 720.0

# ------------------ BALANCE ------------------
var base_mine_power := 8.0
var mine_power_mult := 1.0
var area_radius := 95.0
var pickaxe_speed := 420.0
var rock_spawn_rate := 0.55
var max_rocks := 14
var ore_to_bar_ratio := 3.0

# ------------------ STATE ------------------
var bars := 0.0
var ore := 0.0
var xp := 0.0
var level := 1
var xp_to_next := 40.0

var mining_power_level := 0
var area_level := 0
var speed_level := 0
var value_level := 0

var message := "Passe o mouse sobre as rochas para minerar"
var message_timer := 3.5
var shake := 0.0
var pulse := 0.0

var mouse_pos := Vector2(SCREEN_W * 0.5, SCREEN_H * 0.45)
var hover_button := -1

# Collections
var rocks: Array[Dictionary] = []
var pickaxes: Array[Dictionary] = []
var particles: Array[Dictionary] = []
var float_texts: Array[Dictionary] = []
var dust: Array[Dictionary] = []

# Spawn control
var spawn_timer := 0.0

func _ready() -> void:
    randomize()
    for i in range(50):
        dust.append({
            "pos": Vector2(randf_range(80, 900), randf_range(180, 540)),
            "speed": randf_range(6.0, 16.0),
            "size": randf_range(1.0, 2.4),
            "phase": randf_range(0.0, TAU)
        })
    _spawn_initial_rocks()
    queue_redraw()

func _spawn_initial_rocks() -> void:
    for i in range(8):
        _spawn_rock()

func _process(delta: float) -> void:
    pulse += delta
    message_timer = maxf(0.0, message_timer - delta)
    shake = maxf(0.0, shake - delta * 9.0)

    var mp: Vector2 = get_viewport().get_mouse_position()
    if mp.x > 0 and mp.y > 0:
        mouse_pos = mp

    # Auto convert ore -> bars
    if ore >= ore_to_bar_ratio:
        var convert: float = floorf(ore / ore_to_bar_ratio)
        ore -= convert * ore_to_bar_ratio
        bars += convert

    # Spawn rocks
    spawn_timer -= delta
    if spawn_timer <= 0.0 and rocks.size() < max_rocks:
        _spawn_rock()
        spawn_timer = rock_spawn_rate * randf_range(0.7, 1.3)

    _update_rocks(delta)
    _update_pickaxes(delta)
    _update_particles(delta)
    _update_float_texts(delta)
    _update_dust(delta)
    _try_mine()

    queue_redraw()

func _update_dust(delta: float) -> void:
    for d in dust:
        d["pos"].y -= d["speed"] * delta
        d["pos"].x += sin(pulse * 0.8 + d["phase"]) * delta * 5.0
        if d["pos"].y < 170.0:
            d["pos"].y = 530.0
            d["pos"].x = randf_range(90, 880)

func _spawn_rock() -> void:
    var types := [
        {"name": "comum", "hp": 18.0, "max_hp": 18.0, "ore": 1.0, "color": Color("#8a9ba3"), "radius": 22.0, "weight": 60},
        {"name": "densa", "hp": 42.0, "max_hp": 42.0, "ore": 3.0, "color": Color("#6b8f9c"), "radius": 28.0, "weight": 28},
        {"name": "rica", "hp": 75.0, "max_hp": 75.0, "ore": 7.0, "color": Color("#c4a35a"), "radius": 32.0, "weight": 12},
    ]

    var total_w := 0
    for t in types:
        total_w += t["weight"]
    var r := randi() % total_w
    var chosen: Dictionary
    for t in types:
        r -= t["weight"]
        if r < 0:
            chosen = t.duplicate()
            break

    var scale := 1.0 + (level - 1) * 0.08
    chosen["hp"] *= scale
    chosen["max_hp"] = chosen["hp"]

    var pos := Vector2(
        randf_range(160.0, 780.0),
        randf_range(230.0, 480.0)
    )

    rocks.append({
        "pos": pos,
        "hp": chosen["hp"],
        "max_hp": chosen["max_hp"],
        "ore": chosen["ore"],
        "color": chosen["color"],
        "radius": chosen["radius"],
        "name": chosen["name"],
        "hit_flash": 0.0,
        "spawn_t": 0.0
    })

func _try_mine() -> void:
    var area := area_radius * (1.0 + area_level * 0.12)

    for rock in rocks:
        var dist := mouse_pos.distance_to(rock["pos"])
        if dist < area + rock["radius"] * 0.6:
            if randf() < 0.18 + speed_level * 0.03:
                _spawn_pickaxe(rock)

func _spawn_pickaxe(target: Dictionary) -> void:
    var angle := randf_range(0.0, TAU)
    var offset := Vector2(cos(angle), sin(angle)) * randf_range(30.0, 70.0)
    pickaxes.append({
        "pos": mouse_pos + offset,
        "target": target,
        "speed": pickaxe_speed * (1.0 + speed_level * 0.08),
        "life": 1.4,
        "damage": base_mine_power * mine_power_mult * (1.0 + mining_power_level * 0.35) * randf_range(0.85, 1.15),
        "angle": angle,
        "spin": randf_range(-12.0, 12.0)
    })

func _update_rocks(delta: float) -> void:
    for rock in rocks:
        rock["spawn_t"] = minf(1.0, rock["spawn_t"] + delta * 4.0)
        rock["hit_flash"] = maxf(0.0, rock["hit_flash"] - delta * 6.0)

func _update_pickaxes(delta: float) -> void:
    var to_remove: Array[int] = []
    for i in range(pickaxes.size()):
        var p: Dictionary = pickaxes[i]
        p["life"] -= delta
        p["angle"] += p["spin"] * delta

        var target_pos: Vector2 = p["target"]["pos"] if p["target"] is Dictionary else mouse_pos
        var dir: Vector2 = (target_pos - p["pos"]).normalized()
        p["pos"] += dir * p["speed"] * delta

        if p["pos"].distance_to(target_pos) < 18.0:
            _damage_rock(p["target"], p["damage"])
            _spawn_hit_particles(p["pos"])
            to_remove.append(i)
        elif p["life"] <= 0.0:
            to_remove.append(i)

    for i in range(to_remove.size() - 1, -1, -1):
        pickaxes.remove_at(to_remove[i])

func _damage_rock(rock: Dictionary, dmg: float) -> void:
    if not rocks.has(rock):
        return
    rock["hp"] -= dmg
    rock["hit_flash"] = 1.0
    shake = maxf(shake, 0.35)

    _spawn_float_text(rock["pos"] + Vector2(0, -20), "-%.0f" % dmg, Color("#e8d48b"))

    if rock["hp"] <= 0.0:
        _break_rock(rock)

func _break_rock(rock: Dictionary) -> void:
    var gained_ore: float = rock["ore"] * (1.0 + value_level * 0.25)
    ore += gained_ore
    xp += gained_ore * 2.8

    _spawn_break_particles(rock["pos"], rock["color"])
    _spawn_float_text(rock["pos"], "+%.1f ore" % gained_ore, Color("#7dcea0"))
    shake = 0.7

    rocks.erase(rock)

    while xp >= xp_to_next:
        xp -= xp_to_next
        level += 1
        xp_to_next = 40.0 + level * 18.0
        message = "LEVEL UP! Você alcançou o nível %d" % level
        message_timer = 2.8
        bars += level * 2

func _update_particles(delta: float) -> void:
    for p in particles:
        p["pos"] += p["vel"] * delta
        p["vel"].y += 280.0 * delta
        p["life"] -= delta
    particles = particles.filter(func(p: Dictionary): return p["life"] > 0.0)

func _update_float_texts(delta: float) -> void:
    for t in float_texts:
        t["pos"].y -= 38.0 * delta
        t["life"] -= delta
    float_texts = float_texts.filter(func(t: Dictionary): return t["life"] > 0.0)

func _spawn_hit_particles(pos: Vector2) -> void:
    for i in range(6):
        var a := randf() * TAU
        particles.append({
            "pos": pos,
            "vel": Vector2(cos(a), sin(a)) * randf_range(60, 160),
            "life": randf_range(0.2, 0.45),
            "size": randf_range(1.5, 3.0),
            "color": Color("#d4b56a")
        })

func _spawn_break_particles(pos: Vector2, col: Color) -> void:
    for i in range(18):
        var a := randf() * TAU
        particles.append({
            "pos": pos,
            "vel": Vector2(cos(a), sin(a)) * randf_range(90, 260),
            "life": randf_range(0.35, 0.8),
            "size": randf_range(2.0, 5.0),
            "color": col
        })

func _spawn_float_text(pos: Vector2, text: String, col: Color) -> void:
    float_texts.append({
        "pos": pos,
        "text": text,
        "life": 0.9,
        "color": col
    })

# ------------------ INPUT ------------------
func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventMouseMotion:
        hover_button = _button_at(event.position)
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
        _handle_pointer_press(event.position)
    elif event is InputEventScreenTouch and event.pressed:
        _handle_pointer_press(event.position)

func _handle_pointer_press(p: Vector2) -> void:
    mouse_pos = p
    var b := _button_at(p)
    match b:
        0: _buy_power()
        1: _buy_area()
        2: _buy_speed()
        3: _buy_value()

func _button_at(p: Vector2) -> int:
    if Rect2(40, 600, 220, 72).has_point(p): return 0
    if Rect2(280, 600, 220, 72).has_point(p): return 1
    if Rect2(520, 600, 220, 72).has_point(p): return 2
    if Rect2(760, 600, 220, 72).has_point(p): return 3
    return -1

func _buy_power() -> void:
    var cost := 12.0 * pow(1.55, mining_power_level)
    if bars < cost:
        message = "Faltam %.0f lingotes" % (cost - bars)
        message_timer = 1.4
        return
    bars -= cost
    mining_power_level += 1
    message = "Poder de mineração ↑ Nível %d" % mining_power_level
    message_timer = 1.6

func _buy_area() -> void:
    var cost := 18.0 * pow(1.6, area_level)
    if bars < cost:
        message = "Faltam %.0f lingotes" % (cost - bars)
        message_timer = 1.4
        return
    bars -= cost
    area_level += 1
    message = "Área de mineração ↑ Nível %d" % area_level
    message_timer = 1.6

func _buy_speed() -> void:
    var cost := 15.0 * pow(1.58, speed_level)
    if bars < cost:
        message = "Faltam %.0f lingotes" % (cost - bars)
        message_timer = 1.4
        return
    bars -= cost
    speed_level += 1
    message = "Velocidade de picaretas ↑ Nível %d" % speed_level
    message_timer = 1.6

func _buy_value() -> void:
    var cost := 22.0 * pow(1.65, value_level)
    if bars < cost:
        message = "Faltam %.0f lingotes" % (cost - bars)
        message_timer = 1.4
        return
    bars -= cost
    value_level += 1
    message = "Valor do minério ↑ Nível %d" % value_level
    message_timer = 1.6

# ------------------ DRAW ------------------
func _draw() -> void:
    var sh := Vector2.ZERO
    if shake > 0.0:
        sh = Vector2(randf_range(-2.5, 2.5), randf_range(-2.5, 2.5)) * shake

    draw_rect(Rect2(0, 0, SCREEN_W, SCREEN_H), Color("#071015"))
    draw_rect(Rect2(0, 0, SCREEN_W, 78), Color("#0b161c"))
    draw_rect(Rect2(0, 76, SCREEN_W, 2), Color("#1e353f"))
    draw_string(ThemeDB.fallback_font, Vector2(42, 42), "DEEPCORE", HORIZONTAL_ALIGNMENT_LEFT, -1, 28, Color("#e8f0f2"))
    draw_string(ThemeDB.fallback_font, Vector2(210, 40), "MINING PROTOCOL  //  PROTOTYPE", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#5f7a84"))

    draw_string(ThemeDB.fallback_font, Vector2(980, 32), "LINGOTES", HORIZONTAL_ALIGNMENT_LEFT, -1, 11, Color("#6a838c"))
    draw_string(ThemeDB.fallback_font, Vector2(980, 54), "%.0f" % bars, HORIZONTAL_ALIGNMENT_LEFT, -1, 22, Color("#e6c36a"))
    draw_string(ThemeDB.fallback_font, Vector2(1120, 32), "NÍVEL %d" % level, HORIZONTAL_ALIGNMENT_LEFT, -1, 11, Color("#6a838c"))
    draw_string(ThemeDB.fallback_font, Vector2(1120, 54), "%.0f / %.0f XP" % [xp, xp_to_next], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#9ab8c0"))

    draw_style_box(_make_box(Color("#0c171d"), Color("#1a303a")), Rect2(40, 100, 900, 470))

    for d in dust:
        draw_circle(d["pos"] + sh, d["size"], Color(0.45, 0.55, 0.58, 0.18))

    for rock in rocks:
        var t: float = rock["spawn_t"]
        var r: float = rock["radius"] * t
        var pos: Vector2 = rock["pos"] + sh
        var flash: float = rock["hit_flash"]

        draw_circle(pos, r + 8.0, Color(rock["color"].r, rock["color"].g, rock["color"].b, 0.08 + flash * 0.12))
        var body_col: Color = rock["color"].lightened(flash * 0.35)
        draw_circle(pos, r, body_col)
        draw_circle(pos, r * 0.72, body_col.darkened(0.25))
        var hp_ratio: float = clampf(rock["hp"] / rock["max_hp"], 0.0, 1.0)
        draw_rect(Rect2(pos.x - 18, pos.y + r + 6, 36, 4), Color("#1a2a31"))
        draw_rect(Rect2(pos.x - 18, pos.y + r + 6, 36 * hp_ratio, 4), Color("#c48b43"))

    var area := area_radius * (1.0 + area_level * 0.12)
    draw_arc(mouse_pos + sh, area, 0.0, TAU, 64, Color(0.35, 0.75, 0.7, 0.25), 2.5)
    draw_circle(mouse_pos + sh, area, Color(0.2, 0.55, 0.52, 0.06))
    draw_circle(mouse_pos + sh, 6.0 + sin(pulse * 4.0) * 1.5, Color("#5ecfba"))

    for p in pickaxes:
        var pos: Vector2 = p["pos"] + sh
        var ang: float = p["angle"]
        var tip := pos + Vector2(cos(ang), sin(ang)) * 14.0
        var back := pos - Vector2(cos(ang), sin(ang)) * 10.0
        draw_line(back, tip, Color("#b8894f"), 4.0)
        draw_line(back, tip, Color("#e8c98a"), 1.8)
        draw_circle(tip, 3.2, Color("#d0d6d4"))

    for p in particles:
        draw_circle(p["pos"] + sh, p["size"], p["color"])

    for t in float_texts:
        var a := clampf(t["life"] * 1.4, 0.0, 1.0)
        var col: Color = t["color"]
        col.a = a
        draw_string(ThemeDB.fallback_font, t["pos"] + sh, t["text"], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, col)

    draw_style_box(_make_box(Color("#0c171d"), Color("#1a303a")), Rect2(960, 100, 280, 470))
    draw_string(ThemeDB.fallback_font, Vector2(985, 135), "MELHORIAS", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#8aa3ac"))

    _draw_upgrade_card(Vector2(980, 160), "PODER", mining_power_level, "Dano das picaretas")
    _draw_upgrade_card(Vector2(980, 250), "ÁREA", area_level, "Tamanho da zona")
    _draw_upgrade_card(Vector2(980, 340), "VELOCIDADE", speed_level, "Mais picaretas")
    _draw_upgrade_card(Vector2(980, 430), "VALOR", value_level, "Minério por rocha")

    _draw_button(Rect2(40, 600, 220, 72), "PODER", "%.0f lingotes" % (12.0 * pow(1.55, mining_power_level)), 0)
    _draw_button(Rect2(280, 600, 220, 72), "ÁREA", "%.0f lingotes" % (18.0 * pow(1.6, area_level)), 1)
    _draw_button(Rect2(520, 600, 220, 72), "VELOCIDADE", "%.0f lingotes" % (15.0 * pow(1.58, speed_level)), 2)
    _draw_button(Rect2(760, 600, 220, 72), "VALOR", "%.0f lingotes" % (22.0 * pow(1.65, value_level)), 3)

    if message_timer > 0.0:
        draw_string(ThemeDB.fallback_font, Vector2(40, 690), message, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#e6c36a"))

    draw_string(ThemeDB.fallback_font, Vector2(1000, 690), "Toque nas rochas para minerar", HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color("#4d676f"))

func _draw_upgrade_card(pos: Vector2, title: String, lvl: int, desc: String) -> void:
    draw_style_box(_make_box(Color("#0a1419"), Color("#1c323c")), Rect2(pos.x, pos.y, 240, 78))
    draw_string(ThemeDB.fallback_font, pos + Vector2(14, 24), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#7a939c"))
    draw_string(ThemeDB.fallback_font, pos + Vector2(14, 48), "Nível %d" % lvl, HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("#e4eef0"))
    draw_string(ThemeDB.fallback_font, pos + Vector2(120, 48), desc, HORIZONTAL_ALIGNMENT_LEFT, 110, 11, Color("#6f8a93"))

func _draw_button(rect: Rect2, title: String, cost: String, index: int) -> void:
    var hovered := hover_button == index
    var bg := Color("#1a3038") if hovered else Color("#13242c")
    var border := Color("#3d6a6a") if hovered else Color("#243c46")
    draw_style_box(_make_box(bg, border), rect)
    if hovered:
        draw_rect(Rect2(rect.position + Vector2(6, 5), Vector2(rect.size.x - 12, 2)), Color("#5ecfba"))
    draw_string(ThemeDB.fallback_font, rect.position + Vector2(16, 28), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#e8f0f2"))
    draw_string(ThemeDB.fallback_font, rect.position + Vector2(16, 52), cost, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color("#8eb8b0"))

func _make_box(bg: Color, border: Color) -> StyleBoxFlat:
    var b := StyleBoxFlat.new()
    b.bg_color = bg
    b.border_color = border
    b.set_border_width_all(1)
    b.set_corner_radius_all(8)
    return b
