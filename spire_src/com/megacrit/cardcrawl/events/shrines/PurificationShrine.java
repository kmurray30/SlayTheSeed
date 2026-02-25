package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.cardManip.PurgeCardEffect;

public class PurificationShrine extends AbstractImageEvent {
   public static final String ID = "Purifier";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Purifier");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String DIALOG_2;
   private static final String IGNORE;
   private PurificationShrine.CUR_SCREEN screen = PurificationShrine.CUR_SCREEN.INTRO;

   public PurificationShrine() {
      super(NAME, DIALOG_1, "images/events/shrine3.jpg");
      this.imageEventText.setDialogOption(OPTIONS[0]);
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
         CardCrawlGame.sound.play("CARD_EXHAUST");
         logMetricCardRemoval("Purifier", "Purged", AbstractDungeon.gridSelectScreen.selectedCards.get(0));
         AbstractDungeon.topLevelEffects
            .add(new PurgeCardEffect(AbstractDungeon.gridSelectScreen.selectedCards.get(0), Settings.WIDTH / 2, Settings.HEIGHT / 2));
         AbstractDungeon.player.masterDeck.removeCard(AbstractDungeon.gridSelectScreen.selectedCards.get(0));
         AbstractDungeon.gridSelectScreen.selectedCards.clear();
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            switch (buttonPressed) {
               case 0:
                  this.screen = PurificationShrine.CUR_SCREEN.COMPLETE;
                  this.imageEventText.updateBodyText(DIALOG_2);
                  AbstractDungeon.gridSelectScreen
                     .open(
                        CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()), 1, OPTIONS[2], false, false, false, true
                     );
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.imageEventText.clearRemainingOptions();
                  return;
               case 1:
                  this.screen = PurificationShrine.CUR_SCREEN.COMPLETE;
                  logMetricIgnored("Purifier");
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
