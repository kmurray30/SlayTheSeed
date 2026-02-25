package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.particles.influencers.SpawnInfluencer;
import com.badlogic.gdx.graphics.g3d.particles.values.CylinderSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.EllipseSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.LineSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.PointSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.RectangleSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.UnweightedMeshSpawnShapeValue;
import com.badlogic.gdx.graphics.g3d.particles.values.WeightMeshSpawnShapeValue;
import com.badlogic.gdx.utils.Array;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

class SpawnInfluencerPanel extends InfluencerPanel<SpawnInfluencer> implements TemplatePickerPanel.Listener<Model> {
   private static final String SPAWN_SHAPE_POINT = "Point";
   private static final String SPAWN_SHAPE_LINE = "Line";
   private static final String SPAWN_SHAPE_RECTANGLE = "Rectangle";
   private static final String SPAWN_SHAPE_CYLINDER = "Cylinder";
   private static final String SPAWN_SHAPE_ELLIPSE = "Ellipse";
   private static final String SPAWN_SHAPE_MESH = "Unweighted Mesh";
   private static final String SPAWN_SHAPE_WEIGHT_MESH = "Weighted Mesh";
   private static String[] spawnShapes = new String[]{"Point", "Line", "Rectangle", "Ellipse", "Cylinder", "Unweighted Mesh", "Weighted Mesh"};
   JComboBox shapeCombo;
   JCheckBox edgesCheckbox;
   JLabel edgesLabel;
   JComboBox sideCombo;
   JLabel sideLabel;
   TemplatePickerPanel<Model> meshPanel;
   ScaledNumericPanel widthPanel;
   ScaledNumericPanel heightPanel;
   ScaledNumericPanel depthPanel;
   RangedNumericPanel xPanel;
   RangedNumericPanel yPanel;
   RangedNumericPanel zPanel;
   PointSpawnShapeValue pointSpawnShapeValue;
   LineSpawnShapeValue lineSpawnShapeValue;
   RectangleSpawnShapeValue rectangleSpawnShapeValue;
   EllipseSpawnShapeValue ellipseSpawnShapeValue;
   CylinderSpawnShapeValue cylinderSpawnShapeValue;
   UnweightedMeshSpawnShapeValue meshSpawnShapeValue;
   WeightMeshSpawnShapeValue weightMeshSpawnShapeValue;

   public SpawnInfluencerPanel(FlameMain editor, SpawnInfluencer influencer) {
      super(editor, influencer, "Spawn Influencer", "Define where the particles are spawned.", true, false);
      this.setValue(influencer);
      this.setCurrentSpawnData(influencer.spawnShapeValue);
      this.shapeCombo.setSelectedItem(this.spawnShapeToString(influencer.spawnShapeValue));
   }

   private void setCurrentSpawnData(SpawnShapeValue spawnShapeValue) {
      SpawnShapeValue local = null;
      if (spawnShapeValue instanceof PointSpawnShapeValue) {
         local = this.pointSpawnShapeValue;
      } else if (spawnShapeValue instanceof LineSpawnShapeValue) {
         local = this.lineSpawnShapeValue;
      } else if (spawnShapeValue instanceof RectangleSpawnShapeValue) {
         local = this.rectangleSpawnShapeValue;
      } else if (spawnShapeValue instanceof EllipseSpawnShapeValue) {
         local = this.ellipseSpawnShapeValue;
      } else if (spawnShapeValue instanceof CylinderSpawnShapeValue) {
         local = this.cylinderSpawnShapeValue;
      }

      if (spawnShapeValue instanceof UnweightedMeshSpawnShapeValue) {
         local = this.meshSpawnShapeValue;
      } else if (spawnShapeValue instanceof WeightMeshSpawnShapeValue) {
         local = this.weightMeshSpawnShapeValue;
      }

      local.load(spawnShapeValue);
   }

   protected void setSpawnShapeValue(SpawnShapeValue spawnShapeValue) {
      this.xPanel.setValue(spawnShapeValue.xOffsetValue);
      this.yPanel.setValue(spawnShapeValue.yOffsetValue);
      this.zPanel.setValue(spawnShapeValue.zOffsetValue);
   }

   protected void setPrimitiveSpawnShape(PrimitiveSpawnShapeValue shape, boolean showEdges, PrimitiveSpawnShapeValue.SpawnSide side) {
      this.setSpawnShapeValue(shape);
      SpawnInfluencer influencer = this.editor.getEmitter().findInfluencer(SpawnInfluencer.class);
      influencer.spawnShapeValue = shape;
      this.widthPanel.setValue(shape.getSpawnWidth());
      this.heightPanel.setValue(shape.getSpawnHeight());
      this.depthPanel.setValue(shape.getSpawnDepth());
      this.setEdgesVisible(showEdges);
      if (showEdges) {
         this.edgesCheckbox.setSelected(shape.isEdges());
      }

      if (side != null) {
         this.setSidesVisible(true);
         this.sideCombo.setSelectedItem(side);
      } else {
         this.setSidesVisible(false);
      }

      this.widthPanel.setVisible(true);
      this.heightPanel.setVisible(true);
      this.depthPanel.setVisible(true);
      this.meshPanel.setVisible(false);
   }

   protected void setMeshSpawnShape(SpawnShapeValue shape) {
      this.setSpawnShapeValue(shape);
      this.value.spawnShapeValue = shape;
      this.setEdgesVisible(false);
      this.setSidesVisible(false);
      this.widthPanel.setVisible(false);
      this.heightPanel.setVisible(false);
      this.depthPanel.setVisible(false);
      this.meshPanel.setVisible(true);
   }

   private Object spawnShapeToString(SpawnShapeValue spawnShapeValue) {
      if (spawnShapeValue instanceof PrimitiveSpawnShapeValue) {
         if (spawnShapeValue instanceof PointSpawnShapeValue) {
            return "Point";
         }

         if (spawnShapeValue instanceof LineSpawnShapeValue) {
            return "Line";
         }

         if (spawnShapeValue instanceof RectangleSpawnShapeValue) {
            return "Rectangle";
         }

         if (spawnShapeValue instanceof EllipseSpawnShapeValue) {
            return "Ellipse";
         }

         if (spawnShapeValue instanceof CylinderSpawnShapeValue) {
            return "Cylinder";
         }
      }

      if (spawnShapeValue instanceof WeightMeshSpawnShapeValue) {
         return "Weighted Mesh";
      } else {
         return spawnShapeValue instanceof UnweightedMeshSpawnShapeValue ? "Unweighted Mesh" : null;
      }
   }

   @Override
   public void update(FlameMain editor) {
      SpawnInfluencer influencer = editor.getEmitter().findInfluencer(SpawnInfluencer.class);
      this.shapeCombo.setSelectedItem(this.spawnShapeToString(influencer.spawnShapeValue));
   }

   void setEdgesVisible(boolean visible) {
      this.edgesCheckbox.setVisible(visible);
      this.edgesLabel.setVisible(visible);
   }

   void setSidesVisible(boolean visible) {
      this.sideCombo.setVisible(visible);
      this.sideLabel.setVisible(visible);
   }

   @Override
   protected void initializeComponents() {
      super.initializeComponents();
      this.pointSpawnShapeValue = new PointSpawnShapeValue();
      this.lineSpawnShapeValue = new LineSpawnShapeValue();
      this.rectangleSpawnShapeValue = new RectangleSpawnShapeValue();
      this.ellipseSpawnShapeValue = new EllipseSpawnShapeValue();
      this.cylinderSpawnShapeValue = new CylinderSpawnShapeValue();
      this.meshSpawnShapeValue = new UnweightedMeshSpawnShapeValue();
      this.weightMeshSpawnShapeValue = new WeightMeshSpawnShapeValue();
      this.lineSpawnShapeValue.setDimensions(6.0F, 6.0F, 6.0F);
      this.rectangleSpawnShapeValue.setDimensions(6.0F, 6.0F, 6.0F);
      this.ellipseSpawnShapeValue.setDimensions(6.0F, 6.0F, 6.0F);
      this.cylinderSpawnShapeValue.setDimensions(6.0F, 6.0F, 6.0F);
      this.pointSpawnShapeValue.setActive(true);
      this.lineSpawnShapeValue.setActive(true);
      this.rectangleSpawnShapeValue.setActive(true);
      this.ellipseSpawnShapeValue.setActive(true);
      this.cylinderSpawnShapeValue.setActive(true);
      this.meshSpawnShapeValue.setActive(true);
      this.weightMeshSpawnShapeValue.setActive(true);
      Model defaultModel = this.editor.assetManager.get("monkey.g3db");
      Array<Model> models = new Array<>();
      models.add(defaultModel);
      int i = 0;
      JPanel panel = new JPanel(new GridBagLayout());
      EditorPanel.addContent(panel, i, 0, new JLabel("Shape"), false, 17, 0, 0.0F, 0.0F);
      EditorPanel.addContent(panel, i++, 1, this.shapeCombo = new JComboBox<>(new DefaultComboBoxModel<>(spawnShapes)), false, 17, 0, 1.0F, 0.0F);
      EditorPanel.addContent(panel, i, 0, this.edgesLabel = new JLabel("Edges"), false, 17, 0, 0.0F, 0.0F);
      EditorPanel.addContent(panel, i++, 1, this.edgesCheckbox = new JCheckBox(), false, 17, 0, 0.0F, 0.0F);
      EditorPanel.addContent(panel, i, 0, this.sideLabel = new JLabel("Side"), false, 17, 0, 0.0F, 0.0F);
      EditorPanel.addContent(
         panel, i++, 1, this.sideCombo = new JComboBox<>(new DefaultComboBoxModel<>(PrimitiveSpawnShapeValue.SpawnSide.values())), false, 17, 0, 1.0F, 0.0F
      );
      this.edgesCheckbox.setHorizontalTextPosition(2);
      i = 0;
      this.addContent(i++, 0, panel, 17, 2);
      this.addContent(
         i++,
         0,
         this.meshPanel = new TemplatePickerPanel<>(this.editor, models, this, Model.class, new LoaderButton.ModelLoaderButton(this.editor), true, false),
         false,
         17,
         0
      );
      this.addContent(
         i++,
         0,
         this.xPanel = new RangedNumericPanel(
            this.editor, this.pointSpawnShapeValue.xOffsetValue, "X Offset", "Amount to offset a particle's starting X location, in world units.", false
         )
      );
      this.addContent(
         i++,
         0,
         this.yPanel = new RangedNumericPanel(
            this.editor, this.pointSpawnShapeValue.yOffsetValue, "Y Offset", "Amount to offset a particle's starting Y location, in world units.", false
         )
      );
      this.addContent(
         i++,
         0,
         this.zPanel = new RangedNumericPanel(
            this.editor, this.pointSpawnShapeValue.zOffsetValue, "Z Offset", "Amount to offset a particle's starting Z location, in world units.", false
         )
      );
      this.addContent(
         i++,
         0,
         this.widthPanel = new ScaledNumericPanel(
            this.editor, this.pointSpawnShapeValue.getSpawnWidth(), "Duration", "Spawn Width", "Width of the spawn shape, in world units.", true
         )
      );
      this.addContent(
         i++,
         0,
         this.heightPanel = new ScaledNumericPanel(
            this.editor, this.pointSpawnShapeValue.getSpawnWidth(), "Duration", "Spawn Height", "Height of the spawn shape, in world units.", true
         )
      );
      this.addContent(
         i++,
         0,
         this.depthPanel = new ScaledNumericPanel(
            this.editor, this.pointSpawnShapeValue.getSpawnWidth(), "Duration", "Spawn Depth", "Depth of the spawn shape, in world units.", true
         ),
         false
      );
      this.meshPanel.setIsAlwayShown(true);
      this.onTemplateChecked(defaultModel, true);
      this.shapeCombo
         .addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent event) {
                  String shape = (String)SpawnInfluencerPanel.this.shapeCombo.getSelectedItem();
                  if (shape == "Point") {
                     SpawnInfluencerPanel.this.setPrimitiveSpawnShape(SpawnInfluencerPanel.this.pointSpawnShapeValue, false, null);
                  } else if (shape == "Line") {
                     SpawnInfluencerPanel.this.setPrimitiveSpawnShape(SpawnInfluencerPanel.this.lineSpawnShapeValue, false, null);
                  } else if (shape == "Rectangle") {
                     SpawnInfluencerPanel.this.setPrimitiveSpawnShape(SpawnInfluencerPanel.this.rectangleSpawnShapeValue, true, null);
                  } else if (shape == "Ellipse") {
                     SpawnInfluencerPanel.this.setPrimitiveSpawnShape(
                        SpawnInfluencerPanel.this.ellipseSpawnShapeValue, true, SpawnInfluencerPanel.this.ellipseSpawnShapeValue.getSide()
                     );
                  } else if (shape == "Cylinder") {
                     SpawnInfluencerPanel.this.setPrimitiveSpawnShape(SpawnInfluencerPanel.this.cylinderSpawnShapeValue, true, null);
                  } else if (shape == "Unweighted Mesh") {
                     SpawnInfluencerPanel.this.setMeshSpawnShape(SpawnInfluencerPanel.this.meshSpawnShapeValue);
                  } else if (shape == "Weighted Mesh") {
                     SpawnInfluencerPanel.this.setMeshSpawnShape(SpawnInfluencerPanel.this.weightMeshSpawnShapeValue);
                  }

                  SpawnInfluencerPanel.this.editor.restart();
               }
            }
         );
      this.edgesCheckbox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            SpawnInfluencer influencer = SpawnInfluencerPanel.this.editor.getEmitter().findInfluencer(SpawnInfluencer.class);
            PrimitiveSpawnShapeValue shapeValue = (PrimitiveSpawnShapeValue)influencer.spawnShapeValue;
            shapeValue.setEdges(SpawnInfluencerPanel.this.edgesCheckbox.isSelected());
            SpawnInfluencerPanel.this.setEdgesVisible(true);
         }
      });
      this.sideCombo.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            PrimitiveSpawnShapeValue.SpawnSide side = (PrimitiveSpawnShapeValue.SpawnSide)SpawnInfluencerPanel.this.sideCombo.getSelectedItem();
            SpawnInfluencer influencer = SpawnInfluencerPanel.this.editor.getEmitter().findInfluencer(SpawnInfluencer.class);
            EllipseSpawnShapeValue shapeValue = (EllipseSpawnShapeValue)influencer.spawnShapeValue;
            shapeValue.setSide(side);
         }
      });
   }

   public void onTemplateChecked(Model model, boolean isChecked) {
      SpawnShapeValue shapeValue = null;
      Mesh mesh = model.meshes.get(0);
      this.weightMeshSpawnShapeValue.setMesh(mesh, model);
      this.meshSpawnShapeValue.setMesh(mesh, model);
      if (this.shapeCombo.getSelectedItem() == "Weighted Mesh") {
         SpawnInfluencer influencer = this.editor.getEmitter().findInfluencer(SpawnInfluencer.class);
         influencer.spawnShapeValue = this.weightMeshSpawnShapeValue;
      } else if (this.shapeCombo.getSelectedItem() == "Unweighted Mesh") {
         SpawnInfluencer influencer = this.editor.getEmitter().findInfluencer(SpawnInfluencer.class);
         influencer.spawnShapeValue = this.meshSpawnShapeValue;
      }

      this.editor.restart();
   }
}
