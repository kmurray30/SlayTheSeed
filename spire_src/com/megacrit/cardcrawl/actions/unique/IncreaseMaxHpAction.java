package com.megacrit.cardcrawl.actions.unique;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class IncreaseMaxHpAction extends AbstractGameAction {
   private boolean showEffect;
   private float increasePercent;

   public IncreaseMaxHpAction(AbstractMonster m, float increasePercent, boolean showEffect) {
      if (Settings.FAST_MODE) {
         this.startDuration = Settings.ACTION_DUR_XFAST;
      } else {
         this.startDuration = Settings.ACTION_DUR_FAST;
      }

      this.duration = this.startDuration;
      this.showEffect = showEffect;
      this.increasePercent = increasePercent;
      this.target = m;
   }

   @Override
   public void update() {
      if (this.duration == this.startDuration) {
         this.target.increaseMaxHp(MathUtils.round(this.target.maxHealth * this.increasePercent), this.showEffect);
      }

      this.tickDuration();
   }
}
