package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MummifiedHand extends AbstractRelic {
   private static final Logger logger = LogManager.getLogger(MummifiedHand.class.getName());
   public static final String ID = "Mummified Hand";

   public MummifiedHand() {
      super("Mummified Hand", "mummifiedHand.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.POWER) {
         this.flash();
         this.addToTop(new RelicAboveCreatureAction(AbstractDungeon.player, this));
         ArrayList<AbstractCard> groupCopy = new ArrayList<>();

         for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.cost > 0 && c.costForTurn > 0 && !c.freeToPlayOnce) {
               groupCopy.add(c);
            } else {
               logger.info("COST IS 0: " + c.name);
            }
         }

         for (CardQueueItem i : AbstractDungeon.actionManager.cardQueue) {
            if (i.card != null) {
               logger.info("INVALID: " + i.card.name);
               groupCopy.remove(i.card);
            }
         }

         AbstractCard cx = null;
         if (groupCopy.isEmpty()) {
            logger.info("NO VALID CARDS");
         } else {
            logger.info("VALID CARDS: ");

            for (AbstractCard cc : groupCopy) {
               logger.info(cc.name);
            }

            cx = groupCopy.get(AbstractDungeon.cardRandomRng.random(0, groupCopy.size() - 1));
         }

         if (cx != null) {
            logger.info("Mummified hand: " + cx.name);
            cx.setCostForTurn(0);
         } else {
            logger.info("ERROR: MUMMIFIED HAND NOT WORKING");
         }
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new MummifiedHand();
   }
}
