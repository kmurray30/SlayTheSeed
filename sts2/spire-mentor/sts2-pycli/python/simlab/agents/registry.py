"""Agent registry. Strategies are referenced by name in SweepSpec."""

from simlab.agents.base import Agent
from simlab.agents.heuristics import attack_first, block_first, min_overblock, random_agent
from simlab.agents.mcts_agent import mcts_agent

AGENTS: dict[str, Agent] = {
    "random": random_agent,
    "block_first": block_first,
    "attack_first": attack_first,
    "min_overblock": min_overblock,
    "mcts": mcts_agent,
}
