package com.megacrit.cardcrawl.orbs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.OrbStrings;
import com.megacrit.cardcrawl.vfx.combat.FrostOrbActivateEffect;
import com.megacrit.cardcrawl.vfx.combat.FrostOrbPassiveEffect;
import com.megacrit.cardcrawl.vfx.combat.OrbFlareEffect;

public class Frost extends AbstractOrb {
   public static final String ORB_ID = "Frost";
   private static final OrbStrings orbString = CardCrawlGame.languagePack.getOrbString("Frost");
   private boolean hFlip1;
   private boolean hFlip2;
   private float vfxTimer = 1.0F;
   private float vfxIntervalMin = 0.15F;
   private float vfxIntervalMax = 0.8F;

   public Frost() {
      this.hFlip1 = MathUtils.randomBoolean();
      this.hFlip2 = MathUtils.randomBoolean();
      this.ID = "Frost";
      this.name = orbString.NAME;
      this.baseEvokeAmount = 5;
      this.evokeAmount = this.baseEvokeAmount;
      this.basePassiveAmount = 2;
      this.passiveAmount = this.basePassiveAmount;
      this.updateDescription();
      this.channelAnimTimer = 0.5F;
   }

   @Override
   public void updateDescription() {
      this.applyFocus();
      this.description = orbString.DESCRIPTION[0] + this.passiveAmount + orbString.DESCRIPTION[1] + this.evokeAmount + orbString.DESCRIPTION[2];
   }

   @Override
   public void onEvoke() {
      AbstractDungeon.actionManager.addToTop(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, this.evokeAmount));
   }

   @Override
   public void updateAnimation() {
      super.updateAnimation();
      this.angle = this.angle + Gdx.graphics.getDeltaTime() * 180.0F;
      this.vfxTimer = this.vfxTimer - Gdx.graphics.getDeltaTime();
      if (this.vfxTimer < 0.0F) {
         AbstractDungeon.effectList.add(new FrostOrbPassiveEffect(this.cX, this.cY));
         if (MathUtils.randomBoolean()) {
            AbstractDungeon.effectList.add(new FrostOrbPassiveEffect(this.cX, this.cY));
         }

         this.vfxTimer = MathUtils.random(this.vfxIntervalMin, this.vfxIntervalMax);
      }
   }

   @Override
   public void onEndOfTurn() {
      float speedTime = 0.6F / AbstractDungeon.player.orbs.size();
      if (Settings.FAST_MODE) {
         speedTime = 0.0F;
      }

      AbstractDungeon.actionManager.addToBottom(new VFXAction(new OrbFlareEffect(this, OrbFlareEffect.OrbFlareColor.FROST), speedTime));
      AbstractDungeon.actionManager.addToBottom(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, this.passiveAmount, true));
   }

   @Override
   public void triggerEvokeAnimation() {
      CardCrawlGame.sound.play("ORB_FROST_EVOKE", 0.1F);
      AbstractDungeon.effectsQueue.add(new FrostOrbActivateEffect(this.cX, this.cY));
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.c);
      sb.draw(
         ImageMaster.FROST_ORB_RIGHT,
         this.cX - 48.0F + this.bobEffect.y / 4.0F,
         this.cY - 48.0F + this.bobEffect.y / 4.0F,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale,
         this.scale,
         0.0F,
         0,
         0,
         96,
         96,
         this.hFlip1,
         false
      );
      sb.draw(
         ImageMaster.FROST_ORB_LEFT,
         this.cX - 48.0F + this.bobEffect.y / 4.0F,
         this.cY - 48.0F - this.bobEffect.y / 4.0F,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale,
         this.scale,
         0.0F,
         0,
         0,
         96,
         96,
         this.hFlip1,
         false
      );
      sb.draw(
         ImageMaster.FROST_ORB_MIDDLE,
         this.cX - 48.0F - this.bobEffect.y / 4.0F,
         this.cY - 48.0F + this.bobEffect.y / 2.0F,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale,
         this.scale,
         0.0F,
         0,
         0,
         96,
         96,
         this.hFlip2,
         false
      );
      this.renderText(sb);
      this.hb.render(sb);
   }

   @Override
   public void playChannelSFX() {
      CardCrawlGame.sound.play("ORB_FROST_CHANNEL", 0.1F);
   }

   @Override
   public AbstractOrb makeCopy() {
      return new Frost();
   }
}
