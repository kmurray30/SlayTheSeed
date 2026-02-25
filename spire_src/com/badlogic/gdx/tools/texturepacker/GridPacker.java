package com.badlogic.gdx.tools.texturepacker;

import com.badlogic.gdx.utils.Array;

public class GridPacker implements TexturePacker.Packer {
   private TexturePacker.Settings settings;

   public GridPacker(TexturePacker.Settings settings) {
      this.settings = settings;
   }

   @Override
   public Array<TexturePacker.Page> pack(Array<TexturePacker.Rect> inputRects) {
      if (!this.settings.silent) {
         System.out.print("Packing");
      }

      int cellWidth = 0;
      int cellHeight = 0;
      int i = 0;

      for (int nn = inputRects.size; i < nn; i++) {
         TexturePacker.Rect rect = inputRects.get(i);
         cellWidth = Math.max(cellWidth, rect.width);
         cellHeight = Math.max(cellHeight, rect.height);
      }

      cellWidth += this.settings.paddingX;
      cellHeight += this.settings.paddingY;
      inputRects.reverse();
      Array<TexturePacker.Page> pages = new Array<>();

      while (inputRects.size > 0) {
         TexturePacker.Page result = this.packPage(inputRects, cellWidth, cellHeight);
         pages.add(result);
      }

      return pages;
   }

   private TexturePacker.Page packPage(Array<TexturePacker.Rect> inputRects, int cellWidth, int cellHeight) {
      TexturePacker.Page page = new TexturePacker.Page();
      page.outputRects = new Array<>();
      int maxWidth = this.settings.maxWidth;
      int maxHeight = this.settings.maxHeight;
      if (this.settings.edgePadding) {
         maxWidth -= this.settings.paddingX;
         maxHeight -= this.settings.paddingY;
      }

      int x = 0;
      int y = 0;

      for (int i = inputRects.size - 1; i >= 0; i--) {
         if (x + cellWidth > maxWidth) {
            y += cellHeight;
            if (y > maxHeight - cellHeight) {
               break;
            }

            x = 0;
         }

         TexturePacker.Rect rect = inputRects.removeIndex(i);
         rect.x = x;
         rect.y = y;
         rect.width = rect.width + this.settings.paddingX;
         rect.height = rect.height + this.settings.paddingY;
         page.outputRects.add(rect);
         x += cellWidth;
         page.width = Math.max(page.width, x);
         page.height = Math.max(page.height, y + cellHeight);
      }

      for (int i = page.outputRects.size - 1; i >= 0; i--) {
         TexturePacker.Rect rect = page.outputRects.get(i);
         rect.y = page.height - rect.y - rect.height;
      }

      return page;
   }
}
