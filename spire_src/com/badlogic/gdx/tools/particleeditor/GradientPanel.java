package com.badlogic.gdx.tools.particleeditor;

import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class GradientPanel extends EditorPanel {
   private final ParticleEmitter.GradientColorValue value;
   private GradientPanel.GradientEditor gradientEditor;
   GradientPanel.ColorSlider saturationSlider;
   GradientPanel.ColorSlider lightnessSlider;
   JPanel colorPanel;
   private GradientPanel.ColorSlider hueSlider;

   public GradientPanel(ParticleEmitter.GradientColorValue value, String name, String description, boolean hideGradientEditor) {
      super(value, name, description);
      this.value = value;
      this.initializeComponents();
      if (hideGradientEditor) {
         this.gradientEditor.setVisible(false);
      }

      this.gradientEditor.percentages.clear();

      for (float percent : value.getTimeline()) {
         this.gradientEditor.percentages.add(percent);
      }

      this.gradientEditor.colors.clear();
      float[] colors = value.getColors();
      int i = 0;

      while (i < colors.length) {
         float r = colors[i++];
         float g = colors[i++];
         float b = colors[i++];
         this.gradientEditor.colors.add(new Color(r, g, b));
      }

      if (this.gradientEditor.colors.isEmpty() || this.gradientEditor.percentages.isEmpty()) {
         this.gradientEditor.percentages.clear();
         this.gradientEditor.percentages.add(0.0F);
         this.gradientEditor.percentages.add(1.0F);
         this.gradientEditor.colors.clear();
         this.gradientEditor.colors.add(Color.white);
      }

      this.setColor(this.gradientEditor.colors.get(0));
   }

   @Override
   public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.width = 10;
      return size;
   }

   private void initializeComponents() {
      JPanel contentPanel = this.getContentPanel();
      this.gradientEditor = new GradientPanel.GradientEditor() {
         @Override
         public void handleSelected(Color color) {
            GradientPanel.this.setColor(color);
         }
      };
      contentPanel.add(this.gradientEditor, new GridBagConstraints(0, 1, 3, 1, 1.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 10));
      this.hueSlider = new GradientPanel.ColorSlider(new Color[]{Color.red, Color.yellow, Color.green, Color.cyan, Color.blue, Color.magenta, Color.red}) {
         @Override
         protected void colorPicked() {
            GradientPanel.this.saturationSlider.setColors(new Color[]{new Color(Color.HSBtoRGB(this.getPercentage(), 1.0F, 1.0F)), Color.white});
            GradientPanel.this.updateColor();
         }
      };
      contentPanel.add(this.hueSlider, new GridBagConstraints(1, 2, 2, 1, 1.0, 0.0, 10, 2, new Insets(0, 0, 6, 0), 0, 0));
      this.saturationSlider = new GradientPanel.ColorSlider(new Color[]{Color.red, Color.white}) {
         @Override
         protected void colorPicked() {
            GradientPanel.this.updateColor();
         }
      };
      contentPanel.add(this.saturationSlider, new GridBagConstraints(1, 3, 1, 1, 1.0, 0.0, 10, 2, new Insets(0, 0, 0, 6), 0, 0));
      this.lightnessSlider = new GradientPanel.ColorSlider(new Color[0]) {
         @Override
         protected void colorPicked() {
            GradientPanel.this.updateColor();
         }
      };
      contentPanel.add(this.lightnessSlider, new GridBagConstraints(2, 3, 1, 1, 1.0, 0.0, 10, 2, new Insets(0, 0, 0, 0), 0, 0));
      this.colorPanel = new JPanel() {
         @Override
         public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width = 52;
            return size;
         }
      };
      contentPanel.add(this.colorPanel, new GridBagConstraints(0, 2, 1, 2, 0.0, 0.0, 10, 1, new Insets(3, 0, 0, 6), 0, 0));
      this.colorPanel.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            Color color = JColorChooser.showDialog(GradientPanel.this.colorPanel, "Set Color", GradientPanel.this.colorPanel.getBackground());
            if (color != null) {
               GradientPanel.this.setColor(color);
            }
         }
      });
      this.colorPanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.black));
   }

   public void setColor(Color color) {
      float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      this.hueSlider.setPercentage(hsb[0]);
      this.saturationSlider.setPercentage(1.0F - hsb[1]);
      this.lightnessSlider.setPercentage(1.0F - hsb[2]);
      this.colorPanel.setBackground(color);
   }

   void updateColor() {
      Color color = new Color(Color.HSBtoRGB(this.hueSlider.getPercentage(), 1.0F - this.saturationSlider.getPercentage(), 1.0F));
      this.lightnessSlider.setColors(new Color[]{color, Color.black});
      color = new Color(
         Color.HSBtoRGB(this.hueSlider.getPercentage(), 1.0F - this.saturationSlider.getPercentage(), 1.0F - this.lightnessSlider.getPercentage())
      );
      this.colorPanel.setBackground(color);
      this.gradientEditor.setColor(color);
      float[] colors = new float[this.gradientEditor.colors.size() * 3];
      int i = 0;

      for (Color c : this.gradientEditor.colors) {
         colors[i++] = c.getRed() / 255.0F;
         colors[i++] = c.getGreen() / 255.0F;
         colors[i++] = c.getBlue() / 255.0F;
      }

      float[] percentages = new float[this.gradientEditor.percentages.size()];
      i = 0;

      for (Float percent : this.gradientEditor.percentages) {
         percentages[i++] = percent;
      }

      this.value.setColors(colors);
      this.value.setTimeline(percentages);
   }

   public static class ColorSlider extends JPanel {
      Color[] paletteColors;
      JSlider slider;
      private GradientPanel.ColorSlider.ColorPicker colorPicker;

      public ColorSlider(Color[] paletteColors) {
         this.paletteColors = paletteColors;
         this.setLayout(new GridBagLayout());
         this.slider = new JSlider(0, 1000, 0);
         this.slider.setPaintTrack(false);
         this.add(this.slider, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, 10, 2, new Insets(0, 6, 0, 6), 0, 0));
         this.colorPicker = new GradientPanel.ColorSlider.ColorPicker();
         this.add(this.colorPicker, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, 10, 2, new Insets(0, 6, 0, 6), 0, 0));
         this.slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
               ColorSlider.this.colorPicked();
            }
         });
      }

      @Override
      public Dimension getPreferredSize() {
         Dimension size = super.getPreferredSize();
         size.width = 10;
         return size;
      }

      public void setPercentage(float percent) {
         this.slider.setValue((int)(1000.0F * percent));
      }

      public float getPercentage() {
         return this.slider.getValue() / 1000.0F;
      }

      protected void colorPicked() {
      }

      public void setColors(Color[] colors) {
         this.paletteColors = colors;
         this.repaint();
      }

      public class ColorPicker extends JPanel {
         public ColorPicker() {
            this.addMouseListener(new MouseAdapter() {
               @Override
               public void mouseClicked(MouseEvent event) {
                  ColorSlider.this.slider.setValue((int)((float)event.getX() / ColorPicker.this.getWidth() * 1000.0F));
               }
            });
         }

         @Override
         protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics;
            int width = this.getWidth() - 1;
            int height = this.getHeight() - 1;
            int i = 0;

            for (int n = ColorSlider.this.paletteColors.length - 1; i < n; i++) {
               Color color1 = ColorSlider.this.paletteColors[i];
               Color color2 = ColorSlider.this.paletteColors[i + 1];
               float point1 = (float)i / n * width;
               float point2 = (float)(i + 1) / n * width;
               g.setPaint(new GradientPaint(point1, 0.0F, color1, point2, 0.0F, color2, false));
               g.fillRect((int)point1, 0, (int)Math.ceil(point2 - point1), height);
            }

            g.setPaint(null);
            g.setColor(Color.black);
            g.drawRect(0, 0, width, height);
         }
      }
   }

   public class GradientEditor extends JPanel {
      ArrayList<Color> colors = new ArrayList<>();
      ArrayList<Float> percentages = new ArrayList<>();
      int handleWidth = 12;
      int handleHeight = 12;
      int gradientX = this.handleWidth / 2;
      int gradientY = 0;
      int gradientWidth;
      int gradientHeight;
      int dragIndex = -1;
      int selectedIndex;

      public GradientEditor() {
         this.setPreferredSize(new Dimension(100, 30));
         this.addMouseListener(
            new MouseAdapter() {
               @Override
               public void mousePressed(MouseEvent event) {
                  GradientEditor.this.dragIndex = -1;
                  int mouseX = event.getX();
                  int mouseY = event.getY();
                  int y = GradientEditor.this.gradientY + GradientEditor.this.gradientHeight;
                  int i = 0;

                  for (int n = GradientEditor.this.colors.size(); i < n; i++) {
                     int x = GradientEditor.this.gradientX
                        + (int)(GradientEditor.this.percentages.get(i) * GradientEditor.this.gradientWidth)
                        - GradientEditor.this.handleWidth / 2;
                     if (mouseX >= x
                        && mouseX <= x + GradientEditor.this.handleWidth
                        && mouseY >= GradientEditor.this.gradientY
                        && mouseY <= y + GradientEditor.this.handleHeight) {
                        GradientEditor.this.dragIndex = GradientEditor.this.selectedIndex = i;
                        GradientEditor.this.handleSelected(GradientEditor.this.colors.get(GradientEditor.this.selectedIndex));
                        GradientEditor.this.repaint();
                        break;
                     }
                  }
               }

               @Override
               public void mouseReleased(MouseEvent event) {
                  if (GradientEditor.this.dragIndex != -1) {
                     GradientEditor.this.dragIndex = -1;
                     GradientEditor.this.repaint();
                  }
               }

               @Override
               public void mouseClicked(MouseEvent event) {
                  int mouseX = event.getX();
                  int mouseY = event.getY();
                  if (event.getClickCount() == 2) {
                     if (GradientEditor.this.percentages.size() > 1) {
                        if (GradientEditor.this.selectedIndex != -1 && GradientEditor.this.selectedIndex != 0) {
                           int y = GradientEditor.this.gradientY + GradientEditor.this.gradientHeight;
                           int x = GradientEditor.this.gradientX
                              + (int)(GradientEditor.this.percentages.get(GradientEditor.this.selectedIndex) * GradientEditor.this.gradientWidth)
                              - GradientEditor.this.handleWidth / 2;
                           if (mouseX >= x
                              && mouseX <= x + GradientEditor.this.handleWidth
                              && mouseY >= GradientEditor.this.gradientY
                              && mouseY <= y + GradientEditor.this.handleHeight) {
                              GradientEditor.this.percentages.remove(GradientEditor.this.selectedIndex);
                              GradientEditor.this.colors.remove(GradientEditor.this.selectedIndex);
                              GradientEditor.this.selectedIndex--;
                              GradientEditor.this.dragIndex = GradientEditor.this.selectedIndex;
                              if (GradientEditor.this.percentages.size() == 2) {
                                 GradientEditor.this.percentages.set(1, 1.0F);
                              }

                              GradientEditor.this.handleSelected(GradientEditor.this.colors.get(GradientEditor.this.selectedIndex));
                              GradientEditor.this.repaint();
                           }
                        }
                     }
                  } else if (mouseX >= GradientEditor.this.gradientX && mouseX <= GradientEditor.this.gradientX + GradientEditor.this.gradientWidth) {
                     if (mouseY >= GradientEditor.this.gradientY && mouseY <= GradientEditor.this.gradientY + GradientEditor.this.gradientHeight) {
                        float percent = (float)(event.getX() - GradientEditor.this.gradientX) / GradientEditor.this.gradientWidth;
                        if (GradientEditor.this.percentages.size() == 1) {
                           percent = 1.0F;
                        }

                        int i = 0;

                        for (int n = GradientEditor.this.percentages.size(); i <= n; i++) {
                           if (i == n || percent < GradientEditor.this.percentages.get(i)) {
                              GradientEditor.this.percentages.add(i, percent);
                              GradientEditor.this.colors.add(i, GradientEditor.this.colors.get(i - 1));
                              GradientEditor.this.dragIndex = GradientEditor.this.selectedIndex = i;
                              GradientEditor.this.handleSelected(GradientEditor.this.colors.get(GradientEditor.this.selectedIndex));
                              GradientPanel.this.updateColor();
                              GradientEditor.this.repaint();
                              break;
                           }
                        }
                     }
                  }
               }
            }
         );
         this.addMouseMotionListener(
            new MouseMotionAdapter() {
               @Override
               public void mouseDragged(MouseEvent event) {
                  if (GradientEditor.this.dragIndex != -1
                     && GradientEditor.this.dragIndex != 0
                     && GradientEditor.this.dragIndex != GradientEditor.this.percentages.size() - 1) {
                     float percent = (float)(event.getX() - GradientEditor.this.gradientX) / GradientEditor.this.gradientWidth;
                     percent = Math.max(percent, GradientEditor.this.percentages.get(GradientEditor.this.dragIndex - 1) + 0.01F);
                     percent = Math.min(percent, GradientEditor.this.percentages.get(GradientEditor.this.dragIndex + 1) - 0.01F);
                     GradientEditor.this.percentages.set(GradientEditor.this.dragIndex, percent);
                     GradientPanel.this.updateColor();
                     GradientEditor.this.repaint();
                  }
               }
            }
         );
      }

      public void setColor(Color color) {
         if (this.selectedIndex != -1) {
            this.colors.set(this.selectedIndex, color);
            this.repaint();
         }
      }

      public void handleSelected(Color color) {
      }

      @Override
      protected void paintComponent(Graphics graphics) {
         super.paintComponent(graphics);
         Graphics2D g = (Graphics2D)graphics;
         int width = this.getWidth() - 1;
         int height = this.getHeight();
         this.gradientWidth = width - this.handleWidth;
         this.gradientHeight = height - 16;
         g.translate(this.gradientX, this.gradientY);
         int i = 0;

         for (int n = this.colors.size() == 1 ? 1 : this.colors.size() - 1; i < n; i++) {
            Color color1 = this.colors.get(i);
            Color color2 = this.colors.size() == 1 ? color1 : this.colors.get(i + 1);
            float percent1 = this.percentages.get(i);
            float percent2 = this.colors.size() == 1 ? 1.0F : this.percentages.get(i + 1);
            int point1 = (int)(percent1 * this.gradientWidth);
            int point2 = (int)Math.ceil(percent2 * this.gradientWidth);
            g.setPaint(new GradientPaint(point1, 0.0F, color1, point2, 0.0F, color2, false));
            g.fillRect(point1, 0, point2 - point1, this.gradientHeight);
         }

         g.setPaint(null);
         g.setColor(Color.black);
         g.drawRect(0, 0, this.gradientWidth, this.gradientHeight);
         i = this.gradientHeight;
         int[] yPoints = new int[]{i, i + this.handleHeight, i + this.handleHeight};
         int[] xPoints = new int[3];
         int ix = 0;

         for (int n = this.colors.size(); ix < n; ix++) {
            int x = (int)(this.percentages.get(ix) * this.gradientWidth);
            xPoints[0] = x;
            xPoints[1] = x - this.handleWidth / 2;
            xPoints[2] = x + this.handleWidth / 2;
            if (ix == this.selectedIndex) {
               g.setColor(this.colors.get(ix));
               g.fillPolygon(xPoints, yPoints, 3);
               g.fillRect(xPoints[1], yPoints[1] + 2, this.handleWidth + 1, 2);
               g.setColor(Color.black);
            }

            g.drawPolygon(xPoints, yPoints, 3);
         }

         g.translate(-this.gradientX, -this.gradientY);
      }
   }
}
