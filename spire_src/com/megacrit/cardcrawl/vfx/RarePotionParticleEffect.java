package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class RarePotionParticleEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float oX;
   private float oY;
   private float dur_div2;
   private Hitbox hb = null;
   private TextureAtlas.AtlasRegion img;

   public RarePotionParticleEffect(float x, float y) {
      this(null);
      this.x = x;
      this.y = y;
   }

   public RarePotionParticleEffect(Hitbox hb) {
      this.hb = hb;
      this.img = ImageMaster.ROOM_SHINE_2;
      this.duration = MathUtils.random(0.9F, 1.2F);
      this.scale = MathUtils.random(0.4F, 0.6F) * Settings.scale;
      this.dur_div2 = this.duration / 2.0F;
      this.color = new Color(1.0F, MathUtils.random(0.7F, 1.0F), 0.4F, 0.0F);
      this.oX = this.oX + MathUtils.random(-27.0F, 27.0F) * Settings.scale;
      this.oY = this.oY + MathUtils.random(-27.0F, 27.0F) * Settings.scale;
      this.oX = this.oX - this.img.packedWidth / 2.0F;
      this.oY = this.oY - this.img.packedHeight / 2.0F;
      this.renderBehind = MathUtils.randomBoolean(0.2F + (this.scale - 0.5F));
      this.rotation = MathUtils.random(-5.0F, 5.0F);
   }

   @Override
   public void update() {
      if (this.duration > this.dur_div2) {
         this.color.a = Interpolation.pow3In.apply(0.6F, 0.0F, (this.duration - this.dur_div2) / this.dur_div2);
      } else {
         this.color.a = Interpolation.pow3In.apply(0.0F, 0.6F, this.duration / this.dur_div2);
      }

      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.setBlendFunction(770, 1);
      if (this.hb == null) {
         sb.draw(
            this.img,
            this.x + this.oX,
            this.y + this.oY,
            this.img.packedWidth / 2.0F,
            this.img.packedHeight / 2.0F,
            this.img.packedWidth,
            this.img.packedHeight,
            this.scale,
            this.scale * MathUtils.random(0.6F, 1.4F),
            this.rotation
         );
      } else {
         sb.draw(
            this.img,
            this.hb.cX + this.oX,
            this.hb.cY + this.oY,
            this.img.packedWidth / 2.0F,
            this.img.packedHeight / 2.0F,
            this.img.packedWidth,
            this.img.packedHeight,
            this.scale,
            this.scale * MathUtils.random(0.6F, 1.4F),
            this.rotation
         );
      }

      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
