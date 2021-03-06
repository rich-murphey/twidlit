/**
 * Copyright 2016 Pushkar Piggott
 *
 * KeyPressSource.java
 *
 * A wrapper on a UniformSource to return KeyPressLists
 * representing keystrokes read from file.
 */

package pkp.source;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import pkp.twiddle.KeyPress;
import pkp.twiddle.KeyPressList;
import pkp.io.LineReader;
import pkp.io.Io;
import pkp.util.Pref;
import pkp.util.Log;

////////////////////////////////////////////////////////////////////////////////
public class KeyPressSource implements KeyPressListSource {

   /////////////////////////////////////////////////////////////////////////////
   public KeyPressSource(File f) {
      m_File = f;
      ArrayList<ArrayList<KeyPressList>> keys = new ArrayList<ArrayList<KeyPressList>>();
      if (m_File == null || !m_File.exists()) {
         keys.add(getDefault());
      } else {
         LineReader lr = new LineReader(Io.toExistUrl(m_File), Io.sm_MUST_EXIST);
         keys.add(getKeys(lr));
         lr.close();
      }
      m_UniformSource = new UniformSource<KeyPressList>(keys, 4);
   }
   
   ////////////////////////////////////////////////////////////////////////////
   @Override // KeyPressListSource
   public KeyPressListSource clone() { return new KeyPressSource(m_File); }
   @Override // KeyPressListSource
   public String getName() { return "RandomChords:"; }
   @Override // KeyPressListSource
   public String getFullName() { return getName(); }
   @Override // KeyPressListSource
   public KeyPressListSource getSource() { return null;  }
   @Override // KeyPressListSource
   public void close() {}

   /////////////////////////////////////////////////////////////////////////////
   @Override // KeyPressListSource
   public KeyPressList getNext() {
      return m_UniformSource.get();
   }

   /////////////////////////////////////////////////////////////////////////////
   @Override // KeyPressListSource
   public KeyPressListSource.Message send(KeyPressListSource.Message m) {
      m_UniformSource.next(m != null);
      return null;
   }

   // Private //////////////////////////////////////////////////////////////////

   /////////////////////////////////////////////////////////////////////////////
   // upper and lower case alphabetics by default
   private ArrayList<KeyPressList> getDefault() {
      final int span = 'z' - 'a' + 1;
      byte[] str = new byte[2 * span];
      for (int i = 0; i < span; ++i) {
         str[i * 2] = (byte)('a' + i);
         str[i * 2 + 1] = (byte)(' ');
      }
      ArrayList<KeyPressList> al = new ArrayList<KeyPressList>();
      add(al, new String(str), 1);
      return al;
   }

   /////////////////////////////////////////////////////////////////////////////
   private static ArrayList<KeyPressList> getKeys(LineReader lr) {
      ArrayList<KeyPressList> al = new ArrayList<KeyPressList>();
      String line;
      StringBuilder err = new StringBuilder();
      while ((line = lr.readLine()) != null) {
         line = line.trim();
         // read ":<num>" at end of line into times
         int times = 1;
         int at = line.lastIndexOf(':');
         // ignore if first or last character
         // or has spaces after
         if (at > 0 && at < line.length() - 1
          && line.substring(at).indexOf(' ') == -1) {
            int count = Io.toIntWarnParse(line.substring(at + 1), err);
            if (count == Io.sm_PARSE_FAILED) {
               Log.parseWarn(lr, err.toString(), line);
               err = new StringBuilder();
            } else {
               line = line.substring(0, at);
               if (count > 0) {
                  times = count;
               }
            }
         }
         add(al, line, times);
      }
      return al;
   }

   /////////////////////////////////////////////////////////////////////////////
   private static void add(ArrayList<KeyPressList> al, String str, int times) {
      ArrayList<KeyPressList> kpls = new ArrayList<KeyPressList>();
      List<String> strs = Io.split(str, ' ');
      for (int i = 0; i < strs.size(); ++i) {
         KeyPressList kpl = KeyPressList.parseTextAndTags(strs.get(i));
         if (kpl.isValid()) {
            kpls.add(kpl);
         }
      }
      for (int i = 0; i < times; ++i) {
         for (int j = 0; j < kpls.size(); ++j) {
            al.add(kpls.get(j));
         }
      }
   }

   // Data /////////////////////////////////////////////////////////////////////
   private File m_File;
   private UniformSource<KeyPressList> m_UniformSource;
}
