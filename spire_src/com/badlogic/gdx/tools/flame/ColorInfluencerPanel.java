package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer;

public class ColorInfluencerPanel extends InfluencerPanel<ColorInfluencer.Single> {
   GradientPanel tintPanel;
   PercentagePanel alphaPanel;

   public ColorInfluencerPanel(FlameMain particleEditor3D, ColorInfluencer.Single influencer) {
      super(particleEditor3D, influencer, "Color Influencer", "Sets the particle color.");
      this.initializeComponents(influencer);
      this.setValue(influencer);
   }

   private void initializeComponents(ColorInfluencer.Single emitter) {
      int i = 0;
      this.addContent(i++, 0, this.tintPanel = new GradientPanel(this.editor, emitter.colorValue, "Tint", "", false));
      this.addContent(i++, 0, this.alphaPanel = new PercentagePanel(this.editor, emitter.alphaValue, "Life", "Transparency", ""));
      this.tintPanel.showContent(true);
      this.alphaPanel.showContent(true);
   }
}
