package com.badlogic.gdx.tools.texturepacker;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Sort;
import java.util.Comparator;

public class MaxRectsPacker implements TexturePacker.Packer {
   private MaxRectsPacker.RectComparator rectComparator = new MaxRectsPacker.RectComparator();
   private MaxRectsPacker.FreeRectChoiceHeuristic[] methods = MaxRectsPacker.FreeRectChoiceHeuristic.values();
   private MaxRectsPacker.MaxRects maxRects = new MaxRectsPacker.MaxRects();
   TexturePacker.Settings settings;
   private Sort sort = new Sort();

   public MaxRectsPacker(TexturePacker.Settings settings) {
      this.settings = settings;
      if (settings.minWidth > settings.maxWidth) {
         throw new RuntimeException("Page min width cannot be higher than max width.");
      } else if (settings.minHeight > settings.maxHeight) {
         throw new RuntimeException("Page min height cannot be higher than max height.");
      }
   }

   @Override
   public Array<TexturePacker.Page> pack(Array<TexturePacker.Rect> inputRects) {
      int i = 0;

      for (int nn = inputRects.size; i < nn; i++) {
         TexturePacker.Rect rect = inputRects.get(i);
         rect.width = rect.width + this.settings.paddingX;
         rect.height = rect.height + this.settings.paddingY;
      }

      if (this.settings.fast) {
         if (this.settings.rotation) {
            this.sort.sort(inputRects, new Comparator<TexturePacker.Rect>() {
               public int compare(TexturePacker.Rect o1, TexturePacker.Rect o2) {
                  int n1 = o1.width > o1.height ? o1.width : o1.height;
                  int n2 = o2.width > o2.height ? o2.width : o2.height;
                  return n2 - n1;
               }
            });
         } else {
            this.sort.sort(inputRects, new Comparator<TexturePacker.Rect>() {
               public int compare(TexturePacker.Rect o1, TexturePacker.Rect o2) {
                  return o2.width - o1.width;
               }
            });
         }
      }

      Array<TexturePacker.Page> pages = new Array<>();

      while (inputRects.size > 0) {
         TexturePacker.Page result = this.packPage(inputRects);
         pages.add(result);
         inputRects = result.remainingRects;
      }

      return pages;
   }

   private TexturePacker.Page packPage(Array<TexturePacker.Rect> inputRects) {
      int paddingX = this.settings.paddingX;
      int paddingY = this.settings.paddingY;
      float maxWidth = this.settings.maxWidth;
      float maxHeight = this.settings.maxHeight;
      int edgePaddingX = 0;
      int edgePaddingY = 0;
      if (this.settings.edgePadding) {
         if (this.settings.duplicatePadding) {
            maxWidth -= paddingX;
            maxHeight -= paddingY;
         } else {
            maxWidth -= paddingX * 2;
            maxHeight -= paddingY * 2;
            edgePaddingX = paddingX;
            edgePaddingY = paddingY;
         }
      }

      int minWidth = Integer.MAX_VALUE;
      int minHeight = Integer.MAX_VALUE;
      int i = 0;

      for (int nn = inputRects.size; i < nn; i++) {
         TexturePacker.Rect rect = inputRects.get(i);
         minWidth = Math.min(minWidth, rect.width);
         minHeight = Math.min(minHeight, rect.height);
         float width = rect.width - paddingX;
         float height = rect.height - paddingY;
         if (this.settings.rotation) {
            if ((width > maxWidth || height > maxHeight) && (width > maxHeight || height > maxWidth)) {
               String paddingMessage = edgePaddingX <= 0 && edgePaddingY <= 0 ? "" : " and edge padding " + paddingX + "," + paddingY;
               throw new RuntimeException(
                  "Image does not fit with max page size "
                     + this.settings.maxWidth
                     + "x"
                     + this.settings.maxHeight
                     + paddingMessage
                     + ": "
                     + rect.name
                     + "["
                     + width
                     + ","
                     + height
                     + "]"
               );
            }
         } else {
            if (width > maxWidth) {
               String paddingMessage = edgePaddingX > 0 ? " and X edge padding " + paddingX : "";
               throw new RuntimeException(
                  "Image does not fit with max page width " + this.settings.maxWidth + paddingMessage + ": " + rect.name + "[" + width + "," + height + "]"
               );
            }

            if (height > maxHeight && (!this.settings.rotation || width > maxHeight)) {
               String paddingMessage = edgePaddingY > 0 ? " and Y edge padding " + paddingY : "";
               throw new RuntimeException(
                  "Image does not fit in max page height " + this.settings.maxHeight + paddingMessage + ": " + rect.name + "[" + width + "," + height + "]"
               );
            }
         }
      }

      minWidth = Math.max(minWidth, this.settings.minWidth);
      minHeight = Math.max(minHeight, this.settings.minHeight);
      if (!this.settings.silent) {
         System.out.print("Packing");
      }

      TexturePacker.Page bestResult = null;
      if (this.settings.square) {
         int minSize = Math.max(minWidth, minHeight);
         int maxSize = Math.min(this.settings.maxWidth, this.settings.maxHeight);
         MaxRectsPacker.BinarySearch sizeSearch = new MaxRectsPacker.BinarySearch(minSize, maxSize, this.settings.fast ? 25 : 15, this.settings.pot);
         int size = sizeSearch.reset();
         int ix = 0;

         while (size != -1) {
            TexturePacker.Page result = this.packAtSize(true, size - edgePaddingX, size - edgePaddingY, inputRects);
            if (!this.settings.silent) {
               if (++ix % 70 == 0) {
                  System.out.println();
               }

               System.out.print(".");
            }

            bestResult = this.getBest(bestResult, result);
            size = sizeSearch.next(result == null);
         }

         if (!this.settings.silent) {
            System.out.println();
         }

         if (bestResult == null) {
            bestResult = this.packAtSize(false, maxSize - edgePaddingX, maxSize - edgePaddingY, inputRects);
         }

         this.sort.sort(bestResult.outputRects, this.rectComparator);
         bestResult.width = Math.max(bestResult.width, bestResult.height);
         bestResult.height = Math.max(bestResult.width, bestResult.height);
         return bestResult;
      } else {
         MaxRectsPacker.BinarySearch widthSearch = new MaxRectsPacker.BinarySearch(
            minWidth, this.settings.maxWidth, this.settings.fast ? 25 : 15, this.settings.pot
         );
         MaxRectsPacker.BinarySearch heightSearch = new MaxRectsPacker.BinarySearch(
            minHeight, this.settings.maxHeight, this.settings.fast ? 25 : 15, this.settings.pot
         );
         int width = widthSearch.reset();
         int ix = 0;
         int height = this.settings.square ? width : heightSearch.reset();

         while (true) {
            TexturePacker.Page bestWidthResult = null;

            while (width != -1) {
               TexturePacker.Page result = this.packAtSize(true, width - edgePaddingX, height - edgePaddingY, inputRects);
               if (!this.settings.silent) {
                  if (++ix % 70 == 0) {
                     System.out.println();
                  }

                  System.out.print(".");
               }

               bestWidthResult = this.getBest(bestWidthResult, result);
               width = widthSearch.next(result == null);
               if (this.settings.square) {
                  height = width;
               }
            }

            bestResult = this.getBest(bestResult, bestWidthResult);
            if (this.settings.square) {
               break;
            }

            height = heightSearch.next(bestWidthResult == null);
            if (height == -1) {
               break;
            }

            width = widthSearch.reset();
         }

         if (!this.settings.silent) {
            System.out.println();
         }

         if (bestResult == null) {
            bestResult = this.packAtSize(false, this.settings.maxWidth - edgePaddingX, this.settings.maxHeight - edgePaddingY, inputRects);
         }

         this.sort.sort(bestResult.outputRects, this.rectComparator);
         return bestResult;
      }
   }

   private TexturePacker.Page packAtSize(boolean fully, int width, int height, Array<TexturePacker.Rect> inputRects) {
      TexturePacker.Page bestResult = null;
      int i = 0;

      for (int n = this.methods.length; i < n; i++) {
         this.maxRects.init(width, height);
         TexturePacker.Page result;
         if (!this.settings.fast) {
            result = this.maxRects.pack(inputRects, this.methods[i]);
         } else {
            Array<TexturePacker.Rect> remaining = new Array<>();
            int ii = 0;

            for (int nn = inputRects.size; ii < nn; ii++) {
               TexturePacker.Rect rect = inputRects.get(ii);
               if (this.maxRects.insert(rect, this.methods[i]) == null) {
                  while (ii < nn) {
                     remaining.add(inputRects.get(ii++));
                  }
               }
            }

            result = this.maxRects.getResult();
            result.remainingRects = remaining;
         }

         if ((!fully || result.remainingRects.size <= 0) && result.outputRects.size != 0) {
            bestResult = this.getBest(bestResult, result);
         }
      }

      return bestResult;
   }

   private TexturePacker.Page getBest(TexturePacker.Page result1, TexturePacker.Page result2) {
      if (result1 == null) {
         return result2;
      } else if (result2 == null) {
         return result1;
      } else {
         return result1.occupancy > result2.occupancy ? result1 : result2;
      }
   }

   static class BinarySearch {
      int min;
      int max;
      int fuzziness;
      int low;
      int high;
      int current;
      boolean pot;

      public BinarySearch(int min, int max, int fuzziness, boolean pot) {
         this.pot = pot;
         this.fuzziness = pot ? 0 : fuzziness;
         this.min = pot ? (int)(Math.log(MathUtils.nextPowerOfTwo(min)) / Math.log(2.0)) : min;
         this.max = pot ? (int)(Math.log(MathUtils.nextPowerOfTwo(max)) / Math.log(2.0)) : max;
      }

      public int reset() {
         this.low = this.min;
         this.high = this.max;
         this.current = this.low + this.high >>> 1;
         return this.pot ? (int)Math.pow(2.0, this.current) : this.current;
      }

      public int next(boolean result) {
         if (this.low >= this.high) {
            return -1;
         } else {
            if (result) {
               this.low = this.current + 1;
            } else {
               this.high = this.current - 1;
            }

            this.current = this.low + this.high >>> 1;
            if (Math.abs(this.low - this.high) < this.fuzziness) {
               return -1;
            } else {
               return this.pot ? (int)Math.pow(2.0, this.current) : this.current;
            }
         }
      }
   }

   public static enum FreeRectChoiceHeuristic {
      BestShortSideFit,
      BestLongSideFit,
      BestAreaFit,
      BottomLeftRule,
      ContactPointRule;
   }

   class MaxRects {
      private int binWidth;
      private int binHeight;
      private final Array<TexturePacker.Rect> usedRectangles = new Array<>();
      private final Array<TexturePacker.Rect> freeRectangles = new Array<>();

      public void init(int width, int height) {
         this.binWidth = width;
         this.binHeight = height;
         this.usedRectangles.clear();
         this.freeRectangles.clear();
         TexturePacker.Rect n = new TexturePacker.Rect();
         n.x = 0;
         n.y = 0;
         n.width = width;
         n.height = height;
         this.freeRectangles.add(n);
      }

      public TexturePacker.Rect insert(TexturePacker.Rect rect, MaxRectsPacker.FreeRectChoiceHeuristic method) {
         TexturePacker.Rect newNode = this.scoreRect(rect, method);
         if (newNode.height == 0) {
            return null;
         } else {
            int numRectanglesToProcess = this.freeRectangles.size;

            for (int i = 0; i < numRectanglesToProcess; i++) {
               if (this.splitFreeNode(this.freeRectangles.get(i), newNode)) {
                  this.freeRectangles.removeIndex(i);
                  i--;
                  numRectanglesToProcess--;
               }
            }

            this.pruneFreeList();
            TexturePacker.Rect bestNode = new TexturePacker.Rect();
            bestNode.set(rect);
            bestNode.score1 = newNode.score1;
            bestNode.score2 = newNode.score2;
            bestNode.x = newNode.x;
            bestNode.y = newNode.y;
            bestNode.width = newNode.width;
            bestNode.height = newNode.height;
            bestNode.rotated = newNode.rotated;
            this.usedRectangles.add(bestNode);
            return bestNode;
         }
      }

      public TexturePacker.Page pack(Array<TexturePacker.Rect> rects, MaxRectsPacker.FreeRectChoiceHeuristic method) {
         rects = new Array<>(rects);

         while (rects.size > 0) {
            int bestRectIndex = -1;
            TexturePacker.Rect bestNode = new TexturePacker.Rect();
            bestNode.score1 = Integer.MAX_VALUE;
            bestNode.score2 = Integer.MAX_VALUE;

            for (int i = 0; i < rects.size; i++) {
               TexturePacker.Rect newNode = this.scoreRect(rects.get(i), method);
               if (newNode.score1 < bestNode.score1 || newNode.score1 == bestNode.score1 && newNode.score2 < bestNode.score2) {
                  bestNode.set(rects.get(i));
                  bestNode.score1 = newNode.score1;
                  bestNode.score2 = newNode.score2;
                  bestNode.x = newNode.x;
                  bestNode.y = newNode.y;
                  bestNode.width = newNode.width;
                  bestNode.height = newNode.height;
                  bestNode.rotated = newNode.rotated;
                  bestRectIndex = i;
               }
            }

            if (bestRectIndex == -1) {
               break;
            }

            this.placeRect(bestNode);
            rects.removeIndex(bestRectIndex);
         }

         TexturePacker.Page result = this.getResult();
         result.remainingRects = rects;
         return result;
      }

      public TexturePacker.Page getResult() {
         int w = 0;
         int h = 0;

         for (int i = 0; i < this.usedRectangles.size; i++) {
            TexturePacker.Rect rect = this.usedRectangles.get(i);
            w = Math.max(w, rect.x + rect.width);
            h = Math.max(h, rect.y + rect.height);
         }

         TexturePacker.Page result = new TexturePacker.Page();
         result.outputRects = new Array<>(this.usedRectangles);
         result.occupancy = this.getOccupancy();
         result.width = w;
         result.height = h;
         return result;
      }

      private void placeRect(TexturePacker.Rect node) {
         int numRectanglesToProcess = this.freeRectangles.size;

         for (int i = 0; i < numRectanglesToProcess; i++) {
            if (this.splitFreeNode(this.freeRectangles.get(i), node)) {
               this.freeRectangles.removeIndex(i);
               i--;
               numRectanglesToProcess--;
            }
         }

         this.pruneFreeList();
         this.usedRectangles.add(node);
      }

      private TexturePacker.Rect scoreRect(TexturePacker.Rect rect, MaxRectsPacker.FreeRectChoiceHeuristic method) {
         int width = rect.width;
         int height = rect.height;
         int rotatedWidth = height - MaxRectsPacker.this.settings.paddingY + MaxRectsPacker.this.settings.paddingX;
         int rotatedHeight = width - MaxRectsPacker.this.settings.paddingX + MaxRectsPacker.this.settings.paddingY;
         boolean rotate = rect.canRotate && MaxRectsPacker.this.settings.rotation;
         TexturePacker.Rect newNode = null;
         switch (method) {
            case BestShortSideFit:
               newNode = this.findPositionForNewNodeBestShortSideFit(width, height, rotatedWidth, rotatedHeight, rotate);
               break;
            case BottomLeftRule:
               newNode = this.findPositionForNewNodeBottomLeft(width, height, rotatedWidth, rotatedHeight, rotate);
               break;
            case ContactPointRule:
               newNode = this.findPositionForNewNodeContactPoint(width, height, rotatedWidth, rotatedHeight, rotate);
               newNode.score1 = -newNode.score1;
               break;
            case BestLongSideFit:
               newNode = this.findPositionForNewNodeBestLongSideFit(width, height, rotatedWidth, rotatedHeight, rotate);
               break;
            case BestAreaFit:
               newNode = this.findPositionForNewNodeBestAreaFit(width, height, rotatedWidth, rotatedHeight, rotate);
         }

         if (newNode.height == 0) {
            newNode.score1 = Integer.MAX_VALUE;
            newNode.score2 = Integer.MAX_VALUE;
         }

         return newNode;
      }

      private float getOccupancy() {
         int usedSurfaceArea = 0;

         for (int i = 0; i < this.usedRectangles.size; i++) {
            usedSurfaceArea += this.usedRectangles.get(i).width * this.usedRectangles.get(i).height;
         }

         return (float)usedSurfaceArea / (this.binWidth * this.binHeight);
      }

      private TexturePacker.Rect findPositionForNewNodeBottomLeft(int width, int height, int rotatedWidth, int rotatedHeight, boolean rotate) {
         TexturePacker.Rect bestNode = new TexturePacker.Rect();
         bestNode.score1 = Integer.MAX_VALUE;

         for (int i = 0; i < this.freeRectangles.size; i++) {
            if (this.freeRectangles.get(i).width >= width && this.freeRectangles.get(i).height >= height) {
               int topSideY = this.freeRectangles.get(i).y + height;
               if (topSideY < bestNode.score1 || topSideY == bestNode.score1 && this.freeRectangles.get(i).x < bestNode.score2) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = width;
                  bestNode.height = height;
                  bestNode.score1 = topSideY;
                  bestNode.score2 = this.freeRectangles.get(i).x;
                  bestNode.rotated = false;
               }
            }

            if (rotate && this.freeRectangles.get(i).width >= rotatedWidth && this.freeRectangles.get(i).height >= rotatedHeight) {
               int topSideY = this.freeRectangles.get(i).y + rotatedHeight;
               if (topSideY < bestNode.score1 || topSideY == bestNode.score1 && this.freeRectangles.get(i).x < bestNode.score2) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = rotatedWidth;
                  bestNode.height = rotatedHeight;
                  bestNode.score1 = topSideY;
                  bestNode.score2 = this.freeRectangles.get(i).x;
                  bestNode.rotated = true;
               }
            }
         }

         return bestNode;
      }

      private TexturePacker.Rect findPositionForNewNodeBestShortSideFit(int width, int height, int rotatedWidth, int rotatedHeight, boolean rotate) {
         TexturePacker.Rect bestNode = new TexturePacker.Rect();
         bestNode.score1 = Integer.MAX_VALUE;

         for (int i = 0; i < this.freeRectangles.size; i++) {
            if (this.freeRectangles.get(i).width >= width && this.freeRectangles.get(i).height >= height) {
               int leftoverHoriz = Math.abs(this.freeRectangles.get(i).width - width);
               int leftoverVert = Math.abs(this.freeRectangles.get(i).height - height);
               int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
               int longSideFit = Math.max(leftoverHoriz, leftoverVert);
               if (shortSideFit < bestNode.score1 || shortSideFit == bestNode.score1 && longSideFit < bestNode.score2) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = width;
                  bestNode.height = height;
                  bestNode.score1 = shortSideFit;
                  bestNode.score2 = longSideFit;
                  bestNode.rotated = false;
               }
            }

            if (rotate && this.freeRectangles.get(i).width >= rotatedWidth && this.freeRectangles.get(i).height >= rotatedHeight) {
               int flippedLeftoverHoriz = Math.abs(this.freeRectangles.get(i).width - rotatedWidth);
               int flippedLeftoverVert = Math.abs(this.freeRectangles.get(i).height - rotatedHeight);
               int flippedShortSideFit = Math.min(flippedLeftoverHoriz, flippedLeftoverVert);
               int flippedLongSideFit = Math.max(flippedLeftoverHoriz, flippedLeftoverVert);
               if (flippedShortSideFit < bestNode.score1 || flippedShortSideFit == bestNode.score1 && flippedLongSideFit < bestNode.score2) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = rotatedWidth;
                  bestNode.height = rotatedHeight;
                  bestNode.score1 = flippedShortSideFit;
                  bestNode.score2 = flippedLongSideFit;
                  bestNode.rotated = true;
               }
            }
         }

         return bestNode;
      }

      private TexturePacker.Rect findPositionForNewNodeBestLongSideFit(int width, int height, int rotatedWidth, int rotatedHeight, boolean rotate) {
         TexturePacker.Rect bestNode = new TexturePacker.Rect();
         bestNode.score2 = Integer.MAX_VALUE;

         for (int i = 0; i < this.freeRectangles.size; i++) {
            if (this.freeRectangles.get(i).width >= width && this.freeRectangles.get(i).height >= height) {
               int leftoverHoriz = Math.abs(this.freeRectangles.get(i).width - width);
               int leftoverVert = Math.abs(this.freeRectangles.get(i).height - height);
               int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
               int longSideFit = Math.max(leftoverHoriz, leftoverVert);
               if (longSideFit < bestNode.score2 || longSideFit == bestNode.score2 && shortSideFit < bestNode.score1) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = width;
                  bestNode.height = height;
                  bestNode.score1 = shortSideFit;
                  bestNode.score2 = longSideFit;
                  bestNode.rotated = false;
               }
            }

            if (rotate && this.freeRectangles.get(i).width >= rotatedWidth && this.freeRectangles.get(i).height >= rotatedHeight) {
               int leftoverHoriz = Math.abs(this.freeRectangles.get(i).width - rotatedWidth);
               int leftoverVert = Math.abs(this.freeRectangles.get(i).height - rotatedHeight);
               int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
               int longSideFit = Math.max(leftoverHoriz, leftoverVert);
               if (longSideFit < bestNode.score2 || longSideFit == bestNode.score2 && shortSideFit < bestNode.score1) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = rotatedWidth;
                  bestNode.height = rotatedHeight;
                  bestNode.score1 = shortSideFit;
                  bestNode.score2 = longSideFit;
                  bestNode.rotated = true;
               }
            }
         }

         return bestNode;
      }

      private TexturePacker.Rect findPositionForNewNodeBestAreaFit(int width, int height, int rotatedWidth, int rotatedHeight, boolean rotate) {
         TexturePacker.Rect bestNode = new TexturePacker.Rect();
         bestNode.score1 = Integer.MAX_VALUE;

         for (int i = 0; i < this.freeRectangles.size; i++) {
            int areaFit = this.freeRectangles.get(i).width * this.freeRectangles.get(i).height - width * height;
            if (this.freeRectangles.get(i).width >= width && this.freeRectangles.get(i).height >= height) {
               int leftoverHoriz = Math.abs(this.freeRectangles.get(i).width - width);
               int leftoverVert = Math.abs(this.freeRectangles.get(i).height - height);
               int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
               if (areaFit < bestNode.score1 || areaFit == bestNode.score1 && shortSideFit < bestNode.score2) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = width;
                  bestNode.height = height;
                  bestNode.score2 = shortSideFit;
                  bestNode.score1 = areaFit;
                  bestNode.rotated = false;
               }
            }

            if (rotate && this.freeRectangles.get(i).width >= rotatedWidth && this.freeRectangles.get(i).height >= rotatedHeight) {
               int leftoverHoriz = Math.abs(this.freeRectangles.get(i).width - rotatedWidth);
               int leftoverVert = Math.abs(this.freeRectangles.get(i).height - rotatedHeight);
               int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
               if (areaFit < bestNode.score1 || areaFit == bestNode.score1 && shortSideFit < bestNode.score2) {
                  bestNode.x = this.freeRectangles.get(i).x;
                  bestNode.y = this.freeRectangles.get(i).y;
                  bestNode.width = rotatedWidth;
                  bestNode.height = rotatedHeight;
                  bestNode.score2 = shortSideFit;
                  bestNode.score1 = areaFit;
                  bestNode.rotated = true;
               }
            }
         }

         return bestNode;
      }

      private int commonIntervalLength(int i1start, int i1end, int i2start, int i2end) {
         return i1end >= i2start && i2end >= i1start ? Math.min(i1end, i2end) - Math.max(i1start, i2start) : 0;
      }

      private int contactPointScoreNode(int x, int y, int width, int height) {
         int score = 0;
         if (x == 0 || x + width == this.binWidth) {
            score += height;
         }

         if (y == 0 || y + height == this.binHeight) {
            score += width;
         }

         Array<TexturePacker.Rect> usedRectangles = this.usedRectangles;
         int i = 0;

         for (int n = usedRectangles.size; i < n; i++) {
            TexturePacker.Rect rect = usedRectangles.get(i);
            if (rect.x == x + width || rect.x + rect.width == x) {
               score += this.commonIntervalLength(rect.y, rect.y + rect.height, y, y + height);
            }

            if (rect.y == y + height || rect.y + rect.height == y) {
               score += this.commonIntervalLength(rect.x, rect.x + rect.width, x, x + width);
            }
         }

         return score;
      }

      private TexturePacker.Rect findPositionForNewNodeContactPoint(int width, int height, int rotatedWidth, int rotatedHeight, boolean rotate) {
         TexturePacker.Rect bestNode = new TexturePacker.Rect();
         bestNode.score1 = -1;
         Array<TexturePacker.Rect> freeRectangles = this.freeRectangles;
         int i = 0;

         for (int n = freeRectangles.size; i < n; i++) {
            TexturePacker.Rect free = freeRectangles.get(i);
            if (free.width >= width && free.height >= height) {
               int score = this.contactPointScoreNode(free.x, free.y, width, height);
               if (score > bestNode.score1) {
                  bestNode.x = free.x;
                  bestNode.y = free.y;
                  bestNode.width = width;
                  bestNode.height = height;
                  bestNode.score1 = score;
                  bestNode.rotated = false;
               }
            }

            if (rotate && free.width >= rotatedWidth && free.height >= rotatedHeight) {
               int score = this.contactPointScoreNode(free.x, free.y, rotatedWidth, rotatedHeight);
               if (score > bestNode.score1) {
                  bestNode.x = free.x;
                  bestNode.y = free.y;
                  bestNode.width = rotatedWidth;
                  bestNode.height = rotatedHeight;
                  bestNode.score1 = score;
                  bestNode.rotated = true;
               }
            }
         }

         return bestNode;
      }

      private boolean splitFreeNode(TexturePacker.Rect freeNode, TexturePacker.Rect usedNode) {
         if (usedNode.x < freeNode.x + freeNode.width
            && usedNode.x + usedNode.width > freeNode.x
            && usedNode.y < freeNode.y + freeNode.height
            && usedNode.y + usedNode.height > freeNode.y) {
            if (usedNode.x < freeNode.x + freeNode.width && usedNode.x + usedNode.width > freeNode.x) {
               if (usedNode.y > freeNode.y && usedNode.y < freeNode.y + freeNode.height) {
                  TexturePacker.Rect newNode = new TexturePacker.Rect(freeNode);
                  newNode.height = usedNode.y - newNode.y;
                  this.freeRectangles.add(newNode);
               }

               if (usedNode.y + usedNode.height < freeNode.y + freeNode.height) {
                  TexturePacker.Rect newNode = new TexturePacker.Rect(freeNode);
                  newNode.y = usedNode.y + usedNode.height;
                  newNode.height = freeNode.y + freeNode.height - (usedNode.y + usedNode.height);
                  this.freeRectangles.add(newNode);
               }
            }

            if (usedNode.y < freeNode.y + freeNode.height && usedNode.y + usedNode.height > freeNode.y) {
               if (usedNode.x > freeNode.x && usedNode.x < freeNode.x + freeNode.width) {
                  TexturePacker.Rect newNode = new TexturePacker.Rect(freeNode);
                  newNode.width = usedNode.x - newNode.x;
                  this.freeRectangles.add(newNode);
               }

               if (usedNode.x + usedNode.width < freeNode.x + freeNode.width) {
                  TexturePacker.Rect newNode = new TexturePacker.Rect(freeNode);
                  newNode.x = usedNode.x + usedNode.width;
                  newNode.width = freeNode.x + freeNode.width - (usedNode.x + usedNode.width);
                  this.freeRectangles.add(newNode);
               }
            }

            return true;
         } else {
            return false;
         }
      }

      private void pruneFreeList() {
         Array<TexturePacker.Rect> freeRectangles = this.freeRectangles;
         int i = 0;

         for (int n = freeRectangles.size; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
               TexturePacker.Rect rect1 = freeRectangles.get(i);
               TexturePacker.Rect rect2 = freeRectangles.get(j);
               if (this.isContainedIn(rect1, rect2)) {
                  freeRectangles.removeIndex(i);
                  i--;
                  n--;
                  break;
               }

               if (this.isContainedIn(rect2, rect1)) {
                  freeRectangles.removeIndex(j);
                  j--;
                  n--;
               }
            }
         }
      }

      private boolean isContainedIn(TexturePacker.Rect a, TexturePacker.Rect b) {
         return a.x >= b.x && a.y >= b.y && a.x + a.width <= b.x + b.width && a.y + a.height <= b.y + b.height;
      }
   }

   class RectComparator implements Comparator<TexturePacker.Rect> {
      public int compare(TexturePacker.Rect o1, TexturePacker.Rect o2) {
         return TexturePacker.Rect.getAtlasName(o1.name, MaxRectsPacker.this.settings.flattenPaths)
            .compareTo(TexturePacker.Rect.getAtlasName(o2.name, MaxRectsPacker.this.settings.flattenPaths));
      }
   }
}
