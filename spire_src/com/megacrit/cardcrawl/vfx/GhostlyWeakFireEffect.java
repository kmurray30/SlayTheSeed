package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class GhostlyWeakFireEffect extends AbstractGameEffect {
   private TextureAtlas.AtlasRegion img = this.getImg();
   private float x;
   private float y;
   private float vX;
   private float vY;
   private static final float DUR = 1.0F;

   public GhostlyWeakFireEffect(float x, float y) {
      this.x = x + MathUtils.random(-2.0F, 2.0F) * Settings.scale - this.img.packedWidth / 2.0F;
      this.y = y + MathUtils.random(-2.0F, 2.0F) * Settings.scale - this.img.packedHeight / 2.0F;
      this.vX = MathUtils.random(-2.0F, 2.0F) * Settings.scale;
      this.vY = MathUtils.random(0.0F, 80.0F) * Settings.scale;
      this.duration = 1.0F;
      this.color = Color.SKY.cpy();
      this.color.a = 0.0F;
      this.scale = Settings.scale * MathUtils.random(2.0F, 3.0F);
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
      this.x = this.x + this.vX * Gdx.graphics.getDeltaTime();
      this.y = this.y + this.vY * Gdx.graphics.getDeltaTime();
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      this.color.a = this.duration / 2.0F;
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
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
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
