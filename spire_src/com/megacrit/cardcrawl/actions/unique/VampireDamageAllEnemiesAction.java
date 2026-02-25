package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import com.megacrit.cardcrawl.vfx.combat.FlyingOrbEffect;

public class VampireDamageAllEnemiesAction extends AbstractGameAction {
   public int[] damage;

   public VampireDamageAllEnemiesAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type, AbstractGameAction.AttackEffect effect) {
      this.setValues(null, source, amount[0]);
      this.damage = amount;
      this.actionType = AbstractGameAction.ActionType.DAMAGE;
      this.damageType = type;
      this.attackEffect = effect;
      this.duration = Settings.ACTION_DUR_FAST;
   }

   @Override
   public void update() {
      if (this.duration == Settings.ACTION_DUR_FAST) {
         boolean playedMusic = false;
         int temp = AbstractDungeon.getCurrRoom().monsters.monsters.size();

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
      }

      this.tickDuration();
      if (this.isDone) {
         for (AbstractPower p : AbstractDungeon.player.powers) {
            p.onDamageAllEnemies(this.damage);
         }

         int healAmount = 0;

         for (int ix = 0; ix < AbstractDungeon.getCurrRoom().monsters.monsters.size(); ix++) {
            AbstractMonster target = AbstractDungeon.getCurrRoom().monsters.monsters.get(ix);
            if (!target.isDying && target.currentHealth > 0 && !target.isEscaping) {
               target.damage(new DamageInfo(this.source, this.damage[ix], this.damageType));
               if (target.lastDamageTaken > 0) {
                  healAmount += target.lastDamageTaken;

                  for (int j = 0; j < target.lastDamageTaken / 2 && j < 10; j++) {
                     this.addToBot(new VFXAction(new FlyingOrbEffect(target.hb.cX, target.hb.cY)));
                  }
               }
            }
         }

         if (healAmount > 0) {
            if (!Settings.FAST_MODE) {
               this.addToBot(new WaitAction(0.3F));
            }

            this.addToBot(new HealAction(this.source, this.source, healAmount));
         }

         if (AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
            AbstractDungeon.actionManager.clearPostCombatActions();
         }

         this.addToTop(new WaitAction(0.1F));
      }
   }
}
