package com.badlogic.gdx.maps.tiled.renderers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;

public class HexagonalTiledMapRenderer extends BatchTiledMapRenderer {
   private boolean staggerAxisX = true;
   private boolean staggerIndexEven = false;
   private float hexSideLength = 0.0F;

   public HexagonalTiledMapRenderer(TiledMap map) {
      super(map);
      this.init(map);
   }

   public HexagonalTiledMapRenderer(TiledMap map, float unitScale) {
      super(map, unitScale);
      this.init(map);
   }

   public HexagonalTiledMapRenderer(TiledMap map, Batch batch) {
      super(map, batch);
      this.init(map);
   }

   public HexagonalTiledMapRenderer(TiledMap map, float unitScale, Batch batch) {
      super(map, unitScale, batch);
      this.init(map);
   }

   private void init(TiledMap map) {
      String axis = map.getProperties().get("staggeraxis", String.class);
      if (axis != null) {
         if (axis.equals("x")) {
            this.staggerAxisX = true;
         } else {
            this.staggerAxisX = false;
         }
      }

      String index = map.getProperties().get("staggerindex", String.class);
      if (index != null) {
         if (index.equals("even")) {
            this.staggerIndexEven = true;
         } else {
            this.staggerIndexEven = false;
         }
      }

      Integer length = map.getProperties().get("hexsidelength", Integer.class);
      if (length != null) {
         this.hexSideLength = length.intValue();
      } else if (this.staggerAxisX) {
         length = map.getProperties().get("tilewidth", Integer.class);
         if (length != null) {
            this.hexSideLength = 0.5F * length.intValue();
         } else {
            TiledMapTileLayer tmtl = (TiledMapTileLayer)map.getLayers().get(0);
            this.hexSideLength = 0.5F * tmtl.getTileWidth();
         }
      } else {
         length = map.getProperties().get("tileheight", Integer.class);
         if (length != null) {
            this.hexSideLength = 0.5F * length.intValue();
         } else {
            TiledMapTileLayer tmtl = (TiledMapTileLayer)map.getLayers().get(0);
            this.hexSideLength = 0.5F * tmtl.getTileHeight();
         }
      }
   }

   @Override
   public void renderTileLayer(TiledMapTileLayer layer) {
      Color batchColor = this.batch.getColor();
      float color = Color.toFloatBits(batchColor.r, batchColor.g, batchColor.b, batchColor.a * layer.getOpacity());
      int layerWidth = layer.getWidth();
      int layerHeight = layer.getHeight();
      float layerTileWidth = layer.getTileWidth() * this.unitScale;
      float layerTileHeight = layer.getTileHeight() * this.unitScale;
      float layerHexLength = this.hexSideLength * this.unitScale;
      if (this.staggerAxisX) {
         float tileWidthLowerCorner = (layerTileWidth - layerHexLength) / 2.0F;
         float tileWidthUpperCorner = (layerTileWidth + layerHexLength) / 2.0F;
         float layerTileHeight50 = layerTileHeight * 0.5F;
         int row1 = Math.max(0, (int)((this.viewBounds.y - layerTileHeight50) / layerTileHeight));
         int row2 = Math.min(layerHeight, (int)((this.viewBounds.y + this.viewBounds.height + layerTileHeight) / layerTileHeight));
         int col1 = Math.max(0, (int)((this.viewBounds.x - tileWidthLowerCorner) / tileWidthUpperCorner));
         int col2 = Math.min(layerWidth, (int)((this.viewBounds.x + this.viewBounds.width + tileWidthUpperCorner) / tileWidthUpperCorner));
         int colA = this.staggerIndexEven == (col1 % 2 == 0) ? col1 + 1 : col1;
         int colB = this.staggerIndexEven == (col1 % 2 == 0) ? col1 : col1 + 1;

         for (int row = row2 - 1; row >= row1; row--) {
            for (int col = colA; col < col2; col += 2) {
               this.renderCell(layer.getCell(col, row), tileWidthUpperCorner * col, layerTileHeight50 + layerTileHeight * row, color);
            }

            for (int col = colB; col < col2; col += 2) {
               this.renderCell(layer.getCell(col, row), tileWidthUpperCorner * col, layerTileHeight * row, color);
            }
         }
      } else {
         float tileHeightLowerCorner = (layerTileHeight - layerHexLength) / 2.0F;
         float tileHeightUpperCorner = (layerTileHeight + layerHexLength) / 2.0F;
         float layerTileWidth50 = layerTileWidth * 0.5F;
         int row1 = Math.max(0, (int)((this.viewBounds.y - tileHeightLowerCorner) / tileHeightUpperCorner));
         int row2 = Math.min(layerHeight, (int)((this.viewBounds.y + this.viewBounds.height + tileHeightUpperCorner) / tileHeightUpperCorner));
         int col1 = Math.max(0, (int)((this.viewBounds.x - layerTileWidth50) / layerTileWidth));
         int col2 = Math.min(layerWidth, (int)((this.viewBounds.x + this.viewBounds.width + layerTileWidth) / layerTileWidth));
         float shiftX = 0.0F;

         for (int row = row2 - 1; row >= row1; row--) {
            if (row % 2 == 0 == this.staggerIndexEven) {
               shiftX = 0.0F;
            } else {
               shiftX = layerTileWidth50;
            }

            for (int col = col1; col < col2; col++) {
               this.renderCell(layer.getCell(col, row), layerTileWidth * col + shiftX, tileHeightUpperCorner * row, color);
            }
         }
      }
   }

   private void renderCell(TiledMapTileLayer.Cell cell, float x, float y, float color) {
      if (cell != null) {
         TiledMapTile tile = cell.getTile();
         if (tile != null) {
            if (tile instanceof AnimatedTiledMapTile) {
               return;
            }

            boolean flipX = cell.getFlipHorizontally();
            boolean flipY = cell.getFlipVertically();
            int rotations = cell.getRotation();
            TextureRegion region = tile.getTextureRegion();
            float x1 = x + tile.getOffsetX() * this.unitScale;
            float y1 = y + tile.getOffsetY() * this.unitScale;
            float x2 = x1 + region.getRegionWidth() * this.unitScale;
            float y2 = y1 + region.getRegionHeight() * this.unitScale;
            float u1 = region.getU();
            float v1 = region.getV2();
            float u2 = region.getU2();
            float v2 = region.getV();
            this.vertices[0] = x1;
            this.vertices[1] = y1;
            this.vertices[2] = color;
            this.vertices[3] = u1;
            this.vertices[4] = v1;
            this.vertices[5] = x1;
            this.vertices[6] = y2;
            this.vertices[7] = color;
            this.vertices[8] = u1;
            this.vertices[9] = v2;
            this.vertices[10] = x2;
            this.vertices[11] = y2;
            this.vertices[12] = color;
            this.vertices[13] = u2;
            this.vertices[14] = v2;
            this.vertices[15] = x2;
            this.vertices[16] = y1;
            this.vertices[17] = color;
            this.vertices[18] = u2;
            this.vertices[19] = v1;
            if (flipX) {
               float temp = this.vertices[3];
               this.vertices[3] = this.vertices[13];
               this.vertices[13] = temp;
               temp = this.vertices[8];
               this.vertices[8] = this.vertices[18];
               this.vertices[18] = temp;
            }

            if (flipY) {
               float temp = this.vertices[4];
               this.vertices[4] = this.vertices[14];
               this.vertices[14] = temp;
               temp = this.vertices[9];
               this.vertices[9] = this.vertices[19];
               this.vertices[19] = temp;
            }

            if (rotations == 2) {
               float tempU = this.vertices[3];
               this.vertices[3] = this.vertices[13];
               this.vertices[13] = tempU;
               tempU = this.vertices[8];
               this.vertices[8] = this.vertices[18];
               this.vertices[18] = tempU;
               float tempV = this.vertices[4];
               this.vertices[4] = this.vertices[14];
               this.vertices[14] = tempV;
               tempV = this.vertices[9];
               this.vertices[9] = this.vertices[19];
               this.vertices[19] = tempV;
            }

            this.batch.draw(region.getTexture(), this.vertices, 0, 20);
         }
      }
   }
}
