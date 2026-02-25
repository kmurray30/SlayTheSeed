package com.megacrit.cardcrawl.events.exordium;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.EffectHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeadAdventurer extends AbstractEvent {
   private static final Logger logger = LogManager.getLogger(DeadAdventurer.class.getName());
   public static final String ID = "Dead Adventurer";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Dead Adventurer");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final int GOLD_REWARD = 30;
   private static final int ENCOUNTER_CHANCE_START = 25;
   private static final int A_2_CHANCE_START = 35;
   private static final int ENCOUNTER_CHANCE_RAMP = 25;
   private static final String FIGHT_MSG;
   private static final String ESCAPE_MSG;
   private int numRewards = 0;
   private int encounterChance = 0;
   private ArrayList<String> rewards = new ArrayList<>();
   private float x = 800.0F * Settings.xScale;
   private float y = AbstractDungeon.floorY;
   private int enemy = 0;
   private DeadAdventurer.CUR_SCREEN screen = DeadAdventurer.CUR_SCREEN.INTRO;
   private static final Color DARKEN_COLOR = new Color(0.5F, 0.5F, 0.5F, 1.0F);
   public static final String LAGAVULIN_FIGHT = "Lagavulin Dead Adventurers Fight";
   private Texture adventurerImg;
   private int goldRewardMetric = 0;
   private AbstractRelic relicRewardMetric = null;

   public DeadAdventurer() {
      this.rewards.add("GOLD");
      this.rewards.add("NOTHING");
      this.rewards.add("RELIC");
      Collections.shuffle(this.rewards, new Random(AbstractDungeon.miscRng.randomLong()));
      if (AbstractDungeon.ascensionLevel >= 15) {
         this.encounterChance = 35;
      } else {
         this.encounterChance = 25;
      }

      this.enemy = AbstractDungeon.miscRng.random(0, 2);
      this.adventurerImg = ImageMaster.loadImage("images/npcs/nopants.png");
      this.body = DESCRIPTIONS[2];
      switch (this.enemy) {
         case 0:
            this.body = this.body + DESCRIPTIONS[3];
            break;
         case 1:
            this.body = this.body + DESCRIPTIONS[4];
            break;
         default:
            this.body = this.body + DESCRIPTIONS[5];
      }

      this.body = this.body + DESCRIPTIONS[6];
      this.roomEventText.addDialogOption(OPTIONS[0] + this.encounterChance + OPTIONS[4]);
      this.roomEventText.addDialogOption(OPTIONS[1]);
      this.hasDialog = true;
      this.hasFocus = true;
   }

   @Override
   public void update() {
      super.update();
      if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.EVENT && AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMPLETE) {
         this.imgColor = DARKEN_COLOR;
      } else {
         this.imgColor = Color.WHITE.cpy();
      }

      if (!RoomEventDialog.waitForInput) {
         this.buttonEffect(this.roomEventText.getSelectedOption());
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            switch (buttonPressed) {
               case 0:
                  if (AbstractDungeon.miscRng.random(0, 99) < this.encounterChance) {
                     this.screen = DeadAdventurer.CUR_SCREEN.FAIL;
                     this.roomEventText.updateBodyText(FIGHT_MSG);
                     this.roomEventText.updateDialogOption(0, OPTIONS[2]);
                     this.roomEventText.removeDialogOption(1);
                     if (Settings.isDailyRun) {
                        AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(30));
                     } else {
                        AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(25, 35));
                     }

                     AbstractDungeon.getCurrRoom().monsters = MonsterHelper.getEncounter(this.getMonster());
                     AbstractDungeon.getCurrRoom().eliteTrigger = true;
                  } else {
                     this.randomReward();
                  }

                  return;
               case 1:
                  this.screen = DeadAdventurer.CUR_SCREEN.ESCAPE;
                  this.roomEventText.updateBodyText(ESCAPE_MSG);
                  this.roomEventText.updateDialogOption(0, OPTIONS[1]);
                  this.roomEventText.removeDialogOption(1);
                  return;
               default:
                  return;
            }
         case SUCCESS:
            this.openMap();
            break;
         case FAIL:
            for (String s : this.rewards) {
               if (s.equals("GOLD")) {
                  AbstractDungeon.getCurrRoom().addGoldToRewards(30);
               } else if (s.equals("RELIC")) {
                  AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractDungeon.returnRandomRelicTier());
               }
            }

            this.enterCombat();
            AbstractDungeon.lastCombatMetricKey = this.getMonster();
            this.numRewards++;
            this.logMetric(this.numRewards);
            break;
         case ESCAPE:
            this.logMetric(this.numRewards);
            this.openMap();
            break;
         default:
            logger.info("WHY YOU CALLED?");
      }
   }

   private String getMonster() {
      switch (this.enemy) {
         case 0:
            return "3 Sentries";
         case 1:
            return "Gremlin Nob";
         default:
            return "Lagavulin Event";
      }
   }

   private void randomReward() {
      this.numRewards++;
      this.encounterChance += 25;
      String var1 = this.rewards.remove(0);
      switch (var1) {
         case "GOLD":
            this.roomEventText.updateBodyText(DESCRIPTIONS[7]);
            EffectHelper.gainGold(AbstractDungeon.player, this.x, this.y, 30);
            AbstractDungeon.player.gainGold(30);
            this.goldRewardMetric = 30;
            break;
         case "NOTHING":
            this.roomEventText.updateBodyText(DESCRIPTIONS[8]);
            break;
         case "RELIC":
            this.roomEventText.updateBodyText(DESCRIPTIONS[9]);
            AbstractRelic r = AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier());
            this.relicRewardMetric = r;
            AbstractDungeon.getCurrRoom().spawnRelicAndObtain(this.x, this.y, r);
            break;
         default:
            logger.info("HOW IS THIS POSSSIBLLEEEE");
      }

      if (this.numRewards == 3) {
         this.roomEventText.updateBodyText(DESCRIPTIONS[10]);
         this.roomEventText.updateDialogOption(0, OPTIONS[1]);
         this.roomEventText.removeDialogOption(1);
         this.screen = DeadAdventurer.CUR_SCREEN.SUCCESS;
         this.logMetric(this.numRewards);
      } else {
         logger.info("SHOULD NOT DISMISS");
         this.roomEventText.updateDialogOption(0, OPTIONS[3] + this.encounterChance + OPTIONS[4]);
         this.roomEventText.updateDialogOption(1, OPTIONS[1]);
         this.screen = DeadAdventurer.CUR_SCREEN.INTRO;
      }
   }

   public void logMetric(int numAttempts) {
      if (this.relicRewardMetric != null) {
         AbstractEvent.logMetricGainGoldAndRelic("Dead Adventurer", "Searched '" + numAttempts + "' times", this.relicRewardMetric, this.goldRewardMetric);
      } else {
         AbstractEvent.logMetricGainGold("Dead Adventurer", "Searched '" + numAttempts + "' times", this.goldRewardMetric);
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      super.render(sb);
      sb.setColor(Color.WHITE);
      sb.draw(
         this.adventurerImg, this.x - 146.0F, this.y - 86.5F, 146.0F, 86.5F, 292.0F, 173.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 292, 173, false, false
      );
   }

   @Override
   public void dispose() {
      super.dispose();
      if (this.adventurerImg != null) {
         this.adventurerImg.dispose();
         this.adventurerImg = null;
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      FIGHT_MSG = DESCRIPTIONS[0];
      ESCAPE_MSG = DESCRIPTIONS[1];
   }

   private static enum CUR_SCREEN {
      INTRO,
      FAIL,
      SUCCESS,
      ESCAPE;
   }
}
