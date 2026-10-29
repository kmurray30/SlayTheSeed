---
title: Deprecated Encounter
class_name: DeprecatedEncounter
room_type: Monster
is_weak: false
monsters: []
total_monsters: 0
tags: []
acts: []
---

## Overview

This is a special debug encounter that appears when the player encounters monsters that have been removed or deprecated from the game. It serves as a fallback to prevent crashes when trying to spawn a monster class that no longer exists.

## Mechanics

- Spawns no monsters
- Marked as debug encounter internally
- Used to handle deprecated/removed monster types gracefully
