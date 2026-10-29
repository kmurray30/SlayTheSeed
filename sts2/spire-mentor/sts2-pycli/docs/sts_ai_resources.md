# Slay-I
https://medium.com/data-science/bringing-deep-neural-networks-to-slay-the-spire-a2971d5a5115

Slay-I uses a machine learning model trained on over 325, 000 fights to

1. Predict how much damage you will take in a fight

2. Evaluate adding, upgrading and removing cards from your deck according to average damage reduced in a fight

For more information on how the AI works, check out the article I wrote up on Medium [towardsdatascience.com].

Machine Learning

Slay-I was trained on over 325, 000 fights from Spire Logs [spirelogs.com] and Jorbs . If you're interested in learning more about how the machine learning model works, checkout the Google Colab notebook [colab.research.google.com] and train it yourself!

Card Evaluation

Cards are evaluated based on the average damage reduced in a fight by taking the card. This average is calculated against your current act and next act independently. The calculation is an average between damage saved in boss(es), elites, and hard hallway fights, weighted more heavily towards fights that do more damage.

A percentile ranking is also calculated that compares a card against all the cards in a character's card pool. Percentiles are computed on a scale of 1 to 100. A card that is ranked in the 75th percentile is better than 75% of cards and a card in the 10th percentile is better than 10% of cards (aka is really bad).

Accuracy

Slay-I is generally within +/- 7 HP of how much damage you will take in a fight. For adding, removing, and upgrading cards, the score is the average damage saved in a fight. The model isn't perfect, but it does a pretty good job at evaluating cards!

Only Base Game Characters and Content

Because the only data available to train the machine learning model with is from base game characters, the mod is only compatible with base game cards, relics and enemies (aka. the four STS1 base classes)

# Analysis of Uncertainty in Procedural Maps in Slay the Spire
paper
(2025)
Abstract.

This work investigates the role of uncertainty in Slay the Spire using an information-theoretic framework. Focusing on the entropy of game paths (which are based on procedurally-generated maps) we analyze how randomness influences player decision-making and success. By examining a dataset of 20,000 game runs, we quantify the entropy of paths taken by players and relate it with their outcomes and skill levels. The results show that victorious runs are associated with higher normalized entropy, suggesting more risk-taking. Additionally, higher-skill players tend to exhibit distinct patterns of risk-taking behavior in later game stages.
Entropy, Puzzle, Slay the Spire, Uncertainty, Risk-Taking 

https://arxiv.org/html/2504.03918v1

# MCP The Spire
A Slay the Spire mod that enables AI agents to play the game through the Model Context Protocol (MCP). This allows AI assistants like Claude, GPT, or other MCP-compatible clients to interact with and control Slay the Spire gameplay programmatically.

source code: https://github.com/ifree/MCPTheSpire

https://steamcommunity.com/workshop/filedetails/?id=3632714834

same for Slay The Spire 2:
https://github.com/Gennadiyev/STS2MCP

# STS Agent - LLM-powered AI assistant that uses this mod (MCP The Spire)
STS Agent - AI Assistant for Slay the Spire

An LLM-powered mod that adds an intelligent AI assistant to your game. Ask questions, get strategic advice, or let AI play for you.

source code: https://github.com/ifree/STSAgent

Features

    In-game chat overlay (F8 to toggle, draggable window)
    Strategic analysis with full game state awareness
    Quick combat tips for optimal card play
    Autonomous play mode (requires MCPTheSpire)
    Conversation memory with auto-summarization
    Works with OpenAI, Claude, local models, etc.


Hotkeys

    F8 - Toggle chat window
    F9 - Quick analyze current state


Chat Commands

    /analyze - Get detailed strategic analysis
    /tip - Quick combat suggestion
    /clear - Clear chat history
    /help - Show commands

# sts2 agent 
https://github.com/Alex5418/STS2-Agent

A local LLM agent that plays Slay the Spire 2 autonomously using open-source models.

Built on top of STS2MCP by @Gennadiyev, which provides the C# BepInEx mod that exposes the game as a REST API.
How It Works

Local LLM (Qwen3.5-27B / Phi-4 / etc.)
    │ OpenAI-compatible API
    ▼
agent.py ── main loop: observe → think → act
    │ HTTP requests
    ▼
STS2MCP mod (inside game process, localhost:15526)
    │
    ▼
Slay the Spire 2



# MiniSTS
https://github.com/iambb5445/MiniSTS
https://ceur-ws.org/Vol-3926/paper7.pdf

This repository includes a simplified implementation of the core loop of the game Slay the Spire. This implementation is introduced with the goal of enabling experimentations with adding new cards and game playing agents. MiniStS is discussed as a testbed for dynamic rule systems in paper "MiniStS: A Testbed for Dynamic Rule Exploration" by Bahar Bateni and Jim Whitehead, published at the 20th AAAI Conference on Artificial Intelligence and Interactive Digital Entertainment (AIIDE), workshop on Experimental AI in Games (EXAG), 2024.

MiniStS is also used to explore the use of Large Language Models (LLMs) as game playing agents and the effectiveness of language-based reasoning for game-playing. The results can be found in the paper "Language-Driven Play: Large Language Models as Game-Playing Agents in Slay the Spire" by Bahar Bateni and Jim Whitehead, published at Foundation of Digital Games Conference (FDG) 2024.

# Using machine learning to help find paths through the map in Slay the Spire - thesis
https://www.diva-portal.org/smash/get/diva2:1565751/FULLTEXT02

# Language-Driven Play: Large Language Models as Game-Playing Agents in Slay the Spire
paper 
https://dl.acm.org/doi/pdf/10.1145/3649921.3650013

abstract
One of the major challenges in procedural generation of game rules
is evaluating the generated content. Since the effect of a rule on
game balance and complexity might not be immediately apparent,
one way to evaluate such a content is to simulate the gameplay.
To achieve this, it is necessary to create an agent capable of both
playing the game and adjusting to alterations in the game’s design,
which is often referred to as a general game-playing agent.
In this paper, we study the ability of Large Language Models
(LLMs) in performing this task. Our focus centers on a simplified
implementation of the card game Slay the Spire, in which the rules
support a wide variety of interesting and complex interactions
between the cards. We analyze the performance of LLMs in un-
derstanding the cards and their synergies based solely on their
description. The performance of the LLM agent is evaluated against
alternative game-playing agents across diverse scenarios. Our find-
ings reveal that although the LLM agent may not be optimized
for finding the best move, it exhibits superior long-term planning
without necessitating specialized training.

# Rule Synergy Analysis using LLMs: State of the Art and Implications
paper 

https://arxiv.org/pdf/2508.19484
https://github.com/iambb5445/Language-based-Synergy-Detection

Bahar Bateni, Benjamin Pratt, and Jim Whitehead
Abstract—Large language models (LLMs) have demonstrated
strong performance across a variety of domains, including logical
reasoning, mathematics, and more. In this paper, we investigate
how well LLMs understand and reason about complex rule
interactions in dynamic environments, such as card games. We
introduce a dataset of card synergies from the game Slay the
Spire, where pairs of cards are classified based on their positive,
negative, or neutral interactions. Our evaluation shows that while
LLMs excel at identifying non-synergistic pairs, they struggle
with detecting positive and, particularly, negative synergies. We
categorize common error types, including issues with timing,
defining game states, and following game rules. Our findings
suggest directions for future research to improve model perfor-
mance in predicting the effect of rules and their interactions.
Index Terms—Procedural Content Generation, Large Lan-
guage Models, Collectible Card Games


# SpireEval

A benchmarking framework for testing AI agents in a card-based roguelike environment.
https://github.com/Max-Suslov/SpireEval

Spireeval: Transforming Gameplay into Sequential Datasets for LLM Decision-Making Analysis

    Maxim Suslov, Shivashish Khanna, Zirui Liu
    Published in IEEE International Conference… 12 November 2025
    Computer Science
    2025 IEEE International Conference on Data Mining Workshops (ICDMW)

An open-source architecture, SpireEval, is presented for instrumenting “Slay The Spire” to generate structured, finegrained datasets to evaluate LLM strategic performance, using it as a case study to allow for the development of strong benchmarks within similar procedural game environments.
Abstract
As Artificial Intelligence (AI) systems, particularly Large Language Models (LLMs), become more capable, evaluating their decision-making processes, not just their outcomes, requires environments that are rich, dynamic, and strategically deep. We present an open-source architecture, SpireEval, for instrumenting “Slay The Spire” to generate structured, finegrained datasets to evaluate LLM strategic performance, using it as a case study to allow for the development of strong benchmarks within similar procedural game environments. Our primary contribution in this paper is a framework to connect powerful chatbots to Slay The Spire as a channel to measure the decisionmaking capacity of advanced models. The paper details our opensource repository, which provides the tools for LLM agent interaction with the game state of Slay the Spire to then gather data suitable for statistical inspection. To demonstrate the feasibility of our approach, we also present an example statistical benchmark that works twofold. First, we score individual combat actions, adjusting for contextual factors such as enemy intent, then use a run-level Bayesian model to estimate agent skill. Our approach enables reproducible, detailed analysis at the run and action level, moving beyond aggregate performance metrics to structured, context-rich datasets. The code to reproduce our experiment is available here: https://github.com/Max-Suslov/game-bench.

# Exploring the Integration of Large Language Models and Monte Carlo Tree Search in Team Formation for Turn-Based Games: a case-study with the VGC AI Competition
´Alvaro Ferrero, Jose Barambones, Juan Cano-Benito
Abstract—The integration of Large Language Models (LLMs)
with Monte Carlo Tree Search (MCTS) offers a novel approach
to tackle decision-making challenges in turn-based strategy
games, a domain where traditional methods face scalability and
adaptability limitations. The objective is to explore the feasibility
of LLMs in improving MCTS strategic reasoning by processing
dynamic and probabilistic game states. Although prior research
has demonstrated the strengths of MCTS in tactical optimization
and the potential of LLMs in natural language reasoning, the
synergy between these approaches remains underexplored. To
explore this gap, this study faces the team formation problem in
the Pok´emon VGC AI framework through “PokeHit”, a hybrid
AI agent that incorporates LLM-driven reasoning within the
MCTS pipeline. By testing open-source models as Llama and
Mistral, this work evaluates their ability to guide macro-strategy
decisions and compares their performance against baseline-
heuristic algorithms. Results reveal that LLMs can provide
strategies dynamically but suffer from limitations, including
hallucinations and inconsistent improvements over traditional
methods. However, this research supports the feasibility and
challenges of hybrid AI approaches in fine-tuning and policy
design to improve their efficiency. The findings aim to contribute
to the field of intelligent game agents through the applicability
of LLMs in decision-making domains.
Index Terms—Monte-Carlo Tree Search, Large Language
Models, Team Formation, Case study, Turn-based games, Intel-
ligent game agents.

https://ieeexplore.ieee.org/stamp/stamp.jsp?arnumber=11175503

# mods 
## STSStateSaver [6] allows the creation and loading of in-battle save states. It
extends the base game saving functionality, which only allows to save and resume
the game from the start of a battle.
https://github.com/boardengineer/STSStateSaver

## scumthespire [5] is a battle AI mod, which uses a search-based algorithm to
simulate possible sequences of moves and find one that ends the combat with
the most remaining health. The mod utilises a server, which also runs a separate
instance of the game and must be launched by the user either via command line or
UI. The client (game) sends a request to the server to process a battle. The server
then calculates a good sequence of moves and sends the sequence of commands
back to the client. The commands are then performed in the game. The mod
makes use of the State Saver mod to revert to previous states during the search
process, while the Ludicrous Speed mod enhances the processing speed. Battles
take up to a few seconds to process. Notably, the sequence of moves generated
by the algorithm is not always optimal, especially in later parts of the game, due
to the very large search space
https://github.com/boardengineer/scumthespire


# sts_lightspeed
2.6 Headless version of the game
Alternatively, there exists a headless version of Slay the Spire, called sts lightspeed
[14] written in C++ 17, which can be compiled and played in the command line. The
project supports everything necessary for playing the Ironclad class. According to the
description, it 100% accurately mimics the randomization of the real game and it can
perform 1 million playouts in 5 seconds using 16 threads. Additionally, it contains
an implementation of a Monte Carlo tree search algorithm for the battles. There is
work in progress for integrating this project with CommunicationMod, however it is not
currently usabl
For tree search and simulation of the popular rogue-like deckbuilder game Slay The Spire

Features

    c++ 17 compiled with gcc
    Standalone
    Designed to be 100% RNG accurate*
    Playable in console
    Speed: 1M random playouts in 5s with 16 threads
    Loading from save files (loading into combat currently only supported)
    Tree Search (best result, knowing the state of the game's rng)

https://github.com/gamerpuppy/sts_lightspeed

# Exploring Different Deck Building Algorithms in Slay the Spire
https://project-archive.inf.ed.ac.uk/ug4/20233951/ug4_proj.pdf


# Delta-Based Effect Embeddings for Slay the Spire

s@starbased.net
Abstract

This work proposes a methodology for learning game dynamics in Slay the Spire, a roguelike deckbuilder video game where previous AI attempts have been stymied by combinatorial complexity: over 1000 distinct game effects, context-dependent card synergies, and decision horizons spanning 1000+ actions per run. Existing approaches have plateaued. Monte Carlo Tree Search methods (STS Lightspeed, Bottled AI) achieve only 30-52% win rates despite 600K+ playouts per decision, while reinforcement learning struggles with credit assignment across such long horizons. Sparse terminal rewards provide insufficient signal, and dense reward shaping introduces catastrophic reward hacking. Rather than fighting this complexity through search or end-to-end learning, this work takes a different approach: crystallizing the game state perfectly, maintaining exact numerical access to every variable including RNG, and learning the structure of the state space itself. From this precise observation window, the hypothesis is that effect embeddings can emerge where meaning is grounded in observed state transformations rather than hand-crafted features.
https://gist.github.com/starbaser/1e253584a07d7a93b2e61fe076e11545

# StS2Sim#

A headless Slay the Spire 2 deck analyzer that runs the actual sts2.dll game logic outside the Godot engine. Reads your live current_run.save, spins up isolated combats against a high-HP dummy, and reports per-turn damage statistics with confidence intervals and per-turn play breakdowns over a live web UI.
https://tangled.org/cee.wtf/sts2-sim


[–]dupondius
 
0%[-1] 19 points 7 months ago 

Several people have tried building AI for STS. An underestimated difficulty is getting to the point of being able to do RL. It's not something you can just plug into openai gym. There are two approaches to simulation

    Use the game itself as simulation. https://github.com/ForgottenArbiter/spirecomm, or https://www.reddit.com/r/slaythespire/comments/ncs31p/slay_the_spire_battle_ai/. I don't know the performance of either of these systems.

    This repo https://github.com/gamerpuppy/sts_lightspeed implements game simulation extremely fast in C++. It has incomplete python bindings, and only implements ironclad. They implement bot that does MCTS for fights, and hardcoded rules for everything else. I haven't played with it a ton, but got it to an 8% A0 IC win rate. It probably gets better if you increase the time

I've implemented something with a ~20% A0 IC WR using a simple rule-based system on top of sts_lightspeed.

If you're legitimately interested in doing this, check out the data-analysis channel on the sts discord. There are some more links in there.

A RL-based approach would be cool, but honestly, a tree-based approach is a more reasonable scope

Honourable mentions:

Rule-based system: https://github.com/xaved88/bottled_ai/. WR 52% on its best STS1 class, 20% on IC. IDK if this is ascension or not, but they claim to be SOTA
Bottled AI

Customizable bot for roguelike deck-building game Slay the Spire.
FAQ
Does it win a lot? Is it super smart?

Depends on which character and strategy is in use. We've got a few different ones! Our current best is a stance-class strategy with 52% winrate. You can see our most current winrates here.
How does it work? Machine Learning? Gen AI?

Nah, just good old-fashioned manually constructed automated decision-making. For example:

    Selecting Cards, Boss Relics, and Upgrades works with a prioritized list, e.g. our strategy for one class might prefer taking Blasphemy over Tranquility.
    Some decisions have specific conditions attached to them, like in the Shining Light event: take the damage for the 2 random upgrades... but not if we don't have much health left.
    In combat, the bot figures out what the outcome would be for each of the different ways it could play its hand (with a graph traversal and a simulation we constructed). Then, the bot picks the outcome it likes best and plays the cards in that order.
        To decide what the best outcome is, the bot weighs about 40 different values against each other. Simple example: the bot prefers a turn where it defeats an enemy, but not if we will take a bunch of damage to make it happen.



# Slay the Spire RL

Combat-first training rebuild stacked on top of the audited Rust engine base.

This branch is the training worktree and stacked draft PR:

    engine base: codex/universal-gameplay-runtime via PR #132
    training rebuild: codex/training-rebuild via PR #133

Current scope is intentionally narrow:

    one STS1 class, A0 combat only
    Rust-canonical combat observation and legal-candidate contracts
    snapshot-backed Rust PUCT collection and MLX policy/value learning
    artifact-first monitoring in SpireMonitor
    reconstructed Act 1 validation seeds for easy/minimalist-style single-class checks
    MLX is the only supported backend on this branch

Active Components
Component 	Location 	Role
Rust engine base 	packages/engine-rs/ 	Canonical legality, combat state, training contract, snapshots
Training runtime 	packages/training/ 	Snapshot corpus generation, Rust PUCT collection, policy/value learning, manifests, artifact logging
SpireMonitor 	packages/app/SpireMonitor/ 	SwiftUI monitor for manifests, frontiers, benchmarks, and replay traces
Training entrypoint 	scripts/training.sh 	Canonical CLI wrapper

https://github.com/JackSwitzer/StSRLSolver

