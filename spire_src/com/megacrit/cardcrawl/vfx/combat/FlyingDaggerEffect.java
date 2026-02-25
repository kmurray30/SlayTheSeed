package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class FlyingDaggerEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float destY;
   private float scaleMultiplier;
   private static final float DUR = 0.5F;
   private TextureAtlas.AtlasRegion img;
   private boolean playedSound = false;

   public FlyingDaggerEffect(float x, float y, float fAngle, boolean shouldFlip) {
      this.img = ImageMaster.DAGGER_STREAK;
      this.x = x - this.img.packedWidth / 2.0F;
      this.destY = y;
      this.y = this.destY - this.img.packedHeight / 2.0F;
      this.startingDuration = 0.5F;
      this.duration = 0.5F;
      this.scaleMultiplier = MathUtils.random(1.2F, 1.5F);
      this.scale = 0.25F * Settings.scale;
      if (shouldFlip) {
         this.rotation = fAngle - 180.0F;
      } else {
         this.rotation = fAngle;
      }

      this.color = Color.CHARTREUSE.cpy();
      this.color.a = 0.0F;
   }

   private void playRandomSfX() {
      int roll = MathUtils.random(5);
      switch (roll) {
         case 0:
            CardCrawlGame.sound.play("ATTACK_DAGGER_1");
            break;
         case 1:
            CardCrawlGame.sound.play("ATTACK_DAGGER_2");
            break;
         case 2:
            CardCrawlGame.sound.play("ATTACK_DAGGER_3");
            break;
         case 3:
            CardCrawlGame.sound.play("ATTACK_DAGGER_4");
            break;
         case 4:
            CardCrawlGame.sound.play("ATTACK_DAGGER_5");
            break;
         default:
            CardCrawlGame.sound.play("ATTACK_DAGGER_6");
      }
   }

   @Override
   public void update() {
      if (!this.playedSound) {
         this.playRandomSfX();
         this.playedSound = true;
      }

      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      Vector2 derp = new Vector2(MathUtils.cos((float) (Math.PI / 180.0) * this.rotation), MathUtils.sin((float) (Math.PI / 180.0) * this.rotation));
      this.x = this.x + derp.x * Gdx.graphics.getDeltaTime() * (3500.0F * this.scaleMultiplier) * Settings.scale;
      this.y = this.y + derp.y * Gdx.graphics.getDeltaTime() * (3500.0F * this.scaleMultiplier) * Settings.scale;
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      if (this.duration > 0.25F) {
         this.color.a = Interpolation.pow5In.apply(1.0F, 0.0F, (this.duration - 0.25F) * 4.0F);
      } else {
         this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration * 4.0F);
      }

      this.scale = this.scale + Gdx.graphics.getDeltaTime() * this.scaleMultiplier;
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.draw(
         this.img,
         this.x,
         this.y,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale,
         this.scale * 1.5F,
         this.rotation
      );
      sb.setBlendFunction(770, 1);
      sb.draw(
         this.img,
         this.x,
         this.y,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale * 0.75F,
         this.scale * 0.75F,
         this.rotation
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
