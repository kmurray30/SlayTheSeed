package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.RedMask;
import com.megacrit.cardcrawl.vfx.GainPennyEffect;

public class MaskedBandits extends AbstractEvent {
   public static final String ID = "Masked Bandits";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Masked Bandits");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String PAID_MSG_1;
   private static final String PAID_MSG_2;
   private static final String PAID_MSG_3;
   private static final String PAID_MSG_4;
   private MaskedBandits.CurScreen screen = MaskedBandits.CurScreen.INTRO;

   public MaskedBandits() {
      this.body = DESCRIPTIONS[4];
      this.roomEventText.addDialogOption(OPTIONS[0]);
      this.roomEventText.addDialogOption(OPTIONS[1]);
      this.hasDialog = true;
      this.hasFocus = true;
      AbstractDungeon.getCurrRoom().monsters = MonsterHelper.getEncounter("Masked Bandits");
   }

   @Override
   public void update() {
      super.update();
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
                  this.stealGold();
                  AbstractDungeon.player.loseGold(AbstractDungeon.player.gold);
                  this.roomEventText.updateBodyText(PAID_MSG_1);
                  this.roomEventText.updateDialogOption(0, OPTIONS[2]);
                  this.roomEventText.clearRemainingOptions();
                  this.screen = MaskedBandits.CurScreen.PAID_1;
                  return;
               case 1:
                  logMetric("Masked Bandits", "Fought Bandits");
                  if (Settings.isDailyRun) {
                     AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(30));
                  } else {
                     AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(25, 35));
                  }

                  if (AbstractDungeon.player.hasRelic("Red Mask")) {
                     AbstractDungeon.getCurrRoom().addRelicToRewards(new Circlet());
                  } else {
                     AbstractDungeon.getCurrRoom().addRelicToRewards(new RedMask());
                  }

                  this.enterCombat();
                  AbstractDungeon.lastCombatMetricKey = "Masked Bandits";
                  return;
               default:
                  return;
            }
         case PAID_1:
            this.roomEventText.updateBodyText(PAID_MSG_2);
            this.screen = MaskedBandits.CurScreen.PAID_2;
            this.roomEventText.updateDialogOption(0, OPTIONS[2]);
            break;
         case PAID_2:
            this.roomEventText.updateBodyText(PAID_MSG_3);
            this.screen = MaskedBandits.CurScreen.PAID_3;
            this.roomEventText.updateDialogOption(0, OPTIONS[3]);
            break;
         case PAID_3:
            this.roomEventText.updateBodyText(PAID_MSG_4);
            this.roomEventText.updateDialogOption(0, OPTIONS[3]);
            this.screen = MaskedBandits.CurScreen.END;
            this.openMap();
            break;
         case END:
            this.openMap();
      }
   }

   private void stealGold() {
      AbstractCreature target = AbstractDungeon.player;
      if (target.gold != 0) {
         logMetricLoseGold("Masked Bandits", "Paid Fearfully", target.gold);
         CardCrawlGame.sound.play("GOLD_JINGLE");

         for (int i = 0; i < target.gold; i++) {
            AbstractCreature source = AbstractDungeon.getCurrRoom().monsters.getRandomMonster();
            AbstractDungeon.effectList.add(new GainPennyEffect(source, target.hb.cX, target.hb.cY, source.hb.cX, source.hb.cY, false));
         }
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      PAID_MSG_1 = DESCRIPTIONS[0];
      PAID_MSG_2 = DESCRIPTIONS[1];
      PAID_MSG_3 = DESCRIPTIONS[2];
      PAID_MSG_4 = DESCRIPTIONS[3];
   }

   private static enum CurScreen {
      INTRO,
      PAID_1,
      PAID_2,
      PAID_3,
      END;
   }
}
