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
var mine_anim := 0.0
var drill_anim := 0.0
var button_flash := 0.0
var hover_button := -1
var particles: Array[Dictionary] = []
var ore_chunks: Array[Dictionary] = []
var dust: Array[Dictionary] = []

func _ready() -> void:
    randomize()
    for i in range(42):
        dust.append({
            "pos": Vector2(randf_range(90.0, 800.0), randf_range(215.0, 520.0)),
            "speed": randf_range(5.0, 18.0),
            "size": randf_range(1.0, 2.5),
            "phase": randf_range(0.0, TAU)
        })
    queue_redraw()

func _process(delta: float) -> void:
    mining_cooldown = maxf(0.0, mining_cooldown - delta)
    message_time = maxf(0.0, message_time - delta)
    hit_flash = maxf(0.0, hit_flash - delta * 4.0)
    camera_shake = maxf(0.0, camera_shake - delta * 7.0)
    button_flash = maxf(0.0, button_flash - delta * 5.0)
    pulse += delta
    mine_anim = maxf(0.0, mine_anim - delta * 5.5)
    drill_anim += delta * (2.0 + drill_level * 0.55)
    if drill_level > 0:
        ore += (0.35 * drill_level) * delta
    for d: Dictionary in dust:
        d["pos"].y -= d["speed"] * delta
        d["pos"].x += sin(pulse * 0.7 + d["phase"]) * delta * 4.0
        if d["pos"].y < 210.0:
            d["pos"].y = 515.0
            d["pos"].x = randf_range(90.0, 800.0)
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
    if event is InputEventMouseMotion:
        hover_button = _button_at(event.position)
    elif event is InputEventKey and event.pressed and not event.echo:
        if event.keycode == KEY_SPACE: mine()
        elif event.keycode == KEY_S: sell()
        elif event.keycode == KEY_1: upgrade_pickaxe()
        elif event.keycode == KEY_2: upgrade_drill()
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
        var p: Vector2 = event.position
        var b := _button_at(p)
        if b == 0: mine()
        elif b == 1: sell()
        elif b == 2: upgrade_pickaxe()
        elif b == 3: upgrade_drill()

func _button_at(p: Vector2) -> int:
    if Rect2(64, 574, 250, 78).has_point(p): return 0
    if Rect2(338, 574, 190, 78).has_point(p): return 1
    if Rect2(560, 574, 190, 78).has_point(p): return 2
    if Rect2(782, 574, 190, 78).has_point(p): return 3
    return -1

func mine() -> void:
    if mining_cooldown > 0.0: return
    mining_cooldown = 0.18
    var amount := mine_power * (1.0 + float(pickaxe_level - 1) * 0.35)
    ore += amount
    depth = min(1000, depth + (1 if randf() > 0.72 else 0))
    hit_flash = 1.0
    camera_shake = 1.0
    mine_anim = 1.0
    button_flash = 1.0
    message = "+%.1f kg minério" % amount
    message_time = 0.9
    var center := Vector2(438, 410)
    for i in range(18):
        var angle := randf_range(0.0, TAU)
        var speed := randf_range(80.0, 230.0)
        particles.append({"pos": center, "vel": Vector2(cos(angle), sin(angle)) * speed, "life": randf_range(0.25, 0.65), "size": randf_range(2.0, 4.0)})
    for i in range(5):
        ore_chunks.append({"pos": center + Vector2(randf_range(-22,22), randf_range(-12,12)), "vel": Vector2(randf_range(-100,100), randf_range(-160,-70)), "life": 0.8, "size": randf_range(3.0, 6.0)})

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
    var shake := Vector2.ZERO
    if camera_shake > 0.0:
        shake = Vector2(randf_range(-1.8,1.8), randf_range(-1.8,1.8)) * camera_shake
    draw_rect(Rect2(0,0,1280,720), Color("#070d12"))
    draw_rect(Rect2(0,0,1280,86), Color("#0b171e"))
    draw_rect(Rect2(0,84,1280,2), Color("#203a44"))
    draw_string(ThemeDB.fallback_font, Vector2(52,48), "DEEPCORE", HORIZONTAL_ALIGNMENT_LEFT, -1, 30, Color("#edf4f5"))
    draw_string(ThemeDB.fallback_font, Vector2(245,46), "MINING PROTOCOL / ALPHA", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#718890"))
    draw_string(ThemeDB.fallback_font, Vector2(1060,43), "%04d C" % int(credits), HORIZONTAL_ALIGNMENT_RIGHT, 170, 21, Color("#e6c36a"))
    draw_circle(Vector2(1240,38), 6.0 + sin(pulse*3.0)*1.5, Color("#58b69e"))
    draw_string(ThemeDB.fallback_font, Vector2(1060,62), "SISTEMA ONLINE", HORIZONTAL_ALIGNMENT_RIGHT, 170, 10, Color("#4c8f7e"))

    draw_style_box(_box(Color("#0d1a21"), Color("#1b343e")), Rect2(52,112,780,430))
    draw_string(ThemeDB.fallback_font, Vector2(82,150), "MINA / SETOR %02d" % int(depth / 100 + 1), HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#83a2aa"))
    draw_string(ThemeDB.fallback_font, Vector2(82,188), "PROFUNDIDADE  %03d m" % depth, HORIZONTAL_ALIGNMENT_LEFT, -1, 26, Color("#dce7e9"))
    draw_string(ThemeDB.fallback_font, Vector2(638,184), "%d%%" % int(clampf(float(depth) / 10.0, 0.0, 100.0)), HORIZONTAL_ALIGNMENT_RIGHT, 160, 13, Color("#708890"))
    draw_rect(Rect2(638,192,160,4), Color("#172b33"))
    draw_rect(Rect2(638,192,160 * clampf(float(depth) / 1000.0,0.0,1.0),4), Color("#c48b43"))

    var cave := PackedVector2Array([Vector2(84,235),Vector2(155,205),Vector2(245,228),Vector2(340,194),Vector2(452,230),Vector2(570,198),Vector2(700,236),Vector2(798,212),Vector2(798,515),Vector2(84,515)])
    draw_colored_polygon(cave, Color("#15272e"))
    draw_line(Vector2(84,515), Vector2(798,515), Color("#2a444d"), 2.0)
    for i in range(12):
        var x := 112.0 + i * 59.0
        var y := 275.0 + float((i * 47) % 155)
        var r := 4.0 + float(i%3)*2.0 + sin(pulse*2.0+i)*0.7
        draw_circle(Vector2(x,y), r+5.0, Color(0.76,0.53,0.24,0.06))
        draw_circle(Vector2(x,y), r, Color("#c48b43"))
    for d: Dictionary in dust:
        var dp: Vector2 = d["pos"]
        draw_circle(dp, d["size"], Color(0.56,0.66,0.68,0.16))

    var center := Vector2(438,410) + shake
    var pulse_radius := 48.0 + sin(pulse * 3.0) * 4.0
    draw_circle(center, pulse_radius + hit_flash * 18.0, Color(0.77,0.55,0.25,0.06))
    draw_circle(center, 43.0, Color("#0a1419"))
    draw_circle(center, 37.0, Color("#1c3038"))
    draw_arc(center, pulse_radius, 0.0, TAU, 48, Color(0.77,0.55,0.25,0.25), 2.0)
    draw_arc(center, 52.0, -1.4, -0.4 + pulse * 0.15, 20, Color("#c48b43"), 2.0)
    draw_string(ThemeDB.fallback_font, center + Vector2(-28,5), "VEIO", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#9ab0b6"))

    # Animated mining tool
    var swing := 0.0
    if mine_anim > 0.0:
        swing = sin((1.0 - mine_anim) * PI) * 1.0
    var tool_origin := center + Vector2(58, -62)
    var tool_angle := -0.95 + swing * 1.7
    var tool_end := tool_origin + Vector2(cos(tool_angle), sin(tool_angle)) * 92.0
    draw_line(tool_origin, tool_end, Color("#7b5a3c"), 10.0)
    draw_line(tool_origin, tool_end, Color("#b88451"), 5.0)
    var head := tool_end
    draw_line(head + Vector2(-20,-8), head + Vector2(20,8), Color("#9aa7a9"), 8.0)
    draw_line(head + Vector2(-18,-10), head + Vector2(22,6), Color("#d1d7d6"), 3.0)

    # Automated drill rig
    var rig := Vector2(700,445)
    draw_rect(Rect2(rig.x-48,rig.y-55,96,70), Color("#0a1419"), true)
    draw_rect(Rect2(rig.x-43,rig.y-50,86,60), Color("#1c3038"), true)
    draw_rect(Rect2(rig.x-35,rig.y-39,70,8), Color("#29434c"), true)
    draw_circle(rig, 25.0, Color("#101e24"))
    draw_arc(rig, 25.0, 0.0, TAU, 32, Color("#48636b"), 4.0)
    if drill_level > 0:
        for i in range(4):
            var a := drill_anim * 3.0 + i * TAU/4.0
            var p1 := rig + Vector2(cos(a),sin(a))*8.0
            var p2 := rig + Vector2(cos(a),sin(a))*21.0
            draw_line(p1,p2,Color("#d2a04f"),4.0)
        draw_string(ThemeDB.fallback_font, rig + Vector2(-44,40), "AUTO DRILL", HORIZONTAL_ALIGNMENT_LEFT, -1, 10, Color("#6faaa0"))
    else:
        draw_string(ThemeDB.fallback_font, rig + Vector2(-45,40), "OFFLINE", HORIZONTAL_ALIGNMENT_LEFT, -1, 10, Color("#6c777b"))

    for p: Dictionary in particles:
        draw_circle(p["pos"] + shake, p["size"], Color("#d0a45b"))
    for c: Dictionary in ore_chunks:
        draw_circle(c["pos"] + shake, c["size"], Color("#e5bd6e"))

    draw_string(ThemeDB.fallback_font, Vector2(84,534), "MINÉRIO", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("#718890"))
    draw_string(ThemeDB.fallback_font, Vector2(170,534), "%.1f kg" % ore, HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("#edf4f5"))

    draw_style_box(_box(Color("#0d1a21"), Color("#1b343e")), Rect2(858,112,370,430))
    draw_string(ThemeDB.fallback_font, Vector2(890,150), "OPERAÇÃO", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#83a2aa"))
    _card(Vector2(890,178), "PICARETA", "Nível %d" % pickaxe_level, "+%.2f força" % mine_power)
    _card(Vector2(890,270), "BROCA", "Nível %d" % drill_level, "%.2f kg/s" % (0.35 * drill_level))
    _card(Vector2(890,362), "VALOR", "%.0f C / kg" % ore_value, "mercado local")
    draw_string(ThemeDB.fallback_font, Vector2(890,472), "[SPACE] minerar   [S] vender", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#6e858d"))
    draw_string(ThemeDB.fallback_font, Vector2(890,496), "[1] picareta    [2] broca", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#6e858d"))

    _button(Rect2(64,574,250,78), "MINERAR", "SPACE", Color("#c48b43"), 0)
    _button(Rect2(338,574,190,78), "VENDER", "S", Color("#5f9c91"), 1)
    _button(Rect2(560,574,190,78), "PICARETA", "1", Color("#788e98"), 2)
    _button(Rect2(782,574,190,78), "BROCA", "2", Color("#788e98"), 3)
    _button(Rect2(1004,574,210,78), "PRÓXIMA FASE", "BLOQUEADO", Color("#334a54"), 4)
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
    draw_style_box(_box(Color("#0a151b"), Color("#1b333e")), Rect2(pos.x,pos.y,306,78))
    draw_string(ThemeDB.fallback_font, pos + Vector2(16,24), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color("#6f858d"))
    draw_string(ThemeDB.fallback_font, pos + Vector2(16,50), value, HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("#e5edef"))
    draw_string(ThemeDB.fallback_font, pos + Vector2(180,50), sub, HORIZONTAL_ALIGNMENT_LEFT, 110, 12, Color("#83a2aa"))

func _button(rect: Rect2, title: String, key: String, accent: Color, index: int) -> void:
    var hovered := hover_button == index
    var pressed := button_flash > 0.0 and index == 0
    var bg := Color("#182c35") if hovered else Color("#12232b")
    if pressed:
        bg = Color("#29434a")
    var border := accent if hovered else Color("#29424c")
    draw_style_box(_box(bg, border), rect)
    if hovered:
        draw_rect(Rect2(rect.position + Vector2(8,7), Vector2(rect.size.x-16,2)), accent)
    draw_string(ThemeDB.fallback_font, rect.position + Vector2(18,30), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#e5edef"))
    draw_string(ThemeDB.fallback_font, rect.position + Vector2(18,55), key, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, accent)
