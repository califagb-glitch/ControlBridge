extends Node2D

var ore: float = 0.0
var coins: float = 0.0
var depth: float = 0.0
var pickaxe_level: int = 1
var drill_level: int = 0
var last_tick := 0.0
var pulse := 0.0

const W := 1280.0
const H := 720.0

func _ready() -> void:
	queue_redraw()

func _process(delta: float) -> void:
	last_tick += delta
	pulse += delta
	if last_tick >= 1.0:
		var ticks := int(last_tick)
		last_tick -= ticks
		ore += drill_level * ticks
		queue_redraw()

func _input(event: InputEvent) -> void:
	if event.is_action_pressed("mine"):
		mine()

func mine() -> void:
	var amount := pickaxe_level
	ore += amount
	depth += 0.35 * pickaxe_level
	queue_redraw()

func sell_value() -> float:
	return ore * (1.0 + drill_level * 0.1)

func pickaxe_cost() -> float:
	return 25.0 * pow(1.65, pickaxe_level - 1)

func drill_cost() -> float:
	return 100.0 * pow(2.1, drill_level)

func buy_pickaxe() -> void:
	if coins >= pickaxe_cost():
		coins -= pickaxe_cost()
		pickaxe_level += 1
		queue_redraw()

func buy_drill() -> void:
	if coins >= drill_cost():
		coins -= drill_cost()
		drill_level += 1
		queue_redraw()

func sell_ore() -> void:
	if ore > 0.0:
		coins += sell_value()
		ore = 0.0
		queue_redraw()

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_SPACE: mine()
			KEY_S: sell_ore()
			KEY_1: buy_pickaxe()
			KEY_2: buy_drill()

func money_text(value: float) -> String:
	if value >= 1000000.0:
		return "%.2fM" % (value / 1000000.0)
	if value >= 1000.0:
		return "%.2fK" % (value / 1000.0)
	return "%.0f" % value

func button(rect: Rect2, title: String, subtitle: String, enabled: bool) -> void:
	var c := Color("#26384a") if enabled else Color("#172330")
	draw_style_box(make_box(c, 12, Color("#3c5870")), rect)
	draw_string(ThemeDB.fallback_font, rect.position + Vector2(18, 30), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 22, Color.WHITE)
	draw_string(ThemeDB.fallback_font, rect.position + Vector2(18, 55), subtitle, HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#9db0c1"))

func make_box(color: Color, radius: int, border: Color) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = color
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(radius)
	return box

func _draw() -> void:
	# Background
	draw_rect(Rect2(0, 0, W, H), Color("#081019"))
	draw_rect(Rect2(0, 0, W, 82), Color("#0d1823"))

	# Header
	draw_string(ThemeDB.fallback_font, Vector2(42, 42), "DEEPCORE", HORIZONTAL_ALIGNMENT_LEFT, -1, 28, Color("#f2f5f7"))
	draw_string(ThemeDB.fallback_font, Vector2(42, 66), "MINA 01  •  PROTÓTIPO", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#73899b"))
	draw_string(ThemeDB.fallback_font, Vector2(1030, 45), "$ " + money_text(coins), HORIZONTAL_ALIGNMENT_LEFT, -1, 24, Color("#f2d28b"))

	# Mine panel
	var mine_rect := Rect2(32, 108, 790, 565)
	draw_style_box(make_box(Color("#0e1b27"), 18, Color("#203344")), mine_rect)
	draw_string(ThemeDB.fallback_font, Vector2(58, 145), "POÇO DE MINERAÇÃO", HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("#b8c9d6"))
	draw_string(ThemeDB.fallback_font, Vector2(58, 176), "Profundidade: %.1f m" % depth, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#71899c"))

	# Stylized cave
	var cave := PackedVector2Array([Vector2(120, 235), Vector2(300, 205), Vector2(500, 235), Vector2(690, 210), Vector2(755, 590), Vector2(110, 590)])
	draw_colored_polygon(cave, Color("#182a36"))
	for i in range(7):
		var x := 150.0 + i * 90.0
		var y := 300.0 + sin(i * 1.7 + pulse * 0.25) * 12.0
		draw_circle(Vector2(x, y), 7.0, Color("#6c8490"))
		draw_circle(Vector2(x + 4, y + 32), 4.0, Color("#c99545"))
	
	# Mining button
	var mine_button := Rect2(285, 480, 360, 92)
	draw_style_box(make_box(Color("#bd873d"), 20, Color("#e0ad62")), mine_button)
	draw_string(ThemeDB.fallback_font, Vector2(0, 518), "MINERAR", HORIZONTAL_ALIGNMENT_CENTER, W, 30, Color.WHITE)
	draw_string(ThemeDB.fallback_font, Vector2(0, 549), "+%d minério   •   Toque / clique / ESPAÇO" % pickaxe_level, HORIZONTAL_ALIGNMENT_CENTER, W, 15, Color("#fff0d4"))

	# Resource cards
	draw_string(ThemeDB.fallback_font, Vector2(58, 620), "MINÉRIO", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#71899c"))
	draw_string(ThemeDB.fallback_font, Vector2(58, 650), money_text(ore), HORIZONTAL_ALIGNMENT_LEFT, -1, 28, Color("#e4edf2"))
	draw_string(ThemeDB.fallback_font, Vector2(230, 620), "PRODUÇÃO", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#71899c"))
	draw_string(ThemeDB.fallback_font, Vector2(230, 650), "+%d /s" % drill_level, HORIZONTAL_ALIGNMENT_LEFT, -1, 28, Color("#a9d4b0"))

	# Upgrade panel
	draw_string(ThemeDB.fallback_font, Vector2(860, 125), "OFICINA", HORIZONTAL_ALIGNMENT_LEFT, -1, 22, Color("#e7eef3"))
	draw_string(ThemeDB.fallback_font, Vector2(860, 151), "Melhore sua operação", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color("#71899c"))
	button(Rect2(850, 180, 385, 86), "Picareta Lv.%d" % pickaxe_level, "$%s  •  [1]" % money_text(pickaxe_cost()), coins >= pickaxe_cost())
	button(Rect2(850, 285, 385, 86), "Furadeira Lv.%d" % drill_level, "$%s  •  [2]  •  +1/s" % money_text(drill_cost()), coins >= drill_cost())
	button(Rect2(850, 390, 385, 86), "Vender minério", "Converter tudo em créditos  •  [S]", ore > 0.0)

	draw_string(ThemeDB.fallback_font, Vector2(860, 535), "PRÓXIMOS SISTEMAS", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#71899c"))
	draw_string(ThemeDB.fallback_font, Vector2(860, 566), "◆ Novos minérios", HORIZONTAL_ALIGNMENT_LEFT, -1, 17, Color("#b8c9d6"))
	draw_string(ThemeDB.fallback_font, Vector2(860, 594), "◆ Perfuração automática", HORIZONTAL_ALIGNMENT_LEFT, -1, 17, Color("#b8c9d6"))
	draw_string(ThemeDB.fallback_font, Vector2(860, 622), "◆ Cavernas e descobertas", HORIZONTAL_ALIGNMENT_LEFT, -1, 17, Color("#b8c9d6"))

func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_SIZE_CHANGED:
		queue_redraw()

func _gui_input(event: InputEvent) -> void:
	pass
