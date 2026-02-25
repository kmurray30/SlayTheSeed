package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.influencers.ScaleInfluencer;

public class ScaleInfluencerPanel extends InfluencerPanel<ScaleInfluencer> {
   ScaledNumericPanel scalePanel;

   public ScaleInfluencerPanel(FlameMain editor, ScaleInfluencer influencer) {
      super(editor, influencer, "Scale Influencer", "Particle scale, in world units.");
      this.setValue(influencer);
   }

   public void setValue(ScaleInfluencer value) {
      super.setValue(value);
      if (value != null) {
         this.scalePanel.setValue(value.value);
      }
   }

   @Override
   protected void initializeComponents() {
      super.initializeComponents();
      this.addContent(0, 0, this.scalePanel = new ScaledNumericPanel(this.editor, null, "Life", "", ""));
      this.scalePanel.setIsAlwayShown(true);
   }
}
