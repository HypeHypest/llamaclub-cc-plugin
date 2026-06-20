package com.llamaclub.ui;

import com.llamaclub.LlamaClubConfig;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import com.formdev.flatlaf.FlatClientProperties;
import com.llamaclub.ui.constants.UIConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

final class LlamaClubPanelUi
{
	private static final int SPINNER_FIELD_WIDTH = 6;
	private static final int ROW_GAP = 4;
	private static final int TEXT_ROW_GAP = 3;
	private static final Icon CHECKBOX_UNCHECKED = loadCheckboxIcon("checkbox_unchecked.png");
	private static final Icon CHECKBOX_CHECKED = loadCheckboxIcon("checkbox_checked.png");

	private LlamaClubPanelUi()
	{
	}

	static final class Section
	{
		private final JPanel container;

		Section(JPanel container)
		{
			this.container = container;
		}

		void add(JPanel row)
		{
			if (container.getComponentCount() > 0)
			{
				JComponent spacer = (JComponent) Box.createVerticalStrut(ROW_GAP);
				spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
				container.add(spacer);
			}
			constrainRowHeight(row);
			container.add(row);
		}
	}

	static Section createSection(
		JPanel parent,
		String title,
		String description,
		boolean closedByDefault
	)
	{
		final JPanel section = transparentPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		constrainSectionWidth(section);

		final JPanel sectionHeader = transparentPanel();
		sectionHeader.setLayout(new BorderLayout());
		sectionHeader.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, UIConstants.DIVIDER_COLOR),
			new EmptyBorder(0, 0, 3, 1)
		));
		constrainSectionWidth(sectionHeader);

		final JPanel sectionContents = transparentPanel();
		sectionContents.setLayout(new BoxLayout(sectionContents, BoxLayout.Y_AXIS));
		sectionContents.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, UIConstants.DIVIDER_COLOR),
			new EmptyBorder(PluginPanel.BORDER_OFFSET, 0, PluginPanel.BORDER_OFFSET, 0)
		));
		constrainSectionWidth(sectionContents);
		sectionContents.setVisible(!closedByDefault);

		final JButton sectionToggle = new JButton(closedByDefault ? "▶" : "▼");
		sectionToggle.setPreferredSize(new Dimension(18, 0));
		sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		sectionToggle.setToolTipText(closedByDefault ? "Expand" : "Retract");
		SwingUtil.removeButtonDecorations(sectionToggle);
		styleSectionToggle(sectionToggle);
		sectionHeader.add(sectionToggle, BorderLayout.WEST);

		final JLabel sectionName = new JLabel(title);
		sectionName.setForeground(UIConstants.ACCENT);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		if (description != null && !description.isEmpty())
		{
			sectionName.setToolTipText("<html>" + title + ":<br>" + description + "</html>");
		}
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		Runnable toggle = () ->
		{
			boolean open = !sectionContents.isVisible();
			sectionContents.setVisible(open);
			sectionToggle.setText(open ? "▼" : "▶");
			sectionToggle.setToolTipText(open ? "Retract" : "Expand");
			SwingUtilities.invokeLater(section::revalidate);
		};

		sectionToggle.addActionListener(e -> toggle.run());
		MouseAdapter adapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggle.run();
			}
		};
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);
		constrainRowHeight(sectionHeader);

		section.add(sectionHeader);
		section.add(sectionContents);
		parent.add(section);

		return new Section(sectionContents);
	}

	static JLabel styleStatusLabel(JLabel label)
	{
		label.setForeground(UIConstants.TEXT_PRIMARY);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	static JLabel styleMutedStatusLabel(JLabel label)
	{
		label.setForeground(UIConstants.TEXT_MUTED);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	static JPanel createBooleanRow(
		ConfigManager configManager,
		String label,
		String key,
		String description,
		boolean initialValue,
		Consumer<Boolean> onChange
	)
	{
		JPanel item = createItemRow();
		item.add(createLabel(label, description), BorderLayout.CENTER);

		JCheckBox checkbox = new JCheckBox();
		checkbox.setSelected(initialValue);
		styleCheckbox(checkbox);
		checkbox.addActionListener(e ->
		{
			boolean selected = checkbox.isSelected();
			configManager.setConfiguration(LlamaClubConfig.GROUP, key, selected);
			if (onChange != null)
			{
				onChange.accept(selected);
			}
		});
		item.add(checkbox, BorderLayout.EAST);
		return item;
	}

	static JPanel createIntSpinnerRow(
		ConfigManager configManager,
		String label,
		String key,
		String description,
		int initialValue,
		int min,
		int max
	)
	{
		JPanel item = createItemRow();
		item.add(createLabel(label, description), BorderLayout.CENTER);

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialValue, min, max, 1));
		JFormattedTextField spinnerTextField = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		spinnerTextField.setColumns(SPINNER_FIELD_WIDTH);
		styleSpinner(spinner);
		spinner.addChangeListener(e ->
			configManager.setConfiguration(LlamaClubConfig.GROUP, key, (int) spinner.getValue())
		);
		item.add(spinner, BorderLayout.EAST);
		return item;
	}

	static JPanel createTextRow(
		ConfigManager configManager,
		String label,
		String key,
		String description,
		String initialValue,
		boolean secret
	)
	{
		JPanel item = transparentPanel();
		item.setLayout(new BorderLayout(0, TEXT_ROW_GAP));
		constrainSectionWidth(item);
		item.add(createLabel(label, description), BorderLayout.NORTH);

		if (secret)
		{
			JPasswordField passwordField = new JPasswordField();
			passwordField.setText(initialValue);
			styleTextInput(passwordField);
			DocumentListener saveToken = new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					save();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					save();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					save();
				}

				private void save()
				{
					configManager.setConfiguration(
						LlamaClubConfig.GROUP,
						key,
						new String(passwordField.getPassword()).trim()
					);
				}
			};
			passwordField.getDocument().addDocumentListener(saveToken);
			item.add(passwordField, BorderLayout.SOUTH);
		}
		else
		{
			JTextArea textArea = new JTextArea(initialValue);
			textArea.setLineWrap(true);
			textArea.setWrapStyleWord(true);
			textArea.setRows(2);
			styleTextInput(textArea);
			textArea.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					configManager.setConfiguration(LlamaClubConfig.GROUP, key, textArea.getText());
				}
			});
			item.add(textArea, BorderLayout.SOUTH);
		}

		return item;
	}

	static JButton createPanelButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		styleCompactControl(button);
		button.addActionListener(e -> action.run());
		return button;
	}

	private static JPanel createItemRow()
	{
		JPanel item = transparentPanel();
		item.setLayout(new BorderLayout());
		constrainSectionWidth(item);
		return item;
	}

	private static JLabel createLabel(String name, String description)
	{
		JLabel label = new JLabel(name);
		label.setForeground(UIConstants.TEXT_PRIMARY);
		label.setFont(FontManager.getRunescapeSmallFont());
		if (description != null && !description.isEmpty())
		{
			label.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		}
		return label;
	}

	private static void styleSectionToggle(JButton button)
	{
		button.setOpaque(false);
		button.setForeground(UIConstants.TEXT_PRIMARY);
		button.setFocusPainted(false);
	}

	private static void styleCheckbox(JCheckBox checkbox)
	{
		checkbox.setOpaque(false);
		checkbox.setForeground(UIConstants.TEXT_PRIMARY);
		checkbox.setFocusPainted(false);
		checkbox.setIcon(CHECKBOX_UNCHECKED);
		checkbox.setSelectedIcon(CHECKBOX_CHECKED);
		checkbox.setDisabledIcon(CHECKBOX_UNCHECKED);
		checkbox.setDisabledSelectedIcon(CHECKBOX_CHECKED);
	}

	private static void styleTextInput(JComponent input)
	{
		input.setBackground(UIConstants.INPUT_BG);
		input.setForeground(UIConstants.TEXT_PRIMARY);
		input.setBorder(formControlBorder());
		applyFlatBorderStyle(input);
		if (input instanceof JTextComponent)
		{
			JTextComponent textComponent = (JTextComponent) input;
			textComponent.setCaretColor(UIConstants.TEXT_PRIMARY);
			textComponent.setSelectedTextColor(UIConstants.TEXT_PRIMARY);
			textComponent.setSelectionColor(UIConstants.SELECTION);
		}
	}

	private static void styleSpinner(JSpinner spinner)
	{
		spinner.setBackground(UIConstants.INPUT_BG);
		spinner.setForeground(UIConstants.TEXT_PRIMARY);
		spinner.setBorder(new LineBorder(UIConstants.BORDER_COLOR, 1));
		applyFlatBorderStyle(spinner);

		JFormattedTextField editor = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		editor.setBackground(UIConstants.INPUT_BG);
		editor.setForeground(UIConstants.TEXT_PRIMARY);
		editor.setCaretColor(UIConstants.TEXT_PRIMARY);
		editor.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		applyFlatBorderStyle(editor);
	}

	private static void styleCompactControl(JButton button)
	{
		button.setBackground(UIConstants.INPUT_BG);
		button.setForeground(UIConstants.TEXT_PRIMARY);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBorder(new LineBorder(UIConstants.BORDER_COLOR, 1));
		applyFlatBorderStyle(button);
	}

	private static Border formControlBorder()
	{
		return BorderFactory.createCompoundBorder(
			new LineBorder(UIConstants.BORDER_COLOR, 1),
			new EmptyBorder(4, 4, 4, 4)
		);
	}

	static void constrainSectionWidth(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, Integer.MAX_VALUE));
	}

	static void constrainFullWidthButton(JButton button)
	{
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension preferred = button.getPreferredSize();
		button.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, preferred.height));
		button.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, preferred.height));
		button.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, preferred.height));
	}

	private static JPanel constrainRowHeight(JPanel row)
	{
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension preferred = row.getPreferredSize();
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, preferred.height));
		row.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, preferred.height));
		row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, preferred.height));
		return row;
	}

	private static JPanel transparentPanel()
	{
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		return panel;
	}

	private static Icon loadCheckboxIcon(String resourceName)
	{
		BufferedImage image = ImageUtil.loadImageResource(LlamaClubPanelUi.class, resourceName);
		return new ImageIcon(image);
	}

	private static void applyFlatBorderStyle(JComponent component)
	{
		String hex = UIConstants.toHex(UIConstants.BORDER_COLOR);
		component.putClientProperty(
			FlatClientProperties.STYLE,
			"borderColor: " + hex
				+ "; focusedBorderColor: " + hex
				+ "; hoverBorderColor: " + hex
		);
	}
}
