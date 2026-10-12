import os
import re
import sys

REPOSITORY_ROOT = sys.argv[1]
EXCLUDED_DIRECTORIES = {".git", "target", ".bsp", "node_modules", ".idea", ".metals", ".bloop", "dist"}
SLASH_STYLE_EXTENSIONS = {".go", ".scala", ".sbt"}
HASH_STYLE_EXTENSIONS = {".sh", ".yml", ".yaml", ".py"}
HASH_STYLE_NAMES = {"Dockerfile", "Makefile"}
MARKDOWN_EXTENSIONS = {".md"}
EMOJI_RANGES = [(0x1F000, 0x1FAFF), (0x2600, 0x27BF), (0x2B50, 0x2B55), (0xFE0F, 0xFE0F), (0x200D, 0x200D), (0x2190, 0x21FF), (0x2300, 0x23FF), (0x25A0, 0x25FF)]
EMOJI_PATTERN = re.compile("[" + "".join(chr(low) + "-" + chr(high) for low, high in EMOJI_RANGES) + "]")
DECORATIVE_PATTERN = re.compile(r"([-=*_#~+])\1{4,}")
MARKDOWN_RULE_PATTERN = re.compile(r"^\s*([-=*_])\1{2,}\s*$")
SCALA_FORBIDDEN_PATTERN = re.compile(r"\b(var|null|throw)\b")
GAME_NAME_PATTERN = re.compile(r"racing|combat", re.IGNORECASE)
IGNORED_ERROR_PATTERN = re.compile(r"^\s*_\s*(,\s*_\s*)?(:=|=)")
DISCARDED_VALUE_PATTERN = re.compile(r"^\s*(?!for\b)[\w.\[\]]+(\s*,\s*[\w.\[\]]+)*\s*,\s*_\s*(:=|=)")

findings = []


def record(category, path, line_number, text):
    findings.append((category, os.path.relpath(path, REPOSITORY_ROOT), line_number, text.strip()[:110]))


def source_files():
    for directory, subdirectories, names in os.walk(REPOSITORY_ROOT):
        subdirectories[:] = [name for name in subdirectories if name not in EXCLUDED_DIRECTORIES]
        for name in names:
            yield os.path.join(directory, name)


def kind_of(path):
    name = os.path.basename(path)
    extension = os.path.splitext(name)[1]
    relative = os.path.relpath(path, REPOSITORY_ROOT)
    if relative.startswith(("agents", "prompts", "reports", "project", "docs", "contracts")) and extension not in MARKDOWN_EXTENSIONS:
        return None
    if extension in SLASH_STYLE_EXTENSIONS:
        return "slash"
    if extension in HASH_STYLE_EXTENSIONS or name in HASH_STYLE_NAMES or name.startswith("Dockerfile"):
        return "hash"
    if extension in MARKDOWN_EXTENSIONS:
        return "markdown"
    return None


def read_lines(path):
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read().split("\n")


def slash_comment_lines(lines, raw_strings_allowed):
    found = {}
    in_block = False
    in_triple = False
    in_raw = False
    for number, line in enumerate(lines, 1):
        index = 0
        in_double = False
        in_single = False
        while index < len(line):
            char = line[index]
            pair = line[index:index + 2]
            if in_block:
                found[number] = True
                if pair == "*/":
                    in_block = False
                    index += 1
            elif in_triple:
                if line[index:index + 3] == '"""':
                    in_triple = False
                    index += 2
            elif in_raw:
                if char == "`":
                    in_raw = False
            elif in_double:
                if char == "\\":
                    index += 1
                elif char == '"':
                    in_double = False
            elif in_single:
                if char == "\\":
                    index += 1
                elif char == "'":
                    in_single = False
            elif pair == "//":
                found[number] = True
                break
            elif pair == "/*":
                found[number] = True
                in_block = True
                index += 1
            elif line[index:index + 3] == '"""':
                in_triple = True
                index += 2
            elif char == '"':
                in_double = True
            elif char == "`" and raw_strings_allowed:
                in_raw = True
            elif char == "'" and raw_strings_allowed and re.match(r"'(\\.|[^'\\])'", line[index:]):
                in_single = True
            index += 1
    return found


def hash_comment_lines(lines, path):
    found = {}
    for number, line in enumerate(lines, 1):
        if number == 1 and line.startswith("#!"):
            continue
        in_double = False
        in_single = False
        for index, char in enumerate(line):
            if char == "\\" and not in_single:
                continue
            if char == '"' and not in_single and (index == 0 or line[index - 1] != "\\"):
                in_double = not in_double
            elif char == "'" and not in_double:
                in_single = not in_single
            elif char == "#" and not in_double and not in_single and (index == 0 or line[index - 1].isspace()):
                found[number] = True
                break
    return found


def code_without_comments_and_strings(line):
    result = re.sub(r'"(\\.|[^"\\])*"', '""', line)
    return result.split("//")[0]


def is_scala_production(relative):
    return relative.startswith("analytics-scala/src/main/") and relative.endswith(".scala")


def is_scala_game_zone(relative):
    return "/analysis/games/" in relative or relative.endswith("/Main.scala")


def is_test_file(relative):
    return relative.endswith("_test.go") or "/testsupport/" in relative


def is_go_core(relative):
    return relative.startswith("ingest-go/") and relative.endswith(".go")


def scan_file(path):
    kind = kind_of(path)
    if kind is None:
        return
    relative = os.path.relpath(path, REPOSITORY_ROOT)
    lines = read_lines(path)
    comment_lines = {}
    if kind == "slash":
        comment_lines = slash_comment_lines(lines, relative.endswith(".go"))
    elif kind == "hash":
        comment_lines = hash_comment_lines(lines, path)
    for number, line in enumerate(lines, 1):
        if number in comment_lines:
            record("COMENTARIO", path, number, line)
        if EMOJI_PATTERN.search(line):
            record("EMOJI", path, number, line)
        if kind == "markdown":
            if MARKDOWN_RULE_PATTERN.match(line) and not line.strip().startswith("|"):
                record("LINEA_DECORATIVA", path, number, line)
            continue
        if DECORATIVE_PATTERN.search(line) and number not in comment_lines:
            record("LINEA_DECORATIVA", path, number, line)
        if is_scala_production(relative) and number not in comment_lines:
            if SCALA_FORBIDDEN_PATTERN.search(re.sub(r'"(\\.|[^"\\])*"', '""', line)):
                record("SCALA_VAR_NULL_THROW", path, number, line)
        if is_scala_production(relative) and not is_scala_game_zone(relative) and GAME_NAME_PATTERN.search(line):
            record("JUEGO_EN_NUCLEO_SCALA", path, number, line)
        if is_go_core(relative):
            if GAME_NAME_PATTERN.search(line):
                scope = "JUEGO_EN_NUCLEO_GO_PRUEBA" if is_test_file(relative) else "JUEGO_EN_NUCLEO_GO"
                record(scope, path, number, line)
            if IGNORED_ERROR_PATTERN.match(line):
                record("ERROR_IGNORADO_GO", path, number, line)
        if relative.endswith(".go") and DISCARDED_VALUE_PATTERN.match(line):
            scope = "VALOR_DESCARTADO_GO_PRUEBA" if is_test_file(relative) else "VALOR_DESCARTADO_GO"
            record(scope, path, number, line)


for each_path in sorted(source_files()):
    scan_file(each_path)

findings.sort(key=lambda item: (item[0], item[1], item[2]))
counts = {}
for category, relative, number, text in findings:
    print("%s %s:%d: %s" % (category, relative, number, text))
    counts[category] = counts.get(category, 0) + 1
print("")
for category in sorted(counts):
    print("RESUMEN %s %d" % (category, counts[category]))
print("RESUMEN TOTAL %d" % len(findings))
blocking = [item for item in findings if not item[0].endswith("_PRUEBA")]
sys.exit(1 if blocking else 0)
