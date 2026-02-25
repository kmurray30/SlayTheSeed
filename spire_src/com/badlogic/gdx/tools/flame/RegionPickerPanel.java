package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

public class RegionPickerPanel extends JPanel {
   TextureAtlasPanel atlasPanel;
   TexturePanel texturePanel;
   JButton selectButton;
   JButton selectAllButton;
   JButton clearButton;
   JButton generateButton;
   JButton reverseButton;
   JComboBox generateBox;
   Slider rowSlider;
   Slider columnSlider;
   JPanel generationPanel;
   JPanel content;
   RegionPickerPanel.Listener listener;

   public RegionPickerPanel(RegionPickerPanel.Listener listener) {
      this.initializeComponents();
      this.listener = listener;
   }

   private void initializeComponents() {
      this.setLayout(new GridBagLayout());
      this.content = new JPanel();
      this.atlasPanel = new TextureAtlasPanel();
      this.texturePanel = new TexturePanel();
      CustomCardLayout cardLayout = new CustomCardLayout();
      this.content.setLayout(cardLayout);
      this.content.add(this.atlasPanel, "atlas");
      this.content.add(this.texturePanel, "texture");
      this.add(this.content, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, 17, 0, new Insets(0, 0, 0, 0), 0, 0));
      JPanel controls = new JPanel(new GridBagLayout());
      controls.add(this.selectButton = new JButton("Select"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      controls.add(new JSeparator(0), new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      JPanel pickPanel = new JPanel(new GridBagLayout());
      pickPanel.add(this.selectAllButton = new JButton("Pick All"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      pickPanel.add(this.clearButton = new JButton("Clear Selection"), new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      pickPanel.add(this.reverseButton = new JButton("Reverse Selection"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      controls.add(pickPanel, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.generationPanel = new JPanel(new GridBagLayout());
      this.generationPanel.add(new JLabel("Rows"), new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.generationPanel
         .add(this.rowSlider = new Slider(1.0F, 1.0F, 9999.0F, 1.0F), new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.generationPanel.add(new JLabel("Columns"), new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.generationPanel
         .add(this.columnSlider = new Slider(1.0F, 1.0F, 9999.0F, 1.0F), new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.generationPanel
         .add(
            this.generateBox = new JComboBox<>(new DefaultComboBoxModel<>(RegionPickerPanel.GenerationMode.values())),
            new GridBagConstraints(0, 2, 1, 1, 1.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0)
         );
      this.generationPanel
         .add(this.generateButton = new JButton("Generate"), new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      controls.add(new JSeparator(0), new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      controls.add(this.generationPanel, new GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 10, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.add(controls, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, 18, 0, new Insets(0, 0, 0, 0), 0, 0));
      this.selectButton
         .addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent arg0) {
                  JPanel panel = ((CustomCardLayout)RegionPickerPanel.this.content.getLayout()).getCurrentCard(RegionPickerPanel.this.content);
                  TexturePanel currentTexturePanel = panel == RegionPickerPanel.this.atlasPanel
                     ? RegionPickerPanel.this.atlasPanel.getCurrentRegionPanel()
                     : RegionPickerPanel.this.texturePanel;
                  RegionPickerPanel.this.listener.onRegionsSelected(currentTexturePanel.selectedRegions);
               }
            }
         );
      this.selectAllButton
         .addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent arg0) {
                  JPanel panel = ((CustomCardLayout)RegionPickerPanel.this.content.getLayout()).getCurrentCard(RegionPickerPanel.this.content);
                  TexturePanel currentTexturePanel = panel == RegionPickerPanel.this.atlasPanel
                     ? RegionPickerPanel.this.atlasPanel.getCurrentRegionPanel()
                     : RegionPickerPanel.this.texturePanel;
                  currentTexturePanel.selectAll();
               }
            }
         );
      this.reverseButton
         .addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent arg0) {
                  JPanel panel = ((CustomCardLayout)RegionPickerPanel.this.content.getLayout()).getCurrentCard(RegionPickerPanel.this.content);
                  TexturePanel currentTexturePanel = panel == RegionPickerPanel.this.atlasPanel
                     ? RegionPickerPanel.this.atlasPanel.getCurrentRegionPanel()
                     : RegionPickerPanel.this.texturePanel;
                  currentTexturePanel.selectedRegions.reverse();
                  currentTexturePanel.revalidate();
                  currentTexturePanel.repaint();
               }
            }
         );
      this.clearButton
         .addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent arg0) {
                  JPanel panel = ((CustomCardLayout)RegionPickerPanel.this.content.getLayout()).getCurrentCard(RegionPickerPanel.this.content);
                  TexturePanel currentPanel = panel == RegionPickerPanel.this.atlasPanel
                     ? RegionPickerPanel.this.atlasPanel.getCurrentRegionPanel()
                     : RegionPickerPanel.this.texturePanel;
                  currentPanel.clearSelection();
               }
            }
         );
      this.generateButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            RegionPickerPanel.this.generateRegions((RegionPickerPanel.GenerationMode)RegionPickerPanel.this.generateBox.getSelectedItem());
            RegionPickerPanel.this.texturePanel.revalidate();
            RegionPickerPanel.this.texturePanel.repaint();
         }
      });
   }

   void generateRegions(RegionPickerPanel.GenerationMode mode) {
      this.texturePanel.clear();
      Texture texture = this.texturePanel.getTexture();
      int rows = (int)this.rowSlider.getValue();
      int columns = (int)this.columnSlider.getValue();
      int yOffset = texture.getHeight() / rows;
      int xOffset = texture.getWidth() / columns;
      if (mode == RegionPickerPanel.GenerationMode.ByRows) {
         for (int j = 0; j < rows; j++) {
            int rowOffset = j * yOffset;

            for (int i = 0; i < columns; i++) {
               this.texturePanel.unselectedRegions.add(new TextureRegion(texture, i * xOffset, rowOffset, xOffset, yOffset));
            }
         }
      } else if (mode == RegionPickerPanel.GenerationMode.ByColumns) {
         for (int i = 0; i < columns; i++) {
            int columnOffset = i * xOffset;

            for (int j = 0; j < rows; j++) {
               this.texturePanel.unselectedRegions.add(new TextureRegion(texture, columnOffset, j * yOffset, xOffset, yOffset));
            }
         }
      }
   }

   public void setAtlas(TextureAtlas atlas) {
      this.atlasPanel.clearSelection();
      this.atlasPanel.setAtlas(atlas);
      CustomCardLayout cardLayout = (CustomCardLayout)this.content.getLayout();
      cardLayout.show(this.content, "atlas");
      this.showGenerationPanel(false);
      this.content.revalidate();
      this.content.repaint();
      this.revalidate();
      this.repaint();
   }

   public void setTexture(Texture texture) {
      this.texturePanel.clearSelection();
      this.texturePanel.setTexture(texture);
      CustomCardLayout cardLayout = (CustomCardLayout)this.content.getLayout();
      cardLayout.show(this.content, "texture");
      this.showGenerationPanel(true);
      this.content.revalidate();
      this.content.repaint();
      this.revalidate();
      this.repaint();
   }

   private void showGenerationPanel(boolean isShown) {
      this.generationPanel.setVisible(isShown);
   }

   private static enum GenerationMode {
      ByRows("Generate By Rows"),
      ByColumns("Generate By Columns");

      String string;

      private GenerationMode(String string) {
         this.string = string;
      }

      @Override
      public String toString() {
         return this.string;
      }
   }

   public interface Listener {
      void onRegionsSelected(Array<TextureRegion> var1);
   }
}
