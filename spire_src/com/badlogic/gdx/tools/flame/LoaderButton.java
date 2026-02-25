package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.UBJsonReader;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JOptionPane;

public abstract class LoaderButton<T> extends JButton {
   private String lastDir;
   protected LoaderButton.Listener<T> listener;
   FlameMain editor;

   public LoaderButton(FlameMain editor, String text, LoaderButton.Listener<T> listener) {
      super(text);
      this.editor = editor;
      this.listener = listener;
      this.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            LoaderButton.this.loadResource();
         }
      });
   }

   public LoaderButton(FlameMain editor, String text) {
      this(editor, text, null);
   }

   public void setListener(LoaderButton.Listener listener) {
      this.listener = listener;
   }

   protected abstract void loadResource();

   public interface Listener<T> {
      void onResourceLoaded(T var1);
   }

   public static class ModelLoaderButton extends LoaderButton<Model> {
      public ModelLoaderButton(FlameMain editor) {
         this(editor, null);
      }

      public ModelLoaderButton(FlameMain editor, LoaderButton.Listener<Model> listener) {
         super(editor, "Load Model", listener);
      }

      @Override
      protected void loadResource() {
         File file = this.editor.showFileLoadDialog();
         if (file != null) {
            try {
               String resource = file.getAbsolutePath();
               ModelLoader modelLoader = null;
               if (resource.endsWith(".obj")) {
                  modelLoader = new ObjLoader(new AbsoluteFileHandleResolver());
               } else if (resource.endsWith(".g3dj")) {
                  modelLoader = new G3dModelLoader(new JsonReader(), new AbsoluteFileHandleResolver());
               } else {
                  if (!resource.endsWith(".g3db")) {
                     throw new Exception();
                  }

                  modelLoader = new G3dModelLoader(new UBJsonReader(), new AbsoluteFileHandleResolver());
               }

               this.listener.onResourceLoaded(this.editor.load(resource, Model.class, modelLoader, null));
            } catch (Exception var4) {
               System.out.println("Error loading model: " + file.getAbsolutePath());
               var4.printStackTrace();
               JOptionPane.showMessageDialog(this.getParent(), "Error opening effect.");
               return;
            }
         }
      }
   }

   public static class ParticleEffectLoaderButton extends LoaderButton<ParticleEffect> {
      public ParticleEffectLoaderButton(FlameMain editor) {
         this(editor, null);
      }

      public ParticleEffectLoaderButton(FlameMain editor, LoaderButton.Listener<ParticleEffect> listener) {
         super(editor, "Load Controller", listener);
      }

      @Override
      protected void loadResource() {
         File file = this.editor.showFileLoadDialog();
         if (file != null) {
            try {
               String resource = file.getAbsolutePath();
               this.listener.onResourceLoaded(this.editor.openEffect(file, false));
            } catch (Exception var3) {
               System.out.println("Error loading effect: " + file.getAbsolutePath());
               var3.printStackTrace();
               JOptionPane.showMessageDialog(this.getParent(), "Error opening effect.");
               return;
            }
         }
      }
   }
}
