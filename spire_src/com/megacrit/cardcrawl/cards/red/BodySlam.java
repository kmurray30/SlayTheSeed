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

public class BodySlam extends AbstractCard {
   public static final String ID = "Body Slam";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Body Slam");

   public BodySlam() {
      super(
         "Body Slam",
         cardStrings.NAME,
         "red/attack/body_slam",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 0;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.baseDamage = p.currentBlock;
      this.calculateCardDamage(m);
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, DamageInfo.DamageType.NORMAL), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
      this.rawDescription = cardStrings.DESCRIPTION;
      this.initializeDescription();
   }

   @Override
   public void applyPowers() {
      this.baseDamage = AbstractDungeon.player.currentBlock;
      super.applyPowers();
      this.rawDescription = cardStrings.DESCRIPTION;
      this.rawDescription = this.rawDescription + cardStrings.UPGRADE_DESCRIPTION;
      this.initializeDescription();
   }

   @Override
   public void onMoveToDiscard() {
      this.rawDescription = cardStrings.DESCRIPTION;
      this.initializeDescription();
   }

   @Override
   public void calculateCardDamage(AbstractMonster mo) {
      super.calculateCardDamage(mo);
      this.rawDescription = cardStrings.DESCRIPTION;
      this.rawDescription = this.rawDescription + cardStrings.UPGRADE_DESCRIPTION;
      this.initializeDescription();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeBaseCost(0);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new BodySlam();
   }
}
