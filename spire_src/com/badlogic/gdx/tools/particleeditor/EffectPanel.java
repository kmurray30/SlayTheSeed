package com.badlogic.gdx.tools.particleeditor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.StreamUtils;
import java.awt.FileDialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URI;
import javax.swing.JButton;
import javax.swing.JOptionPane;
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
   ParticleEditor editor;
   JTable emitterTable;
   DefaultTableModel emitterTableModel;
   int editIndex;
   String lastDir;

   public EffectPanel(ParticleEditor editor) {
      this.editor = editor;
      this.initializeComponents();
   }

   public ParticleEmitter newEmitter(String name, boolean select) {
      ParticleEmitter emitter = new ParticleEmitter();
      emitter.getDuration().setLow(1000.0F);
      emitter.getEmission().setHigh(50.0F);
      emitter.getLife().setHigh(500.0F);
      emitter.getScale().setHigh(32.0F, 32.0F);
      emitter.getTint().setColors(new float[]{1.0F, 0.12156863F, 0.047058824F});
      emitter.getTransparency().setHigh(1.0F);
      emitter.setMaxParticleCount(25);
      emitter.setImagePath("particle.png");
      this.addEmitter(name, select, emitter);
      return emitter;
   }

   public ParticleEmitter newExampleEmitter(String name, boolean select) {
      ParticleEmitter emitter = new ParticleEmitter();
      emitter.getDuration().setLow(3000.0F);
      emitter.getEmission().setHigh(250.0F);
      emitter.getLife().setHigh(500.0F, 1000.0F);
      emitter.getLife().setTimeline(new float[]{0.0F, 0.66F, 1.0F});
      emitter.getLife().setScaling(new float[]{1.0F, 1.0F, 0.3F});
      emitter.getScale().setHigh(32.0F, 32.0F);
      emitter.getRotation().setLow(1.0F, 360.0F);
      emitter.getRotation().setHigh(180.0F, 180.0F);
      emitter.getRotation().setTimeline(new float[]{0.0F, 1.0F});
      emitter.getRotation().setScaling(new float[]{0.0F, 1.0F});
      emitter.getRotation().setRelative(true);
      emitter.getAngle().setHigh(45.0F, 135.0F);
      emitter.getAngle().setLow(90.0F);
      emitter.getAngle().setTimeline(new float[]{0.0F, 0.5F, 1.0F});
      emitter.getAngle().setScaling(new float[]{1.0F, 0.0F, 0.0F});
      emitter.getAngle().setActive(true);
      emitter.getVelocity().setHigh(30.0F, 300.0F);
      emitter.getVelocity().setActive(true);
      emitter.getTint().setColors(new float[]{1.0F, 0.12156863F, 0.047058824F});
      emitter.getTransparency().setHigh(1.0F, 1.0F);
      emitter.getTransparency().setTimeline(new float[]{0.0F, 0.2F, 0.8F, 1.0F});
      emitter.getTransparency().setScaling(new float[]{0.0F, 1.0F, 0.75F, 0.0F});
      emitter.setMaxParticleCount(200);
      emitter.setImagePath("particle.png");
      this.addEmitter(name, select, emitter);
      return emitter;
   }

   private void addEmitter(String name, boolean select, ParticleEmitter emitter) {
      Array<ParticleEmitter> emitters = this.editor.effect.getEmitters();
      if (emitters.size == 0) {
         emitter.setPosition(this.editor.worldCamera.viewportWidth / 2.0F, this.editor.worldCamera.viewportHeight / 2.0F);
      } else {
         ParticleEmitter p = emitters.get(0);
         emitter.setPosition(p.getX(), p.getY());
      }

      emitters.add(emitter);
      this.emitterTableModel.addRow(new Object[]{name, true});
      if (select) {
         this.editor.reloadRows();
         int row = this.emitterTableModel.getRowCount() - 1;
         this.emitterTable.getSelectionModel().setSelectionInterval(row, row);
      }
   }

   void emitterSelected() {
      int row = this.emitterTable.getSelectedRow();
      if (row == -1) {
         row = this.editIndex;
         this.emitterTable.getSelectionModel().setSelectionInterval(row, row);
      }

      if (row != this.editIndex) {
         this.editIndex = row;
         this.editor.reloadRows();
      }
   }

   void openEffect(boolean mergeIntoCurrent) {
      FileDialog dialog = new FileDialog(this.editor, "Open Effect", 0);
      if (this.lastDir != null) {
         dialog.setDirectory(this.lastDir);
      }

      dialog.setVisible(true);
      String file = dialog.getFile();
      String dir = dialog.getDirectory();
      if (dir != null && file != null && file.trim().length() != 0) {
         this.lastDir = dir;
         ParticleEffect effect = new ParticleEffect();

         try {
            File effectFile = new File(dir, file);
            effect.loadEmitters(Gdx.files.absolute(effectFile.getAbsolutePath()));
            if (mergeIntoCurrent) {
               for (ParticleEmitter emitter : effect.getEmitters()) {
                  this.addEmitter(emitter.getName(), false, emitter);
               }
            } else {
               this.editor.effect = effect;
               this.editor.effectFile = effectFile;
            }

            this.emitterTableModel.getDataVector().removeAllElements();
            this.editor.particleData.clear();
         } catch (Exception var9) {
            System.out.println("Error loading effect: " + new File(dir, file).getAbsolutePath());
            var9.printStackTrace();
            JOptionPane.showMessageDialog(this.editor, "Error opening effect.");
            return;
         }

         for (ParticleEmitter emitter : this.editor.effect.getEmitters()) {
            emitter.setPosition(this.editor.worldCamera.viewportWidth / 2.0F, this.editor.worldCamera.viewportHeight / 2.0F);
            this.emitterTableModel.addRow(new Object[]{emitter.getName(), true});
         }

         this.editIndex = 0;
         this.emitterTable.getSelectionModel().setSelectionInterval(this.editIndex, this.editIndex);
         this.editor.reloadRows();
      }
   }

   void saveEffect() {
      FileDialog dialog = new FileDialog(this.editor, "Save Effect", 1);
      if (this.lastDir != null) {
         dialog.setDirectory(this.lastDir);
      }

      dialog.setVisible(true);
      String file = dialog.getFile();
      String dir = dialog.getDirectory();
      if (dir != null && file != null && file.trim().length() != 0) {
         this.lastDir = dir;
         int index = 0;
         File effectFile = new File(dir, file);
         URI effectDirUri = effectFile.getParentFile().toURI();

         for (ParticleEmitter emitter : this.editor.effect.getEmitters()) {
            emitter.setName((String)this.emitterTableModel.getValueAt(index++, 0));
            String imagePath = emitter.getImagePath();
            if ((imagePath.contains("/") || imagePath.contains("\\")) && !imagePath.contains("..")) {
               URI imageUri = new File(emitter.getImagePath()).toURI();
               emitter.setImagePath(effectDirUri.relativize(imageUri).getPath());
            }
         }

         File outputFile = new File(dir, file);
         Writer fileWriter = null;

         try {
            fileWriter = new FileWriter(outputFile);
            this.editor.effect.save(fileWriter);
         } catch (Exception var14) {
            System.out.println("Error saving effect: " + outputFile.getAbsolutePath());
            var14.printStackTrace();
            JOptionPane.showMessageDialog(this.editor, "Error saving effect.");
         } finally {
            StreamUtils.closeQuietly(fileWriter);
         }
      }
   }

   void duplicateEmitter() {
      int row = this.emitterTable.getSelectedRow();
      if (row != -1) {
         String name = (String)this.emitterTableModel.getValueAt(row, 0);
         this.addEmitter(name, true, new ParticleEmitter(this.editor.effect.getEmitters().get(row)));
      }
   }

   void deleteEmitter() {
      if (this.editor.effect.getEmitters().size != 1) {
         int row = this.emitterTable.getSelectedRow();
         if (row != -1) {
            if (row <= this.editIndex) {
               int oldEditIndex = this.editIndex;
               this.editIndex = Math.max(0, this.editIndex - 1);
               if (oldEditIndex == row) {
                  this.editor.reloadRows();
               }
            }

            this.editor.effect.getEmitters().removeIndex(row);
            this.emitterTableModel.removeRow(row);
            this.emitterTable.getSelectionModel().setSelectionInterval(this.editIndex, this.editIndex);
         }
      }
   }

   void move(int direction) {
      if (direction >= 0 || this.editIndex != 0) {
         Array<ParticleEmitter> emitters = this.editor.effect.getEmitters();
         if (direction <= 0 || this.editIndex != emitters.size - 1) {
            int insertIndex = this.editIndex + direction;
            Object name = this.emitterTableModel.getValueAt(this.editIndex, 0);
            this.emitterTableModel.removeRow(this.editIndex);
            ParticleEmitter emitter = emitters.removeIndex(this.editIndex);
            this.emitterTableModel.insertRow(insertIndex, new Object[]{name});
            emitters.insert(insertIndex, emitter);
            this.editIndex = insertIndex;
            this.emitterTable.getSelectionModel().setSelectionInterval(this.editIndex, this.editIndex);
         }
      }
   }

   void emitterChecked(int index, boolean checked) {
      this.editor.setEnabled(this.editor.effect.getEmitters().get(index), checked);
      this.editor.effect.start();
   }

   private void initializeComponents() {
      this.setLayout(new GridBagLayout());
      JPanel sideButtons = new JPanel(new GridBagLayout());
      this.add(sideButtons, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, 10, 1, new Insets(0, 0, 0, 0), 0, 0));
      JButton newButton = new JButton("New");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.newEmitter("Untitled", true);
         }
      });
      newButton = new JButton("Duplicate");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.duplicateEmitter();
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
            EffectPanel.this.openEffect(false);
         }
      });
      newButton = new JButton("Merge");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.openEffect(true);
         }
      });
      newButton = new JButton("Up");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 1.0, 15, 2, new Insets(0, 0, 6, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.move(-1);
         }
      });
      newButton = new JButton("Down");
      sideButtons.add(newButton, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 0, 0), 0, 0));
      newButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            EffectPanel.this.move(1);
         }
      });
      JScrollPane scroll = new JScrollPane();
      this.add(scroll, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, 10, 1, new Insets(0, 0, 0, 6), 0, 0));
      this.emitterTable = new JTable() {
         @Override
         public Class getColumnClass(int column) {
            return column == 1 ? Boolean.class : super.getColumnClass(column);
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
   }
}
