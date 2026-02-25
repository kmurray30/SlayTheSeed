package com.megacrit.cardcrawl.orbs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.OrbStrings;

public class EmptyOrbSlot extends AbstractOrb {
   public static final String ORB_ID = "Empty";
   private static final OrbStrings orbString = CardCrawlGame.languagePack.getOrbString("Empty");
   public static final String[] DESC = orbString.DESCRIPTION;

   public EmptyOrbSlot(float x, float y) {
      this.angle = MathUtils.random(360.0F);
      this.ID = "Empty";
      this.name = orbString.NAME;
      this.evokeAmount = 0;
      this.cX = x;
      this.cY = y;
      this.updateDescription();
      this.channelAnimTimer = 0.5F;
   }

   public EmptyOrbSlot() {
      this.angle = MathUtils.random(360.0F);
      this.name = orbString.NAME;
      this.evokeAmount = 0;
      this.cX = AbstractDungeon.player.drawX + AbstractDungeon.player.hb_x;
      this.cY = AbstractDungeon.player.drawY + AbstractDungeon.player.hb_y + AbstractDungeon.player.hb_h / 2.0F;
      this.updateDescription();
   }

   @Override
   public void updateDescription() {
      this.description = DESC[0];
   }

   @Override
   public void onEvoke() {
   }

   @Override
   public void updateAnimation() {
      super.updateAnimation();
      this.angle = this.angle + Gdx.graphics.getDeltaTime() * 10.0F;
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.c);
      sb.draw(
         ImageMaster.ORB_SLOT_2,
         this.cX - 48.0F - this.bobEffect.y / 8.0F,
         this.cY - 48.0F + this.bobEffect.y / 8.0F,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale,
         this.scale,
         0.0F,
         0,
         0,
         96,
         96,
         false,
         false
      );
      sb.draw(
         ImageMaster.ORB_SLOT_1,
         this.cX - 48.0F + this.bobEffect.y / 8.0F,
         this.cY - 48.0F - this.bobEffect.y / 8.0F,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale,
         this.scale,
         this.angle,
         0,
         0,
         96,
         96,
         false,
         false
      );
      this.renderText(sb);
      this.hb.render(sb);
   }

   @Override
   public AbstractOrb makeCopy() {
      return new EmptyOrbSlot();
   }

   @Override
   public void playChannelSFX() {
   }
}
