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

public class AngularVelocityPanel extends EditorPanel<DynamicsModifier.Angular> {
   JCheckBox isGlobalCheckBox;
   ScaledNumericPanel thetaPanel;
   ScaledNumericPanel phiPanel;
   ScaledNumericPanel magnitudePanel;

   public AngularVelocityPanel(FlameMain editor, DynamicsModifier.Angular aValue, String charTitle, String name, String description) {
      super(editor, name, description);
      this.initializeComponents(aValue, charTitle);
      this.setValue(this.value);
   }

   public void setValue(DynamicsModifier.Angular value) {
      super.setValue(value);
      if (value != null) {
         setValue(this.isGlobalCheckBox, this.value.isGlobal);
         this.magnitudePanel.setValue(this.value.strengthValue);
         this.thetaPanel.setValue(this.value.thetaValue);
         this.phiPanel.setValue(this.value.phiValue);
      }
   }

   private void initializeComponents(DynamicsModifier.Angular aValue, String charTitle) {
      JPanel contentPanel = this.getContentPanel();
      JPanel panel = new JPanel();
      panel.add(new JLabel("Global"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      panel.add(this.isGlobalCheckBox = new JCheckBox(), new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      contentPanel.add(panel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      contentPanel.add(
         this.magnitudePanel = new ScaledNumericPanel(
            this.editor, aValue == null ? null : aValue.strengthValue, charTitle, "Strength", "In world units per second.", true
         ),
         new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 6), 0, 0)
      );
      contentPanel.add(
         this.phiPanel = new ScaledNumericPanel(this.editor, aValue == null ? null : aValue.phiValue, charTitle, "Azimuth", "Rotation starting on Y", true),
         new GridBagConstraints(0, 4, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 6), 0, 0)
      );
      contentPanel.add(
         this.thetaPanel = new ScaledNumericPanel(
            this.editor, aValue == null ? null : aValue.thetaValue, charTitle, "Polar angle", "around Y axis on XZ plane", true
         ),
         new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 6), 0, 0)
      );
      panel = new JPanel();
      panel.setPreferredSize(new Dimension());
      contentPanel.add(panel, new GridBagConstraints(6, 0, 1, 1, 1.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.magnitudePanel.setIsAlwayShown(true);
      this.phiPanel.setIsAlwayShown(true);
      this.thetaPanel.setIsAlwayShown(true);
      this.isGlobalCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            AngularVelocityPanel.this.value.isGlobal = AngularVelocityPanel.this.isGlobalCheckBox.isSelected();
         }
      });
   }

   public ScaledNumericPanel getThetaPanel() {
      return this.thetaPanel;
   }

   public ScaledNumericPanel getPhiPanel() {
      return this.phiPanel;
   }

   public ScaledNumericPanel getMagnitudePanel() {
      return this.magnitudePanel;
   }
}
