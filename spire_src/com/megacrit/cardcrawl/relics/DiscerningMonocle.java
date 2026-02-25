package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.ShopRoom;

public class DiscerningMonocle extends AbstractRelic {
   public static final String ID = "Discerning Monocle";
   public static final float MULTIPLIER = 0.8F;

   public DiscerningMonocle() {
      super("Discerning Monocle", "monocle.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEnterRoom(AbstractRoom room) {
      if (room instanceof ShopRoom) {
         this.flash();
         this.pulse = true;
      } else {
         this.pulse = false;
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new DiscerningMonocle();
   }
}
