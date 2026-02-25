package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.ParticleController;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter;
import com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier;
import com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerFinalizerInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.ScaleInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.influencers.SpawnInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.renderers.BillboardRenderer;
import com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceRenderer;
import com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerControllerRenderer;
import com.badlogic.gdx.graphics.g3d.particles.renderers.PointSpriteRenderer;
import com.badlogic.gdx.graphics.g3d.particles.values.EllipseSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.PointSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;

class EffectPanel extends JPanel {
   FlameMain editor;
   JTable emitterTable;
   DefaultTableModel emitterTableModel;
   int editIndex = -1;
   String lastDir;
   JComboBox controllerTypeCombo;

   public EffectPanel(FlameMain editor) {
      this.editor = editor;
      this.initializeComponents();
   }

   public <T extends ParticleController> T createDefaultEmitter(FlameMain.ControllerType type, boolean select, boolean add) {
      T controller = null;
      if (type == FlameMain.ControllerType.Billboard) {
         controller = (T)this.createDefaultBillboardController();
      } else if (type == FlameMain.ControllerType.ModelInstance) {
         controller = (T)this.createDefaultModelInstanceController();
      } else if (type == FlameMain.ControllerType.PointSprite) {
         controller = (T)this.createDefaultPointController();
      } else if (type == FlameMain.ControllerType.ParticleController) {
         controller = (T)this.createDefaultParticleController();
      }

      if (add) {
         controller.init();
         this.addEmitter(controller, select);
      }

      return controller;
   }

   private ParticleController createDefaultModelInstanceController() {
      RegularEmitter emitter = new RegularEmitter();
      emitter.getDuration().setLow(3000.0F);
      emitter.getEmission().setHigh(80.0F);
      emitter.getLife().setHigh(500.0F, 1000.0F);
      emitter.getLife().setTimeline(new float[]{0.0F, 0.66F, 1.0F});
      emitter.getLife().setScaling(new float[]{1.0F, 1.0F, 0.3F});
      emitter.setMaxParticleCount(100);
      ColorInfluencer.Random colorInfluencer = new ColorInfluencer.Random();
      EllipseSpawnShapeValue spawnShapeValue = new EllipseSpawnShapeValue();
      spawnShapeValue.setDimensions(1.0F, 1.0F, 1.0F);
      SpawnInfluencer spawnSource = new SpawnInfluencer(spawnShapeValue);
      DynamicsInfluencer velocityInfluencer = new DynamicsInfluencer();
      DynamicsModifier.CentripetalAcceleration velocityValue = new DynamicsModifier.CentripetalAcceleration();
      velocityValue.strengthValue.setHigh(5.0F, 11.0F);
      velocityValue.strengthValue.setActive(true);
      velocityInfluencer.velocities.add(velocityValue);
      return new ParticleController(
         "ModelInstance Controller",
         emitter,
         new ModelInstanceRenderer(this.editor.getModelInstanceParticleBatch()),
         new ModelInfluencer.Single(this.editor.assetManager.get("monkey.g3db")),
         spawnSource,
         colorInfluencer,
         velocityInfluencer
      );
   }

   private ParticleController createDefaultBillboardController() {
      RegularEmitter emitter = new RegularEmitter();
      emitter.getDuration().setLow(3000.0F);
      emitter.getEmission().setHigh(250.0F);
      emitter.getLife().setHigh(500.0F, 1000.0F);
      emitter.getLife().setTimeline(new float[]{0.0F, 0.66F, 1.0F});
      emitter.getLife().setScaling(new float[]{1.0F, 1.0F, 0.3F});
      emitter.setMaxParticleCount(200);
      PointSpawnShapeValue pointSpawnShapeValue = new PointSpawnShapeValue();
      SpawnInfluencer spawnSource = new SpawnInfluencer(pointSpawnShapeValue);
      ColorInfluencer.Single colorInfluencer = new ColorInfluencer.Single();
      colorInfluencer.colorValue.setColors(new float[]{1.0F, 0.12156863F, 0.047058824F, 0.0F, 0.0F, 0.0F});
      colorInfluencer.colorValue.setTimeline(new float[]{0.0F, 1.0F});
      colorInfluencer.alphaValue.setHigh(1.0F);
      colorInfluencer.alphaValue.setTimeline(new float[]{0.0F, 0.5F, 0.8F, 1.0F});
      colorInfluencer.alphaValue.setScaling(new float[]{0.0F, 0.15F, 0.5F, 0.0F});
      DynamicsInfluencer velocityInfluencer = new DynamicsInfluencer();
      DynamicsModifier.PolarAcceleration velocityValue = new DynamicsModifier.PolarAcceleration();
      velocityValue.phiValue.setHigh(-35.0F, 35.0F);
      velocityValue.phiValue.setActive(true);
      velocityValue.phiValue.setTimeline(new float[]{0.0F, 0.5F, 1.0F});
      velocityValue.phiValue.setScaling(new float[]{1.0F, 0.0F, 0.0F});
      velocityValue.thetaValue.setHigh(0.0F, 360.0F);
      velocityValue.strengthValue.setHigh(5.0F, 10.0F);
      velocityInfluencer.velocities.add(velocityValue);
      return new ParticleController(
         "Billboard Controller",
         emitter,
         new BillboardRenderer(this.editor.getBillboardBatch()),
         new RegionInfluencer.Single(this.editor.getTexture()),
         spawnSource,
         colorInfluencer,
         velocityInfluencer
      );
   }

   private ParticleController createDefaultPointController() {
      RegularEmitter emitter = new RegularEmitter();
      emitter.getDuration().setLow(3000.0F);
      emitter.getEmission().setHigh(250.0F);
      emitter.getLife().setHigh(500.0F, 1000.0F);
      emitter.getLife().setTimeline(new float[]{0.0F, 0.66F, 1.0F});
      emitter.getLife().setScaling(new float[]{1.0F, 1.0F, 0.3F});
      emitter.setMaxParticleCount(200);
      ScaleInfluencer scaleInfluencer = new ScaleInfluencer();
      scaleInfluencer.value.setHigh(1.0F);
      ColorInfluencer.Single colorInfluencer = new ColorInfluencer.Single();
      colorInfluencer.colorValue.setColors(new float[]{0.12156863F, 0.047058824F, 1.0F, 0.0F, 0.0F, 0.0F});
      colorInfluencer.colorValue.setTimeline(new float[]{0.0F, 1.0F});
      colorInfluencer.alphaValue.setHigh(1.0F);
      colorInfluencer.alphaValue.setTimeline(new float[]{0.0F, 0.5F, 0.8F, 1.0F});
      colorInfluencer.alphaValue.setScaling(new float[]{0.0F, 0.15F, 0.5F, 0.0F});
      PointSpawnShapeValue pointSpawnShapeValue = new PointSpawnShapeValue();
      SpawnInfluencer spawnSource = new SpawnInfluencer(pointSpawnShapeValue);
      DynamicsInfluencer velocityInfluencer = new DynamicsInfluencer();
      DynamicsModifier.PolarAcceleration velocityValue = new DynamicsModifier.PolarAcceleration();
      velocityValue.phiValue.setHigh(-35.0F, 35.0F);
      velocityValue.phiValue.setActive(true);
      velocityValue.phiValue.setTimeline(new float[]{0.0F, 0.5F, 1.0F});
      velocityValue.phiValue.setScaling(new float[]{1.0F, 0.0F, 0.0F});
      velocityValue.thetaValue.setHigh(0.0F, 360.0F);
      velocityValue.strengthValue.setHigh(5.0F, 10.0F);
      return new ParticleController(
         "PointSprite Controller",
         emitter,
         new PointSpriteRenderer(this.editor.getPointSpriteBatch()),
         new RegionInfluencer.Single(this.editor.assetManager.get("pre_particle.png")),
         spawnSource,
         scaleInfluencer,
         colorInfluencer,
         velocityInfluencer
      );
   }

   private ParticleController createDefaultParticleController() {
      RegularEmitter emitter = new RegularEmitter();
      emitter.getDuration().setLow(3000.0F);
      emitter.getEmission().setHigh(90.0F);
      emitter.getLife().setHigh(3000.0F);
      emitter.setMaxParticleCount(100);
      EllipseSpawnShapeValue pointSpawnShapeValue = new EllipseSpawnShapeValue();
      pointSpawnShapeValue.setDimensions(1.0F, 1.0F, 1.0F);
      pointSpawnShapeValue.setSide(PrimitiveSpawnShapeValue.SpawnSide.top);
      SpawnInfluencer spawnSource = new SpawnInfluencer(pointSpawnShapeValue);
      ScaleInfluencer scaleInfluencer = new ScaleInfluencer();
      scaleInfluencer.value.setHigh(1.0F);
      scaleInfluencer.value.setLow(0.0F);
      scaleInfluencer.value.setTimeline(new float[]{0.0F, 1.0F});
      scaleInfluencer.value.setScaling(new float[]{1.0F, 0.0F});
      DynamicsInfluencer velocityInfluencer = new DynamicsInfluencer();
      DynamicsModifier.CentripetalAcceleration velocityValue = new DynamicsModifier.CentripetalAcceleration();
      velocityValue.strengthValue.setHigh(5.0F, 10.0F);
      velocityValue.strengthValue.setActive(true);
      velocityInfluencer.velocities.add(velocityValue);
      return new ParticleController(
         "ParticleController Controller",
         emitter,
         new ParticleControllerControllerRenderer(),
         new ParticleControllerInfluencer.Single(this.editor.assetManager.get("defaultTemplate.pfx", ParticleEffect.class).getControllers().get(0)),
         spawnSource,
         scaleInfluencer,
         velocityInfluencer,
         new ParticleControllerFinalizerInfluencer()
      );
   }

   public ParticleController createDefaultTemplateController() {
      RegularEmitter emitter = new RegularEmitter();
      emitter.getDuration().setLow(3000.0F);
      emitter.getEmission().setHigh(90.0F);
      emitter.getLife().setHigh(1000.0F);
      emitter.getLife().setTimeline(new float[]{0.0F, 0.66F, 1.0F});
      emitter.getLife().setScaling(new float[]{1.0F, 1.0F, 0.3F});
      emitter.setMaxParticleCount(100);
      PointSpawnShapeValue pointSpawnShapeValue = new PointSpawnShapeValue();
      pointSpawnShapeValue.xOffsetValue.setLow(0.0F, 1.0F);
      pointSpawnShapeValue.xOffsetValue.setActive(true);
      pointSpawnShapeValue.yOffsetValue.setLow(0.0F, 1.0F);
      pointSpawnShapeValue.yOffsetValue.setActive(true);
      pointSpawnShapeValue.zOffsetValue.setLow(0.0F, 1.0F);
      pointSpawnShapeValue.zOffsetValue.setActive(true);
      SpawnInfluencer spawnSource = new SpawnInfluencer(pointSpawnShapeValue);
      ScaleInfluencer scaleInfluencer = new ScaleInfluencer();
      scaleInfluencer.value.setHigh(1.0F);
      ColorInfluencer.Single colorInfluencer = new ColorInfluencer.Single();
      colorInfluencer.colorValue.setColors(new float[]{1.0F, 0.12156863F, 0.047058824F, 0.0F, 0.0F, 0.0F});
      colorInfluencer.colorValue.setTimeline(new float[]{0.0F, 1.0F});
      colorInfluencer.alphaValue.setHigh(1.0F);
      colorInfluencer.alphaValue.setTimeline(new float[]{0.0F, 0.5F, 0.8F, 1.0F});
      colorInfluencer.alphaValue.setScaling(new float[]{0.0F, 0.15F, 0.5F, 0.0F});
      return new ParticleController(
         "Billboard Controller",
         emitter,
         new BillboardRenderer(this.editor.getBillboardBatch()),
         new RegionInfluencer.Single(this.editor.getTexture()),
         spawnSource,
         scaleInfluencer,
         colorInfluencer
      );
   }

   private void addEmitter(ParticleController emitter, boolean select) {
      this.editor.addEmitter(emitter);
      this.emitterTableModel.addRow(new Object[]{emitter.name, true});
      int row = this.emitterTableModel.getRowCount() - 1;
      this.emitterChecked(row, true);
      if (select) {
         this.emitterTable.getSelectionModel().setSelectionInterval(row, row);
      }
   }

   void emitterSelected() {
      int row = this.emitterTable.getSelectedRow();
      if (row != this.editIndex) {
         this.editIndex = row;
         this.editor.reloadRows();
      }
   }

   void emitterChecked(int index, boolean checked) {
      this.editor.setEnabled(index, checked);
   }

   void openEffect() {
      File file = this.editor.showFileLoadDialog();
      if (file != null && this.editor.openEffect(file, true) != null) {
         this.emitterTableModel.getDataVector().removeAllElements();

         for (FlameMain.ControllerData data : this.editor.controllersData) {
            this.emitterTableModel.addRow(new Object[]{data.controller.name, true});
         }

         this.editIndex = 0;
         this.emitterTable.getSelectionModel().setSelectionInterval(this.editIndex, this.editIndex);
      }
   }

   protected void importEffect() {
      File file = this.editor.showFileLoadDialog();
      ParticleEffect effect;
      if (file != null && (effect = this.editor.openEffect(file, false)) != null) {
         for (ParticleController controller : effect.getControllers()) {
            this.addEmitter(controller, false);
         }

         this.editIndex = 0;
         this.emitterTable.getSelectionModel().setSelectionInterval(this.editIndex, this.editIndex);
      }
   }

   void saveEffect() {
      File file = this.editor.showFileSaveDialog();
      if (file != null) {
         int index = 0;

         for (FlameMain.ControllerData data : this.editor.controllersData) {
            data.controller.name = (String)this.emitterTableModel.getValueAt(index++, 0);
         }

         this.editor.saveEffect(file);
      }
   }

   void deleteEmitter() {
      int row = this.emitterTable.getSelectedRow();
      if (row != -1) {
         int newIndex = Math.min(this.editIndex, this.emitterTableModel.getRowCount() - 2);
         this.editor.removeEmitter(row);
         this.emitterTableModel.removeRow(row);
         this.emitterTable.getSelectionModel().setSelectionInterval(newIndex, newIndex);
      }
   }

   protected void cloneEmitter() {
      int row = this.emitterTable.getSelectedRow();
      if (row != -1) {
         ParticleController controller = this.editor.controllersData.get(row).controller.copy();
         controller.init();
         controller.name = controller.name + " Clone";
         this.addEmitter(controller, true);
      }
   }

   void move(int direction) {
   }

   private void initializeComponents() {
      this.setLayout(new GridBagLayout());
      JScrollPane scroll = new JScrollPane();
      this.add(scroll, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, 10, 1, new Insets(0, 0, 0, 6), 0, 0));
      this.emitterTable = new JTable() {
         @Override
         public Class getColumnClass(int column) {
            return column == 1 ? Boolean.class : super.getColumnClass(column);
         }

         @Override
         public Dimension getPreferredScrollableViewportSize() {
            Dimension dim = super.getPreferredScrollableViewportSize();
            dim.height = this.getPreferredSize().height;
            return dim;
         }
      };
      this.emitterTable.getTableHeader().setReorderingAllowed(false);
      this.emitterTable.setSelectionMode(0);
      scroll.setViewportView(this.emitterTable);
      this.emitterTableModel = new DefaultTableModel(new String[0][0], new String[]{"Emitter", ""});
      this.emitterTable.setModel(this.emitterTableModel);
      this.emitterTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent event) {
            if (!event.getValueIsAdjusting()) {
               EffectPanel.this.emitterSelected();
            }
         }
      });
      this.emitterTableModel.addTableModelListener(new TableModelListener() {
         @Override
         public void tableChanged(TableModelEvent event) {
            if (event.getColumn() == 1) {
               EffectPanel.this.emitterChecked(event.getFirstRow(), (Boolean)EffectPanel.this.emitterTable.getValueAt(event.getFirstRow(), 1));
            }
         }
      });
      JPanel sideButtons = new JPanel(new GridBagLayout());
      this.add(sideButtons, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, 18, 1, new Insets(0, 0, 0, 0), 0, 0));
      this.controllerTypeCombo = new JComboBox();
      this.controllerTypeCombo.setModel(new DefaultComboBoxModel<>(FlameMain.ControllerType.values()));
      sideButtons.add(this.controllerTypeCombo, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      JButton newButton = new JButton("New");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            FlameMain.ControllerType item = (FlameMain.ControllerType)EffectPanel.this.controllerTypeCombo.getSelectedItem();
            EffectPanel.this.createDefaultEmitter(item, true, true);
         }
      });
      newButton = new JButton("Delete");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.deleteEmitter();
         }
      });
      newButton = new JButton("Clone");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.cloneEmitter();
         }
      });
      sideButtons.add(new JSeparator(0), new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton = new JButton("Save");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.saveEffect();
         }
      });
      newButton = new JButton("Open");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.openEffect();
         }
      });
      newButton = new JButton("Import");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.importEffect();
         }
      });
   }
}
