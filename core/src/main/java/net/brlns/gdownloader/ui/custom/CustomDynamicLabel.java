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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static net.brlns.gdownloader.ui.UIUtils.wrapTextInHtml;
import static net.brlns.gdownloader.ui.themes.UIColors.FOREGROUND;
import static net.brlns.gdownloader.util.StringUtils.fastTruncate;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CustomDynamicLabel extends JLabel {

    private static final int THROTTLE_MS = 80;

    private final Timer debounceTimer;
    private final AtomicLong lastUpdateTime = new AtomicLong();

    @Setter
    private boolean lineWrapping = false;

    @Setter
    private boolean centerText = false;

    private String[] fullText;

    private int lastComputedWidth = -1;

    @SuppressWarnings("this-escape")
    public CustomDynamicLabel(String initialText) {
        this();

        setFullText(initialText);
    }

    @SuppressWarnings("this-escape")
    public CustomDynamicLabel() {
        super();

        debounceTimer = new Timer(THROTTLE_MS, e -> {
            lastUpdateTime.set(System.currentTimeMillis());

            doUpdateTruncatedText();
        });
        debounceTimer.setRepeats(false);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                doUpdateTruncatedText();
            }
        });
    }

    public final void setFullText(String... text) {
        String[] newFullText = Arrays.stream(text)
            .flatMap(s -> Arrays.stream(s.split("\n")))
            .toArray(String[]::new);

        if (Arrays.equals(fullText, newFullText)) {
            return;
        }

        fullText = newFullText;
        lastComputedWidth = -1;

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

        long now = System.currentTimeMillis();

        if (now - lastUpdateTime.get() >= THROTTLE_MS || getText() == null || getText().isEmpty()) {
            lastUpdateTime.set(now);

            doUpdateTruncatedText();
        } else if (!debounceTimer.isRunning()) {
            debounceTimer.start();
        }
    }

    private void doUpdateTruncatedText() {
        assert SwingUtilities.isEventDispatchThread();

        if (fullText == null || fullText.length == 0) {
            setText("");
            return;
        }

        int availableWidth = getWidth() - getInsets().left - getInsets().right - 10;

        if (availableWidth <= 10) {// Likely size 0 at the time it was called, ignore
            Container parent = getParent();
            if (parent != null && parent.getWidth() > 10) {
                availableWidth = parent.getWidth() - getInsets().left - getInsets().right - 10;
            } else {
                availableWidth = 10;
            }
        }

        if (lastComputedWidth >= 0 && Math.abs(availableWidth - lastComputedWidth) < 5) {
            return;
        }

        lastComputedWidth = availableWidth;

        String[] truncatedText = getTruncated(fullText, availableWidth);
        setText(wrapTextInHtml(FOREGROUND, true, centerText, truncatedText));
    }

    private String[] getTruncated(String[] fullText, int availableWidth) {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        if (fontMetrics == null) {
            return fullText;
        }

        String[] truncatedText = new String[fullText.length];

        for (int i = 0; i < fullText.length; i++) {
            String line = fullText[i];
            if (line == null || line.isEmpty() || fontMetrics.stringWidth(line) <= availableWidth) {
                truncatedText[i] = line;
                continue;
            }

            if (!lineWrapping) {
                truncatedText[i] = fastTruncate(line, fontMetrics, availableWidth);
            } else {
                StringBuilder wrappedLine = new StringBuilder();
                StringBuilder currentLine = new StringBuilder();

                for (char c : line.toCharArray()) {
                    currentLine.append(c);
                    if (fontMetrics.stringWidth(currentLine.toString()) > availableWidth) {
                        wrappedLine.append(currentLine.substring(0, currentLine.length() - 1))
                            .append(System.lineSeparator());
                        currentLine = new StringBuilder().append(c);
                    }
                }

                wrappedLine.append(currentLine);
                truncatedText[i] = wrappedLine.toString();
            }
        }

        return truncatedText;
    }
}
