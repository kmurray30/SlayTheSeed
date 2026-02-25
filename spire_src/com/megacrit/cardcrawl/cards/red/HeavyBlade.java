package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.VerticalImpactEffect;

public class HeavyBlade extends AbstractCard {
   public static final String ID = "Heavy Blade";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Heavy Blade");

   public HeavyBlade() {
      super(
         "Heavy Blade",
         cardStrings.NAME,
         "red/attack/heavy_blade",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 14;
      this.baseMagicNumber = 3;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (m != null) {
         this.addToBot(new VFXAction(new VerticalImpactEffect(m.hb.cX + m.hb.width / 4.0F, m.hb.cY - m.hb.height / 4.0F)));
      }

      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
   }

   @Override
   public void applyPowers() {
      AbstractPower strength = AbstractDungeon.player.getPower("Strength");
      if (strength != null) {
         strength.amount = strength.amount * this.magicNumber;
      }

      super.applyPowers();
      if (strength != null) {
         strength.amount = strength.amount / this.magicNumber;
      }
   }

   @Override
   public void calculateCardDamage(AbstractMonster mo) {
      AbstractPower strength = AbstractDungeon.player.getPower("Strength");
      if (strength != null) {
         strength.amount = strength.amount * this.magicNumber;
      }

      super.calculateCardDamage(mo);
      if (strength != null) {
         strength.amount = strength.amount / this.magicNumber;
      }
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

   @Override
   public AbstractCard makeCopy() {
      return new HeavyBlade();
   }
}
