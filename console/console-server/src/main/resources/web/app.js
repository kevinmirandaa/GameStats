const REFRESH_PANEL_MILLISECONDS = 2000;
const REFRESH_SESSIONS_MILLISECONDS = 3000;
const REFRESH_STATUS_MILLISECONDS = 4000;
const CONTINUE_DELAY_MILLISECONDS = 1600;
const STORAGE_KEY = "gameconsole.sessions";

const METRIC_LABELS = {
  totalMoves: "jugadas",
  aiDecisions: "decisiones de Prolog",
  humanWin: "victorias humanas",
  computerWin: "victorias de la computadora",
  draw: "empates",
  abandoned: "partidas abandonadas",
  averageAiNodes: "nodos buscados por decision",
  averageAiMillis: "ms por decision",
  moves: "jugadas",
  wins: "victorias",
  losses: "derrotas",
  draws: "empates",
  pairsFound: "parejas encontradas",
  attempts: "intentos",
  flips: "cartas volteadas",
  accuracyPercent: "precision",
  cellsRevealed: "casillas descubiertas",
  flagsPlaced: "banderas",
  hitMine: "mina pisada",
  won: "victorias",
  secondsElapsed: "segundos de juego",
  durationSeconds: "duracion (s)",
  totalEvents: "eventos",
  totalFlips: "cartas volteadas",
  reveals: "jugadas para descubrir casillas",
  cascades: "descubrimientos en cascada",
  drawn: "empates",
  lost: "derrotas",
  pairsMissed: "parejas falladas",
  aiDecisions: "decisiones de Prolog",
  eventCount: "eventos"
};

const FLAG_METRICS = new Set(["humanWin", "computerWin", "drawn", "abandoned", "won", "lost", "hitMine"]);

const TOTAL_LABELS = { matchesAnalyzed: "Partidas analizadas", matchesCompleted: "Partidas completas", matchesPlayed: "Partidas jugadas", wins: "Victorias" };

const AGGREGATE_PREFIXES = { mean_: "Promedio", min_: "Minimo", max_: "Maximo", total_: "Total", patternCount_: "Patrones" };

const state = {
  games: [],
  sessions: new Map(),
  activeId: null,
  currentView: "console",
  panelTimer: null,
  gameFilter: ""
};

function element(tag, attributes = {}, children = []) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    if (name === "class") node.className = value;
    else if (name === "text") node.textContent = value;
    else if (name.startsWith("on")) node.addEventListener(name.slice(2), value);
    else if (value === true) node.setAttribute(name, "");
    else if (value !== false && value !== null && value !== undefined) node.setAttribute(name, value);
  }
  for (const child of [].concat(children)) {
    if (child === null || child === undefined) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

async function api(path, options = {}) {
  try {
    const response = await fetch(path, {
      method: options.method || "GET",
      headers: options.body ? { "Content-Type": "application/json" } : {},
      body: options.body ? JSON.stringify(options.body) : undefined
    });
    const text = await response.text();
    return { ok: response.ok, status: response.status, data: text ? JSON.parse(text) : null };
  } catch (failure) {
    return { ok: false, status: 0, data: null };
  }
}

function toast(message) {
  const box = document.getElementById("toast");
  box.textContent = message;
  box.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { box.hidden = true; }, 2800);
}

function shortId(matchId) {
  return matchId.slice(matchId.lastIndexOf("-") + 1, matchId.lastIndexOf("-") + 7);
}

function saveSessionIds() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify([...state.sessions.keys()]));
  } catch (unavailable) {
    return;
  }
}

function savedSessionIds() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "[]");
  } catch (unavailable) {
    return [];
  }
}

function switchView(name) {
  state.currentView = name;
  document.getElementById("view-console").hidden = name !== "console";
  document.getElementById("view-panel").hidden = name !== "panel";
  for (const tab of document.querySelectorAll("#main-tabs .tab")) {
    tab.classList.toggle("active", tab.dataset.view === name);
  }
  if (name === "panel") {
    refreshPanel();
    schedulePanelRefresh();
  } else {
    clearTimeout(state.panelTimer);
  }
}

function schedulePanelRefresh() {
  clearTimeout(state.panelTimer);
  if (state.currentView !== "panel") return;
  state.panelTimer = setTimeout(async () => {
    if (document.getElementById("auto-refresh").checked) await refreshPanel();
    schedulePanelRefresh();
  }, REFRESH_PANEL_MILLISECONDS);
}

function renderCatalog() {
  const container = document.getElementById("catalog");
  container.replaceChildren();
  if (state.games.length === 0) {
    container.append(element("div", { class: "empty-note", text: "No hay juegos cargados en la carpeta de plugins." }));
    return;
  }
  for (const game of state.games) {
    const modes = game.modes.map((mode) => mode.label).join(" / ");
    container.append(element("div", { class: "game-card" }, [
      element("h3", { text: game.title }),
      element("p", { text: game.description }),
      element("div", { class: "modes", text: modes }),
      element("button", { class: "primary", text: "Nueva partida", onclick: () => openNewGameDialog(game) })
    ]));
  }
}

function openNewGameDialog(game) {
  const dialog = document.getElementById("new-game-dialog");
  document.getElementById("dialog-title").textContent = game.title;
  document.getElementById("dialog-description").textContent = game.description;
  const modeSelect = document.getElementById("dialog-mode");
  modeSelect.replaceChildren(...game.modes.map((mode) => element("option", { value: mode.id, text: mode.label })));
  const playersBox = document.getElementById("dialog-players");
  const renderPlayers = () => {
    const mode = game.modes.find((candidate) => candidate.id === modeSelect.value);
    playersBox.replaceChildren(...Array.from({ length: mode.playerCount }, (unused, index) =>
      element("label", { text: mode.playerCount === 1 ? "Tu nombre" : `Nombre del jugador ${index + 1}` }, [
        element("input", { type: "text", maxlength: "24", value: defaultName(index), "data-player": index })
      ])));
  };
  modeSelect.onchange = renderPlayers;
  renderPlayers();
  document.getElementById("dialog-options").replaceChildren(...game.options.map((option) =>
    element("label", { text: option.label }, [
      element("select", { "data-option": option.id }, option.choices.map((choice) =>
        element("option", { value: choice, text: choice, selected: choice === option.defaultChoice })))
    ])));
  document.getElementById("new-game-form").onsubmit = async (event) => {
    event.preventDefault();
    const players = [...playersBox.querySelectorAll("input")].map((input) => input.value);
    const options = {};
    for (const select of document.querySelectorAll("#dialog-options select")) options[select.dataset.option] = select.value;
    dialog.close();
    await createSession(game, modeSelect.value, players, options);
  };
  dialog.showModal();
}

function defaultName(index) {
  return index === 0 ? "Jugador" : `Jugador ${index + 1}`;
}

async function createSession(game, mode, players, options) {
  const result = await api("/api/sessions", { method: "POST", body: { gameId: game.gameId, mode, players, options } });
  if (!result.ok) {
    toast(result.data && result.data.error ? result.data.error : "No se pudo iniciar la partida");
    return;
  }
  adopt(result.data);
  refreshServerSessions();
}

function adopt(view) {
  state.sessions.set(view.matchId, { view, busy: false, flagMode: false, continueToken: 0 });
  state.activeId = view.matchId;
  saveSessionIds();
  renderSessionTabs();
  renderGame();
  scheduleContinue(view.matchId);
}

function renderSessionTabs() {
  const container = document.getElementById("session-tabs");
  container.replaceChildren();
  for (const [matchId, session] of state.sessions) {
    const tab = element("div", {
      class: "session-tab" + (matchId === state.activeId ? " active" : "") + (session.view.finished ? " finished" : ""),
      onclick: () => { state.activeId = matchId; renderSessionTabs(); renderGame(); }
    }, [
      element("span", { class: "dot" }),
      element("span", { text: `${session.view.title} ${shortId(matchId)}` }),
      element("button", { class: "close", title: "Cerrar", text: "x", onclick: (event) => { event.stopPropagation(); closeSession(matchId); } })
    ]);
    container.append(tab);
  }
}

async function closeSession(matchId) {
  const session = state.sessions.get(matchId);
  if (session && !session.view.finished) {
    await api(`/api/sessions/${matchId}`, { method: "DELETE" });
  }
  state.sessions.delete(matchId);
  if (state.activeId === matchId) state.activeId = state.sessions.size ? [...state.sessions.keys()].pop() : null;
  saveSessionIds();
  renderSessionTabs();
  renderGame();
  refreshServerSessions();
}

function renderGame() {
  const area = document.getElementById("game-area");
  area.replaceChildren();
  const session = state.activeId ? state.sessions.get(state.activeId) : null;
  if (!session) {
    area.append(element("div", { class: "placeholder" }, [element("div", {}, [
      "Elige un juego del catalogo para iniciar una partida.",
      element("br"),
      "Puedes tener varias partidas abiertas a la vez, tambien en otras pestanas del navegador."
    ])]));
    return;
  }
  const view = session.view;
  const playerPills = view.players.map((player) => element("div", {
    class: "player-pill" + (player.playerId === view.currentPlayerId ? " current" : "") + (player.playerId === view.winnerPlayerId ? " winner" : "")
  }, [player.displayName, element("span", { class: "kind", text: `${player.kind === "COMPUTER" ? "Prolog" : "Humano"} · ${player.score}` })]));
  const head = element("div", { class: "game-head" }, [
    element("div", {}, [
      element("div", { class: "game-title", text: view.title }),
      element("div", { class: "game-status" + (view.finished ? " finished" : ""), text: view.status }),
      element("div", { class: "game-message", text: view.message })
    ]),
    element("div", { class: "players" }, playerPills)
  ]);
  const toolbar = element("div", { class: "toolbar" });
  if (view.extraActions.includes("flag") && !view.finished) {
    toolbar.append(element("button", {
      class: "toggle" + (session.flagMode ? " on" : ""),
      text: session.flagMode ? "Modo bandera activo" : "Modo bandera",
      onclick: () => { session.flagMode = !session.flagMode; renderGame(); }
    }));
  }
  if (!view.finished) {
    toolbar.append(element("button", { class: "ghost", text: "Abandonar partida", onclick: () => closeSession(view.matchId) }));
  }
  if (session.busy) toolbar.append(element("span", { class: "thinking", text: "Procesando jugada..." }));

  const board = element("div", {
    class: "board" + (session.busy ? " busy" : ""),
    "data-game": ["connect4", "memory", "minesweeper"].includes(view.gameId) ? view.gameId : "generic",
    style: `--columns:${view.columns}`
  });
  view.cells.forEach((cell, index) => {
    board.append(element("button", {
      class: "cell " + cell.style,
      text: cell.text,
      disabled: !cell.enabled || view.finished || session.busy,
      onclick: () => act(view.matchId, index, session.flagMode),
      oncontextmenu: (event) => {
        event.preventDefault();
        if (view.extraActions.includes("flag") && cell.enabled) act(view.matchId, index, true);
      }
    }));
  });
  area.append(head, toolbar, element("div", { class: "board-wrap" }, [board]));
}

async function act(matchId, cellIndex, flag) {
  const session = state.sessions.get(matchId);
  if (!session || session.busy) return;
  session.busy = true;
  renderGame();
  const action = flag ? { cell: cellIndex, flag: true } : { cell: cellIndex };
  const result = await api(`/api/sessions/${matchId}/actions`, { method: "POST", body: { playerId: session.view.currentPlayerId, action } });
  session.busy = false;
  if (result.data && result.data.view) {
    session.view = result.data.view;
    if (!result.data.accepted && result.data.message) toast(result.data.message);
  } else {
    toast("No se pudo enviar la jugada");
  }
  renderSessionTabs();
  if (state.activeId === matchId) renderGame();
  scheduleContinue(matchId);
}

function scheduleContinue(matchId) {
  const session = state.sessions.get(matchId);
  if (!session || session.view.finished || !session.view.extraActions.includes("continue")) return;
  const token = ++session.continueToken;
  setTimeout(async () => {
    const current = state.sessions.get(matchId);
    if (!current || current.continueToken !== token || current.busy || !current.view.extraActions.includes("continue")) return;
    const result = await api(`/api/sessions/${matchId}/actions`, {
      method: "POST",
      body: { playerId: current.view.currentPlayerId, action: { continue: true } }
    });
    if (result.data && result.data.view) current.view = result.data.view;
    if (state.activeId === matchId) renderGame();
  }, CONTINUE_DELAY_MILLISECONDS);
}

async function refreshOpenSessions() {
  let changed = false;
  for (const [matchId, session] of [...state.sessions]) {
    if (session.busy) continue;
    const result = await api(`/api/sessions/${matchId}`);
    if (result.status === 404) {
      state.sessions.delete(matchId);
      if (state.activeId === matchId) state.activeId = state.sessions.size ? [...state.sessions.keys()].pop() : null;
      changed = true;
    } else if (result.ok && JSON.stringify(result.data) !== JSON.stringify(session.view)) {
      session.view = result.data;
      changed = true;
    }
  }
  if (!changed) return;
  saveSessionIds();
  renderSessionTabs();
  renderGame();
}

async function refreshServerSessions() {
  const result = await api("/api/sessions");
  const container = document.getElementById("server-sessions");
  container.replaceChildren();
  const rows = (result.data || []).filter((session) => !session.finished);
  if (rows.length === 0) {
    container.append(element("div", { class: "empty-note", text: "No hay partidas abiertas." }));
    return;
  }
  for (const row of rows) {
    const opened = state.sessions.has(row.matchId);
    container.append(element("div", { class: "server-session" }, [
      element("span", { class: "grow", text: `${row.gameId} ${shortId(row.matchId)} · ${row.status}` }),
      opened ? null : element("button", { text: "Abrir", onclick: () => openExisting(row.matchId) })
    ]));
  }
}

async function openExisting(matchId) {
  const result = await api(`/api/sessions/${matchId}`);
  if (result.ok) adopt(result.data);
}

async function refreshChips() {
  const [health, delivery] = await Promise.all([api("/api/health"), api("/api/delivery")]);
  const chips = document.getElementById("status-chips");
  chips.replaceChildren();
  const prologUp = health.ok && health.data.prologAvailable;
  chips.append(chip(prologUp ? "ok" : "bad", prologUp ? "Prolog activo" : "Prolog sin respuesta"));
  if (delivery.ok) {
    const stats = delivery.data.statistics;
    const level = stats.failed > 0 ? "bad" : stats.pending > 0 ? "warn" : "ok";
    chips.append(chip(level, `Eventos a GameStats: ${stats.delivered} enviados, ${stats.pending} en cola, ${stats.failed} fallidos`));
  } else {
    chips.append(chip("bad", "Consola sin respuesta"));
  }
}

function chip(level, text) {
  return element("span", { class: `chip ${level}` }, [element("span", { class: "dot" }), text]);
}

function friendlyMetric(name) {
  if (TOTAL_LABELS[name]) return TOTAL_LABELS[name];
  if (name.startsWith("mean_") && FLAG_METRICS.has(name.slice(5))) return `Porcentaje de ${friendlyBase(name.slice(5))}`;
  for (const [prefix, label] of Object.entries(AGGREGATE_PREFIXES)) {
    if (name.startsWith(prefix)) return `${label} · ${friendlyBase(name.slice(prefix.length))}`;
  }
  return friendlyBase(name);
}

function formatMetric(name, value) {
  if (name.startsWith("mean_") && FLAG_METRICS.has(name.slice(5))) return `${formatNumber(value * 100)} %`;
  return formatNumber(value);
}

function metricRow(name, value) {
  return element("div", { class: "metric-row" }, [
    element("span", { class: "name", text: friendlyMetric(name) }),
    element("span", { text: formatMetric(name, value) })
  ]);
}

function metricBlock(aggregates) {
  const names = Object.keys(aggregates);
  if (names.length === 0) return [element("div", { class: "empty-note", text: "Aun no hay partidas analizadas de este juego." })];
  const byLabel = (a, b) => friendlyMetric(a).localeCompare(friendlyMetric(b));
  const main = names.filter((name) => TOTAL_LABELS[name] || name.startsWith("mean_")).sort(byLabel);
  const extremes = names.filter((name) => !main.includes(name)).sort(byLabel);
  const rows = main.map((name) => metricRow(name, aggregates[name]));
  if (extremes.length > 0) {
    rows.push(element("details", { class: "extremes" }, [
      element("summary", { text: "Minimos, maximos y patrones" }),
      ...extremes.map((name) => metricRow(name, aggregates[name]))
    ]));
  }
  return rows;
}

function friendlyBase(name) {
  if (METRIC_LABELS[name]) return METRIC_LABELS[name];
  return name.replace(/([A-Z])/g, " $1").toLowerCase();
}

function formatNumber(value) {
  if (typeof value !== "number") return String(value);
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

function statCard(label, value, hint, accent) {
  return element("div", { class: "stat-card" + (accent ? " accent" : "") }, [
    element("div", { class: "label", text: label }),
    element("div", { class: "value", text: value }),
    hint ? element("div", { class: "hint", text: hint }) : null
  ]);
}

async function refreshPanel() {
  const [overview, live] = await Promise.all([api("/api/stats/overview"), api("/api/stats/live")]);
  const overviewData = overview.ok ? overview.data : { totalMatches: 0, completedMatches: 0, incompleteMatches: 0, totalEvents: 0, playerCount: 0, games: [] };
  const liveData = live.ok ? live.data : { activeMatches: 0, activeMatchesByGame: {}, eventsAccepted: 0, eventsAcceptedByGame: {} };
  const activeByGame = liveData.activeMatchesByGame || {};
  const eventsByGame = liveData.eventsAcceptedByGame || {};
  const finishedByGame = Object.fromEntries((overviewData.games || []).map((game) => [game.gameId, game]));
  const gameIds = [...new Set([...state.games.map((game) => game.gameId), ...Object.keys(finishedByGame), ...Object.keys(activeByGame)])];
  const titles = Object.fromEntries(state.games.map((game) => [game.gameId, game.title]));
  const activeTotal = Object.values(activeByGame).reduce((sum, count) => sum + count, 0);

  const general = document.getElementById("general-cards");
  general.replaceChildren(
    statCard("Instancias ejecutadas", String(overviewData.totalMatches + activeTotal), "finalizadas y en curso", true),
    statCard("En curso ahora", String(activeTotal), "partidas abiertas en GameStats"),
    statCard("Finalizadas", String(overviewData.totalMatches), `${overviewData.completedMatches} completas, ${overviewData.incompleteMatches} incompletas`),
    statCard("Jugadores distintos", String(overviewData.playerCount), "entre todos los juegos"),
    statCard("Eventos recibidos", String(liveData.eventsAccepted || 0), "aceptados por GameStats")
  );

  const maximum = Math.max(1, ...gameIds.map((gameId) => (finishedByGame[gameId] ? finishedByGame[gameId].matches : 0) + (activeByGame[gameId] || 0)));
  const bars = document.getElementById("instances-chart");
  bars.replaceChildren(element("div", { class: "bar-legend" }, [element("span", { text: "Finalizadas" }), element("span", { class: "live", text: "En curso" })]));
  for (const gameId of gameIds) {
    const done = finishedByGame[gameId] ? finishedByGame[gameId].matches : 0;
    const live = activeByGame[gameId] || 0;
    bars.append(element("div", { class: "bar-row" }, [
      element("div", { text: titles[gameId] || gameId }),
      element("div", { class: "bar-track" }, [
        element("div", { class: "bar-done", style: `width:${(done / maximum) * 100}%` }),
        element("div", { class: "bar-live", style: `width:${(live / maximum) * 100}%` })
      ]),
      element("div", { class: "muted", text: `${done + live} en total` })
    ]));
  }

  const cards = document.getElementById("game-cards");
  const details = await Promise.all(gameIds.map((gameId) => api(`/api/stats/games/${gameId}`)));
  cards.replaceChildren();
  gameIds.forEach((gameId, index) => {
    const finished = finishedByGame[gameId];
    const aggregates = details[index].ok ? details[index].data.aggregates : {};
    cards.append(element("div", { class: "game-result" }, [
      element("h3", { text: titles[gameId] || gameId }),
      element("div", { class: "mini-stats" }, [
        miniStat(String((finished ? finished.matches : 0) + (activeByGame[gameId] || 0)), "instancias"),
        miniStat(String(activeByGame[gameId] || 0), "en curso"),
        miniStat(String(eventsByGame[gameId] || 0), "eventos")
      ]),
      element("div", { class: "metric-list" }, metricBlock(aggregates))
    ]));
  });

  const filter = document.getElementById("match-filter");
  if (filter.options.length - 1 !== gameIds.length) {
    filter.replaceChildren(element("option", { value: "", text: "Todos los juegos" }), ...gameIds.map((gameId) => element("option", { value: gameId, text: titles[gameId] || gameId })));
    filter.value = state.gameFilter;
  }
  const query = state.gameFilter ? `?gameId=${encodeURIComponent(state.gameFilter)}` : "";
  const matches = await api(`/api/stats/matches${query}`);
  renderMatchTable(matches.ok ? matches.data : [], titles);
}

function miniStat(value, label) {
  return element("div", {}, [element("div", { class: "value", text: value }), element("div", { class: "label", text: label })]);
}

function renderMatchTable(matches, titles) {
  const wrap = document.getElementById("match-table");
  wrap.replaceChildren();
  if (matches.length === 0) {
    wrap.append(element("div", { class: "empty-note", text: "Todavia no hay partidas finalizadas. Juega una partida hasta el final para verla aqui." }));
    return;
  }
  const rows = matches.slice(-15).reverse().map((match) => element("tr", {}, [
    element("td", { text: titles[match.gameId] || match.gameId }),
    element("td", { text: match.matchId }),
    element("td", {}, [element("span", { class: "badge " + match.status.toLowerCase(), text: match.status === "COMPLETED" ? "Completa" : "Incompleta" })]),
    element("td", { text: String(match.totalEvents) }),
    element("td", { text: `${formatNumber(match.durationSeconds)} s` })
  ]));
  wrap.append(element("table", {}, [
    element("thead", {}, [element("tr", {}, ["Juego", "Partida", "Estado", "Eventos", "Duracion"].map((title) => element("th", { text: title })))]),
    element("tbody", {}, rows)
  ]));
}

async function start() {
  for (const tab of document.querySelectorAll("#main-tabs .tab")) {
    tab.addEventListener("click", () => switchView(tab.dataset.view));
  }
  document.getElementById("dialog-cancel").addEventListener("click", () => document.getElementById("new-game-dialog").close());
  document.getElementById("auto-refresh").addEventListener("change", () => schedulePanelRefresh());
  document.getElementById("match-filter").addEventListener("change", (event) => { state.gameFilter = event.target.value; refreshPanel(); });

  if (location.hash === "#panel") switchView("panel");
  const games = await api("/api/games");
  state.games = games.ok ? games.data : [];
  renderCatalog();

  for (const matchId of savedSessionIds()) {
    const result = await api(`/api/sessions/${matchId}`);
    if (result.ok) {
      state.sessions.set(matchId, { view: result.data, busy: false, flagMode: false, continueToken: 0 });
      state.activeId = matchId;
    }
  }
  saveSessionIds();
  renderSessionTabs();
  renderGame();
  refreshServerSessions();
  refreshChips();
  setInterval(refreshChips, REFRESH_STATUS_MILLISECONDS);
  setInterval(refreshOpenSessions, REFRESH_SESSIONS_MILLISECONDS);
  setInterval(refreshServerSessions, REFRESH_SESSIONS_MILLISECONDS * 2);
}

start();
