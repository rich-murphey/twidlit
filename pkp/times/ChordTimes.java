/**
 * Copyright 2015 Pushkar Piggott
 *
 * ChordTimes.java
 */

package pkp.times;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import pkp.twiddle.Chord;
import pkp.util.AxisLabels;
import pkp.util.Persistent;
import pkp.util.Persist;
import pkp.util.Pref;
import pkp.util.Log;
import pkp.io.Io;

////////////////////////////////////////////////////////////////////////////////
// Chord 0 is not counted so we subtract 1 and use Chord.sm_VALUES counts.
public class ChordTimes implements Persistent {
   
   /////////////////////////////////////////////////////////////////////////////
   // without and with thumbkeys
   static final int sm_CHORD_TYPES = 2;
   public static final int sm_MAX_MSEC = 32000;

   /////////////////////////////////////////////////////////////////////////////
   public ChordTimes(boolean isKeys, boolean isRightHand) {
      this(isKeys, isRightHand, Pref.getInt("#.chord.times.stored", 16));
//System.out.println(getExtension());
   }

   /////////////////////////////////////////////////////////////////////////////
   public int getSpan() {
      return m_SPAN;
   }

   /////////////////////////////////////////////////////////////////////////////
   public boolean hasData() {
      return m_DataStatus != DataStatus.NONE;
   }

   /////////////////////////////////////////////////////////////////////////////
   public boolean isKeystrokes() {
      return m_KEYS;
   }

   /////////////////////////////////////////////////////////////////////////////
   public boolean isRightHand() {
      return m_RIGHTHAND;
   }

   /////////////////////////////////////////////////////////////////////////////
   public String getExtension() {
      return (isRightHand() ? "right." : "left.")
           + (isKeystrokes() ? "key." : "")
           + "chords";
   }

   /////////////////////////////////////////////////////////////////////////////
   public String header(String what, String time) {
      return "# " + what + ' ' + getExtension().replace('.', ' ') + '\n'
           + "# " + time + '\n';
   }

   /////////////////////////////////////////////////////////////////////////////
   public void clear() {
      File f = Io.createFile(Persist.getFolderName(), getFileName());
      if (f.exists() && !f.isDirectory()) {
         f.delete();
      }
      load();
   }
      
   /////////////////////////////////////////////////////////////////////////////
   public int[] getCounts() {
      int[] counts = new int[Chord.sm_VALUES];
      for (int c = 0; c < Chord.sm_VALUES; ++c) {
         counts[c] = getCount(c + 1, 0);
      }
      return counts;
   }

   /////////////////////////////////////////////////////////////////////////////
   public boolean add(int chord, int thumbKeys, int timeMs) {
//System.out.printf("add(chord %d thumbKeys %d time %d)%n", chord, thumbKeys, timeMs);
      if (timeMs == 0) {
         Log.warn("Adding zero chord time");
         timeMs = 1;
      }
      if (timeMs > Short.MAX_VALUE) {
         return false;
      }
      int thumb = Math.min(thumbKeys, 1);
      ++m_TotalSamples[thumb];
      addMean(false, chord, thumb);
      m_DataStatus = DataStatus.NEW;
      if ((chord & ~Chord.sm_VALUES) != 0) {
         Log.err(String.format("Chord value %d is not in the range [1..%d]\n", chord, Chord.sm_VALUES));
         chord &= Chord.sm_VALUES;
      }
      byte count = m_Counts[thumb][chord - 1];
      int i = count & (m_SPAN - 1);
//System.out.printf("timeMs %d m_Counts %d mean %d%n", timeMs, m_Counts[thumb][chord - 1], getMean(chord, thumb));
      m_Times[thumb][chord - 1][i] = (short)timeMs;
      ++i;
      int full = count & m_SPAN;
      if (i == m_SPAN) {
         i = 0;
         full = m_SPAN;
      }
      m_Counts[thumb][chord - 1] = (byte)(full | i);
      addMean(true, chord, thumb);
//System.out.println(list(m_Times[thumb][chord - 1]));
      return true;
   }

   /////////////////////////////////////////////////////////////////////////////
   public int getMean(int chord, int thumbKeys) {
      short[] sort = getIq(chord, thumbKeys);
      if (sort == null) {
         return 0;
      }
      int sum = 0;
      int i = 0;
      for (; sort[i] != -1; ++i) {
         sum += sort[i];
      }
      return sum / i;
   }

   /////////////////////////////////////////////////////////////////////////////
   int getRange(int chord, int thumbKeys) {
      short[] sort = getIq(chord, thumbKeys);
      if (sort == null) {
         return 0;
      }
      int i = Math.min(m_SPAN - 3, sort.length - 1);
      while (sort[i] != -1) {
         --i;
      }
      if (i == 1) {
         return 0;
      }
      getMinMax(sort, i);
//System.out.printf("getRange() max %d min %d%n", sort[i - 1], sort[i - 2]);
      return sort[i - 1] - sort[i - 2];
   }

   ////////////////////////////////////////////////////////////////////////////
   public int getMeanMean(int thumbKeys) {
      int thumb = Math.min(thumbKeys, 1);
      if (m_MeanCount[thumb] == 0) {
         return 0;
      }
      int meanMean = m_MeanSum[thumb] / m_MeanCount[thumb];
//System.out.printf("getMeanMean() thumb %d m_MeanSum[thumb] %d m_MeanCount[thumb] %d meanmean %d%n", thumb, m_MeanSum[thumb], m_MeanCount[thumb], meanMean);
      return meanMean;
   }

   ////////////////////////////////////////////////////////////////////////////
   public int getMeanCount(int thumbKeys) {
      return m_MeanCount[Math.min(thumbKeys, 1)];
   }

   ////////////////////////////////////////////////////////////////////////////
   public int getTotalSamples(int thumbKeys) {
      return m_TotalSamples[Math.min(thumbKeys, 1)];
   }

   ////////////////////////////////////////////////////////////////////////////
   public class FingerLabels implements AxisLabels {
      public int size() { return Chord.Finger.count(); }
      public String getLabel(int i) { return Chord.Finger.fromInt(i).toString(); }
   }

   ////////////////////////////////////////////////////////////////////////////
   public class FingerCountLabels implements AxisLabels {
      public int size() { return Chord.Finger.count(); }
      public String getLabel(int i) { return "" + (i + 1); }
   }

   ////////////////////////////////////////////////////////////////////////////
   public String comparePositions(boolean vertical) {
      if (getMeanCount(0) < Chord.sm_VALUES) {
         return "";
      }

      int nCounts[] = new int[Chord.Position.count()];
      int nSums[] = new int[Chord.Position.count()];
      for (int n = 0; n < Chord.Position.count(); ++n) {
         nCounts[0] = 0;
         nSums[0] = 0;
      }
      for (int c = 0; c < Chord.sm_VALUES; ++c) {
         int n = Chord.fromChordValue(c + 1).countFingers();
         ++nCounts[n - 1];
         nSums[n - 1] += getMean(c + 1, 0);
      }
      String nCountStr = "";
      String nMeanStr = "";
      for (int n = 0; n < Chord.Position.count(); ++n) {
         nCountStr += String.format("%5d ", nCounts[n]);
         nMeanStr += String.format("%5.0f ", (double)nSums[n] / nCounts[n]);
      }

      int gCounts[] = new int[Chord.positionGapLimit()];
      int gSums[] = new int[Chord.positionGapLimit()];
      for (int i = 0; i < Chord.positionGapLimit(); ++i) {
         gCounts[i] = 0;
         gSums[i] = 0;
      }
      int[] eg = new int[Chord.positionGapLimit()];
      for (int c = 0; c < Chord.sm_VALUES; ++c) {
         int g = Chord.fromChordValue(c + 1).countPositionGaps();
         if (eg[g] == 0) {
            eg[g] = c + 1;
         }
         ++gCounts[g];
         gSums[g] += getMean(c + 1, 0);
      }
      String gGapStr0 = "";
      String gGapStr1 = "";
      String gEgStr = "";
      String gCountStr = "";
      String gMeanStr = "";
      for (int g = 0; g < Chord.positionGapLimit(); ++g) {
         gGapStr0 += String.format("%5d ", g / 6); 
         gGapStr1 += g % 6 == 0
                     ? "      "
                     : String.format("%3d/6 ", g % 6);
         gEgStr += String.format("%5s ", 
                                 eg[g] == 0
                                 ? ""
                                 : Chord.fromChordValue(eg[g])); 
         gCountStr += String.format("%5d ", gCounts[g]);
         gMeanStr += String.format("%5.0f ", 
                        gCounts[g] == 0
                        ? 0.0
                        : (double)gSums[g] / gCounts[g]);
      }

      int lCounts[][] = new int[Chord.Finger.count()][];
      int lSums[][] = new int[Chord.Finger.count()][];
      for (int f = 0; f < Chord.Finger.count(); ++f) {
         lCounts[f] = new int[Chord.Position.count()];
         lSums[f] = new int[Chord.Position.count()];
         for (int b = 0; b < Chord.Position.count(); ++b) {
            lCounts[f][b] = 0;
            lSums[f][b] = 0;
         }
      }
      for (int c = 0; c < Chord.sm_VALUES; ++c) {
         Chord ch = Chord.fromChordValue(c + 1);
         if (ch.countPositions() == 1 && ch.countPositionGaps() == 0) {
            for (Chord.Position p : Chord.Position.values()) {
               if (p != Chord.Position.O && ch.contains(p)) {
                  int f = ch.countFingers() - 1;
                  ++lCounts[f][p.toInt()];
                  lSums[f][p.toInt()] += getMean(ch.toInt(), 0);
                  break;
               }
            }
         }
      }

      int sum = 0;
      int counts[][] = new int[Chord.Finger.count()][];
      int sums[][] = new int[Chord.Finger.count()][];
      for (int f = 0; f < Chord.Finger.count(); ++f) {
         counts[f] = new int[Chord.Position.count()];
         sums[f] = new int[Chord.Position.count()];
         for (int b = 0; b < Chord.Position.count(); ++b) {
            counts[f][b] = 0;
            sums[f][b] = 0;
         }
      }
      for (int c = 0; c < Chord.sm_VALUES; ++c) {
         sum += getMean(c + 1, 0);
         for (Chord.Finger f : Chord.Finger.values()) {
            for (Chord.Position b : Chord.Position.values()) {
               if (Chord.fromChordValue(c + 1).contains(f, b)) {
                  ++counts[f.toInt()][b.toInt()];
                  sums[f.toInt()][b.toInt()] += getMean(c + 1, 0);
               }
            }
         }
      }

      return '\n' 
           + String.format("Samples: %d%n", getTotalSamples(0))
           + String.format("Mean (msec): %d%n", sum / Chord.sm_VALUES)
           + "\nFingers in chord:   1     2     3     4"
           + "\nPossible chords:" + nCountStr
           + "\nMean (msec):    " + nMeanStr
           + '\n'
           + "\nGaps in chord: " + gGapStr0
           + "\n               " + gGapStr1
           + "\nExample chord: " + gEgStr
           + "\nChords:        " + gCountStr
           + "\nMean (msec):   " + gMeanStr
           + '\n'
           + "\n0 gap chords means (msec) by finger count\n" 
           + toString(new MeanNo0(lCounts, lSums), new FingerCountLabels(), vertical)
           + "\nSample chords per button\n"
           + toString(new Count(counts), new FingerLabels(), vertical)
           + "\nMean (msec)\n" 
           + toString(new Mean(counts, sums), new FingerLabels(), vertical)
           + "\nDiff from total mean (msec)\n" 
           + toString(new CompareToMean(counts, sums, (double)sum / Chord.sm_VALUES), new FingerLabels(), vertical)
           + "\nDiff from total mean (%)\n" 
           + toString(new CompareToMeanPercent(counts, sums, (double)sum / Chord.sm_VALUES), new FingerLabels(), vertical)
           + String.format("\nDiff from %s mean (msec)\n", Chord.Position.O)
           + toString(new CompareToNone(counts, sums), new FingerLabels(), vertical)
           + String.format("\nDiff from %s mean (%%)\n", Chord.Position.O)
           + toString(new CompareToNonePercent(counts, sums), new FingerLabels(), vertical);
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override
   public void persist(String tag) {
//System.out.println("persist " + getFileName());
      if (m_DataStatus != DataStatus.NEW) {
         return;
      }
      m_DataStatus = DataStatus.SAVED;
      byte[] data = new byte[sm_CHORD_TYPES * Chord.sm_VALUES * (1 + m_SPAN * 2)];
      ByteBuffer bb = ByteBuffer.wrap(data);
      for (int thumb = 0; thumb < sm_CHORD_TYPES; ++thumb) {
         for (int c = 0; c < Chord.sm_VALUES; ++c) {
            byte count = m_Counts[thumb][c];
            int first = 0;
            int limit = m_SPAN;
            if ((count & m_SPAN) != 0) {
               first = count & ~m_SPAN;
            } else {
               limit = count;
            }
            bb.put((byte)limit);
            short[] times = m_Times[thumb][c];
            for (int i = 0; i < limit; ++i) {
               bb.putShort(times[(i + first) % m_SPAN]);
            }
         }
      }
      File f = Io.createFile(Persist.getFolderName(), getFileName());
      FileOutputStream fos = null;
      try {
         fos = new FileOutputStream(f);
      } catch (FileNotFoundException e) {
         Log.warn("Failed to open: \"" + f.getPath() + "\".");
         return;
      }
      try {
         // use bb.size()
         fos.write(data, 0, data.length);
         fos.flush();
         fos.close();
      } catch (IOException e) {
         Log.warn("Failed to write: \"" + f.getPath() + "\".");
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   String getTimes(int chord, int thumbKeys) {
      int thumb = Math.min(thumbKeys, 1);
      int count = getCount(chord, thumb);
      String str = String.format("%d:", count);
      for (int i = 0; i < count; ++i) {
         str += String.format(" %d", m_Times[thumb][chord - 1][i]);
      }
      return str;
   }
   
   /////////////////////////////////////////////////////////////////////////////
   // returns the number of valid entries [0..m_SPAN]
   int getCount(int chord, int thumb) {
      int count = m_Counts[thumb][chord - 1] & m_SPAN;
      if (count != 0) {
         return count;
      }
      return m_Counts[thumb][chord - 1] & (m_SPAN - 1);
   }

   // Private //////////////////////////////////////////////////////////////////

   /////////////////////////////////////////////////////////////////////////////
   private static String toString(Output o, AxisLabels axis, boolean vertical) {
      final String fmt = String.format("%%%ds ", o.width());
      String str = "";
      if (vertical) {
         str = "       ";
         for (Chord.Position b : Chord.Position.values()) {
            str += String.format(fmt, b.reverse());
         }
         str += '\n';
         for (Chord.Finger f : Chord.Finger.values()) {
            str += String.format("%6s ", axis.getLabel(f.toInt()));
            for (Chord.Position b : Chord.Position.values()) {
               str += o.toString(f.toInt(), b.reverse().toInt());
            }
            str += '\n';
         }
      } else {
         str = "  ";
         for (int f = 0; f < Chord.Finger.count(); ++f) {
            str += String.format(fmt, axis.getLabel(f));
         }
         str += '\n';
         for (Chord.Position b : Chord.Position.values()) {
            String line = "";
            for (Chord.Finger f : Chord.Finger.values()) {
               line += o.toString(f.toInt(), b.reverse().toInt());
            }
            if (line.length() > 0) {
               str += String.format("%s ", b.reverse())
                    + line + '\n';
            }
         }
      }
      return str;
   }

   /////////////////////////////////////////////////////////////////////////////
   public interface Output {
      public String toString(int f, int b);
      public int width();
   }
   
   /////////////////////////////////////////////////////////////////////////////
   public class Count implements Output {
      public Count(int c[][]) {
         counts = c;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         String fmt = String.format("%%%dd ", width());
         return String.format(fmt, counts[f][b]);
      }
      private final int width = 5;
      private int counts[][];
   }

   /////////////////////////////////////////////////////////////////////////////
   public class Mean implements Output {
      public Mean(int c[][], int s[][]) {
         counts = c;
         sums = s;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         String fmt = String.format("%%%d.0f ", width());
         return String.format(fmt, (double)sums[f][b] / counts[f][b]);
      }
      private final int width = 5;
      private int counts[][];
      private int sums[][];
   }

   /////////////////////////////////////////////////////////////////////////////
   public class CompareToMean implements Output {
      public CompareToMean(int c[][], int s[][], double m) {
         counts = c;
         sums = s;
         mean = m;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         String fmt = String.format("%%%d.1f ", width());
         double button = (double)sums[f][b] / counts[f][b];
         return String.format(fmt, button - mean);
      }
      private final int width = 6;
      private int counts[][];
      private int sums[][];
      private double mean;
   }

   /////////////////////////////////////////////////////////////////////////////
   public class CompareToMeanPercent implements Output {
      public CompareToMeanPercent(int c[][], int s[][], double m) {
         counts = c;
         sums = s;
         mean = m;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         String fmt = String.format("%%%d.2f ", width());
         double button = (double)sums[f][b] / counts[f][b];
         return String.format(fmt, 100.0 * (button - mean) / mean);
      }
      private final int width = 6;
      private int counts[][];
      private int sums[][];
      private double mean;
   }

   /////////////////////////////////////////////////////////////////////////////
   public class CompareToNone implements Output {
      public CompareToNone(int c[][], int s[][]) {
         counts = c;
         sums = s;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         if (b == 0) {
            return "";
         }
         String fmt = String.format("%%%d.1f ", width());
         double button = (double)sums[f][b] / counts[f][b];
         double none = (double)sums[f][0] / counts[f][0];
         return String.format(fmt, button - none);
      }
      private final int width = 6;
      private int counts[][];
      private int sums[][];
   }

   /////////////////////////////////////////////////////////////////////////////
   public class CompareToNonePercent implements Output {
      public CompareToNonePercent(int c[][], int s[][]) {
         counts = c;
         sums = s;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         if (b == 0) {
            return "";
         }
         String fmt = String.format("%%%d.1f ", width());
         double button = (double)sums[f][b] / counts[f][b];
         double none = (double)sums[f][0] / counts[f][0];
         return String.format(fmt, 100.0 * (button - none) / none);
      }
      private final int width = 5;
      private int counts[][];
      private int sums[][];
   }

   /////////////////////////////////////////////////////////////////////////////
   public class MeanNo0 implements Output {
      public MeanNo0(int c[][], int s[][]) {
         counts = c;
         sums = s;
      }
      public int width() {
         return width;
      }
      public String toString(int f, int b) {
         if (b == 0) {
            return "";
         }
         String fmt = String.format("%%%d.1f ", width());
         double button = (double)sums[f][b] / counts[f][b];
         return String.format(fmt, button);
      }
      private final int width = 6;
      private int counts[][];
      private int sums[][];
   }

   /////////////////////////////////////////////////////////////////////////////
   private static void legalSpan(int span) {
      if (span <= 64) {
         for (int i = 0; i < 6; ++i) {
            if ((span & 1 << i) != 0
             && (span & ~(1 << i)) == 0) {
               return;
            }
         }
      }
      Log.err(String.format("chord.times.span [%d] must be one of [1, 2, 4, 8, 16, 32, 64].", span));
   }

   /////////////////////////////////////////////////////////////////////////////
   private static String list(short[] a) {
      String str = "";
      for (int i = 0; i < a.length; ++i) {
         str += String.format("%d ", a[i]);
      }
      return str;
   }

   /////////////////////////////////////////////////////////////////////////////
   private ChordTimes(boolean isKeys, boolean isRightHand, int span) {
      m_SPAN = span;
      legalSpan(m_SPAN);
      m_KEYS = isKeys;
      m_RIGHTHAND = isRightHand;
      load();
   }

   ////////////////////////////////////////////////////////////////////////////
   // false: remove (oldest) mean for a span of a chord.
   // if it was empty then this is a new chord, so increment count.
   // true: add (new) mean
   private void addMean(boolean add, int chord, int thumb) {
      int mean = getMean(chord, thumb);
      if (mean == 0) {
         if (add) {
            Log.err("Added zero time");
            return;
         }
         mean = 0;
         ++m_MeanCount[thumb];
      }
      m_MeanSum[thumb] += add ? mean : -mean;
//System.out.printf("addMean() add %b chord %d thumb %d mean %d m_MeanCount[thumb] %d m_MeanSum[thumb] %d%n", add, chord, thumb, mean, m_MeanCount[thumb], m_MeanSum[thumb]);
   }

   /////////////////////////////////////////////////////////////////////////////
   // returns the InterQuartile values terminated by -1
   private short[] getIq(int chord, int thumbKeys) {
      int thumb = Math.min(thumbKeys, 1);
      int end = getCount(chord, thumb);
      if (end == 0) {
         // no time
         return null;
      }
      short[] sort = java.util.Arrays.copyOf(m_Times[thumb][chord - 1], end + 1);
      if (end > 2) {
         int iq = end / 2;
         for (; end > iq; end -= 2) {
            getMinMax(sort, end);
         }
      }
      sort[end] = -1;
//System.out.println("getIq() " + list(sort));
      return sort;
   }

   /////////////////////////////////////////////////////////////////////////////
   // move min and max to end
   private void getMinMax(short[] data, int count) {
      int min = count - 2;
      int max = count - 1;
      if (data[max] < data[min]) {
         short swap = data[max];
         data[max] = data[min];
         data[min] = swap;
      }
      for (int i = 0; i < min; ++i) {
         if (data[max] < data[i]) {
            short swap = data[max];
            data[max] = data[i];
            data[i] = swap;
         }
         if (data[min] > data[i]) {
            short swap = data[min];
            data[min] = data[i];
            data[i] = swap;
         }
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   private void load() {
      // up to m_SPAN times for each
      m_Times = new short[sm_CHORD_TYPES][Chord.sm_VALUES][m_SPAN];
      // the actual number of times held
      m_Counts = new byte[sm_CHORD_TYPES][Chord.sm_VALUES];
      m_MeanSum = new int[sm_CHORD_TYPES];
      m_MeanSum[0] = 0;
      m_MeanSum[1] = 0;
      m_MeanCount = new int[sm_CHORD_TYPES];
      m_MeanCount[0] = 0;
      m_MeanCount[1] = 0;
      m_TotalSamples = new int[sm_CHORD_TYPES];
      m_TotalSamples[0] = 0;
      m_TotalSamples[1] = 0;
      File f = Io.createFile(Persist.getFolderName(), getFileName());
      if (!f.exists() || f.isDirectory()) {
         m_DataStatus = DataStatus.NONE;
         return;
      }
//System.out.println("load " + f.getPath());
      m_DataStatus = DataStatus.SAVED;
      byte[] data = new byte[(int)f.length()];
      FileInputStream fis = null;
      try {
         fis = new FileInputStream(f);
      } catch (FileNotFoundException e) {
         Log.log("No existing times");
         return;
      }
      try {
         fis.read(data, 0, data.length);
         fis.close();
      } catch (IOException e) {
         Log.err("Failed to read times: " + e);
      }
      ByteBuffer bb = ByteBuffer.wrap(data);
      for (int thumb = 0; thumb < sm_CHORD_TYPES; ++thumb) {
         for (int c = 0; c < Chord.sm_VALUES; ++c) {
            int count = bb.get();
            if (count > 0) {
               // increment count
               addMean(false, c + 1, thumb);
               m_Counts[thumb][c] = (byte)Math.min(count, m_SPAN);
               m_TotalSamples[thumb] += m_Counts[thumb][c];
               short[] times = m_Times[thumb][c];
               // skip olders if span < count
               int start = Math.max(0, count - m_SPAN);
               for (int i = 0; i < start; ++i) {
                  bb.getShort();
               }
               int end = count - start;
               for (int i = 0; i < end; ++i) {
                  times[i] = bb.getShort();
               }
               // add new mean
               addMean(true, c + 1, thumb);
            }
         }
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   private String getFileName() {
      return (m_KEYS ? "key" : "chord") + '.' + (m_RIGHTHAND ? "right" : "left") + ".times";
   }
      
   // Data /////////////////////////////////////////////////////////////////////

   /////////////////////////////////////////////////////////////////////////////
   enum DataStatus {
      NONE, SAVED, NEW;
   }
   
   // number off attempts we keep track of
   private final int m_SPAN;
   private final boolean m_KEYS;
   private final boolean m_RIGHTHAND;
   private DataStatus m_DataStatus;
   private short[][][] m_Times;
   private byte[][] m_Counts;
   private int[] m_MeanSum;
   private int[] m_MeanCount;
   private int[] m_TotalSamples;

   // Main /////////////////////////////////////////////////////////////////////
   public static void main(String[] args) {
      final int LIMIT = 100;
      Log.init(Log.ExitOnError);
      Persist.init("twidlit.properties", ".", "pref");
      Pref.init("twidlit.preferences", Persist.get("#.pref.dir"), "pref");
      int count = Integer.parseInt(args[2]);
      ChordTimes times = new ChordTimes(true, true, Integer.parseInt(args[0]));
      for (int i = 0; i < count; ++i) {
         times.add(1, 0, i + 1);
         times.add(2, 0, i + 3);
         times.add(Chord.sm_VALUES, 1, i + 1);
      }
      int limit = Math.min(count, times.getSpan());
      for (int i = 0; i < limit; ++i) {
         System.out.printf("ChordTimes %d %d %d%n", times.m_Times[0][0][i], times.m_Times[0][1][i], times.m_Times[1][Chord.sm_VALUES - 1][i]);
      }
      System.out.printf("Mean %d %d %d meanmean[0] %d%n", times.getMean(1, 0), times.getMean(2, 0), times.getMean(Chord.sm_VALUES, 1), times.getMeanMean(0));
      int size = Integer.parseInt(args[1]);
      if (size > 0) {
         times.persist("");
         times = new ChordTimes(true, true, size);
         limit = Math.min(count, times.getSpan());
         for (int i = 0; i < limit; ++i) {
            System.out.printf("ChordTimes %d %d%n", times.m_Times[0][0][i], times.m_Times[1][Chord.sm_VALUES - 1][i]);
         }
         System.out.printf("Mean %d %d meanmean[0] %d%n", times.getMean(1, 0), times.getMean(Chord.sm_VALUES, 1), times.getMeanMean(0));
      }
   }
}
