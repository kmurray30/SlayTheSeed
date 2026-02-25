package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.rewards.RewardItem;

public class Cauldron extends AbstractRelic {
   public static final String ID = "Cauldron";

   public Cauldron() {
      super("Cauldron", "cauldron.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
      for (int i = 0; i < 5; i++) {
         AbstractDungeon.getCurrRoom().addPotionToRewards(PotionHelper.getRandomPotion());
      }

      AbstractDungeon.combatRewardScreen.open(this.DESCRIPTIONS[1]);
      AbstractDungeon.getCurrRoom().rewardPopOutTimer = 0.0F;
      int remove = -1;

      for (int i = 0; i < AbstractDungeon.combatRewardScreen.rewards.size(); i++) {
         if (AbstractDungeon.combatRewardScreen.rewards.get(i).type == RewardItem.RewardType.CARD) {
            remove = i;
            break;
         }
      }

      if (remove != -1) {
         AbstractDungeon.combatRewardScreen.rewards.remove(remove);
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Cauldron();
   }
}
