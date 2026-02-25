package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class Colosseum extends AbstractImageEvent {
   public static final String ID = "Colosseum";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Colosseum");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private Colosseum.CurScreen screen = Colosseum.CurScreen.INTRO;

   public Colosseum() {
      super(NAME, DESCRIPTIONS[0], "images/events/colosseum.jpg");
      this.imageEventText.setDialogOption(OPTIONS[0]);
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            switch (buttonPressed) {
               case 0:
                  this.imageEventText.updateBodyText(DESCRIPTIONS[1] + DESCRIPTIONS[2] + 4200 + DESCRIPTIONS[3]);
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.screen = Colosseum.CurScreen.FIGHT;
                  return;
               default:
                  return;
            }
         case FIGHT:
            switch (buttonPressed) {
               case 0:
                  this.screen = Colosseum.CurScreen.POST_COMBAT;
                  this.logMetric("Fight");
                  AbstractDungeon.getCurrRoom().monsters = MonsterHelper.getEncounter("Colosseum Slavers");
                  AbstractDungeon.getCurrRoom().rewards.clear();
                  AbstractDungeon.getCurrRoom().rewardAllowed = false;
                  this.enterCombatFromImage();
                  AbstractDungeon.lastCombatMetricKey = "Colosseum Slavers";
               default:
                  this.imageEventText.clearRemainingOptions();
                  return;
            }
         case POST_COMBAT:
            AbstractDungeon.getCurrRoom().rewardAllowed = true;
            switch (buttonPressed) {
               case 1:
                  this.screen = Colosseum.CurScreen.LEAVE;
                  this.logMetric("Fought Nobs");
                  AbstractDungeon.getCurrRoom().monsters = MonsterHelper.getEncounter("Colosseum Nobs");
                  AbstractDungeon.getCurrRoom().rewards.clear();
                  AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.RARE);
                  AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.UNCOMMON);
                  AbstractDungeon.getCurrRoom().addGoldToRewards(100);
                  AbstractDungeon.getCurrRoom().eliteTrigger = true;
                  this.enterCombatFromImage();
                  AbstractDungeon.lastCombatMetricKey = "Colosseum Nobs";
                  return;
               default:
                  this.logMetric("Fled From Nobs");
                  this.openMap();
                  return;
            }
         case LEAVE:
            this.openMap();
            break;
         default:
            this.openMap();
      }
   }

   public void logMetric(String actionTaken) {
      AbstractEvent.logMetric("Colosseum", actionTaken);
   }

   @Override
   public void reopen() {
      if (this.screen != Colosseum.CurScreen.LEAVE) {
         AbstractDungeon.resetPlayer();
         AbstractDungeon.player.drawX = Settings.WIDTH * 0.25F;
         AbstractDungeon.player.preBattlePrep();
         this.enterImageFromCombat();
         this.imageEventText.updateBodyText(DESCRIPTIONS[4]);
         this.imageEventText.updateDialogOption(0, OPTIONS[2]);
         this.imageEventText.setDialogOption(OPTIONS[3]);
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
   }

   private static enum CurScreen {
      INTRO,
      FIGHT,
      LEAVE,
      POST_COMBAT;
   }
}
