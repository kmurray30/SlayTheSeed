package com.megacrit.cardcrawl.map;

import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoomTypeAssigner {
   private static final Logger logger = LogManager.getLogger(RoomTypeAssigner.class.getName());

   public static void assignRowAsRoomType(ArrayList<MapRoomNode> row, Class<? extends AbstractRoom> c) {
      for (MapRoomNode n : row) {
         if (n.getRoom() == null) {
            try {
               n.setRoom(c.newInstance());
            } catch (IllegalAccessException | InstantiationException var5) {
               var5.printStackTrace();
            }
         }
      }
   }

   private static int getConnectedNonAssignedNodeCount(List<ArrayList<MapRoomNode>> map) {
      int count = 0;

      for (ArrayList<MapRoomNode> row : map) {
         for (MapRoomNode node : row) {
            if (node.hasEdges() && node.getRoom() == null) {
               count++;
            }
         }
      }

      return count;
   }

   private static ArrayList<MapRoomNode> getSiblings(List<ArrayList<MapRoomNode>> map, ArrayList<MapRoomNode> parents, MapRoomNode n) {
      ArrayList<MapRoomNode> siblings = new ArrayList<>();

      for (MapRoomNode parent : parents) {
         for (MapEdge parentEdge : parent.getEdges()) {
            MapRoomNode siblingNode = map.get(parentEdge.dstY).get(parentEdge.dstX);
            if (!siblingNode.equals(n)) {
               siblings.add(siblingNode);
            }
         }
      }

      return siblings;
   }

   private static boolean ruleSiblingMatches(ArrayList<MapRoomNode> siblings, AbstractRoom roomToBeSet) {
      List<Class<? extends AbstractRoom>> applicableRooms = Arrays.asList(
         RestRoom.class, MonsterRoom.class, EventRoom.class, MonsterRoomElite.class, ShopRoom.class
      );

      for (MapRoomNode siblingNode : siblings) {
         if (siblingNode.getRoom() != null
            && applicableRooms.contains(roomToBeSet.getClass())
            && roomToBeSet.getClass().equals(siblingNode.getRoom().getClass())) {
            return true;
         }
      }

      return false;
   }

   private static boolean ruleParentMatches(ArrayList<MapRoomNode> parents, AbstractRoom roomToBeSet) {
      List<Class<? extends AbstractRoom>> applicableRooms = Arrays.asList(RestRoom.class, TreasureRoom.class, ShopRoom.class, MonsterRoomElite.class);

      for (MapRoomNode parentNode : parents) {
         AbstractRoom parentRoom = parentNode.getRoom();
         if (parentRoom != null && applicableRooms.contains(roomToBeSet.getClass()) && roomToBeSet.getClass().equals(parentRoom.getClass())) {
            return true;
         }
      }

      return false;
   }

   private static boolean ruleAssignableToRow(MapRoomNode n, AbstractRoom roomToBeSet) {
      List<Class<? extends AbstractRoom>> applicableRooms = Arrays.asList(RestRoom.class, MonsterRoomElite.class);
      List<Class<RestRoom>> applicableRooms2 = Collections.singletonList(RestRoom.class);
      return n.y <= 4 && applicableRooms.contains(roomToBeSet.getClass()) ? false : n.y < 13 || !applicableRooms2.contains(roomToBeSet.getClass());
   }

   private static AbstractRoom getNextRoomTypeAccordingToRules(ArrayList<ArrayList<MapRoomNode>> map, MapRoomNode n, ArrayList<AbstractRoom> roomList) {
      ArrayList<MapRoomNode> parents = n.getParents();
      ArrayList<MapRoomNode> siblings = getSiblings(map, parents, n);

      for (AbstractRoom roomToBeSet : roomList) {
         if (ruleAssignableToRow(n, roomToBeSet)) {
            if (!ruleParentMatches(parents, roomToBeSet) && !ruleSiblingMatches(siblings, roomToBeSet)) {
               return roomToBeSet;
            }

            if (n.y == 0) {
               return roomToBeSet;
            }
         }
      }

      return null;
   }

   private static void lastMinuteNodeChecker(ArrayList<ArrayList<MapRoomNode>> map, MapRoomNode n) {
      for (ArrayList<MapRoomNode> row : map) {
         for (MapRoomNode node : row) {
            if (node != null && node.hasEdges() && node.getRoom() == null) {
               logger.info("INFO: Node=" + node.toString() + " was null. Changed to a MonsterRoom.");
               node.setRoom(new MonsterRoom());
            }
         }
      }
   }

   private static void assignRoomsToNodes(ArrayList<ArrayList<MapRoomNode>> map, ArrayList<AbstractRoom> roomList) {
      for (ArrayList<MapRoomNode> row : map) {
         for (MapRoomNode node : row) {
            if (node != null && node.hasEdges() && node.getRoom() == null) {
               AbstractRoom roomToBeSet = getNextRoomTypeAccordingToRules(map, node, roomList);
               if (roomToBeSet != null) {
                  node.setRoom(roomList.remove(roomList.indexOf(roomToBeSet)));
               }
            }
         }
      }
   }

   public static ArrayList<ArrayList<MapRoomNode>> distributeRoomsAcrossMap(Random rng, ArrayList<ArrayList<MapRoomNode>> map, ArrayList<AbstractRoom> roomList) {
      int nodeCount = getConnectedNonAssignedNodeCount(map);

      while (roomList.size() < nodeCount) {
         roomList.add(new MonsterRoom());
      }

      if (roomList.size() > nodeCount) {
         logger.info("WARNING: the roomList is larger than the number of connected nodes. Not all desired roomTypes will be used.");
      }

      Collections.shuffle(roomList, (java.util.Random)rng.random);
      assignRoomsToNodes(map, roomList);
      logger.info("#### Unassigned Rooms:");

      for (AbstractRoom r : roomList) {
         logger.info(r.getClass());
      }

      lastMinuteNodeChecker(map, null);
      return map;
   }
}
