import { describe, it, expect } from "vitest";
import { matchEncounter, nextSpawnSlot, slotNamed } from "../encounterSlots";
import { ENCOUNTER_SLOTS } from "../encounterSlots.data";
import { artSlugs } from "../roomScenes";
import { GROUND_Y, SCENE_CX } from "../../scene";
import type { Enemy } from "../../api";

/** An enemy the way the backend sends one — the art slug rides on `rig`. */
function enemy(name: string, slug: string, index = 0): Enemy {
  return {
    index, name, hp: 10, max_hp: 10, block: 0,
    image: `monsters/${slug}.png`, rig: `monster_rigs/${slug}/${slug}`,
  };
}

const match = (...es: Enemy[]) => matchEncounter(es, artSlugs);
/** The id `matchEncounter` filed a board under, or undefined. A match carries
 *  its id and its summoner's slot-scan direction, so it is not the table entry
 *  itself — compare on the id. */
const matchId = (...es: Enemy[]) => match(...es)?.id;

describe("the extracted slot table", () => {
  it("covers every encounter the game gives a scene", () => {
    // `EncounterModel.HasScene` is true for exactly 24, and each has a
    // `scenes/encounters/<id>.tscn`.
    expect(Object.keys(ENCOUNTER_SLOTS)).toHaveLength(24);
  });

  it("opens on slots that exist, in the order the model hands them out", () => {
    for (const [id, e] of Object.entries(ENCOUNTER_SLOTS)) {
      expect(e.start.length, id).toBeGreaterThan(0);
      expect(e.start.length, id).toBeLessThanOrEqual(e.slots.length);
      for (const name of e.start) expect(slotNamed(e, name), `${id}:${name}`).toBeDefined();
      expect(new Set(e.start).size, id).toBe(e.start.length);
    }
  });

  it("does not open on the FRONT of the list where the game does not", () => {
    // TwoTailedRatsNormal declares five slots and starts on `Slots[2..4]`.
    // Reading the row off the front stands all three rats a slot and a half
    // left of where the game stands them.
    expect(ENCOUNTER_SLOTS.two_tailed_rats_normal.start)
      .toEqual(["third", "fourth", "fifth"]);
    // And the Fabricator opens alone in the MIDDLE of its five.
    expect(ENCOUNTER_SLOTS.fabricator_normal.start).toEqual(["fabricator"]);
    expect(ENCOUNTER_SLOTS.fabricator_normal.slots.map((s) => s.name))
      .toEqual(["bot1", "bot2", "fabricator", "bot3", "bot4"]);
  });

  it("puts every slot inside the scene, near the ground or above it", () => {
    for (const [id, e] of Object.entries(ENCOUNTER_SLOTS)) {
      for (const s of e.slots) {
        // Centre-relative x. A slot may sit past the 1920 scene's own edge —
        // the Ovicopter's is at +1011 — because `SceneContainer.Scale` is the
        // encounter camera, and that fight pulls back to 0.8. Bound it at what
        // the widest pull-back (0.75, the Kaiser Crab) can still show.
        expect(Math.abs(s.x) * 0.75, `${id}:${s.name}`).toBeLessThanOrEqual(SCENE_CX);
        // Absolute scene y: nothing stands below the ground line, and the
        // highest authored slot is a Living Fog bomb at 285.
        expect(s.y, `${id}:${s.name}`).toBeGreaterThan(200);
        expect(s.y, `${id}:${s.name}`).toBeLessThan(GROUND_Y + 60);
      }
    }
  });

  it("carries the depth the row algorithm cannot", () => {
    // The row puts everything on one line. The Living Fog's five bombs hang in
    // an arc overhead and the Fabricator's bots sit at four corners — which is
    // the whole reason to read the markers at all.
    const bombs = ENCOUNTER_SLOTS.living_fog_normal.slots.filter((s) => s.name.startsWith("bomb"));
    expect(new Set(bombs.map((s) => s.y)).size).toBeGreaterThan(3);
    expect(Math.min(...bombs.map((s) => s.y))).toBeLessThan(400);
  });
});

describe("matching a board to its encounter", () => {
  it("finds an encounter from a monster only it fields", () => {
    expect(matchId(enemy("Ovicopter", "ovicopter"))).toBe("ovicopter_normal");
    expect(matchId(enemy("Queen", "queen"), enemy("Torch Head Amalgam", "torch_head_amalgam", 1)))
      .toBe("queen_boss");
    // The match carries the table entry's own fields through.
    expect(match(enemy("Ovicopter", "ovicopter"))?.slots)
      .toEqual(ENCOUNTER_SLOTS.ovicopter_normal.slots);
  });

  it("keeps the Fabricator when its spawned bots are not in its roster", () => {
    // `FabricatorNormal.GenerateMonsters` names only the Fabricator; the bots
    // come out of `StockPower`, so they are strangers to the encounter's own
    // class. A stranger costs the match a point — it must not veto it.
    const board = [
      enemy("Fabricator", "fabricator"),
      enemy("Zapbot", "zapbot", 1),
      enemy("Stabbot", "stabbot", 2),
    ];
    expect(matchEncounter(board, artSlugs)?.id).toBe("fabricator_normal");
  });

  it("splits two sizes of the same fight by how many are standing", () => {
    // ExoskeletonsNormal fields four and Weak three, out of the same monster.
    const exos = (n: number) =>
      Array.from({ length: n }, (_, i) => enemy("Exoskeleton", "exoskeleton", i));
    expect(matchEncounter(exos(4), artSlugs)?.id).toBe("exoskeletons_normal");
    expect(matchEncounter(exos(3), artSlugs)?.id).toBe("exoskeletons_weak");
  });

  it("declines rather than guesses when two encounters fit equally", () => {
    // A Bowlbug Rock and a Bowlbug Silk are BowlbugsNormal's opening pair and
    // SlumberingBeetleNormal's — same hits, same slot count. Guessing wrong
    // would stand them somewhere the game never does; the row is a better
    // answer than a confident mistake.
    const board = [enemy("Bowlbug Rock", "bowlbug_rock"), enemy("Bowlbug Silk", "bowlbug_silk", 1)];
    expect(matchEncounter(board, artSlugs)).toBeUndefined();
    // One more monster settles it.
    expect(matchEncounter(
      [...board, enemy("Slumbering Beetle", "slumbering_beetle", 2)], artSlugs,
    )?.id).toBe("slumbering_beetle_normal");
  });

  it("leaves an encounter with no scene alone", () => {
    expect(match(enemy("Toadpole", "toadpole"))).toBeUndefined();
    expect(matchEncounter([], artSlugs)).toBeUndefined();
  });

  it("will not squeeze a board into an encounter with too few slots", () => {
    // Five Myte would not be MytesNormal, which authors two.
    const board = Array.from({ length: 5 }, (_, i) => enemy("Myte", "myte", i));
    expect(matchEncounter(board, artSlugs)).toBeUndefined();
  });
});

describe("the slot a mid-fight spawn takes", () => {
  /** `matchEncounter` for a board that is only `slug`s. */
  const board = (...slugs: string[]) =>
    match(...slugs.map((slug, i) => enemy(slug, slug, i)));

  it("scans from the END for the summoners that do", () => {
    // `Ovicopter.LayEggsMove` is `Slots.LastOrDefault(free)`, not
    // `GetNextSlot`: the three eggs land in egg5/egg4/egg3, tucked in beside
    // the Ovicopter, NOT at the far end of the row in egg1/egg2/egg3.
    const ovi = board("ovicopter");
    expect(ovi?.spawnPick).toBe("last");
    const taken = new Set(["ovicopter"]);
    const laid: string[] = [];
    for (let i = 0; i < 3; i++) {
      const s2 = nextSpawnSlot(ovi!, taken);
      expect(s2).toBeDefined();
      taken.add(s2!);
      laid.push(s2!);
    }
    expect(laid).toEqual(["egg5", "egg4", "egg3"]);

    // `TwoTailedRat.CallForBackup` scans the same way; the fight opens on the
    // last three of five, so backup fills `second`, then `first`.
    const rats = board("two_tailed_rat", "two_tailed_rat", "two_tailed_rat");
    expect(rats?.spawnPick).toBe("last");
    expect(nextSpawnSlot(rats!, new Set(["third", "fourth", "fifth"]))).toBe("second");
    expect(nextSpawnSlot(rats!, new Set(["second", "third", "fourth", "fifth"]))).toBe("first");
  });

  it("scans from the FRONT for `GetNextSlot`, which is the default", () => {
    // `Fabricator.cs` and `LivingFog.cs` both call `Encounter.GetNextSlot`,
    // which is `Slots.FirstOrDefault(free)`.
    const fab = board("fabricator");
    expect(fab?.spawnPick).toBe("first");
    expect(nextSpawnSlot(fab!, new Set(["fabricator"]))).toBe("bot1");
    expect(nextSpawnSlot(fab!, new Set(["fabricator", "bot1"]))).toBe("bot2");
  });

  it("has nothing to hand out once every slot is held", () => {
    const ovi = board("ovicopter");
    const all = new Set(ovi!.slots.map((s2) => s2.name));
    expect(nextSpawnSlot(ovi!, all)).toBeUndefined();
  });
});
