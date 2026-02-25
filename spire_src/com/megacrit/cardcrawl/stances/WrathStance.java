package com.megacrit.cardcrawl.stances;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.StanceStrings;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.stance.StanceAuraEffect;
import com.megacrit.cardcrawl.vfx.stance.StanceChangeParticleGenerator;
import com.megacrit.cardcrawl.vfx.stance.WrathParticleEffect;

public class WrathStance extends AbstractStance {
   public static final String STANCE_ID = "Wrath";
   private static final StanceStrings stanceString = CardCrawlGame.languagePack.getStanceString("Wrath");
   private static long sfxId = -1L;

   public WrathStance() {
      this.ID = "Wrath";
      this.name = stanceString.NAME;
      this.updateDescription();
   }

   @Override
   public float atDamageGive(float damage, DamageInfo.DamageType type) {
      return type == DamageInfo.DamageType.NORMAL ? damage * 2.0F : damage;
   }

   @Override
   public float atDamageReceive(float damage, DamageInfo.DamageType type) {
      return type == DamageInfo.DamageType.NORMAL ? damage * 2.0F : damage;
   }

   @Override
   public void updateAnimation() {
      if (!Settings.DISABLE_EFFECTS) {
         this.particleTimer = this.particleTimer - Gdx.graphics.getDeltaTime();
         if (this.particleTimer < 0.0F) {
            this.particleTimer = 0.05F;
            AbstractDungeon.effectsQueue.add(new WrathParticleEffect());
         }
      }

      this.particleTimer2 = this.particleTimer2 - Gdx.graphics.getDeltaTime();
      if (this.particleTimer2 < 0.0F) {
         this.particleTimer2 = MathUtils.random(0.3F, 0.4F);
         AbstractDungeon.effectsQueue.add(new StanceAuraEffect("Wrath"));
      }
   }

   @Override
   public void updateDescription() {
      this.description = stanceString.DESCRIPTION[0];
   }

   @Override
   public void onEnterStance() {
      if (sfxId != -1L) {
         this.stopIdleSfx();
      }

      CardCrawlGame.sound.play("STANCE_ENTER_WRATH");
      sfxId = CardCrawlGame.sound.playAndLoop("STANCE_LOOP_WRATH");
      AbstractDungeon.effectsQueue.add(new BorderFlashEffect(Color.SCARLET, true));
      AbstractDungeon.effectsQueue.add(new StanceChangeParticleGenerator(AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY, "Wrath"));
   }

   @Override
   public void onExitStance() {
      this.stopIdleSfx();
   }

   @Override
   public void stopIdleSfx() {
      if (sfxId != -1L) {
         CardCrawlGame.sound.stop("STANCE_LOOP_WRATH", sfxId);
         sfxId = -1L;
      }
   }
}
