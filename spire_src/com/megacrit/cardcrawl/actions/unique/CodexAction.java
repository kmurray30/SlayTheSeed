package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDrawPileEffect;
import java.util.ArrayList;

public class CodexAction extends AbstractGameAction {
   public static int numPlaced;
   private boolean retrieveCard = false;

   public CodexAction() {
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.duration = Settings.ACTION_DUR_FAST;
   }

   @Override
   public void update() {
      if (AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         this.isDone = true;
      } else if (this.duration == Settings.ACTION_DUR_FAST) {
         AbstractDungeon.cardRewardScreen.customCombatOpen(this.generateCardChoices(), CardRewardScreen.TEXT[1], true);
         this.tickDuration();
      } else {
         if (!this.retrieveCard) {
            if (AbstractDungeon.cardRewardScreen.discoveryCard != null) {
               AbstractCard codexCard = AbstractDungeon.cardRewardScreen.discoveryCard.makeStatEquivalentCopy();
               codexCard.current_x = -1000.0F * Settings.xScale;
               AbstractDungeon.effectList.add(new ShowCardAndAddToDrawPileEffect(codexCard, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F, true));
               AbstractDungeon.cardRewardScreen.discoveryCard = null;
            }

            this.retrieveCard = true;
         }

         this.tickDuration();
      }
   }

   private ArrayList<AbstractCard> generateCardChoices() {
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         AbstractCard tmp = AbstractDungeon.returnTrulyRandomCardInCombat();

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      return derp;
   }
}
