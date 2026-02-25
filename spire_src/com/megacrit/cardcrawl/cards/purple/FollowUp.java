package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.watcher.FollowUpAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class FollowUp extends AbstractCard {
   public static final String ID = "FollowUp";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("FollowUp");

   public FollowUp() {
      super(
         "FollowUp",
         cardStrings.NAME,
         "purple/attack/follow_up",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 7;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
      this.addToBot(new FollowUpAction());
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
         this.upgradeDamage(4);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new FollowUp();
   }
}
