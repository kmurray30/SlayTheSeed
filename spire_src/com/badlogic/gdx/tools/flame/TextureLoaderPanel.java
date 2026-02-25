package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;

public class TextureLoaderPanel extends EditorPanel {
   public TextureLoaderPanel(FlameMain editor, String name, String description) {
      super(editor, name, description);
      this.setValue(null);
   }

   @Override
   protected void initializeComponents() {
      super.initializeComponents();
      JButton atlasButton = new JButton("Open Atlas");
      JButton textureButton = new JButton("Open Texture");
      JButton defaultTextureButton = new JButton("Default Texture");
      final JCheckBox genMipMaps = new JCheckBox("Generate MipMaps");
      final JComboBox minFilterBox = new JComboBox<>(new DefaultComboBoxModel<>(Texture.TextureFilter.values()));
      final JComboBox magFilterBox = new JComboBox<>(new DefaultComboBoxModel<>(Texture.TextureFilter.values()));
      minFilterBox.setSelectedItem(this.editor.getTexture().getMinFilter());
      magFilterBox.setSelectedItem(this.editor.getTexture().getMagFilter());
      ActionListener filterListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            TextureLoaderPanel.this.editor
               .getTexture()
               .setFilter((Texture.TextureFilter)minFilterBox.getSelectedItem(), (Texture.TextureFilter)magFilterBox.getSelectedItem());
         }
      };
      minFilterBox.addActionListener(filterListener);
      magFilterBox.addActionListener(filterListener);
      atlasButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            File file = TextureLoaderPanel.this.editor.showFileLoadDialog();
            if (file != null) {
               TextureAtlas atlas = TextureLoaderPanel.this.editor.load(file.getAbsolutePath(), TextureAtlas.class, null, null);
               if (atlas != null) {
                  TextureLoaderPanel.this.editor.setAtlas(atlas);
               }
            }
         }
      });
      textureButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            File file = TextureLoaderPanel.this.editor.showFileLoadDialog();
            if (file != null) {
               TextureLoader.TextureParameter params = new TextureLoader.TextureParameter();
               params.genMipMaps = genMipMaps.isSelected();
               params.minFilter = (Texture.TextureFilter)minFilterBox.getSelectedItem();
               params.magFilter = (Texture.TextureFilter)magFilterBox.getSelectedItem();
               Texture texture = TextureLoaderPanel.this.editor.load(file.getAbsolutePath(), Texture.class, null, params);
               if (texture != null) {
                  TextureLoaderPanel.this.editor.setTexture(texture);
               }
            }
         }
      });
      defaultTextureButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            TextureLoaderPanel.this.editor.setTexture(TextureLoaderPanel.this.editor.assetManager.get("pre_particle.png", Texture.class));
         }
      });
      this.contentPanel.add(new JLabel("Min. Filter"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(minFilterBox, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(new JLabel("Mag. Filter"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(magFilterBox, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(genMipMaps, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(atlasButton, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(textureButton, new GridBagConstraints(1, 3, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
      this.contentPanel.add(defaultTextureButton, new GridBagConstraints(2, 3, 1, 1, 1.0, 0.0, 17, 0, new Insets(6, 0, 0, 0), 0, 0));
   }
}
