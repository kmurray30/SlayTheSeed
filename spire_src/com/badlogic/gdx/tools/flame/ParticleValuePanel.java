package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue;

public class ParticleValuePanel<T extends ParticleValue> extends EditorPanel<T> {
   public ParticleValuePanel(FlameMain editor, String name, String description) {
      this(editor, name, description, true);
   }

   public ParticleValuePanel(FlameMain editor, String name, String description, boolean isAlwaysActive) {
      this(editor, name, description, isAlwaysActive, false);
   }

   public ParticleValuePanel(FlameMain editor, String name, String description, boolean isAlwaysActive, boolean isRemovable) {
      super(editor, name, description, isAlwaysActive, isRemovable);
   }

   @Override
   public void setHasAdvanced(boolean hasAdvanced) {
      super.setHasAdvanced(hasAdvanced);
      this.advancedButton.setVisible(hasAdvanced && (this.value.isActive() || this.isAlwaysActive));
   }

   public void setValue(T value) {
      super.setValue(value);
      if (value != null) {
         this.activeButton.setSelected(value.isActive());
      }
   }

   @Override
   protected void activate() {
      super.activate();
      if (this.value != null) {
         this.value.setActive(this.activeButton.isSelected());
      }
   }
}
