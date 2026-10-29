// GENERATED — do not edit by hand.
//
// Regenerate from the game PCK + the decomp (from `sts2-wiki/`):
//
//   uv run python -m scripts.extract_encounter_slots \
//       ../sts2_binaries/v0.107.1/SlayTheSpire2.pck \
//       decompiled/v0.107.1 \
//       ../sts2-pycli/web/src/components/encounterSlots.data.ts
//
// Source of truth: `scenes/encounters/<id>.tscn` in the v0.107.1 PCK and
// `MegaCrit.Sts2.Core.Models.Encounters` in the decomp. See
// `sts2-wiki/scripts/extract_encounter_slots.py`, and `encounterSlots.ts` for
// how a fight is matched to one of these.
import type { EncounterSlotTable } from "./encounterSlots";

export const ENCOUNTER_SLOTS: EncounterSlotTable = {
  "axebots_normal": {
    "roster": [
      "axebot"
    ],
    "start": [
      "front"
    ],
    "slots": [
      {
        "name": "front",
        "x": 545.0,
        "y": 715.0
      }
    ]
  },
  "bowlbugs_normal": {
    "roster": [
      "bowlbug_egg",
      "bowlbug_nectar",
      "bowlbug_rock",
      "bowlbug_silk"
    ],
    "start": [
      "first",
      "middle",
      "last"
    ],
    "slots": [
      {
        "name": "first",
        "x": 139.0,
        "y": 673.0
      },
      {
        "name": "middle",
        "x": 408.0,
        "y": 712.0
      },
      {
        "name": "last",
        "x": 706.0,
        "y": 675.0
      }
    ]
  },
  "bowlbugs_weak": {
    "roster": [
      "bowlbug_egg",
      "bowlbug_nectar",
      "bowlbug_rock"
    ],
    "start": [
      "odd",
      "even"
    ],
    "slots": [
      {
        "name": "odd",
        "x": 265.0,
        "y": 686.0
      },
      {
        "name": "even",
        "x": 580.0,
        "y": 713.0
      }
    ]
  },
  "decimillipede_elite": {
    "roster": [
      "decimillipede_segment_back",
      "decimillipede_segment_front",
      "decimillipede_segment_middle"
    ],
    "start": [
      "segment1",
      "segment2",
      "segment3"
    ],
    "slots": [
      {
        "name": "segment1",
        "x": 143.0,
        "y": 740.0
      },
      {
        "name": "segment2",
        "x": 491.0,
        "y": 740.0
      },
      {
        "name": "segment3",
        "x": 837.0,
        "y": 740.0
      }
    ]
  },
  "dense_vegetation_event_encounter": {
    "roster": [
      "wriggler"
    ],
    "start": [
      "wriggler1",
      "wriggler2",
      "wriggler3",
      "wriggler4"
    ],
    "slots": [
      {
        "name": "wriggler1",
        "x": 118.0,
        "y": 682.0
      },
      {
        "name": "wriggler2",
        "x": 355.0,
        "y": 706.0
      },
      {
        "name": "wriggler3",
        "x": 596.0,
        "y": 714.0
      },
      {
        "name": "wriggler4",
        "x": 821.0,
        "y": 690.0
      }
    ]
  },
  "exoskeletons_normal": {
    "roster": [
      "exoskeleton"
    ],
    "start": [
      "first",
      "second",
      "third",
      "fourth"
    ],
    "slots": [
      {
        "name": "first",
        "x": 145.0,
        "y": 705.0
      },
      {
        "name": "second",
        "x": 352.0,
        "y": 737.0
      },
      {
        "name": "third",
        "x": 559.0,
        "y": 724.0
      },
      {
        "name": "fourth",
        "x": 756.0,
        "y": 699.0
      }
    ]
  },
  "exoskeletons_weak": {
    "roster": [
      "exoskeleton"
    ],
    "start": [
      "first",
      "second",
      "third"
    ],
    "slots": [
      {
        "name": "first",
        "x": 205.0,
        "y": 691.0
      },
      {
        "name": "second",
        "x": 441.0,
        "y": 756.0
      },
      {
        "name": "third",
        "x": 683.0,
        "y": 712.0
      }
    ]
  },
  "fabricator_normal": {
    "roster": [
      "fabricator"
    ],
    "start": [
      "fabricator"
    ],
    "slots": [
      {
        "name": "bot1",
        "x": 125.0,
        "y": 575.0
      },
      {
        "name": "bot2",
        "x": 325.0,
        "y": 261.0
      },
      {
        "name": "fabricator",
        "x": 523.0,
        "y": 713.0
      },
      {
        "name": "bot3",
        "x": 685.0,
        "y": 256.0
      },
      {
        "name": "bot4",
        "x": 915.0,
        "y": 581.0
      }
    ]
  },
  "fake_merchant_event_encounter": {
    "roster": [
      "fake_merchant_monster"
    ],
    "start": [
      "merchant"
    ],
    "slots": [
      {
        "name": "merchant",
        "x": 240.0,
        "y": 700.0
      }
    ]
  },
  "fogmog_normal": {
    "roster": [
      "eye_with_teeth",
      "fogmog"
    ],
    "start": [
      "fogmog"
    ],
    "slots": [
      {
        "name": "illusion",
        "x": 265.0,
        "y": 686.0
      },
      {
        "name": "fogmog",
        "x": 578.0,
        "y": 713.0
      }
    ]
  },
  "gremlin_merc_normal": {
    "roster": [
      "fat_gremlin",
      "gremlin_merc",
      "sneaky_gremlin"
    ],
    "start": [
      "merc"
    ],
    "slots": [
      {
        "name": "merc",
        "x": 338.0,
        "y": 694.0
      },
      {
        "name": "sneaky",
        "x": 338.0,
        "y": 695.0
      },
      {
        "name": "fat",
        "x": 603.0,
        "y": 659.0
      }
    ]
  },
  "kaiser_crab_boss": {
    "roster": [
      "crusher",
      "rocket"
    ],
    "start": [
      "crusher",
      "rocket"
    ],
    "slots": [
      {
        "name": "crusher",
        "x": -824.0,
        "y": 736.0
      },
      {
        "name": "rocket",
        "x": 896.0,
        "y": 712.0
      }
    ]
  },
  "knights_elite": {
    "roster": [
      "flail_knight",
      "magi_knight",
      "spectral_knight"
    ],
    "start": [
      "first",
      "second",
      "third"
    ],
    "slots": [
      {
        "name": "first",
        "x": 67.0,
        "y": 697.0
      },
      {
        "name": "second",
        "x": 472.0,
        "y": 754.0
      },
      {
        "name": "third",
        "x": 870.0,
        "y": 722.0
      }
    ]
  },
  "living_fog_normal": {
    "roster": [
      "gas_bomb",
      "living_fog"
    ],
    "start": [
      "livingFog"
    ],
    "slots": [
      {
        "name": "bomb1",
        "x": 125.0,
        "y": 575.0
      },
      {
        "name": "bomb2",
        "x": 302.0,
        "y": 361.0
      },
      {
        "name": "bomb3",
        "x": 531.0,
        "y": 285.0
      },
      {
        "name": "bomb4",
        "x": 763.0,
        "y": 372.0
      },
      {
        "name": "bomb5",
        "x": 915.0,
        "y": 581.0
      },
      {
        "name": "livingFog",
        "x": 521.0,
        "y": 729.0
      }
    ]
  },
  "mytes_normal": {
    "roster": [
      "myte"
    ],
    "start": [
      "first",
      "second"
    ],
    "slots": [
      {
        "name": "first",
        "x": 285.0,
        "y": 711.0
      },
      {
        "name": "second",
        "x": 776.0,
        "y": 737.0
      }
    ]
  },
  "nibbits_normal": {
    "roster": [
      "nibbit"
    ],
    "start": [
      "front",
      "back"
    ],
    "slots": [
      {
        "name": "front",
        "x": 265.0,
        "y": 686.0
      },
      {
        "name": "back",
        "x": 578.0,
        "y": 713.0
      }
    ]
  },
  "ovicopter_normal": {
    "roster": [
      "ovicopter",
      "tough_egg"
    ],
    "start": [
      "ovicopter"
    ],
    "slots": [
      {
        "name": "egg1",
        "x": 1.0,
        "y": 678.0
      },
      {
        "name": "egg2",
        "x": 180.0,
        "y": 697.0
      },
      {
        "name": "egg3",
        "x": 363.0,
        "y": 654.0
      },
      {
        "name": "egg4",
        "x": 538.0,
        "y": 705.0
      },
      {
        "name": "egg5",
        "x": 691.0,
        "y": 665.0
      },
      {
        "name": "ovicopter",
        "x": 1011.0,
        "y": 699.0
      }
    ]
  },
  "phantasmal_gardeners_elite": {
    "roster": [
      "phantasmal_gardener"
    ],
    "start": [
      "first",
      "second",
      "third",
      "fourth"
    ],
    "slots": [
      {
        "name": "first",
        "x": 65.0,
        "y": 705.0
      },
      {
        "name": "second",
        "x": 340.0,
        "y": 737.0
      },
      {
        "name": "third",
        "x": 640.0,
        "y": 724.0
      },
      {
        "name": "fourth",
        "x": 940.0,
        "y": 724.0
      }
    ]
  },
  "phrog_parasite_elite": {
    "roster": [
      "phrog_parasite",
      "wriggler"
    ],
    "start": [
      "phrog"
    ],
    "slots": [
      {
        "name": "phrog",
        "x": 406.0,
        "y": 694.0
      },
      {
        "name": "wriggler1",
        "x": 155.0,
        "y": 685.0
      },
      {
        "name": "wriggler2",
        "x": 376.0,
        "y": 706.0
      },
      {
        "name": "wriggler3",
        "x": 617.0,
        "y": 714.0
      },
      {
        "name": "wriggler4",
        "x": 842.0,
        "y": 690.0
      }
    ]
  },
  "queen_boss": {
    "roster": [
      "queen",
      "torch_head_amalgam"
    ],
    "start": [
      "amalgam",
      "queen"
    ],
    "slots": [
      {
        "name": "amalgam",
        "x": 247.0,
        "y": 709.0
      },
      {
        "name": "queen",
        "x": 646.0,
        "y": 695.0
      }
    ]
  },
  "slumbering_beetle_normal": {
    "roster": [
      "bowlbug_rock",
      "bowlbug_silk",
      "slumbering_beetle"
    ],
    "start": [
      "first",
      "second",
      "third"
    ],
    "slots": [
      {
        "name": "first",
        "x": -3.0,
        "y": 692.0
      },
      {
        "name": "second",
        "x": 220.0,
        "y": 741.0
      },
      {
        "name": "third",
        "x": 748.0,
        "y": 717.0
      }
    ]
  },
  "the_kin_boss": {
    "roster": [
      "kin_follower",
      "kin_priest"
    ],
    "start": [
      "slot1",
      "slot2",
      "leaderSlot"
    ],
    "slots": [
      {
        "name": "slot1",
        "x": 140.0,
        "y": 711.0
      },
      {
        "name": "slot2",
        "x": 537.0,
        "y": 736.0
      },
      {
        "name": "leaderSlot",
        "x": 945.0,
        "y": 686.0
      }
    ]
  },
  "the_obscura_normal": {
    "roster": [
      "parafright",
      "the_obscura"
    ],
    "start": [
      "obscura"
    ],
    "slots": [
      {
        "name": "illusion",
        "x": 265.0,
        "y": 686.0
      },
      {
        "name": "obscura",
        "x": 642.0,
        "y": 713.0
      }
    ]
  },
  "two_tailed_rats_normal": {
    "roster": [
      "two_tailed_rat"
    ],
    "start": [
      "third",
      "fourth",
      "fifth"
    ],
    "slots": [
      {
        "name": "first",
        "x": 65.0,
        "y": 705.0
      },
      {
        "name": "second",
        "x": 292.0,
        "y": 749.0
      },
      {
        "name": "third",
        "x": 511.0,
        "y": 680.0
      },
      {
        "name": "fourth",
        "x": 749.0,
        "y": 753.0
      },
      {
        "name": "fifth",
        "x": 960.0,
        "y": 664.0
      }
    ]
  }
} as const;
