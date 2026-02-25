package com.megacrit.cardcrawl.map;

import java.util.Comparator;

class EdgeComparator implements Comparator<MapEdge> {
   public int compare(MapEdge e1, MapEdge e2) {
      return e1.compareTo(e2);
   }
}
