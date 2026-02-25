package com.badlogic.gdx.utils;

import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class JsonReader implements BaseJsonReader {
   private static final byte[] _json_actions = init__json_actions_0();
   private static final short[] _json_key_offsets = init__json_key_offsets_0();
   private static final char[] _json_trans_keys = init__json_trans_keys_0();
   private static final byte[] _json_single_lengths = init__json_single_lengths_0();
   private static final byte[] _json_range_lengths = init__json_range_lengths_0();
   private static final short[] _json_index_offsets = init__json_index_offsets_0();
   private static final byte[] _json_indicies = init__json_indicies_0();
   private static final byte[] _json_trans_targs = init__json_trans_targs_0();
   private static final byte[] _json_trans_actions = init__json_trans_actions_0();
   private static final byte[] _json_eof_actions = init__json_eof_actions_0();
   static final int json_start = 1;
   static final int json_first_final = 35;
   static final int json_error = 0;
   static final int json_en_object = 5;
   static final int json_en_array = 23;
   static final int json_en_main = 1;
   private final Array<JsonValue> elements = new Array<>(8);
   private final Array<JsonValue> lastChild = new Array<>(8);
   private JsonValue root;
   private JsonValue current;

   public JsonValue parse(String json) {
      char[] data = json.toCharArray();
      return this.parse(data, 0, data.length);
   }

   public JsonValue parse(Reader reader) {
      try {
         char[] data = new char[1024];
         int offset = 0;

         while (true) {
            int length = reader.read(data, offset, data.length - offset);
            if (length == -1) {
               return this.parse(data, 0, offset);
            }

            if (length == 0) {
               char[] newData = new char[data.length * 2];
               System.arraycopy(data, 0, newData, 0, data.length);
               data = newData;
            } else {
               offset += length;
            }
         }
      } catch (IOException var9) {
         throw new SerializationException(var9);
      } finally {
         StreamUtils.closeQuietly(reader);
      }
   }

   @Override
   public JsonValue parse(InputStream input) {
      JsonValue ex;
      try {
         ex = this.parse(new InputStreamReader(input, "UTF-8"));
      } catch (IOException var6) {
         throw new SerializationException(var6);
      } finally {
         StreamUtils.closeQuietly(input);
      }

      return ex;
   }

   @Override
   public JsonValue parse(FileHandle file) {
      try {
         return this.parse(file.reader("UTF-8"));
      } catch (Exception var3) {
         throw new SerializationException("Error parsing file: " + file, var3);
      }
   }

   public JsonValue parse(char[] data, int offset, int length) {
      int p = offset;
      int pe = length;
      int eof = length;
      int top = 0;
      int[] stack = new int[4];
      int s = 0;
      Array<String> names = new Array<>(8);
      boolean needsUnescape = false;
      boolean stringIsName = false;
      boolean stringIsUnquoted = false;
      RuntimeException parseRuntimeEx = null;
      boolean debug = false;
      if (debug) {
         System.out.println();
      }

      try {
         label502: {
            int cs = 1;
            top = 0;
            int _trans = 0;
            int _goto_targ = 0;

            label494:
            while (true) {
               label508: {
                  switch (_goto_targ) {
                     case 0:
                        if (p == pe) {
                           _goto_targ = 4;
                           continue;
                        }

                        if (cs == 0) {
                           _goto_targ = 5;
                           continue;
                        }
                     case 1:
                        break;
                     case 2:
                        break label508;
                     case 3:
                     case 5:
                     default:
                        break label502;
                     case 4:
                        break label494;
                  }

                  label481: {
                     int _keys = _json_key_offsets[cs];
                     _trans = _json_index_offsets[cs];
                     int _klen = _json_single_lengths[cs];
                     if (_klen > 0) {
                        int _lower = _keys;
                        int _upper = _keys + _klen - 1;

                        while (_upper >= _lower) {
                           int _mid = _lower + (_upper - _lower >> 1);
                           if (data[p] < _json_trans_keys[_mid]) {
                              _upper = _mid - 1;
                           } else {
                              if (data[p] <= _json_trans_keys[_mid]) {
                                 _trans += _mid - _keys;
                                 break label481;
                              }

                              _lower = _mid + 1;
                           }
                        }

                        _keys += _klen;
                        _trans += _klen;
                     }

                     int var36 = _json_range_lengths[cs];
                     if (var36 > 0) {
                        int _lower = _keys;
                        int _upper = _keys + (var36 << 1) - 2;

                        while (true) {
                           if (_upper < _lower) {
                              _trans += var36;
                              break;
                           }

                           int _mid = _lower + (_upper - _lower >> 1 & -2);
                           if (data[p] < _json_trans_keys[_mid]) {
                              _upper = _mid - 2;
                           } else {
                              if (data[p] <= _json_trans_keys[_mid + 1]) {
                                 _trans += _mid - _keys >> 1;
                                 break;
                              }

                              _lower = _mid + 2;
                           }
                        }
                     }
                  }

                  int var39 = _json_indicies[_trans];
                  cs = _json_trans_targs[var39];
                  if (_json_trans_actions[var39] != 0) {
                     int _acts = _json_trans_actions[var39];
                     int _nacts = _json_actions[_acts++];

                     while (_nacts-- > 0) {
                        switch (_json_actions[_acts++]) {
                           case 0:
                              stringIsName = true;
                              break;
                           case 1:
                              String value = new String(data, s, p - s);
                              if (needsUnescape) {
                                 value = this.unescape(value);
                              }

                              if (stringIsName) {
                                 stringIsName = false;
                                 if (debug) {
                                    System.out.println("name: " + value);
                                 }

                                 names.add(value);
                              } else {
                                 label516: {
                                    String name = names.size > 0 ? names.pop() : null;
                                    if (stringIsUnquoted) {
                                       if (value.equals("true")) {
                                          if (debug) {
                                             System.out.println("boolean: " + name + "=true");
                                          }

                                          this.bool(name, true);
                                          break label516;
                                       }

                                       if (value.equals("false")) {
                                          if (debug) {
                                             System.out.println("boolean: " + name + "=false");
                                          }

                                          this.bool(name, false);
                                          break label516;
                                       }

                                       if (value.equals("null")) {
                                          this.string(name, null);
                                          break label516;
                                       }

                                       boolean couldBeDouble = false;
                                       boolean couldBeLong = true;

                                       label431:
                                       for (int i = s; i < p; i++) {
                                          switch (data[i]) {
                                             case ',':
                                             case '/':
                                             case ':':
                                             case ';':
                                             case '<':
                                             case '=':
                                             case '>':
                                             case '?':
                                             case '@':
                                             case 'A':
                                             case 'B':
                                             case 'C':
                                             case 'D':
                                             case 'F':
                                             case 'G':
                                             case 'H':
                                             case 'I':
                                             case 'J':
                                             case 'K':
                                             case 'L':
                                             case 'M':
                                             case 'N':
                                             case 'O':
                                             case 'P':
                                             case 'Q':
                                             case 'R':
                                             case 'S':
                                             case 'T':
                                             case 'U':
                                             case 'V':
                                             case 'W':
                                             case 'X':
                                             case 'Y':
                                             case 'Z':
                                             case '[':
                                             case '\\':
                                             case ']':
                                             case '^':
                                             case '_':
                                             case '`':
                                             case 'a':
                                             case 'b':
                                             case 'c':
                                             case 'd':
                                             default:
                                                couldBeDouble = false;
                                                couldBeLong = false;
                                                break label431;
                                             case '.':
                                             case 'E':
                                             case 'e':
                                                couldBeDouble = true;
                                                couldBeLong = false;
                                                break;
                                             case '+':
                                             case '-':
                                             case '0':
                                             case '1':
                                             case '2':
                                             case '3':
                                             case '4':
                                             case '5':
                                             case '6':
                                             case '7':
                                             case '8':
                                             case '9':
                                          }
                                       }

                                       if (couldBeDouble) {
                                          try {
                                             if (debug) {
                                                System.out.println("double: " + name + "=" + Double.parseDouble(value));
                                             }

                                             this.number(name, Double.parseDouble(value), value);
                                             break label516;
                                          } catch (NumberFormatException var33) {
                                          }
                                       } else if (couldBeLong) {
                                          if (debug) {
                                             System.out.println("double: " + name + "=" + Double.parseDouble(value));
                                          }

                                          try {
                                             this.number(name, Long.parseLong(value), value);
                                             break label516;
                                          } catch (NumberFormatException var32) {
                                          }
                                       }
                                    }

                                    if (debug) {
                                       System.out.println("string: " + name + "=" + value);
                                    }

                                    this.string(name, value);
                                 }
                              }

                              stringIsUnquoted = false;
                              s = p;
                              break;
                           case 2:
                              String namex = names.size > 0 ? names.pop() : null;
                              if (debug) {
                                 System.out.println("startObject: " + namex);
                              }

                              this.startObject(namex);
                              if (top == stack.length) {
                                 int[] newStack = new int[stack.length * 2];
                                 System.arraycopy(stack, 0, newStack, 0, stack.length);
                                 stack = newStack;
                              }

                              stack[top++] = cs;
                              cs = 5;
                              _goto_targ = 2;
                              continue label494;
                           case 3:
                              if (debug) {
                                 System.out.println("endObject");
                              }

                              this.pop();
                              cs = stack[--top];
                              _goto_targ = 2;
                              continue label494;
                           case 4:
                              String namexx = names.size > 0 ? names.pop() : null;
                              if (debug) {
                                 System.out.println("startArray: " + namexx);
                              }

                              this.startArray(namexx);
                              if (top == stack.length) {
                                 int[] newStack = new int[stack.length * 2];
                                 System.arraycopy(stack, 0, newStack, 0, stack.length);
                                 stack = newStack;
                              }

                              stack[top++] = cs;
                              cs = 23;
                              _goto_targ = 2;
                              continue label494;
                           case 5:
                              if (debug) {
                                 System.out.println("endArray");
                              }

                              this.pop();
                              cs = stack[--top];
                              _goto_targ = 2;
                              continue label494;
                           case 6:
                              int start = p - 1;
                              if (data[p++] == '/') {
                                 while (p != eof && data[p] != '\n') {
                                    p++;
                                 }

                                 p--;
                              } else {
                                 while (p + 1 < eof && data[p] != '*' || data[p + 1] != '/') {
                                    p++;
                                 }

                                 p++;
                              }

                              if (debug) {
                                 System.out.println("comment " + new String(data, start, p - start));
                              }
                              break;
                           case 7:
                              if (debug) {
                                 System.out.println("unquotedChars");
                              }

                              s = p;
                              needsUnescape = false;
                              stringIsUnquoted = true;
                              if (stringIsName) {
                                 label375:
                                 do {
                                    switch (data[p]) {
                                       case '\n':
                                       case '\r':
                                       case ':':
                                          break label375;
                                       case '/':
                                          if (p + 1 != eof) {
                                             char c = data[p + 1];
                                             if (c == '/' || c == '*') {
                                                break label375;
                                             }
                                          }
                                          break;
                                       case '\\':
                                          needsUnescape = true;
                                    }

                                    if (debug) {
                                       System.out.println("unquotedChar (name): '" + data[p] + "'");
                                    }
                                 } while (++p != eof);
                              } else {
                                 label386:
                                 do {
                                    switch (data[p]) {
                                       case '\n':
                                       case '\r':
                                       case ',':
                                       case ']':
                                       case '}':
                                          break label386;
                                       case '/':
                                          if (p + 1 != eof) {
                                             char c = data[p + 1];
                                             if (c == '/' || c == '*') {
                                                break label386;
                                             }
                                          }
                                          break;
                                       case '\\':
                                          needsUnescape = true;
                                    }

                                    if (debug) {
                                       System.out.println("unquotedChar (value): '" + data[p] + "'");
                                    }
                                 } while (++p != eof);
                              }

                              p--;

                              while (Character.isSpace(data[p])) {
                                 p--;
                              }
                              break;
                           case 8:
                              if (debug) {
                                 System.out.println("quotedChars");
                              }

                              s = ++p;
                              needsUnescape = false;

                              label456:
                              do {
                                 switch (data[p]) {
                                    case '"':
                                       break label456;
                                    case '\\':
                                       needsUnescape = true;
                                       p++;
                                 }
                              } while (++p != eof);

                              p--;
                        }
                     }
                  }
               }

               if (cs == 0) {
                  _goto_targ = 5;
               } else {
                  if (++p == pe) {
                     break;
                  }

                  _goto_targ = 1;
               }
            }

            if (p == eof) {
               int __acts = _json_eof_actions[cs];
               int __nacts = _json_actions[__acts++];

               while (__nacts-- > 0) {
                  switch (_json_actions[__acts++]) {
                     case 1:
                        String valuex = new String(data, s, p - s);
                        if (needsUnescape) {
                           valuex = this.unescape(valuex);
                        }

                        if (stringIsName) {
                           stringIsName = false;
                           if (debug) {
                              System.out.println("name: " + valuex);
                           }

                           names.add(valuex);
                        } else {
                           label523: {
                              String namexxx = names.size > 0 ? names.pop() : null;
                              if (stringIsUnquoted) {
                                 if (valuex.equals("true")) {
                                    if (debug) {
                                       System.out.println("boolean: " + namexxx + "=true");
                                    }

                                    this.bool(namexxx, true);
                                    break label523;
                                 }

                                 if (valuex.equals("false")) {
                                    if (debug) {
                                       System.out.println("boolean: " + namexxx + "=false");
                                    }

                                    this.bool(namexxx, false);
                                    break label523;
                                 }

                                 if (valuex.equals("null")) {
                                    this.string(namexxx, null);
                                    break label523;
                                 }

                                 boolean couldBeDouble = false;
                                 boolean couldBeLong = true;

                                 label324:
                                 for (int i = s; i < p; i++) {
                                    switch (data[i]) {
                                       case ',':
                                       case '/':
                                       case ':':
                                       case ';':
                                       case '<':
                                       case '=':
                                       case '>':
                                       case '?':
                                       case '@':
                                       case 'A':
                                       case 'B':
                                       case 'C':
                                       case 'D':
                                       case 'F':
                                       case 'G':
                                       case 'H':
                                       case 'I':
                                       case 'J':
                                       case 'K':
                                       case 'L':
                                       case 'M':
                                       case 'N':
                                       case 'O':
                                       case 'P':
                                       case 'Q':
                                       case 'R':
                                       case 'S':
                                       case 'T':
                                       case 'U':
                                       case 'V':
                                       case 'W':
                                       case 'X':
                                       case 'Y':
                                       case 'Z':
                                       case '[':
                                       case '\\':
                                       case ']':
                                       case '^':
                                       case '_':
                                       case '`':
                                       case 'a':
                                       case 'b':
                                       case 'c':
                                       case 'd':
                                       default:
                                          couldBeDouble = false;
                                          couldBeLong = false;
                                          break label324;
                                       case '.':
                                       case 'E':
                                       case 'e':
                                          couldBeDouble = true;
                                          couldBeLong = false;
                                          break;
                                       case '+':
                                       case '-':
                                       case '0':
                                       case '1':
                                       case '2':
                                       case '3':
                                       case '4':
                                       case '5':
                                       case '6':
                                       case '7':
                                       case '8':
                                       case '9':
                                    }
                                 }

                                 if (couldBeDouble) {
                                    try {
                                       if (debug) {
                                          System.out.println("double: " + namexxx + "=" + Double.parseDouble(valuex));
                                       }

                                       this.number(namexxx, Double.parseDouble(valuex), valuex);
                                       break label523;
                                    } catch (NumberFormatException var31) {
                                    }
                                 } else if (couldBeLong) {
                                    if (debug) {
                                       System.out.println("double: " + namexxx + "=" + Double.parseDouble(valuex));
                                    }

                                    try {
                                       this.number(namexxx, Long.parseLong(valuex), valuex);
                                       break label523;
                                    } catch (NumberFormatException var30) {
                                    }
                                 }
                              }

                              if (debug) {
                                 System.out.println("string: " + namexxx + "=" + valuex);
                              }

                              this.string(namexxx, valuex);
                           }
                        }

                        stringIsUnquoted = false;
                        s = p;
                  }
               }
            }
         }
      } catch (RuntimeException var34) {
         parseRuntimeEx = var34;
      }

      JsonValue root = this.root;
      this.root = null;
      this.current = null;
      this.lastChild.clear();
      if (p < pe) {
         int lineNumber = 1;

         for (int i = 0; i < p; i++) {
            if (data[i] == '\n') {
               lineNumber++;
            }
         }

         int startx = Math.max(0, p - 32);
         throw new SerializationException(
            "Error parsing JSON on line "
               + lineNumber
               + " near: "
               + new String(data, startx, p - startx)
               + "*ERROR*"
               + new String(data, p, Math.min(64, pe - p)),
            parseRuntimeEx
         );
      } else if (this.elements.size != 0) {
         JsonValue element = this.elements.peek();
         this.elements.clear();
         if (element != null && element.isObject()) {
            throw new SerializationException("Error parsing JSON, unmatched brace.");
         } else {
            throw new SerializationException("Error parsing JSON, unmatched bracket.");
         }
      } else if (parseRuntimeEx != null) {
         throw new SerializationException("Error parsing JSON: " + new String(data), parseRuntimeEx);
      } else {
         return root;
      }
   }

   private static byte[] init__json_actions_0() {
      return new byte[]{0, 1, 1, 1, 2, 1, 3, 1, 4, 1, 5, 1, 6, 1, 7, 1, 8, 2, 0, 7, 2, 0, 8, 2, 1, 3, 2, 1, 5};
   }

   private static short[] init__json_key_offsets_0() {
      return new short[]{
         0,
         0,
         11,
         13,
         14,
         16,
         25,
         31,
         37,
         39,
         50,
         57,
         64,
         73,
         74,
         83,
         85,
         87,
         96,
         98,
         100,
         101,
         103,
         105,
         116,
         123,
         130,
         141,
         142,
         153,
         155,
         157,
         168,
         170,
         172,
         174,
         179,
         184,
         184
      };
   }

   private static char[] init__json_trans_keys_0() {
      return new char[]{
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '[',
         ']',
         '{',
         '\t',
         '\n',
         '*',
         '/',
         '"',
         '*',
         '/',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '}',
         '\t',
         '\n',
         '\r',
         ' ',
         '/',
         ':',
         '\t',
         '\n',
         '\r',
         ' ',
         '/',
         ':',
         '\t',
         '\n',
         '*',
         '/',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '[',
         ']',
         '{',
         '\t',
         '\n',
         '\t',
         '\n',
         '\r',
         ' ',
         ',',
         '/',
         '}',
         '\t',
         '\n',
         '\r',
         ' ',
         ',',
         '/',
         '}',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '}',
         '\t',
         '\n',
         '"',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '}',
         '\t',
         '\n',
         '*',
         '/',
         '*',
         '/',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '}',
         '\t',
         '\n',
         '*',
         '/',
         '*',
         '/',
         '"',
         '*',
         '/',
         '*',
         '/',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '[',
         ']',
         '{',
         '\t',
         '\n',
         '\t',
         '\n',
         '\r',
         ' ',
         ',',
         '/',
         ']',
         '\t',
         '\n',
         '\r',
         ' ',
         ',',
         '/',
         ']',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '[',
         ']',
         '{',
         '\t',
         '\n',
         '"',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '[',
         ']',
         '{',
         '\t',
         '\n',
         '*',
         '/',
         '*',
         '/',
         '\r',
         ' ',
         '"',
         ',',
         '/',
         ':',
         '[',
         ']',
         '{',
         '\t',
         '\n',
         '*',
         '/',
         '*',
         '/',
         '*',
         '/',
         '\r',
         ' ',
         '/',
         '\t',
         '\n',
         '\r',
         ' ',
         '/',
         '\t',
         '\n',
         '\u0000'
      };
   }

   private static byte[] init__json_single_lengths_0() {
      return new byte[]{0, 9, 2, 1, 2, 7, 4, 4, 2, 9, 7, 7, 7, 1, 7, 2, 2, 7, 2, 2, 1, 2, 2, 9, 7, 7, 9, 1, 9, 2, 2, 9, 2, 2, 2, 3, 3, 0, 0};
   }

   private static byte[] init__json_range_lengths_0() {
      return new byte[]{0, 1, 0, 0, 0, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0};
   }

   private static short[] init__json_index_offsets_0() {
      return new short[]{
         0,
         0,
         11,
         14,
         16,
         19,
         28,
         34,
         40,
         43,
         54,
         62,
         70,
         79,
         81,
         90,
         93,
         96,
         105,
         108,
         111,
         113,
         116,
         119,
         130,
         138,
         146,
         157,
         159,
         170,
         173,
         176,
         187,
         190,
         193,
         196,
         201,
         206,
         207
      };
   }

   private static byte[] init__json_indicies_0() {
      return new byte[]{
         1,
         1,
         2,
         3,
         4,
         3,
         5,
         3,
         6,
         1,
         0,
         7,
         7,
         3,
         8,
         3,
         9,
         9,
         3,
         11,
         11,
         12,
         13,
         14,
         3,
         15,
         11,
         10,
         16,
         16,
         17,
         18,
         16,
         3,
         19,
         19,
         20,
         21,
         19,
         3,
         22,
         22,
         3,
         21,
         21,
         24,
         3,
         25,
         3,
         26,
         3,
         27,
         21,
         23,
         28,
         29,
         28,
         28,
         30,
         31,
         32,
         3,
         33,
         34,
         33,
         33,
         13,
         35,
         15,
         3,
         34,
         34,
         12,
         36,
         37,
         3,
         15,
         34,
         10,
         16,
         3,
         36,
         36,
         12,
         3,
         38,
         3,
         3,
         36,
         10,
         39,
         39,
         3,
         40,
         40,
         3,
         13,
         13,
         12,
         3,
         41,
         3,
         15,
         13,
         10,
         42,
         42,
         3,
         43,
         43,
         3,
         28,
         3,
         44,
         44,
         3,
         45,
         45,
         3,
         47,
         47,
         48,
         49,
         50,
         3,
         51,
         52,
         53,
         47,
         46,
         54,
         55,
         54,
         54,
         56,
         57,
         58,
         3,
         59,
         60,
         59,
         59,
         49,
         61,
         52,
         3,
         60,
         60,
         48,
         62,
         63,
         3,
         51,
         52,
         53,
         60,
         46,
         54,
         3,
         62,
         62,
         48,
         3,
         64,
         3,
         51,
         3,
         53,
         62,
         46,
         65,
         65,
         3,
         66,
         66,
         3,
         49,
         49,
         48,
         3,
         67,
         3,
         51,
         52,
         53,
         49,
         46,
         68,
         68,
         3,
         69,
         69,
         3,
         70,
         70,
         3,
         8,
         8,
         71,
         8,
         3,
         72,
         72,
         73,
         72,
         3,
         3,
         3,
         0
      };
   }

   private static byte[] init__json_trans_targs_0() {
      return new byte[]{
         35,
         1,
         3,
         0,
         4,
         36,
         36,
         36,
         36,
         1,
         6,
         5,
         13,
         17,
         22,
         37,
         7,
         8,
         9,
         7,
         8,
         9,
         7,
         10,
         20,
         21,
         11,
         11,
         11,
         12,
         17,
         19,
         37,
         11,
         12,
         19,
         14,
         16,
         15,
         14,
         12,
         18,
         17,
         11,
         9,
         5,
         24,
         23,
         27,
         31,
         34,
         25,
         38,
         25,
         25,
         26,
         31,
         33,
         38,
         25,
         26,
         33,
         28,
         30,
         29,
         28,
         26,
         32,
         31,
         25,
         23,
         2,
         36,
         2
      };
   }

   private static byte[] init__json_trans_actions_0() {
      return new byte[]{
         13,
         0,
         15,
         0,
         0,
         7,
         3,
         11,
         1,
         11,
         17,
         0,
         20,
         0,
         0,
         5,
         1,
         1,
         1,
         0,
         0,
         0,
         11,
         13,
         15,
         0,
         7,
         3,
         1,
         1,
         1,
         1,
         23,
         0,
         0,
         0,
         0,
         0,
         0,
         11,
         11,
         0,
         11,
         11,
         11,
         11,
         13,
         0,
         15,
         0,
         0,
         7,
         9,
         3,
         1,
         1,
         1,
         1,
         26,
         0,
         0,
         0,
         0,
         0,
         0,
         11,
         11,
         0,
         11,
         11,
         11,
         1,
         0,
         0
      };
   }

   private static byte[] init__json_eof_actions_0() {
      return new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0};
   }

   private void addChild(String name, JsonValue child) {
      child.setName(name);
      if (this.current == null) {
         this.current = child;
         this.root = child;
      } else if (!this.current.isArray() && !this.current.isObject()) {
         this.root = this.current;
      } else {
         child.parent = this.current;
         if (this.current.size == 0) {
            this.current.child = child;
         } else {
            JsonValue last = this.lastChild.pop();
            last.next = child;
            child.prev = last;
         }

         this.lastChild.add(child);
         this.current.size++;
      }
   }

   protected void startObject(String name) {
      JsonValue value = new JsonValue(JsonValue.ValueType.object);
      if (this.current != null) {
         this.addChild(name, value);
      }

      this.elements.add(value);
      this.current = value;
   }

   protected void startArray(String name) {
      JsonValue value = new JsonValue(JsonValue.ValueType.array);
      if (this.current != null) {
         this.addChild(name, value);
      }

      this.elements.add(value);
      this.current = value;
   }

   protected void pop() {
      this.root = this.elements.pop();
      if (this.current.size > 0) {
         this.lastChild.pop();
      }

      this.current = this.elements.size > 0 ? this.elements.peek() : null;
   }

   protected void string(String name, String value) {
      this.addChild(name, new JsonValue(value));
   }

   protected void number(String name, double value, String stringValue) {
      this.addChild(name, new JsonValue(value, stringValue));
   }

   protected void number(String name, long value, String stringValue) {
      this.addChild(name, new JsonValue(value, stringValue));
   }

   protected void bool(String name, boolean value) {
      this.addChild(name, new JsonValue(value));
   }

   private String unescape(String value) {
      int length = value.length();
      StringBuilder buffer = new StringBuilder(length + 16);
      int i = 0;

      while (i < length) {
         char c = value.charAt(i++);
         if (c != '\\') {
            buffer.append(c);
         } else {
            if (i == length) {
               break;
            }

            c = value.charAt(i++);
            if (c == 'u') {
               buffer.append(Character.toChars(Integer.parseInt(value.substring(i, i + 4), 16)));
               i += 4;
            } else {
               switch (c) {
                  case '"':
                  case '/':
                  case '\\':
                     break;
                  case 'b':
                     c = '\b';
                     break;
                  case 'f':
                     c = '\f';
                     break;
                  case 'n':
                     c = '\n';
                     break;
                  case 'r':
                     c = '\r';
                     break;
                  case 't':
                     c = '\t';
                     break;
                  default:
                     throw new SerializationException("Illegal escaped character: \\" + c);
               }

               buffer.append(c);
            }
         }
      }

      return buffer.toString();
   }
}
