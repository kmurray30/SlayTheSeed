package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.colorless.JAX;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.MutagenicStrength;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DrugDealer extends AbstractImageEvent {
   private static final Logger logger = LogManager.getLogger(DrugDealer.class.getName());
   public static final String ID = "Drug Dealer";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Drug Dealer");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private int screenNum = 0;
   private boolean cardsSelected = false;

   public DrugDealer() {
      super(NAME, DESCRIPTIONS[0], "images/events/drugDealer.jpg");
      this.imageEventText.setDialogOption(OPTIONS[0], CardLibrary.getCopy("J.A.X."));
      if (AbstractDungeon.player.masterDeck.getPurgeableCards().size() >= 2) {
         this.imageEventText.setDialogOption(OPTIONS[1]);
      } else {
         this.imageEventText.setDialogOption(OPTIONS[4], true);
      }

      this.imageEventText.setDialogOption(OPTIONS[2], new MutagenicStrength());
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screenNum) {
         case 0:
            switch (buttonPressed) {
               case 0:
                  AbstractCard jax = new JAX();
                  logMetricObtainCard("Drug Dealer", "Obtain J.A.X.", jax);
                  this.imageEventText.updateBodyText(DESCRIPTIONS[1]);
                  AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(jax, Settings.WIDTH / 2, Settings.HEIGHT / 2));
                  this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                  this.imageEventText.clearRemainingOptions();
                  break;
               case 1:
                  this.imageEventText.updateBodyText(DESCRIPTIONS[2]);
                  this.transform();
                  this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                  this.imageEventText.clearRemainingOptions();
                  break;
               case 2:
                  this.imageEventText.updateBodyText(DESCRIPTIONS[3]);
                  AbstractRelic r;
                  if (!AbstractDungeon.player.hasRelic("MutagenicStrength")) {
                     r = new MutagenicStrength();
                     AbstractDungeon.getCurrRoom().spawnRelicAndObtain(this.drawX, this.drawY, r);
                  } else {
                     r = new Circlet();
                     AbstractDungeon.getCurrRoom().spawnRelicAndObtain(this.drawX, this.drawY, r);
                  }

                  logMetricObtainRelic("Drug Dealer", "Inject Mutagens", r);
                  this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                  this.imageEventText.clearRemainingOptions();
                  break;
               default:
                  logger.info("ERROR: Unhandled case " + buttonPressed);
            }

            this.screenNum = 1;
            break;
         case 1:
            this.openMap();
      }
   }

   @Override
   public void update() {
      super.update();
      if (!this.cardsSelected) {
         List<String> transformedCards = new ArrayList<>();
         List<String> obtainedCards = new ArrayList<>();
         if (AbstractDungeon.gridSelectScreen.selectedCards.size() == 2) {
            this.cardsSelected = true;
            float displayCount = 0.0F;

            for (AbstractCard card : AbstractDungeon.gridSelectScreen.selectedCards) {
               card.untip();
               card.unhover();
               transformedCards.add(card.cardID);
               AbstractDungeon.player.masterDeck.removeCard(card);
               AbstractDungeon.transformCard(card, false, AbstractDungeon.miscRng);
               AbstractCard c = AbstractDungeon.getTransformedCard();
               obtainedCards.add(c.cardID);
               if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.TRANSFORM && c != null) {
                  AbstractDungeon.topLevelEffectsQueue
                     .add(new ShowCardAndObtainEffect(c.makeCopy(), Settings.WIDTH / 3.0F + displayCount, Settings.HEIGHT / 2.0F, false));
                  displayCount += Settings.WIDTH / 6.0F;
               }
            }

            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            logMetricTransformCards("Drug Dealer", "Became Test Subject", transformedCards, obtainedCards);
            AbstractDungeon.getCurrRoom().rewardPopOutTimer = 0.25F;
         }
      }
   }

   private void transform() {
      if (!AbstractDungeon.isScreenUp) {
         AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck.getPurgeableCards(), 2, OPTIONS[5], false, false, false, false);
      } else {
         AbstractDungeon.dynamicBanner.hide();
         AbstractDungeon.previousScreen = AbstractDungeon.screen;
         AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck.getPurgeableCards(), 2, OPTIONS[5], false, false, false, false);
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
   }
}
