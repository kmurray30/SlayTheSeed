package com.megacrit.cardcrawl.orbs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DarkOrbEvokeAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.OrbStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.DarkOrbActivateEffect;
import com.megacrit.cardcrawl.vfx.combat.DarkOrbPassiveEffect;
import com.megacrit.cardcrawl.vfx.combat.OrbFlareEffect;

public class Dark extends AbstractOrb {
   public static final String ORB_ID = "Dark";
   private static final OrbStrings orbString = CardCrawlGame.languagePack.getOrbString("Dark");
   public static final String[] DESC = orbString.DESCRIPTION;
   private static final float ORB_BORDER_SCALE = 1.2F;
   private float vfxTimer = 0.5F;
   private static final float VFX_INTERVAL_TIME = 0.25F;

   public Dark() {
      this.ID = "Dark";
      this.img = ImageMaster.ORB_DARK;
      this.name = orbString.NAME;
      this.baseEvokeAmount = 6;
      this.evokeAmount = this.baseEvokeAmount;
      this.basePassiveAmount = 6;
      this.passiveAmount = this.basePassiveAmount;
      this.updateDescription();
      this.channelAnimTimer = 0.5F;
   }

   @Override
   public void updateDescription() {
      this.applyFocus();
      this.description = DESC[0] + this.passiveAmount + DESC[1] + this.evokeAmount + DESC[2];
   }

   @Override
   public void onEvoke() {
      AbstractDungeon.actionManager
         .addToTop(
            new DarkOrbEvokeAction(new DamageInfo(AbstractDungeon.player, this.evokeAmount, DamageInfo.DamageType.THORNS), AbstractGameAction.AttackEffect.FIRE)
         );
   }

   @Override
   public void onEndOfTurn() {
      float speedTime = 0.6F / AbstractDungeon.player.orbs.size();
      if (Settings.FAST_MODE) {
         speedTime = 0.0F;
      }

      AbstractDungeon.actionManager.addToBottom(new VFXAction(new OrbFlareEffect(this, OrbFlareEffect.OrbFlareColor.DARK), speedTime));
      this.evokeAmount = this.evokeAmount + this.passiveAmount;
      this.updateDescription();
   }

   @Override
   public void triggerEvokeAnimation() {
      CardCrawlGame.sound.play("ORB_DARK_EVOKE", 0.1F);
      AbstractDungeon.effectsQueue.add(new DarkOrbActivateEffect(this.cX, this.cY));
   }

   @Override
   public void applyFocus() {
      AbstractPower power = AbstractDungeon.player.getPower("Focus");
      if (power != null) {
         this.passiveAmount = Math.max(0, this.basePassiveAmount + power.amount);
      } else {
         this.passiveAmount = this.basePassiveAmount;
      }
   }

   @Override
   public void updateAnimation() {
      super.updateAnimation();
      this.angle = this.angle + Gdx.graphics.getDeltaTime() * 120.0F;
      this.vfxTimer = this.vfxTimer - Gdx.graphics.getDeltaTime();
      if (this.vfxTimer < 0.0F) {
         AbstractDungeon.effectList.add(new DarkOrbPassiveEffect(this.cX, this.cY));
         this.vfxTimer = 0.25F;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.c);
      sb.draw(
         this.img,
         this.cX - 48.0F,
         this.cY - 48.0F + this.bobEffect.y,
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
      this.shineColor.a = this.c.a / 3.0F;
      sb.setColor(this.shineColor);
      sb.setBlendFunction(770, 1);
      sb.draw(
         this.img,
         this.cX - 48.0F,
         this.cY - 48.0F + this.bobEffect.y,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale * 1.2F,
         this.scale * 1.2F,
         this.angle / 1.2F,
         0,
         0,
         96,
         96,
         false,
         false
      );
      sb.draw(
         this.img,
         this.cX - 48.0F,
         this.cY - 48.0F + this.bobEffect.y,
         48.0F,
         48.0F,
         96.0F,
         96.0F,
         this.scale * 1.5F,
         this.scale * 1.5F,
         this.angle / 1.4F,
         0,
         0,
         96,
         96,
         false,
         false
      );
      sb.setBlendFunction(770, 771);
      this.renderText(sb);
      this.hb.render(sb);
   }

   @Override
   protected void renderText(SpriteBatch sb) {
      FontHelper.renderFontCentered(
         sb,
         FontHelper.cardEnergyFont_L,
         Integer.toString(this.evokeAmount),
         this.cX + NUM_X_OFFSET,
         this.cY + this.bobEffect.y / 2.0F + NUM_Y_OFFSET - 4.0F * Settings.scale,
         new Color(0.2F, 1.0F, 1.0F, this.c.a),
         this.fontScale
      );
      FontHelper.renderFontCentered(
         sb,
         FontHelper.cardEnergyFont_L,
         Integer.toString(this.passiveAmount),
         this.cX + NUM_X_OFFSET,
         this.cY + this.bobEffect.y / 2.0F + NUM_Y_OFFSET + 20.0F * Settings.scale,
         this.c,
         this.fontScale
      );
   }

   @Override
   public void playChannelSFX() {
      CardCrawlGame.sound.play("ORB_DARK_CHANNEL", 0.1F);
   }

   @Override
   public AbstractOrb makeCopy() {
      return new Dark();
   }
}
