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

public class AdditiveSlashImpactEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float targetScale;
   private static TextureAtlas.AtlasRegion img;

   public AdditiveSlashImpactEffect(float x, float y, Color color) {
      if (img == null) {
         img = ImageMaster.vfxAtlas.findRegion("ui/impactLineThick");
      }

      this.x = x - img.packedWidth / 2.0F;
      this.y = y - img.packedHeight / 2.0F;
      this.color = color;
      this.duration = 0.4F;
      this.scale = 0.01F;
      this.targetScale = MathUtils.random(3.0F, 5.0F);
      this.rotation = MathUtils.random(360.0F);
   }

   @Override
   public void update() {
      if (this.duration > 0.2F) {
         this.color.a = Interpolation.fade.apply(0.0F, 0.8F, (this.duration - 0.2F) * 5.0F);
         this.scale = Interpolation.fade.apply(0.01F, this.targetScale, (this.duration - 0.2F) * 5.0F) * Settings.scale;
      } else {
         this.color.a = Interpolation.fade.apply(0.0F, 0.8F, this.duration * 5.0F);
         this.scale = Interpolation.fade.apply(0.01F, this.targetScale, this.duration * 5.0F) * Settings.scale;
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
      sb.draw(
         img, this.x, this.y, img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth, img.packedHeight, this.scale / 3.0F, this.scale, this.rotation
      );
      sb.draw(
         img,
         this.x,
         this.y,
         img.packedWidth / 2.0F,
         img.packedHeight / 2.0F,
         img.packedWidth,
         img.packedHeight,
         this.scale / 6.0F,
         this.scale * 0.5F,
         this.rotation + 90.0F
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
