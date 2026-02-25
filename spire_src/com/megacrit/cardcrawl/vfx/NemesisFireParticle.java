package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class NemesisFireParticle extends AbstractGameEffect {
   private float x;
   private float y;
   private float vY;
   private TextureAtlas.AtlasRegion img;

   public NemesisFireParticle(float x, float y) {
      this.duration = MathUtils.random(0.5F, 1.0F);
      this.startingDuration = this.duration;
      this.img = this.getImg();
      this.x = x - this.img.packedWidth / 2;
      this.y = y - this.img.packedHeight / 2;
      this.scale = Settings.scale * MathUtils.random(0.25F, 0.5F);
      this.vY = MathUtils.random(1.0F, 10.0F) * Settings.scale;
      this.vY = this.vY * this.vY;
      this.rotation = MathUtils.random(-20.0F, 20.0F);
      this.color = new Color(0.1F, 0.2F, 0.1F, 0.01F);
   }

   private TextureAtlas.AtlasRegion getImg() {
      switch (MathUtils.random(0, 2)) {
         case 0:
            return ImageMaster.TORCH_FIRE_1;
         case 1:
            return ImageMaster.TORCH_FIRE_2;
         default:
            return ImageMaster.TORCH_FIRE_3;
      }
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration / this.startingDuration);
      this.y = this.y + this.vY * Gdx.graphics.getDeltaTime();
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
         this.scale,
         this.rotation
      );
   }

   @Override
   public void dispose() {
   }
}
