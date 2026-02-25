package com.badlogic.gdx.tools.particleeditor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import javax.swing.JPanel;

public class Chart extends JPanel {
   private static final int POINT_SIZE = 6;
   private static final int POINT_SIZE_EXPANDED = 10;
   ArrayList<Chart.Point> points = new ArrayList<>();
   private int numberHeight;
   int chartX;
   int chartY;
   int chartWidth;
   int chartHeight;
   int maxX;
   int maxY;
   int overIndex = -1;
   int movingIndex = -1;
   boolean isExpanded;
   String title;
   boolean moveAll = false;
   boolean moveAllProportionally = false;
   int moveAllPrevY;

   public Chart(String title) {
      this.title = title;
      this.setLayout(new GridBagLayout());
      this.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            Chart.this.movingIndex = Chart.this.overIndex;
            Chart.this.moveAll = event.isControlDown();
            if (Chart.this.moveAll) {
               Chart.this.moveAllProportionally = event.isShiftDown();
               Chart.this.moveAllPrevY = event.getY();
            }
         }

         @Override
         public void mouseReleased(MouseEvent event) {
            Chart.this.movingIndex = -1;
            Chart.this.moveAll = false;
         }

         @Override
         public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2) {
               if (Chart.this.overIndex > 0 && Chart.this.overIndex < Chart.this.points.size()) {
                  Chart.this.points.remove(Chart.this.overIndex);
                  Chart.this.pointsChanged();
                  Chart.this.repaint();
               }
            } else if (Chart.this.movingIndex == -1) {
               if (Chart.this.overIndex == -1) {
                  int mouseX = event.getX();
                  int mouseY = event.getY();
                  if (mouseX >= Chart.this.chartX && mouseX <= Chart.this.chartX + Chart.this.chartWidth) {
                     if (mouseY >= Chart.this.chartY && mouseY <= Chart.this.chartY + Chart.this.chartHeight) {
                        Chart.Point newPoint = Chart.this.pixelToPoint(mouseX, mouseY);
                        int i = 0;
                        Chart.Point lastPoint = null;

                        for (Chart.Point point : Chart.this.points) {
                           if (point.x > newPoint.x) {
                              if (Math.abs(point.x - newPoint.x) < 0.001F) {
                                 return;
                              }

                              if (lastPoint != null && Math.abs(lastPoint.x - newPoint.x) < 0.001F) {
                                 return;
                              }

                              Chart.this.points.add(i, newPoint);
                              Chart.this.overIndex = i;
                              Chart.this.pointsChanged();
                              Chart.this.repaint();
                              return;
                           }

                           lastPoint = point;
                           i++;
                        }

                        Chart.this.overIndex = Chart.this.points.size();
                        Chart.this.points.add(newPoint);
                        Chart.this.pointsChanged();
                        Chart.this.repaint();
                     }
                  }
               }
            }
         }
      });
      this.addMouseMotionListener(
         new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent event) {
               if (Chart.this.movingIndex != -1 && Chart.this.movingIndex < Chart.this.points.size()) {
                  if (Chart.this.moveAll) {
                     int newY = event.getY();
                     float deltaY = (float)(Chart.this.moveAllPrevY - newY) / Chart.this.chartHeight * Chart.this.maxY;

                     for (Chart.Point point : Chart.this.points) {
                        point.y = Math.min((float)Chart.this.maxY, Math.max(0.0F, point.y + (Chart.this.moveAllProportionally ? deltaY * point.y : deltaY)));
                     }

                     Chart.this.moveAllPrevY = newY;
                  } else {
                     float nextX = Chart.this.movingIndex == Chart.this.points.size() - 1
                        ? Chart.this.maxX
                        : Chart.this.points.get(Chart.this.movingIndex + 1).x - 0.001F;
                     if (Chart.this.movingIndex == 0) {
                        nextX = 0.0F;
                     }

                     float prevX = Chart.this.movingIndex == 0 ? 0.0F : Chart.this.points.get(Chart.this.movingIndex - 1).x + 0.001F;
                     Chart.Point point = Chart.this.points.get(Chart.this.movingIndex);
                     point.x = Math.min(nextX, Math.max(prevX, (float)(event.getX() - Chart.this.chartX) / Chart.this.chartWidth * Chart.this.maxX));
                     point.y = Math.min(
                        (float)Chart.this.maxY,
                        (float)Math.max(0, Chart.this.chartHeight - (event.getY() - Chart.this.chartY)) / Chart.this.chartHeight * Chart.this.maxY
                     );
                  }

                  Chart.this.pointsChanged();
                  Chart.this.repaint();
               }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
               int mouseX = event.getX();
               int mouseY = event.getY();
               int oldIndex = Chart.this.overIndex;
               Chart.this.overIndex = -1;
               int pointSize = Chart.this.isExpanded ? 10 : 6;
               int i = 0;

               for (Chart.Point point : Chart.this.points) {
                  int x = Chart.this.chartX + (int)(Chart.this.chartWidth * (point.x / Chart.this.maxX));
                  int y = Chart.this.chartY + Chart.this.chartHeight - (int)(Chart.this.chartHeight * (point.y / Chart.this.maxY));
                  if (Math.abs(x - mouseX) <= pointSize && Math.abs(y - mouseY) <= pointSize) {
                     Chart.this.overIndex = i;
                     break;
                  }

                  i++;
               }

               if (Chart.this.overIndex != oldIndex) {
                  Chart.this.repaint();
               }
            }
         }
      );
   }

   public void addPoint(float x, float y) {
      this.points.add(new Chart.Point(x, y));
   }

   public void pointsChanged() {
   }

   public float[] getValuesX() {
      float[] values = new float[this.points.size()];
      int i = 0;

      for (Chart.Point point : this.points) {
         values[i++] = point.x;
      }

      return values;
   }

   public float[] getValuesY() {
      float[] values = new float[this.points.size()];
      int i = 0;

      for (Chart.Point point : this.points) {
         values[i++] = point.y;
      }

      return values;
   }

   public void setValues(float[] x, float[] y) {
      this.points.clear();

      for (int i = 0; i < x.length; i++) {
         this.points.add(new Chart.Point(x[i], y[i]));
      }
   }

   Chart.Point pixelToPoint(float x, float y) {
      Chart.Point point = new Chart.Point();
      point.x = Math.min((float)this.maxX, Math.max(0.0F, x - this.chartX) / this.chartWidth * this.maxX);
      point.y = Math.min((float)this.maxY, Math.max(0.0F, this.chartHeight - (y - this.chartY)) / this.chartHeight * this.maxY);
      return point;
   }

   Chart.Point pointToPixel(Chart.Point point) {
      Chart.Point pixel = new Chart.Point();
      pixel.x = this.chartX + (int)(this.chartWidth * (point.x / this.maxX));
      pixel.y = this.chartY + this.chartHeight - (int)(this.chartHeight * (point.y / this.maxY));
      return pixel;
   }

   @Override
   protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D)graphics;
      FontMetrics metrics = g.getFontMetrics();
      if (this.numberHeight == 0) {
         this.numberHeight = this.getFont()
            .layoutGlyphVector(g.getFontRenderContext(), new char[]{'0'}, 0, 1, 0)
            .getGlyphPixelBounds(0, g.getFontRenderContext(), 0.0F, 0.0F)
            .height;
      }

      int width = this.getWidth();
      if (!this.isExpanded) {
         width = Math.min(150, width);
      }

      width = Math.max(100, width);
      int height = this.getHeight();
      int maxAxisLabelWidth;
      int yAxisWidth;
      if (this.isExpanded) {
         maxAxisLabelWidth = metrics.stringWidth("100%");
         yAxisWidth = maxAxisLabelWidth + 8;
         this.chartX = yAxisWidth;
         this.chartY = this.numberHeight / 2 + 1;
         this.chartWidth = width - yAxisWidth - 2;
         this.chartHeight = height - this.chartY - this.numberHeight - 8;
      } else {
         maxAxisLabelWidth = 0;
         yAxisWidth = 2;
         this.chartX = yAxisWidth;
         this.chartY = 2;
         this.chartWidth = width - yAxisWidth - 2;
         this.chartHeight = height - this.chartY - 3;
      }

      g.setColor(Color.white);
      g.fillRect(this.chartX, this.chartY, this.chartWidth, this.chartHeight);
      g.setColor(Color.black);
      g.drawRect(this.chartX, this.chartY, this.chartWidth, this.chartHeight);
      this.maxX = 1;
      int ySplit;
      if (this.isExpanded) {
         ySplit = height - this.numberHeight;
      } else {
         ySplit = height + 5;
      }

      int xSplit = (int)Math.min(10.0F, this.chartWidth / (maxAxisLabelWidth * 1.5F));

      for (int i = 0; i <= xSplit; i++) {
         float percent = (float)i / xSplit;
         String label = this.axisLabel(this.maxX * percent);
         int labelWidth = metrics.stringWidth(label);
         int x = (int)(yAxisWidth + this.chartWidth * percent);
         if (i != 0 && i != xSplit) {
            g.setColor(Color.lightGray);
            g.drawLine(x, this.chartY + 1, x, this.chartY + this.chartHeight);
            g.setColor(Color.black);
         }

         g.drawLine(x, ySplit - 4, x, ySplit - 8);
         if (this.isExpanded) {
            x -= labelWidth / 2;
            if (i == xSplit) {
               x = Math.min(x, width - labelWidth);
            }

            g.drawString(label, x, ySplit + this.numberHeight);
         }
      }

      this.maxY = 1;
      ySplit = this.isExpanded ? Math.min(10, this.chartHeight / (this.numberHeight * 3)) : 4;

      for (int i = 0; i <= ySplit; i++) {
         float percentx = (float)i / ySplit;
         String labelx = this.axisLabel(this.maxY * percentx);
         int labelWidthx = metrics.stringWidth(labelx);
         int y = (int)(this.chartY + this.chartHeight - this.chartHeight * percentx);
         if (this.isExpanded) {
            g.drawString(labelx, yAxisWidth - 6 - labelWidthx, y + this.numberHeight / 2);
         }

         if (i != 0 && i != ySplit) {
            g.setColor(Color.lightGray);
            g.drawLine(this.chartX, y, this.chartX + this.chartWidth - 1, y);
            g.setColor(Color.black);
         }

         g.drawLine(yAxisWidth - 4, y, yAxisWidth, y);
      }

      ySplit = metrics.stringWidth(this.title);
      xSplit = yAxisWidth + this.chartWidth / 2 - ySplit / 2;
      int yx = this.chartY + this.chartHeight / 2 - this.numberHeight / 2;
      g.setColor(Color.white);
      g.fillRect(xSplit - 2, yx - 2, ySplit + 4, this.numberHeight + 4);
      g.setColor(Color.lightGray);
      g.drawString(this.title, xSplit, yx + this.numberHeight);
      g.setColor(Color.blue);
      g.setStroke(new BasicStroke(this.isExpanded ? 3.0F : 2.0F));
      ySplit = -1;
      xSplit = -1;

      for (Chart.Point point : this.points) {
         Chart.Point pixel = this.pointToPixel(point);
         if (ySplit != -1) {
            g.drawLine(ySplit, xSplit, (int)pixel.x, (int)pixel.y);
         }

         ySplit = (int)pixel.x;
         xSplit = (int)pixel.y;
      }

      g.drawLine(ySplit, xSplit, this.chartX + this.chartWidth - 1, xSplit);
      yx = 0;

      for (int n = this.points.size(); yx < n; yx++) {
         Chart.Point point = this.points.get(yx);
         Chart.Point pixel = this.pointToPixel(point);
         if (this.overIndex == yx) {
            g.setColor(Color.red);
         } else {
            g.setColor(Color.black);
         }

         String labelxx = this.valueLabel(point.y);
         int labelWidthxx = metrics.stringWidth(labelxx);
         int pointSize = this.isExpanded ? 10 : 6;
         int xx = (int)pixel.x - pointSize / 2;
         int yxx = (int)pixel.y - pointSize / 2;
         g.fillOval(xx, yxx, pointSize, pointSize);
         if (this.isExpanded) {
            g.setColor(Color.black);
            xx = Math.max(this.chartX + 2, Math.min(this.chartX + this.chartWidth - labelWidthxx, xx));
            yxx -= 3;
            if (yxx < this.chartY + this.numberHeight + 3) {
               yxx += 27;
            } else if (n > 1) {
               Chart.Point comparePoint = yx == n - 1 ? this.points.get(yx - 1) : this.points.get(yx + 1);
               if (yxx < this.chartY + this.chartHeight - 27 && comparePoint.y > point.y) {
                  yxx += 27;
               }
            }

            g.drawString(labelxx, xx, yxx);
         }
      }
   }

   private String valueLabel(float value) {
      value = (int)(value * 1000.0F) / 10.0F;
      return value % 1.0F == 0.0F ? String.valueOf((int)value) + '%' : String.valueOf(value) + '%';
   }

   private String axisLabel(float value) {
      value = (int)(value * 100.0F);
      return value % 1.0F == 0.0F ? String.valueOf((int)value) + '%' : String.valueOf(value) + '%';
   }

   public boolean isExpanded() {
      return this.isExpanded;
   }

   public void setExpanded(boolean isExpanded) {
      this.isExpanded = isExpanded;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public static class Point {
      public float x;
      public float y;

      public Point() {
      }

      public Point(float x, float y) {
         this.x = x;
         this.y = y;
      }
   }
}
