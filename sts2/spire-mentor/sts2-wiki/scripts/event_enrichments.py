"""Hand-written enrichments for event pages (excluded from public exports).

See generate_events.py, which falls back to an empty dict when this module
is absent.
"""

# Hand-written enrichments for events whose descriptions/options are
# defined in code rather than localization data, or whose localization
# contains unresolved template variables.
#
# "options" replaces the extracted options entirely.
# "option_overrides" patches specific options by title match.
_EVENT_ENRICHMENTS: dict[str, dict] = {
    "FakeMerchant": {
        "description": (
            "A suspicious merchant has laid out a rug covered in relics. "
            "They look familiar, but something is off about them...\n\n"
            "The merchant sells [gold]fake relics[/gold] — weaker versions "
            "of real relics — for [gold]50 gold[/gold] each. "
            "Up to 6 are available from a pool of 9.\n\n"
            "If you have a [purple]Foul Potion[/purple], you can throw it "
            "at the merchant to trigger a fight. Defeating The Merchant??? "
            "(175 HP) rewards [gold]300 gold[/gold], "
            "[gold]The Merchant's Rug[/gold] relic, and any unsold fake relics."
        ),
        "options": [
            {"title": "Browse Wares", "description": "View and buy fake relics for 50 gold each."},
            {
                "title": "Throw Foul Potion",
                "description": "Requires a Foul Potion. Fight The Merchant??? (175 HP). "
                "Rewards: 300 gold, The Merchant's Rug, and unsold fake relics.",
            },
            {"title": "Leave", "description": "Walk away."},
        ],
    },
    "BattlewornDummy": {
        "option_overrides": {
            "Setting 1": {
                "description": "Fight a [blue]75[/blue] HP dummy. Reward: a random potion."
            },
            "Setting 2": {
                "description": "Fight a [blue]150[/blue] HP dummy. Reward: upgrade 2 random cards."
            },
            "Setting 3": {
                "description": "Fight a [blue]300[/blue] HP dummy. Reward: obtain a relic."
            },
        },
        "notes": (
            "Setting 1 potion: drawn from your character's potion pool + shared pool, "
            "weighted by rarity (65% Common, 25% Uncommon, 10% Rare).\n\n"
            "Setting 2 upgrades: 2 random upgradable cards from your deck (not player-chosen).\n\n"
            "Setting 3 relic: pulled from the standard relic pool "
            "(the next relic you'd get from an elite)."
        ),
    },
    "ColossalFlower": {
        "options": [
            {"title": "Extract Nectar", "description": "Gain [blue]35[/blue] [gold]Gold[/gold]."},
            {
                "title": "Reach Deeper",
                "description": "Take [red]5[/red] damage. Access higher tiers: "
                "[blue]75 gold[/blue] (6 damage) or [blue]135 gold[/blue] (7 damage). "
                "At the deepest level, you can take the [gold]Pollinous Core[/gold] relic instead.",
            },
        ],
    },
    "DenseVegetation": {
        "option_overrides": {
            "Rest": {"description": "Heal [green]30%[/green] of max HP. Then fight some enemies."},
        },
        "notes": (
            "The [red]8[/red] HP loss from [gold]Trudge On[/gold] is [red]unblockable[/red] "
            "and [red]unpowered[/red] — it cannot be blocked or reduced by any powers or relics. "
            "[gold]Gold[/gold] gained is random: [blue]61\u201399[/blue] [gold]Gold[/gold].\n\n"
            "Choosing [gold]Rest[/gold] heals you for the standard rest site amount "
            "(roughly [green]30%[/green] of max HP), then immediately forces combat "
            "against a [red]Dense Vegetation encounter[/red]. "
            "This combat provides [red]no rewards[/red], and the event ends after the fight."
        ),
    },
    "FieldOfManSizedHoles": {
        "option_overrides": {
            "Enter Your Hole": {
                "description": "[gold]Enchant[/gold] a card with [purple]Perfect Fit[/purple]."
            },
        },
    },
    "HungryForMushrooms": {
        "options": [
            {
                "title": "Big Mushroom",
                "description": "Obtain the [gold]Big Mushroom[/gold] relic.",
            },
            {
                "title": "Fragrant Mushroom",
                "description": "Take [red]15[/red] damage. "
                "Obtain the [gold]Fragrant Mushroom[/gold] relic.",
            },
        ],
    },
    "PotionCourier": {
        "option_overrides": {
            "Grab Potions": {"description": "Procure [blue]3[/blue] [gold]Foul Potions[/gold]."},
        },
    },
    "RelicTrader": {
        "description": (
            "A mysterious figure offers to trade your relics. "
            "Three of your relics are randomly shuffled and each paired "
            "with the next relic you would have found from the relic pool. "
            "You must accept exactly one trade."
        ),
        "options": [
            {
                "title": "Take the Top One",
                "description": (
                    "Trade [gold]one of your relics[/gold] "
                    "for [gold]a new relic from the pool[/gold]."
                ),
            },
            {
                "title": "Take the Middle One",
                "description": (
                    "Trade [gold]one of your relics[/gold] "
                    "for [gold]a new relic from the pool[/gold]."
                ),
            },
            {
                "title": "Take the Bottom One",
                "description": (
                    "Trade [gold]one of your relics[/gold] "
                    "for [gold]a new relic from the pool[/gold]."
                ),
            },
        ],
    },
    "SelfHelpBook": {
        "option_overrides": {
            "Read the Back": {
                "description": "Choose an Attack to [gold]Enchant[/gold] "
                "with [purple]Sharp[/purple] [blue]2[/blue]."
            },
            "Read a Random Passage": {
                "description": "Choose a Skill to [gold]Enchant[/gold] "
                "with [purple]Nimble[/purple] [blue]2[/blue]."
            },
            "Read the Entire Book": {
                "description": "Choose a Power to [gold]Enchant[/gold] "
                "with [purple]Swift[/purple] [blue]2[/blue]."
            },
        },
    },
    "SlipperyBridge": {
        "option_overrides": {
            "Overcome": {"description": "A random card is removed from your [gold]Deck[/gold]."},
            "Hold On": {
                "description": "Lose [red]3[/red] HP (increases by 1 each time you Hold On). "
                "Damage is [red]unblockable[/red]. "
                "The card in the Overcome option is re-randomized."
            },
        },
        "notes": (
            "The first card removed is random from your non-Basic removable cards. "
            "Subsequent removals pick from cards of a "
            "[gold]different type[/gold] than the previous one "
            "(falls back to any removable card if none qualify).\n\n"
            "Hold On HP cost escalates: [red]3[/red], [red]4[/red], [red]5[/red], [red]6[/red]... "
            "(increases by 1 each time). "
            "The damage is [red]unblockable[/red] and ignores powers.\n\n"
            "The event has 7 unique pages of escalating distress text. "
            "After 7 or more holds, the text loops indefinitely — "
            "the developer apologizes for running out of content."
        ),
    },
    "SpiralingWhirlpool": {
        "option_overrides": {
            "Drink": {"description": "Heal [green]33%[/green] of max HP."},
        },
        "notes": "Heal amount is exactly 33% of your Max HP (rounded).",
    },
    "StoneOfAllTime": {
        "option_overrides": {
            "Drink and Lift": {
                "description": "Lose a random [gold]Potion[/gold]. Gain [green]10[/green] Max HP."
            },
        },
    },
    "Symbiote": {
        "option_overrides": {
            "Approach": {
                "description": "[gold]Enchant[/gold] an Attack with [purple]Corrupted[/purple]."
            },
        },
    },
    "TheFutureOfPotions": {
        "description": (
            "A strange device promises to transform your potions into something greater.\n\n"
            "Trade a potion for a card reward of 3 [gold]upgraded[/gold] cards. "
            "The card rarity matches the potion rarity "
            "(Rare potion = Rare cards, etc.), "
            "and the card type (Attack/Skill/Power) is randomly assigned."
        ),
        "options": [
            {
                "title": "Insert a Potion",
                "description": "Trade a potion for 3 [gold]upgraded[/gold] card choices "
                "matching the potion's rarity.",
            },
            {"title": "Leave", "description": "Walk away."},
        ],
        "notes": (
            "The card rarity exactly matches the potion rarity: Rare/Event potion → Rare cards, "
            "Uncommon → Uncommon, Common/Token → Common.\n\n"
            "The card type (Attack/Skill/Power) is chosen randomly for each potion "
            "(Common/Token potions exclude Power type). "
            "All 3 offered cards share the same type and rarity, drawn from your character's pool. "
            "All generated cards are pre-upgraded."
        ),
    },
    "SunkenStatue": {
        "option_overrides": {
            "Grab the Sword": {"description": "Obtain the [gold]Sword of Stone[/gold]."},
        },
        "notes": (
            "Gold is randomized: [blue]101–121[/blue] gold. "
            "The HP loss from Dive into the Water is [red]unblockable[/red] "
            "and unaffected by powers."
        ),
    },
    "TrashHeap": {
        "option_overrides": {
            "Dive In": {
                "description": "Lose [red]8[/red] HP. Obtain a random relic from the pool below. "
                "These are [gold]STS1 relics[/gold] exclusive to this event — "
                "one is chosen at random regardless of your character.",
            },
            "Grab Random Junk": {
                "description": "Gain [blue]100[/blue] [gold]Gold[/gold]. "
                "Add a random card from the pool below to your deck. "
                "These are [gold]STS1 cards[/gold] exclusive to this event — "
                "one is chosen at random regardless of your character.",
            },
        },
        "notes": (
            "Both pools are fixed — every card and relic has equal chance. "
            "The card is added directly to your deck (no choice screen). "
            "The HP loss from Dive In is unblockable and ignores powers."
        ),
    },
    "EndlessConveyor": {
        "notes": (
            "[gold]Dish selection[/gold] uses weighted randomization. "
            "Every 5th grab is always Seapunk Salad (adds Feeding Frenzy card). "
            "Otherwise, dishes are drawn by weight from those currently available:\n\n"
            "Caviar (+4 Max HP): weight 6\n"
            "Clam Roll (heal 10 HP): weight 6, only if not at full HP\n"
            "Spicy Snappy (random upgrade): weight 3\n"
            "Jelly Liver (transform a card): weight 3\n"
            "Fried Eel (random colorless card): weight 3\n"
            "Suspicious Condiment (random potion): weight 3, only if you have open potion slots\n"
            "Golden Fysh (free, +75 gold): weight 1, only after first grab\n\n"
            "The previous dish cannot repeat. Spicy Snappy and Observe the Chef both "
            "upgrade a random card from your deck (not player-chosen). "
            "Fried Eel adds a random colorless card directly to your deck (no choice)."
        ),
    },
    "CrystalSphere": {
        "notes": (
            "The gold cost is randomized: [blue]51–99[/blue] gold. "
            "This is determined when the event spawns and varies between runs."
        ),
    },
    "BrainLeech": {
        "notes": (
            "[gold]Rip[/gold]: standard 3-card reward screen from the Colorless card pool.\n\n"
            "[gold]Share Knowledge[/gold]: generates 5 cards from your character's pool, "
            "then you pick 1 (you must choose one — cannot cancel)."
        ),
    },
    "InfestedAutomaton": {
        "notes": (
            "[gold]Study[/gold]: adds a random Power card from your "
            "character's pool directly to your deck (no choice).\n\n"
            "[gold]Touch Core[/gold]: adds a random 0-cost card from "
            "your character's pool directly to your deck (no choice)."
        ),
    },
    "RoomFullOfCheese": {
        "notes": (
            "[gold]Gorge[/gold]: generates 8 Common-rarity cards from your character's pool, "
            "then you pick 2.\n\n"
            "[gold]Search[/gold]: the [red]14[/red] HP loss is [red]unblockable[/red] "
            "and not affected by powers or relics."
        ),
    },
    "Reflections": {
        "notes": (
            "[gold]Touch a Mirror[/gold]: randomly downgrades up to 2 upgraded cards, "
            "then randomly upgrades up to 4 upgradable cards. Both selections are random, "
            "not player-chosen.\n\n"
            "[gold]Shatter[/gold]: duplicates your entire deck "
            "(doubles every card), then adds a Curse."
        ),
    },
    "TabletOfTruth": {
        "notes": (
            "Each Decipher costs increasing Max HP: [red]3[/red], [red]6[/red], "
            "[red]12[/red], [red]24[/red], then [red]all but 1[/red] Max HP.\n\n"
            "Deciphers 1–4 each upgrade a random card from your deck. "
            "The 5th and final decipher upgrades [gold]ALL[/gold] upgradable cards in your deck."
        ),
    },
    "TinkerTime": {
        "notes": (
            "You build a custom Mad Science card in two steps:\n\n"
            "Step 1: Choose a card type — shown 2 of 3 (Attack/Skill/Power) at random.\n\n"
            "Step 2: Choose a rider effect — each type has 3 possible riders "
            "(e.g. Attack: Sapping/Violence/Choking), shown 2 of 3 at random.\n\n"
            "The resulting card combines the chosen type and rider effect."
        ),
    },
    "Trial": {
        "notes": (
            "This is a multi-stage event. Choosing [gold]Reject[/gold] on the initial screen "
            "leads to a second page where the [gold]Grand Arbiter[/gold] threatens death for "
            "refusal. From there you can still [gold]Accept[/gold] (give in) or "
            "[red]Double Down[/red] \u2014 [red]Double Down[/red] deals [red]9999[/red] damage "
            "(instant death) and shows an abandon-run confirmation dialog.\n\n"
            "After [gold]Accepting[/gold] (from either screen), one of three trial scenarios "
            "is chosen at random ([blue]1 in 3[/blue] each):\n\n"
            "[gold]The Merchant[/gold] \u2014 accused of murder; evidence is thin. "
            "[red]Guilty[/red]: Add [red]Regret[/red] to deck, gain [blue]2[/blue] random relics. "
            "[green]Innocent[/green]: Add [red]Shame[/red] to deck, "
            "upgrade [blue]2[/blue] cards.\n\n"
            "[gold]The Noble[/gold] \u2014 charged with corruption; slips you a bribe note. "
            "[red]Guilty[/red]: Heal [green]10[/green] HP. "
            "[green]Innocent[/green]: Add [red]Regret[/red] to deck, "
            "gain [blue]300[/blue] gold.\n\n"
            "[gold]The Nondescript Woman[/gold] \u2014 charged with theft and con-artistry; "
            "both sides compelling. "
            "[red]Guilty[/red]: Add [red]Doubt[/red] to deck, gain [blue]2[/blue] card rewards. "
            "[green]Innocent[/green]: Add [red]Doubt[/red] to deck, "
            "transform [blue]2[/blue] cards.\n\n"
            "The entrant number in the flavor text is randomly chosen between [blue]101[/blue] and "
            "[blue]999[/blue] when the event is generated. "
            "The [blue]2[/blue] random relics from the Merchant Guilty outcome are drawn from "
            "the front of your character\u2019s relic pool."
        ),
    },
    "DollRoom": {
        "notes": (
            "There are [blue]3[/blue] possible Doll Relics: "
            "[gold]Daughter of the Wind[/gold], [gold]Mr. Struggles[/gold], "
            "and [gold]Bing Bong[/gold].\n\n"
            "[gold]Pick at Random[/gold]: get 1 of the 3 relics at random (free).\n"
            "[gold]Take Some Time[/gold]: see [blue]2[/blue] of the 3 to choose from "
            "(costs [red]5[/red] unblockable HP).\n"
            "[gold]Examine Each[/gold]: see all [blue]3[/blue] to choose from "
            "(costs [red]15[/red] unblockable HP)."
        ),
    },
    "SunkenTreasury": {
        "notes": (
            "Small chest gold: [blue]52–67[/blue]. "
            "Large chest gold: [blue]303–363[/blue] (adds Greed curse)."
        ),
    },
    "ThisOrThat": {
        "option_overrides": {
            "This": {
                "description": "Lose [red]6[/red] HP. Gain [blue]41–68[/blue] [gold]Gold[/gold]."
            },
        },
        "notes": (
            "Gold from 'This' is randomized: [blue]41–68[/blue] gold.\n\n"
            "'That' gives a relic from the standard relic pool + Clumsy curse."
        ),
    },
    "WaterloggedScriptorium": {
        "option_overrides": {
            "Prickly Sponge": {
                "description": "Pay [red]155[/red] [gold]Gold[/gold]. "
                "[gold]Enchant[/gold] [blue]2[/blue] cards with [purple]Steady[/purple]."
            },
            "Locked": {"description": "Requires [blue]155[/blue] [gold]Gold[/gold]."},
        },
    },
    "WelcomeToWongos": {
        "option_overrides": {
            "Wongo's Featured Item": {
                "description": "Pay [red]200[/red] [gold]Gold[/gold]. "
                "Obtain a random [gold]Rare Relic[/gold]."
            },
        },
        "notes": (
            "[gold]Bargain Bin[/gold]: pulls from the Common relic pool. "
            "Earns [blue]32[/blue] [gold]Wongo Points[/gold].\n"
            "[gold]Featured Item[/gold]: pulls from the Rare relic pool; "
            "the specific relic is shown in the option. "
            "Earns [blue]16[/blue] [gold]Wongo Points[/gold].\n"
            "[gold]Mystery Box[/gold]: gives the [gold]Wongo's Mystery Ticket[/gold] "
            "relic (delivers [blue]3[/blue] relics after [blue]5[/blue] combats). "
            "Earns [blue]8[/blue] [gold]Wongo Points[/gold].\n\n"
            "[gold]Wongo Points[/gold] persist across runs. "
            "Every [blue]2000[/blue] points earns a "
            "[gold]Wongo Customer Appreciation Badge[/gold]. "
            "After each purchase the clerk announces your running total "
            "and remaining points to the next badge.\n\n"
            "[red]Leave[/red]: only downgrades an [blue]upgraded[/blue] card "
            "chosen at random. If you have no upgraded cards, nothing happens."
        ),
    },
    "ColorfulPhilosophers": {
        "description": (
            "A group of colorful philosophers argue about which school of thought is best.\n\n"
            "Choose another character's card pool to receive 3 card rewards "
            "(one Common, one Uncommon, one Rare), each with 3 cards to pick from."
        ),
        "options": [
            {
                "title": "Choose a Philosophy",
                "description": "Pick another character's card pool. "
                "Receive 3 card reward screens (Common, Uncommon, Rare).",
            },
        ],
        "notes": (
            "Only shows unlocked characters other than your own. "
            "Up to 3 are offered from the fixed order: "
            "Necrobinder, Ironclad, Regent, Silent, Defect "
            "(if more than 3 qualify, extras are randomly removed).\n\n"
            "Each chosen pool gives 3 separate reward screens with guaranteed rarities "
            "(one Common, one Uncommon, one Rare) — you pick 1 card from each."
        ),
    },
}
