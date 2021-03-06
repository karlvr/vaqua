/*
 * Changes Copyright (c) 2015-2018 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.violetlib.aqua;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.jnr.aqua.PopupButtonLayoutConfiguration;
import org.violetlib.jnr.aqua.AquaUIPainter.Size;

@SuppressWarnings("serial") // Superclass is not serializable across versions
public class AquaComboBoxRendererInternal<E> extends JLabel implements ListCellRenderer<E> {
    protected final JComboBox<?> fComboBox;
    protected boolean fSelected;
    protected boolean fChecked;
    protected boolean fInList;
    protected boolean fEditable;
    protected boolean fDrawCheckedItem = true;
    protected boolean fIsDark;

    protected static int checkMarkLeftInset = 5;
    protected static int checkMarkTopInset = 3;

    protected static int menuLabelLeftInset = 21;
    protected static int editableMenuLabelLeftInset = 5;
    protected static int menuLabelRightInset = 5;
    protected static int menuLabelTopInset = 0;
    protected static int menuLabelBottomInset = 1;
    protected static int miniMenuLabelTopInset = 1;
    protected static int miniMenuLabelBottomInset = 0;

    protected static int buttonLabelLeftInset = 0;    // already determined using content area and padding
    protected static int buttonLabelTopInset = 0;     // already determined using content area
    protected static int buttonLabelBottomInset = 0;  // already determined using content area
    protected static int buttonLabelRightInset = 0;   // already determined using content area and padding

    // Provides space for a checkbox, and is translucent
    public AquaComboBoxRendererInternal(JComboBox<?> comboBox) {
        super();
        fComboBox = comboBox;
    }

    // Don't include checkIcon space, because this is also used for button size calculations
    // - the popup-size calc will get checkIcon space from getInsets
    public Dimension getPreferredSize() {
        // From BasicComboBoxRenderer - trick to avoid zero-height items

        Dimension size;
        String text = getText();
        if (text == null || text.isEmpty()) {
            setText(" ");
            size = super.getPreferredSize();
            setText("");
        } else {
            size = super.getPreferredSize();
        }
        return size;
    }

    // Don't paint the border here, it gets painted by the UI
    protected void paintBorder(Graphics g) {

    }

    public int getBaseline(int width, int height) {
        return super.getBaseline(width, height) - 1;
    }

    // Really means is the one with the mouse over it
    public Component getListCellRendererComponent(JList<? extends E> list,
                                                  E value, int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        fInList = (index >= 0); // When the button wants the item painted, it passes in -1
        fSelected = isSelected;
        if (index < 0) {
            index = fComboBox.getSelectedIndex();
        }

        // changed this to not ask for selected index but directly compare the current item and selected item
        // different from basic because basic has no concept of checked, just has the last one selected,
        // and the user changes selection. We have selection and a check mark.
        // we used to call fComboBox.getSelectedIndex which ends up being a very bad call for large checkboxes
        // it does a linear compare of every object in the checkbox until it finds the selected one, so if
        // we have a 5000 element list we will 5000 * (selected index) .equals() of objects.
        // See Radar #3141307

        // Fix for Radar # 3204287 where we ask for an item at a negative index!
        if (index >= 0) {
            Object item = fComboBox.getItemAt(index);
            // Ideally, there would be a way to set check marks on individual model elements of a pull down menu
            // independently of the selected element, which does not have a special display.
            fChecked = fInList && item != null && item.equals(fComboBox.getSelectedItem()) && !isPullDown(fComboBox);
        } else {
            fChecked = false;
        }

        fEditable = fComboBox.isEditable();

        //if (fInList) {
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setFont(list.getFont());
//        } else {
//            setBackground(fComboBox.getBackground());
//            setForeground(fComboBox.getForeground());
//            setFont(fComboBox.getFont());
//        }

        if (value instanceof Icon) {
            setIcon((Icon)value);
            setText(null);
        } else {
            setIcon(null);
            setText((value == null) ? " " : value.toString());
        }

        AquaAppearance appearance = AppearanceManager.getAppearance(fComboBox);
        fIsDark = appearance.isDark();

        return this;
    }

    public @NotNull Insets getInsets(@Nullable Insets insets) {
        if (insets == null) {
            insets = new Insets(0, 0, 0, 0);
        }

        Size size = getComboBoxSizeVariant();

        if (fInList) {
            insets.top = size == Size.MINI ? miniMenuLabelTopInset : menuLabelTopInset;
            insets.bottom = size == Size.MINI ? miniMenuLabelBottomInset : menuLabelBottomInset;
            insets.left = fEditable ? editableMenuLabelLeftInset : menuLabelLeftInset;
            insets.right = menuLabelRightInset;
        } else {
            insets.top = buttonLabelTopInset;
            insets.bottom = buttonLabelBottomInset;
            insets.right = buttonLabelRightInset;
            insets.left = buttonLabelLeftInset;
        }

        return insets;
    }

    protected void setDrawCheckedItem(boolean drawCheckedItem) {
        this.fDrawCheckedItem = drawCheckedItem;
    }

    // Paint this component, and a check mark if it's the selected item and not in the button
    protected void paintComponent(Graphics g) {
        if (fInList) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());

            if (fChecked && !fEditable && fDrawCheckedItem) {
                Size size = getComboBoxSizeVariant();
                Icon checkMark = AquaImageFactory.getPopupMenuItemCheckIcon(size);
                Color color = getForeground();
                Image im = AquaImageFactory.getProcessedImage(checkMark, color);
                int height = getHeight();
                int left = checkMarkLeftInset;
                int top = Math.max(checkMarkTopInset, (height - checkMark.getIconHeight() - 1) / 2);
                g.drawImage(im, left, top, null);
            }
        }
        super.paintComponent(g);
    }

    protected Size getComboBoxSizeVariant() {
        AquaComboBoxUI ui = AquaUtils.getUI(fComboBox, AquaComboBoxUI.class);
        if (ui != null) {
            return ui.getSizeVariant();
        }
        return Size.REGULAR;
    }

    protected boolean isPullDown(JComboBox comboBox) {
        if (!comboBox.isEditable()) {
            AquaComboBoxUI ui = AquaUtils.getUI(comboBox, AquaComboBoxUI.class);
            if (ui != null) {
                PopupButtonLayoutConfiguration g = (PopupButtonLayoutConfiguration) ui.getLayoutConfiguration();
                return g != null && !g.isPopUp();
            }
        }
        return false;
    }

     @Override
     public void validate() {}

     @Override
     public void invalidate() {}

     @Override
     public void repaint() {}

     @Override
     public void revalidate() {}

     @Override
     public void repaint(long tm, int x, int y, int width, int height) {}

     @Override
     public void repaint(Rectangle r) {}

     @Override
     public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {}

     @Override
     public void firePropertyChange(String propertyName, char oldValue, char newValue) {}

     @Override
     public void firePropertyChange(String propertyName, short oldValue, short newValue) {}

     @Override
     public void firePropertyChange(String propertyName, int oldValue, int newValue) {}

     @Override
     public void firePropertyChange(String propertyName, long oldValue, long newValue) {}

     @Override
     public void firePropertyChange(String propertyName, float oldValue, float newValue) {}

     @Override
     public void firePropertyChange(String propertyName, double oldValue, double newValue) {}

     @Override
     public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
