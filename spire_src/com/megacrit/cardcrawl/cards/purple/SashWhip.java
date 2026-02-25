package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.watcher.HeadStompAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SashWhip extends AbstractCard {
   public static final String ID = "SashWhip";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("SashWhip");

   public SashWhip() {
      super(
         "SashWhip",
         cardStrings.NAME,
         "purple/attack/sash_whip",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 8;
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
      this.addToBot(new HeadStompAction(m, this.magicNumber));
   }

   @Override
   public void triggerOnGlowCheck() {
      if (!AbstractDungeon.actionManager.cardsPlayedThisCombat.isEmpty()
         && AbstractDungeon.actionManager.cardsPlayedThisCombat.get(AbstractDungeon.actionManager.cardsPlayedThisCombat.size() - 1).type
            == AbstractCard.CardType.ATTACK) {
         this.glowColor = AbstractCard.GOLD_BORDER_GLOW_COLOR.cpy();
      } else {
         this.glowColor = AbstractCard.BLUE_BORDER_GLOW_COLOR.cpy();
      }
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(2);
         this.upgradeMagicNumber(1);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new SashWhip();
   }
}
