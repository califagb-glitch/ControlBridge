extends Node2D

var ore := 0.0
var credits := 25.0
var depth := 12
var pickaxe_level := 1
var drill_level := 0
var mine_power := 1.0
var ore_value := 2.0
var mining_cooldown := 0.0
var message := "A mina está esperando."
var message_time := 0.0
var hit_flash := 0.0
var camera_shake := 0.0
var pulse := 0.0
var particles: Array[Dictionary] = []
var ore_chunks: Array[Dictionary] = []

func _ready() -> void:
    randomize()
    queue_redraw()

func _process(delta: float) -> void:
    mining_cooldown = maxf(0.0, mining_cooldown - delta)
    message_time = maxf(0.0, message_time - delta)
    hit_flash = maxf(0.0, hit_flash - delta * 4.0)
    camera_shake = maxf(0.0, camera_shake - delta * 7.0)
    pulse += delta
    if drill_level > 0:
        ore += (0.35 * drill_level) * delta
    for p: Dictionary in particles:
        p["pos"] = p["pos"] + p["vel"] * delta
        p["vel"].y += 260.0 * delta
        p["life"] -= delta
    particles = particles.filter(func(p: Dictionary): return p["life"] > 0.0)
    for c: Dictionary in ore_chunks:
        c["pos"] = c["pos"] + c["vel"] * delta
        c["vel"].y += 180.0 * delta
        c["life"] -= delta
    ore_chunks = ore_chunks.filter(func(c: Dictionary): return c["life"] > 0.0)
    queue_redraw()

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventKey and event.pressed and not event.echo:
        if event.keycode == KEY_SPACE: mine()
        elif event.keycode == KEY_S: sell()
        elif event.keycode == KEY_1: upgrade_pickaxe()
        elif event.keycode == KEY_2: upgrade_drill()
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
        var p: Vector2 = event.position
        if Rect2(64, 574, 250, 78).has_point(p): mine()
        elif Rect2(338, 574, 190, 78).has_point(p): sell()
        elif Rect2(560, 574, 190, 78).has_point(p): upgrade_pickaxe()
        elif Rect2(782, 574, 190, 78).has_point(p): upgrade_drill()

func mine() -> void:
    if mining_cooldown > 0.0: return
    mining_cooldown = 0.18
    var amount := mine_power * (1.0 + float(pickaxe_level - 1) * 0.35)
    ore += amount
    depth = min(1000, depth + (1 if randf() > 0.72 else 0))
    hit_flash = 1.0
    camera_shake = 1.0
    message = "+%.1f kg minério" % amount
    message_time = 0.9
    var center := Vector2(438, 410)
    for i in range(12):
        var angle := randf_range(0.0, TAU)
        var speed := randf_range(70.0, 180.0)
        particles.append({"pos": center, "vel": Vector2(cos(angle), sin(angle)) * speed, "life": randf_range(0.25, 0.55)})
    for i in range(3):
        ore_chunks.append({"pos": center + Vector2(randf_range(-20,20), randf_range(-10,10)), "vel": Vector2(randf_range(-70,70), randf_range(-130,-60)), "life": 0.7})

func sell() -> void:
    if ore <= 0.0:
        message = "Você não tem minério para vender."
        message_time = 1.5
        return
    var earned := ore * ore_value
    credits += earned
    ore = 0.0
    message = "+%d créditos" % int(earned)
    message_time = 1.2

func upgrade_pickaxe() -> void:
    var cost := 35.0 * pow(1.65, pickaxe_level - 1)
    if credits < cost:
        message = "Faltam %d créditos." % int(cost - credits)
        message_time = 1.2
        return
    credits -= cost
    pickaxe_level += 1
    mine_power += 0.45
    message = "Picareta nível %d" % pickaxe_level
    message_time = 1.4

func upgrade_drill() -> void:
    var cost := 90.0 * pow(1.8, drill_level)
    if credits < cost:
        message = "Faltam %d créditos." % int(cost - credits)
        message_time = 1.2
        return
    credits -= cost
    drill_level += 1
    message = "Broca automática nível %d" % drill_level
    message_time = 1.4

func _draw() -> void:
    draw_rect(Rect2(0,0,1280,720), Color("#081016"))
    draw_rect(Rect2(0,0,1280,86), Color("#0d1b24"))
    draw_string(ThemeDB.fallback_font, Vector2(52,48), "DEEPCORE", HORIZONTAL_ALIGNMENT_LEFT, -1, 30, Color("#e8f0f2"))
    draw_string(ThemeDB.fallback_font, Vector2(245,46), "MINING PROTOCOL / ALPHA", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#7e969e"))
    draw_string(ThemeDB.fallback_font, Vector2(1080,45), "%04d C" % int(credits), HORIZONTAL_ALIGNMENT_RIGHT, 150, 20, Color("#e6c36a"))
    draw_style_box(_box(Color("#101f28"), Color("#1b333e")), Rect2(52,112,780,430))
    draw_string(ThemeDB.fallback_font, Vector2(82,150), "MINA", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#83a2aa"))
    draw_string(ThemeDB.fallback_font, Vector2(82,188), "PROFUNDIDADE  %03d m" % depth, HORIZONTAL_ALIGNMENT_LEFT, -1, 26, Color("#d9e5e7"))
    var cave := PackedVector2Array([Vector2(84,235),Vector2(155,205),Vector2(245,228),Vector2(340,194),Vector2(452,230),Vector2(570,198),Vector2(700,236),Vector2(798,212),Vector2(798,515),Vector2(84,515)])
    draw_colored_polygon(cave, Color("#182a31"))
    for i in range(9):
        var x := 112.0 + i * 76.0
        var y := 285.0 + float((i * 41) % 100)
        draw_circle(Vector2(x,y), 5.0 + float(i%3)*2.0, Color("#c48b43"))
    var pulse_radius := 46.0 + sin(pulse * 3.0) * 3.0
    draw_circle(Vector2(438,410), pulse_radius + hit_flash * 14.0, Color("#0a151b"))
    draw_circle(Vector2(438,410), 38.0 + hit_flash * 8.0, Color("#15232a"))
    if hit_flash > 0.0:
        draw_circle(Vector2(438,410), 48.0, Color(0.85,0.65,0.25,hit_flash * 0.25))
    draw_string(ThemeDB.fallback_font, Vector2(381,415), "VEIO", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#7e969e"))
    for p: Dictionary in particles:
        draw_circle(p["pos"], 3.0, Color("#d0a45b"))
    for c: Dictionary in ore_chunks:
        draw_circle(c["pos"], 4.0, Color("#e0b96e"))
    draw_string(ThemeDB.fallback_font, Vector2(84,534), "MINÉRIO", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#718890"))
    draw_string(ThemeDB.fallback_font, Vector2(170,534), "%.1f kg" % ore, HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("#edf4f5"))
    draw_style_box(_box(Color("#101f28"), Color("#1b333e")), Rect2(858,112,370,430))
    draw_string(ThemeDB.fallback_font, Vector2(890,150), "OPERAÇÃO", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#83a2aa"))
    _card(Vector2(890,178), "PICARETA", "Nível %d" % pickaxe_level, "+%.2f força" % mine_power)
    _card(Vector2(890,270), "BROCA", "Nível %d" % drill_level, "%.2f kg/s" % (0.35 * drill_level))
    _card(Vector2(890,362), "VALOR", "%.0f C / kg" % ore_value, "mercado local")
    draw_string(ThemeDB.fallback_font, Vector2(890,472), "[SPACE] minerar   [S] vender", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#6e858d"))
    draw_string(ThemeDB.fallback_font, Vector2(890,496), "[1] picareta    [2] broca", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#6e858d"))
    _button(Rect2(64,574,250,78), "MINERAR", "SPACE", Color("#c48b43"))
    _button(Rect2(338,574,190,78), "VENDER", "S", Color("#5f9c91"))
    _button(Rect2(560,574,190,78), "PICARETA", "1", Color("#788e98"))
    _button(Rect2(782,574,190,78), "BROCA", "2", Color("#788e98"))
    _button(Rect2(1004,574,210,78), "PRÓXIMA FASE", "EM BREVE", Color("#334a54"))
    if message_time > 0.0:
        draw_string(ThemeDB.fallback_font, Vector2(64,690), message, HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#e6c36a"))

func _box(bg: Color, border: Color) -> StyleBoxFlat:
    var b := StyleBoxFlat.new()
    b.bg_color = bg
    b.border_color = border
    b.set_border_width_all(1)
    b.set_corner_radius_all(8)
    return b

func _card(pos: Vector2, title: String, value: String, sub: String) -> void:
    draw_style_box(_box(Color("#0c181f"), Color("#1b333e")), Rect2(pos.x,pos.y,306,78))
    draw_string(ThemeDB.fallback_font, pos + Vector2(16,24), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color("#6f858d"))
    draw_string(ThemeDB.fallback_font, pos + Vector2(16,50), value, HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("#e5edef"))
    draw_string(ThemeDB.fallback_font, pos + Vector2(180,50), sub, HORIZONTAL_ALIGNMENT_LEFT, 110, 12, Color("#83a2aa"))

func _button(rect: Rect2, title: String, key: String, accent: Color) -> void:
    draw_style_box(_box(Color("#12232b"), Color("#29424c")), rect)
    draw_string(ThemeDB.fallback_font, rect.position + Vector2(18,30), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#e5edef"))
    draw_string(ThemeDB.fallback_font, rect.position + Vector2(18,55), key, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, accent)
