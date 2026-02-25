package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.ParticleShader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleSorter;
import com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class BillboardBatchPanel extends EditorPanel<BillboardParticleBatch> {
   JComboBox alignCombo;
   JCheckBox useGPUBox;
   JComboBox sortCombo;

   public BillboardBatchPanel(FlameMain particleEditor3D, BillboardParticleBatch renderer) {
      super(particleEditor3D, "Billboard Batch", "Renderer used to draw billboards particles.");
      this.initializeComponents(renderer);
      this.setValue(renderer);
   }

   private void initializeComponents(BillboardParticleBatch renderer) {
      this.alignCombo = new JComboBox();
      this.alignCombo.setModel(new DefaultComboBoxModel<>(BillboardBatchPanel.AlignModeWrapper.values()));
      this.alignCombo.setSelectedItem(this.getAlignModeWrapper(renderer.getAlignMode()));
      this.alignCombo.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            BillboardBatchPanel.AlignModeWrapper align = (BillboardBatchPanel.AlignModeWrapper)BillboardBatchPanel.this.alignCombo.getSelectedItem();
            BillboardBatchPanel.this.editor.getBillboardBatch().setAlignMode(align.mode);
         }
      });
      this.useGPUBox = new JCheckBox();
      this.useGPUBox.setSelected(renderer.isUseGPU());
      this.useGPUBox.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            BillboardBatchPanel.this.editor.getBillboardBatch().setUseGpu(BillboardBatchPanel.this.useGPUBox.isSelected());
         }
      });
      this.sortCombo = new JComboBox();
      this.sortCombo.setModel(new DefaultComboBoxModel<>(BillboardBatchPanel.SortMode.values()));
      this.sortCombo.setSelectedItem(this.getSortMode(renderer.getSorter()));
      this.sortCombo.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            BillboardBatchPanel.SortMode mode = (BillboardBatchPanel.SortMode)BillboardBatchPanel.this.sortCombo.getSelectedItem();
            BillboardBatchPanel.this.editor.getBillboardBatch().setSorter(mode.sorter);
         }
      });
      int i = 0;
      this.contentPanel.add(new JLabel("Align"), new GridBagConstraints(0, i, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(this.alignCombo, new GridBagConstraints(1, i++, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(new JLabel("Use GPU"), new GridBagConstraints(0, i, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(this.useGPUBox, new GridBagConstraints(1, i++, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(new JLabel("Sort"), new GridBagConstraints(0, i, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(this.sortCombo, new GridBagConstraints(1, i, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
   }

   private Object getSortMode(ParticleSorter sorter) {
      Class type = sorter.getClass();

      for (BillboardBatchPanel.SortMode wrapper : BillboardBatchPanel.SortMode.values()) {
         if (wrapper.sorter.getClass() == type) {
            return wrapper;
         }
      }

      return null;
   }

   private Object getAlignModeWrapper(ParticleShader.AlignMode alignMode) {
      for (BillboardBatchPanel.AlignModeWrapper wrapper : BillboardBatchPanel.AlignModeWrapper.values()) {
         if (wrapper.mode == alignMode) {
            return wrapper;
         }
      }

      return null;
   }

   private static enum AlignModeWrapper {
      Screen(ParticleShader.AlignMode.Screen, "Screen"),
      ViewPoint(ParticleShader.AlignMode.ViewPoint, "View Point");

      public String desc;
      public ParticleShader.AlignMode mode;

      private AlignModeWrapper(ParticleShader.AlignMode mode, String desc) {
         this.mode = mode;
         this.desc = desc;
      }

      @Override
      public String toString() {
         return this.desc;
      }
   }

   private static enum SortMode {
      None("None", new ParticleSorter.None()),
      Distance("Distance", new ParticleSorter.Distance());

      public String desc;
      public ParticleSorter sorter;

      private SortMode(String desc, ParticleSorter sorter) {
         this.sorter = sorter;
         this.desc = desc;
      }

      @Override
      public String toString() {
         return this.desc;
      }
   }
}
