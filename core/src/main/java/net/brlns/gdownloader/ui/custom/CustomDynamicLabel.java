/*
 * Copyright (C) 2024 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.ui.custom;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ui.GUIManager;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CustomDynamicLabel extends JLabel {

    @Getter
    private final ComponentAdapter listener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            updateTruncatedText();
        }
    };

    private String[] fullText;

    @SuppressWarnings("this-escape")
    public CustomDynamicLabel(String initialText) {
        this();

        setFullText(initialText);
    }

    @SuppressWarnings("this-escape")
    public CustomDynamicLabel() {
        super();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateTruncatedText();
            }
        });
    }

    public final void setFullText(String... text) {
        this.fullText = text;

        setText(GUIManager.wrapTextInHtml(Integer.MAX_VALUE, fullText));
        updateTruncatedText();
    }

    @Override
    public Dimension getMaximumSize() {
        Container parent = getParent();
        if (parent != null) {
            int availableWidth = parent.getWidth();
            return new Dimension(availableWidth, super.getMaximumSize().height);
        }

        return super.getMaximumSize();
    }

    public void updateTruncatedText() {
        assert SwingUtilities.isEventDispatchThread();

        if (fullText == null || fullText.length == 0) {
            setText("");
            return;
        }

        int availableWidth = getWidth() - getInsets().left - getInsets().right - 10;
        FontMetrics fontMetrics = getFontMetrics(getFont());
        String[] truncatedText = new String[fullText.length];

        for (int i = 0; i < fullText.length; i++) {
            String line = fullText[i];
            if (fontMetrics.stringWidth(line) <= availableWidth) {
                truncatedText[i] = line;
                continue;
            }

            String ellipsis = "...";
            int ellipsisWidth = fontMetrics.stringWidth(ellipsis);
            StringBuilder truncatedLine = new StringBuilder(line);

            while (fontMetrics.stringWidth(truncatedLine.toString()) + ellipsisWidth > availableWidth && truncatedLine.length() > 0) {
                truncatedLine.deleteCharAt(truncatedLine.length() - 1);
            }

            truncatedText[i] = truncatedLine + ellipsis;
        }

        setText(GUIManager.wrapTextInHtml(Integer.MAX_VALUE, truncatedText));
    }
}
