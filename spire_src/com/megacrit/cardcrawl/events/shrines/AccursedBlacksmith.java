package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.curses.Pain;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.WarpedTongs;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;

public class AccursedBlacksmith extends AbstractImageEvent {
   public static final String ID = "Accursed Blacksmith";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Accursed Blacksmith");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String FORGE_RESULT;
   private static final String RUMMAGE_RESULT;
   private static final String CURSE_RESULT2;
   private static final String LEAVE_RESULT;
   private int screenNum = 0;
   private boolean pickCard = false;

   public AccursedBlacksmith() {
      super(NAME, DIALOG_1, "images/events/blacksmith.jpg");
      if (AbstractDungeon.player.masterDeck.hasUpgradableCards()) {
         this.imageEventText.setDialogOption(OPTIONS[0]);
      } else {
         this.imageEventText.setDialogOption(OPTIONS[4], true);
      }

      this.imageEventText.setDialogOption(OPTIONS[1], CardLibrary.getCopy("Pain"), new WarpedTongs());
      this.imageEventText.setDialogOption(OPTIONS[2]);
   }

   @Override
   public void onEnterRoom() {
      if (Settings.AMBIANCE_ON) {
         CardCrawlGame.sound.play("EVENT_FORGE");
      }
   }

   @Override
   public void update() {
      super.update();
      if (this.pickCard && !AbstractDungeon.isScreenUp && !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
         AbstractCard c = AbstractDungeon.gridSelectScreen.selectedCards.get(0);
         c.upgrade();
         logMetricCardUpgrade("Accursed Blacksmith", "Forge", c);
         AbstractDungeon.player.bottledCardUpgradeCheck(AbstractDungeon.gridSelectScreen.selectedCards.get(0));
         AbstractDungeon.effectsQueue.add(new ShowCardBrieflyEffect(c.makeStatEquivalentCopy()));
         AbstractDungeon.topLevelEffects.add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
         AbstractDungeon.gridSelectScreen.selectedCards.clear();
         this.pickCard = false;
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screenNum) {
         case 0:
            switch (buttonPressed) {
               case 0:
                  this.pickCard = true;
                  AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck.getUpgradableCards(), 1, OPTIONS[3], true, false, false, false);
                  this.imageEventText.updateBodyText(FORGE_RESULT);
                  this.screenNum = 2;
                  this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                  break;
               case 1:
                  this.screenNum = 2;
                  this.imageEventText.updateBodyText(RUMMAGE_RESULT + CURSE_RESULT2);
                  AbstractCard curse = new Pain();
                  AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(curse, Settings.WIDTH / 2, Settings.HEIGHT / 2));
                  AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH / 2, Settings.HEIGHT / 2, new WarpedTongs());
                  logMetricObtainCardAndRelic("Accursed Blacksmith", "Rummage", curse, new WarpedTongs());
                  this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                  break;
               case 2:
                  this.screenNum = 2;
                  logMetricIgnored("Accursed Blacksmith");
                  this.imageEventText.updateBodyText(LEAVE_RESULT);
                  this.imageEventText.updateDialogOption(0, OPTIONS[2]);
            }

            this.imageEventText.clearRemainingOptions();
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
      FORGE_RESULT = DESCRIPTIONS[1];
      RUMMAGE_RESULT = DESCRIPTIONS[2];
      CURSE_RESULT2 = DESCRIPTIONS[4];
      LEAVE_RESULT = DESCRIPTIONS[5];
   }
}
