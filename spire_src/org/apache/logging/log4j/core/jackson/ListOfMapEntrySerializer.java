package org.apache.logging.log4j.core.jackson;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class ListOfMapEntrySerializer extends StdSerializer<Map<String, String>> {
   private static final long serialVersionUID = 1L;

   protected ListOfMapEntrySerializer() {
      super(Map.class, false);
   }

   public void serialize(final Map<String, String> map, final JsonGenerator jgen, final SerializerProvider provider) throws IOException, JsonGenerationException {
      Set<Entry<String, String>> entrySet = map.entrySet();
      MapEntry[] pairs = new MapEntry[entrySet.size()];
      int i = 0;

      for (Entry<String, String> entry : entrySet) {
         pairs[i++] = new MapEntry(entry.getKey(), entry.getValue());
      }

      jgen.writeObject(pairs);
   }
}
