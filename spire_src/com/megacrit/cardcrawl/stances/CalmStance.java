package com.megacrit.cardcrawl.stances;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.StanceStrings;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.stance.CalmParticleEffect;
import com.megacrit.cardcrawl.vfx.stance.StanceAuraEffect;

public class CalmStance extends AbstractStance {
   public static final String STANCE_ID = "Calm";
   private static final StanceStrings stanceString = CardCrawlGame.languagePack.getStanceString("Calm");
   private static long sfxId = -1L;

   public CalmStance() {
      this.ID = "Calm";
      this.name = stanceString.NAME;
      this.updateDescription();
   }

   @Override
   public void updateDescription() {
      this.description = stanceString.DESCRIPTION[0];
   }

   @Override
   public void updateAnimation() {
      if (!Settings.DISABLE_EFFECTS) {
         this.particleTimer = this.particleTimer - Gdx.graphics.getDeltaTime();
         if (this.particleTimer < 0.0F) {
            this.particleTimer = 0.04F;
            AbstractDungeon.effectsQueue.add(new CalmParticleEffect());
         }
      }

      this.particleTimer2 = this.particleTimer2 - Gdx.graphics.getDeltaTime();
      if (this.particleTimer2 < 0.0F) {
         this.particleTimer2 = MathUtils.random(0.45F, 0.55F);
         AbstractDungeon.effectsQueue.add(new StanceAuraEffect("Calm"));
      }
   }

   @Override
   public void onEnterStance() {
      if (sfxId != -1L) {
         this.stopIdleSfx();
      }

      CardCrawlGame.sound.play("STANCE_ENTER_CALM");
      sfxId = CardCrawlGame.sound.playAndLoop("STANCE_LOOP_CALM");
      AbstractDungeon.effectsQueue.add(new BorderFlashEffect(Color.SKY, true));
   }

   @Override
   public void onExitStance() {
      AbstractDungeon.actionManager.addToBottom(new GainEnergyAction(2));
      this.stopIdleSfx();
   }

   @Override
   public void stopIdleSfx() {
      if (sfxId != -1L) {
         CardCrawlGame.sound.stop("STANCE_LOOP_CALM", sfxId);
         sfxId = -1L;
      }
   }
}
