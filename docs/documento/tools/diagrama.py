from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT_REGULAR = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
LINE = 4
OUTPUT_DIRECTORY = Path(__file__).resolve().parent.parent


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.image = Image.new("RGB", (width, height), "white")
        self.draw = ImageDraw.Draw(self.image)
        self.regular = ImageFont.truetype(FONT_REGULAR, 40)
        self.small = ImageFont.truetype(FONT_REGULAR, 36)
        self.bold = ImageFont.truetype(FONT_BOLD, 44)

    def centered(self, text, font, center_x, top):
        box = self.draw.textbbox((0, 0), text, font=font)
        self.draw.text((center_x - (box[2] - box[0]) / 2, top), text, font=font, fill="black")

    def rectangle(self, left, top, right, bottom, width=LINE):
        self.draw.rectangle((left, top, right, bottom), outline="black", width=width)

    def vertical_arrow(self, x, top, bottom, label, label_side="right"):
        self.draw.line((x, top, x, bottom - 22), fill="black", width=LINE)
        self.draw.polygon([(x - 16, bottom - 30), (x + 16, bottom - 30), (x, bottom)], fill="black")
        middle = (top + bottom) / 2 - 22
        if label_side == "right":
            self.draw.text((x + 30, middle), label, font=self.small, fill="black")
        else:
            box = self.draw.textbbox((0, 0), label, font=self.small)
            self.draw.text((x - 30 - (box[2] - box[0]), middle), label, font=self.small, fill="black")

    def upward_arrow(self, x, tip, start):
        self.draw.line((x, start, x, tip + 22), fill="black", width=LINE)
        self.draw.polygon([(x - 16, tip + 30), (x + 16, tip + 30), (x, tip)], fill="black")

    def horizontal_arrow(self, left, right, y, width=3):
        self.draw.line((left, y, right - 22, y), fill="black", width=width)
        self.draw.polygon([(right - 30, y - 12), (right - 30, y + 12), (right, y)], fill="black")

    def save(self, name):
        self.image.save(OUTPUT_DIRECTORY / name, dpi=(333, 333))
        print(name, self.image.size)


def architecture():
    canvas = Canvas(2000, 1720)
    center = 1000
    canvas.rectangle(500, 20, 1500, 190)
    canvas.centered("Fuente de eventos", canvas.bold, center, 45)
    canvas.centered("simulador, o cualquier juego en cualquier lenguaje", canvas.small, center, 112)

    canvas.vertical_arrow(640, 190, 360, "POST /events  (JSON)")
    canvas.vertical_arrow(1380, 190, 360, "archivos en la carpeta inbox")

    canvas.rectangle(120, 360, 1880, 900)
    canvas.centered("Módulo Go: ingest-go (imperativo), puerto 8080", canvas.bold, center, 385)
    canvas.rectangle(170, 465, 1830, 555, 3)
    canvas.centered("Entradas: API HTTP y vigilante de la carpeta inbox (.json, .jsonl)", canvas.regular, center, 487)
    row_top, row_bottom = 600, 700
    canvas.rectangle(170, row_top, 530, row_bottom, 3)
    canvas.centered("Validar", canvas.regular, 350, 635)
    canvas.rectangle(640, row_top, 1000, row_bottom, 3)
    canvas.centered("Deduplicar", canvas.regular, 820, 635)
    canvas.rectangle(1110, row_top, 1470, row_bottom, 3)
    canvas.centered("Router por partida", canvas.regular, 1290, 635)
    canvas.rectangle(1580, row_top, 1830, row_bottom, 3)
    canvas.centered("Rechazados", canvas.regular, 1705, 635)
    canvas.horizontal_arrow(530, 640, 650)
    canvas.horizontal_arrow(1000, 1110, 650)
    canvas.horizontal_arrow(1470, 1580, 650)
    canvas.rectangle(170, 750, 1830, 860, 3)
    canvas.centered("Una goroutine por partida activa: acumula, ordena y cierra la partida", canvas.regular, center, 785)

    canvas.vertical_arrow(center, 900, 1050, "POST /analyze  (la partida completa y ordenada)")

    canvas.rectangle(120, 1050, 1880, 1480)
    canvas.centered("Módulo Scala: analytics-scala (funcional), puerto 8081", canvas.bold, center, 1075)
    canvas.rectangle(170, 1160, 640, 1270, 3)
    canvas.centered("Decodificar", canvas.regular, 405, 1195)
    canvas.rectangle(740, 1160, 1260, 1270, 3)
    canvas.centered("Análisis genérico y del juego", canvas.regular, 1000, 1195)
    canvas.rectangle(1360, 1160, 1830, 1270, 3)
    canvas.centered("Reglas y patrones", canvas.regular, 1595, 1195)
    canvas.horizontal_arrow(640, 740, 1215)
    canvas.horizontal_arrow(1260, 1360, 1215)
    canvas.rectangle(170, 1310, 1830, 1440, 3)
    canvas.centered("Resultados guardados en archivos JSON, API de consulta y panel web", canvas.regular, center, 1352)

    canvas.vertical_arrow(center, 1480, 1600, "GET /stats/...  y  /patterns  (JSON)")
    canvas.rectangle(560, 1600, 1440, 1690)
    canvas.centered("Consulta: panel web, curl o cualquier cliente", canvas.regular, center, 1628)
    canvas.save("diagrama-arquitectura.png")


def platform():
    canvas = Canvas(2000, 1060)
    canvas.rectangle(20, 40, 420, 230)
    canvas.centered("Jugadores", canvas.bold, 220, 75)
    canvas.centered("varios, conectados", canvas.small, 220, 140)
    canvas.centered("al mismo tiempo", canvas.small, 220, 180)

    canvas.rectangle(20, 330, 600, 1020)
    canvas.centered("Consola de juegos", canvas.bold, 310, 355)
    games = [("Juego A", "carreras"), ("Juego B", "ajedrez, lógica en Prolog"), ("Juego C", "cartas")]
    for position, (name, detail) in enumerate(games):
        top = 450 + position * 190
        canvas.rectangle(60, top, 560, top + 150, 3)
        canvas.centered(name, canvas.regular, 310, top + 25)
        canvas.centered(detail, canvas.small, 310, top + 85)
    canvas.vertical_arrow(220, 230, 330, "juegan")

    canvas.horizontal_arrow(600, 980, 700)
    canvas.centered("eventos con el mismo", canvas.small, 790, 600)
    canvas.centered("contrato", canvas.small, 790, 640)
    canvas.centered("(HTTP o archivos)", canvas.small, 790, 725)

    canvas.rectangle(980, 330, 1560, 1020)
    canvas.centered("GameStats", canvas.bold, 1270, 355)
    canvas.rectangle(1020, 460, 1520, 640, 3)
    canvas.centered("ingest-go", canvas.regular, 1270, 495)
    canvas.centered("recibe, valida, agrupa", canvas.small, 1270, 555)
    canvas.vertical_arrow(1270, 640, 760, "")
    canvas.rectangle(1020, 760, 1520, 940, 3)
    canvas.centered("analytics-scala", canvas.regular, 1270, 795)
    canvas.centered("estadísticas y patrones", canvas.small, 1270, 855)

    canvas.horizontal_arrow(1560, 1660, 850)
    canvas.rectangle(1660, 700, 1990, 1020)
    canvas.centered("Resultados", canvas.bold, 1825, 760)
    canvas.centered("guardados", canvas.bold, 1825, 810)
    canvas.centered("(historial)", canvas.small, 1825, 890)
    canvas.upward_arrow(1825, 560, 700)
    canvas.rectangle(1660, 330, 1990, 560)
    canvas.centered("Panel de", canvas.bold, 1825, 370)
    canvas.centered("estadísticas", canvas.bold, 1825, 420)
    canvas.centered("por juego", canvas.small, 1825, 495)
    canvas.save("diagrama-plataforma.png")


architecture()
platform()
