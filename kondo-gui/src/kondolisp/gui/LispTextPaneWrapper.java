/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package kondolisp.gui;

import java.awt.Color;
import java.awt.Event;
import java.awt.event.KeyEvent;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 *
 * @author haya
 */
public class LispTextPaneWrapper {

    private static InputMap normal_map = null;
    private static InputMap ctrl_x_map = null;

    private LispTextPaneWrapper(){

    }
    public static void setup(JTextPane text_pane) {
        prepareKeyBindings();
        LispTextPaneWrapper wrapper = new LispTextPaneWrapper();
        text_pane.setInputMap(JComponent.WHEN_FOCUSED, normal_map);
        wrapper.setupParenMarker(text_pane);
    }

    private void setupParenMarker(JTextPane text_pane) {
        LispDocumentListener listener = new LispDocumentListener(text_pane);
        text_pane.getDocument().addDocumentListener(listener);
        text_pane.addCaretListener(new LispCaretListener(text_pane));
    }

    private class LispCaretListener extends LispMarker implements CaretListener {
        private JTextPane text_pane;

        public LispCaretListener(JTextPane text_pane) {
            this.text_pane = text_pane;
        }

        public void caretUpdate(CaretEvent e) {
            this.invokeUpdateMarker(text_pane);
        }
    }

    private class LispDocumentListener extends LispMarker implements DocumentListener {
        private JTextPane text_pane;
        public LispDocumentListener(JTextPane text_pane) {
            this.text_pane = text_pane;
        }

        public void insertUpdate(DocumentEvent e) {
            this.invokeUpdateMarker(text_pane);
        }

        public void removeUpdate(DocumentEvent e) {
            this.invokeUpdateMarker(text_pane);
        }

        public void changedUpdate(DocumentEvent e) {
            return;
        }

    }

    private abstract class LispMarker {
        protected  void invokeUpdateMarker(final JTextPane text_pane) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    updateMarker(text_pane);
                }
            });
        }

        private void updateMarker(JTextPane text_pane) {
            String text = text_pane.getText();
            StyledDocument sdoc = text_pane.getStyledDocument();
            SimpleAttributeSet plane = new SimpleAttributeSet();
            sdoc.setCharacterAttributes(0, text.length(), plane, true); // clear

            int caret_pos = text_pane.getCaretPosition();
            if (caret_pos <= text.length() - 1
                    && text.charAt(caret_pos) == '(') {
                int paren_depth = 0;
                int open_pos = caret_pos;
                int close_pos = -1;
                for (int i = caret_pos + 1; i < text.length(); i++) {
                    switch (text.charAt(i)) {
                        case '(':
                            paren_depth++;
                            break;
                        case ')':
                            if (paren_depth == 0) {
                                close_pos = i;
                                markParen(text_pane, open_pos, close_pos);
                                return;
                            }
                            paren_depth--;
                            break;
                    }
                }
            } else if (caret_pos > 0 && text.charAt(caret_pos - 1) == ')') {
                int paren_depth = 0;
                int open_pos = -1;
                int close_pos = caret_pos - 1;
                for (int i = caret_pos - 2; i >= 0; i--) {
                    char c = text.charAt(i);
                    switch (c) {
                        case ')':
                            paren_depth++;
                            break;
                        case '(':
                            if (paren_depth == 0) {
                                open_pos = i;
                                markParen(text_pane, open_pos, close_pos);
                                return;
                            }
                            paren_depth--;
                            break;
                    }
                }

            }
        }

        private void markParen(JTextPane text_pane, int open_pos, int close_pos) {
            System.out.println(open_pos + "," + close_pos);
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setBackground(attr, Color.yellow);
            StyledDocument doc = (StyledDocument) text_pane.getDocument();
            doc.setCharacterAttributes(open_pos, 1, attr, true);
            doc.setCharacterAttributes(close_pos, 1, attr, false);
            attr = new SimpleAttributeSet();
            StyleConstants.setBold(attr, true);
            doc.setCharacterAttributes(open_pos, close_pos - open_pos + 1, attr, false);
        }
    }

    private synchronized static void prepareKeyBindings() {
        if (normal_map == null) {
            normal_map = (new JTextPane()).getInputMap();
            // Ctrl-b
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, Event.CTRL_MASK),
                    DefaultEditorKit.backwardAction);
            // Ctrl-f
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Event.CTRL_MASK),
                    DefaultEditorKit.forwardAction);
            // Ctrl-p
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, Event.CTRL_MASK),
                    DefaultEditorKit.upAction);
            // Ctrl-n
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, Event.CTRL_MASK),
                    DefaultEditorKit.downAction);
            // Ctrl-a
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, Event.CTRL_MASK),
                    DefaultEditorKit.beginLineAction);
            // Ctrl-e
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, Event.CTRL_MASK),
                    DefaultEditorKit.endLineAction);
            // Ctrl-h
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, Event.CTRL_MASK),
                    DefaultEditorKit.deletePrevCharAction);
            // Ctrl-d
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, Event.CTRL_MASK),
                    DefaultEditorKit.deleteNextCharAction);
            // Ctrl-w
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, Event.CTRL_MASK),
                    DefaultEditorKit.cutAction);
            // Alt-w
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, Event.ALT_MASK),
                    DefaultEditorKit.copyAction);
            // Ctrl-y
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Event.CTRL_MASK),
                    DefaultEditorKit.pasteAction);
            // Ctrl-f
            normal_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Event.CTRL_MASK),
                    DefaultEditorKit.forwardAction);
        }
        if (ctrl_x_map == null) {
            ctrl_x_map = (new JTextPane()).getInputMap();
            // C-x C-h
            ctrl_x_map.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, Event.CTRL_MASK),
                    DefaultEditorKit.selectAllAction);
        }

    }
}
