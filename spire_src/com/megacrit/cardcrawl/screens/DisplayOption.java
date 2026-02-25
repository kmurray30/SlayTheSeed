package com.megacrit.cardcrawl.screens;

public class DisplayOption implements Comparable<DisplayOption> {
   public int width;
   public int height;
   public String aspectRatio = " TAB TAB";

   public DisplayOption(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public DisplayOption(int width, int height, boolean showAspectRatio) {
      this.width = width;
      this.height = height;
      if (showAspectRatio) {
         this.appendAspectRatio();
      }
   }

   private void appendAspectRatio() {
      float ratio = (float)this.width / this.height;
      if (ratio > 1.25F && ratio < 1.26F) {
         this.aspectRatio = " (5:4)";
      } else if (ratio > 1.32F && ratio < 1.34F) {
         this.aspectRatio = " (4:3)";
      } else if (ratio > 1.76F && ratio < 1.78F) {
         this.aspectRatio = " (16:9)";
      } else if (ratio > 1.59F && ratio < 1.61F) {
         this.aspectRatio = " (16:10)";
      } else if (ratio > 2.32F && ratio < 2.34F) {
         this.aspectRatio = " (21:9)";
      } else {
         this.aspectRatio = " (" + String.format("#.##", ratio) + ")";
      }
   }

   public int compareTo(DisplayOption other) {
      if (this.width == other.width) {
         if (this.height == other.height) {
            return 0;
         } else {
            return this.height < other.height ? -1 : 1;
         }
      } else {
         return this.width < other.width ? -1 : 1;
      }
   }

   @Override
   public boolean equals(Object other) {
      return ((DisplayOption)other).width == this.width && ((DisplayOption)other).height == this.height;
   }

   @Override
   public String toString() {
      return "(" + this.width + "," + this.height + ")";
   }

   public String uiString() {
      return this.width + " x " + this.height + this.aspectRatio;
   }
}
