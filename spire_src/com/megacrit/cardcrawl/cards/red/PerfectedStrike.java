package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class PerfectedStrike extends AbstractCard {
   public static final String ID = "Perfected Strike";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Perfected Strike");

   public PerfectedStrike() {
      super(
         "Perfected Strike",
         cardStrings.NAME,
         "red/attack/perfected_strike",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 6;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.tags.add(AbstractCard.CardTags.STRIKE);
   }

   public static int countCards() {
      int count = 0;

      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (isStrike(c)) {
            count++;
         }
      }

      for (AbstractCard cx : AbstractDungeon.player.drawPile.group) {
         if (isStrike(cx)) {
            count++;
         }
      }

      for (AbstractCard cxx : AbstractDungeon.player.discardPile.group) {
         if (isStrike(cxx)) {
            count++;
         }
      }

      return count;
   }

   public static boolean isStrike(AbstractCard c) {
      return c.hasTag(AbstractCard.CardTags.STRIKE);
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
   }

   @Override
   public void calculateCardDamage(AbstractMonster mo) {
      int realBaseDamage = this.baseDamage;
      this.baseDamage = this.baseDamage + this.magicNumber * countCards();
      super.calculateCardDamage(mo);
      this.baseDamage = realBaseDamage;
      this.isDamageModified = this.damage != this.baseDamage;
   }

   @Override
   public void applyPowers() {
      int realBaseDamage = this.baseDamage;
      this.baseDamage = this.baseDamage + this.magicNumber * countCards();
      super.applyPowers();
      this.baseDamage = realBaseDamage;
      this.isDamageModified = this.damage != this.baseDamage;
   }

   @Override
   public AbstractCard makeCopy() {
      return new PerfectedStrike();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }
}
