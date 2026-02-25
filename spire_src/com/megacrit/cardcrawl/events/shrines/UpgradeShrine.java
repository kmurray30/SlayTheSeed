package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;

public class UpgradeShrine extends AbstractImageEvent {
   public static final String ID = "Upgrade Shrine";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Upgrade Shrine");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String DIALOG_2;
   private static final String IGNORE;
   private UpgradeShrine.CUR_SCREEN screen = UpgradeShrine.CUR_SCREEN.INTRO;

   public UpgradeShrine() {
      super(NAME, DIALOG_1, "images/events/shrine2.jpg");
      if (AbstractDungeon.player.masterDeck.hasUpgradableCards()) {
         this.imageEventText.setDialogOption(OPTIONS[0]);
      } else {
         this.imageEventText.setDialogOption(OPTIONS[3], true);
      }

      this.imageEventText.setDialogOption(OPTIONS[1]);
   }

   @Override
   public void onEnterRoom() {
      CardCrawlGame.music.playTempBGM("SHRINE");
   }

   @Override
   public void update() {
      super.update();
      if (!AbstractDungeon.isScreenUp && !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
         AbstractCard c = AbstractDungeon.gridSelectScreen.selectedCards.get(0);
         c.upgrade();
         logMetricCardUpgrade("Upgrade Shrine", "Upgraded", c);
         AbstractDungeon.player.bottledCardUpgradeCheck(c);
         AbstractDungeon.effectsQueue.add(new ShowCardBrieflyEffect(c.makeStatEquivalentCopy()));
         AbstractDungeon.topLevelEffects.add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
         AbstractDungeon.gridSelectScreen.selectedCards.clear();
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            switch (buttonPressed) {
               case 0:
                  this.screen = UpgradeShrine.CUR_SCREEN.COMPLETE;
                  AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
                  this.imageEventText.updateBodyText(DIALOG_2);
                  AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck.getUpgradableCards(), 1, OPTIONS[2], true, false, false, false);
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.imageEventText.clearRemainingOptions();
                  return;
               case 1:
                  this.screen = UpgradeShrine.CUR_SCREEN.COMPLETE;
                  AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
                  logMetricIgnored("Upgrade Shrine");
                  this.imageEventText.updateBodyText(IGNORE);
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.imageEventText.clearRemainingOptions();
                  return;
               default:
                  return;
            }
         case COMPLETE:
            this.openMap();
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      DIALOG_1 = DESCRIPTIONS[0];
      DIALOG_2 = DESCRIPTIONS[1];
      IGNORE = DESCRIPTIONS[2];
   }

   private static enum CUR_SCREEN {
      INTRO,
      COMPLETE;
   }
}
