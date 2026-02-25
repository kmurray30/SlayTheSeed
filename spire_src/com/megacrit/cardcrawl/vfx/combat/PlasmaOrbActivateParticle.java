package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class PlasmaOrbActivateParticle extends AbstractGameEffect {
   private float effectDuration;
   private float x;
   private float y;
   private float sX;
   private float sY;
   private float tX;
   private float tY;
   private TextureAtlas.AtlasRegion img = ImageMaster.GLOW_SPARK_2;

   public PlasmaOrbActivateParticle(float x, float y) {
      this.effectDuration = 0.5F;
      this.duration = this.effectDuration;
      this.startingDuration = this.effectDuration;
      this.sX = x + MathUtils.random(-100.0F, 100.0F) * Settings.scale;
      this.sY = y + MathUtils.random(-30.0F, 30.0F) * Settings.scale;
      this.tX = x;
      this.tY = y;
      this.x = x;
      this.y = y;
      int tmp = MathUtils.random(2);
      if (tmp == 0) {
         this.color = Settings.LIGHT_YELLOW_COLOR.cpy();
      } else if (tmp == 1) {
         this.color = Color.CYAN.cpy();
      } else {
         this.color = Color.SALMON.cpy();
      }

      this.scale = MathUtils.random(0.6F, 1.8F) * Settings.scale;
      this.renderBehind = true;
   }

   @Override
   public void update() {
      this.x = Interpolation.swing.apply(this.sX, this.tX, this.duration);
      this.y = Interpolation.swing.apply(this.sY, this.tY, this.duration);
      super.update();
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.setBlendFunction(770, 1);
      sb.draw(
         this.img,
         this.x - this.img.packedWidth / 2.0F,
         this.y - this.img.packedWidth / 2.0F,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale * MathUtils.random(0.7F, 1.4F),
         this.scale * MathUtils.random(0.7F, 1.4F),
         this.rotation
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
