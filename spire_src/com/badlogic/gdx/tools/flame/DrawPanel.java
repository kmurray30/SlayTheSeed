package com.badlogic.gdx.tools.flame;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class DrawPanel extends EditorPanel {
   JCheckBox drawXYZCheckBox;
   JCheckBox drawXZPlaneBox;
   JCheckBox drawXYPlaneBox;

   public DrawPanel(FlameMain editor, String name, String description) {
      super(editor, name, description);
      this.setValue(null);
   }

   @Override
   protected void initializeComponents() {
      super.initializeComponents();
      JPanel contentPanel = this.getContentPanel();
      contentPanel.add(new JLabel("XYZ:"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, 13, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.drawXYZCheckBox = new JCheckBox();
      contentPanel.add(this.drawXYZCheckBox, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 6, 0, 0), 0, 0));
      contentPanel.add(new JLabel("XZ Plane:"), new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, 13, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.drawXZPlaneBox = new JCheckBox();
      contentPanel.add(this.drawXZPlaneBox, new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 6, 0, 0), 0, 0));
      contentPanel.add(new JLabel("XY Plane:"), new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0, 13, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.drawXYPlaneBox = new JCheckBox();
      contentPanel.add(this.drawXYPlaneBox, new GridBagConstraints(1, 3, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 6, 0, 0), 0, 0));
      this.drawXYZCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            DrawPanel.this.editor.getRenderer().setDrawXYZ(DrawPanel.this.drawXYZCheckBox.isSelected());
         }
      });
      this.drawXYZCheckBox.setSelected(this.editor.getRenderer().IsDrawXYZ());
      this.drawXZPlaneBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            DrawPanel.this.editor.getRenderer().setDrawXZPlane(DrawPanel.this.drawXZPlaneBox.isSelected());
         }
      });
      this.drawXZPlaneBox.setSelected(this.editor.getRenderer().IsDrawXZPlane());
      this.drawXYPlaneBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            DrawPanel.this.editor.getRenderer().setDrawXYPlane(DrawPanel.this.drawXYPlaneBox.isSelected());
         }
      });
   }
}
