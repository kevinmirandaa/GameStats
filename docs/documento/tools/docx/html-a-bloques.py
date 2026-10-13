import json
import re
import sys
from html.parser import HTMLParser

SKIPPED_TAGS = {"style", "title", "head"}
BLOCK_TAGS = {"h1", "h2", "h3", "p", "li"}


class BlockParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.blocks = []
        self.skipped_depth = 0
        self.cover_depth = 0
        self.runs = None
        self.block_kind = None
        self.block_attributes = {}
        self.bold_depth = 0
        self.lists = []
        self.table = None
        self.row = None
        self.cell = None
        self.code_text = None

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        classes = (attributes.get("class") or "").split()
        if tag in SKIPPED_TAGS:
            self.skipped_depth += 1
            return
        if self.skipped_depth:
            return
        if self.cover_depth:
            if tag == "div":
                self.cover_depth += 1
            return
        if tag == "div":
            if "cover" in classes:
                self.cover_depth = 1
                self.blocks.append({"type": "cover"})
            elif "pb" in classes:
                self.blocks.append({"type": "pagebreak"})
            elif "code" in classes:
                self.code_text = ""
        elif tag == "img" and self.code_text is None:
            self.blocks.append({"type": "figure", "src": attributes.get("src", "")})
        elif tag in ("ul", "ol"):
            self.lists.append({"type": "list", "ordered": tag == "ol", "items": []})
        elif tag in BLOCK_TAGS:
            self.start_block(tag, classes, attributes)
        elif tag == "table":
            self.table = {"type": "table", "rows": []}
        elif tag == "tr":
            self.row = []
        elif tag in ("th", "td"):
            width = re.search(r"width:\s*(\d+)%", attributes.get("style") or "")
            self.cell = {
                "header": tag == "th",
                "runs": [],
                "rowspan": int(attributes.get("rowspan", 1)),
                "width": int(width.group(1)) if width else None,
            }
            self.runs = self.cell["runs"]
        elif tag == "b":
            self.bold_depth += 1
        elif tag == "br" and self.runs is not None:
            self.runs.append({"text": "", "br": True})

    def handle_endtag(self, tag):
        if tag in SKIPPED_TAGS:
            self.skipped_depth -= 1
            return
        if self.skipped_depth:
            return
        if self.cover_depth:
            if tag == "div":
                self.cover_depth -= 1
            return
        if tag == "div" and self.code_text is not None:
            self.blocks.append({"type": "code", "lines": self.code_text.strip("\n").split("\n")})
            self.code_text = None
        elif tag in ("ul", "ol"):
            self.blocks.append(self.lists.pop())
        elif tag in BLOCK_TAGS:
            self.end_block(tag)
        elif tag in ("th", "td"):
            self.row.append(self.cell)
            self.cell = None
            self.runs = None
        elif tag == "tr":
            self.table["rows"].append(self.row)
            self.row = None
        elif tag == "table":
            self.blocks.append(self.table)
            self.table = None
        elif tag == "b":
            self.bold_depth -= 1

    def handle_data(self, data):
        if self.skipped_depth or self.cover_depth:
            return
        if self.code_text is not None:
            self.code_text += data
            return
        if self.runs is None:
            return
        text = re.sub(r"\s+", " ", data)
        if text:
            self.runs.append({"text": text, "bold": self.bold_depth > 0})

    def start_block(self, tag, classes, attributes):
        self.block_kind = tag
        self.block_attributes = {
            "pagebreak": "pb" in classes,
            "caption": "cap" in classes,
            "left": "text-align:left" in (attributes.get("style") or "").replace(" ", ""),
        }
        self.runs = []

    def end_block(self, tag):
        runs = self.tidy(self.runs)
        self.runs = None
        if tag == "li":
            self.lists[-1]["items"].append(runs)
        elif tag == "p":
            kind = "caption" if self.block_attributes["caption"] else "paragraph"
            self.blocks.append({"type": kind, "runs": runs, "left": self.block_attributes["left"]})
        else:
            self.blocks.append({"type": tag, "runs": runs, "pagebreak": self.block_attributes["pagebreak"]})

    @staticmethod
    def tidy(runs):
        cleaned = [dict(run) for run in runs]
        while cleaned and not cleaned[0].get("br"):
            cleaned[0]["text"] = cleaned[0]["text"].lstrip()
            if cleaned[0]["text"] or len(cleaned) == 1:
                break
            cleaned.pop(0)
        if cleaned and not cleaned[-1].get("br"):
            cleaned[-1]["text"] = cleaned[-1]["text"].rstrip()
        return [run for run in cleaned if run.get("br") or run["text"]]


source, destination = sys.argv[1], sys.argv[2]
parser = BlockParser()
parser.feed(open(source, encoding="utf-8").read())
json.dump(parser.blocks, open(destination, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(parser.blocks), "bloques")
