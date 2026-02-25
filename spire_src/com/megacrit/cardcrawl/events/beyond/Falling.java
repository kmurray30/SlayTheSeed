package com.megacrit.cardcrawl.events.beyond;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.CardHelper;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.cardManip.PurgeCardEffect;

public class Falling extends AbstractImageEvent {
   public static final String ID = "Falling";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Falling");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String DIALOG_2;
   private Falling.CurScreen screen = Falling.CurScreen.INTRO;
   private boolean attack;
   private boolean skill;
   private boolean power;
   private AbstractCard attackCard;
   private AbstractCard skillCard;
   private AbstractCard powerCard;

   public Falling() {
      super(NAME, DIALOG_1, "images/events/falling.jpg");
      this.setCards();
      this.imageEventText.setDialogOption(OPTIONS[0]);
   }

   @Override
   public void onEnterRoom() {
      if (Settings.AMBIANCE_ON) {
         CardCrawlGame.sound.play("EVENT_FALLING");
      }
   }

   private void setCards() {
      this.attack = CardHelper.hasCardWithType(AbstractCard.CardType.ATTACK);
      this.skill = CardHelper.hasCardWithType(AbstractCard.CardType.SKILL);
      this.power = CardHelper.hasCardWithType(AbstractCard.CardType.POWER);
      if (this.attack) {
         this.attackCard = CardHelper.returnCardOfType(AbstractCard.CardType.ATTACK, AbstractDungeon.miscRng);
      }

      if (this.skill) {
         this.skillCard = CardHelper.returnCardOfType(AbstractCard.CardType.SKILL, AbstractDungeon.miscRng);
      }

      if (this.power) {
         this.powerCard = CardHelper.returnCardOfType(AbstractCard.CardType.POWER, AbstractDungeon.miscRng);
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screen) {
         case INTRO:
            this.screen = Falling.CurScreen.CHOICE;
            this.imageEventText.updateBodyText(DIALOG_2);
            this.imageEventText.clearAllDialogs();
            if (!this.skill && !this.power && !this.attack) {
               this.imageEventText.setDialogOption(OPTIONS[8]);
            } else {
               if (this.skill) {
                  this.imageEventText.setDialogOption(OPTIONS[1] + FontHelper.colorString(this.skillCard.name, "r"), this.skillCard.makeStatEquivalentCopy());
               } else {
                  this.imageEventText.setDialogOption(OPTIONS[2], true);
               }

               if (this.power) {
                  this.imageEventText.setDialogOption(OPTIONS[3] + FontHelper.colorString(this.powerCard.name, "r"), this.powerCard.makeStatEquivalentCopy());
               } else {
                  this.imageEventText.setDialogOption(OPTIONS[4], true);
               }

               if (this.attack) {
                  this.imageEventText.setDialogOption(OPTIONS[5] + FontHelper.colorString(this.attackCard.name, "r"), this.attackCard.makeStatEquivalentCopy());
               } else {
                  this.imageEventText.setDialogOption(OPTIONS[6], true);
               }
            }
            break;
         case CHOICE:
            this.screen = Falling.CurScreen.RESULT;
            this.imageEventText.clearAllDialogs();
            this.imageEventText.setDialogOption(OPTIONS[7]);
            switch (buttonPressed) {
               case 0:
                  if (!this.skill && !this.power && !this.attack) {
                     this.imageEventText.updateBodyText(DESCRIPTIONS[5]);
                     logMetricIgnored("Falling");
                  } else {
                     this.imageEventText.updateBodyText(DESCRIPTIONS[2]);
                     AbstractDungeon.effectList.add(new PurgeCardEffect(this.skillCard));
                     AbstractDungeon.player.masterDeck.removeCard(this.skillCard);
                     logMetricCardRemoval("Falling", "Removed Skill", this.skillCard);
                  }

                  return;
               case 1:
                  this.imageEventText.updateBodyText(DESCRIPTIONS[3]);
                  AbstractDungeon.effectList.add(new PurgeCardEffect(this.powerCard));
                  AbstractDungeon.player.masterDeck.removeCard(this.powerCard);
                  logMetricCardRemoval("Falling", "Removed Power", this.powerCard);
                  return;
               case 2:
                  this.imageEventText.updateBodyText(DESCRIPTIONS[4]);
                  AbstractDungeon.effectList.add(new PurgeCardEffect(this.attackCard));
                  logMetricCardRemoval("Falling", "Removed Attack", this.attackCard);
                  AbstractDungeon.player.masterDeck.removeCard(this.attackCard);
                  return;
               default:
                  return;
            }
         default:
            this.openMap();
      }
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      DIALOG_1 = DESCRIPTIONS[0];
      DIALOG_2 = DESCRIPTIONS[1];
   }

   private static enum CurScreen {
      INTRO,
      CHOICE,
      RESULT;
   }
}
