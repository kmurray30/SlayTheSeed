package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class StrengthVelocityPanel extends EditorPanel<DynamicsModifier.Strength> {
   JCheckBox isGlobalCheckBox;
   ScaledNumericPanel magnitudePanel;

   public StrengthVelocityPanel(FlameMain editor, DynamicsModifier.Strength value, String charTitle, String name, String description) {
      super(editor, name, description);
      this.initializeComponents(charTitle);
      this.setValue(value);
   }

   public void setValue(DynamicsModifier.Strength value) {
      super.setValue(value);
      if (value != null) {
         setValue(this.isGlobalCheckBox, this.value.isGlobal);
         this.magnitudePanel.setValue(value.strengthValue);
      }
   }

   private void initializeComponents(String charTitle) {
      JPanel contentPanel = this.getContentPanel();
      JPanel panel = new JPanel();
      panel.add(new JLabel("Global"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      panel.add(this.isGlobalCheckBox = new JCheckBox(), new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      contentPanel.add(panel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      contentPanel.add(
         this.magnitudePanel = new ScaledNumericPanel(this.editor, null, charTitle, "Strength", "In world units per second.", true),
         new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 6), 0, 0)
      );
      panel = new JPanel();
      panel.setPreferredSize(new Dimension());
      contentPanel.add(panel, new GridBagConstraints(6, 0, 1, 1, 1.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.magnitudePanel.setIsAlwayShown(true);
      this.isGlobalCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            StrengthVelocityPanel.this.value.isGlobal = StrengthVelocityPanel.this.isGlobalCheckBox.isSelected();
         }
      });
   }
}
