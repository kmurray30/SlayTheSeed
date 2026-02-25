package com.megacrit.cardcrawl.events.exordium;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.PurgeCardEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class LivingWall extends AbstractImageEvent {
   public static final String ID = "Living Wall";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Living Wall");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String RESULT_DIALOG;
   private LivingWall.CurScreen screen = LivingWall.CurScreen.INTRO;
   private boolean pickCard = false;
   private LivingWall.Choice choice = null;

   public LivingWall() {
      super(NAME, DIALOG_1, "images/events/livingWall.jpg");
      this.imageEventText.setDialogOption(OPTIONS[0]);
      this.imageEventText.setDialogOption(OPTIONS[1]);
      if (AbstractDungeon.player.masterDeck.hasUpgradableCards()) {
         this.imageEventText.setDialogOption(OPTIONS[2]);
      } else {
         this.imageEventText.setDialogOption(OPTIONS[7], true);
      }
   }

   @Override
   public void onEnterRoom() {
      if (Settings.AMBIANCE_ON) {
         CardCrawlGame.sound.play("EVENT_LIVING_WALL");
      }
   }

   @Override
   public void update() {
      super.update();
      if (this.pickCard && !AbstractDungeon.isScreenUp && !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
         switch (this.choice) {
            case FORGET:
               CardCrawlGame.sound.play("CARD_EXHAUST");
               AbstractDungeon.topLevelEffects
                  .add(new PurgeCardEffect(AbstractDungeon.gridSelectScreen.selectedCards.get(0), Settings.WIDTH / 2, Settings.HEIGHT / 2));
               AbstractEvent.logMetricCardRemoval("Living Wall", "Forget", AbstractDungeon.gridSelectScreen.selectedCards.get(0));
               AbstractDungeon.player.masterDeck.removeCard(AbstractDungeon.gridSelectScreen.selectedCards.get(0));
               break;
            case CHANGE:
               AbstractCard c = AbstractDungeon.gridSelectScreen.selectedCards.get(0);
               AbstractDungeon.player.masterDeck.removeCard(c);
               AbstractDungeon.transformCard(c, false, AbstractDungeon.miscRng);
               AbstractCard transCard = AbstractDungeon.getTransformedCard();
               AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(transCard, c.current_x, c.current_y));
               AbstractEvent.logMetricTransformCard("Living Wall", "Change", c, transCard);
               break;
            case GROW:
               AbstractDungeon.effectsQueue.add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
               AbstractDungeon.gridSelectScreen.selectedCards.get(0).upgrade();
               AbstractCard upgCard = AbstractDungeon.gridSelectScreen.selectedCards.get(0);
               AbstractEvent.logMetricCardUpgrade("Living Wall", "Grow", upgCard);
               AbstractDungeon.player.bottledCardUpgradeCheck(upgCard);
         }

         AbstractDungeon.gridSelectScreen.selectedCards.clear();
         this.pickCard = false;
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            switch (buttonPressed) {
               case 0:
                  this.choice = LivingWall.Choice.FORGET;
                  if (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()).size() > 0) {
                     AbstractDungeon.gridSelectScreen
                        .open(
                           CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()),
                           1,
                           OPTIONS[3],
                           false,
                           false,
                           false,
                           true
                        );
                  }
                  break;
               case 1:
                  this.choice = LivingWall.Choice.CHANGE;
                  if (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()).size() > 0) {
                     AbstractDungeon.gridSelectScreen
                        .open(
                           CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()),
                           1,
                           OPTIONS[4],
                           false,
                           true,
                           false,
                           false
                        );
                  }
                  break;
               default:
                  this.choice = LivingWall.Choice.GROW;
                  if (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()).size() > 0) {
                     AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck.getUpgradableCards(), 1, OPTIONS[5], true, false, false, false);
                  }
            }

            this.pickCard = true;
            this.imageEventText.updateBodyText(RESULT_DIALOG);
            this.imageEventText.clearAllDialogs();
            this.imageEventText.setDialogOption(OPTIONS[6]);
            this.screen = LivingWall.CurScreen.RESULT;
            break;
         default:
            this.openMap();
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      DIALOG_1 = DESCRIPTIONS[0];
      RESULT_DIALOG = DESCRIPTIONS[1];
   }

   private static enum Choice {
      FORGET,
      CHANGE,
      GROW;
   }

   private static enum CurScreen {
      INTRO,
      RESULT;
   }
}
