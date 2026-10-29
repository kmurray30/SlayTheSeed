---
title: Bowlbug Swarm
class_name: BowlbugsNormal
room_type: Monster
is_weak: false
monsters: [
  {"class_name": "BowlbugRock", "title": "Bowlbug (Rock)", "slug": "bowlbug-rock"},
  {"class_name": "BowlbugEgg", "title": "Bowlbug (Egg)", "slug": "bowlbug-egg"},
  {"class_name": "BowlbugSilk", "title": "Bowlbug (Silk)", "slug": "bowlbug-silk"},
  {"class_name": "BowlbugNectar", "title": "Bowlbug (Nectar)", "slug": "bowlbug-nectar"}
]
total_monsters: 3
tags: ["Workers"]
acts: ["Hive"]
---

## Encounter Composition

This encounter always spawns one **Bowlbug (Rock)** and two additional workers. The two additional workers are randomly selected without replacement from:
- Bowlbug (Egg)
- Bowlbug (Silk)  
- Bowlbug (Nectar)

This means you will always face exactly 3 beetles, with the Rock beetle guaranteed, and the other two being different types randomly chosen from the three worker variants.
