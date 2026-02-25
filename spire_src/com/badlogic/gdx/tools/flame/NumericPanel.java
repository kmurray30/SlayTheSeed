package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.values.NumericValue;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class NumericPanel extends ParticleValuePanel<NumericValue> {
   JSpinner valueSpinner;

   public NumericPanel(FlameMain editor, NumericValue value, String name, String description) {
      super(editor, name, description);
      this.setValue(value);
   }

   public void setValue(NumericValue value) {
      super.setValue(value);
      if (value != null) {
         setValue(this.valueSpinner, Float.valueOf(value.getValue()));
      }
   }

   @Override
   protected void initializeComponents() {
      super.initializeComponents();
      JPanel contentPanel = this.getContentPanel();
      JLabel label = new JLabel("Value:");
      contentPanel.add(label, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, 13, 0, new Insets(0, 0, 0, 6), 0, 0));
      this.valueSpinner = new JSpinner(new SpinnerNumberModel(new Float(0.0F), new Float(-99999.0F), new Float(99999.0F), new Float(0.1F)));
      contentPanel.add(this.valueSpinner, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.valueSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            NumericPanel.this.value.setValue((Float)NumericPanel.this.valueSpinner.getValue());
         }
      });
   }
}
