package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.cardManip.PurgeCardEffect;
import java.util.ArrayList;
import java.util.List;

public class FountainOfCurseRemoval extends AbstractImageEvent {
   public static final String ID = "Fountain of Cleansing";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Fountain of Cleansing");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String DIALOG_2;
   private static final String DIALOG_3;
   private int screenNum = 0;

   public FountainOfCurseRemoval() {
      super(NAME, DIALOG_1, "images/events/fountain.jpg");
      this.imageEventText.setDialogOption(OPTIONS[0]);
      this.imageEventText.setDialogOption(OPTIONS[1]);
   }

   @Override
   public void onEnterRoom() {
      CardCrawlGame.music.playTempBGM("SHRINE");
      if (Settings.AMBIANCE_ON) {
         CardCrawlGame.sound.play("EVENT_FOUNTAIN");
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screenNum) {
         case 0:
            switch (buttonPressed) {
               case 0:
                  this.imageEventText.updateBodyText(DIALOG_2);
                  List<String> curses = new ArrayList<>();
                  this.screenNum = 1;

                  for (int i = AbstractDungeon.player.masterDeck.group.size() - 1; i >= 0; i--) {
                     if (AbstractDungeon.player.masterDeck.group.get(i).type == AbstractCard.CardType.CURSE
                        && !AbstractDungeon.player.masterDeck.group.get(i).inBottleFlame
                        && !AbstractDungeon.player.masterDeck.group.get(i).inBottleLightning
                        && AbstractDungeon.player.masterDeck.group.get(i).cardID != "AscendersBane"
                        && AbstractDungeon.player.masterDeck.group.get(i).cardID != "CurseOfTheBell"
                        && AbstractDungeon.player.masterDeck.group.get(i).cardID != "Necronomicurse") {
                        AbstractDungeon.effectList.add(new PurgeCardEffect(AbstractDungeon.player.masterDeck.group.get(i)));
                        curses.add(AbstractDungeon.player.masterDeck.group.get(i).cardID);
                        AbstractDungeon.player.masterDeck.removeCard(AbstractDungeon.player.masterDeck.group.get(i));
                     }
                  }

                  logMetricRemoveCards("Fountain of Cleansing", "Removed Curses", curses);
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.imageEventText.clearRemainingOptions();
                  return;
               default:
                  logMetricIgnored("Fountain of Cleansing");
                  this.imageEventText.updateBodyText(DIALOG_3);
                  this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                  this.imageEventText.clearRemainingOptions();
                  this.screenNum = 1;
                  return;
            }
         case 1:
            this.openMap();
            break;
         default:
            this.openMap();
      }
   }

   public void logMetric(String cardGiven) {
      AbstractEvent.logMetric("Fountain of Cleansing", cardGiven);
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      DIALOG_1 = DESCRIPTIONS[0];
      DIALOG_2 = DESCRIPTIONS[1];
      DIALOG_3 = DESCRIPTIONS[2];
   }
}
