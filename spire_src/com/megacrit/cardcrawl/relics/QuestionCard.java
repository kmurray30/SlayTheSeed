package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class QuestionCard extends AbstractRelic {
   private static final int CARDS_ADDED = 1;
   public static final String ID = "Question Card";

   public QuestionCard() {
      super("Question Card", "questionCard.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 48;
   }

   @Override
   public int changeNumberOfCardsInReward(int numberOfCards) {
      return numberOfCards + 1;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new QuestionCard();
   }
}
