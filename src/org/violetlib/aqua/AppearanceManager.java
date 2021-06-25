/*
 * Copyright (c) 2018-2021 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.aqua;

import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.WeakHashMap;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.violetlib.aqua.AquaFocusHandler.FRAME_ACTIVE_PROPERTY;

/**
 * This class supports the association of appearances with components.
 */

public class AppearanceManager {

    public static boolean isDebug = false;

    public static final String AQUA_APPEARANCE_NAME_KEY = "Aqua.appearanceName";    // the name of an explicitly chosen appearance
    public static final String AQUA_APPEARANCE_KEY = "Aqua.appearance";             // the effective appearance

    private static final AppearanceManagerHierarchyListener hierarchyListener = new AppearanceManagerHierarchyListener();
    private static final ActiveStateListener activeStateListener = new ActiveStateListener();
    private static final AppearanceNamePropertyListener appearanceNamePropertyListener = new AppearanceNamePropertyListener();
    private static final SpecifiedAppearanceContainerListener specifiedAppearanceContainerListener = new SpecifiedAppearanceContainerListener();

    private static final WeakComponentSet componentsUsingSpecifiedAppearances = new WeakComponentSet();

    private static @Nullable AquaAppearance currentAppearance;

    /**
     * Return the current appearance. This method is for the very unusual cases where a component is painted that is
     * not in the component hierarchy.
     * @return the current appearance, or a default appearance if no current appearance has been registered.
     */

    public static @NotNull AquaAppearance getCurrentAppearance() {
        if (currentAppearance != null) {
            return currentAppearance;
        } else {
            return AquaAppearances.getDefaultAppearance();
        }
    }

    /**
     * Ensure that a component has the correct registered appearance for a painting operation in progress and
     * make that appearance available using the {@link #getCurrentAppearance getCurrentAppearance} method.
     * @param c The component.
     * @return the appearance for the component.
     */

    public static @NotNull AquaAppearance registerCurrentAppearance(@NotNull JComponent c) {
        AquaAppearance appearance = ensureAppearance(c);
        if (appearance != currentAppearance) {
            currentAppearance = appearance;
            if (isDebug) {
                debug(c, "Current appearance changed to: " + currentAppearance);
            }
        }
        return currentAppearance;
    }

    /**
     * Install event listeners to help manage the appearance properties of the specified component.
     * @param c The component.
     * @throws IllegalArgumentException if {@code c} does not support {@link AquaComponentUI}.
     */

    public static void installListeners(@NotNull JComponent c) {
        AquaComponentUI ui = AquaUtils.getUI(c, AquaComponentUI.class);
        if (ui != null || c instanceof JLayeredPane) {
            c.addHierarchyListener(hierarchyListener);
            c.addPropertyChangeListener(FRAME_ACTIVE_PROPERTY, activeStateListener);
            c.addPropertyChangeListener(AQUA_APPEARANCE_NAME_KEY, appearanceNamePropertyListener);
        } else {
            throw new IllegalArgumentException("Component must support AquaComponentUI");
        }
    }

    /**
     * Uninstall the event listeners installed by {@link #installListeners}.
     * @param c The component.
     */

    public static void uninstallListeners(@NotNull Component c) {
        c.removeHierarchyListener(hierarchyListener);
        c.removePropertyChangeListener(FRAME_ACTIVE_PROPERTY, activeStateListener);
        c.addPropertyChangeListener(AQUA_APPEARANCE_NAME_KEY, appearanceNamePropertyListener);
        if (c instanceof Container) {
            Container cc = (Container) c;
            cc.removeContainerListener(specifiedAppearanceContainerListener);
        }
    }

    public static void setRootPaneRegisteredAppearance(@NotNull JRootPane rp, @NotNull AquaAppearance appearance) {
        updateAppearancesInSubtree(rp, appearance);
    }

    private static void setRegisteredAppearance(@NotNull Component c, @NotNull AquaAppearance appearance) {
        updateAppearancesInSubtree(c, appearance);
    }

    /**
     * Set the component registered appearance to the specified appearance.
     * @param jc The component.
     * @param appearance The appearance.
     * @return true if the registered appearance was updated (or it should have been), false if the existing registered
     * appearance matches {@code appearance}.
     */

    private static boolean setRegisteredAppearance(@NotNull JComponent jc, @NotNull AquaAppearance appearance) {
        AquaAppearance existingAppearance = getRegisteredAppearance(jc);
        if (appearance != existingAppearance) {
            try {
                jc.putClientProperty(AQUA_APPEARANCE_KEY, appearance);
                if (isDebug) {
                    debug(jc, "Registering appearance " + appearance.getName() + " for " + AquaUtils.show(jc));
                }
                appearanceHasChanged(jc, appearance);
            } catch (Throwable th) {
                AquaUtils.logError("Unable to set appearance property on " + AquaUtils.show(jc)
                        + ". Check for failure in a property change listener", th);
                th.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public static void removeRegisteredAppearance(@NotNull JComponent jc) {
        try {
            Object o = jc.getClientProperty(AQUA_APPEARANCE_KEY);
            if (o != null) {
                jc.putClientProperty(AQUA_APPEARANCE_KEY, null);
                if (isDebug) {
                    String name;
                    if (o instanceof AquaAppearance) {
                        name = ((AquaAppearance) o).getName();
                    } else {
                        name = "?";
                    }
                    debug(jc, "Removing appearance " + name + " for " + AquaUtils.show(jc));
                }
            }
        } catch (Throwable th) {
            AquaUtils.logError("Unable to uninstall appearance property on " + AquaUtils.show(jc)
                    + ". Check for failure in a property change listener", th);
            th.printStackTrace();
        }
    }

    /**
     * Return the current application appearance.
     */

    public static @NotNull AquaAppearance getApplicationAppearance() {
        String name = AquaUtils.nativeGetApplicationAppearanceName();
        if (name != null) {
            return AquaAppearances.get(name);
        }
        return AquaAppearances.getDefaultAppearance();
    }

    /**
     * Set the registered appearance for the specified component and update its subcomponents accordingly.
     * This method short-circuits on any subcomponent that already has the appropriate registered appearance.
     */

    public static void updateAppearancesInTree(@NotNull Component c, @NotNull AquaAppearance appearance) {
        setRegisteredAppearance(c, appearance);

        if (c instanceof Container) {
            Container cc = (Container) c;
            int count = cc.getComponentCount();
            for (int i = 0; i < count; i++) {
                Component child = cc.getComponent(i);
                updateAppearancesInSubtree(child, appearance);
            }
        }
    }

    /**
     * Set the registered appearance for the specified component and update its subcomponents accordingly.
     * This method short-circuits on any component that already has the appropriate registered appearance.
     */

    private static void updateAppearancesInSubtree(@NotNull Component c, @NotNull AquaAppearance appearance)
    {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            if (matchesRegisteredAppearance(jc, appearance)) {
                if (isDebug) {
                    debug(c, "Short circuit: component " + AquaUtils.show(c) + " has the correct appearance");
                }
                return;
            }
            if (hasValidRegisteredSpecifiedAppearance(jc)) {
                if (isDebug) {
                    debug(c, "Short circuit: component " + AquaUtils.show(c) + " has a valid specified appearance");
                }
                return;
            }
            setRegisteredAppearance(jc, appearance);
        }

        if (c instanceof Container) {
            Container cc = (Container) c;
            int count = cc.getComponentCount();
            for (int i = 0; i < count; i++) {
                Component child = cc.getComponent(i);
                updateAppearancesInSubtree(child, appearance);
            }
        }
    }

    private static boolean matchesRegisteredAppearance(@NotNull JComponent jc, @NotNull AquaAppearance appearance) {
        AquaAppearance registeredAppearance = getRegisteredAppearance(jc);
        if (registeredAppearance == null) {
            return false;
        }
        if (registeredAppearance == appearance) {
            return true;
        }
        if (useVibrantAppearance(jc)) {
            AquaAppearance vibrantVersion = AquaAppearances.getVibrantAppearance(appearance);
            if (vibrantVersion == registeredAppearance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update the registered appearances for the specified component and its subcomponents in response to a change in
     * the registered appearance of its parent. Components with a valid specified appearance are not updated.
     * @param c The component.
     */

    public static void updateAppearancesInTree(@NotNull Component c) {
        if (!updateAppearance(c)) {
            return;
        }
        if (c instanceof Container) {
            Container parent = (Container) c;
            int count = parent.getComponentCount();
            for (int i = 0; i < count; i++) {
                Component child = parent.getComponent(i);
                updateAppearancesInTree(child);
            }
        }
    }

    /**
     * Update the registered appearance of the specified component to the appropriate appearance based on the existence
     * of a specified appearance, an appearance obtained from an ancestor, or the application appearance. The currently
     * registered appearance, if any, is ignored.
     *
     * @return true if the component was updated with a new registered appearance or the component does not support a
     * registered appearance, false otherwise.
     */

    private static boolean updateAppearance(@NotNull Component c) {

        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            // If the component has been configured to use a particular appearance regardless of context, use that.
            AquaAppearance a = getSpecifiedAppearanceVariant(jc);
            if (a != null) {
                return setRegisteredAppearance(jc, a);
            }

            // If the nearest JComponent ancestor has a known appearance, use that.
            JComponent ancestor = getJComponentAncestor(jc);
            if (ancestor != null) {
                a = getKnownAppearance(ancestor, false);
                if (a != null) {
                    return setRegisteredAppearance(jc, a);
                }
            }

            // Otherwise, use the application appearance.
            return setRegisteredAppearance(jc, getApplicationAppearance());
        }
        return true;
    }

    /**
     * Ensure that a component has the proper appearance properties for painting. The selected appearance is based on
     * the existence of a specified appearance, an appearance obtained from an ancestor, or the application appearance.
     * If appropriate, the vibrant variant of the specified, inherited, or application appearance will be used. The
     * currently registered appearance, if any, is ignored.
     *
     * A component UI should call this method before performing a painting operation to ensure that the correct
     * appearance is used even if the component hierarchy has been modified since the UI last configured itself.
     * Updating the configuration may be essential if the component is being used as a cell renderer.
     * <p>
     * The component is updated, if necessary, to register the appearance for future use. Ancestor components may also
     * be updated.
     * @param c The component.
     * @return the appearance to use for the component.
     */

    public static @NotNull AquaAppearance ensureAppearance(@NotNull Component c) {

        // Unlike getAppearance(), this method computes the appearance without using the registered appearance and
        // registers the computed appearance even if it is a default appearance.

        AquaAppearance a = getKnownAppearance(c, true);
        if (a == null) {
            a = getApplicationAppearance();
            setRegisteredAppearance(c, a);
        }
        return a;
    }

    /**
     * Return the appearance that should be used by a component for a painting operation in progress. If the component
     * has a registered appearance, that appearance is returned. Otherwise an appearance is determined based on the
     * existence of a specified appearance, an appearance obtained from an ancestor, or the application appearance. If
     * appropriate, the vibrant variant of the specified, inherited, or application appearance will be used. The
     * component is updated to register the appearance for future use, if it is not a default appearance. Ancestors may
     * also be updated.
     * @param c The component.
     * @return the appearance to use.
     */

    public static @NotNull AquaAppearance getAppearance(@NotNull Component c) {
        AquaAppearance a = getKnownAppearance(c, false);
        if (a != null) {
            return a;
        }
        a = getApplicationAppearance();
        if (isDebug) {
            debug(c, "Using application appearance " + a.getName() + " for " + AquaUtils.show(c));
        }
        return a;
    }

    /**
     * Return the non-default appearance that should be used by a component for a painting operation in progress. If an
     * appearance is found, the component and its descendants are updated (if possible and as needed) to register the
     * appearance for future use. Ancestors and their descendants may also be updated.
     * @param c The component.
     * @param isReset If true, any existing registered appearance will be ignored.
     * @return the appearance to use, or null if no known appearance is available.
     */

    private static @Nullable AquaAppearance getKnownAppearance(@NotNull Component c, boolean isReset) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            if (!isReset) {
                // If an appearance has been registered for this component, use that.
                AquaAppearance a = getRegisteredAppearance(jc);
                if (a != null) {
                    return a;
                }
            }
            // If the component has been configured to use a particular appearance regardless of context, use that.
            // The appearance is registered for future use.
            AquaAppearance a = getSpecifiedAppearanceVariant(jc);
            if (a != null) {
                updateAppearancesInSubtree(jc, a);
                return a;
            }
        }
        // If the nearest JComponent ancestor has a known appearance, use that.
        // The appearance is registered for future use, if possible.
        JComponent ancestor = getJComponentAncestor(c);
        if (ancestor != null) {
            AquaAppearance a = getKnownAppearance(ancestor, false);
            if (a != null) {
                updateAppearancesInSubtree(c, a);
                return a;
            }
        } else if (c instanceof JRootPane) {
            // The root pane has not been configured to use a specific appearance, so use the application appearance.
            AquaAppearance a = getApplicationAppearance();
            updateAppearancesInSubtree(c, a);
            return a;
        }
        // There is no known appearance for the component.
        return null;
    }

    /**
     * Return the nearest ancestor that is a Swing component.
     */

    private static @Nullable JComponent getJComponentAncestor(@NotNull Component c) {
        Component current = c;
        while (true) {
            Container parent = current.getParent();
            if (parent == null) {
                return null;
            }
            if (parent instanceof JComponent) {
                return (JComponent) parent;
            }
            current = parent;
        }
    }

    /**
     * Return the appearance that has been registered for use by a component.
     * @param c The component.
     * @return the registered appearance, or null.
     */

    public static @Nullable AquaAppearance getRegisteredAppearance(@NotNull Component c) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            return getRegisteredAppearance(jc);
        }
        return null;
    }

    /**
     * Return the appearance that has been registered for use by a component.
     * @param jc The component.
     * @return the registered appearance, or null.
     */

    public static @Nullable AquaAppearance getRegisteredAppearance(@NotNull JComponent jc) {
        Object o = jc.getClientProperty(AQUA_APPEARANCE_KEY);
        if (o instanceof AquaAppearance) {
            return (AquaAppearance) o;
        }
        return null;
    }

    /**
     * Return the appearance that has been specified for use by a component, if valid. The vibrant version of the
     * appearance will be returned, if appropriate. The component is not updated.
     * @param jc The component.
     * @return the specified appearance, or null if none.
     */

    private static @Nullable AquaAppearance getSpecifiedAppearanceVariant(@NotNull JComponent jc) {
        String name = getSpecifiedAppearanceName(jc);
        if (name != null) {
            AquaAppearance appearance = AquaAppearances.get(name);
            return getVariant(jc, appearance);
        }
        return null;
    }

    /**
     * Return the appearance name that has been specified for use by a component.
     * @param jc The component.
     * @return the specified appearance name, or null if none.
     */

    private static @Nullable String getSpecifiedAppearanceName(@NotNull JComponent jc) {
        Object o = jc.getClientProperty(AQUA_APPEARANCE_NAME_KEY);
        if (o instanceof String) {
            return (String) o;
        }
        return null;
    }

    /**
     * Check for a valid specified appearance for a component. A specified appearance is valid if the specified
     * appearance name identifies a supported appearance and the registered appearance matches that appearance,
     * with vibrant variants taken into account.
     * @return true if the component specifies an appearance and it matches the registered appearance.
     */

    private static boolean hasValidRegisteredSpecifiedAppearance(@NotNull JComponent jc) {
        AquaAppearance appearance = getSpecifiedAppearanceVariant(jc);
        if (appearance != null) {
            AquaAppearance registeredAppearance = getRegisteredAppearance(jc);
            return registeredAppearance == appearance;
        }
        return false;
    }

    /**
     * Check for an unexpected mismatch between the specified appearance and the registered appearance.
     * @param jc A component with a valid specified appearance.
     * @param specifiedAppearance The specified appearance.
     * @return true if the registered appearance matches the specified appearance or there is no registered
     * appearance, false otherwise.
     */

    private static boolean validateRegistrationForSpecifiedAppearance(@NotNull JComponent jc,
                                                                      @NotNull AquaAppearance specifiedAppearance) {
        AquaAppearance registeredAppearance = getRegisteredAppearance(jc);
        if (registeredAppearance == null) {
            return true;
        }
        if (registeredAppearance != specifiedAppearance) {
            AquaUtils.syslog("Registered appearance " + registeredAppearance.getName()
                    + " does not match specified appearance " + specifiedAppearance.getName()
                    + " for " + AquaUtils.show(jc));
            return false;
        }
        return true;
    }

    private static void appearanceHasChanged(@NotNull JComponent c, @NotNull AquaAppearance appearance) {
        if (c instanceof JMenuBar) {
            JMenuBar mb = (JMenuBar) c;
            // Special hack because we are required to use the platform AquaMenuBarUI to be able to use the screen menu
            // bar
            Color background = mb.getBackground();
            if (background instanceof ColorUIResource) {
                mb.setBackground(appearance.getColor("controlBackground"));
            }
            Color foreground = mb.getForeground();
            if (foreground instanceof ColorUIResource) {
                mb.setForeground(appearance.getColor("control"));
            }
        }

        AquaComponentUI ui = AquaUtils.getUI(c, AquaComponentUI.class);
        if (ui != null) {
            ui.appearanceChanged(c, appearance);
        }
    }

    private static class ActiveStateListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent e) {
            Object source = e.getSource();
            if (source instanceof JComponent) {
                JComponent jc = (JComponent) source;
                AquaComponentUI ui = AquaUtils.getUI(jc, AquaComponentUI.class);
                if (ui != null) {
                    Object newValue = e.getNewValue();
                    if (newValue instanceof Boolean) {
                        Boolean b = (Boolean) newValue;
                        ui.activeStateChanged(jc, b);
                    }
                }
            }
        }
    }

    private static class AppearanceNamePropertyListener
            implements PropertyChangeListener {
        @Override
        public void propertyChange(@NotNull PropertyChangeEvent e) {
            Object source = e.getSource();
            if (source instanceof JComponent) {
                JComponent jc = (JComponent) source;
                Object newValue = e.getNewValue();
                if (newValue == null || newValue instanceof String) {
                    String appearanceName = (String) newValue;
                    specifiedAppearanceNameChanged(jc, appearanceName);
                }
            }
        }
    }

    private static class SpecifiedAppearanceContainerListener
            implements ContainerListener {
        @Override
        public void componentAdded(@NotNull ContainerEvent e) {
            Object source = e.getSource();
            if (source instanceof JComponent) {
                JComponent jc = (JComponent) source;
                componentAddedToContainerWithSpecifiedAppearance(jc, e.getChild());
            }
        }

        @Override
        public void componentRemoved(@NotNull ContainerEvent e) {
        }
    }

    /**
     * This method is called when the specified appearance name client property of a managed component changes.
     */

    private static void specifiedAppearanceNameChanged(@NotNull JComponent c, @Nullable String appearanceName) {
        if (appearanceName != null) {
            AquaAppearance appearance = getNamedAppearanceVariant(c, appearanceName);
            if (appearance != null) {
                if (isDebug) {
                    debug(c, "Appearance " + appearanceName + " specified for " + AquaUtils.show(c));
                }
                componentsUsingSpecifiedAppearances.add(c);
                c.addContainerListener(specifiedAppearanceContainerListener);
                updateAppearancesInTree(c, appearance);
            } else {
                if (isDebug) {
                    AquaUtils.syslog("Specified appearance " + appearanceName + " for " + AquaUtils.show(c)
                            + " is not available");
                }
            }
        } else {
            if (isDebug) {
                debug(c, "Specified appearance for " + AquaUtils.show(c) + " removed");
            }
            componentsUsingSpecifiedAppearances.remove(c);
            c.removePropertyChangeListener(appearanceNamePropertyListener);
            c.removeContainerListener(specifiedAppearanceContainerListener);
            AquaAppearance specifiedAppearance = getSpecifiedAppearanceVariant(c);
            if (specifiedAppearance != null) {
                validateRegistrationForSpecifiedAppearance(c, specifiedAppearance);
                updateAppearancesInTree(c);
            }
        }
    }

    private static void componentAddedToContainerWithSpecifiedAppearance(@NotNull JComponent container,
                                                                         @NotNull Component child) {
        AquaAppearance specifiedAppearance = getSpecifiedAppearanceVariant(container);
        if (specifiedAppearance != null) {
            if (validateRegistrationForSpecifiedAppearance(container, specifiedAppearance)) {
                if (isDebug) {
                    debug(container, "Updating new child " + AquaUtils.show(child)
                            + " to container specified appearance " + specifiedAppearance.getName());
                }
                updateAppearancesInTree(child);
                return;
            }
        } else {
            debug(container, "Received unexpected notification of new child " + AquaUtils.show(child)
                    + " in container without a specified appearance " + AquaUtils.show(container));
        }
    }

    private static class AppearanceManagerHierarchyListener implements HierarchyListener {
        @Override
        public void hierarchyChanged(@NotNull HierarchyEvent e) {
            long flags = e.getChangeFlags();
            if ((flags & HierarchyEvent.PARENT_CHANGED) != 0) {
                Component c = e.getChanged();
                if (c == e.getSource() && c instanceof JComponent) {
                    JComponent jc = (JComponent) c;
                    parentChanged(jc);
                }
            }
        }
    }

    private static void parentChanged(@NotNull JComponent c) {
        Container parent = c.getParent();
        if (parent != null && !componentsUsingSpecifiedAppearances.contains(parent)) {
            // Attempt to determine the appearance of the component.
            AquaAppearance a = getKnownAppearance(c, true);
            if (a != null) {
                if (isDebug) {
                    debug(c, "Updating appearance of " + AquaUtils.show(c) + " to " + a.getName() + " after parent change");
                }
                updateAppearancesInTree(c, a);
            }
        }
    }

    /**
     * Return the appearance with the specified name that is appropriate for the specified component.
     * The returned appearance may be a vibrant appearance.
     * @param jc The component.
     * @param name The base (non-vibrant) appearance name.
     */

    private static @Nullable AquaAppearance getNamedAppearanceVariant(@NotNull JComponent jc, @NotNull String name) {
        AquaAppearance appearance = AquaAppearances.getOptional(name);
        return appearance != null ? getVariant(jc, appearance) : null;
    }

    private static @NotNull AquaAppearance getVariant(@NotNull JComponent jc, @NotNull AquaAppearance appearance) {
        if (useVibrantAppearance(jc)) {
            appearance = AquaAppearances.getVibrantAppearance(appearance);
        }
        return appearance;
    }

    private static boolean useVibrantAppearance(@NotNull JComponent jc) {
        return AquaVibrantSupport.isVibrant(jc) && !(jc instanceof JRootPane);
    }

    private static class WeakComponentSet {
        private final WeakHashMap<JComponent,JComponent> map = new WeakHashMap<>();

        public void add(@NotNull JComponent c) {
            map.put(c, c);
        }

        public void remove(@NotNull JComponent c) {
            map.remove(c);
        }

        public boolean contains(@NotNull Component c) {
            return c instanceof JComponent && map.containsKey(c);
        }

        public @NotNull java.util.List<JComponent> components() {
            return new ArrayList<>(map.keySet());
        }
    }

    private static void debug(@NotNull Component c, @NotNull String s)
    {
        Window w = SwingUtilities.getWindowAncestor(c);
        String name = w != null ? w.getName() : "";
        if (name != null && !name.isEmpty()) {
            AquaUtils.logDebug("[" + name + "] " + s);
        } else {
            AquaUtils.logDebug(s);
        }
    }
}
