import os
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1500, 1000
FONT_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"
BOLD_PATH = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
BLACK = (0, 0, 0)
WHITE = (255, 255, 255)
LIGHT = (240, 240, 240)

image = Image.new("RGB", (WIDTH, HEIGHT), WHITE)
draw = ImageDraw.Draw(image)
regular = ImageFont.truetype(FONT_PATH, 27)
bold = ImageFont.truetype(BOLD_PATH, 29)
small = ImageFont.truetype(FONT_PATH, 24)


def centered(box, lines, font=regular, spacing=36, top_offset=0):
    left, top, right, bottom = box
    total = spacing * len(lines)
    y = top + (bottom - top - total) / 2 + top_offset
    for line in lines:
        width = draw.textlength(line, font=font)
        draw.text((left + (right - left - width) / 2, y), line, fill=BLACK, font=font)
        y += spacing


def rectangle(box, lines, fill=WHITE, font=regular, width=3, spacing=36):
    draw.rectangle(box, outline=BLACK, fill=fill, width=width)
    centered(box, lines, font, spacing)


def title_box(box, title, fill=WHITE, centered_title=True):
    draw.rectangle(box, outline=BLACK, fill=fill, width=4)
    left, top, right, bottom = box
    width = draw.textlength(title, font=bold)
    x = left + (right - left - width) / 2 if centered_title else left + 20
    draw.text((x, top + 16), title, fill=BLACK, font=bold)


def arrow(points, label=None, label_at=None):
    for start, end in zip(points, points[1:]):
        draw.line([start, end], fill=BLACK, width=4)
    (x0, y0), (x1, y1) = points[-2], points[-1]
    size = 14
    if x0 == x1:
        direction = 1 if y1 > y0 else -1
        draw.polygon([(x1, y1), (x1 - size, y1 - direction * 2 * size), (x1 + size, y1 - direction * 2 * size)], fill=BLACK)
    else:
        direction = 1 if x1 > x0 else -1
        draw.polygon([(x1, y1), (x1 - direction * 2 * size, y1 - size), (x1 - direction * 2 * size, y1 + size)], fill=BLACK)
    if label:
        x, y = label_at
        draw.text((x, y), label, fill=BLACK, font=small)


rectangle((90, 40, 560, 140), ["Navegador", "consola de juegos y panel"], LIGHT)

title_box((90, 250, 760, 560), "console (Java 21) :8100", WHITE)
rectangle((120, 310, 410, 430), ["Juegos (plugins JAR)", "Conecta 4, Memoria,", "Buscaminas"], WHITE, small, spacing=32)
rectangle((440, 310, 730, 430), ["Sesiones y", "observadores"], WHITE, small)
rectangle((120, 450, 410, 535), ["Publicador de eventos", "(cola por partida)"], WHITE, small)
rectangle((440, 450, 730, 535), ["Consulta de", "estadisticas"], WHITE, small)

rectangle((960, 250, 1400, 400), ["prolog-ai (SWI-Prolog) :8090", "minimax con poda", "alfa-beta"], LIGHT, small)

title_box((90, 690, 640, 900), "GameStats", LIGHT, centered_title=False)
rectangle((120, 760, 330, 870), ["ingest-go", "(Go) :8080"], WHITE, small)
rectangle((380, 760, 610, 870), ["analytics-scala", "(Scala) :8081"], WHITE, small)

rectangle((960, 740, 1400, 890), ["Resultados en archivos", "JSON (volumen de", "datos)"], WHITE, small)

arrow([(325, 140), (325, 250)], "HTTP", (345, 180))
arrow([(760, 330), (960, 330)], "POST /decide", (775, 285))
arrow([(300, 535), (300, 760)], "POST /events", (320, 610))
arrow([(330, 815), (380, 815)])
arrow([(585, 535), (585, 640), (520, 640), (520, 760)], "GET /stats", (610, 595))
arrow([(610, 815), (960, 815)], "guarda", (740, 770))

os.makedirs(os.path.dirname(os.path.abspath(__file__)) + "/..", exist_ok=True)
image.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "diagrama-consola.png"))
