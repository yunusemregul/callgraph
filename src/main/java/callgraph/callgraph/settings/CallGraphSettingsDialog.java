package callgraph.callgraph.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Dialog for configuring CallGraph plugin settings.
 */
public class CallGraphSettingsDialog extends DialogWrapper {
    private final CallGraphSettings settings;
    private final Project project;
    
    private JBRadioButton useCustomColorButton;
    private JBRadioButton useIdeColorButton;
    private JButton colorPickerButton;
    private JPanel colorPreview;

    // Graph settings controls
    private JBRadioButton directionCallersButton;
    private JBRadioButton directionCalleesButton;
    private JBRadioButton directionBothButton;
    private JBCheckBox filterTestCodeCheckbox;
    private JBCheckBox lazyExpansionCheckbox;
    private JSpinner maxDepthSpinner;
    private JLabel maxDepthLabel;

    private String selectedBackgroundType;
    private String selectedCustomColor;
    private String selectedDirection;
    private boolean selectedFilterTestCode;
    private boolean selectedLazyExpansion;
    private int selectedMaxDepth;

    // Store original settings to revert if canceled
    private final String originalBackgroundType;
    private final String originalCustomColor;
    private final String originalDirection;
    private final boolean originalFilterTestCode;
    private final boolean originalLazyExpansion;
    private final int originalMaxDepth;
    
    /**
     * Listener for real-time settings changes
     */
    public interface SettingsChangeListener {
        void onSettingsChanged(String backgroundType, String customBackgroundColor);
    }
    
    private SettingsChangeListener changeListener;

    public CallGraphSettingsDialog(Project project) {
        super(project, true);
        this.project = project;
        this.settings = CallGraphSettings.getInstance(project);
        
        // Store original settings for cancel action
        this.originalBackgroundType = settings.getBackgroundType();
        this.originalCustomColor = settings.getCustomBackgroundColor();
        this.originalDirection = settings.getGraphDirection();
        this.originalFilterTestCode = settings.isFilterTestCode();
        this.originalLazyExpansion = settings.isLazyExpansion();
        this.originalMaxDepth = settings.getMaxDepth();

        // Initialize current working values
        this.selectedBackgroundType = this.originalBackgroundType;
        this.selectedCustomColor = this.originalCustomColor;
        this.selectedDirection = this.originalDirection;
        this.selectedFilterTestCode = this.originalFilterTestCode;
        this.selectedLazyExpansion = this.originalLazyExpansion;
        this.selectedMaxDepth = this.originalMaxDepth;
        
        // Set the change listener to update the browser in real-time
        this.changeListener = (backgroundType, customBackgroundColor) -> {
            // Apply changes immediately
            settings.setBackgroundType(backgroundType);
            settings.setCustomBackgroundColor(customBackgroundColor);
            callgraph.callgraph.browser.BrowserManager.getInstance(project).applySettings();
        };
        
        setTitle("Call Graph Settings");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setPreferredSize(new Dimension(420, 320));
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        JPanel appearancePanel = createAppearancePanel();
        tabbedPane.addTab("Appearance", appearancePanel);

        JPanel graphPanel = createGraphPanel();
        tabbedPane.addTab("Graph", graphPanel);
        
        dialogPanel.add(tabbedPane, BorderLayout.CENTER);
        
        return dialogPanel;
    }

    private JPanel createAppearancePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(5));
        
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 1.0;
        
        // Background color section
        JPanel backgroundPanel = new JPanel(new GridBagLayout());
        backgroundPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Background Color", 
                TitledBorder.LEFT, TitledBorder.TOP));
        
        ButtonGroup backgroundGroup = new ButtonGroup();
        
        useIdeColorButton = new JBRadioButton("Use IDE Editor Background");
        useIdeColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_IDE.equals(selectedBackgroundType));
        backgroundGroup.add(useIdeColorButton);
        
        useCustomColorButton = new JBRadioButton("Use Custom Color");
        useCustomColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_CUSTOM.equals(selectedBackgroundType));
        backgroundGroup.add(useCustomColorButton);
        
        // Color picker panel with color preview
        JPanel colorPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        colorPickerButton = new JButton("Choose Color...");
        
        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(24, 24));
        colorPreview.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
        updateColorPreview();
        
        colorPickerButton.addActionListener(e -> {
            Color initialColor;
            try {
                initialColor = Color.decode(selectedCustomColor);
            } catch (NumberFormatException ex) {
                initialColor = Color.BLACK;
            }
            
            Color color = JColorChooser.showDialog(panel, "Choose Background Color", initialColor);
            
            if (color != null) {
                selectedCustomColor = "#" + ColorUtil.toHex(color);
                updateColorPreview();

                useCustomColorButton.setSelected(true);
                selectedBackgroundType = CallGraphSettings.BACKGROUND_TYPE_CUSTOM;
                
                // Notify changes to update the UI immediately
                if (changeListener != null) {
                    changeListener.onSettingsChanged(selectedBackgroundType, selectedCustomColor);
                }
            }
        });
        
        colorPickerPanel.add(colorPickerButton);
        colorPickerPanel.add(colorPreview);
        
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 0;
        bc.anchor = GridBagConstraints.WEST;
        bc.insets = JBUI.insets(3, 3, 1, 3);
        bc.fill = GridBagConstraints.HORIZONTAL;
        backgroundPanel.add(useIdeColorButton, bc);
        
        bc.gridx = 0;
        bc.gridy = 1;
        bc.insets = JBUI.insets(1, 3, 1, 3);
        backgroundPanel.add(useCustomColorButton, bc);
        
        bc.gridx = 0;
        bc.gridy = 2;
        bc.insets = JBUI.insets(0, 20, 3, 3);
        backgroundPanel.add(colorPickerPanel, bc);
        
        panel.add(backgroundPanel, c);
        
        // Setup action listeners for radio buttons
        ActionListener radioListener = actionEvent -> {
            if (useCustomColorButton.isSelected()) {
                selectedBackgroundType = CallGraphSettings.BACKGROUND_TYPE_CUSTOM;
                colorPickerButton.setEnabled(true);
            } else {
                selectedBackgroundType = CallGraphSettings.BACKGROUND_TYPE_IDE;
                colorPickerButton.setEnabled(false);
            }
            
            // Notify listener about the change to update the UI immediately
            if (changeListener != null) {
                changeListener.onSettingsChanged(selectedBackgroundType, selectedCustomColor);
            }
        };
        
        useCustomColorButton.addActionListener(radioListener);
        useIdeColorButton.addActionListener(radioListener);
        
        // Initialize enabled state
        colorPickerButton.setEnabled(useCustomColorButton.isSelected());
        
        return panel;
    }
    
    private JPanel createGraphPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(5));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 1.0;

        // Direction section
        JPanel directionPanel = new JPanel(new GridBagLayout());
        directionPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Graph Direction",
                TitledBorder.LEFT, TitledBorder.TOP));

        ButtonGroup directionGroup = new ButtonGroup();
        directionCallersButton = new JBRadioButton("Callers (who calls the selected method)");
        directionCalleesButton = new JBRadioButton("Callees (what the selected method calls)");
        directionBothButton = new JBRadioButton("Both");
        directionGroup.add(directionCallersButton);
        directionGroup.add(directionCalleesButton);
        directionGroup.add(directionBothButton);

        directionCallersButton.setSelected(CallGraphSettings.DIRECTION_CALLERS.equals(selectedDirection));
        directionCalleesButton.setSelected(CallGraphSettings.DIRECTION_CALLEES.equals(selectedDirection));
        directionBothButton.setSelected(CallGraphSettings.DIRECTION_BOTH.equals(selectedDirection));

        ActionListener directionListener = e -> {
            if (directionCallersButton.isSelected()) selectedDirection = CallGraphSettings.DIRECTION_CALLERS;
            else if (directionCalleesButton.isSelected()) selectedDirection = CallGraphSettings.DIRECTION_CALLEES;
            else selectedDirection = CallGraphSettings.DIRECTION_BOTH;
        };
        directionCallersButton.addActionListener(directionListener);
        directionCalleesButton.addActionListener(directionListener);
        directionBothButton.addActionListener(directionListener);

        GridBagConstraints dc = new GridBagConstraints();
        dc.gridx = 0; dc.gridy = 0; dc.anchor = GridBagConstraints.WEST;
        dc.insets = JBUI.insets(3, 3, 1, 3); dc.fill = GridBagConstraints.HORIZONTAL;
        directionPanel.add(directionCallersButton, dc);
        dc.gridy = 1; dc.insets = JBUI.insets(1, 3, 1, 3);
        directionPanel.add(directionCalleesButton, dc);
        dc.gridy = 2; dc.insets = JBUI.insets(1, 3, 3, 3);
        directionPanel.add(directionBothButton, dc);
        panel.add(directionPanel, c);

        // Options section
        c.gridy = 1;
        c.insets = JBUI.insets(6, 0, 0, 0);
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Options",
                TitledBorder.LEFT, TitledBorder.TOP));

        lazyExpansionCheckbox = new JBCheckBox("Lazy expansion (start with root node, expand on right-click)");
        lazyExpansionCheckbox.setSelected(selectedLazyExpansion);

        filterTestCodeCheckbox = new JBCheckBox("Filter out test code");
        filterTestCodeCheckbox.setSelected(selectedFilterTestCode);

        maxDepthLabel = new JLabel("Max depth (when lazy expansion is off):");
        maxDepthSpinner = new JSpinner(new SpinnerNumberModel(selectedMaxDepth, 1, 50, 1));
        maxDepthSpinner.setPreferredSize(new Dimension(60, maxDepthSpinner.getPreferredSize().height));
        maxDepthSpinner.setEnabled(!selectedLazyExpansion);
        maxDepthLabel.setEnabled(!selectedLazyExpansion);

        lazyExpansionCheckbox.addActionListener(e -> {
            selectedLazyExpansion = lazyExpansionCheckbox.isSelected();
            maxDepthSpinner.setEnabled(!selectedLazyExpansion);
            maxDepthLabel.setEnabled(!selectedLazyExpansion);
        });
        filterTestCodeCheckbox.addActionListener(e -> selectedFilterTestCode = filterTestCodeCheckbox.isSelected());
        maxDepthSpinner.addChangeListener(e -> selectedMaxDepth = (Integer) maxDepthSpinner.getValue());

        GridBagConstraints oc = new GridBagConstraints();
        oc.gridx = 0; oc.gridy = 0; oc.anchor = GridBagConstraints.WEST;
        oc.insets = JBUI.insets(3, 3, 1, 3); oc.fill = GridBagConstraints.HORIZONTAL;
        oc.gridwidth = GridBagConstraints.REMAINDER;
        optionsPanel.add(lazyExpansionCheckbox, oc);
        oc.gridy = 1; oc.insets = JBUI.insets(1, 3, 3, 3);
        optionsPanel.add(filterTestCodeCheckbox, oc);
        oc.gridy = 2; oc.gridwidth = 1; oc.fill = GridBagConstraints.NONE; oc.insets = JBUI.insets(1, 3, 3, 3);
        optionsPanel.add(maxDepthLabel, oc);
        oc.gridx = 1; oc.insets = JBUI.insets(1, 6, 3, 3);
        optionsPanel.add(maxDepthSpinner, oc);

        panel.add(optionsPanel, c);

        // Filler
        c.gridy = 2; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), c);

        return panel;
    }

    private void updateColorPreview() {
        try {
            colorPreview.setBackground(Color.decode(selectedCustomColor));
        } catch (NumberFormatException e) {
            colorPreview.setBackground(Color.BLACK);
        }
    }

    @Override
    protected void doOKAction() {
        settings.setBackgroundType(selectedBackgroundType);
        settings.setCustomBackgroundColor(selectedCustomColor);
        settings.setGraphDirection(selectedDirection);
        settings.setFilterTestCode(selectedFilterTestCode);
        settings.setLazyExpansion(selectedLazyExpansion);
        settings.setMaxDepth(selectedMaxDepth);
        super.doOKAction();
    }

    public void resetToOriginal() {
        selectedBackgroundType = originalBackgroundType;
        selectedCustomColor = originalCustomColor;
        selectedDirection = originalDirection;
        selectedFilterTestCode = originalFilterTestCode;
        selectedLazyExpansion = originalLazyExpansion;
        selectedMaxDepth = originalMaxDepth;

        useIdeColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_IDE.equals(originalBackgroundType));
        useCustomColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_CUSTOM.equals(originalBackgroundType));
        colorPickerButton.setEnabled(CallGraphSettings.BACKGROUND_TYPE_CUSTOM.equals(originalBackgroundType));
        updateColorPreview();

        directionCallersButton.setSelected(CallGraphSettings.DIRECTION_CALLERS.equals(originalDirection));
        directionCalleesButton.setSelected(CallGraphSettings.DIRECTION_CALLEES.equals(originalDirection));
        directionBothButton.setSelected(CallGraphSettings.DIRECTION_BOTH.equals(originalDirection));
        filterTestCodeCheckbox.setSelected(originalFilterTestCode);
        lazyExpansionCheckbox.setSelected(originalLazyExpansion);
        maxDepthSpinner.setValue(originalMaxDepth);
        maxDepthSpinner.setEnabled(!originalLazyExpansion);
        maxDepthLabel.setEnabled(!originalLazyExpansion);

        settings.setBackgroundType(originalBackgroundType);
        settings.setCustomBackgroundColor(originalCustomColor);
        settings.setGraphDirection(originalDirection);
        settings.setFilterTestCode(originalFilterTestCode);
        settings.setLazyExpansion(originalLazyExpansion);
        settings.setMaxDepth(originalMaxDepth);
        callgraph.callgraph.browser.BrowserManager.getInstance(project).applySettings();
    }

    @Override
    protected Action @NotNull [] createActions() {
        Action[] defaultActions = super.createActions();
        
        // Replace the cancel action with a new action named "Reset"
        for (int i = 0; i < defaultActions.length; i++) {
            if (defaultActions[i] == getCancelAction()) {
                defaultActions[i] = new DialogWrapperAction("Reset") {
                    @Override
                    protected void doAction(ActionEvent e) {
                        resetToOriginal();
                    }
                };
                break;
            }
        }
        
        return defaultActions;
    }
}
