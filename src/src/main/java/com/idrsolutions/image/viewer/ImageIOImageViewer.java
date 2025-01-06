/*
 * Copyright (c) 1997-2025 IDRsolutions (https://www.idrsolutions.com)
 */

package com.idrsolutions.image.viewer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

final class ImageIOImageViewer extends JavaImageViewer {

    private ImageIOImageViewer() {
        super("ImageIO Viewer");
    }

    public static void main(final String[] args) throws Exception {
        final ImageIOImageViewer viewer = new ImageIOImageViewer();
        viewer.run();
    }

    @Override
    void run() throws Exception {
        super.run();
        setJMenuBar(toolBar);
        setVisible(true);
    }

    @Override
    BufferedImage getImage() {
        try {
            return ImageIO.read(file);
        } catch (final IOException e) {
            System.err.println(e);
        }
        return null;
    }

    @Override
    Rectangle getImageDimension() {
        try {
            final ImageInputStream iis = ImageIO.createImageInputStream(file);
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (readers.hasNext()) {
                final ImageReader reader = readers.next();
                reader.setInput(iis, true);

                return new Rectangle(reader.getWidth(0), reader.getHeight(0));
            }

        } catch (final IOException e) {
            System.err.println("Unable to get image dimensions: " + e);
        }
        return new Rectangle(0, 0);
    }

    @Override
    String getImageType() {
        try {
            final ImageInputStream iis = ImageIO.createImageInputStream(file);
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (readers.hasNext()) {
                final ImageReader reader = readers.next();
                reader.setInput(iis, true);
                reader.getImageMetadata(0);
                return reader.getFormatName();
            }

        } catch (final IOException e) {
            System.err.println("Unable to get image type: " + e);
        }
        return null;
    }

    @Override
    protected boolean isImageFormatSupported(final String format) {
        final String[] supportedFormats = ImageIO.getReaderFormatNames();
        for (final String imageFormat : supportedFormats) {
            if (imageFormat.equals(format)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("PMD.UnnecessaryCaseChange")
    void saveFile() {
        final BufferedImage image = getImage();
        if (image != null) {
            final JFileChooser fileChooser = new JFileChooser();
            Arrays.stream(ImageIO.getWriterFormatNames()).forEach(a -> {
                if (!a.equals(a.toLowerCase())) {
                    fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(a, a));
                }
            });
            fileChooser.setFileHidingEnabled(true);
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.showSaveDialog(this);
            try {
                if (fileChooser.getSelectedFile() != null) {
                    final String format = fileChooser.getFileFilter().getDescription();
                    ImageIO.write(image, format, new File(fileChooser.getSelectedFile().getAbsolutePath() + '.' + format));
                    JOptionPane.showMessageDialog(this, "File saved");
                }
            } catch (final Exception e) {
                JOptionPane.showMessageDialog(this, "Cannot save file");

            }
        } else {
            JOptionPane.showMessageDialog(this, "Cannot save file");
        }
    }
}
