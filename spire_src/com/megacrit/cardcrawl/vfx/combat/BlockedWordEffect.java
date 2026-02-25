package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class BlockedWordEffect extends AbstractGameEffect {
   private static final float EFFECT_DUR = 2.3F;
   private float x;
   private float y;
   private static final float OFFSET_Y = 150.0F * Settings.scale;
   private String msg;
   private float scale = 1.0F;
   public AbstractCreature target;

   public BlockedWordEffect(AbstractCreature target, float x, float y, String msg) {
      this.duration = 2.3F;
      this.startingDuration = 2.3F;
      this.x = x;
      this.y = y + OFFSET_Y;
      this.target = target;
      this.msg = msg;
      this.color = Color.WHITE.cpy();
   }

   @Override
   public void update() {
      this.y = this.y + Gdx.graphics.getDeltaTime() * this.duration * 100.0F * Settings.scale;
      super.update();
      this.scale = Settings.scale * this.duration / 2.3F + 1.0F;
   }

   @Override
   public void render(SpriteBatch sb) {
      FontHelper.damageNumberFont.getData().setScale(this.scale);
      FontHelper.renderFontCentered(sb, FontHelper.damageNumberFont, this.msg, this.x, this.y, this.color);
   }

   @Override
   public void dispose() {
   }
}
