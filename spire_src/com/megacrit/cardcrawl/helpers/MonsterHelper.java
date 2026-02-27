package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.beyond.MysteriousSphere;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.metrics.BotDataUploader;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.monsters.beyond.AwakenedOne;
import com.megacrit.cardcrawl.monsters.beyond.Darkling;
import com.megacrit.cardcrawl.monsters.beyond.Deca;
import com.megacrit.cardcrawl.monsters.beyond.Donu;
import com.megacrit.cardcrawl.monsters.beyond.Exploder;
import com.megacrit.cardcrawl.monsters.beyond.GiantHead;
import com.megacrit.cardcrawl.monsters.beyond.Maw;
import com.megacrit.cardcrawl.monsters.beyond.Nemesis;
import com.megacrit.cardcrawl.monsters.beyond.OrbWalker;
import com.megacrit.cardcrawl.monsters.beyond.Reptomancer;
import com.megacrit.cardcrawl.monsters.beyond.Repulsor;
import com.megacrit.cardcrawl.monsters.beyond.SnakeDagger;
import com.megacrit.cardcrawl.monsters.beyond.Spiker;
import com.megacrit.cardcrawl.monsters.beyond.SpireGrowth;
import com.megacrit.cardcrawl.monsters.beyond.TimeEater;
import com.megacrit.cardcrawl.monsters.beyond.Transient;
import com.megacrit.cardcrawl.monsters.beyond.WrithingMass;
import com.megacrit.cardcrawl.monsters.city.BanditBear;
import com.megacrit.cardcrawl.monsters.city.BanditLeader;
import com.megacrit.cardcrawl.monsters.city.BanditPointy;
import com.megacrit.cardcrawl.monsters.city.BookOfStabbing;
import com.megacrit.cardcrawl.monsters.city.BronzeAutomaton;
import com.megacrit.cardcrawl.monsters.city.Byrd;
import com.megacrit.cardcrawl.monsters.city.Centurion;
import com.megacrit.cardcrawl.monsters.city.Champ;
import com.megacrit.cardcrawl.monsters.city.Chosen;
import com.megacrit.cardcrawl.monsters.city.GremlinLeader;
import com.megacrit.cardcrawl.monsters.city.Healer;
import com.megacrit.cardcrawl.monsters.city.Mugger;
import com.megacrit.cardcrawl.monsters.city.ShelledParasite;
import com.megacrit.cardcrawl.monsters.city.SnakePlant;
import com.megacrit.cardcrawl.monsters.city.Snecko;
import com.megacrit.cardcrawl.monsters.city.SphericGuardian;
import com.megacrit.cardcrawl.monsters.city.Taskmaster;
import com.megacrit.cardcrawl.monsters.city.TheCollector;
import com.megacrit.cardcrawl.monsters.ending.CorruptHeart;
import com.megacrit.cardcrawl.monsters.ending.SpireShield;
import com.megacrit.cardcrawl.monsters.ending.SpireSpear;
import com.megacrit.cardcrawl.monsters.exordium.AcidSlime_L;
import com.megacrit.cardcrawl.monsters.exordium.AcidSlime_M;
import com.megacrit.cardcrawl.monsters.exordium.AcidSlime_S;
import com.megacrit.cardcrawl.monsters.exordium.ApologySlime;
import com.megacrit.cardcrawl.monsters.exordium.Cultist;
import com.megacrit.cardcrawl.monsters.exordium.FungiBeast;
import com.megacrit.cardcrawl.monsters.exordium.GremlinFat;
import com.megacrit.cardcrawl.monsters.exordium.GremlinNob;
import com.megacrit.cardcrawl.monsters.exordium.GremlinThief;
import com.megacrit.cardcrawl.monsters.exordium.GremlinTsundere;
import com.megacrit.cardcrawl.monsters.exordium.GremlinWarrior;
import com.megacrit.cardcrawl.monsters.exordium.GremlinWizard;
import com.megacrit.cardcrawl.monsters.exordium.Hexaghost;
import com.megacrit.cardcrawl.monsters.exordium.JawWorm;
import com.megacrit.cardcrawl.monsters.exordium.Lagavulin;
import com.megacrit.cardcrawl.monsters.exordium.Looter;
import com.megacrit.cardcrawl.monsters.exordium.LouseDefensive;
import com.megacrit.cardcrawl.monsters.exordium.LouseNormal;
import com.megacrit.cardcrawl.monsters.exordium.Sentry;
import com.megacrit.cardcrawl.monsters.exordium.SlaverBlue;
import com.megacrit.cardcrawl.monsters.exordium.SlaverRed;
import com.megacrit.cardcrawl.monsters.exordium.SlimeBoss;
import com.megacrit.cardcrawl.monsters.exordium.SpikeSlime_L;
import com.megacrit.cardcrawl.monsters.exordium.SpikeSlime_M;
import com.megacrit.cardcrawl.monsters.exordium.SpikeSlime_S;
import com.megacrit.cardcrawl.monsters.exordium.TheGuardian;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MonsterHelper {
   private static final Logger logger = LogManager.getLogger(MonsterHelper.class.getName());
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("RunHistoryMonsterNames");
   public static final String[] MIXED_COMBAT_NAMES = uiStrings.TEXT;
   public static final String BLUE_SLAVER_ENC = "Blue Slaver";
   public static final String CULTIST_ENC = "Cultist";
   public static final String JAW_WORM_ENC = "Jaw Worm";
   public static final String LOOTER_ENC = "Looter";
   public static final String TWO_LOUSE_ENC = "2 Louse";
   public static final String SMALL_SLIMES_ENC = "Small Slimes";
   public static final String GREMLIN_GANG_ENC = "Gremlin Gang";
   public static final String RED_SLAVER_ENC = "Red Slaver";
   public static final String LARGE_SLIME_ENC = "Large Slime";
   public static final String LVL_1_THUGS_ENC = "Exordium Thugs";
   public static final String LVL_1_WILDLIFE_ENC = "Exordium Wildlife";
   public static final String THREE_LOUSE_ENC = "3 Louse";
   public static final String TWO_FUNGI_ENC = "2 Fungi Beasts";
   public static final String LOTS_OF_SLIMES_ENC = "Lots of Slimes";
   public static final String GREMLIN_NOB_ENC = "Gremlin Nob";
   public static final String LAGAVULIN_ENC = "Lagavulin";
   public static final String THREE_SENTRY_ENC = "3 Sentries";
   public static final String LAGAVULIN_EVENT_ENC = "Lagavulin Event";
   public static final String MUSHROOMS_EVENT_ENC = "The Mushroom Lair";
   public static final String GUARDIAN_ENC = "The Guardian";
   public static final String HEXAGHOST_ENC = "Hexaghost";
   public static final String SLIME_BOSS_ENC = "Slime Boss";
   public static final String TWO_THIEVES_ENC = "2 Thieves";
   public static final String THREE_BYRDS_ENC = "3 Byrds";
   public static final String CHOSEN_ENC = "Chosen";
   public static final String SHELL_PARASITE_ENC = "Shell Parasite";
   public static final String SPHERE_GUARDIAN_ENC = "Spheric Guardian";
   public static final String CULTIST_CHOSEN_ENC = "Cultist and Chosen";
   public static final String THREE_CULTISTS_ENC = "3 Cultists";
   public static final String FOUR_BYRDS_ENC = "4 Byrds";
   public static final String CHOSEN_FLOCK_ENC = "Chosen and Byrds";
   public static final String SENTRY_SPHERE_ENC = "Sentry and Sphere";
   public static final String SNAKE_PLANT_ENC = "Snake Plant";
   public static final String SNECKO_ENC = "Snecko";
   public static final String TANK_HEALER_ENC = "Centurion and Healer";
   public static final String PARASITE_AND_FUNGUS = "Shelled Parasite and Fungi";
   public static final String STAB_BOOK_ENC = "Book of Stabbing";
   public static final String GREMLIN_LEADER_ENC = "Gremlin Leader";
   public static final String SLAVERS_ENC = "Slavers";
   public static final String MASKED_BANDITS_ENC = "Masked Bandits";
   public static final String COLOSSEUM_SLAVER_ENC = "Colosseum Slavers";
   public static final String COLOSSEUM_NOB_ENC = "Colosseum Nobs";
   public static final String AUTOMATON_ENC = "Automaton";
   public static final String CHAMP_ENC = "Champ";
   public static final String COLLECTOR_ENC = "Collector";
   public static final String THREE_DARKLINGS_ENC = "3 Darklings";
   public static final String THREE_SHAPES_ENC = "3 Shapes";
   public static final String ORB_WALKER_ENC = "Orb Walker";
   public static final String TRANSIENT_ENC = "Transient";
   public static final String REPTOMANCER_ENC = "Reptomancer";
   public static final String SPIRE_GROWTH_ENC = "Spire Growth";
   public static final String MAW_ENC = "Maw";
   public static final String FOUR_SHAPES_ENC = "4 Shapes";
   public static final String SPHERE_TWO_SHAPES_ENC = "Sphere and 2 Shapes";
   public static final String JAW_WORMS_HORDE = "Jaw Worm Horde";
   public static final String SNECKO_WITH_MYSTICS = "Snecko and Mystics";
   public static final String WRITHING_MASS_ENC = "Writhing Mass";
   public static final String TWO_ORB_WALKER_ENC = "2 Orb Walkers";
   public static final String NEMESIS_ENC = "Nemesis";
   public static final String GIANT_HEAD_ENC = "Giant Head";
   public static final String MYSTERIOUS_SPHERE_ENC = "Mysterious Sphere";
   public static final String MIND_BLOOM_BOSS = "Mind Bloom Boss Battle";
   public static final String TIME_EATER_ENC = "Time Eater";
   public static final String AWAKENED_ENC = "Awakened One";
   public static final String DONU_DECA_ENC = "Donu and Deca";
   public static final String THE_HEART_ENC = "The Heart";
   public static final String SHIELD_SPEAR_ENC = "Shield and Spear";
   public static final String EYES_ENC = "The Eyes";
   public static final String APOLOGY_SLIME_ENC = "Apologetic Slime";
   public static final String OLD_REPTO_ONE_ENC = "Flame Bruiser 1 Orb";
   public static final String OLD_REPTO_TWO_ENC = "Flame Bruiser 2 Orb";
   public static final String OLD_SLAVER_PARASITE = "Slaver and Parasite";
   public static final String OLD_SNECKO_MYSTICS = "Snecko and Mystics";

   public static String getEncounterName(String key) {
      if (key == null) {
         return "";
      } else {
         switch (key) {
            case "Flame Bruiser 1 Orb":
            case "Flame Bruiser 2 Orb":
               return MIXED_COMBAT_NAMES[25];
            case "Slaver and Parasite":
               return MIXED_COMBAT_NAMES[26];
            case "Snecko and Mystics":
               return MIXED_COMBAT_NAMES[27];
            default:
               switch (key) {
                  case "Blue Slaver":
                     return SlaverBlue.NAME;
                  case "Cultist":
                     return Cultist.NAME;
                  case "Jaw Worm":
                     return JawWorm.NAME;
                  case "Looter":
                     return Looter.NAME;
                  case "Gremlin Gang":
                     return MIXED_COMBAT_NAMES[0];
                  case "Red Slaver":
                     return SlaverRed.NAME;
                  case "Large Slime":
                     return MIXED_COMBAT_NAMES[1];
                  case "Exordium Thugs":
                     return MIXED_COMBAT_NAMES[2];
                  case "Exordium Wildlife":
                     return MIXED_COMBAT_NAMES[3];
                  case "3 Louse":
                     return LouseNormal.NAME;
                  case "2 Louse":
                     return LouseNormal.NAME;
                  case "2 Fungi Beasts":
                     return FungiBeast.NAME;
                  case "Lots of Slimes":
                     return MIXED_COMBAT_NAMES[4];
                  case "Small Slimes":
                     return MIXED_COMBAT_NAMES[5];
                  case "Gremlin Nob":
                     return GremlinNob.NAME;
                  case "Lagavulin":
                     return Lagavulin.NAME;
                  case "3 Sentries":
                     return MIXED_COMBAT_NAMES[23];
                  case "Lagavulin Event":
                     return Lagavulin.NAME;
                  case "The Mushroom Lair":
                     return FungiBeast.NAME;
                  case "The Guardian":
                     return TheGuardian.NAME;
                  case "Hexaghost":
                     return Hexaghost.NAME;
                  case "Slime Boss":
                     return SlimeBoss.NAME;
                  default:
                     switch (key) {
                        case "2 Thieves":
                           return MIXED_COMBAT_NAMES[6];
                        case "3 Byrds":
                           return MIXED_COMBAT_NAMES[7];
                        case "4 Byrds":
                           return MIXED_COMBAT_NAMES[8];
                        case "Chosen":
                           return Chosen.NAME;
                        case "Shell Parasite":
                           return ShelledParasite.NAME;
                        case "Spheric Guardian":
                           return SphericGuardian.NAME;
                        case "Cultist and Chosen":
                           return MIXED_COMBAT_NAMES[24];
                        case "3 Cultists":
                           return MIXED_COMBAT_NAMES[9];
                        case "Chosen and Byrds":
                           return MIXED_COMBAT_NAMES[10];
                        case "Sentry and Sphere":
                           return MIXED_COMBAT_NAMES[11];
                        case "Snake Plant":
                           return SnakePlant.NAME;
                        case "Snecko":
                           return Snecko.NAME;
                        case "Centurion and Healer":
                           return MIXED_COMBAT_NAMES[12];
                        case "Shelled Parasite and Fungi":
                           return MIXED_COMBAT_NAMES[13];
                        case "Book of Stabbing":
                           return BookOfStabbing.NAME;
                        case "Gremlin Leader":
                           return GremlinLeader.NAME;
                        case "Slavers":
                           return Taskmaster.NAME;
                        case "Masked Bandits":
                           return MIXED_COMBAT_NAMES[14];
                        case "Colosseum Nobs":
                           return MIXED_COMBAT_NAMES[15];
                        case "Colosseum Slavers":
                           return MIXED_COMBAT_NAMES[16];
                        case "Automaton":
                           return BronzeAutomaton.NAME;
                        case "Champ":
                           return Champ.NAME;
                        case "Collector":
                           return TheCollector.NAME;
                        default:
                           switch (key) {
                              case "Reptomancer":
                                 return Reptomancer.NAME;
                              case "Transient":
                                 return Transient.NAME;
                              case "3 Darklings":
                                 return Darkling.NAME;
                              case "3 Shapes":
                                 return MIXED_COMBAT_NAMES[17];
                              case "Jaw Worm Horde":
                                 return MIXED_COMBAT_NAMES[18];
                              case "Orb Walker":
                                 return OrbWalker.NAME;
                              case "Spire Growth":
                                 return SpireGrowth.NAME;
                              case "Maw":
                                 return Maw.NAME;
                              case "4 Shapes":
                                 return MIXED_COMBAT_NAMES[19];
                              case "Sphere and 2 Shapes":
                                 return MIXED_COMBAT_NAMES[20];
                              case "2 Orb Walkers":
                                 return MIXED_COMBAT_NAMES[21];
                              case "Nemesis":
                                 return Nemesis.NAME;
                              case "Writhing Mass":
                                 return WrithingMass.NAME;
                              case "Giant Head":
                                 return GiantHead.NAME;
                              case "Mysterious Sphere":
                                 return MysteriousSphere.NAME;
                              case "Time Eater":
                                 return TimeEater.NAME;
                              case "Awakened One":
                                 return AwakenedOne.NAME;
                              case "Donu and Deca":
                                 return MIXED_COMBAT_NAMES[22];
                              default:
                                 switch (key) {
                                    case "The Heart":
                                       return CorruptHeart.NAME;
                                    case "Shield and Spear":
                                       return MIXED_COMBAT_NAMES[28];
                                    default:
                                       return "";
                                 }
                           }
                     }
               }
         }
      }
   }

   public static MonsterGroup getEncounter(String key) {
      switch (key) {
         case "Blue Slaver":
            return new MonsterGroup(new SlaverBlue(0.0F, 0.0F));
         case "Cultist":
            return new MonsterGroup(new Cultist(0.0F, -10.0F));
         case "Jaw Worm":
            return new MonsterGroup(new JawWorm(0.0F, 25.0F));
         case "Looter":
            return new MonsterGroup(new Looter(0.0F, 0.0F));
         case "Gremlin Gang":
            return spawnGremlins();
         case "Red Slaver":
            return new MonsterGroup(new SlaverRed(0.0F, 0.0F));
         case "Large Slime":
            if (AbstractDungeon.miscRng.randomBoolean()) {
               return new MonsterGroup(new AcidSlime_L(0.0F, 0.0F));
            }

            return new MonsterGroup(new SpikeSlime_L(0.0F, 0.0F));
         case "Exordium Thugs":
            return bottomHumanoid();
         case "Exordium Wildlife":
            return bottomWildlife();
         case "3 Louse":
            return new MonsterGroup(new AbstractMonster[]{getLouse(-350.0F, 25.0F), getLouse(-125.0F, 10.0F), getLouse(80.0F, 30.0F)});
         case "2 Louse":
            return new MonsterGroup(new AbstractMonster[]{getLouse(-200.0F, 10.0F), getLouse(80.0F, 30.0F)});
         case "2 Fungi Beasts":
            return new MonsterGroup(new AbstractMonster[]{new FungiBeast(-400.0F, 30.0F), new FungiBeast(-40.0F, 20.0F)});
         case "Lots of Slimes":
            return spawnManySmallSlimes();
         case "Small Slimes":
            return spawnSmallSlimes();
         case "Gremlin Nob":
            return new MonsterGroup(new GremlinNob(0.0F, 0.0F));
         case "Lagavulin":
            return new MonsterGroup(new Lagavulin(true));
         case "3 Sentries":
            return new MonsterGroup(new AbstractMonster[]{new Sentry(-330.0F, 25.0F), new Sentry(-85.0F, 10.0F), new Sentry(140.0F, 30.0F)});
         case "Lagavulin Event":
            return new MonsterGroup(new Lagavulin(false));
         case "The Mushroom Lair":
            return new MonsterGroup(new AbstractMonster[]{new FungiBeast(-450.0F, 30.0F), new FungiBeast(-145.0F, 20.0F), new FungiBeast(180.0F, 15.0F)});
         case "The Guardian":
            return new MonsterGroup(new TheGuardian());
         case "Hexaghost":
            return new MonsterGroup(new Hexaghost());
         case "Slime Boss":
            return new MonsterGroup(new SlimeBoss());
         default:
            switch (key) {
               case "2 Thieves":
                  return new MonsterGroup(new AbstractMonster[]{new Looter(-200.0F, 15.0F), new Mugger(80.0F, 0.0F)});
               case "3 Byrds":
                  return new MonsterGroup(
                     new AbstractMonster[]{
                        new Byrd(-360.0F, MathUtils.random(25.0F, 70.0F)),
                        new Byrd(-80.0F, MathUtils.random(25.0F, 70.0F)),
                        new Byrd(200.0F, MathUtils.random(25.0F, 70.0F))
                     }
                  );
               case "4 Byrds":
                  return new MonsterGroup(
                     new AbstractMonster[]{
                        new Byrd(-470.0F, MathUtils.random(25.0F, 70.0F)),
                        new Byrd(-210.0F, MathUtils.random(25.0F, 70.0F)),
                        new Byrd(50.0F, MathUtils.random(25.0F, 70.0F)),
                        new Byrd(310.0F, MathUtils.random(25.0F, 70.0F))
                     }
                  );
               case "Chosen":
                  return new MonsterGroup(new Chosen());
               case "Shell Parasite":
                  return new MonsterGroup(new ShelledParasite());
               case "Spheric Guardian":
                  return new MonsterGroup(new SphericGuardian());
               case "Cultist and Chosen":
                  return new MonsterGroup(new AbstractMonster[]{new Cultist(-230.0F, 15.0F, false), new Chosen(100.0F, 25.0F)});
               case "3 Cultists":
                  return new MonsterGroup(
                     new AbstractMonster[]{new Cultist(-465.0F, -20.0F, false), new Cultist(-130.0F, 15.0F, false), new Cultist(200.0F, -5.0F)}
                  );
               case "Chosen and Byrds":
                  return new MonsterGroup(new AbstractMonster[]{new Byrd(-170.0F, MathUtils.random(25.0F, 70.0F)), new Chosen(80.0F, 0.0F)});
               case "Sentry and Sphere":
                  return new MonsterGroup(new AbstractMonster[]{new Sentry(-305.0F, 30.0F), new SphericGuardian()});
               case "Snake Plant":
                  return new MonsterGroup(new SnakePlant(-30.0F, -30.0F));
               case "Snecko":
                  return new MonsterGroup(new Snecko());
               case "Centurion and Healer":
                  return new MonsterGroup(new AbstractMonster[]{new Centurion(-200.0F, 15.0F), new Healer(120.0F, 0.0F)});
               case "Shelled Parasite and Fungi":
                  return new MonsterGroup(new AbstractMonster[]{new ShelledParasite(-260.0F, 15.0F), new FungiBeast(120.0F, 0.0F)});
               case "Book of Stabbing":
                  return new MonsterGroup(new BookOfStabbing());
               case "Gremlin Leader":
                  return new MonsterGroup(
                     new AbstractMonster[]{
                        spawnGremlin(GremlinLeader.POSX[0], GremlinLeader.POSY[0]),
                        spawnGremlin(GremlinLeader.POSX[1], GremlinLeader.POSY[1]),
                        new GremlinLeader()
                     }
                  );
               case "Slavers":
                  return new MonsterGroup(new AbstractMonster[]{new SlaverBlue(-385.0F, -15.0F), new Taskmaster(-133.0F, 0.0F), new SlaverRed(125.0F, -30.0F)});
               case "Masked Bandits":
                  return new MonsterGroup(
                     new AbstractMonster[]{new BanditPointy(-320.0F, 0.0F), new BanditLeader(-75.0F, -6.0F), new BanditBear(150.0F, -6.0F)}
                  );
               case "Colosseum Nobs":
                  return new MonsterGroup(new AbstractMonster[]{new Taskmaster(-270.0F, 15.0F), new GremlinNob(130.0F, 0.0F)});
               case "Colosseum Slavers":
                  return new MonsterGroup(new AbstractMonster[]{new SlaverBlue(-270.0F, 15.0F), new SlaverRed(130.0F, 0.0F)});
               case "Automaton":
                  return new MonsterGroup(new BronzeAutomaton());
               case "Champ":
                  return new MonsterGroup(new Champ());
               case "Collector":
                  return new MonsterGroup(new TheCollector());
               default:
                  switch (key) {
                     case "Flame Bruiser 1 Orb":
                        return new MonsterGroup(new AbstractMonster[]{new Reptomancer(), new SnakeDagger(Reptomancer.POSX[0], Reptomancer.POSY[0])});
                     case "Flame Bruiser 2 Orb":
                     case "Reptomancer":
                        return new MonsterGroup(
                           new AbstractMonster[]{
                              new SnakeDagger(Reptomancer.POSX[1], Reptomancer.POSY[1]),
                              new Reptomancer(),
                              new SnakeDagger(Reptomancer.POSX[0], Reptomancer.POSY[0])
                           }
                        );
                     case "Transient":
                        return new MonsterGroup(new Transient());
                     case "3 Darklings":
                        return new MonsterGroup(new AbstractMonster[]{new Darkling(-440.0F, 10.0F), new Darkling(-140.0F, 30.0F), new Darkling(180.0F, -5.0F)});
                     case "3 Shapes":
                        return spawnShapes(true);
                     case "Jaw Worm Horde":
                        return new MonsterGroup(
                           new AbstractMonster[]{new JawWorm(-490.0F, -5.0F, true), new JawWorm(-150.0F, 20.0F, true), new JawWorm(175.0F, 5.0F, true)}
                        );
                     case "Snecko and Mystics":
                        return new MonsterGroup(new AbstractMonster[]{new Healer(-475.0F, -10.0F), new Snecko(-130.0F, -13.0F), new Healer(175.0F, -10.0F)});
                     case "Orb Walker":
                        return new MonsterGroup(new OrbWalker(-30.0F, 20.0F));
                     case "Spire Growth":
                        return new MonsterGroup(new SpireGrowth());
                     case "Maw":
                        return new MonsterGroup(new Maw(-70.0F, 20.0F));
                     case "4 Shapes":
                        return spawnShapes(false);
                     case "Sphere and 2 Shapes":
                        return new MonsterGroup(
                           new AbstractMonster[]{getAncientShape(-435.0F, 10.0F), getAncientShape(-210.0F, 0.0F), new SphericGuardian(110.0F, 10.0F)}
                        );
                     case "2 Orb Walkers":
                        return new MonsterGroup(new AbstractMonster[]{new OrbWalker(-250.0F, 32.0F), new OrbWalker(150.0F, 26.0F)});
                     case "Nemesis":
                        return new MonsterGroup(new Nemesis());
                     case "Writhing Mass":
                        return new MonsterGroup(new WrithingMass());
                     case "Giant Head":
                        return new MonsterGroup(new GiantHead());
                     case "Mysterious Sphere":
                        return new MonsterGroup(
                           new AbstractMonster[]{getAncientShape(-475.0F, 10.0F), getAncientShape(-250.0F, 0.0F), new OrbWalker(150.0F, 30.0F)}
                        );
                     case "Time Eater":
                        return new MonsterGroup(new TimeEater());
                     case "Awakened One":
                        return new MonsterGroup(
                           new AbstractMonster[]{new Cultist(-590.0F, 10.0F, false), new Cultist(-298.0F, -10.0F, false), new AwakenedOne(100.0F, 15.0F)}
                        );
                     case "Donu and Deca":
                        return new MonsterGroup(new AbstractMonster[]{new Deca(), new Donu()});
                     default:
                        switch (key) {
                           case "The Heart":
                              return new MonsterGroup(new CorruptHeart());
                           case "Shield and Spear":
                              return new MonsterGroup(new AbstractMonster[]{new SpireShield(), new SpireSpear()});
                           default:
                              return new MonsterGroup(new ApologySlime());
                        }
                  }
            }
      }
   }

   private static float randomYOffset(float y) {
      return y + MathUtils.random(-20.0F, 20.0F);
   }

   private static float randomXOffset(float x) {
      return x + MathUtils.random(-20.0F, 20.0F);
   }

   public static AbstractMonster getGremlin(String key, float xPos, float yPos) {
      switch (key) {
         case "GremlinWarrior":
            return new GremlinWarrior(xPos, yPos);
         case "GremlinThief":
            return new GremlinThief(xPos, yPos);
         case "GremlinFat":
            return new GremlinFat(xPos, yPos);
         case "GremlinTsundere":
            return new GremlinTsundere(xPos, yPos);
         case "GremlinWizard":
            return new GremlinWizard(xPos, yPos);
         default:
            logger.info("UNKNOWN GREMLIN: " + key);
            return null;
      }
   }

   public static AbstractMonster getAncientShape(float x, float y) {
      switch (AbstractDungeon.miscRng.random(2)) {
         case 0:
            return new Spiker(x, y);
         case 1:
            return new Repulsor(x, y);
         default:
            return new Exploder(x, y);
      }
   }

   public static AbstractMonster getShape(String key, float xPos, float yPos) {
      switch (key) {
         case "Repulsor":
            return new Repulsor(xPos, yPos);
         case "Spiker":
            return new Spiker(xPos, yPos);
         case "Exploder":
            return new Exploder(xPos, yPos);
         default:
            logger.info("UNKNOWN SHAPE: " + key);
            return null;
      }
   }

   private static MonsterGroup spawnShapes(boolean weak) {
      ArrayList<String> shapePool = new ArrayList<>();
      shapePool.add("Repulsor");
      shapePool.add("Repulsor");
      shapePool.add("Exploder");
      shapePool.add("Exploder");
      shapePool.add("Spiker");
      shapePool.add("Spiker");
      AbstractMonster[] retVal;
      if (weak) {
         retVal = new AbstractMonster[3];
      } else {
         retVal = new AbstractMonster[4];
      }

      int index = AbstractDungeon.miscRng.random(shapePool.size() - 1);
      String key = shapePool.get(index);
      shapePool.remove(index);
      retVal[0] = getShape(key, -480.0F, 6.0F);
      index = AbstractDungeon.miscRng.random(shapePool.size() - 1);
      key = shapePool.get(index);
      shapePool.remove(index);
      retVal[1] = getShape(key, -240.0F, -6.0F);
      index = AbstractDungeon.miscRng.random(shapePool.size() - 1);
      key = shapePool.get(index);
      shapePool.remove(index);
      retVal[2] = getShape(key, 0.0F, -12.0F);
      if (!weak) {
         index = AbstractDungeon.miscRng.random(shapePool.size() - 1);
         key = shapePool.get(index);
         shapePool.remove(index);
         retVal[3] = getShape(key, 240.0F, 12.0F);
      }

      return new MonsterGroup(retVal);
   }

   private static MonsterGroup spawnSmallSlimes() {
      AbstractMonster[] retVal = new AbstractMonster[2];
      if (AbstractDungeon.miscRng.randomBoolean()) {
         retVal[0] = new SpikeSlime_S(-230.0F, 32.0F, 0);
         retVal[1] = new AcidSlime_M(35.0F, 8.0F);
      } else {
         retVal[0] = new AcidSlime_S(-230.0F, 32.0F, 0);
         retVal[1] = new SpikeSlime_M(35.0F, 8.0F);
      }

      return new MonsterGroup(retVal);
   }

   private static MonsterGroup spawnManySmallSlimes() {
      ArrayList<String> slimePool = new ArrayList<>();
      slimePool.add("SpikeSlime_S");
      slimePool.add("SpikeSlime_S");
      slimePool.add("SpikeSlime_S");
      slimePool.add("AcidSlime_S");
      slimePool.add("AcidSlime_S");
      AbstractMonster[] retVal = new AbstractMonster[5];
      int index = AbstractDungeon.miscRng.random(slimePool.size() - 1);
      String key = slimePool.get(index);
      slimePool.remove(index);
      if (key.equals("SpikeSlime_S")) {
         retVal[0] = new SpikeSlime_S(-480.0F, 30.0F, 0);
      } else {
         retVal[0] = new AcidSlime_S(-480.0F, 30.0F, 0);
      }

      index = AbstractDungeon.miscRng.random(slimePool.size() - 1);
      key = slimePool.get(index);
      slimePool.remove(index);
      if (key.equals("SpikeSlime_S")) {
         retVal[1] = new SpikeSlime_S(-320.0F, 2.0F, 0);
      } else {
         retVal[1] = new AcidSlime_S(-320.0F, 2.0F, 0);
      }

      index = AbstractDungeon.miscRng.random(slimePool.size() - 1);
      key = slimePool.get(index);
      slimePool.remove(index);
      if (key.equals("SpikeSlime_S")) {
         retVal[2] = new SpikeSlime_S(-160.0F, 32.0F, 0);
      } else {
         retVal[2] = new AcidSlime_S(-160.0F, 32.0F, 0);
      }

      index = AbstractDungeon.miscRng.random(slimePool.size() - 1);
      key = slimePool.get(index);
      slimePool.remove(index);
      if (key.equals("SpikeSlime_S")) {
         retVal[3] = new SpikeSlime_S(10.0F, -12.0F, 0);
      } else {
         retVal[3] = new AcidSlime_S(10.0F, -12.0F, 0);
      }

      index = AbstractDungeon.miscRng.random(slimePool.size() - 1);
      key = slimePool.get(index);
      slimePool.remove(index);
      if (key.equals("SpikeSlime_S")) {
         retVal[4] = new SpikeSlime_S(200.0F, 9.0F, 0);
      } else {
         retVal[4] = new AcidSlime_S(200.0F, 9.0F, 0);
      }

      return new MonsterGroup(retVal);
   }

   private static MonsterGroup spawnGremlins() {
      ArrayList<String> gremlinPool = new ArrayList<>();
      gremlinPool.add("GremlinWarrior");
      gremlinPool.add("GremlinWarrior");
      gremlinPool.add("GremlinThief");
      gremlinPool.add("GremlinThief");
      gremlinPool.add("GremlinFat");
      gremlinPool.add("GremlinFat");
      gremlinPool.add("GremlinTsundere");
      gremlinPool.add("GremlinWizard");
      AbstractMonster[] retVal = new AbstractMonster[4];
      int index = AbstractDungeon.miscRng.random(gremlinPool.size() - 1);
      String key = gremlinPool.get(index);
      gremlinPool.remove(index);
      retVal[0] = getGremlin(key, -320.0F, 25.0F);
      index = AbstractDungeon.miscRng.random(gremlinPool.size() - 1);
      key = gremlinPool.get(index);
      gremlinPool.remove(index);
      retVal[1] = getGremlin(key, -160.0F, -12.0F);
      index = AbstractDungeon.miscRng.random(gremlinPool.size() - 1);
      key = gremlinPool.get(index);
      gremlinPool.remove(index);
      retVal[2] = getGremlin(key, 25.0F, -35.0F);
      index = AbstractDungeon.miscRng.random(gremlinPool.size() - 1);
      key = gremlinPool.get(index);
      gremlinPool.remove(index);
      retVal[3] = getGremlin(key, 205.0F, 40.0F);
      return new MonsterGroup(retVal);
   }

   private static AbstractMonster spawnGremlin(float x, float y) {
      ArrayList<String> gremlinPool = new ArrayList<>();
      gremlinPool.add("GremlinWarrior");
      gremlinPool.add("GremlinWarrior");
      gremlinPool.add("GremlinThief");
      gremlinPool.add("GremlinThief");
      gremlinPool.add("GremlinFat");
      gremlinPool.add("GremlinFat");
      gremlinPool.add("GremlinTsundere");
      gremlinPool.add("GremlinWizard");
      return getGremlin(gremlinPool.get(AbstractDungeon.miscRng.random(0, gremlinPool.size() - 1)), x, y);
   }

   private static MonsterGroup bottomHumanoid() {
      AbstractMonster[] monsters = new AbstractMonster[]{
         bottomGetWeakWildlife(randomXOffset(-160.0F), randomYOffset(20.0F)), bottomGetStrongHumanoid(randomXOffset(130.0F), randomYOffset(20.0F))
      };
      return new MonsterGroup(monsters);
   }

   private static MonsterGroup bottomWildlife() {
      int numMonster = 2;
      AbstractMonster[] monsters = new AbstractMonster[numMonster];
      if (numMonster == 2) {
         monsters[0] = bottomGetStrongWildlife(randomXOffset(-150.0F), randomYOffset(20.0F));
         monsters[1] = bottomGetWeakWildlife(randomXOffset(150.0F), randomYOffset(20.0F));
      } else if (numMonster == 3) {
         monsters[0] = bottomGetWeakWildlife(randomXOffset(-200.0F), randomYOffset(20.0F));
         monsters[1] = bottomGetWeakWildlife(randomXOffset(0.0F), randomYOffset(20.0F));
         monsters[2] = bottomGetWeakWildlife(randomXOffset(200.0F), randomYOffset(20.0F));
      }

      return new MonsterGroup(monsters);
   }

   private static AbstractMonster bottomGetStrongHumanoid(float x, float y) {
      ArrayList<AbstractMonster> monsters = new ArrayList<>();
      monsters.add(new Cultist(x, y));
      monsters.add(getSlaver(x, y));
      monsters.add(new Looter(x, y));
      return monsters.get(AbstractDungeon.miscRng.random(0, monsters.size() - 1));
   }

   private static AbstractMonster bottomGetStrongWildlife(float x, float y) {
      ArrayList<AbstractMonster> monsters = new ArrayList<>();
      monsters.add(new FungiBeast(x, y));
      monsters.add(new JawWorm(x, y));
      return monsters.get(AbstractDungeon.miscRng.random(0, monsters.size() - 1));
   }

   private static AbstractMonster bottomGetWeakWildlife(float x, float y) {
      ArrayList<AbstractMonster> monsters = new ArrayList<>();
      monsters.add(getLouse(x, y));
      monsters.add(new SpikeSlime_M(x, y));
      monsters.add(new AcidSlime_M(x, y));
      return monsters.get(AbstractDungeon.miscRng.random(0, monsters.size() - 1));
   }

   private static AbstractMonster getSlaver(float x, float y) {
      return (AbstractMonster)(AbstractDungeon.miscRng.randomBoolean() ? new SlaverRed(x, y) : new SlaverBlue(x, y));
   }

   private static AbstractMonster getLouse(float x, float y) {
      return (AbstractMonster)(AbstractDungeon.miscRng.randomBoolean() ? new LouseNormal(x, y) : new LouseDefensive(x, y));
   }

   public static void uploadEnemyData() {
      if (System.getProperty("seedsearch.standalone") != null) {
         seedsearch.StandaloneHooks.encounterIds.clear();
      }
      ArrayList<String> derp = new ArrayList<>();
      ArrayList<EnemyData> data = new ArrayList<>();
      data.add(new EnemyData("Blue Slaver", 1, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Cultist", 1, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Jaw Worm", 1, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("2 Louse", 1, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Small Slimes", 1, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Gremlin Gang", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Large Slime", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Looter", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Lots of Slimes", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Exordium Thugs", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Exordium Wildlife", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Red Slaver", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("3 Louse", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("2 Fungi Beasts", 1, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Gremlin Nob", 1, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Lagavulin", 1, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("3 Sentries", 1, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Lagavulin Event", 1, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("The Mushroom Lair", 1, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("The Guardian", 1, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Hexaghost", 1, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Slime Boss", 1, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Chosen", 2, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Shell Parasite", 2, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Spheric Guardian", 2, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("3 Byrds", 2, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("2 Thieves", 2, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Chosen and Byrds", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Sentry and Sphere", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Snake Plant", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Snecko", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Centurion and Healer", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Cultist and Chosen", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("3 Cultists", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Shelled Parasite and Fungi", 2, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Gremlin Leader", 2, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Slavers", 2, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Book of Stabbing", 2, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Masked Bandits", 2, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("Colosseum Nobs", 2, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("Colosseum Slavers", 2, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("Automaton", 2, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Champ", 2, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Collector", 2, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Orb Walker", 3, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("3 Darklings", 3, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("3 Shapes", 3, EnemyData.MonsterType.WEAK));
      data.add(new EnemyData("Transient", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("4 Shapes", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Maw", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Jaw Worm Horde", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Sphere and 2 Shapes", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Spire Growth", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Writhing Mass", 3, EnemyData.MonsterType.STRONG));
      data.add(new EnemyData("Giant Head", 3, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Nemesis", 3, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Reptomancer", 3, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("Mysterious Sphere", 3, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("Mind Bloom Boss Battle", 3, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("2 Orb Walkers", 3, EnemyData.MonsterType.EVENT));
      data.add(new EnemyData("Awakened One", 3, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Donu and Deca", 3, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Time Eater", 3, EnemyData.MonsterType.BOSS));
      data.add(new EnemyData("Shield and Spear", 4, EnemyData.MonsterType.ELITE));
      data.add(new EnemyData("The Heart", 4, EnemyData.MonsterType.BOSS));

      for (EnemyData d : data) {
         derp.add(d.gameDataUploadData());
         if (System.getProperty("seedsearch.standalone") != null) {
            seedsearch.StandaloneHooks.encounterIds.add(d.name);
         }
      }

      BotDataUploader.uploadDataAsync(BotDataUploader.GameDataType.ENEMY_DATA, EnemyData.gameDataUploadHeader(), derp);
   }
}
