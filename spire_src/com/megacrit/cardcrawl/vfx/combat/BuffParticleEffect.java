package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class BuffParticleEffect extends AbstractGameEffect {
   private TextureAtlas.AtlasRegion img;
   private static final float DURATION = 0.5F;
   private float x;
   private float y;
   private float vY;
   private float scale = 0.0F;

   public BuffParticleEffect(float x, float y) {
      this.x = x + MathUtils.random(-25.0F, 25.0F) * Settings.scale;
      this.y = y + MathUtils.random(-20.0F, 10.0F) * Settings.scale;
      this.duration = 0.5F;
      this.rotation = MathUtils.random(-5.0F, 5.0F);
      switch (MathUtils.random(2)) {
         case 0:
            this.img = ImageMaster.vfxAtlas.findRegion("buffVFX1");
            break;
         case 1:
            this.img = ImageMaster.vfxAtlas.findRegion("buffVFX2");
            break;
         default:
            this.img = ImageMaster.vfxAtlas.findRegion("buffVFX3");
      }

      this.renderBehind = MathUtils.randomBoolean();
      this.vY = MathUtils.random(30.0F, 50.0F) * Settings.scale;
      this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
      this.scale = MathUtils.random(1.0F, 1.5F) * Settings.scale;
   }

   @Override
   public void update() {
      this.scale = this.scale + Gdx.graphics.getDeltaTime() / 2.0F;
      if (this.duration > 0.5F) {
         this.color.a = Interpolation.fade.apply(0.0F, 1.0F, 1.0F - (this.duration - 3.0F));
      } else if (this.duration < 0.5F) {
         this.color.a = Interpolation.fade.apply(1.0F, 0.0F, 1.0F - this.duration * 2.0F);
      }

      this.y = this.y + Gdx.graphics.getDeltaTime() * this.vY;
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      sb.setColor(this.color);
      sb.draw(
         this.img,
         this.x - this.img.packedWidth / 2.0F,
         this.y - this.img.packedHeight / 2.0F,
         this.img.offsetX,
         this.img.offsetY,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale,
         this.scale,
         this.rotation
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
