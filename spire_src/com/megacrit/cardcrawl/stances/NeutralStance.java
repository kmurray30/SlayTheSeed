package com.megacrit.cardcrawl.stances;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.StanceStrings;

public class NeutralStance extends AbstractStance {
   public static final String STANCE_ID = "Neutral";
   private static final StanceStrings stanceString = CardCrawlGame.languagePack.getStanceString("Neutral");

   public NeutralStance() {
      this.ID = "Neutral";
      this.img = null;
      this.name = null;
      this.updateDescription();
   }

   @Override
   public void updateDescription() {
      this.description = stanceString.DESCRIPTION[0];
   }

   @Override
   public void onEnterStance() {
   }

   @Override
   public void render(SpriteBatch sb) {
   }
}
