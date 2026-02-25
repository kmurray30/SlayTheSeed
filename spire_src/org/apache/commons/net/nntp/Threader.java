package org.apache.commons.net.nntp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class Threader {
   public Threadable thread(List<? extends Threadable> messages) {
      return this.thread(messages);
   }

   public Threadable thread(Iterable<? extends Threadable> messages) {
      if (messages == null) {
         return null;
      } else {
         HashMap<String, ThreadContainer> idTable = new HashMap<>();

         for (Threadable t : messages) {
            if (!t.isDummy()) {
               this.buildContainer(t, idTable);
            }
         }

         if (idTable.isEmpty()) {
            return null;
         } else {
            ThreadContainer root = this.findRootSet(idTable);
            idTable.clear();
            HashMap<String, ThreadContainer> var5 = null;
            this.pruneEmptyContainers(root);
            root.reverseChildren();
            this.gatherSubjects(root);
            if (root.next != null) {
               throw new RuntimeException("root node has a next:" + root);
            } else {
               for (ThreadContainer r = root.child; r != null; r = r.next) {
                  if (r.threadable == null) {
                     r.threadable = r.child.threadable.makeDummy();
                  }
               }

               Threadable result = root.child == null ? null : root.child.threadable;
               root.flush();
               return result;
            }
         }
      }
   }

   private void buildContainer(Threadable threadable, HashMap<String, ThreadContainer> idTable) {
      String id = threadable.messageThreadId();
      ThreadContainer container = idTable.get(id);
      int bogusIdCount = 0;
      if (container != null) {
         if (container.threadable != null) {
            id = "<Bogus-id:" + ++bogusIdCount + ">";
            container = null;
         } else {
            container.threadable = threadable;
         }
      }

      if (container == null) {
         container = new ThreadContainer();
         container.threadable = threadable;
         idTable.put(id, container);
      }

      ThreadContainer parentRef = null;
      String[] references = threadable.messageThreadReferences();

      for (String refString : references) {
         ThreadContainer ref = idTable.get(refString);
         if (ref == null) {
            ref = new ThreadContainer();
            idTable.put(refString, ref);
         }

         if (parentRef != null && ref.parent == null && parentRef != ref && !ref.findChild(parentRef)) {
            ref.parent = parentRef;
            ref.next = parentRef.child;
            parentRef.child = ref;
         }

         parentRef = ref;
      }

      if (parentRef != null && (parentRef == container || container.findChild(parentRef))) {
         parentRef = null;
      }

      if (container.parent != null) {
         ThreadContainer prev = null;

         for (rest = container.parent.child; rest != null && rest != container; rest = rest.next) {
            prev = rest;
         }

         if (rest == null) {
            throw new RuntimeException("Didnt find " + container + " in parent" + container.parent);
         }

         if (prev == null) {
            container.parent.child = container.next;
         } else {
            prev.next = container.next;
         }

         container.next = null;
         container.parent = null;
      }

      if (parentRef != null) {
         container.parent = parentRef;
         container.next = parentRef.child;
         parentRef.child = container;
      }
   }

   private ThreadContainer findRootSet(HashMap<String, ThreadContainer> idTable) {
      ThreadContainer root = new ThreadContainer();

      for (Entry<String, ThreadContainer> entry : idTable.entrySet()) {
         ThreadContainer c = entry.getValue();
         if (c.parent == null) {
            if (c.next != null) {
               throw new RuntimeException("c.next is " + c.next.toString());
            }

            c.next = root.child;
            root.child = c;
         }
      }

      return root;
   }

   private void pruneEmptyContainers(ThreadContainer parent) {
      ThreadContainer prev = null;
      ThreadContainer container = parent.child;

      for (ThreadContainer next = container.next; container != null; next = next == null ? null : next.next) {
         if (container.threadable == null && container.child == null) {
            if (prev == null) {
               parent.child = container.next;
            } else {
               prev.next = container.next;
            }

            container = prev;
         } else if (container.threadable == null && container.child != null && (container.parent != null || container.child.next == null)) {
            ThreadContainer kids = container.child;
            if (prev == null) {
               parent.child = kids;
            } else {
               prev.next = kids;
            }

            ThreadContainer tail;
            for (tail = kids; tail.next != null; tail = tail.next) {
               tail.parent = container.parent;
            }

            tail.parent = container.parent;
            tail.next = container.next;
            next = kids;
            container = prev;
         } else if (container.child != null) {
            this.pruneEmptyContainers(container);
         }

         prev = container;
         container = next;
      }
   }

   private void gatherSubjects(ThreadContainer root) {
      int count = 0;

      for (ThreadContainer c = root.child; c != null; c = c.next) {
         count++;
      }

      HashMap<String, ThreadContainer> subjectTable = new HashMap<>((int)(count * 1.2), 0.9F);
      count = 0;

      for (ThreadContainer c = root.child; c != null; c = c.next) {
         Threadable threadable = c.threadable;
         if (threadable == null) {
            threadable = c.child.threadable;
         }

         String subj = threadable.simplifiedSubject();
         if (subj != null && subj.length() != 0) {
            ThreadContainer old = subjectTable.get(subj);
            if (old == null
               || c.threadable == null && old.threadable != null
               || old.threadable != null && old.threadable.subjectIsReply() && c.threadable != null && !c.threadable.subjectIsReply()) {
               subjectTable.put(subj, c);
               count++;
            }
         }
      }

      if (count != 0) {
         ThreadContainer prev = null;
         ThreadContainer c = root.child;

         for (ThreadContainer rest = c.next; c != null; rest = rest == null ? null : rest.next) {
            Threadable threadablex = c.threadable;
            if (threadablex == null) {
               threadablex = c.child.threadable;
            }

            String subj = threadablex.simplifiedSubject();
            if (subj != null && subj.length() != 0) {
               ThreadContainer old = subjectTable.get(subj);
               if (old != c) {
                  if (prev == null) {
                     root.child = c.next;
                  } else {
                     prev.next = c.next;
                  }

                  c.next = null;
                  if (old.threadable == null && c.threadable == null) {
                     ThreadContainer tail = old.child;

                     while (tail != null && tail.next != null) {
                        tail = tail.next;
                     }

                     if (tail != null) {
                        tail.next = c.child;
                     }

                     for (ThreadContainer var20 = c.child; var20 != null; var20 = var20.next) {
                        var20.parent = old;
                     }

                     c.child = null;
                  } else if (old.threadable != null && (c.threadable == null || !c.threadable.subjectIsReply() || old.threadable.subjectIsReply())) {
                     ThreadContainer newc = new ThreadContainer();
                     newc.threadable = old.threadable;
                     newc.child = old.child;

                     for (ThreadContainer tail = newc.child; tail != null; tail = tail.next) {
                        tail.parent = newc;
                     }

                     old.threadable = null;
                     old.child = null;
                     c.parent = old;
                     newc.parent = old;
                     old.child = c;
                     c.next = newc;
                  } else {
                     c.parent = old;
                     c.next = old.child;
                     old.child = c;
                  }

                  c = prev;
               }
            }

            prev = c;
            c = rest;
         }

         subjectTable.clear();
         HashMap<String, ThreadContainer> var14 = null;
      }
   }

   @Deprecated
   public Threadable thread(Threadable[] messages) {
      return messages == null ? null : this.thread(Arrays.asList(messages));
   }
}
