package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.ParticleController;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class CountPanel extends EditorPanel {
   Slider maxSlider;
   Slider minSlider;

   public CountPanel(FlameMain editor, String name, String description, int min, int max) {
      super(editor, name, description);
      this.initializeComponents(min, max);
      this.setValue(null);
   }

   private void initializeComponents(int min, int max) {
      this.minSlider = new Slider(0.0F, 0.0F, 999999.0F, 1.0F);
      this.minSlider.setValue(min);
      this.minSlider.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            ParticleController controller = CountPanel.this.editor.getEmitter();
            controller.emitter.minParticleCount = (int)CountPanel.this.minSlider.getValue();
            CountPanel.this.editor.restart();
         }
      });
      this.maxSlider = new Slider(0.0F, 0.0F, 999999.0F, 1.0F);
      this.maxSlider.setValue(max);
      this.maxSlider.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            ParticleController controller = CountPanel.this.editor.getEmitter();
            controller.emitter.maxParticleCount = (int)CountPanel.this.maxSlider.getValue();
            CountPanel.this.editor.restart();
         }
      });
      int i = 0;
      this.contentPanel.add(new JLabel("Min"), new GridBagConstraints(0, i, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(this.minSlider, new GridBagConstraints(1, i++, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(new JLabel("Max"), new GridBagConstraints(0, i, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(this.maxSlider, new GridBagConstraints(1, i++, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
   }
}
