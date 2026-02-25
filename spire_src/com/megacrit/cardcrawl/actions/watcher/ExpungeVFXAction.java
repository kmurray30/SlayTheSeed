package com.megacrit.cardcrawl.actions.watcher;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.AnimatedSlashEffect;

public class ExpungeVFXAction extends AbstractGameAction {
   public ExpungeVFXAction(AbstractMonster m) {
      this.source = m;
   }

   @Override
   public void update() {
      if (!this.source.isDeadOrEscaped()) {
         this.addToTop(
            new VFXAction(
               new AnimatedSlashEffect(
                  this.source.hb.cX, this.source.hb.cY - 30.0F * Settings.scale, -500.0F, -500.0F, 135.0F, 4.0F, Color.VIOLET, Color.MAGENTA
               )
            )
         );
         this.addToTop(new SFXAction("ATTACK_MAGIC_BEAM_SHORT", 0.7F, true));
         this.addToTop(new SFXAction("ATTACK_IRON_3", 0.2F));
      }

      this.isDone = true;
   }
}
