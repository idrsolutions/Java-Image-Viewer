/*
 * Copyright (c) 1997-2025 IDRsolutions (https://www.idrsolutions.com)
 */

package com.idrsolutions.image.viewer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
        viewerTitle = title + "   -   Version: " + VERSION;
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
        propertiesWindow.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        propertiesWindow.setSize(400, 300);
        propertiesWindow.setLocationRelativeTo(null);
        propertiesWindow.setVisible(true);
        propertiesWindow.getContentPane().setLayout(new BoxLayout(propertiesWindow.getContentPane(), BoxLayout.Y_AXIS));

        final JPanel propertiesPanel = new JPanel();
        propertiesWindow.getContentPane().add(propertiesPanel);

        final Dimension panelDimensions = new Dimension(200, 250);
        propertiesPanel.setPreferredSize(panelDimensions);
        propertiesPanel.setMaximumSize(panelDimensions);
        propertiesPanel.setAlignmentX(CENTER_ALIGNMENT);
        propertiesPanel.setAlignmentY(CENTER_ALIGNMENT);
        propertiesPanel.setLayout(new GridLayout(3, 0));


        final Rectangle dimension = getImageDimension();
        final int h = dimension.height;
        final int w = dimension.width;
        final String type = getImageType();

        final JLabel widthData = new JLabel("Width: " + w);
        final JLabel heightData = new JLabel("Height: " + h);
        final JLabel typeData = new JLabel("Type: " + type);

        typeData.setAlignmentX(CENTER_ALIGNMENT);

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

    @SuppressWarnings({"OverlyLongMethod", "java:S138"})
    void displayAbout() {
        final JFrame aboutWindow = new JFrame("About");
        aboutWindow.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        aboutWindow.setSize(450, 400);
        aboutWindow.setLocationRelativeTo(null);
        aboutWindow.setVisible(true);

        final JPanel aboutPanel = new JPanel();
        aboutPanel.setBackground(new Color(84, 130, 31));
        aboutPanel.setForeground(Color.WHITE);

        final Dimension panelDimensions = new Dimension(450, 400);
        aboutPanel.setPreferredSize(panelDimensions);
        aboutPanel.setMaximumSize(panelDimensions);
        aboutPanel.setAlignmentX(CENTER_ALIGNMENT);
        aboutPanel.setAlignmentY(CENTER_ALIGNMENT);
        final GridBagLayout gl = new GridBagLayout();
        final GridBagConstraints constraints = new GridBagConstraints();

        final JLabel title = new JLabel(viewerTitle.substring(0, viewerTitle.lastIndexOf('-')));
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setForeground(Color.WHITE);

        final JLabel versionsHeaderLabel = new JLabel(" -Version- ");
        versionsHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        versionsHeaderLabel.setForeground(Color.WHITE);
        versionsHeaderLabel.setLayout(gl);
        versionsHeaderLabel.setBorder(new EmptyBorder(20, 0, 0, 0));

        final JLabel jdeliLabel = new JLabel("JDeli: " + VERSION);
        final JLabel javaLabel = new JLabel("Java: " + System.getProperty("java.version"));
        jdeliLabel.setLayout(gl);
        javaLabel.setLayout(gl);
        javaLabel.setForeground(Color.WHITE);
        jdeliLabel.setForeground(Color.WHITE);
        jdeliLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        javaLabel.setBorder(new EmptyBorder(0, 0, 50, 0));

        final JTextArea aboutUsSection = new JTextArea("Our Java swing Image Viewer is split for JDeli and ImageIO so no matter how you use JDeli, you can view your images.");
        aboutUsSection.setOpaque(false);
        aboutUsSection.setLineWrap(true);
        aboutUsSection.setBounds(aboutUsSection.getX(), aboutUsSection.getY(), 400, 20);
        aboutUsSection.setWrapStyleWord(true);
        aboutUsSection.setEditable(false);
        aboutUsSection.setForeground(Color.WHITE);

        final JLabel url = new JLabel("<html><center>Take me to JDeli");
        url.setForeground(Color.blue);
        url.setHorizontalAlignment(SwingConstants.CENTER);
        url.setAlignmentX(CENTER_ALIGNMENT);

        //Create cursor control
        url.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(final MouseEvent e) {
                aboutPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                url.setText("<html><center><a href='https://www.idrsolutions.com/jdeli/'>Take me to JDeli</a></center>");
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                aboutPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                url.setText("<html><center>Take me to JDeli");
            }

            @Override
            public void mouseClicked(final MouseEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(new URI("https://www.idrsolutions.com/jdeli/"));
                } catch (final Exception e1) {
                    System.err.println("Exception attempting launch browser: " + e1);
                }
            }
        });
        aboutPanel.setLayout(gl);
        gl.setConstraints(aboutPanel, constraints);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.gridheight = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        aboutPanel.add(title, constraints);
        constraints.gridheight = 1;
        constraints.gridx = 1;
        constraints.gridy = 1;
        aboutPanel.add(versionsHeaderLabel, constraints);
        constraints.weighty = 0;
        constraints.gridx = 1;
        constraints.gridy = 2;
        aboutPanel.add(jdeliLabel, constraints);
        constraints.gridx = 1;
        constraints.gridy = 3;
        aboutPanel.add(javaLabel, constraints);
        constraints.weighty = 1.0;
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.gridy = 4;
        aboutPanel.add(aboutUsSection, constraints);
        constraints.gridx = 0;
        constraints.gridy = 5;
        aboutPanel.add(url, constraints);
        aboutWindow.getContentPane().add(aboutPanel);
        aboutPanel.paint(aboutPanel.getGraphics());
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
