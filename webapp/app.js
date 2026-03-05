let runId = null;

document.getElementById('start-btn').addEventListener('click', startRun);

async function startRun() {
    const seed = parseInt(document.getElementById('seed').value, 10);
    const playerClass = document.getElementById('playerClass').value;
    const ascension = parseInt(document.getElementById('ascension').value, 10);

    try {
        const res = await fetch('/api/run/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ seed, playerClass, ascensionLevel: ascension })
        });
        const data = await res.json();
        if (!res.ok) {
            alert(data.error || 'Failed to start run');
            return;
        }

        runId = data.runId;
        document.getElementById('start-section').classList.add('hidden');
        document.getElementById('run-section').classList.remove('hidden');

        renderRunState(data.runState);
        renderMap(data.actMap);
        renderDecision(data.decision, data.actMap);
    } catch (err) {
        alert('Error: ' + err.message);
    }
}

async function applyChoice(choice) {
    if (!runId) return;

    try {
        const res = await fetch(`/api/run/${runId}/choose`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ choice })
        });
        const data = await res.json();
        if (!res.ok) {
            alert(data.error || 'Failed to apply choice');
            return;
        }

        if (data.complete) {
            runId = null;
            document.getElementById('run-section').classList.add('hidden');
            document.getElementById('start-section').classList.remove('hidden');
            alert('Run complete!');
            return;
        }

        renderRunState(data.runState);
        renderMap(data.actMap);
        renderDecision(data.decision, data.actMap);
    } catch (err) {
        alert('Error: ' + err.message);
    }
}

function renderRunState(state) {
    if (!state) return;
    const el = document.getElementById('run-state');
    el.innerHTML = `
        <div class="stat">Floor: ${state.floor}</div>
        <div class="stat">Gold: ${state.gold}</div>
        <div class="stat">Act: ${state.actName}</div>
        <div class="stat">Deck: ${state.deckCardIds ? state.deckCardIds.length : 0} cards</div>
        <div class="stat">Relics: ${state.relicIds ? state.relicIds.length : 0}</div>
    `;
}

function renderMap(actMap) {
    const container = document.getElementById('map-container');
    if (!actMap || !actMap.nodes || actMap.nodes.length === 0) {
        container.innerHTML = '<p>No map data</p>';
        return;
    }

    const width = 400;
    const height = 300;
    const padding = 40;
    const rows = 15;
    const cols = 7;
    const cellW = (width - 2 * padding) / cols;
    const cellH = (height - 2 * padding) / rows;

    let svg = `<svg viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg">`;

    if (actMap.edges && actMap.edges.length > 0) {
        actMap.edges.forEach(edge => {
            const fromX = padding + edge.fromX * cellW + cellW / 2;
            const fromY = padding + edge.fromY * cellH + cellH / 2;
            const toX = padding + edge.toX * cellW + cellW / 2;
            const toY = padding + edge.toY * cellH + cellH / 2;
            svg += `<line x1="${fromX}" y1="${fromY}" x2="${toX}" y2="${toY}" stroke="#444" stroke-width="1"/>`;
        });
    }

    actMap.nodes.forEach(node => {
        const x = padding + node.x * cellW + cellW / 2;
        const y = padding + node.y * cellH + cellH / 2;
        const r = Math.min(cellW, cellH) * 0.35;
        svg += `<circle class="map-node ${node.roomType}" cx="${x}" cy="${y}" r="${r}" title="${node.symbol}"/>`;
        svg += `<text x="${x}" y="${y + 4}" text-anchor="middle" font-size="10" fill="#fff">${node.symbol}</text>`;
    });

    svg += '</svg>';
    container.innerHTML = svg;
}

function renderDecision(decision, actMap) {
    const container = document.getElementById('decision-container');
    if (!decision) {
        container.innerHTML = '<p>No decision pending</p>';
        return;
    }

    const type = decision.type;
    let html = `<div class="decision-title">Choose: ${type}</div>`;

    const nodeLabel = (nodeId) => {
        if (!actMap?.nodes) return nodeId;
        const [y, x] = nodeId.split(',').map(Number);
        const node = actMap.nodes.find(n => n.y === y && n.x === x);
        return node ? `${node.symbol} (${nodeId})` : nodeId;
    };

    if (type === 'neow' && decision.options) {
        html += '<div class="choice-buttons">';
        decision.options.forEach((opt, idx) => {
            html += `<button class="choice-btn" data-choice="${idx}">${opt}</button>`;
        });
        html += '</div>';
    } else if (type === 'path' && decision.nextNodeIds) {
        html += '<div class="choice-buttons">';
        decision.nextNodeIds.forEach(nodeId => {
            html += `<button class="choice-btn" data-choice="${nodeId}">${nodeLabel(nodeId)}</button>`;
        });
        html += '</div>';
    } else if (type === 'bossRelic' && decision.relicIds) {
        html += '<div class="choice-buttons">';
        decision.relicIds.forEach(relicId => {
            html += `<button class="choice-btn" data-choice="${relicId}">${relicId}</button>`;
        });
        if (decision.canSkip) {
            html += '<button class="choice-btn" data-choice="">Skip</button>';
        }
        html += '</div>';
    } else if (type === 'cardReward' && decision.cards) {
        html += '<div class="choice-buttons">';
        decision.cards.forEach(card => {
            html += `<button class="choice-btn" data-choice="${card.cardId}">${card.name || card.cardId}</button>`;
        });
        if (decision.canSkip) {
            html += '<button class="choice-btn" data-choice="">Skip</button>';
        }
        html += '</div>';
    } else if (type === 'shop' && (decision.cards?.length || decision.relics?.length || decision.potions?.length)) {
        html += `<div class="shop-choices">Gold: ${decision.gold || 0}<br>`;
        if (decision.cards?.length) {
            decision.cards.forEach(card => {
                html += `<button class="choice-btn" data-choice="${card.cardId}">${card.name || card.cardId} (${card.price}g)</button>`;
            });
        }
        if (decision.relics?.length) {
            decision.relics.forEach(relic => {
                html += `<button class="choice-btn" data-choice="${relic.relicId}">${relic.name || relic.relicId} (${relic.price}g)</button>`;
            });
        }
        if (decision.potions?.length) {
            decision.potions.forEach(potion => {
                html += `<button class="choice-btn" data-choice="${potion.potionId}">${potion.name || potion.potionId} (${potion.price}g)</button>`;
            });
        }
        html += '<button class="choice-btn" data-choice="">Done</button></div>';
    } else if (type === 'event' && decision.options) {
        html += '<div class="choice-buttons">';
        decision.options.forEach(opt => {
            html += `<button class="choice-btn" data-choice="${opt.id}">${opt.label || opt.id}</button>`;
        });
        html += '</div>';
    } else {
        html += '<p>Decision type not yet supported in UI</p>';
    }

    container.innerHTML = html;
    container.querySelectorAll('.choice-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const choice = btn.dataset.choice;
            let parsed;
            if (choice === '') {
                parsed = '';
            } else if (type === 'neow') {
                parsed = parseInt(choice, 10);
            } else {
                parsed = choice;
            }
            applyChoice(parsed);
        });
    });
}
