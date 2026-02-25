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

public class StunStarEffect extends AbstractGameEffect {
   private TextureAtlas.AtlasRegion img;
   private static final float DURATION = 2.0F;
   private float x;
   private float y;
   private float vX;
   private float vY;
   private float scale;

   public StunStarEffect(float x, float y) {
      this.duration = 2.0F;
      this.img = ImageMaster.TINY_STAR;
      this.x = x - this.img.packedWidth / 2.0F;
      this.y = y - this.img.packedWidth / 2.0F;
      this.vX = 128.0F * Settings.scale;
      this.color = new Color(1.0F, 0.9F, 0.3F, 0.0F);
      this.renderBehind = false;
      this.scale = Settings.scale;
      this.rotation = MathUtils.random(360.0F);
   }

   @Override
   public void update() {
      this.vX = MathUtils.cos((float) Math.PI * this.duration);
      this.vY = MathUtils.cos((float) Math.PI * this.duration * 2.0F);
      this.rotation = this.rotation - Gdx.graphics.getDeltaTime() * 60.0F;
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      } else if (this.duration < 1.0F) {
         this.color.a = Interpolation.pow5Out.apply(this.duration);
         this.color.r = Interpolation.pow2Out.apply(this.duration);
         this.color.g = Interpolation.pow2Out.apply(this.duration) * 0.9F;
         this.color.b = Interpolation.pow2Out.apply(this.duration) * 0.3F;
      } else if (this.duration > 1.0F) {
         this.color.a = Interpolation.pow5Out.apply(1.0F - (this.duration - 1.0F));
         this.color.r = Interpolation.pow4Out.apply(1.0F - (this.duration - 1.0F));
         this.color.g = Interpolation.pow4Out.apply(1.0F - (this.duration - 1.0F)) * 0.9F;
         this.color.b = Interpolation.pow4Out.apply(1.0F - (this.duration - 1.0F)) * 0.3F;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.draw(
         this.img,
         this.x - this.vX * 30.0F * Settings.scale,
         this.y - this.vY * 5.0F * Settings.scale,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale,
         this.scale,
         this.rotation
      );
   }

   @Override
   public void dispose() {
   }
}
