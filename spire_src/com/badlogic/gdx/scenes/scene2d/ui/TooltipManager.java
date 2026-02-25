package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;

public class TooltipManager {
   private static TooltipManager instance;
   private static Files files;
   public float initialTime = 2.0F;
   public float subsequentTime = 0.0F;
   public float resetTime = 1.5F;
   public boolean enabled = true;
   public boolean animations = true;
   public float maxWidth = 2.1474836E9F;
   public float offsetX = 15.0F;
   public float offsetY = 19.0F;
   public float edgeDistance = 7.0F;
   final Array<Tooltip> shown = new Array<>();
   float time = this.initialTime;
   final Timer.Task resetTask = new Timer.Task() {
      @Override
      public void run() {
         TooltipManager.this.time = TooltipManager.this.initialTime;
      }
   };
   Tooltip showTooltip;
   final Timer.Task showTask = new Timer.Task() {
      @Override
      public void run() {
         if (TooltipManager.this.showTooltip != null) {
            Stage stage = TooltipManager.this.showTooltip.targetActor.getStage();
            if (stage != null) {
               stage.addActor(TooltipManager.this.showTooltip.container);
               TooltipManager.this.showTooltip.container.toFront();
               TooltipManager.this.shown.add(TooltipManager.this.showTooltip);
               TooltipManager.this.showTooltip.container.clearActions();
               TooltipManager.this.showAction(TooltipManager.this.showTooltip);
               if (!TooltipManager.this.showTooltip.instant) {
                  TooltipManager.this.time = TooltipManager.this.subsequentTime;
                  TooltipManager.this.resetTask.cancel();
               }
            }
         }
      }
   };

   public void touchDown(Tooltip tooltip) {
      this.showTask.cancel();
      if (tooltip.container.remove()) {
         this.resetTask.cancel();
      }

      this.resetTask.run();
      if (this.enabled || tooltip.always) {
         this.showTooltip = tooltip;
         Timer.schedule(this.showTask, this.time);
      }
   }

   public void enter(Tooltip tooltip) {
      this.showTooltip = tooltip;
      this.showTask.cancel();
      if (this.enabled || tooltip.always) {
         if (this.time != 0.0F && !tooltip.instant) {
            Timer.schedule(this.showTask, this.time);
         } else {
            this.showTask.run();
         }
      }
   }

   public void hide(Tooltip tooltip) {
      this.showTooltip = null;
      this.showTask.cancel();
      if (tooltip.container.hasParent()) {
         this.shown.removeValue(tooltip, true);
         this.hideAction(tooltip);
         this.resetTask.cancel();
         Timer.schedule(this.resetTask, this.resetTime);
      }
   }

   protected void showAction(Tooltip tooltip) {
      float actionTime = this.animations ? (this.time > 0.0F ? 0.5F : 0.15F) : 0.1F;
      tooltip.container.setTransform(true);
      tooltip.container.getColor().a = 0.2F;
      tooltip.container.setScale(0.05F);
      tooltip.container
         .addAction(Actions.parallel(Actions.fadeIn(actionTime, Interpolation.fade), Actions.scaleTo(1.0F, 1.0F, actionTime, Interpolation.fade)));
   }

   protected void hideAction(Tooltip tooltip) {
      tooltip.container
         .addAction(
            Actions.sequence(
               Actions.parallel(Actions.alpha(0.2F, 0.2F, Interpolation.fade), Actions.scaleTo(0.05F, 0.05F, 0.2F, Interpolation.fade)), Actions.removeActor()
            )
         );
   }

   public void hideAll() {
      this.resetTask.cancel();
      this.showTask.cancel();
      this.time = this.initialTime;
      this.showTooltip = null;

      for (Tooltip tooltip : this.shown) {
         tooltip.hide();
      }

      this.shown.clear();
   }

   public void instant() {
      this.time = 0.0F;
      this.showTask.run();
      this.showTask.cancel();
   }

   public static TooltipManager getInstance() {
      if (files == null || files != Gdx.files) {
         files = Gdx.files;
         instance = new TooltipManager();
      }

      return instance;
   }
}
