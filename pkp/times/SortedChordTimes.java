/**
 * SortedChordTimes.java
 */

package pkp.times;

import pkp.twiddle.Chord;
import pkp.lookup.SharedIndexableInts;
import pkp.lookup.SharedIndex;

////////////////////////////////////////////////////////////////////////////////
// Chord 0 is not counted so we subtract 1 and use Chord.sm_VALUES counts.
public class SortedChordTimes implements SharedIndexableInts {
   
   /////////////////////////////////////////////////////////////////////////////
   public SortedChordTimes(ChordTimes times) {
      m_Times = new int[Chord.sm_VALUES];
      m_Labels = new String[Chord.sm_VALUES];
      for (int i = 0; i < Chord.sm_VALUES; ++i) {
         m_Times[i] = times.getMean(i + 1, 0);
         int range = times.getRange(i + 1, 0);
         m_Labels[i] = (new Chord(i + 1)).toString()
                     + ((m_Times[i] == 0)
                        ? "" 
                        : String.format(" %d %d (", m_Times[i], range)
                         + times.getTimes(i + 1, 0) + ")");
      }
      m_Index = SharedIndex.create(this, 0, Integer.MAX_VALUE);
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override // SharedIndexableInts
   public int getSize() {
      return Chord.sm_VALUES;
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override // SharedIndexableInts
   public String getLabel(int i) {
      return m_Labels[i];
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override // SharedIndexableInts
   public int getCount(int i) {
      return m_Times[i];
   }

   ///////////////////////////////////////////////////////////////////////////////
   public String listChordsByTime() {
      String str = "";
      for (int i = 0; i < Chord.sm_VALUES; ++i) {
         str += m_Index.getLabel(i) + "\n";
      }
      return str;
   }
   
   ///////////////////////////////////////////////////////////////////////////////
   public String getSortedLabel(int i) {
      return m_Index.getLabel(i);
   }
   
   ///////////////////////////////////////////////////////////////////////////////
   public int getSortedValue(int i) {
      return m_Index.getValue(i);
   }
   
   // Private //////////////////////////////////////////////////////////////////
   // Data /////////////////////////////////////////////////////////////////////
   private int[] m_Times;
   private String[] m_Labels;
   private SharedIndex m_Index;
}