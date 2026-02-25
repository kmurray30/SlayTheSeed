package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.Frost;

public class ColdSnap extends AbstractCard {
   public static final String ID = "Cold Snap";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Cold Snap");

   public ColdSnap() {
      super(
         "Cold Snap",
         cardStrings.NAME,
         "blue/attack/cold_snap",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.showEvokeValue = true;
      this.showEvokeOrbCount = 1;
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
      this.baseDamage = 6;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.SLASH_HEAVY));

      for (int i = 0; i < this.magicNumber; i++) {
         this.addToBot(new ChannelAction(new Frost()));
      }
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(3);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new ColdSnap();
   }
}
