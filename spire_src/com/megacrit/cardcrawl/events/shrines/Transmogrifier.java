package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class Transmogrifier extends AbstractImageEvent {
   public static final String ID = "Transmorgrifier";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Transmorgrifier");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String DIALOG_2;
   private static final String IGNORE;
   private Transmogrifier.CUR_SCREEN screen = Transmogrifier.CUR_SCREEN.INTRO;

   public Transmogrifier() {
      super(NAME, DIALOG_1, "images/events/shrine1.jpg");
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
         AbstractCard c = AbstractDungeon.gridSelectScreen.selectedCards.get(0);
         AbstractDungeon.player.masterDeck.removeCard(c);
         AbstractDungeon.transformCard(c, false, AbstractDungeon.miscRng);
         AbstractCard transCard = AbstractDungeon.getTransformedCard();
         logMetricTransformCard("Transmorgrifier", "Transformed", c, transCard);
         AbstractDungeon.effectsQueue.add(new ShowCardAndObtainEffect(transCard, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
         AbstractDungeon.gridSelectScreen.selectedCards.clear();
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            switch (buttonPressed) {
               case 0:
                  this.screen = Transmogrifier.CUR_SCREEN.COMPLETE;
                  this.imageEventText.updateBodyText(DIALOG_2);
                  AbstractDungeon.gridSelectScreen
                     .open(
                        CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()), 1, OPTIONS[2], false, true, false, false
                     );
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.imageEventText.clearRemainingOptions();
                  return;
               case 1:
                  this.screen = Transmogrifier.CUR_SCREEN.COMPLETE;
                  logMetricIgnored("Transmorgrifier");
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
