package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer;

public abstract class InfluencerPanel<T extends Influencer> extends EditorPanel<T> {
   public InfluencerPanel(FlameMain editor, T influencer, String name, String description) {
      super(editor, name, description, true, true);
      this.setValue(influencer);
   }

   public InfluencerPanel(FlameMain editor, T influencer, String name, String description, boolean isAlwaysActive, boolean isRemovable) {
      super(editor, name, description, isAlwaysActive, isRemovable);
      this.setValue(influencer);
   }

   @Override
   protected void removePanel() {
      super.removePanel();
      this.editor.getEmitter().influencers.removeValue(this.value, true);
      this.editor.getEmitter().init();
      this.editor.getEmitter().start();
      this.editor.reloadRows();
   }
}
