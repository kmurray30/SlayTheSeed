package com.megacrit.cardcrawl.actions.unique;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ExhaustSpecificCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class ImmolateAction extends AbstractGameAction {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ImmolateAction");
   public static final String[] TEXT;
   public int[] damage;

   public ImmolateAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type) {
      this.setValues(null, source, amount[0]);
      this.damage = amount;
      this.actionType = AbstractGameAction.ActionType.DAMAGE;
      this.damageType = type;
      this.attackEffect = AbstractGameAction.AttackEffect.FIRE;
      this.duration = Settings.ACTION_DUR_FAST;
   }

   @Override
   public void update() {
      if (this.duration == Settings.ACTION_DUR_FAST) {
         if (AbstractDungeon.player.hand.size() == 0) {
            this.isDone = true;
         } else if (AbstractDungeon.player.hand.size() != 1) {
            AbstractDungeon.handCardSelectScreen.open(TEXT[0], 1, false);
            this.tickDuration();
         } else {
            AbstractCard card = AbstractDungeon.player.hand.getBottomCard();
            if (card.type == AbstractCard.CardType.CURSE || card.type == AbstractCard.CardType.STATUS) {
               this.dealDamage();
            }

            this.addToTop(new ExhaustSpecificCardAction(card, AbstractDungeon.player.hand));
            this.isDone = true;
         }
      } else {
         if (!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
            for (AbstractCard c : AbstractDungeon.handCardSelectScreen.selectedCards.group) {
               if (c.type == AbstractCard.CardType.CURSE || c.type == AbstractCard.CardType.STATUS) {
                  this.dealDamage();
               }

               this.addToTop(new ExhaustSpecificCardAction(c, AbstractDungeon.handCardSelectScreen.selectedCards));
            }

            AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
         }

         this.tickDuration();
      }
   }

   public void dealDamage() {
      boolean playedMusic = false;
      int temp = AbstractDungeon.getCurrRoom().monsters.monsters.size();

      for (int i = 0; i < temp; i++) {
         if (!AbstractDungeon.getCurrRoom().monsters.monsters.get(i).isDying && !AbstractDungeon.getCurrRoom().monsters.monsters.get(i).isEscaping) {
            if (playedMusic) {
               AbstractDungeon.effectList
                  .add(
                     new FlashAtkImgEffect(
                        AbstractDungeon.getCurrRoom().monsters.monsters.get(i).hb.cX,
                        AbstractDungeon.getCurrRoom().monsters.monsters.get(i).hb.cY,
                        this.attackEffect,
                        true
                     )
                  );
            } else {
               playedMusic = true;
               AbstractDungeon.effectList
                  .add(
                     new FlashAtkImgEffect(
                        AbstractDungeon.getCurrRoom().monsters.monsters.get(i).hb.cX,
                        AbstractDungeon.getCurrRoom().monsters.monsters.get(i).hb.cY,
                        this.attackEffect
                     )
                  );
            }
         }
      }

      for (AbstractPower p : AbstractDungeon.player.powers) {
         p.onDamageAllEnemies(this.damage);
      }

      int temp2 = AbstractDungeon.getCurrRoom().monsters.monsters.size();

      for (int ix = 0; ix < temp2; ix++) {
         if (!AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).isDying && !AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).isEscaping) {
            AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).tint.color = Color.RED.cpy();
            AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).tint.changeColor(Color.WHITE.cpy());
            AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).damage(new DamageInfo(this.source, this.damage[ix], this.damageType));
         }
      }

      if (AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
         AbstractDungeon.actionManager.clearPostCombatActions();
      }
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
