package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class RefreshEnergyEffect extends AbstractGameEffect {
   private static final float EFFECT_DUR = 0.4F;
   private float scale;
   private Color color;
   private TextureAtlas.AtlasRegion img;
   private float x;
   private float y;

   public RefreshEnergyEffect() {
      this.scale = Settings.scale / 1.2F;
      this.color = new Color(1.0F, 1.0F, 1.0F, 1.0F);
      this.img = ImageMaster.WHITE_RING;
      this.x = 198.0F * Settings.scale - this.img.packedWidth / 2.0F;
      this.y = 190.0F * Settings.scale - this.img.packedHeight / 2.0F;
      this.duration = 0.4F;
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      this.scale = this.scale * (1.0F + Gdx.graphics.getDeltaTime() * 2.5F);
      this.color.a = Interpolation.fade.apply(0.0F, 0.75F, this.duration / 0.4F);
      if (this.color.a < 0.0F) {
         this.color.a = 0.0F;
      }

      if (this.duration < 0.0F) {
         this.isDone = true;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.setBlendFunction(770, 1);
      sb.draw(
         this.img,
         this.x,
         this.y,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale * 1.5F,
         this.scale * 1.5F,
         this.rotation
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
