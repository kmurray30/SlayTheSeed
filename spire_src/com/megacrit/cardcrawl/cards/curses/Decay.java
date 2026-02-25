package com.megacrit.cardcrawl.cards.curses;

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

public class Decay extends AbstractCard {
   public static final String ID = "Decay";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Decay");

   public Decay() {
      super(
         "Decay",
         cardStrings.NAME,
         "curse/decay",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.CURSE,
         AbstractCard.CardColor.CURSE,
         AbstractCard.CardRarity.CURSE,
         AbstractCard.CardTarget.NONE
      );
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (this.dontTriggerOnUseCard) {
         this.addToTop(
            new DamageAction(
               AbstractDungeon.player, new DamageInfo(AbstractDungeon.player, 2, DamageInfo.DamageType.THORNS), AbstractGameAction.AttackEffect.FIRE
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
   public void upgrade() {
   }

   @Override
   public AbstractCard makeCopy() {
      return new Decay();
   }
}
