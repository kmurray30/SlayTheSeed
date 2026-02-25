package com.megacrit.cardcrawl.events.beyond;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class MysteriousSphere extends AbstractEvent {
   public static final String ID = "Mysterious Sphere";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Mysterious Sphere");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String INTRO_MSG;
   private MysteriousSphere.CurScreen screen = MysteriousSphere.CurScreen.INTRO;

   public MysteriousSphere() {
      this.initializeImage("images/events/sphereClosed.png", 1120.0F * Settings.xScale, AbstractDungeon.floorY - 50.0F * Settings.scale);
      this.body = INTRO_MSG;
      this.roomEventText.addDialogOption(OPTIONS[0]);
      this.roomEventText.addDialogOption(OPTIONS[1]);
      this.hasDialog = true;
      this.hasFocus = true;
      AbstractDungeon.getCurrRoom().monsters = MonsterHelper.getEncounter("2 Orb Walkers");
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
                  this.screen = MysteriousSphere.CurScreen.PRE_COMBAT;
                  this.roomEventText.updateBodyText(DESCRIPTIONS[1]);
                  this.roomEventText.updateDialogOption(0, OPTIONS[2]);
                  this.roomEventText.clearRemainingOptions();
                  logMetric("Mysterious Sphere", "Fight");
                  return;
               case 1:
                  this.screen = MysteriousSphere.CurScreen.END;
                  this.roomEventText.updateBodyText(DESCRIPTIONS[2]);
                  this.roomEventText.updateDialogOption(0, OPTIONS[1]);
                  this.roomEventText.clearRemainingOptions();
                  logMetricIgnored("Mysterious Sphere");
                  return;
               default:
                  return;
            }
         case PRE_COMBAT:
            if (Settings.isDailyRun) {
               AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(50));
            } else {
               AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(45, 55));
            }

            AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractDungeon.returnRandomScreenlessRelic(AbstractRelic.RelicTier.RARE));
            if (this.img != null) {
               this.img.dispose();
               this.img = null;
            }

            this.img = ImageMaster.loadImage("images/events/sphereOpen.png");
            this.enterCombat();
            AbstractDungeon.lastCombatMetricKey = "2 Orb Walkers";
            break;
         case END:
            this.openMap();
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      INTRO_MSG = DESCRIPTIONS[0];
   }

   private static enum CurScreen {
      INTRO,
      PRE_COMBAT,
      END;
   }
}
