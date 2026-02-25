package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import java.util.ArrayList;
import java.util.Iterator;

public class PandorasBox extends AbstractRelic {
   public static final String ID = "Pandora's Box";
   private int count = 0;
   private boolean calledTransform = true;

   public PandorasBox() {
      super("Pandora's Box", "pandoras_box.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.MAGICAL);
      this.removeStrikeTip();
   }

   private void removeStrikeTip() {
      ArrayList<String> strikes = new ArrayList<>();

      for (String s : GameDictionary.STRIKE.NAMES) {
         strikes.add(s.toLowerCase());
      }

      Iterator<PowerTip> t = this.tips.iterator();

      while (t.hasNext()) {
         PowerTip derp = t.next();
         String s = derp.header.toLowerCase();
         if (strikes.contains(s)) {
            t.remove();
            break;
         }
      }
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
      this.calledTransform = false;
      Iterator<AbstractCard> i = AbstractDungeon.player.masterDeck.group.iterator();

      while (i.hasNext()) {
         AbstractCard e = i.next();
         if (e.hasTag(AbstractCard.CardTags.STARTER_DEFEND) || e.hasTag(AbstractCard.CardTags.STARTER_STRIKE)) {
            i.remove();
            this.count++;
         }
      }

      if (this.count > 0) {
         CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

         for (int ix = 0; ix < this.count; ix++) {
            AbstractCard card = AbstractDungeon.returnTrulyRandomCard().makeCopy();
            UnlockTracker.markCardAsSeen(card.cardID);
            card.isSeen = true;

            for (AbstractRelic r : AbstractDungeon.player.relics) {
               r.onPreviewObtainCard(card);
            }

            group.addToBottom(card);
         }

         AbstractDungeon.gridSelectScreen.openConfirmationGrid(group, this.DESCRIPTIONS[1]);
      }
   }

   @Override
   public void update() {
      super.update();
      if (!this.calledTransform && AbstractDungeon.screen != AbstractDungeon.CurrentScreen.GRID) {
         this.calledTransform = true;
         AbstractDungeon.getCurrRoom().rewardPopOutTimer = 0.25F;
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new PandorasBox();
   }
}
