/*
 * Copyright (c) 1997-2024 IDRsolutions (https://www.idrsolutions.com)
 */

package com.idrsolutions.image.viewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

abstract class JavaImageViewer extends JFrame implements ActionListener {

    JLabel imageLabel;
    private JMenuItem docProperties;
    private JMenuItem open;
    private JMenuItem close;

    private JMenuItem save;
    private JMenuItem about;
    private JMenuItem visitWebsite;
    private JMenuItem openTutorials;
    private final String viewerTitle;

    final JMenuBar toolBar = new JMenuBar();
    static int frameWidth;
    static int frameHeight;

    int windowWidth, windowHeight;



    File file;
    private static final String VERSION;

    static {
        final Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        frameWidth = (int) (screenDimension.getWidth() / 1.5);
        frameHeight = (int) (screenDimension.getHeight() / 1.5);
        final Properties props = new Properties();
        try (InputStream is = JavaImageViewer.class.getResourceAsStream("/version.num")) {
            if (is != null) {
                try {
                    props.load(is);
                } catch (final IOException ex) {
                    System.err.println("Exception: " + ex.getMessage());
                }
            }
        } catch (final IOException e) {
            System.err.println("Exception: " + e.getMessage());
        }

        final String versionSet = props.getProperty("release");

        if (versionSet != null) {
            VERSION = versionSet;
        } else {
            VERSION = "@VERSION@";
        }
    }

    JavaImageViewer(final String title) {
        viewerTitle = title;
        setPreferredSize(new Dimension(frameWidth, frameHeight));
    }

    void run() throws Exception {

        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(Color.GRAY, 1, true));
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        setTitle(viewerTitle);
        setSize(frameWidth, frameHeight);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                close();
            }
        });


        final JPanel window = new JPanel();
        windowWidth = frameWidth - 20;
        windowHeight = frameHeight - 100;

        imageLabel = new JLabel();
        imageLabel.setBounds(10, 10, 500, 500);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);

        final JScrollPane scrollPane = new JScrollPane(imageLabel);

        scrollPane.setSize(600, 600);
        toolBar.setBounds(0, 0, 400, 20);
        final JMenu fileMenu = getMenu("File");
        final JMenu helpMenu = getMenu("Help");

        docProperties = new JMenuItem("Document Properties");
        docProperties.addActionListener(this);
        open = new JMenuItem("Open");
        open.addActionListener(this);
        close = new JMenuItem("Close");
        close.addActionListener(this);
        save = new JMenuItem("Save");
        save.addActionListener(this);

        about = new JMenuItem("About");
        about.addActionListener(this);
        visitWebsite = new JMenuItem("Visit Website");
        visitWebsite.addActionListener(this);
        openTutorials = new JMenuItem("Documentation");
        openTutorials.addActionListener(this);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        panel.add(window, BorderLayout.CENTER);
        add(panel);
        toolBar.add(fileMenu);
        toolBar.add(helpMenu);

        fileMenu.add(open);
        fileMenu.addSeparator();
        fileMenu.add(save);
        fileMenu.addSeparator();
        fileMenu.add(docProperties);
        fileMenu.addSeparator();
        fileMenu.add(close);

        helpMenu.add(about);
        helpMenu.addSeparator();
        helpMenu.add(visitWebsite);
        helpMenu.addSeparator();
        helpMenu.add(openTutorials);

        window.setLayout(new BorderLayout());
        window.add(scrollPane, BorderLayout.CENTER);
    }

    private static JMenu getMenu(final String name) {

        final JMenu menu = new JMenu(name);

        menu.setBorderPainted(true);

        return menu;

    }

    private void selectFile() {
        final FileDialog fileChooser = new FileDialog((Frame) null, "File chooser");
        fileChooser.setMode(FileDialog.LOAD);
        fileChooser.setFilenameFilter((File f, String name) -> isImageFormatSupported(name.substring(name.lastIndexOf('.') + 1)));
        fileChooser.setVisible(true);
        if (fileChooser.getDirectory() != null && fileChooser.getFile() != null) {
            file = new File(fileChooser.getDirectory() + fileChooser.getFile());
        }
    }

    private boolean canConvert() {
        if (file == null) {
            JOptionPane.showMessageDialog(this, "No File Selected");
            return false;
        }
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "File does not exist: " + file);
            return false;
        }
        if (!file.isFile()) {
            JOptionPane.showMessageDialog(this, "File selected is not a valid file: " + file);
            return false;
        }
        final String ext = getExtension();
        if (!isImageFormatSupported(ext)) {
            JOptionPane.showMessageDialog(this, ext + " is not a supported image format");
            return false;
        }
        return true;
    }

    String getExtension() {
        final String fileName = file.getName();
        final int index = fileName.lastIndexOf('.') + 1;
        return fileName.substring(index).toLowerCase();
    }

    void setAndDisplayFile(final File file) {
        this.file = file;
        if (canConvert()) {
            displayImage();
        }
    }

    abstract BufferedImage getImage();

    abstract Rectangle getImageDimension();

    abstract String getImageType();

    abstract void saveFile();

    abstract boolean isImageFormatSupported(final String format);

    void displayImage() {
        draw();
    }

    private void displayProperties() {
        if (!canConvert()) {
            return;
        }

        final JFrame propertiesWindow = new JFrame("Document Properties");
        propertiesWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        propertiesWindow.setSize(400, 300);
        propertiesWindow.setLocationRelativeTo(null);
        propertiesWindow.setVisible(true);
        propertiesWindow.getContentPane().setLayout(new BoxLayout(propertiesWindow.getContentPane(), BoxLayout.Y_AXIS));

        final JPanel propertiesPanel = new JPanel();
        propertiesWindow.getContentPane().add(propertiesPanel);

        final Dimension panelDimensions = new Dimension(200, 250);
        propertiesPanel.setPreferredSize(panelDimensions);
        propertiesPanel.setMaximumSize(panelDimensions);
        propertiesPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        propertiesPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
        propertiesPanel.setLayout(new GridLayout(3, 0));


        final Rectangle dimension = getImageDimension();
        final int h = dimension.height;
        final int w = dimension.width;
        final String type = getImageType();

        final JLabel widthData = new JLabel("Width: " + w);
        final JLabel heightData = new JLabel("Height: " + h);
        final JLabel typeData = new JLabel("Type: " + type);

        typeData.setAlignmentX(Component.CENTER_ALIGNMENT);

        propertiesPanel.add(typeData);
        propertiesPanel.add(widthData);
        propertiesPanel.add(heightData);
        propertiesPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));

    }

    private void close() {
        if (file != null) {
            final int saveOnClose = JOptionPane.showOptionDialog(this, "Save Image?", "Save", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
            if (saveOnClose == JOptionPane.YES_OPTION) {
                saveFile();
            }
        }
        dispose();
        System.exit(0);
    }

    private static void displayAbout() {
        final JFrame aboutWindow = new JFrame("About");
        aboutWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        aboutWindow.setSize(400, 300);
        aboutWindow.setLocationRelativeTo(null);
        aboutWindow.setVisible(true);
        aboutWindow.getContentPane().setLayout(new BoxLayout(aboutWindow.getContentPane(), BoxLayout.Y_AXIS));

        final JPanel aboutPanel = new JPanel();
        aboutWindow.getContentPane().add(aboutPanel);

        final Dimension panelDimensions = new Dimension(200, 250);
        aboutPanel.setPreferredSize(panelDimensions);
        aboutPanel.setMaximumSize(panelDimensions);
        aboutPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        aboutPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
        aboutPanel.setLayout(new GridLayout(3, 0));

        final JLabel versionLabel = new JLabel("Version: " + VERSION);

        aboutPanel.add(versionLabel);

    }

    private static void openWebsite(final String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (final IOException | URISyntaxException e) {
            System.err.println("Failed to open website: " + e.getMessage());
        }
    }

    void draw() {
        final BufferedImage original = getImage();
        if (original == null) {
            return;
        }

        final double zoomAmount = calculateFitToScreen(original.getWidth(), original.getHeight());

        final double zoomWidth = original.getWidth() * zoomAmount;
        final double zoomHeight = original.getHeight() * zoomAmount;

        imageLabel.setIcon(new ImageIcon(original.getScaledInstance((int)zoomWidth, (int)zoomHeight, Image.SCALE_SMOOTH)));
    }

    static float calculateFitToScreen(final int imageWidth, final int imageHeight) {
        if (imageWidth > imageHeight) {
            return frameWidth / (float) imageWidth;
        } else {
            return (float) frameHeight / imageHeight;
        }
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        if (e.getSource() == open) {
            try {
                selectFile();
                if (canConvert()) {
                    displayImage();
                }
            } catch (final Exception exception) {
                System.err.println("Failed to open file: " + exception.getMessage());
            }
        }

        if (e.getSource() == docProperties) {
            try {
                displayProperties();
            } catch (final Exception exception) {
                System.err.println("Failed to gather file properties: " + exception.getMessage());
            }
        }

        if (e.getSource() == close) {
            close();
        }

        if (e.getSource() == about) {
            displayAbout();
        }

        if (e.getSource() == save && canConvert()) {
            saveFile();
        }

        if (e.getSource() == visitWebsite) {
            openWebsite("https://www.idrsolutions.com/jdeli/pricing");
        }

        if (e.getSource() == openTutorials) {
            openWebsite("https://support.idrsolutions.com/jdeli/");
        }
    }
}
