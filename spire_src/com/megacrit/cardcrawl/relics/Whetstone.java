package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class Whetstone extends AbstractRelic {
   public static final String ID = "Whetstone";
   private static final int CARD_AMT = 2;

   public Whetstone() {
      super("Whetstone", "whetstone.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.SOLID);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 2 + this.DESCRIPTIONS[1];
   }

   @Override
   public void onEquip() {
      ArrayList<AbstractCard> upgradableCards = new ArrayList<>();

      for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
         if (c.canUpgrade() && c.type == AbstractCard.CardType.ATTACK) {
            upgradableCards.add(c);
         }
      }

      Collections.shuffle(upgradableCards, new Random(AbstractDungeon.miscRng.randomLong()));
      if (!upgradableCards.isEmpty()) {
         if (upgradableCards.size() == 1) {
            upgradableCards.get(0).upgrade();
            AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
            AbstractDungeon.topLevelEffects.add(new ShowCardBrieflyEffect(upgradableCards.get(0).makeStatEquivalentCopy()));
            AbstractDungeon.topLevelEffects.add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
         } else {
            upgradableCards.get(0).upgrade();
            upgradableCards.get(1).upgrade();
            AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
            AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(1));
            AbstractDungeon.topLevelEffects
               .add(
                  new ShowCardBrieflyEffect(
                     upgradableCards.get(0).makeStatEquivalentCopy(),
                     Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH / 2.0F - 20.0F * Settings.scale,
                     Settings.HEIGHT / 2.0F
                  )
               );
            AbstractDungeon.topLevelEffects
               .add(
                  new ShowCardBrieflyEffect(
                     upgradableCards.get(1).makeStatEquivalentCopy(),
                     Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH / 2.0F + 20.0F * Settings.scale,
                     Settings.HEIGHT / 2.0F
                  )
               );
            AbstractDungeon.topLevelEffects.add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
         }
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Whetstone();
   }
}
