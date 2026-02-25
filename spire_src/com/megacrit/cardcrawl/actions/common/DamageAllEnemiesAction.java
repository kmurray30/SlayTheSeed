package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DamageAllEnemiesAction extends AbstractGameAction {
   public int[] damage;
   private int baseDamage;
   private boolean firstFrame = true;
   private boolean utilizeBaseDamage = false;

   public DamageAllEnemiesAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type, AbstractGameAction.AttackEffect effect, boolean isFast) {
      this.source = source;
      this.damage = amount;
      this.actionType = AbstractGameAction.ActionType.DAMAGE;
      this.damageType = type;
      this.attackEffect = effect;
      if (isFast) {
         this.duration = Settings.ACTION_DUR_XFAST;
      } else {
         this.duration = Settings.ACTION_DUR_FAST;
      }
   }

   public DamageAllEnemiesAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type, AbstractGameAction.AttackEffect effect) {
      this(source, amount, type, effect, false);
   }

   public DamageAllEnemiesAction(AbstractPlayer player, int baseDamage, DamageInfo.DamageType type, AbstractGameAction.AttackEffect effect) {
      this(player, null, type, effect, false);
      this.baseDamage = baseDamage;
      this.utilizeBaseDamage = true;
   }

   @Override
   public void update() {
      if (this.firstFrame) {
         boolean playedMusic = false;
         int temp = AbstractDungeon.getCurrRoom().monsters.monsters.size();
         if (this.utilizeBaseDamage) {
            this.damage = DamageInfo.createDamageMatrix(this.baseDamage);
         }

         for (int i = 0; i < temp; i++) {
            if (!AbstractDungeon.getCurrRoom().monsters.monsters.get(i).isDying
               && AbstractDungeon.getCurrRoom().monsters.monsters.get(i).currentHealth > 0
               && !AbstractDungeon.getCurrRoom().monsters.monsters.get(i).isEscaping) {
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

         this.firstFrame = false;
      }

      this.tickDuration();
      if (this.isDone) {
         for (AbstractPower p : AbstractDungeon.player.powers) {
            p.onDamageAllEnemies(this.damage);
         }

         int temp = AbstractDungeon.getCurrRoom().monsters.monsters.size();

         for (int ix = 0; ix < temp; ix++) {
            if (!AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).isDeadOrEscaped()) {
               if (this.attackEffect == AbstractGameAction.AttackEffect.POISON) {
                  AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).tint.color.set(Color.CHARTREUSE);
                  AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).tint.changeColor(Color.WHITE.cpy());
               } else if (this.attackEffect == AbstractGameAction.AttackEffect.FIRE) {
                  AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).tint.color.set(Color.RED);
                  AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).tint.changeColor(Color.WHITE.cpy());
               }

               AbstractDungeon.getCurrRoom().monsters.monsters.get(ix).damage(new DamageInfo(this.source, this.damage[ix], this.damageType));
            }
         }

         if (AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
            AbstractDungeon.actionManager.clearPostCombatActions();
         }

         if (!Settings.FAST_MODE) {
            this.addToTop(new WaitAction(0.1F));
         }
      }
   }
}
