package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import java.util.ArrayList;

public class LineTestEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float x2;
   private float y2;
   private static final float SPACING = 30.0F * Settings.scale;
   private ArrayList<MapDot> dots = new ArrayList<>();

   public LineTestEffect() {
      this.x = InputHelper.mX;
      this.y = InputHelper.mY;
      this.x2 = Settings.WIDTH / 2.0F;
      this.y2 = Settings.HEIGHT / 2.0F;
      Vector2 vec2 = new Vector2(this.x2, this.y2).sub(new Vector2(this.x, this.y));
      float length = vec2.len();
      float START = SPACING * MathUtils.random();

      for (float i = START; i < length; i += SPACING) {
         vec2.clamp(length - i, length - i);
         this.dots.add(new MapDot(this.x + vec2.x, this.y + vec2.y, new Vector2(this.x - this.x2, this.y - this.y2).nor().angle() + 90.0F, true));
      }

      this.duration = 3.0F;
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      for (MapDot d : this.dots) {
         d.render(sb);
      }
   }

   @Override
   public void dispose() {
   }
}
