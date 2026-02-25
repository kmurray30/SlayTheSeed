package com.megacrit.cardcrawl.events.city;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import java.util.ArrayList;

public class TheLibrary extends AbstractImageEvent {
   public static final String ID = "The Library";
   private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("The Library");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final String[] OPTIONS;
   private static final String DIALOG_1;
   private static final String SLEEP_RESULT;
   private int screenNum = 0;
   private boolean pickCard = false;
   private static final float HP_HEAL_PERCENT = 0.33F;
   private static final float A_2_HP_HEAL_PERCENT = 0.2F;
   private int healAmt;

   public TheLibrary() {
      super(NAME, DIALOG_1, "images/events/library.jpg");
      if (AbstractDungeon.ascensionLevel >= 15) {
         this.healAmt = MathUtils.round(AbstractDungeon.player.maxHealth * 0.2F);
      } else {
         this.healAmt = MathUtils.round(AbstractDungeon.player.maxHealth * 0.33F);
      }

      this.imageEventText.setDialogOption(OPTIONS[0]);
      this.imageEventText.setDialogOption(OPTIONS[1] + this.healAmt + OPTIONS[2]);
   }

   @Override
   public void update() {
      super.update();
      if (this.pickCard && !AbstractDungeon.isScreenUp && !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
         AbstractCard c = AbstractDungeon.gridSelectScreen.selectedCards.get(0).makeCopy();
         logMetricObtainCard("The Library", "Read", c);
         AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(c, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
         AbstractDungeon.gridSelectScreen.selectedCards.clear();
      }
   }

   @Override
   protected void buttonEffect(int buttonPressed) {
      switch (this.screenNum) {
         case 0:
            switch (buttonPressed) {
               case 0:
                  this.imageEventText.updateBodyText(this.getBook());
                  this.screenNum = 1;
                  this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                  this.imageEventText.clearRemainingOptions();
                  this.pickCard = true;
                  CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

                  for (int i = 0; i < 20; i++) {
                     AbstractCard card = AbstractDungeon.getCard(AbstractDungeon.rollRarity()).makeCopy();
                     boolean containsDupe = true;

                     while (containsDupe) {
                        containsDupe = false;

                        for (AbstractCard c : group.group) {
                           if (c.cardID.equals(card.cardID)) {
                              containsDupe = true;
                              card = AbstractDungeon.getCard(AbstractDungeon.rollRarity()).makeCopy();
                              break;
                           }
                        }
                     }

                     if (group.contains(card)) {
                        i--;
                     } else {
                        for (AbstractRelic r : AbstractDungeon.player.relics) {
                           r.onPreviewObtainCard(card);
                        }

                        group.addToBottom(card);
                     }
                  }

                  for (AbstractCard cx : group.group) {
                     UnlockTracker.markCardAsSeen(cx.cardID);
                  }

                  AbstractDungeon.gridSelectScreen.open(group, 1, OPTIONS[4], false);
                  return;
               default:
                  this.imageEventText.updateBodyText(SLEEP_RESULT);
                  AbstractDungeon.player.heal(this.healAmt, true);
                  logMetricHeal("The Library", "Heal", this.healAmt);
                  this.screenNum = 1;
                  this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                  this.imageEventText.clearRemainingOptions();
                  return;
            }
         default:
            this.openMap();
      }
   }

   private String getBook() {
      ArrayList<String> list = new ArrayList<>();
      list.add(DESCRIPTIONS[2]);
      list.add(DESCRIPTIONS[3]);
      list.add(DESCRIPTIONS[4]);
      return list.get(MathUtils.random(2));
   }

   static {
      NAME = eventStrings.NAME;
      DESCRIPTIONS = eventStrings.DESCRIPTIONS;
      OPTIONS = eventStrings.OPTIONS;
      DIALOG_1 = DESCRIPTIONS[0];
      SLEEP_RESULT = DESCRIPTIONS[1];
   }
}
