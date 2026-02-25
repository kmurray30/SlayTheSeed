package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class ShineLinesEffect extends AbstractGameEffect {
   private static final float EFFECT_DUR = 0.25F;
   private float x;
   private float y;
   private static final float SCALE_INCREASE_RATE = 8.0F;
   private TextureAtlas.AtlasRegion img = ImageMaster.GRAB_COIN;

   public ShineLinesEffect(float x, float y) {
      this.duration = 0.25F;
      this.startingDuration = 0.25F;
      this.x = x - this.img.packedWidth / 2.0F;
      this.y = y - this.img.packedHeight / 2.0F;
      this.rotation = MathUtils.random(0.0F, 360.0F);
      this.color = Color.WHITE.cpy();
      this.scale = 0.0F;
   }

   @Override
   public void update() {
      super.update();
      this.scale = this.scale + Gdx.graphics.getDeltaTime() * 8.0F;
   }

   @Override
   public void render(SpriteBatch sb) {
      if (!this.isDone) {
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
   }

   @Override
   public void dispose() {
   }
}
