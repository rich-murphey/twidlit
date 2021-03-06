/**
 * Copyright 2015 Pushkar Piggott
 *
 * Twidlit.java
 */

package pkp.twidlit;

import java.awt.*;
import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.JFrame;
import java.io.File;
import java.util.ArrayList;
import pkp.text.TextPanel;
import pkp.twiddle.Assignment;
import pkp.twiddle.Chord;
import pkp.twiddle.ThumbKeys;
import pkp.twiddle.KeyMap;
import pkp.twiddle.KeyPress;
import pkp.twiddle.KeyPressList;
import pkp.twiddle.Twiddle;
import pkp.times.ChordTimes;
import pkp.ui.Splash;
import pkp.ui.PersistentFrame;
import pkp.util.Persist;
import pkp.util.Pref;
import pkp.util.Log;
import pkp.io.Io;

////////////////////////////////////////////////////////////////////////////////
class Twidlit 
   extends PersistentFrame 
   implements TwidlitInit, WindowListener, MouseListener, KeyListener, ActionListener, Log.Quitter {

   ////////////////////////////////////////////////////////////////////////////////
   // Gathers initial settings so source can be created once only.
   class Init implements TwidlitInit {
      public boolean setKeyMap(KeyMap km) {
         m_KeyMap = km;
         return true;
      }
      public void setRightHand(boolean right) {
         m_Right = right;
      }
      public void setChords() {
         m_IsKeyStrokes = false;
         m_File = null;
      }
      public boolean setKeystrokes(File f) {
         m_IsKeyStrokes = true;
         m_File = f;
         return true;
      }

      private KeyMap m_KeyMap;
      private boolean m_Right;
      private boolean m_IsKeyStrokes;
      private File m_File;
   }
   
   /////////////////////////////////////////////////////////////////////////////
   Twidlit(JFrame splash) {
      super();
      Color bg = Pref.getColor("#.background.color");
      UIManager.put("OptionPane.background", bg);
      UIManager.put("Panel.background", bg);
      UIManager.put("Button.background", bg);
      UIManager.put("CheckBox.background", bg);
      //UIManager.put("ScrollPane.background", bg);
      UIManager.put("ScrollBar.background", bg);
      // Spinner 
      UIManager.put("FormattedTextField.background", bg);
        // FileChooser list
      UIManager.put("List.background", bg);
      UIManager.put("TextField.background", bg);
      UIManager.put("ComboBox.background", bg);
      Log.setWindow(this);
      Log.setQuitter(this);
      setIconImage(Pref.getIcon().getImage());
      setFocusable(true);
      requestFocusInWindow();
      setFocusTraversalKeysEnabled(false);
      addWindowListener(this);
      addMouseListener(this);
      addKeyListener(this);
      setTitle(getClass().getSimpleName());
      setPersistName("#." + getClass().getSimpleName());
      setResizable(true);

      TwidlitMenu mb = new TwidlitMenu(this);
      setJMenuBar(mb);

      m_KeysPressed = new KeyPressList();
      setKeyWaitMsec(Pref.getInt("#.key.wait.msec"));
      m_StartTimeMs = 0;
      m_TimeMs = 0;
      m_ProgressFactor = Pref.getInt("#.chord.wait.timed.percent") / 100.0;
      
      pack();
      // uses Init and calls initialize()
      mb.start();
      splash.dispose();
   }

   /////////////////////////////////////////////////////////////////////////////
   TwidlitInit getInit() {
      return new Init();
   }

   /////////////////////////////////////////////////////////////////////////////
   void initialize(TwidlitInit init) {
      if (!(init instanceof Init)) {
         Log.err("Bad TwidlitInit");
         return;
      }
      if (m_ChordTimes != null
       || m_KeyMap != null
       || m_TextPanel != null) {
         Log.err("Repeated initialization");
         return;
      }
      Init in = (Init)init;
      m_TwiddlerWindow.setRightHand(in.m_Right);
      m_KeyMap = in.m_KeyMap;
      m_TextPanel = new TextPanel(m_KeyMap);
      setContentPane(m_TextPanel);
      if (in.m_IsKeyStrokes) {
         setKeystrokes(in.m_File);
      } else {
         setChords();
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   @Override // TwidlitInit
   public boolean setKeyMap(KeyMap km) {
      if (km == null || !km.getAssignments().hasChords()) {
         return false;
      }
      m_KeyMap = km;
      m_TextPanel.setKeyMap(m_KeyMap);
      if (start()) {
         return true;
      }
      setChords();
      return false;
   }
   
   /////////////////////////////////////////////////////////////////////////////
   KeyMap getKeyMap() {
      return m_KeyMap;
   }

   /////////////////////////////////////////////////////////////////////////////
   @Override // TwidlitInit
   public void setRightHand(boolean right) {
      if (right == isRightHand()) {
         return;
      }
      m_TwiddlerWindow.setRightHand(right);
      if (m_TextPanel.isChords()) {
         // new times need new chords
         setChords();
      } else {
         setChordTimes(!m_TextPanel.isChords());
         start();
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   boolean isRightHand() {
      return m_TwiddlerWindow.isRightHand();
   }

   /////////////////////////////////////////////////////////////////////////////
   @Override // TwidlitInit
   public void setChords() {
      setChordTimes(false);
      m_TwiddlerWindow.useDelay(false);
      m_TwiddlerWindow.setDistinctChordCount(Chord.sm_VALUES);
      m_TextPanel.setChords(m_ChordTimes.getCounts());
      start();
   }

   /////////////////////////////////////////////////////////////////////////////
   @Override // TwidlitInit
   public boolean setKeystrokes(File f) {
      if (!m_TextPanel.isKeystrokes()) {
         setChordTimes(true);
         m_TwiddlerWindow.useDelay(true);
      }
      m_TextPanel.setKeystrokes(f);
      m_TwiddlerWindow.setDistinctChordCount(0);
      if (start()) {
         return true;
      }
      setChords();
      return false;
   }

   /////////////////////////////////////////////////////////////////////////////
   public boolean isChords() {
      return m_TextPanel.isChords();
   }
   
   /////////////////////////////////////////////////////////////////////////////
   String getHomeDir() {
      return m_HomeDir;
   }

   /////////////////////////////////////////////////////////////////////////////
   void setTwiddlerWindow(TwiddlerWindow tw) {
      m_TwiddlerWindow = tw;
   }

   /////////////////////////////////////////////////////////////////////////////
   TwiddlerWindow getTwiddlerWindow() {
      return m_TwiddlerWindow;
   }

   /////////////////////////////////////////////////////////////////////////////
   void extendTitle(String extension) {
      if (extension == null || "".equals(extension)) {
         setTitle(getClass().getSimpleName());
      } else {
         setTitle(getClass().getSimpleName() + " - " + extension);
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   void clearTimes() {
      m_ChordTimes.clear();
      m_TwiddlerWindow.reAutoScale();
      if (m_TextPanel.isChords()) {
         setChords();
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   ChordTimes getChordTimes() {
      return m_ChordTimes;
   }

   /////////////////////////////////////////////////////////////////////////////
   @Override // Log.Quitter
   public void quit(int err) {
      //m_TwiddlerWindow.setVisible(false);
      m_TwiddlerWindow.dispose();
      setVisible(false);
      m_ChordTimes.persist("");
      getJMenuBar().setVisible(false); // persist
      Log.close();
      // call this explicitly since we are not DISPOSE_ON_CLOSE
      dispose();
      // close saves to persist, so write persist last
      Pref.writePersist();
      System.exit(err);
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override // WindowListener
   public void windowActivated(WindowEvent e) { requestFocusInWindow(); }
   @Override // WindowListener
   public void windowDeactivated(WindowEvent e) { requestFocusInWindow(); }
   @Override // WindowListener
   public void windowDeiconified(WindowEvent e) { requestFocusInWindow(); }
   @Override // WindowListener
   public void windowIconified(WindowEvent e) {}
   @Override // WindowListener
   public void windowOpened(WindowEvent e) { requestFocusInWindow(); }
   @Override // WindowListener
   public void windowClosed(WindowEvent e) { quit(0); }
   @Override // WindowListener
   public void windowClosing(WindowEvent e) { quit(0); }

   ////////////////////////////////////////////////////////////////////////////
   @Override // MouseListener
   public void mouseReleased(MouseEvent e) {}
   @Override // MouseListener
   public void mouseEntered(MouseEvent e) {}
   @Override // MouseListener
   public void mouseExited(MouseEvent e) {}
   @Override // MouseListener
   public void mouseClicked(MouseEvent e) {}

   ////////////////////////////////////////////////////////////////////////////
   @Override // MouseListener
   public void mousePressed(MouseEvent e) { 
      keyPressed(KeyPress.parseMouseEvent(e), e.getWhen());
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override // KeyListener
   public void keyTyped(KeyEvent e) {}
   @Override // KeyListener
   public void keyReleased(KeyEvent e) {}

   ////////////////////////////////////////////////////////////////////////////
   @Override // KeyListener
   public void keyPressed(KeyEvent e) {
      keyPressed(KeyPress.parseEvent(e), e.getWhen());
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override // ActionListener
   public void actionPerformed(ActionEvent e) {
      if (e.getActionCommand() != m_TimerKeyWait.getActionCommand()) {
         Log.err("Unknown action: " + e.getActionCommand());
         return;
      }
      timeUp();
   }

   /////////////////////////////////////////////////////////////////////////////
   void setKeyWaitMsec(int newValue) {
      m_KeyWaitMsec = newValue;
      m_TimerKeyWait = new Timer(m_KeyWaitMsec, this);
      m_TimerKeyWait.setActionCommand("key.collection.complete");
   }

   /////////////////////////////////////////////////////////////////////////////
   boolean isTimed() {
      return m_Timed;
   }

   /////////////////////////////////////////////////////////////////////////////
   void setTimed(boolean set) {
      m_Timed = set;
   }

   // Private /////////////////////////////////////////////////////////////////

   ////////////////////////////////////////////////////////////////////////////
   private boolean start() {
      Twiddle tw = m_TextPanel.getFirstTwiddle();
      m_TwiddlerWindow.show(tw, null, m_ChordTimes);
      // Start the timer in the past so the resulting time is not recorded.
      m_StartTimeMs = System.currentTimeMillis() - 2 * m_TwiddlerWindow.getProgressMax();
      m_TimeMs = 0;
      return tw != null;
   }

   /////////////////////////////////////////////////////////////////////////////
   private void setChordTimes(boolean keys) {
      if (m_ChordTimes != null) {
         m_ChordTimes.persist("");
      }
      m_ChordTimes = new ChordTimes(keys, isRightHand());
   }

   ////////////////////////////////////////////////////////////////////////////
   private void keyPressed(KeyPress kp, long when) {
//System.out.printf("pressed %s %s%n", kp.toString(KeyPress.Format.HEX), kp.toString());
      if (kp.isMouseButton()) {
         if (m_KeysPressed.size() != 0) {
            timeUp();
         }
         m_KeysPressed.add(KeyPress.fromKeyCodeAndModifiers(kp, m_ModifiersKeyPress));
         timeUp();
         return;
      }
      if (kp.isModifiers()) {
         m_ModifiersKeyPress = kp;
         return;
      }
      m_ModifiersKeyPress = null;
      m_TimerKeyWait.restart();
      if (m_KeyStartMs != 0) {
         Log.log(String.format("keyPressed event %dms now %dms", 
                  (int)(when - m_KeyStartMs),
                  (int)(System.currentTimeMillis() - m_KeyStartMs)));
      }
      m_KeyStartMs = System.currentTimeMillis();
      // store time of arrival of first key press
      if (m_TimeMs == 0) {
         m_TimeMs = (int)(when - m_StartTimeMs);
      }
      m_KeysPressed.add(kp);
   }

   ////////////////////////////////////////////////////////////////////////////
   private void timeUp() {
      m_KeyStartMs = 0;
      m_TimerKeyWait.stop();
      m_TextPanel.setPressed(m_KeysPressed.toString(KeyPress.Format.FILE));
      // convert keys to twiddle
      Assignment pressed = m_KeysPressed.findLongestPrefix(m_KeyMap);
      if (pressed == null) {
         // no matching twiddle
         Log.warn("Unmapped key(s): " + m_KeysPressed.toString(KeyPress.Format.FILE)
                + " (" + m_KeysPressed + ").");
         m_KeysPressed = new KeyPressList();
         return;
      }
//System.out.println("actionPerformed pressed " + pressed.toString());
      if (!pressed.getKeyPressList().equals(m_KeysPressed)) {
         // only partial match
         Log.log("Only matched \"" 
            + pressed.getKeyPressList() + "\" (" + pressed.getKeyPressList().toString(KeyPress.Format.HEX)
            + ") of \"" + m_KeysPressed + "\" (" + m_KeysPressed.toString(KeyPress.Format.HEX) + ')');
//         Log.warn("Twidlit probably has a different cfg from your Twiddler.");
      }
      m_KeysPressed = new KeyPressList();
      Twiddle tw = pressed.getBestTwiddle();
      Twiddle next = m_TextPanel.getNextTwiddle(pressed.getKeyPressList());
      if (next == null) {
         m_TwiddlerWindow.markMismatch(tw);
         // continue time
         m_TimeMs = 0;
      } else {
         // only accept chord if not timing or successfully timed
         m_TextPanel.next(
               !m_Timed
            || tw.getChord().isMouseButton()
            // only record times within 2* progress bar
            || (m_TimeMs < (int)(0.5 + m_ProgressFactor * m_TwiddlerWindow.getProgressMax())
             && m_ChordTimes.add(tw.getChord().toInt(),
                                 tw.getThumbKeys().toInt(),
                                 m_TimeMs))
         );
//System.out.printf("%s %s%n", tw.getChord(), m_ChordTimes.getTimes(tw.getChord().toInt(), 0));
         m_TwiddlerWindow.show(next, tw, m_ChordTimes);
         // start time
         m_StartTimeMs = System.currentTimeMillis();
         m_TimeMs = 0;
      }
   }
   
   // Data /////////////////////////////////////////////////////////////////////
   private static String m_HomeDir;
   private TwiddlerWindow m_TwiddlerWindow;
   private TextPanel m_TextPanel;
   private KeyMap m_KeyMap;
   private KeyPressList m_KeysPressed;
   private Timer m_TimerKeyWait;
   private int m_KeyWaitMsec;
   private long m_StartTimeMs;
   private int m_TimeMs;
   private long m_KeyStartMs;
   private boolean m_Timed;
   private ChordTimes m_ChordTimes;
   private double m_ProgressFactor;
   private KeyPress m_ModifiersKeyPress;

   // Main /////////////////////////////////////////////////////////////////////
   public static void main(String[] argv) {
      m_HomeDir = argv.length == 0 ? "." : argv[0];
      String text = Io.readLineFromCodeJar(4, "/data/about.html");
      Splash splash = new Splash(300, 150, text, 32, new Color(0xf5f5d5));
      Log.init(Log.ExitOnError);
      Persist.init("twidlit.properties", m_HomeDir, "pref");
      Pref.init("twidlit.preferences", Persist.get("#.pref.dir"), "pref");
      Pref.setIconPath("/data/icon.gif");
      if (Persist.getBool("#." + TwidlitMenu.sm_HELP_MENU_TEXT 
                          + ' ' + TwidlitMenu.sm_HELP_WRITE_LOG_TEXT)) {
         Log.setFile(Io.createFile(m_HomeDir, TwidlitMenu.sm_LOG_FILE_NAME));
      }
      KeyPress.init();
      Twidlit twidlit = new Twidlit(splash);
   }
}
