package com.badlogic.gdx.tools.flame;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public class PreAlpha extends JFrame {
   BufferedImage image;
   ImagePanel imagePanel;
   String lastDir;

   public PreAlpha() {
      super("Premultiply alpha converter");
      this.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosed(WindowEvent event) {
            System.exit(0);
         }
      });
      this.initializeComponents();
      this.pack();
      this.setLocationRelativeTo(null);
      this.setDefaultCloseOperation(2);
      this.setVisible(true);
   }

   private void initializeComponents() {
      JMenuBar menuBar = new JMenuBar();
      JMenu menu = new JMenu("File");
      menuBar.add(menu);
      JMenuItem menuItem = new JMenuItem("Open");
      menuItem.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            PreAlpha.this.open();
         }
      });
      menu.add(menuItem);
      menuItem = new JMenuItem("Save");
      menuItem.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            PreAlpha.this.save();
         }
      });
      menu.add(menuItem);
      this.setJMenuBar(menuBar);
      this.imagePanel = new ImagePanel();
      this.getContentPane().add(this.imagePanel);
   }

   protected void save() {
      FileDialog dialog = new FileDialog(this, "Save Image", 1);
      if (this.lastDir != null) {
         dialog.setDirectory(this.lastDir);
      }

      dialog.setVisible(true);
      String file = dialog.getFile();
      String dir = dialog.getDirectory();
      if (dir != null && file != null && file.trim().length() != 0) {
         this.lastDir = dir;

         try {
            this.generatePremultiplyAlpha(new File(dir, file));
            JOptionPane.showMessageDialog(this, "Conversion complete!");
         } catch (Exception var5) {
            JOptionPane.showMessageDialog(this, "Error saving image.");
         }
      }
   }

   protected void open() {
      FileDialog dialog = new FileDialog(this, "Open Image", 0);
      if (this.lastDir != null) {
         dialog.setDirectory(this.lastDir);
      }

      dialog.setVisible(true);
      String file = dialog.getFile();
      String dir = dialog.getDirectory();
      if (dir != null && file != null && file.trim().length() != 0) {
         this.lastDir = dir;

         try {
            this.image = ImageIO.read(new File(dir, file));
            this.imagePanel.setImage(this.image);
            this.imagePanel.revalidate();
            this.imagePanel.repaint();
            this.pack();
         } catch (Exception var5) {
            JOptionPane.showMessageDialog(this, "Error opening image.");
         }
      }
   }

   private void generatePremultiplyAlpha(File out) {
      try {
         BufferedImage outImage = new BufferedImage(this.image.getWidth(), this.image.getHeight(), 2);
         float[] color = new float[4];
         WritableRaster raster = this.image.getRaster();
         WritableRaster outRaster = outImage.getRaster();
         int x = 0;

         for (int w = this.image.getWidth(); x < w; x++) {
            int y = 0;

            for (int h = this.image.getHeight(); y < h; y++) {
               raster.getPixel(x, y, color);
               float alpha = color[3] / 255.0F;

               for (int i = 0; i < 3; i++) {
                  color[i] *= alpha;
               }

               outRaster.setPixel(x, y, color);
            }
         }

         ImageIO.write(outImage, "png", out);
      } catch (IOException var12) {
         var12.printStackTrace();
      }
   }

   public static void main(String[] args) {
      for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
         if ("Nimbus".equals(info.getName())) {
            try {
               UIManager.setLookAndFeel(info.getClassName());
            } catch (Throwable var6) {
            }
            break;
         }
      }

      EventQueue.invokeLater(new Runnable() {
         @Override
         public void run() {
            new PreAlpha();
         }
      });
   }
}
