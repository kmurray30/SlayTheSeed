package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class Lab extends AbstractImageEvent {
   public static final String ID = "Lab";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Lab");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private Lab.CUR_SCREEN screen = Lab.CUR_SCREEN.INTRO;

   public Lab() {
      super(NAME, DIALOG_1, "images/events/lab.jpg");
      this.noCardsInRewards = true;
      this.imageEventText.setDialogOption(OPTIONS[0]);
   }

   @Override
   public void onEnterRoom() {
      if (Settings.AMBIANCE_ON) {
         CardCrawlGame.sound.play("EVENT_LAB");
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            GenericEventDialog.hide();
            AbstractDungeon.getCurrRoom().rewards.clear();
            AbstractDungeon.getCurrRoom().rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
            AbstractDungeon.getCurrRoom().rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
            if (AbstractDungeon.ascensionLevel < 15) {
               AbstractDungeon.getCurrRoom().rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
            }

            this.screen = Lab.CUR_SCREEN.COMPLETE;
            AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
            AbstractDungeon.combatRewardScreen.open();
            logMetric("Lab", "Got Potions");
            break;
         case COMPLETE:
            this.openMap();
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      DIALOG_1 = DESCRIPTIONS[0];
   }

   private static enum CUR_SCREEN {
      INTRO,
      COMPLETE;
   }
}
