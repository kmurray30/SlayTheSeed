package com.badlogic.gdx.tools.distancefield;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class DistanceFieldGenerator {
   private Color color = Color.white;
   private int downscale = 1;
   private float spread = 1.0F;

   public Color getColor() {
      return this.color;
   }

   public void setColor(Color color) {
      this.color = color;
   }

   public int getDownscale() {
      return this.downscale;
   }

   public void setDownscale(int downscale) {
      if (downscale <= 0) {
         throw new IllegalArgumentException("downscale must be positive");
      } else {
         this.downscale = downscale;
      }
   }

   public float getSpread() {
      return this.spread;
   }

   public void setSpread(float spread) {
      if (spread <= 0.0F) {
         throw new IllegalArgumentException("spread must be positive");
      } else {
         this.spread = spread;
      }
   }

   private static int squareDist(int x1, int y1, int x2, int y2) {
      int dx = x1 - x2;
      int dy = y1 - y2;
      return dx * dx + dy * dy;
   }

   public BufferedImage generateDistanceField(BufferedImage inImage) {
      int inWidth = inImage.getWidth();
      int inHeight = inImage.getHeight();
      int outWidth = inWidth / this.downscale;
      int outHeight = inHeight / this.downscale;
      BufferedImage outImage = new BufferedImage(outWidth, outHeight, 6);
      boolean[][] bitmap = new boolean[inHeight][inWidth];

      for (int y = 0; y < inHeight; y++) {
         for (int x = 0; x < inWidth; x++) {
            bitmap[y][x] = this.isInside(inImage.getRGB(x, y));
         }
      }

      for (int y = 0; y < outHeight; y++) {
         for (int x = 0; x < outWidth; x++) {
            int centerX = x * this.downscale + this.downscale / 2;
            int centerY = y * this.downscale + this.downscale / 2;
            float signedDistance = this.findSignedDistance(centerX, centerY, bitmap);
            outImage.setRGB(x, y, this.distanceToRGB(signedDistance));
         }
      }

      return outImage;
   }

   private boolean isInside(int rgb) {
      return (rgb & 8421504) != 0 && (rgb & -2147483648) != 0;
   }

   private int distanceToRGB(float signedDistance) {
      float alpha = 0.5F + 0.5F * (signedDistance / this.spread);
      alpha = Math.min(1.0F, Math.max(0.0F, alpha));
      int alphaByte = (int)(alpha * 255.0F);
      return alphaByte << 24 | this.color.getRGB() & 16777215;
   }

   private float findSignedDistance(int centerX, int centerY, boolean[][] bitmap) {
      int width = bitmap[0].length;
      int height = bitmap.length;
      boolean base = bitmap[centerY][centerX];
      int delta = (int)Math.ceil(this.spread);
      int startX = Math.max(0, centerX - delta);
      int endX = Math.min(width - 1, centerX + delta);
      int startY = Math.max(0, centerY - delta);
      int endY = Math.min(height - 1, centerY + delta);
      int closestSquareDist = delta * delta;

      for (int y = startY; y <= endY; y++) {
         for (int x = startX; x <= endX; x++) {
            if (base != bitmap[y][x]) {
               int squareDist = squareDist(centerX, centerY, x, y);
               if (squareDist < closestSquareDist) {
                  closestSquareDist = squareDist;
               }
            }
         }
      }

      float closestDist = (float)Math.sqrt(closestSquareDist);
      return (base ? 1 : -1) * Math.min(closestDist, this.spread);
   }

   private static void usage() {
      System.out
         .println(
            "Generates a distance field image from a black and white input image.\nThe distance field image contains a solid color and stores the distance\nin the alpha channel.\n\nThe output file format is inferred from the file name.\n\nCommand line arguments: INFILE OUTFILE [OPTION...]\n\nPossible options:\n  --color rrggbb    color of output image (default: ffffff)\n  --downscale n     downscale by factor of n (default: 1)\n  --spread n        edge scan distance (default: 1)\n"
         );
   }

   public static void main(String[] args) {
      try {
         run(args);
      } catch (DistanceFieldGenerator.CommandLineArgumentException var2) {
         System.err.println("Error: " + var2.getMessage() + "\n");
         usage();
         System.exit(1);
      }
   }

   private static void run(String[] args) {
      DistanceFieldGenerator generator = new DistanceFieldGenerator();
      String inputFile = null;
      String outputFile = null;
      int i = 0;

      try {
         for (; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-")) {
               if ("--help".equals(arg)) {
                  usage();
                  System.exit(0);
               } else if ("--color".equals(arg)) {
                  generator.setColor(new Color(Integer.parseInt(args[++i], 16)));
               } else if ("--downscale".equals(arg)) {
                  generator.setDownscale(Integer.parseInt(args[++i]));
               } else {
                  if (!"--spread".equals(arg)) {
                     throw new DistanceFieldGenerator.CommandLineArgumentException("unknown option " + arg);
                  }

                  generator.setSpread(Float.parseFloat(args[++i]));
               }
            } else if (inputFile == null) {
               inputFile = arg;
            } else {
               if (outputFile != null) {
                  throw new DistanceFieldGenerator.CommandLineArgumentException("exactly two file names are expected");
               }

               outputFile = arg;
            }
         }
      } catch (IndexOutOfBoundsException var12) {
         throw new DistanceFieldGenerator.CommandLineArgumentException("option " + args[args.length - 1] + " requires an argument");
      } catch (NumberFormatException var13) {
         throw new DistanceFieldGenerator.CommandLineArgumentException(args[i] + " is not a number");
      }

      if (inputFile == null) {
         throw new DistanceFieldGenerator.CommandLineArgumentException("no input file specified");
      } else if (outputFile == null) {
         throw new DistanceFieldGenerator.CommandLineArgumentException("no output file specified");
      } else {
         String outputFormat = outputFile.substring(outputFile.lastIndexOf(46) + 1);
         if (!ImageIO.getImageWritersByFormatName(outputFormat).hasNext()) {
            throw new RuntimeException("No image writers found that can handle the format '" + outputFormat + "'");
         } else {
            BufferedImage input = null;

            try {
               input = ImageIO.read(new File(inputFile));
            } catch (IOException var11) {
               System.err.println("Failed to load image: " + var11.getMessage());
            }

            BufferedImage output = generator.generateDistanceField(input);

            try {
               ImageIO.write(output, outputFormat, new File(outputFile));
            } catch (IOException var10) {
               System.err.println("Failed to write output image: " + var10.getMessage());
            }
         }
      }
   }

   private static class CommandLineArgumentException extends IllegalArgumentException {
      public CommandLineArgumentException(String message) {
         super(message);
      }
   }
}
