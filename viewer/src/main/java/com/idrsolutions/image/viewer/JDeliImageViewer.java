/*
 * Copyright (c) 1997-2026 IDRsolutions (https://www.idrsolutions.com)
 */

package com.idrsolutions.image.viewer;

import com.idrsolutions.image.ImageFormat;
import com.idrsolutions.image.JDeli;
import com.idrsolutions.image.encoder.OutputFormat;
import com.idrsolutions.image.heic.HeicDecoder;
import com.idrsolutions.image.metadata.Exif;
import com.idrsolutions.image.metadata.Metadata;
import com.idrsolutions.image.metadata.ifd.IFDData;
import com.idrsolutions.image.process.ImageProcessingOperations;
import com.idrsolutions.image.process.MirrorOperations;
import com.idrsolutions.image.process.Watermark;
import com.idrsolutions.image.tiff.TiffDecoder;
import org.jpedal.utils.LogWriter;

import javax.imageio.stream.FileImageInputStream;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;

public final class JDeliImageViewer extends JavaImageViewer implements ItemListener {

    private static final String noZoomMessage = "No Image to zoom";
    private JComboBox<String> zoomCombo;
    private JButton zoomIn;
    private JButton zoomOut;
    private JButton rotateClockwise;
    private JButton rotateAntiClockwise;

    private JButton metadataMenu;
    private JMenu processOptions;
    private JButton blur, brighten, crop, darken, edgeDetection, emboss, gaussianBlur, invertColors, mirrorV, mirrorH, sharpen, stretch, watermark, reset, undo, redo;
    private JButton toARGB, toBinary, toGrayscale, toIndexed, toRGB;
    private double scale;
    private static BufferedImage image;

    public Metadata metadata;
    private JFrame info;

    private boolean isMulti;
    private int imageCount;
    private int currIm;

    private JDeliImageViewer() {
        super("JDeli Viewer");
        imageCount = 1;
        currIm = 0;
    }

    public static void main(final String[] args) {
        final JDeliImageViewer viewer = new JDeliImageViewer();
        try {
            viewer.run();
            if (args.length == 1) {
                viewer.setAndDisplayFile(new File(args[0]));
            }
        } catch (final Exception e) {
            LogWriter.error(e, "Exception thrown starting the JDeliImageViewer");
        }
    }

    @Override
    BufferedImage getImage(final ImageTab tab) {
        final File file = tab.getFile();
        final File tmp = tab.getTmp();
        try {
            if (isMulti) {
                final TiffDecoder tiff = new TiffDecoder();
                return tiff.readImageAt(currIm, file);
            }
            return tmp == null ? JDeli.read(file) : JDeli.read(tmp);
        } catch (final Exception e) {
            LogWriter.writeLog("Unable to read file: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Unable to read file: " + file.getName());
        }
        return null;
    }

    @Override
    protected Rectangle getImageDimension(final ImageTab tab) {
        try {
            final File tmp = tab.getTmp();
            return JDeli.readDimension(tmp == null ? tab.getFile() : tmp);
        } catch (final Exception e) {
            LogWriter.writeLog("Unable to read file for dimensions: " + e.getMessage());
            return new Rectangle(0, 0);
        }
    }

    @Override
    protected String getImageType(final ImageTab tab) {
        try {
            metadata = tab.getMetadata() == null ? JDeli.getImageInfo(tab.getFile()) : tab.getMetadata();
        } catch (final Exception e) {
            LogWriter.writeLog("Unable to get image type: " + e.getMessage());
            return "N/A";
        }
        return metadata.getImageMetadataType().toString();
    }

    @Override
    void setAndDisplayFile(final File file) {
        final ImageTab tab = new ImageTab(file, new JLabel(), new ImageProcessingOperations());
        tabs.add(tab);
        if (canConvert(tab)) {
            displayImage(tab);
        }
    }

    @Override
    void displayImage(final ImageTab tab) {
        final JLabel imageLabel = tab.getImageLabel();
        final JPanel tabPanel = new JPanel();
        final JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setSize(600, 600);
        final JButton close = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/cross.png"))));
        close.setBorderPainted(false);
        final JLabel tabLabel = new JLabel(tab.getFile().getName());
        tabPanel.setBackground(Color.white);
        tabPanel.setBounds(new Rectangle(tabLabel.getWidth() + 2, tabLabel.getHeight() + 2));
        tabPanel.add(tabLabel);
        tabPanel.add(close);
        tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.X_AXIS));
        imageTabs.add(scrollPane);
        imageTabs.setTabComponentAt(tabs.size() - 1, tabPanel);
        close.addActionListener(e -> {
            final int i = imageTabs.indexOfTabComponent(tabPanel);
            final int saveOnClose = JOptionPane.showOptionDialog(this, "Save Image?", "Save", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
            if (saveOnClose == JOptionPane.YES_OPTION) {
                saveFile(tab);
            }
            imageTabs.remove(i);
            tabs.remove(i);
        });

        try {
            if ("tif".equals(getExtension(tab)) || "tiff".equals(getExtension(tab))) {
                final TiffDecoder tiff = new TiffDecoder();
                imageCount = tiff.getImageCount(tab.getFile());
                isMulti = imageCount > 1;
                if (isMulti) {
                    setUpMulti(tab);
                }
            }
        } catch (final IOException e) {
            LogWriter.error(e, "Exception thrown reading tiff image");
        }
        resetImage(tab);
        draw(tab);
        resetScale();
        reset();

        enableMenus(true);
    }

    private void setUpMulti(final ImageTab tab) {
        final JButton next = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/next.gif"))));
        final JButton prev = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/prev.gif"))));
        final JComboBox<Integer> img = new JComboBox<>(IntStream.iterate(0, x -> x + 1).limit(imageCount).boxed().toArray(Integer[]::new));
        next.setToolTipText("Next Image");
        next.addActionListener(a -> {
            if (currIm < imageCount - 1) {
                currIm++;
                draw(tab);
                img.setSelectedIndex(currIm);
            }
        });
        prev.setToolTipText("Previous Image");
        prev.addActionListener(a -> {
            if (currIm > 0) {
                currIm--;
                draw(tab);
                img.setSelectedIndex(currIm);
            }
        });
        img.addItemListener(i -> {
            if (i.getStateChange() == ItemEvent.SELECTED) {
                currIm = img.getSelectedIndex();
                draw(tab);
            }
        });

        final JPanel multiButtons = new JPanel();
        multiButtons.setLayout(new GridLayout(1, 5));
        multiButtons.setVisible(true);
        multiButtons.add(prev);
        multiButtons.add(img);
        multiButtons.add(next);

        add(multiButtons, BorderLayout.PAGE_END);
        final JPanel thumbnails = new JPanel();
        thumbnails.setLayout(new GridLayout(imageCount, 1, 0, 5));
        thumbnails.setVisible(true);
        final JScrollPane sp = new JScrollPane(thumbnails);
        for (int i = 0; i < imageCount; i++) {
            currIm = i;
            BufferedImage im = getImage(tab);
            final ImageProcessingOperations imops = new ImageProcessingOperations();
            imops.thumbnail(100, 100);
            im = imops.apply(im);
            final JButton t = new JButton(String.valueOf(i), new ImageIcon(im));
            final int finalI = i;
            t.addActionListener(a -> img.setSelectedIndex(finalI));
            thumbnails.add(t);
        }
        currIm = 0;
        add(sp, BorderLayout.EAST);
        pack();
    }

    void resetImage(final ImageTab tab) {
        image = Objects.requireNonNull(getImage(tab));
    }

    void resetScale() {
        scale = calculateFitToScreen(image.getWidth(), image.getHeight());
    }

    @Override
    boolean isImageFormatSupported(final String format) {
        return JDeli.isImageSupportedForInput(format);
    }

    @Override
    void run() throws Exception {
        super.run();
        final JMenuBar buttonPanel = new JMenuBar();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

        processOptions = new JMenu("Process");
        processOptions.setToolTipText("Process image");

        buttonPanel.add(processOptions);
        zoomIn = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/zoom.gif"))));
        zoomIn.setToolTipText("Zoom in");
        zoomIn.addActionListener(this);
        zoomOut = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/minimise.gif"))));
        zoomOut.setToolTipText("Zoom out");
        zoomOut.addActionListener(this);

        zoomCombo = new JComboBox<>(new String[]{"fit page", "fit height", "fit width", "10%", "20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%", "110%",
                "120%", "130%", "140%", "150%", "160%", "170%", "180%", "190%", "200%", "210%", "220%", "230%", "240%", "250%"});
        zoomCombo.setSelectedIndex(0);
        zoomCombo.setMaximumSize(zoomCombo.getPreferredSize());
        zoomCombo.setToolTipText("Change zoom");
        zoomCombo.addItemListener(this);

        metadataMenu = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/metadataIcon.png"))));
        metadataMenu.setToolTipText("Image info");
        metadataMenu.addActionListener(this);
        buttonPanel.add(metadataMenu);


        rotateAntiClockwise = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/rotateLeft.gif"))));
        rotateAntiClockwise.setToolTipText("Rotate anticlockwise");
        rotateAntiClockwise.addActionListener(this);
        rotateClockwise = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/rotateRight.gif"))));
        rotateClockwise.setToolTipText("Rotate clockwise");
        rotateClockwise.addActionListener(this);


        rotateAntiClockwise.setBounds(0, 0, 20, 20);
        rotateClockwise.setBounds(0, 0, 20, 20);
        buttonPanel.add(rotateAntiClockwise);
        buttonPanel.add(rotateClockwise);

        zoomIn.setBounds(0, 0, 20, 20);
        zoomOut.setBounds(0, 0, 20, 20);

        undo = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/undo.png"))));
        undo.setToolTipText("undo");
        undo.addActionListener(this);
        redo = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/redo.png"))));
        redo.setToolTipText("redo");
        redo.addActionListener(this);
        undo.setBounds(0, 0, 20, 20);
        redo.setBounds(0, 0, 20, 20);
        buttonPanel.add(undo);
        buttonPanel.add(redo);

        reset = new JButton("Reset");
        reset.setToolTipText("Reset Image");
        reset.addActionListener(this);

        addProcesses();
        buttonPanel.add(zoomIn);
        buttonPanel.add(zoomOut);
        buttonPanel.add(zoomCombo);
        buttonPanel.add(reset);

        add(buttonPanel, BorderLayout.PAGE_START);

        setJMenuBar(toolBar);
        setVisible(true);

        if (image == null) {
            enableMenus(false);
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                if (e.getComponent() == JDeliImageViewer.this && image != null) {
                    if (frameWidth != e.getComponent().getWidth() || frameHeight != e.getComponent().getHeight()) {
                        windowWidth = frameWidth - 20;
                        windowHeight = frameHeight - 100;
                        resetScale();
                        draw(tabs.get(imageTabs.getSelectedIndex()));
                    }
                }
            }
        });

        imageTabs.addChangeListener(l -> {
            if (imageTabs.getSelectedIndex() >= 0) {
                final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
                zoomCombo.setSelectedIndex(tab.getZoomIndex());
            }
        });
    }

    @Override
    void draw(final ImageTab tab) {
        BufferedImage im = Objects.requireNonNull(getImage(tab));
        final ImageProcessingOperations zoomOps = new ImageProcessingOperations();
        switch (tab.getZoomIndex()) {
            case 0 :
                zoomOps.resizeToFit(windowWidth, windowHeight);
                break;
            case 1 :
                zoomOps.resizeToHeight(windowHeight);
                break;
            case 2 :
                zoomOps.resizeToWidth(windowWidth);
                break;
            default :
                zoomOps.scale(tab.getZoom());
                break;
        }

        if (tab.getOperations() == null) {
            tab.setOperations(new ImageProcessingOperations());
        }
        im = zoomOps.apply(im);
        image = tab.getOperations().apply(im);
        tab.getImageLabel().setIcon(new ImageIcon(image));
    }

    void enableMenus(final boolean status) {
        metadataMenu.setEnabled(status);
        processOptions.setEnabled(status);
        redo.setEnabled(status);
        reset.setEnabled(status);
        rotateAntiClockwise.setEnabled(status);
        rotateClockwise.setEnabled(status);
        undo.setEnabled(status);
        zoomCombo.setEnabled(status);
        zoomIn.setEnabled(status);
        zoomOut.setEnabled(status);
    }

    private JLabel getCurrentImageLabel() {
        return tabs.get(imageTabs.getSelectedIndex()).getImageLabel();
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final int index = imageTabs.getSelectedIndex();
        final Object source = e.getSource();
        if (source == zoomIn) {
            actionZoomIn();
        } else if (source == zoomOut) {
            actionZoomOut();
        } else if (source == rotateClockwise) {
            actionRotateClockwise();
        } else if (source == rotateAntiClockwise) {
            actonRotateAntiClockwise();
        } else if (source == undo) {
            actionUndo();
            draw(tabs.get(index));
        } else if (source == redo) {
            actionRedo();
            draw(tabs.get(index));
        } else if (source == metadataMenu) {
            showImageInfo(tabs.get(index));
        } else if (source == blur) {
            tabs.get(index).getOperations().blur();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == brighten) {
            tabs.get(index).getOperations().brighten(10);
            draw(tabs.get(index));
        } else if (source == crop) {
            zoomCombo.setSelectedIndex(0);
            processOptions.setSelected(false);
            processOptions.setPopupMenuVisible(false);
            actionCrop();
        } else if (source == darken) {
            tabs.get(index).getOperations().brighten(-10);
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == edgeDetection) {
            tabs.get(index).getOperations().edgeDetection();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == emboss) {
            tabs.get(index).getOperations().emboss();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == gaussianBlur) {
            tabs.get(index).getOperations().gaussianBlur();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == invertColors) {
            tabs.get(index).getOperations().invertColors();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == mirrorH) {
            tabs.get(index).getOperations().mirror(MirrorOperations.HORIZONTAL);
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == mirrorV) {
            tabs.get(index).getOperations().mirror(MirrorOperations.VERTICAL);
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == sharpen) {
            tabs.get(index).getOperations().sharpen();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == stretch) {
            tabs.get(index).getOperations().stretchToFill(windowWidth, windowHeight);
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == toARGB) {
            tabs.get(index).getOperations().toARGB();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == toBinary) {
            tabs.get(index).getOperations().toBinary();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == toGrayscale) {
            tabs.get(index).getOperations().toGrayscale();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == toIndexed) {
            tabs.get(index).getOperations().toIndexed();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == toRGB) {
            tabs.get(index).getOperations().toRGB();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else if (source == watermark) {
            watermarkPopup();
        } else if (source == reset) {
            reset();
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else {
            super.actionPerformed(e);
        }
    }

    private void actionUndo() {
        final int index = imageTabs.getSelectedIndex();
        final ImageTab tab = tabs.get(index);
        final File file = tab.getFile();
        int cropOpIndex = tab.getCropOpIndex();
        int clipOpIndex = tab.getClipOpIndex();
        if (cropOpIndex == 5) {
            try {
                final File temp;
                if (clipOpIndex == 6) {
                    temp = tab.getTmp();
                } else {
                    temp = file;
                }
                final CroppingLabel cropLabel = tab.getCroppingLabel();
                image = JDeli.read(temp);
                cropLabel.imops.undo().undo();
                tab.setTmp(cropLabel.applyCrop(temp, image));
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            cropOpIndex--;
            clipOpIndex = clipOpIndex != -1 ? clipOpIndex - 1 : -1;

        } else if (clipOpIndex == 5) {
            try {
                final ClippingLabel clippingLabel = tab.getClippingLabel();
                image = JDeli.read(file);
                clippingLabel.imops.undo().undo();
                tab.setTmp(clippingLabel.applyClip(file, image));
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            clipOpIndex--;
            cropOpIndex = cropOpIndex != -1 ? cropOpIndex - 1 : -1;
        } else {
            if (tab.getOperations().operationsListSize() != 0) {
                tab.getOperations().undo();
                cropOpIndex = cropOpIndex > 0 ? cropOpIndex - 1 : -1;
                clipOpIndex = clipOpIndex > 0 ? clipOpIndex - 1 : -1;
            }
        }
        tab.setCropOpIndex(cropOpIndex);
        tab.setClipOpIndex(clipOpIndex);
    }

    private void actionRedo() {
        final int index = imageTabs.getSelectedIndex();
        final ImageTab tab = tabs.get(index);
        final File file = tab.getFile();
        int cropOpIndex = tab.getCropOpIndex();
        int clipOpIndex = tab.getClipOpIndex();
        if (cropOpIndex == 4) {
            try {
                final CroppingLabel cropLabel = tab.getCroppingLabel();
                cropLabel.imops.redo().redo();
                tab.setTmp(cropLabel.applyCrop(file, image));
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            cropOpIndex++;
            clipOpIndex = clipOpIndex > 0 ? clipOpIndex + 1 : -1;
        } else if (clipOpIndex == 4) {
            try {
                final ClippingLabel clippingLabel = tab .getClippingLabel();
                clippingLabel.imops.redo().redo();
                tab.setTmp(clippingLabel.applyClip(file, image));
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            clipOpIndex++;
            cropOpIndex = cropOpIndex > 0 ? cropOpIndex + 1 : -1;
        } else {
            cropOpIndex = cropOpIndex > 0 ? cropOpIndex + 1 : -1;
            clipOpIndex = clipOpIndex > 0 ? clipOpIndex + 1 : -1;
            tab.getOperations().redo();
        }
        tab.setClipOpIndex(clipOpIndex);
        tab.setCropOpIndex(cropOpIndex);
    }

    private void actionClip(final ClippingLabel.shape clipShape) {
        int topX = 0;
        int topY = 0;
        final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
        draw(tab);
        final Dimension imageLabelSize = tab.getImageLabel().getSize();
        ClippingLabel clippingLabel = tab.getClippingLabel();
        if (clippingLabel == null) {
            clippingLabel = new ClippingLabel(this, clipShape, tab, image);
            tab.setClippingLabel(clippingLabel);
        } else {
            clippingLabel.clip(this, clipShape, tab, image);
        }
        if (image.getWidth() < imageLabelSize.getWidth()) {
            topX = (int) ((imageLabelSize.getWidth() / 2) - (image.getWidth() / 2.0));
        }
        if (image.getHeight() < imageLabelSize.getHeight()) {
            topY = (int) ((imageLabelSize.getHeight() / 2) - (image.getHeight() / 2.0));
        }
        clippingLabel.setBounds(topX, topY, (image.getWidth()), (image.getHeight()));
        getCurrentImageLabel().add(clippingLabel);
        tab.setClipOpIndex(5);
        final int cropOpIndex = tab.getCropOpIndex();
        tab.setCropOpIndex(cropOpIndex == 5 ? cropOpIndex + 1 : cropOpIndex);
    }

    private void actionCrop() {
        int topX = 0;
        int topY = 0;
        final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
        draw(tab);
        final Dimension imageLabelSize = tab.getImageLabel().getSize();
        CroppingLabel cropLabel = tab.getCroppingLabel();
        if (cropLabel == null) {
            cropLabel = new CroppingLabel(this, tab, image);
            tab.setCroppingLabel(cropLabel);
        } else {
            cropLabel.crop(this, tab, image);
        }
        if (image.getWidth() < imageLabelSize.getWidth()) {
            topX = (int) ((imageLabelSize.getWidth() / 2) - (image.getWidth() / 2.0));
        } else if (image.getHeight() < imageLabelSize.getHeight()) {
            topY = (int) ((imageLabelSize.getHeight() / 2) - (image.getHeight() / 2.0));
        }
        cropLabel.setBounds(topX, topY, (image.getWidth()), (image.getHeight()));
        getCurrentImageLabel().add(cropLabel);
        tab.setCropOpIndex(5);
        final int clipOpIndex = tab.getClipOpIndex();
        tab.setClipOpIndex(clipOpIndex == 5 ? clipOpIndex + 1 : clipOpIndex);
    }

    private void actonRotateAntiClockwise() {
        if (image != null) {
            tabs.get(imageTabs.getSelectedIndex()).getOperations().rotate(270);
            final int temp = windowWidth;
            windowWidth = windowHeight;
            windowHeight = temp;
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else {
            JOptionPane.showMessageDialog(this, "No Image to rotate");
        }
    }

    private void actionRotateClockwise() {
        if (image != null) {
            tabs.get(imageTabs.getSelectedIndex()).getOperations().rotate(90);
            final int temp = windowWidth;
            windowWidth = windowHeight;
            windowHeight = temp;
            draw(tabs.get(imageTabs.getSelectedIndex()));
        } else {
            JOptionPane.showMessageDialog(this, "No Image to rotate");
        }
    }

    private void actionZoomOut() {
        if (imageTabs.getSelectedIndex() != -1) {
            final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
            final int selectedIndex = zoomCombo.getSelectedIndex();
            if (selectedIndex < 3) {
                final double s = (Math.round(scale * 10) * 10);
                zoomCombo.setSelectedItem((int) s + "%");
                if (s > 250) {
                    zoomCombo.setSelectedItem("250%");
                } else if ((s / 100) > scale) {

                    zoomCombo.setSelectedIndex(zoomCombo.getSelectedIndex() - 1);
                }
                tab.setZoomIndex(selectedIndex);
            } else if (selectedIndex > 3 && selectedIndex < zoomCombo.getItemCount()) {
                zoomCombo.setSelectedIndex(selectedIndex - 1);
                tab.setZoom(parseZoomCombo());
            }
            draw(tab);
        } else {
            JOptionPane.showMessageDialog(this, noZoomMessage);
        }
    }

    private void actionZoomIn() {
        if (image != null) {
            final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
            final int selectedIndex = zoomCombo.getSelectedIndex();
            if (selectedIndex < zoomCombo.getItemCount() - 1) {
                if (selectedIndex < 3) {
                    final double s = (Math.round(scale * 10) * 10);
                    zoomCombo.setSelectedItem((int) s + "%");
                    if (s < 10) {
                        zoomCombo.setSelectedItem("10%");
                    }
                    if ((s / 100) < scale) {
                        zoomCombo.setSelectedIndex(zoomCombo.getSelectedIndex() + 1);
                    }
                    tab.setZoomIndex(zoomCombo.getSelectedIndex());
                } else {
                    zoomCombo.setSelectedIndex(selectedIndex + 1);
                    tab.setZoom(parseZoomCombo());
                }
            }
            draw(tab);
        } else {
            JOptionPane.showMessageDialog(this, noZoomMessage);

        }
    }

    @Override
    public void itemStateChanged(final ItemEvent e) {
        if (e.getSource() == zoomCombo && e.getStateChange() == ItemEvent.SELECTED) {
            if (image != null) {
                final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
                tab.setZoomIndex(zoomCombo.getSelectedIndex());
                if (zoomCombo.getSelectedIndex() > 3) {
                    tab.setZoom(parseZoomCombo());
                }
                draw(tab);
            } else {
                zoomCombo.removeItemListener(this);
                JOptionPane.showMessageDialog(this, noZoomMessage);
                zoomCombo.setSelectedIndex(0);
                zoomCombo.addItemListener(this);
            }
        }
    }

    private double parseZoomCombo() {
        final String zoom = Objects.requireNonNull(zoomCombo.getSelectedItem()).toString();
        return (Double.parseDouble(zoom.substring(0, zoom.lastIndexOf('%'))) / 100);
    }

    private void addProcesses() {

        final JMenu colorSpaceChange = new JMenu("change colorSpace");
        colorSpaceChange.setBorderPainted(true);
        processOptions.add(colorSpaceChange);
        processOptions.addSeparator();

        blur = new JButton("Blur");
        brighten = new JButton("Brighten");
        crop = new JButton("Crop");
        darken = new JButton("Darken");
        edgeDetection = new JButton("Edge Detection");
        emboss = new JButton("Emboss");
        gaussianBlur = new JButton("Gaussian Blur");
        invertColors = new JButton("Invert Colors");
        mirrorH = new JButton("Mirror Horizontally");
        mirrorV = new JButton("Mirror Vertically");
        final JMenu mirror = new JMenu("mirror");
        colorSpaceChange.setBorderPainted(true);
        processOptions.add(mirror);
        processOptions.addSeparator();
        sharpen = new JButton("Sharpen");
        stretch = new JButton("Stretch");
        watermark = new JButton("Watermark");

        toARGB = new JButton("To ARGB");
        toBinary = new JButton("To Binary");
        toGrayscale = new JButton("To Grayscale");
        toIndexed = new JButton("To Indexed");
        toRGB = new JButton("To RGB");


        final JButton[] colorSpace = {toARGB, toBinary, toGrayscale, toIndexed, toRGB};
        for (final JButton b : colorSpace) {
            b.addActionListener(this);
            colorSpaceChange.add(b);
            colorSpaceChange.addSeparator();
        }

        mirrorH.addActionListener(this);
        mirror.add(mirrorH);
        colorSpaceChange.addSeparator();

        mirrorV.addActionListener(this);
        mirror.add(mirrorV);
        colorSpaceChange.addSeparator();

        final JButton[] processes = {blur, brighten, crop, darken, edgeDetection, emboss, gaussianBlur, invertColors, sharpen, stretch, watermark};
        for (final JButton b : processes) {
            b.addActionListener(this);
            processOptions.add(b);
            processOptions.addSeparator();
        }

        final JMenu clip = new JMenu("Clip");
        processOptions.add(clip);

        final JButton rec = new JButton("Rectangle");
        rec.addActionListener(e -> {
            zoomCombo.setSelectedIndex(0);
            actionClip(ClippingLabel.shape.RECTANGLE);
            processOptions.setSelected(false);
            processOptions.setPopupMenuVisible(false);
        });
        clip.add(rec);

        final JButton circ = new JButton("Circle");
        circ.addActionListener(e -> {
            zoomCombo.setSelectedIndex(0);
            actionClip(ClippingLabel.shape.CIRCLE);
            processOptions.setSelected(false);
            processOptions.setPopupMenuVisible(false);
        });
        clip.add(circ);

        final JButton polygon = new JButton("Polygon");
        polygon.addActionListener(e -> {
            zoomCombo.setSelectedIndex(0);
            actionClip(ClippingLabel.shape.POLYGON);
            processOptions.setSelected(false);
            processOptions.setPopupMenuVisible(false);
        });
        clip.add(polygon);
    }

    void showImageInfo(final ImageTab tab) {
        info = new JFrame("Image Info");
        final JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridLayout(22, 2, 1, 1));
        Exif exif = null;
        try (FileImageInputStream fios = new FileImageInputStream(tab.getTmp() == null ? tab.getFile() : tab.getTmp())) {
            final byte[] data = new byte[(int) fios.length()];
            fios.read(data);
            if (tab.getMetadata() == null) {
                tab.setMetadata(JDeli.getImageInfo(data));
            }
            metadata = tab.getMetadata();

            final TreeMap<String, String> metadataMap = (TreeMap<String, String>) metadata.toMap();
            if (getImageType(tab).equals(ImageFormat.HEIC_IMAGE.toString())) {
                final HeicDecoder hdec = new HeicDecoder();
                exif = hdec.readExif(data);
            } else if (getImageType(tab).equals(ImageFormat.JPEG_IMAGE.toString())) {
                if (data[0] == 'E' && data[1] == 'x' && data[2] == 'i' && data[3] == 'f') {
                    final byte[] edata = new byte[data.length - 6];
                    System.arraycopy(data, 6, edata, 0, edata.length);
                    exif = Exif.readExif(edata);
                }
            } else if (getImageType(tab).equals(ImageFormat.TIFF_IMAGE.toString())) {
                exif = Exif.readExif(data);
            }
            if (exif != null && !exif.getIfdDataList().isEmpty()) {
                final List<IFDData> exifList = exif.getIfdDataList();
                String remainingexif = exifList.get(0).toString();
                int p = 0;
                while (p < remainingexif.length() && remainingexif.contains("\n")) {
                    if (!remainingexif.startsWith("imageHeight") && !remainingexif.startsWith("imageWidth")) {
                        metadataMap.put(remainingexif.substring(0, remainingexif.indexOf(':') + 1), remainingexif.substring(remainingexif.indexOf(':') + 1, remainingexif.indexOf('\n')));
                    }
                    p = remainingexif.indexOf('\n') + 1;
                    remainingexif = remainingexif.substring(p);

                }
            }

            metadataMap.forEach((k, v) -> {
                final JTextField text = new JTextField("   " + k + " : " + v);
                text.setEditable(false);
                infoPanel.add(text);
            });

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        infoPanel.setSize(400, 500);
        info.add(infoPanel);
        info.setLocation(300, 250);
        info.setSize(450, 500);

        info.setVisible(true);
    }

    @SuppressWarnings({"OverlyLongMethod", "ConstantConditions", "java:S138"})
    private void watermarkPopup() {
        final JFrame watermarkFrame = new JFrame("Watermark");
        final JPanel popup = new JPanel();
        popup.setLayout(new BoxLayout(popup, BoxLayout.Y_AXIS));
        final JTabbedPane tabsPane = new JTabbedPane();
        tabsPane.setPreferredSize(new Dimension(460, 550));

        //  text watermark
        final JPanel textPanel = new JPanel();
        textPanel.setPreferredSize(new Dimension(450, 540));
        textPanel.setLayout(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        final JLabel textLabel = new JLabel("Text : ");
        final JTextArea text = new JTextArea();
        final JLabel tColorLabel = new JLabel("Color : ");
        final JColorChooser tColor = new JColorChooser();
        final JLabel fontLabel = new JLabel("Font : ");
        final GraphicsEnvironment gEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final JComboBox<String> font = new JComboBox<>(gEnv.getAvailableFontFamilyNames());
        final JLabel fontSizeLabel = new JLabel("Font Size : ");

        final JComboBox<Integer> fontSize = new JComboBox<>(IntStream.iterate(10, i -> i + 2).limit(96).boxed().toArray(Integer[]::new));
        final JLabel fontStyleLabel = new JLabel("Font Style : ");
        final JComboBox<String> fontStyle = new JComboBox<>(new String[]{"Plain", "Bold", "Italic"});
        final JLabel tPosLabel = new JLabel("Text position : ");
        final JComboBox<Watermark.WatermarkPosition> tPos = new JComboBox<>(Watermark.WatermarkPosition.values());
        final JLabel rectCoordsLabel = new JLabel("Coordinates Rectangle :");
        final JLabel xLabel = new JLabel("X");
        final JLabel yLabel = new JLabel("Y");
        final JLabel wLabel = new JLabel("Width");
        final JLabel hLabel = new JLabel("Height");
        final JSpinner spinnerX = new JSpinner(new SpinnerNumberModel());
        spinnerX.setPreferredSize(new Dimension(50, 30));
        final JSpinner spinnerY = new JSpinner(new SpinnerNumberModel());
        spinnerY.setPreferredSize(new Dimension(50, 30));
        final JSpinner spinnerW = new JSpinner(new SpinnerNumberModel());
        spinnerW.setPreferredSize(new Dimension(50, 30));
        final JSpinner spinnerH = new JSpinner(new SpinnerNumberModel());
        spinnerH.setPreferredSize(new Dimension(50, 30));

        // text panel layout
        c.gridwidth = 2;
        textPanel.add(textLabel, c);
        c.gridx += 2;
        c.gridwidth = 2;
        text.setLineWrap(true);
        textPanel.add(text, c);
        c.gridwidth = 3;
        c.gridx += 2;
        textPanel.add(tPosLabel, c);
        c.gridwidth = 3;
        c.gridx += 3;
        textPanel.add(tPos, c);
        final JPanel rect = new JPanel();
        rect.setLayout(new GridBagLayout());
        rect.setPreferredSize(new Dimension(450, 30));
        rect.setMinimumSize(new Dimension(400, 30));
        tPos.addActionListener(a -> {
            if (tPos.getSelectedIndex() == 2) {

                c.gridy = 1;
                c.gridwidth = 12;
                c.gridx = 0;
                final GridBagConstraints g = new GridBagConstraints();
                g.gridx = 0;
                g.gridwidth = 2;
                g.weightx = 1;
                rect.add(rectCoordsLabel, g);
                g.gridwidth = 2;
                g.weightx = 0.1;
                g.gridx += 2;
                rect.add(xLabel, g);
                g.gridx += 2;
                rect.add(spinnerX, g);
                g.gridx += 2;
                rect.add(yLabel, g);
                g.gridx += 2;
                rect.add(spinnerY, g);
                g.gridx += 2;
                rect.add(wLabel, g);
                g.gridx += 2;
                rect.add(spinnerW, g);
                g.gridx += 2;
                rect.add(hLabel, g);
                g.gridx += 2;
                rect.add(spinnerH, g);

                textPanel.add(rect, c);
            } else {
                if (Arrays.asList(textPanel.getComponents()).contains(rect)) {
                    textPanel.remove(rect);
                }
            }
            textPanel.updateUI();
        });
        c.gridy = 2;
        c.gridx = 0;
        textPanel.add(tColorLabel, c);
        c.gridy = 3;
        c.gridwidth = 10;
        textPanel.add(tColor, c);
        c.gridwidth = 2;
        c.gridy = 4;
        textPanel.add(fontLabel, c);
        c.weightx = 0.5;
        c.gridwidth = 2;
        c.gridx += 2;
        textPanel.add(font, c);
        c.gridwidth = 2;
        c.gridx += 2;
        textPanel.add(fontSizeLabel, c);
        c.weightx = 0.5;
        c.gridwidth = 2;
        c.gridx += 2;
        textPanel.add(fontSize, c);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        c.weightx = 1;
        textPanel.add(fontStyleLabel, c);
        c.weightx = 0.5;
        c.gridwidth = 2;
        c.gridx += 3;
        textPanel.add(fontStyle, c);

        //  shape watermark
        final JPanel shapePanel = new JPanel();
        shapePanel.setLayout(new GridBagLayout());
        final JLabel shapeLabel = new JLabel("Shape : ");
        final HashMap<String, Shape> shapeHashMap = new HashMap<>();
        shapeHashMap.put("Tall Rectangle", new Rectangle(0, 0, 80, 100));
        shapeHashMap.put("Wide Rectangle", new Rectangle(0, 0, 100, 80));
        shapeHashMap.put("Square", new Rectangle(0, 0, 100, 100));
        shapeHashMap.put("Triangle", new Polygon(new int[]{0, 100, 200}, new int[]{100, 0, 100}, 3));
        final JComboBox<String> shape = new JComboBox<>(shapeHashMap.keySet().toArray(new String[4]));
        final JLabel colorLabel = new JLabel("Color : ");
        final JColorChooser sColor = new JColorChooser();
        final JLabel posLabel = new JLabel("Shape position : ");
        final JComboBox<Watermark.WatermarkPosition> sPos = new JComboBox<>(Watermark.WatermarkPosition.values());
        final JLabel shapePropLabel = new JLabel("Properties : ");
        final JComboBox<Watermark.WatermarkShapeProperties> properties = new JComboBox<>(Watermark.WatermarkShapeProperties.values());
        final JLabel shapeAlphaLabel = new JLabel("Alpha Composite : ");
        final HashMap<String, AlphaComposite> alphaHashMap = new HashMap<>();
        final Field[] fields = AlphaComposite.class.getDeclaredFields();
        for (final Field f : fields) {
            final int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) && f.getType() == AlphaComposite.class) {
                try {
                    alphaHashMap.put(f.toString().substring(f.toString().lastIndexOf('.') + 1), (AlphaComposite) f.get(AlphaComposite.class));
                } catch (final IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        final JComboBox<String> alpha = new JComboBox<>(alphaHashMap.keySet().toArray(new String[12]));

        // shape panel layout
        c.gridx = 0;
        c.gridy = 0;
        shapePanel.add(shapeLabel, c);
        c.gridx++;
        shapePanel.add(shape, c);
        c.gridy++;
        c.gridx--;
        shapePanel.add(colorLabel, c);
        c.gridy++;
        c.gridwidth = 5;
        shapePanel.add(sColor, c);
        c.gridwidth = 1;
        c.gridy++;
        shapePanel.add(posLabel, c);
        c.gridx++;
        shapePanel.add(sPos, c);
        c.gridx++;
        shapePanel.add(shapePropLabel, c);
        c.gridx++;
        shapePanel.add(properties, c);
        c.gridx = 0;
        c.gridy++;
        shapePanel.add(shapeAlphaLabel, c);
        c.gridx++;
        shapePanel.add(alpha, c);

        //  image watermark
        final JPanel imagePanel = new JPanel();
        imagePanel.setLayout(new GridBagLayout());
        final JLabel fileLabel = new JLabel("Image file : ");
        final JButton selectFile = new JButton("Select File");
        final String[] filename = {""};
        selectFile.addActionListener(e -> {
            final FileDialog imFile = new FileDialog(watermarkFrame, "File chooser");
            imFile.setMode(FileDialog.LOAD);
            imFile.setFilenameFilter((File file, String name) -> isImageFormatSupported(name.substring(name.lastIndexOf('.') + 1)));

            imFile.setVisible(true);

            if (imFile.getDirectory() != null && imFile.getFile() != null) {
                filename[0] = imFile.getDirectory() + imFile.getFile();
                fileLabel.setText("Image file : " + filename[0]);
            }
        });
        final JLabel imPropLabel = new JLabel("Image position : ");
        final JComboBox<Watermark.WatermarkPosition> imPos = new JComboBox<>(Watermark.WatermarkPosition.values());
        final JLabel imAlphaLabel = new JLabel("Alpha Composite : ");
        final JComboBox<String> imAlpha = new JComboBox<>(alphaHashMap.keySet().toArray(new String[12]));

        // image panel layout
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        imagePanel.add(fileLabel, c);
        c.gridy++;
        c.gridwidth = 1;
        imagePanel.add(selectFile, c);
        c.gridy++;
        imagePanel.add(imPropLabel, c);
        c.gridx++;
        imagePanel.add(imPos, c);
        c.gridx++;
        imagePanel.add(imAlphaLabel, c);
        c.gridx++;
        imagePanel.add(imAlpha, c);


        tabsPane.addTab("Text", textPanel);
        tabsPane.addTab("Shape", shapePanel);
        tabsPane.addTab("Image", imagePanel);

        popup.add(tabsPane);
        final JButton applyWatermark = new JButton("Apply");
        applyWatermark.addActionListener(e -> {
            final int index = imageTabs.getSelectedIndex();
            if (tabsPane.getSelectedComponent() == textPanel) {
                final Font f = new Font((String) font.getSelectedItem(), fontStyle.getSelectedIndex(), (Integer) fontSize.getSelectedItem());
                if (tPos.getSelectedIndex() == 2 && spinnerX.getValue() != null && spinnerY.getValue() != null && spinnerW.getValue() != null && spinnerH.getValue() != null) {
                    tabs.get(index).getOperations().watermark(text.getText(), tColor.getColor(), f, (Watermark.WatermarkPosition) tPos.getSelectedItem(), new Rectangle((int) spinnerX.getValue(), (int) spinnerY.getValue(), (int) spinnerW.getValue(), (int) spinnerH.getValue()));
                } else {
                    tabs.get(index).getOperations().watermark(text.getText(), tColor.getColor(), f, (Watermark.WatermarkPosition) tPos.getSelectedItem());

                }
            } else if (tabsPane.getSelectedComponent() == shapePanel) {
                tabs.get(index).getOperations().watermark(shapeHashMap.get(shape.getSelectedItem()), sColor.getColor(), (Watermark.WatermarkPosition) sPos.getSelectedItem(), alphaHashMap.get(alpha.getSelectedItem()), (Watermark.WatermarkShapeProperties) properties.getSelectedItem());
            } else if (tabsPane.getSelectedComponent() == imagePanel) {
                try {
                    tabs.get(index).getOperations().watermark(JDeli.read(new File(filename[0])), (Watermark.WatermarkPosition) imPos.getSelectedItem(), alphaHashMap.get(imAlpha.getSelectedItem()));
                } catch (final Exception ex) {
                    JOptionPane.showMessageDialog(popup, "Cannot read image file");
                }
            }
            draw(tabs.get(imageTabs.getSelectedIndex()));
        });
        popup.add(applyWatermark);
        watermarkFrame.add(popup, BorderLayout.PAGE_START);
        watermarkFrame.setLocation(250, 250);
        watermarkFrame.setSize(650, 650);
        watermarkFrame.setVisible(true);
    }

    private void reset() {
        final ImageTab tab = tabs.get(imageTabs.getSelectedIndex());
        tab.setOperations(new ImageProcessingOperations());
        tab.setZoom(scale);
        if (tab.getCroppingLabel() != null) {
            tab.getImageLabel().remove(tab.getCroppingLabel());
        }
        if (tab.getClippingLabel() != null) {
            tab.getImageLabel().remove(tab.getClippingLabel());
        }
        zoomCombo.setSelectedIndex(0);
        if (tab.getTmp() != null) {
            try {
                Files.delete(tab.getTmp().toPath());
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            tab.setTmp(null);
        }
        info = null;
    }

    @Override
    protected void saveFile(final ImageTab tab) {
        draw(tab);
        final JFileChooser fileChooser = new JFileChooser();
        Arrays.stream(OutputFormat.values()).forEach(x -> fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(x.name(), x.name())));
        fileChooser.setFileHidingEnabled(true);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.showSaveDialog(this);
        try {
            if (fileChooser.getSelectedFile() != null) {
                final String format = fileChooser.getFileFilter().getDescription();
                JDeli.write(image, format, new File(fileChooser.getSelectedFile().getAbsolutePath() + '.' + format));
                JOptionPane.showMessageDialog(this, "File saved");
            }
        } catch (final Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot save file");

        }
    }

    @Override
    protected void saveFiles() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Arrays.stream(OutputFormat.values()).forEach(x -> fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(x.name(), x.name())));
        fileChooser.setFileHidingEnabled(true);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.showSaveDialog(this);
        final File folder = fileChooser.getSelectedFile();
        if (!folder.getName().isEmpty() && !folder.exists()) {
            folder.mkdir();
        }
        try {
            for (final ImageTab tab : tabs) {
                draw(tab);
                if (fileChooser.getSelectedFile() != null) {
                    final String format = fileChooser.getFileFilter().getDescription();
                    final String name = tab.getFile().getName();
                    JDeli.write(image, format, new File(fileChooser.getSelectedFile() + File.separator + name.substring(0, name.indexOf('.')) + '.' + format));
                }
            }
            JOptionPane.showMessageDialog(this, "Files saved");
        } catch (final Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot save files");

        }
    }
}
