package com.megacrit.cardcrawl.cards.status;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Burn extends AbstractCard {
   public static final String ID = "Burn";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Burn");

   public Burn() {
      super(
         "Burn",
         cardStrings.NAME,
         "status/burn",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.STATUS,
         AbstractCard.CardColor.COLORLESS,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.NONE
      );
      this.magicNumber = 2;
      this.baseMagicNumber = 2;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (this.dontTriggerOnUseCard) {
         this.addToBot(
            new DamageAction(
               AbstractDungeon.player,
               new DamageInfo(AbstractDungeon.player, this.magicNumber, DamageInfo.DamageType.THORNS),
               AbstractGameAction.AttackEffect.FIRE
            )
         );
      }
   }

   @Override
   public void triggerOnEndOfTurnForPlayingCard() {
      this.dontTriggerOnUseCard = true;
      AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(this, true));
   }

   @Override
   public AbstractCard makeCopy() {
      return new Burn();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(2);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }
}
