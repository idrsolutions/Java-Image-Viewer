/*
 * Copyright (c) 1997-2024 IDRsolutions (https://www.idrsolutions.com)
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
import javax.swing.JScrollPane;
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
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
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
    private double zoom;
    private double scale;
    private static BufferedImage image;
    private ImageProcessingOperations operations;
    private CroppingLabel cropLabel;

    private ClippingLabel clippingLabel;

    private Dimension imageLabelSize;

    public Metadata metadata;
    private JFrame info;
    private int cropOpIndex;
    private int clipOpIndex;

    private File tmp;
    private boolean isMulti;
    private int imageCount;
    private int currIm;

    private JDeliImageViewer() {
        super("JDeli Viewer");
        operations = new ImageProcessingOperations();
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
            LogWriter.writeLog(e);
        }
    }

    @Override
    BufferedImage getImage() {
        try {
            if (isMulti) {
                final TiffDecoder tiff = new TiffDecoder();
                return tiff.readImageAt(currIm, file);
            }
            return tmp == null ? JDeli.read(file) : JDeli.read(tmp);
        } catch (final Exception e) {
            LogWriter.writeLog("Unable to read file: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Unable to read file: " + file.getName());
            file = null;
        }
        return null;
    }

    @Override
    protected Rectangle getImageDimension() {
        try {
            return JDeli.readDimension(tmp == null ? file : tmp);
        } catch (final Exception e) {
            LogWriter.writeLog("Unable to read file for dimensions: " + e.getMessage());
            return new Rectangle(0, 0);
        }
    }

    @Override
    protected String getImageType() {
        try {
            metadata = JDeli.getImageInfo(tmp == null ? file : tmp);
        } catch (final Exception e) {
            LogWriter.writeLog("Unable to get image type: " + e.getMessage());
            return "N/A";
        }
        return metadata.getImageMetadataType().toString();
    }

    @Override
    void displayImage() {
        try {
            if ("tif".equals(getExtension()) || "tiff".equals(getExtension())) {
                final TiffDecoder tiff = new TiffDecoder();
                imageCount = tiff.getImageCount(file);
                isMulti = imageCount > 1;
                if (isMulti) {
                   setUpMulti();
                }
            }
        } catch (final IOException e) {
            LogWriter.writeLog(e);
        }
        resetImage();
        draw();
        resetScale();
        reset();

        imageLabelSize = new Dimension(imageLabel.getWidth(), imageLabel.getHeight());
        enableMenus(true);
    }

    private void setUpMulti() {
        final JButton next = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/next.gif"))));
        final JButton prev = new JButton(new ImageIcon(Objects.requireNonNull(getClass().getResource("/jdeli/viewer/prev.gif"))));
        final JComboBox<Integer> img = new JComboBox<>(IntStream.iterate(0, x -> x + 1).limit(imageCount).boxed().toArray(Integer[]::new));
        next.setToolTipText("Next Image");
        next.addActionListener(a -> {
            if (currIm < imageCount - 1) {
                currIm++;
                draw();
                img.setSelectedIndex(currIm);
            }
        });
        prev.setToolTipText("Previous Image");
        prev.addActionListener(a -> {
            if (currIm > 0) {
                currIm--;
                draw();
                img.setSelectedIndex(currIm);
            }
        });
        img.addItemListener(i -> {
            if (i.getStateChange() == ItemEvent.SELECTED) {
                currIm = img.getSelectedIndex();
                draw();
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
            BufferedImage im = getImage();
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

    void resetImage() {
        image = Objects.requireNonNull(getImage());
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
                        draw();
                    }
                }
            }
        });

    }

    @Override
    void draw() {
        BufferedImage im = Objects.requireNonNull(getImage());

        final ImageProcessingOperations zoomOps = new ImageProcessingOperations();
        switch (zoomCombo.getSelectedIndex()) {
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
                zoomOps.scale(zoom);
                break;
        }

        im = zoomOps.apply(im);
        image = operations.apply(im);
        imageLabel.setIcon(new ImageIcon(image));
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

    @Override
    public void actionPerformed(final ActionEvent e) {
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
            draw();
        } else if (source == redo) {
            actionRedo();
            draw();
        } else if (source == metadataMenu) {
            showImageInfo();
        } else if (source == blur) {
            operations.blur();
            draw();
        } else if (source == brighten) {
            operations.brighten(10);
            draw();
        } else if (source == crop) {
            zoomCombo.setSelectedIndex(0);
            processOptions.setSelected(false);
            processOptions.setPopupMenuVisible(false);
            actionCrop();
        } else if (source == darken) {
            operations.brighten(-10);
            draw();
        } else if (source == edgeDetection) {
            operations.edgeDetection();
            draw();
        } else if (source == emboss) {
            operations.emboss();
            draw();
        } else if (source == gaussianBlur) {
            operations.gaussianBlur();
            draw();
        } else if (source == invertColors) {
            operations.invertColors();
            draw();
        } else if (source == mirrorH) {
            operations.mirror(MirrorOperations.HORIZONTAL);
            draw();
        } else if (source == mirrorV) {
            operations.mirror(MirrorOperations.VERTICAL);
            draw();
        } else if (source == sharpen) {
            operations.sharpen();
            draw();
        } else if (source == stretch) {
            operations.stretchToFill(windowWidth, windowHeight);
            draw();
        } else if (source == toARGB) {
            operations.toARGB();
            draw();
        } else if (source == toBinary) {
            operations.toBinary();
            draw();
        } else if (source == toGrayscale) {
            operations.toGrayscale();
            draw();
        } else if (source == toIndexed) {
            operations.toIndexed();
            draw();
        } else if (source == toRGB) {
            operations.toRGB();
            draw();
        } else if (source == watermark) {
            watermarkPopup();
        } else if (source == reset) {
            reset();
            draw();
        } else {
            super.actionPerformed(e);
        }
    }

    private void actionUndo() {
        if (cropOpIndex == 5) {
            try {
                final File temp;
                if (clipOpIndex == 6) {
                    temp = tmp;
                } else {
                    temp = file;
                }
                image = JDeli.read(temp);
                cropLabel.imops.undo().undo();
                tmp = cropLabel.applyCrop(temp);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            cropOpIndex--;
            clipOpIndex = clipOpIndex != -1 ? clipOpIndex - 1 : -1;

        } else if (clipOpIndex == 5) {
            try {
                image = JDeli.read(file);
                clippingLabel.imops.undo().undo();
                tmp = clippingLabel.applyClip(file);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            clipOpIndex--;
            cropOpIndex = cropOpIndex != -1 ? cropOpIndex - 1 : -1;
        } else {
            if (operations.operationsListSize() != 0) {
                operations.undo();
                cropOpIndex = cropOpIndex > 0 ? cropOpIndex - 1 : -1;
                clipOpIndex = clipOpIndex > 0 ? clipOpIndex - 1 : -1;
            }
        }
    }

    private void actionRedo() {
        if (cropOpIndex == 4) {
            try {
                cropLabel.imops.redo().redo();
                tmp = cropLabel.applyCrop(file);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            cropOpIndex++;
            clipOpIndex = clipOpIndex > 0 ? clipOpIndex + 1 : -1;
        } else if (clipOpIndex == 4) {
            try {
                clippingLabel.imops.redo().redo();
                tmp = clippingLabel.applyClip(file);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
            clipOpIndex++;
            cropOpIndex = cropOpIndex > 0 ? cropOpIndex + 1 : -1;
        } else {
            cropOpIndex = cropOpIndex > 0 ? cropOpIndex + 1 : -1;
            clipOpIndex = clipOpIndex > 0 ? clipOpIndex + 1 : -1;
            operations.redo();
        }
    }

    private void actionClip(final ClippingLabel.shape clipShape) {
        int topX = 0;
        int topY = 0;

        if (clippingLabel == null) {
            clippingLabel = new ClippingLabel(this, clipShape);
        } else {
            clippingLabel.clip(this, clipShape);
        }
        if (image.getWidth() < imageLabelSize.getWidth()) {
            topX = (int) ((imageLabelSize.getWidth() / 2) - (image.getWidth() / 2.0));
        }
        if (image.getHeight() < imageLabelSize.getHeight()) {
            topY = (int) ((imageLabelSize.getHeight() / 2) - (image.getHeight() / 2.0));
        }
        clippingLabel.setBounds(topX, topY, (image.getWidth()), (image.getHeight()));
        imageLabel.add(clippingLabel);
        clipOpIndex = 5;
        cropOpIndex = cropOpIndex == 5 ? cropOpIndex + 1 : cropOpIndex;
    }

    static class ClippingLabel extends JLabel {
        private enum shape {
            RECTANGLE, CIRCLE, POLYGON
        }

        private Shape clipShape;
        private final Shape[] polyLines;
        private final Point[] points;
        private int pointsNum;
        protected ImageProcessingOperations imops;

        public ClippingLabel(final JDeliImageViewer v, final shape s) {
            points = new Point[20];
            pointsNum = 0;
            polyLines = new Shape[18];
            clip(v, s);
        }

        protected void clip(final JDeliImageViewer v, final shape s) {
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

            final boolean[] drawing = {false};
            final boolean[] clipSelected = {true};

            final MouseAdapter ma = new MouseAdapter() {

                @Override
                public void mouseMoved(final MouseEvent e) {
                    if (clipSelected[0]) {
                        if (e.getX() < 0 || e.getY() < 0 || e.getX() > e.getComponent().getWidth() || e.getY() > e.getComponent().getHeight()) {
                            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

                        } else if (drawing[0]) {
                            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                            clipShape = updateShape(s, new Point(e.getX(), e.getY()), false, false);
                            repaint();
                        } else {
                            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                        }
                    }
                }
            };
            addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (clipSelected[0]) {
                        if (e.getX() > 0 && e.getY() > 0) {
                            if (e.getButton() == MouseEvent.BUTTON1 && !e.isControlDown()) {

                                clipShape = updateShape(s, new Point(e.getX(), e.getY()), !drawing[0], false);
                                if (s == shape.POLYGON) {
                                    if (drawing[0]) {
                                        points[pointsNum] = new Point(e.getX(), e.getY());
                                        pointsNum++;
                                    }
                                    if (pointsNum > 1) {
                                        polyLines[pointsNum - 2] = new Line2D.Double(points[pointsNum - 2], points[pointsNum - 1]);
                                        if (pointsNum == 18) {
                                            JOptionPane.showMessageDialog(e.getComponent(), "Polygon sides limit reached");
                                        }
                                        repaint();
                                    }
                                }
                                drawing[0] = true;
                            } else if (e.getButton() == MouseEvent.BUTTON3 || (e.isControlDown() && e.getButton() == MouseEvent.BUTTON1)) {
                                drawing[0] = false;
                                removeMouseListener(ma);
                                clipSelected[0] = false;
                                final JPopupMenu popup = new JPopupMenu();
                                final JButton clipToShape = new JButton("Clip to shape");
                                clipToShape.addActionListener(ev -> {
                                    if (v.tmp == null) {
                                        v.tmp = clipImage(v.file, s, new Point(e.getX(), e.getY()), true);
                                    } else {
                                        v.tmp = clipImage(v.tmp, s, new Point(e.getX(), e.getY()), true);
                                    }
                                    v.draw();
                                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                                    popup.setVisible(false);
                                    removeMouseListener(this);
                                    v.imageLabel.remove(v.clippingLabel);
                                });
                                popup.add(clipToShape);
                                popup.addSeparator();
                                final JButton clipShape = new JButton("Clip shape");
                                clipShape.addActionListener(ev -> {
                                    if (v.tmp == null) {
                                        v.tmp = clipImage(v.file, s, new Point(e.getX(), e.getY()), false);
                                    } else {
                                        v.tmp = clipImage(v.tmp, s, new Point(e.getX(), e.getY()), false);
                                    }
                                    v.draw();
                                    popup.setVisible(false);
                                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                                    removeMouseListener(this);
                                    v.imageLabel.remove(v.clippingLabel);
                                });
                                popup.add(clipShape);
                                popup.addSeparator();
                                final JButton cancel = new JButton("Cancel");
                                cancel.addActionListener(ev -> {
                                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                                    popup.setVisible(false);
                                    removeMouseListener(this);
                                    v.draw();
                                    v.imageLabel.remove(v.clippingLabel);

                                });
                                popup.add(cancel);
                                popup.addSeparator();
                                v.clippingLabel.setComponentPopupMenu(popup);
                                popup.show((Component) e.getSource(), e.getX(), e.getY());
                            } else {
                                v.clippingLabel.remove(0);
                            }
                        } else {
                            JOptionPane.showMessageDialog(e.getComponent(), "Please select on the image");
                        }
                    }
                }
            });
            addMouseMotionListener(ma);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            super.paintComponent(g);

            if (clipShape != null) {
                final Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(Color.BLACK);
                final Stroke dashedStroke = new BasicStroke(2.5F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.5F, new float[]{6F, 6F}, 0F);
                g2d.fill(dashedStroke.createStrokedShape(clipShape));
            }
            if (polyLines != null && pointsNum > 1) {
                for (int i = 0; i < pointsNum - 1; i++) {
                    final Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(Color.BLACK);
                    final Stroke dashedStroke = new BasicStroke(2.5F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.5F, new float[]{6F, 6F}, 0F);
                    g2d.fill(dashedStroke.createStrokedShape(polyLines[i]));
                }
            }
        }

        private Shape updateShape(final shape s, final Point p, final boolean starting, final boolean finishing) {

            if (starting) {
                points[0] = p;
                pointsNum++;
            } else {
                if (pointsNum >= 1) {
                    final Point start = points[0];

                    switch (s) {
                        case RECTANGLE:
                            return new Rectangle(start.x, start.y, p.x - start.x, p.y - start.y);
                        case CIRCLE:
                            return new Arc2D.Double(new Rectangle(start.x, start.y, (p.x - start.x), (p.y - start.y)), 0, 360, Arc2D.PIE);
                        case POLYGON:
                            if (finishing) {
                                final int[] x = new int[pointsNum];
                                final int[] y = new int[pointsNum];
                                for (int f = 0; f < pointsNum; f++) {
                                    x[f] = (int) points[f].getX();
                                    y[f] = (int) points[f].getY();
                                }

                                return new Polygon(x, y, pointsNum);
                            }
                            return new Line2D.Double(points[pointsNum - 1].x, points[pointsNum - 1].y, p.x, p.y);
                    }
                }
            }
            return null;
        }

        private File clipImage(final File file, final shape s, final Point p, final boolean clipToShape) {
            imops = new ImageProcessingOperations();
            imops.clip(updateShape(s, p, false, true), clipToShape);
            return applyClip(file);
        }

        protected File applyClip(final File file) {
            final String format = file.getName().substring(file.getName().lastIndexOf('.') + 1);
            final File tmp;
            try {
                tmp = File.createTempFile("tmp", '.' + format);
                image = imops.apply(image);
                JDeli.write(image, format, tmp);
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }

            return tmp;
        }

    }

    static class CroppingLabel extends JLabel {
        private Rectangle rec;
        protected ImageProcessingOperations imops;

        public CroppingLabel(final JDeliImageViewer v) {
            crop(v);
        }

        protected void crop(final JDeliImageViewer viewer) {
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            final Point[] start = {null};
            final Dimension[] d = {null};
            final boolean[] cropSelected = {true};

            final MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseDragged(final MouseEvent e) {
                    if (cropSelected[0] && start[0] != null) {
                        rec.setBounds(start[0].x, start[0].y, e.getX() - start[0].x, e.getY() - start[0].y);
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(final MouseEvent e) {
                    if (cropSelected[0]) {
                        if (e.getX() < 0 || e.getY() < 0 || e.getX() > e.getComponent().getWidth() || e.getY() > e.getComponent().getHeight()) {
                            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        } else {
                            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                        }
                    }
                }
            };
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent e) {
                    if (cropSelected[0] && start[0] == null) {
                        if (e.getX() > 0 && e.getY() > 0) {
                            start[0] = new Point(e.getX(), e.getY());
                        } else {
                            JOptionPane.showMessageDialog(e.getComponent(), "Please select on the image");
                        }
                        rec = new Rectangle();
                    }
                }

                @Override
                public void mouseReleased(final MouseEvent e) {
                    if (rec.width != 0 || rec.height != 0) {
                        final int x, y;
                        final Rectangle r = viewer.getImageDimension();
                        final int imageh = image.getHeight();
                        final int imagew = image.getWidth();

                        if (cropSelected[0] && d[0] == null && start[0] != null) {
                            if (e.getX() > e.getComponent().getWidth() || e.getY() > e.getComponent().getHeight()) {
                                JOptionPane.showMessageDialog(e.getComponent(), "Please select on the image");
                                getGraphics().clearRect(rec.x, rec.y, rec.width, rec.height);
                            } else {
                                x = start[0].x;
                                y = start[0].y;
                                d[0] = new Dimension(e.getX() - x, e.getY() - y);
                                rec = null;
                                cropSelected[0] = false;
                                imops = new ImageProcessingOperations();
                                imops = imops.crop(new Rectangle(new Point(x, y), d[0]));
                                try {

                                    final double imh = ((d[0].height / (float) imageh) * r.height) / d[0].height;
                                    final double imw = ((d[0].width / (float) imagew) * r.width) / d[0].width;
                                    imops = imops.scale(Math.max(imh, imw));
                                    if (viewer.tmp == null) {
                                        viewer.tmp = applyCrop(viewer.file);
                                    } else {
                                        viewer.tmp = applyCrop(viewer.tmp);
                                    }
                                } catch (final Exception ex) {
                                    throw new RuntimeException(ex);
                                }

                                viewer.draw();
                            }
                        }
                    }
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    removeMouseListener(this);
                    removeMouseListener(ma);
                    viewer.imageLabel.remove(viewer.cropLabel);
                }
            });
            addMouseMotionListener(ma);
        }

        protected File applyCrop(final File prevFile) throws Exception {
            final String format = prevFile.getName().substring(prevFile.getName().lastIndexOf('.') + 1);
            final File newFile = File.createTempFile("tmp", '.' + format);
            image = imops.apply(image);
            JDeli.write(image, format, newFile);
            return newFile;
        }

        @Override
        protected void paintComponent(final Graphics g) {
            super.paintComponent(g);

            if (rec != null) {
                final Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(Color.BLACK);
                final Stroke dashedStroke = new BasicStroke(2.5F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.5F, new float[]{6F, 6F}, 0F);
                g2d.fill(dashedStroke.createStrokedShape(rec));
            }
        }
    }

    private void actionCrop() {
        int topX = 0;
        int topY = 0;
        if (cropLabel == null) {
            cropLabel = new CroppingLabel(this);
        } else {
            cropLabel.crop(this);
        }
        if (image.getWidth() < imageLabelSize.getWidth()) {
            topX = (int) ((imageLabelSize.getWidth() / 2) - (image.getWidth() / 2.0));
        } else if (image.getHeight() < imageLabelSize.getHeight()) {
            topY = (int) ((imageLabelSize.getHeight() / 2) - (image.getHeight() / 2.0));
        }
        cropLabel.setBounds(topX, topY, (image.getWidth()), (image.getHeight()));
        imageLabel.add(cropLabel);
        cropOpIndex = 5;
        clipOpIndex = clipOpIndex == 5 ? clipOpIndex + 1 : clipOpIndex;
    }

    private void actonRotateAntiClockwise() {
        if (image != null) {
            operations.rotate(270);
            final int temp = windowWidth;
            windowWidth = windowHeight;
            windowHeight = temp;
            draw();
        } else {
            JOptionPane.showMessageDialog(this, "No Image to rotate");
        }
    }

    private void actionRotateClockwise() {
        if (image != null) {
            operations.rotate(90);
            final int temp = windowWidth;
            windowWidth = windowHeight;
            windowHeight = temp;
            draw();
        } else {
            JOptionPane.showMessageDialog(this, "No Image to rotate");
        }
    }

    private void actionZoomOut() {
        if (image != null) {
            final int selectedIndex = zoomCombo.getSelectedIndex();
            if (selectedIndex < 3) {
                final double s = (Math.round(scale * 10) * 10);
                zoomCombo.setSelectedItem((int) s + "%");
                if (s > 250) {
                    zoomCombo.setSelectedItem("250%");
                } else if ((s / 100) > scale) {

                    zoomCombo.setSelectedIndex(zoomCombo.getSelectedIndex() - 1);
                }
            } else if (selectedIndex > 3 && selectedIndex < zoomCombo.getItemCount() - 3) {
                zoomCombo.setSelectedIndex(selectedIndex - 1);
                zoom = parseZoomCombo();
            }
            draw();
        } else {
            JOptionPane.showMessageDialog(this, noZoomMessage);
        }
    }

    private void actionZoomIn() {
        if (image != null) {
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
                } else {
                    zoomCombo.setSelectedIndex(selectedIndex + 1);
                    zoom = parseZoomCombo();
                }
            }
            draw();
        } else {
            JOptionPane.showMessageDialog(this, noZoomMessage);

        }
    }

    @Override
    public void itemStateChanged(final ItemEvent e) {
        if (e.getSource() == zoomCombo && e.getStateChange() == ItemEvent.SELECTED) {
            if (image != null) {
                if (zoomCombo.getSelectedIndex() > 3) {
                    zoom = parseZoomCombo();
                }
                draw();
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
        cropOpIndex = -1;
        clipOpIndex = -1;
    }

    void showImageInfo() {
        if (info == null) {
            info = new JFrame("Image Info");
            final JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new GridLayout(22, 2, 1, 1));
            Exif exif = null;
            try (FileImageInputStream fios = new FileImageInputStream(tmp == null ? file : tmp)) {
                final byte[] data = new byte[(int) fios.length()];
                fios.read(data);
                metadata = JDeli.getImageInfo(data);
                final TreeMap<String, String> metadataMap = (TreeMap<String, String>) metadata.toMap();
                if (getImageType().equals(ImageFormat.HEIC_IMAGE.toString())) {
                    final HeicDecoder hdec = new HeicDecoder();
                    exif = hdec.readExif(data);
                } else if (getImageType().equals(ImageFormat.JPEG_IMAGE.toString())) {
                    if (data[0] == 'E' && data[1] == 'x' && data[2] == 'i' && data[3] == 'f') {
                        final byte[] edata = new byte[data.length - 6];
                        System.arraycopy(data, 6, edata, 0, edata.length);
                        exif = Exif.readExif(edata);
                    }
                } else if (getImageType().equals(ImageFormat.TIFF_IMAGE.toString())) {
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
        }
        info.setVisible(true);
    }

    @SuppressWarnings({"OverlyLongMethod", "ConstantConditions", "java:S138"})
    private void watermarkPopup() {
        final JFrame watermarkFrame = new JFrame("Watermark");
        final JPanel popup = new JPanel();
        popup.setLayout(new BoxLayout(popup, BoxLayout.Y_AXIS));
        final JTabbedPane tabsPane = new JTabbedPane();
        tabsPane.setPreferredSize(new Dimension(450, 550));

        //  text watermark
        final JPanel textPanel = new JPanel();
        textPanel.setPreferredSize(new Dimension(400, 500));
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

        // text panel layout
        textPanel.add(textLabel, c);
        c.gridx++;
        textPanel.add(text, c);
        c.gridx += 1;
        textPanel.add(tPosLabel, c);
        c.gridx++;
        textPanel.add(tPos, c);
        c.gridy++;
        c.gridx = 0;
        textPanel.add(tColorLabel, c);
        c.gridy++;
        c.gridwidth = 4;
        textPanel.add(tColor, c);
        c.gridwidth = 1;
        c.gridy++;
        textPanel.add(fontLabel, c);
        c.gridx++;
        textPanel.add(font, c);
        c.gridx++;
        textPanel.add(fontSizeLabel, c);
        c.gridx++;
        textPanel.add(fontSize, c);
        c.gridx = 0;
        c.gridy++;
        c.weightx = 0.5;
        textPanel.add(fontStyleLabel, c);
        c.gridx++;
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
            if (tabsPane.getSelectedComponent() == textPanel) {
                final Font f = new Font((String) font.getSelectedItem(), fontStyle.getSelectedIndex(), (Integer) fontSize.getSelectedItem());
                operations.watermark(text.getText(), tColor.getColor(), f, (Watermark.WatermarkPosition) tPos.getSelectedItem());
            } else if (tabsPane.getSelectedComponent() == shapePanel) {
                operations.watermark(shapeHashMap.get(shape.getSelectedItem()), sColor.getColor(), (Watermark.WatermarkPosition) sPos.getSelectedItem(), alphaHashMap.get(alpha.getSelectedItem()), (Watermark.WatermarkShapeProperties) properties.getSelectedItem());
            } else if (tabsPane.getSelectedComponent() == imagePanel) {
                try {
                    operations.watermark(JDeli.read(new File(filename[0])), (Watermark.WatermarkPosition) imPos.getSelectedItem(), alphaHashMap.get(imAlpha.getSelectedItem()));
                } catch (final Exception ex) {
                    JOptionPane.showMessageDialog(popup, "Cannot read image file");
                }
            }
            draw();
        });
        popup.add(applyWatermark);
        watermarkFrame.add(popup, BorderLayout.PAGE_START);
        watermarkFrame.setLocation(250, 250);
        watermarkFrame.setSize(650, 650);
        watermarkFrame.setVisible(true);
    }

    private void reset() {
        operations = new ImageProcessingOperations();
        zoom = scale;
        if (cropLabel != null) {
            imageLabel.remove(cropLabel);
        }
        if (clippingLabel != null) {
            imageLabel.remove(clippingLabel);
        }
        zoomCombo.setSelectedIndex(0);
        if (tmp != null) {
            try {
                Files.delete(tmp.toPath());
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            tmp = null;
        }
        info = null;
    }

    @Override
    protected void saveFile() {
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
}
